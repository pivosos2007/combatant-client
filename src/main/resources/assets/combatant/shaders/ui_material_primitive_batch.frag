#version 330 core

#moj_import <combatant:ui_aa.glsl>

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec4 v_Local;
in vec4 v_Color;
in vec4 v_Rect;
in vec4 v_Params;
in vec4 v_Params2;
in vec4 v_Params3;
in vec4 v_Params4;
in vec4 v_Params5;
in vec4 v_Params6;
in vec4 v_Params7;

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

// Shared input snapshot: no additional framebuffer capture for solid metal.
layout (std140) uniform UIGlassFrame {
    vec4 uGlassPointer;
    vec4 uGlassMotion;
};

vec2 primitivePoint(int index) {
    if (index == 0) return v_Params.xy;
    if (index == 1) return v_Params.zw;
    if (index == 2) return v_Params2.xy;
    if (index == 3) return v_Params2.zw;
    if (index == 4) return v_Params3.xy;
    if (index == 5) return v_Params3.zw;
    if (index == 6) return v_Params4.xy;
    return v_Params4.zw;
}

float smoothMaximum(float a, float b, float radius) {
    if (radius <= 0.0001) return max(a, b);
    float h = clamp(0.5 + 0.5 * (a - b) / radius, 0.0, 1.0);
    return mix(b, a, h) + radius * h * (1.0 - h);
}

