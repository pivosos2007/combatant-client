#version 330 core

out vec4 color;

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

in vec2 v_TexCoord;

const float CELL_SIZE = 8.0;
const int MAX_CELL_RADIUS = 5;

void main() {
    ivec2 size = max(textureSize(u_Mask, 0), ivec2(1));
    ivec2 center = clamp(ivec2(floor(v_TexCoord * vec2(size))), ivec2(0), size - ivec2(1));
    int radius = clamp(int(ceil(max(u_Params2.z, 1.0) / CELL_SIZE)), 1, MAX_CELL_RADIUS);
    float occupied = 0.0;

    // Square dilation is intentionally conservative. It may retain a few extra fragments near
    // corners, but it can never discard a fragment covered by the original radial edge search.
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
