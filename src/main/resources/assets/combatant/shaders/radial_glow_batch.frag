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

float roundedBoxSDF(vec2 p, vec2 halfSize, float r) {
    return length(max(abs(p) - halfSize + r, 0.0)) - r;
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

    float radius = v_Params.x;
    float softness = v_Params.y;
    float glowRadius = v_Params.z;
    float glowSoftness = v_Params.w; // reserved

    float d = roundedBoxSDF(frag - center, halfSize, radius);
    float maskSoft = max(softness, 0.0001);
    float mask = 1.0 - smoothstep(0.0, maskSoft, d);

    float g = max(glowRadius, 0.0001);
    float dist = length(frag - vec2(v_Params2.x, v_Params2.y));
    float falloff = 1.0 - smoothstep(0.0, g, dist);
    falloff = falloff * falloff;

    float alpha = v_Color.a * falloff * mask;
    fragColor = vec4(v_Color.rgb, alpha);
}
