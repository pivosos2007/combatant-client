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
in vec4 v_Params; // kind, shape parameter, stroke width, flags

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
};

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

float pixelAa(vec2 logicalScale) {
    return max(max(logicalScale.x, logicalScale.y), 0.0001);
}

float analyticAa(float d, vec2 logicalScale, float softness) {
    return max(pixelAa(logicalScale), max(fwidth(d) * 0.75, 0.0001)) + max(0.0, softness);
}

float crispCoverage(float d, float aa) {
    return clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
}

// Whole-bounds superellipse. The implicit-gradient normalization gives a
// stable pixel-distance approximation at the boundary, including wide/tall
// boxes where four local corner arcs cannot reproduce the same silhouette.
float squircleSdf(vec2 p, vec2 halfSize, float exponent) {
    vec2 h = max(halfSize, vec2(0.0001));
    float n = clamp(exponent, 2.0, 16.0);
    vec2 q = abs(p) / h;
    vec2 qn = pow(q, vec2(n));
    float implicit = qn.x + qn.y - 1.0;
    vec2 gradient = n * vec2(
        pow(max(q.x, 0.000001), n - 1.0) / h.x,
        pow(max(q.y, 0.000001), n - 1.0) / h.y
    );
    float radial = (pow(max(qn.x + qn.y, 0.000001), 1.0 / n) - 1.0) * min(h.x, h.y);
    return length(gradient) > 0.00001 ? implicit / length(gradient) : radial;
}

float roundedBoxSdf(vec2 p, vec2 halfSize, float radius) {
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

// q is local corner coordinate in [0..extent]. The visible rounded corner is the
// quarter ellipse centered at extent. The old version used a rounded-box quadrant
// distance here, which did not cut the actual outer corner and became visibly wrong
// at large radii / circle-like shapes.
void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 frag = warpedLocal(v_Local);
    vec2 size = max(v_Rect.zw, vec2(0.0001));
    vec2 halfSize = size * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    float d = v_Params.x < 1.5
            ? roundedBoxSdf(frag - center, halfSize, v_Params.y)
            : squircleSdf(frag - center, halfSize, v_Params.y);
    float strokeWidth = max(0.0, v_Params.z);
    int flags = int(v_Params.w + 0.5);
    bool fill = (flags & 1) != 0;
    bool innerStroke = (flags & 2) != 0;
    if (!fill && strokeWidth > 0.0) {
        d = innerStroke
                ? abs(d + strokeWidth * 0.5) - strokeWidth * 0.5
                : abs(d) - strokeWidth * 0.5;
    }
    float a = crispCoverage(d, analyticAa(d, logicalScale, 0.0));
    fragColor = vec4(v_Color.rgb, v_Color.a * a);
}
