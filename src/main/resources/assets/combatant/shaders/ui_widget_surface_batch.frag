#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec4 v_Local;
in vec4 v_Color;
in vec4 v_Rect;
in vec4 v_CornerModes;   // radius, kind(0 button / 1 slider), hover, enabled
in vec4 v_CornerExtentX; // secondary RGBA
in vec4 v_CornerExtentY; // accent RGBA
in vec4 v_EdgeModes;     // time, mouse uv x/y, focused
in vec4 v_EdgeData;      // slider value, dragging, softness, global alpha
in vec4 v_MaterialData;  // gradient direction xy, gradient intensity, field strength

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen;
};

float saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

float roundedBoxSdf(vec2 p, vec2 halfSize, float radius) {
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float band(float x, float center, float halfWidth, float feather) {
    return 1.0 - smoothstep(halfWidth, halfWidth + feather, abs(x - center));
}

void main() {
    vec2 frag = warpedLocal(v_Local);
    vec2 size = max(v_Rect.zw, vec2(0.001));
    vec2 uv = (frag - v_Rect.xy) / size;
    vec2 halfSize = size * 0.5;
    float d = roundedBoxSdf(frag - (v_Rect.xy + halfSize), halfSize, v_CornerModes.x);

    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    float aa = max(max(logicalScale.x, logicalScale.y), max(fwidth(d) * 0.75, 0.0001));
    aa += max(v_EdgeData.z, 0.0);
    float coverage = saturate(0.5 - d / max(aa, 0.0001));
    if (coverage <= 0.001) discard;

    float hover = saturate(v_CornerModes.z);
    float enabled = saturate(v_CornerModes.w);
    float focused = saturate(v_EdgeModes.w);
    float t = v_EdgeModes.x;
    vec2 mouseUv = v_EdgeModes.yz;

    vec3 primary = v_Color.rgb;
    vec3 secondary = v_CornerExtentX.rgb;
    vec3 accent = v_CornerExtentY.rgb;

    vec2 gradDir = normalize(v_MaterialData.xy);
    float gradientIntensity = saturate(v_MaterialData.z);
    float fieldStrength = saturate(v_MaterialData.w);

    // The theme gradient is evaluated in logical widget space, so warp only bends geometry.
    vec2 centeredUv = uv - 0.5;
    float gradientCoord = saturate(dot(centeredUv, gradDir) * 1.15 + 0.5);
    gradientCoord = smoothstep(0.0, 1.0, gradientCoord);
    float gradientMix = mix(0.20, 0.90, gradientIntensity) * gradientCoord;
    vec3 col = mix(primary, secondary, gradientMix);

    // Aspect-limited field space keeps the same visual scale on short and long buttons.
    float aspect = clamp(size.x / max(size.y, 1.0), 1.0, 4.25);
    vec2 p = centeredUv * vec2(aspect, 1.0);
    float seed = fract(v_Rect.x * 0.0137 + v_Rect.y * 0.0191 + size.x * 0.0023);

    // Mouse acts like local pressure on the membrane; it bends the wave phases instead of adding a spotlight.
    vec2 md = (uv - mouseUv) * vec2(aspect, 1.0);
    float mouseRadius = length(md);
    float pressureEnvelope = (1.0 - smoothstep(0.06, 0.92, mouseRadius)) * hover;
    float pressureWave = sin(mouseRadius * 12.0 - t * 2.15 + seed * 4.0) * pressureEnvelope;

    // Coupled directional phases. Their near-cancellations form sparse caustic-like folds.
    float phaseA = dot(p, vec2(3.65, 5.30)) + t * 0.58 + seed * 6.2831853;
    float waveA = sin(phaseA + pressureWave * 0.70);

    float phaseB = dot(p, vec2(-5.20, 3.75)) - t * 0.43
            + waveA * 0.82 + pressureWave * 1.05 + seed * 3.10;
    float waveB = sin(phaseB);

    float phaseC = dot(p, vec2(6.85, -2.45)) + t * 0.31
            + waveB * 0.62 - waveA * 0.24 + seed * 1.70;
    float waveC = sin(phaseC);

    float interference = waveA * 0.46 + waveB * 0.35 + waveC * 0.19;
    float foldDistance = abs(waveA * 0.58 + waveB * 0.42);
    float caustic = 1.0 - smoothstep(0.045, 0.235, foldDistance);
    float crest = smoothstep(0.58, 0.93, abs(interference));
    float microFold = 0.5 + 0.5 * sin(dot(p, vec2(9.2, -6.4)) - t * 0.24 + waveB * 0.90);
    caustic *= 0.62 + microFold * 0.38;

    // Slow low-frequency breathing makes the field alive at idle without moving the whole gradient.
    float breath = 0.5 + 0.5 * sin(t * 0.72 + seed * 6.2831853);
    float activity = fieldStrength * (0.34 + hover * 0.66) * (0.88 + breath * 0.12);

    // Membrane depth: interference changes apparent depth, not metallic brightness.
    float depthWave = interference * 0.5 + (microFold - 0.5) * 0.22;
    col *= 0.965 + depthWave * (0.040 + activity * 0.026);
    col += secondary * max(depthWave, 0.0) * (0.018 + activity * 0.025);

    // Theme-colored caustic folds. Accent contribution rises on hover, but a subtle idle structure remains.
    float causticMix = caustic * activity * (0.055 + hover * 0.105);
    float crestMix = crest * activity * (0.018 + hover * 0.055);
    vec3 foldColor = mix(secondary, accent, 0.54 + 0.18 * interference);
    col = mix(col, foldColor, causticMix);
    col += accent * crestMix * (0.45 + 0.30 * microFold);

    // Cursor pressure excites the same field rather than painting an unrelated radial highlight.
    float pressureEnergy = pressureEnvelope * (0.5 + 0.5 * pressureWave);
    col = mix(col, mix(secondary, accent, 0.72), pressureEnergy * (0.045 + activity * 0.075));

    // Inner stroke derives from the exact same SDF and interference field.
    // A fold reaching the border energizes the stroke at the same location.
    float edgeDepth = max(-d, 0.0);
    float rimWidth = aa * (1.25 + hover * 0.45) + 0.32;
    float innerRim = 1.0 - smoothstep(0.0, max(rimWidth, 0.0001), edgeDepth);
    float edgeField = saturate(caustic * 0.74 + crest * 0.22 + pressureEnergy * 0.34);
    float rimBase = 0.105 + hover * 0.105 + focused * 0.145;
    float rimReactive = edgeField * (0.16 + fieldStrength * 0.25 + hover * 0.10);
    vec3 rimColor = mix(accent, secondary, 0.20 + 0.26 * (0.5 + 0.5 * interference));
    col = mix(col, rimColor + vec3(0.025) * edgeField, innerRim * (rimBase + rimReactive));

    // Very thin edge crest gives definition without the old metallic stripe.
    float hairline = 1.0 - smoothstep(0.0, max(aa * 0.52, 0.0001), edgeDepth);
    col += mix(accent, secondary, 0.34) * hairline * (0.018 + edgeField * 0.075 + focused * 0.030);

    float kind = v_CornerModes.y;
    if (kind > 0.5) {
        float value = saturate(v_EdgeData.x);
        float dragging = saturate(v_EdgeData.y);
        const float trackLeft = 0.075;
        const float trackRight = 0.925;
        const float trackY = 0.765;
        const float trackHalf = 0.052;

        float track = band(uv.y, trackY, trackHalf, 0.032)
                * smoothstep(trackLeft - 0.015, trackLeft + 0.015, uv.x)
                * (1.0 - smoothstep(trackRight - 0.015, trackRight + 0.015, uv.x));
        float valueX = mix(trackLeft, trackRight, value);
        float fill = track * (1.0 - smoothstep(valueX - 0.010, valueX + 0.010, uv.x));
        float front = track * (1.0 - smoothstep(0.0, 0.050, abs(uv.x - valueX)));

        float trackEnergy = saturate(caustic * 0.72 + crest * 0.30);
        vec3 trackBase = mix(primary, vec3(0.0), 0.42);
        vec3 fillColor = mix(secondary, accent, 0.52 + 0.22 * trackEnergy);
        col = mix(col, trackBase, track * 0.58);
        col = mix(col, fillColor, fill * (0.46 + 0.15 * hover + trackEnergy * 0.12));

        float dragPulse = 0.5 + 0.5 * sin(t * 6.0 + interference * 1.8);
        col += accent * front * (0.15 + hover * 0.12 + dragging * dragPulse * 0.14);

        // Compact energy node at the value front; it inherits local field phase instead of acting as a generic knob.
        vec2 nodeDelta = vec2((uv.x - valueX) * aspect, (uv.y - trackY) * 1.45);
        float node = 1.0 - smoothstep(0.045, 0.120, length(nodeDelta));
        col = mix(col, mix(secondary, accent, 0.78), node * (0.22 + hover * 0.14 + dragging * 0.12));
    }

    if (enabled < 0.5) {
        float luma = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(vec3(luma), col, 0.30) * 0.66;
    }

    float alpha = v_Color.a * v_EdgeData.w * coverage;
    fragColor = vec4(clamp(col, 0.0, 1.0), alpha);
}
