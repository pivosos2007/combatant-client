#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 *
 * Analytic convex-panel primitive. Up to eight clockwise points are supplied
 * as local pixel coordinates. One rounding value softens the complete topology,
 * so presets and warped panels do not need per-corner shader variants.
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
    vec4 uScreen;
    vec4 uLayer;
};

#moj_import <combatant:ui_aa.glsl>
#moj_import <combatant:ui_stroke.glsl>

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

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

float smoothMinimum(float a, float b, float radius) {
    if (radius <= 0.0001) return min(a, b);
    float h = clamp(0.5 + 0.5 * (b - a) / radius, 0.0, 1.0);
    return mix(b, a, h) - radius * h * (1.0 - h);
}

vec4 compoundSource(int index) {
    if (index == 0) return v_Params;
    if (index == 1) return v_Params2;
    if (index == 2) return v_Params3;
    return v_Params4;
}

float circleSdf(vec2 p, vec3 source) {
    return length(p - source.xy) - max(source.z, 0.0);
}

float roundedRectSdfLocal(vec2 p, vec4 rect, float radius) {
    vec2 center = rect.xy + rect.zw * 0.5;
    vec2 halfSize = max(rect.zw * 0.5, vec2(0.0001));
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p - center) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float islandBlobSdf(vec2 p, int count, float smoothing) {
    float d = circleSdf(p, compoundSource(0).xyz);
    for (int i = 1; i < 4; i++) {
        if (i >= count) break;
        d = smoothMinimum(d, circleSdf(p, compoundSource(i).xyz), smoothing);
    }
    return d;
}

float smoothBoxUnionSdf(vec2 p, float smoothing) {
    float first = roundedRectSdfLocal(p, v_Params, max(v_Params3.x, 0.0));
    float second = roundedRectSdfLocal(p, v_Params2, max(v_Params3.y, 0.0));
    return smoothMinimum(first, second, smoothing);
}

float convexPrimitiveSdf(vec2 p, int count, float rounding) {
    float d = -1.0e20;
    for (int i = 0; i < 8; i++) {
        if (i >= count) break;
        int next = i + 1;
        if (next >= count) next = 0;
        vec2 a = primitivePoint(i);
        vec2 b = primitivePoint(next);
        vec2 edge = b - a;
        float lengthEdge = max(length(edge), 0.0001);
        // Points are clockwise in top-left UI coordinates. Interior is the
        // positive cross-product half-plane, hence negative signed distance.
        float edgeDistance = -(edge.x * (p.y - a.y) - edge.y * (p.x - a.x)) / lengthEdge;
        d = i == 0 ? edgeDistance : smoothMaximum(d, edgeDistance, rounding);
    }
    return d;
}

void main() {
    int packedFlags = int(floor(v_Params5.w + 0.5));
    int mode = packedFlags / 16;
    int flags = packedFlags - mode * 16;
    int count = mode == 0
            ? int(clamp(floor(v_Params5.x + 0.5), 3.0, 8.0))
            : int(clamp(floor(v_Params5.x + 0.5), 1.0, 4.0));
    float rounding = max(0.0, v_Params5.y);
    float strokeWidth = max(0.0, v_Params5.z);
    bool fill = (flags & 1) != 0;
    bool innerStroke = (flags & 2) != 0;

    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 local = warpedLocal(v_Local) - v_Rect.xy;
    float d;
    if (mode == 1) {
        d = islandBlobSdf(local, count, rounding);
    } else if (mode == 2) {
        d = smoothBoxUnionSdf(local, rounding);
    } else {
        d = convexPrimitiveSdf(local, count, rounding);
    }

    float alpha;
    if (!fill && strokeWidth > 0.0) {
        alpha = innerStroke
                ? innerStrokeCoverage(d, strokeWidth, logicalScale, 0.0)
                : centeredStrokeCoverage(d, strokeWidth, logicalScale, 0.0);
    } else {
        alpha = coverage(d, analyticAa(d, logicalScale));
    }
    if (alpha <= 0.001) discard;
    fragColor = vec4(v_Color.rgb, v_Color.a * alpha);
}
