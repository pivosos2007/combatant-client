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
in vec4 v_Params2;

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
};

float chamferedBoxSDF(vec2 p, vec2 halfSize, vec4 chamfer) {
    vec2 q = abs(p);
    float d = max(q.x - halfSize.x, q.y - halfSize.y);

    float tl = (-p.x - p.y - halfSize.x - halfSize.y + chamfer.x) * 0.70710678;
    float tr = ( p.x - p.y - halfSize.x - halfSize.y + chamfer.y) * 0.70710678;
    float br = ( p.x + p.y - halfSize.x - halfSize.y + chamfer.z) * 0.70710678;
    float bl = (-p.x + p.y - halfSize.x - halfSize.y + chamfer.w) * 0.70710678;

    return max(d, max(max(tl, tr), max(br, bl)));
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


vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

void main() {
    vec2 logicalScale = uScreen.zw / uScreen.xy;
    vec2 frag = warpedLocal(v_Local);

    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    vec4 chamfer = clamp(v_Params, vec4(0.0), vec4(min(halfSize.x, halfSize.y)));
    float thickness = max(0.0, v_Params2.x);

    float d = chamferedBoxSDF(frag - center, halfSize, chamfer);
    vec2 innerHalf = max(vec2(0.0), halfSize - vec2(thickness));
    vec4 innerChamfer = max(vec4(0.0), chamfer - vec4(thickness));
    float innerD = chamferedBoxSDF(frag - center, innerHalf, innerChamfer);

    float edge = analyticAa(d, logicalScale);
    float outer = crispCoverage(d, edge);
    float inner = crispCoverage(innerD, edge);
    float a = clamp(outer - inner, 0.0, 1.0);

    fragColor = vec4(v_Color.rgb, v_Color.a * a);
}
