#version 330 core

#moj_import <combatant:ui_aa.glsl>
#moj_import <combatant:ui_stroke.glsl>

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

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

const float TAU = 6.283185307179586;

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

float normalizeAngle(float angleDeg) {
    float outAngle = mod(angleDeg, 360.0);
    return outAngle < 0.0 ? outAngle + 360.0 : outAngle;
}

float cyclicMix(float value) {
    float wrapped = fract(value);
    return wrapped < 0.5 ? wrapped * 2.0 : (1.0 - wrapped) * 2.0;
}

vec3 hsb2rgb(vec3 c) {
    vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    rgb = rgb * rgb * (3.0 - 2.0 * rgb);
    return c.z * mix(vec3(1.0), rgb, c.y);
}

vec3 paletteColor(int mode, float t, vec3 primary, vec3 secondary) {
    float p = fract(t);
    if (mode == 1) {
        return hsb2rgb(vec3(p, 1.0, 1.0));
    }
    if (mode == 2) {
        return hsb2rgb(vec3(p, 0.58, 1.0));
    }
    if (mode == 3) {
        float hue = p < 0.5 ? -p : p;
        return hsb2rgb(vec3(hue, 0.50, 1.0));
    }
    if (mode == 4) {
        float v = 0.38 + 0.62 * cyclicMix(p);
        return primary * v;
    }
    if (mode == 5 || mode == 6 || mode == 7) {
        return mix(primary, secondary, cyclicMix(p));
    }
    return primary;
}

float circularDistance(float a, float b) {
    return abs(fract(a - b + 0.5) - 0.5);
}

float arcMask(float angleDeg, float startDeg, float sweepDeg, float softDeg) {
    if (sweepDeg <= 0.001) {
        return 0.0;
    }
    if (sweepDeg >= 359.99) {
        return 1.0;
    }

    float start = normalizeAngle(startDeg);
    float angle = normalizeAngle(angleDeg);
    if (angle < start) {
        angle += 360.0;
    }

    float end = start + clamp(sweepDeg, 0.0, 360.0);
    float soft = max(softDeg, 0.001);
    float startMask = smoothstep(start - soft, start, angle);
    float endMask = 1.0 - smoothstep(end, end + soft, angle);
    return startMask * endMask;
}

vec2 arcPoint(vec2 center, float radius, float angleDeg) {
    float rad = radians(angleDeg);
    return center + vec2(sin(rad), -cos(rad)) * radius;
}

void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 frag = warpedLocal(v_Local);

    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    vec2 local = frag - center;

    float radius = max(v_Params.x, 0.0001);
    float softness = max(v_Params.y, 0.0);
    float thickness = max(v_Params.z, 0.0001);
    float startDeg = normalizeAngle(v_Params.w);

    float sweepDeg = clamp(v_Params2.x, 0.0, 360.0);
    float glowRadius = max(v_Params2.y, 0.0);
    float glowStrength = max(v_Params2.z, 0.0);
    int colorMode = int(floor(v_Params2.w + 0.5));

    vec4 secondary = v_Params3;
    float angularOffset = v_Params4.x;
    float angularCycles = max(v_Params4.y, 0.001);
    float requestedSoftDeg = max(v_Params4.z, 0.0);
    float capsEnabled = v_Params4.w;

    float trackAlpha = clamp(v_Params5.x, 0.0, 1.0);
    float bodyAlpha = clamp(v_Params5.y, 0.0, 1.0);
    float headBoost = max(v_Params5.z, 0.0);
    float trackGlow = clamp(v_Params5.w, 0.0, 1.0);

    float dist = length(local);
    float radialD = abs(dist - radius) - thickness * 0.5;
    float radialAa = strokeAnalyticAa(dist - radius, logicalScale, softness);
    float stroke = centeredStrokeCoverage(dist - radius, thickness, logicalScale, softness);

    float glowFalloff = 0.0;
    if (glowRadius > 0.001) {
        float glowD = max(radialD, 0.0);
        glowFalloff = 1.0 - smoothstep(0.0, glowRadius, glowD);
        glowFalloff *= glowFalloff;
    }

    float angleDeg = normalizeAngle(degrees(atan(local.x, -local.y)));
    float softDeg = requestedSoftDeg > 0.001
            ? requestedSoftDeg
            : max(degrees((softness + radialAa) / max(radius, 0.0001)), 0.18);
    float arcFill = arcMask(angleDeg, startDeg, sweepDeg, softDeg);
    float strokeFill = arcFill;
    float glowFill = arcFill;

    if (capsEnabled > 0.5 && sweepDeg > 0.001 && sweepDeg < 359.99) {
        float capRadius = thickness * 0.5;
        float capAa = max(max(softness, fwidth(capRadius)), 0.0001);
        float startCapD = length(frag - arcPoint(center, radius, startDeg)) - capRadius;
        float endCapD = length(frag - arcPoint(center, radius, startDeg + sweepDeg)) - capRadius;
        float startCap = 1.0 - smoothstep(0.0, capAa, startCapD);
        float endCap = 1.0 - smoothstep(0.0, capAa, endCapD);
        float capGlowAa = max(glowRadius + capAa, capAa);
        float startCapGlow = 1.0 - smoothstep(0.0, capGlowAa, startCapD);
        float endCapGlow = 1.0 - smoothstep(0.0, capGlowAa, endCapD);
        strokeFill = max(strokeFill, max(startCap, endCap));
        glowFill = max(glowFill, max(startCapGlow, endCapGlow));
    }

    float paletteT = angleDeg / 360.0 * angularCycles + angularOffset;
    vec3 primary = v_Color.rgb;
    vec3 arcColor = paletteColor(colorMode, paletteT, primary, secondary.rgb);
    vec3 trackColor = mix(arcColor * 0.34, vec3(0.0), 0.18);

    float head = 0.0;
    if (sweepDeg > 0.001 && sweepDeg < 359.99) {
        float headPhase = fract(normalizeAngle(startDeg + sweepDeg) / 360.0);
        head = 1.0 - smoothstep(0.0, 0.035, circularDistance(angleDeg / 360.0, headPhase));
        head *= strokeFill;
    }

    vec3 hotColor = mix(arcColor, vec3(1.0), clamp(headBoost, 0.0, 1.0));
    vec3 bodyColor = mix(arcColor, hotColor, head);

    float track = stroke * trackAlpha * v_Color.a;
    float body = stroke * strokeFill * bodyAlpha * max(v_Color.a, secondary.a);
    float glow = glowFalloff * glowStrength * v_Color.a * (trackAlpha * trackGlow + glowFill * bodyAlpha);

    float outAlpha = clamp(max(max(track, body), glow), 0.0, 1.0);
    vec3 outColor = mix(trackColor, bodyColor, clamp((body + glow * 0.65) / max(outAlpha, 0.0001), 0.0, 1.0));

    fragColor = vec4(outColor, outAlpha);
}
