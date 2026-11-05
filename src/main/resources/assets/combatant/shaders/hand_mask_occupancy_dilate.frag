#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) out vec4 color;

uniform sampler2D u_Mask;

layout (std140) uniform HandMetallic {
    vec4 u_Base;
    vec4 u_Highlight;
    vec4 u_Glow;
    vec4 u_Shadow;
    vec4 u_Params0;
    vec4 u_Params1;
    vec4 u_Params2;
    vec4 u_Culling;
};

layout(location = 0) in vec2 v_TexCoord;

const float CELL_SIZE = 8.0;
const int MAX_CELL_RADIUS = 5;

void main() {
    ivec2 size = max(textureSize(u_Mask, 0), ivec2(1));
    ivec2 center = clamp(ivec2(floor(v_TexCoord * vec2(size))), ivec2(0), size - ivec2(1));
    int radius = clamp(int(ceil(max(u_Params2.z, 1.0) / CELL_SIZE)), 1, MAX_CELL_RADIUS);
    float occupied = 0.0;

    for (int y = -MAX_CELL_RADIUS; y <= MAX_CELL_RADIUS; ++y) {
        if (abs(y) > radius) continue;
        for (int x = -MAX_CELL_RADIUS; x <= MAX_CELL_RADIUS; ++x) {
            if (abs(x) > radius) continue;
            ivec2 samplePixel = clamp(center + ivec2(x, y), ivec2(0), size - ivec2(1));
            occupied = max(occupied, texelFetch(u_Mask, samplePixel, 0).r);
        }
    }

    color = vec4(occupied, occupied, occupied, 1.0);
}
