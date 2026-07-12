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
in vec4 v_CornerModes;   // TL, TR, BR, BL: 0 square, 1 round, 2 chamfer, 3 concave, 4 notch
in vec4 v_CornerExtentX; // TL, TR, BR, BL
in vec4 v_CornerExtentY; // TL, TR, BR, BL
in vec4 v_EdgeModes;     // top, right, bottom, left
in vec4 v_EdgeData;      // top notch offset, top notch size, top notch depth, softness/stroke fallback

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

// q is local corner coordinate in [0..extent]. The visible rounded corner is the
// quarter ellipse centered at extent. The old version used a rounded-box quadrant
// distance here, which did not cut the actual outer corner and became visibly wrong
// at large radii / circle-like shapes.
float roundedCornerSdf(vec2 q, vec2 extent) {
    vec2 e = max(extent, vec2(0.0001));
    vec2 p = (q - e) / e;
    return (length(p) - 1.0) * min(e.x, e.y);
}

float chamferCornerSdf(vec2 q, vec2 extent) {
    vec2 e = max(extent, vec2(0.0001));
    return (1.0 - q.x / e.x - q.y / e.y) / length(vec2(1.0 / e.x, 1.0 / e.y));
}

float cornerSdf(vec2 frag, vec2 origin, float mode, vec2 extent, int corner) {
    if (mode < 0.5 || extent.x <= 0.0001 || extent.y <= 0.0001) return -1.0;

    vec2 q;
    if (corner == 0) q = vec2(frag.x - origin.x, frag.y - origin.y);             // TL
    else if (corner == 1) q = vec2(origin.x - frag.x, frag.y - origin.y);        // TR
    else if (corner == 2) q = vec2(origin.x - frag.x, origin.y - frag.y);        // BR
    else q = vec2(frag.x - origin.x, origin.y - frag.y);                        // BL

    if (q.x < 0.0 || q.y < 0.0 || q.x > extent.x || q.y > extent.y) return -1.0;
    if (mode < 1.5) return roundedCornerSdf(q, extent);
    if (mode < 2.5) return chamferCornerSdf(q, extent);
    if (mode < 3.5) return -roundedCornerSdf(q, extent); // inverse/concave corner
    if (mode < 4.5) return chamferCornerSdf(q, extent);
    return -1.0;
}

float topNotchSdf(vec2 frag, vec4 rect, vec4 edgeModes, vec4 edgeData) {
    if (edgeModes.x < 0.5 || edgeData.y <= 0.0001 || edgeData.z <= 0.0001) return -1.0;
    float offset = edgeData.x < 0.0 ? rect.z * 0.5 : edgeData.x;
    float halfW = edgeData.y * 0.5;
    float cx = rect.x + clamp(offset, halfW, max(halfW, rect.z - halfW));
    float y = rect.y;
    if (frag.y < y || frag.y > y + edgeData.z) return -1.0;
    float dx = abs(frag.x - cx) - halfW;
    return max(dx, frag.y - (y + edgeData.z));
}

void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 frag = warpedLocal(v_Local);
    vec2 size = max(v_Rect.zw, vec2(0.0001));
    vec2 halfSize = size * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    vec2 p = abs(frag - center) - halfSize;
    float d = max(p.x, p.y);

    vec2 tl = vec2(v_CornerExtentX.x, v_CornerExtentY.x);
    vec2 tr = vec2(v_CornerExtentX.y, v_CornerExtentY.y);
    vec2 br = vec2(v_CornerExtentX.z, v_CornerExtentY.z);
    vec2 bl = vec2(v_CornerExtentX.w, v_CornerExtentY.w);

    d = max(d, cornerSdf(frag, v_Rect.xy, v_CornerModes.x, tl, 0));
    d = max(d, cornerSdf(frag, vec2(v_Rect.x + v_Rect.z, v_Rect.y), v_CornerModes.y, tr, 1));
    d = max(d, cornerSdf(frag, vec2(v_Rect.x + v_Rect.z, v_Rect.y + v_Rect.w), v_CornerModes.z, br, 2));
    d = max(d, cornerSdf(frag, vec2(v_Rect.x, v_Rect.y + v_Rect.w), v_CornerModes.w, bl, 3));
    d = max(d, topNotchSdf(frag, v_Rect, v_EdgeModes, v_EdgeData));

    float a = crispCoverage(d, analyticAa(d, logicalScale, v_EdgeData.w));
    fragColor = vec4(v_Color.rgb, v_Color.a * a);
}
