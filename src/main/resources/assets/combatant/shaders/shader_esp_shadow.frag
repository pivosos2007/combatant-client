#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;
uniform sampler2D u_Mask;

layout (std140) uniform ShaderEspBlur {
    vec4 u_TexelRadius; // xy = texel size, z = radius, w = sigma
    vec4 u_Direction;   // xy = blur direction
};

in vec2 v_TexCoord;

float gaussian(float x, float sigma) {
    float s = max(sigma, 0.5);
    return exp(-(x * x) / (2.0 * s * s));
}

void main() {
    vec2 uv = v_TexCoord;

    if (abs(u_Direction.x) < 0.0001 && texture(u_Mask, uv).a > 0.0) {
        discard;
    }

    float radius = clamp(u_TexelRadius.z, 0.0, 63.0);
    int iradius = int(radius + 0.5);
    vec2 stepUv = u_TexelRadius.xy * u_Direction.xy;

    vec4 pixelColor = texture(u_Texture, uv);
    pixelColor.rgb *= pixelColor.a;

    float weight = gaussian(0.0, u_TexelRadius.w);
    vec4 accum = pixelColor * weight;
    float weightSum = weight;

    for (int i = 1; i < 64; i++) {
        if (i > iradius) {
            break;
        }

        vec2 offset = float(i) * stepUv;
        vec4 left = texture(u_Texture, uv - offset);
        vec4 right = texture(u_Texture, uv + offset);
        left.rgb *= left.a;
        right.rgb *= right.a;

        weight = gaussian(float(i), u_TexelRadius.w);
        accum += (left + right) * weight;
        weightSum += weight * 2.0;
    }

    vec4 blurred = accum / max(weightSum, 0.0001);
    vec3 rgb = blurred.a > 0.0001 ? blurred.rgb / blurred.a : vec3(0.0);
    color = vec4(rgb, blurred.a);
}
