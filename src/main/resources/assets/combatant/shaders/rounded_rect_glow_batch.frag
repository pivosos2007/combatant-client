#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 1) in vec4 v_Local;

layout(location = 2) in vec4 v_Color;
layout(location = 3) in vec4 v_Rect;
layout(location = 4) in vec4 v_Params;

layout(location = 0) out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen; // xy = framebuffer size, zw = logical size
    vec4 uLayer;
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

    float radius = clamp(v_Params.x, 0.0, min(halfSize.x, halfSize.y));
    float glow = max(v_Params.z, 0.0001);

    float d = roundedBoxSDF(frag - center, halfSize, radius);
    float dist = max(d, 0.0);
    float outside = step(0.0, d);
    float falloff = clamp(1.0 - (dist / glow), 0.0, 1.0);

    fragColor = vec4(v_Color.rgb, v_Color.a * falloff * outside);
}
