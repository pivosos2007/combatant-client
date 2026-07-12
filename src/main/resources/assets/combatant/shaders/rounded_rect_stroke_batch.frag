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
in vec4 v_Params;

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
};

float roundedBoxSDF(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float pixelAa(vec2 logicalScale) {
    return max(max(logicalScale.x, logicalScale.y), 0.0001);
}

float analyticAa(float d, vec2 logicalScale) {
    return max(pixelAa(logicalScale), max(fwidth(d) * 0.75, 0.0001));
}

float crispCoverage(float d, float aa) {
    return clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
}

float crispCoverageSoft(float d, float aa, float softness) {
    return crispCoverage(d, aa + max(softness, 0.0));
}

float thinStrokeFloor(float thickness, float aa) {
    float px = max(aa, 0.0001);
    float minStroke = px * 1.18;
    return max(thickness, minStroke);
}

float strokeCoverage(float d, float aa, float thickness) {
    float t = thinStrokeFloor(thickness, aa);
    float outer = crispCoverage(d, aa);
    float inner = crispCoverage(d + t, aa);
    float a = clamp(outer - inner, 0.0, 1.0);
    float boost = thickness < t ? mix(1.0, 1.18, clamp((t - thickness) / max(t, 0.0001), 0.0, 1.0)) : 1.0;
    return clamp(a * boost, 0.0, 1.0);
}

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 frag = warpedLocal(v_Local);

    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;

    float radius = clamp(v_Params.x, 0.0, min(halfSize.x, halfSize.y));
    float softness = max(v_Params.y, 0.0);
    float thickness = max(0.0, v_Params.z);

    float d = roundedBoxSDF(frag - center, halfSize, radius);
    float aa = analyticAa(d, logicalScale) + softness;
    float stroke = min(thickness, min(halfSize.x, halfSize.y));

    float a = strokeCoverage(d, aa, stroke);

    fragColor = vec4(v_Color.rgb, v_Color.a * a);
}
