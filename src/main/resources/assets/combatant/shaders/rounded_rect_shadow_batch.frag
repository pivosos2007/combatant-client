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


vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

void main() {
    vec2 logicalScale = uScreen.zw / uScreen.xy;
    vec2 frag = warpedLocal(v_Local);
    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    float r = min(v_Params.x, min(halfSize.x, halfSize.y));

    vec2 p = frag - center;
    float dBase = roundedBoxSDF(p, halfSize, r);
    float outside = step(0.0, dBase);

    float falloffEdge = max(fwidth(dBase), 0.0001);
    float falloff = 1.0 - smoothstep(0.0, falloffEdge, dBase);

    float absX = abs(p.x);
    float innerX = halfSize.x - r;
    float rawDx = absX - innerX;
    float dx = max(rawDx, 0.0);
    float arc = sqrt(max(r * r - dx * dx, 0.0));
    float cornerBase = v_Rect.y + v_Rect.w - r;
    float cornerEdge = cornerBase + arc;
    float cornerMask = step(0.0, rawDx);
    float bottomEdge = mix(v_Rect.y + v_Rect.w, cornerEdge, cornerMask);

    float down = frag.y - bottomEdge;
    float length = max(v_Params.z, 0.0001);
    float bottomMask = step(0.0, down) * (1.0 - smoothstep(0.0, length, down));
    float widthMask = step(absX, halfSize.x);

    fragColor = vec4(v_Color.rgb, v_Color.a * falloff * outside * bottomMask * widthMask);
}
