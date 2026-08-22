#version 330 core

out vec4 color;

uniform sampler2D u_Mask;

in vec2 v_TexCoord;

const int CELL_SIZE = 8;

float maskAt(ivec2 pixel, ivec2 size) {
    vec4 mask = texelFetch(u_Mask, clamp(pixel, ivec2(0), size - ivec2(1)), 0);
    return max(mask.a, max(mask.r, max(mask.g, mask.b)));
}

void main() {
    ivec2 sourceSize = max(textureSize(u_Mask, 0), ivec2(1));
    ivec2 occupancySize = (sourceSize + ivec2(CELL_SIZE - 1)) / CELL_SIZE;
    ivec2 cell = clamp(ivec2(floor(v_TexCoord * vec2(occupancySize))),
                       ivec2(0), occupancySize - ivec2(1));
    ivec2 firstPixel = cell * CELL_SIZE;
    float occupied = 0.0;

    for (int y = 0; y < CELL_SIZE; ++y) {
        for (int x = 0; x < CELL_SIZE; ++x) {
            occupied = max(occupied, maskAt(firstPixel + ivec2(x, y), sourceSize));
        }
    }

    color = vec4(occupied, occupied, occupied, 1.0);
}
