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
};

float cornerCut(float u, float v, float cutX, float cutY) {
    if (cutX <= 0.0001 || cutY <= 0.0001) {
        return -1.0;
    }
    return (1.0 - u / cutX - v / cutY) / length(vec2(1.0 / cutX, 1.0 / cutY));
}

float chamferedBoxSDF(vec2 p, vec2 halfSize, vec4 chamferX, vec4 chamferY) {
    vec2 q = abs(p);
    float d = max(q.x - halfSize.x, q.y - halfSize.y);

    float left = p.x + halfSize.x;
    float right = halfSize.x - p.x;
    float top = p.y + halfSize.y;
    float bottom = halfSize.y - p.y;

    float tl = cornerCut(left, top, chamferX.x, chamferY.x);
    float tr = cornerCut(right, top, chamferX.y, chamferY.y);
    float br = cornerCut(right, bottom, chamferX.z, chamferY.z);
    float bl = cornerCut(left, bottom, chamferX.w, chamferY.w);

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
    vec4 chamferX = clamp(v_Params, vec4(0.0), vec4(v_Rect.z));
    vec4 chamferY = clamp(v_Params2, vec4(0.0), vec4(v_Rect.w));

    float d = chamferedBoxSDF(frag - center, halfSize, chamferX, chamferY);
    float edge = analyticAa(d, logicalScale);
    float a = crispCoverage(d, edge);

    fragColor = vec4(v_Color.rgb, v_Color.a * a);
}
