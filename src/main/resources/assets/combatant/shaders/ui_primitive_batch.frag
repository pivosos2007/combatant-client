#version 330 core
#extension GL_ARB_separate_shader_objects : require

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

layout(location = 1) in vec4 v_Local;
layout(location = 2) in vec4 v_Color;
layout(location = 3) in vec4 v_Rect;
layout(location = 4) in vec4 v_Params;
layout(location = 5) in vec4 v_Params2;
layout(location = 6) in vec4 v_Params3;
layout(location = 7) in vec4 v_Params4;
layout(location = 8) in vec4 v_Params5;

layout(location = 0) out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

#include <combatant:ui_aa.glsl>
#include <combatant:ui_stroke.glsl>

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

float squircleSdfLocal(vec2 p, vec4 rect, float exponent) {
    vec2 center = rect.xy + rect.zw * 0.5;
    vec2 halfSize = max(rect.zw * 0.5, vec2(0.0001));
    float n = clamp(exponent, 2.0, 16.0);
    vec2 q = abs(p - center) / halfSize;
    vec2 qn = pow(q, vec2(n));
    float implicit = qn.x + qn.y - 1.0;
    vec2 gradient = n * vec2(
        pow(max(q.x, 0.000001), n - 1.0) / halfSize.x,
        pow(max(q.y, 0.000001), n - 1.0) / halfSize.y
    );
    float radial = (pow(max(qn.x + qn.y, 0.000001), 1.0 / n) - 1.0) * min(halfSize.x, halfSize.y);
    return length(gradient) > 0.00001 ? implicit / length(gradient) : radial;
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

float smoothSquircleUnionSdf(vec2 p, float smoothing) {
    float first = squircleSdfLocal(p, v_Params, max(v_Params3.x, 2.0));
    float second = squircleSdfLocal(p, v_Params2, max(v_Params3.y, 2.0));
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
    } else if (mode == 3) {
        d = smoothSquircleUnionSdf(local, rounding);
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