float primitiveSdf(vec2 p, int count, float rounding) {
    float d = -1.0e20;
    for (int i = 0; i < 8; i++) {
        if (i >= count) break;
        int next = i + 1;
        if (next >= count) next = 0;
        vec2 a = primitivePoint(i);
        vec2 b = primitivePoint(next);
        vec2 edge = b - a;
        float edgeLength = max(length(edge), 0.0001);
        float edgeDistance = -(edge.x * (p.y - a.y) - edge.y * (p.x - a.x)) / edgeLength;
        d = i == 0 ? edgeDistance : smoothMaximum(d, edgeDistance, rounding);
    }
    return d;
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    float invW = abs(v_Local.z) > 0.000001 ? v_Local.z : 1.0;
    vec2 local = v_Local.xy / invW - v_Rect.xy;
    int count = int(clamp(floor(v_Params5.x + 0.5), 3.0, 8.0));
    float d = primitiveSdf(local, count, max(v_Params5.y, 0.0));
    float alpha = coverage(d, analyticAa(d, logicalScale));
    if (alpha <= 0.001) discard;

    int mode = int(floor(v_Params6.x + 0.5));
    float detailScale = max(v_Params6.y, 0.01);
    float roughness = clamp(v_Params6.z, 0.0, 1.0);
    float response = clamp(v_Params6.w, 0.0, 1.0);
    vec3 accent = clamp(v_Params7.rgb, 0.0, 1.0);
    float bevelStrength = clamp(v_Params7.w, 0.0, 1.0);
    vec3 color = clamp(v_Color.rgb, 0.0, 1.0);

    vec2 gradient = vec2(dFdx(d) / max(logicalScale.x, 0.0001),
                         dFdy(d) / max(logicalScale.y, 0.0001));
    vec2 normal = length(gradient) > 0.0001 ? normalize(gradient) : vec2(0.0, -1.0);
    float bevelWidth = mix(1.5, 6.5, bevelStrength);
    float edge = 1.0 - smoothstep(0.0, bevelWidth, max(-d, 0.0));

    if (mode == 1) {
        vec2 uv = local / max(v_Rect.zw, vec2(1.0));
        // Anisotropy is locked to the top face. No phase jump when the whole
        // hexagon shifts down on hover/press.
        float tangent = uv.x + uv.y * 0.17;
        float bands = sin(tangent * (90.0 * detailScale)) * 0.5 + 0.5;
        float bandFilter = 1.0 - smoothstep(0.28, 0.72, fwidth(tangent) * 90.0 * detailScale);

        // A travelling specular lobe over the entire face, not just a rim tint.
        // The input is physical surface-local pointer position and only affects
        // this primitive when the corresponding hover parameter is non-zero.
        vec2 pointerLocal = uGlassPointer.zw - v_Rect.xy;
        vec2 pointerDelta = (local - pointerLocal) / max(v_Rect.zw, vec2(1.0));
        float lightDistance = length(pointerDelta * vec2(0.77, 1.32));
        float spot = 1.0 - smoothstep(0.04, 0.75, lightDistance);
        float specSpot = spot * spot * (3.0 - 2.0 * spot);
        vec2 lightDirection = normalize(vec2(-0.48, -1.0)
                + clamp(-pointerDelta * response, vec2(-0.48), vec2(0.48)));
        float facing = dot(normal, lightDirection) * 0.5 + 0.5;
        float rimSpec = pow(max(facing, 0.0), mix(2.2, 5.6, roughness));
        float satinSpec = specSpot * response * (1.0 - roughness * 0.35);
        color = mix(color, accent, 0.045 + edge * rimSpec * 0.10);
        color += vec3((bands - 0.5) * bandFilter * (1.0 - roughness) * 0.046);
        color += mix(accent, vec3(0.92, 0.95, 0.98), 0.38) * satinSpec * 0.19;
        color *= 1.0 - edge * (0.10 - rimSpec * 0.14);
    } else if (mode == 2) {
        float grain = hash21(floor(local * detailScale)) - 0.5;
        color += grain * (0.018 * (1.0 - roughness * 0.45));
        float diffuse = dot(normal, normalize(vec2(-0.35, -1.0))) * 0.5 + 0.5;
        color = mix(color, accent, edge * bevelStrength * diffuse * 0.12);
    } else if (mode == 3) {
        vec2 physical = local / max(logicalScale, vec2(0.0001));
        vec2 cell = fract(physical / detailScale) - 0.5;
        float cellDistance = length(cell);
        float pixelAa = max(fwidth(cellDistance), 0.015);
        float pixel = 1.0 - smoothstep(0.28 - pixelAa, 0.38 + pixelAa, cellDistance);
        float offPixel = pixel * roughness;
        float emitter = pixel * response;
        color = mix(color, accent * 0.32, offPixel);
        color += accent * emitter * (0.16 + bevelStrength * 0.18);
        color *= 1.0 - edge * 0.08;
    } else if (mode == 4) { // GRAPHITE
        vec2 localMicro = local * detailScale;
        vec2 cells = floor(localMicro);
        float pix = hash21(cells + vec2(5.17, 12.43)) - 0.5;
        float filtered = 1.0 - smoothstep(0.25, 0.78, length(fwidth(localMicro)));
        float coarse = hash21(floor(local * 0.075)) - 0.5;
        color = color * (0.94 + coarse * 0.040)
                + pix * filtered * (0.045 + 0.020 * roughness);
        float grazing = clamp(dot(normal, normalize(vec2(-0.30, -0.95))) * 0.5 + 0.5, 0.0, 1.0);
        color += accent * edge * grazing * bevelStrength * 0.08;
        color *= 1.0 - edge * (1.0 - grazing) * 0.10;
    } else if (mode == 5) { // CERAMIC
        vec2 uv = local / max(v_Rect.zw, vec2(1.0));
        float glossy = pow(max(0.0, 1.0 - length((uv - vec2(0.24, 0.04)) * vec2(0.72, 1.60))),
                           mix(1.6, 6.0, roughness));
        float grazing = clamp(dot(normal, normalize(vec2(-0.45, -0.89))) * 0.5 + 0.5, 0.0, 1.0);
        color = mix(color, accent, 0.04 + edge * grazing * 0.10);
        color += vec3(glossy * (1.0 - roughness) * 0.075);
        color += vec3(edge * pow(grazing, 5.0) * bevelStrength * 0.12);
        color *= 1.0 - edge * (1.0 - grazing) * 0.13;
    } else if (mode == 6) { // BRUSHED METAL
        vec2 uv = local / max(v_Rect.zw, vec2(1.0));
        float brush = uv.x * 0.92 + uv.y * 0.065;
        float wave = sin(brush * (160.0 * detailScale) +
                         sin(uv.y * 19.0) * 0.16);
        float aliasGate = 1.0 - smoothstep(0.25, 0.64, fwidth(brush) * 160.0 * detailScale);
        float coarse = hash21(floor(local * 0.18)) - 0.5;
        color += wave * aliasGate * (0.035 + (1.0 - roughness) * 0.035);
        color += coarse * 0.018;
        vec2 dp = (local - (uGlassPointer.zw - v_Rect.xy)) / max(v_Rect.zw, vec2(1.0));
        float spot = 1.0 - smoothstep(0.06, 0.84, length(dp * vec2(0.60, 1.25)));
        float grazing = clamp(dot(normal, normalize(vec2(-0.50, -0.87))) * 0.5 + 0.5, 0.0, 1.0);
        color = mix(color, accent, edge * grazing * 0.14);
        color += accent * spot * response * (1.0 - roughness * 0.25) * 0.23;
        color *= 1.0 - edge * (1.0 - grazing) * 0.12;
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), v_Color.a * alpha);
}
