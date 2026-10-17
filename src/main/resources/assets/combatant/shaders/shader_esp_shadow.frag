#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) out vec4 color;

uniform sampler2D u_Texture;
uniform sampler2D u_Mask;

layout (std140) uniform ShaderEspBlur {
    vec4 u_TexelRadius; // xy = texel size, z = visual radius, w = reserved sigma
    vec4 u_Direction;   // xy = blur direction
};

layout(location = 0) in vec2 v_TexCoord;

const float CENTER_WEIGHT = 0.2270270270;
const float INNER_WEIGHT = 0.3162162162;
const float OUTER_WEIGHT = 0.0702702703;
const float INNER_OFFSET = 1.3846153846;
const float OUTER_OFFSET = 3.2307692308;

vec4 samplePremultiplied(vec2 uv) {
    vec4 value = texture(u_Texture, uv);
    value.rgb *= value.a;
    return value;
}

void main() {
    vec2 uv = v_TexCoord;

    // Vertical/final pass removes the original model so glow/outline stays outside the silhouette.
    if (abs(u_Direction.x) < 0.0001 && texture(u_Mask, uv).a > 0.0) {
        discard;
    }

    float radius = clamp(u_TexelRadius.z, 0.0, 63.0);
    if (radius <= 0.01) {
        color = texture(u_Texture, uv);
        return;
    }

    float kernelScale = max(radius / 3.2307692308, 0.25);
    vec2 stepUv = u_TexelRadius.xy * u_Direction.xy * kernelScale;

    vec4 accum = samplePremultiplied(uv) * CENTER_WEIGHT;
    accum += (samplePremultiplied(uv - stepUv * INNER_OFFSET)
            + samplePremultiplied(uv + stepUv * INNER_OFFSET)) * INNER_WEIGHT;
    accum += (samplePremultiplied(uv - stepUv * OUTER_OFFSET)
            + samplePremultiplied(uv + stepUv * OUTER_OFFSET)) * OUTER_WEIGHT;

    vec3 rgb = accum.a > 0.0001 ? accum.rgb / accum.a : vec3(0.0);
    color = vec4(rgb, accum.a);
}
