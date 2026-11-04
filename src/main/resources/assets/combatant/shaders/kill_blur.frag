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

layout (std140) uniform PostProcess {
    vec4 u_Params;
};

layout(location = 0) in vec2 v_TexCoord;

void main() {
    float strength = clamp(u_Params.x, 0.0, 1.0);
    if (strength <= 0.0001) {
        color = vec4(texture(u_Texture, v_TexCoord).rgb, 1.0);
        return;
    }

    vec2 texel = 1.0 / vec2(textureSize(u_Texture, 0));
    float radius = 0.65 + 4.35 * strength;
    vec2 dx = vec2(texel.x * radius, 0.0);
    vec2 dy = vec2(0.0, texel.y * radius);
    vec2 dd = vec2(texel.x * radius * 0.72, texel.y * radius * 0.72);

    // Nine taps, one pass. Fine structure remains legible behind tools and windows.
    vec3 sum = texture(u_Texture, v_TexCoord).rgb * 0.24;
    sum += texture(u_Texture, clamp(v_TexCoord + dx, 0.0, 1.0)).rgb * 0.12;
    sum += texture(u_Texture, clamp(v_TexCoord - dx, 0.0, 1.0)).rgb * 0.12;
    sum += texture(u_Texture, clamp(v_TexCoord + dy, 0.0, 1.0)).rgb * 0.12;
    sum += texture(u_Texture, clamp(v_TexCoord - dy, 0.0, 1.0)).rgb * 0.12;
    sum += texture(u_Texture, clamp(v_TexCoord + dd, 0.0, 1.0)).rgb * 0.07;
    sum += texture(u_Texture, clamp(v_TexCoord - dd, 0.0, 1.0)).rgb * 0.07;
    sum += texture(u_Texture, clamp(v_TexCoord + vec2(dd.x, -dd.y), 0.0, 1.0)).rgb * 0.07;
    sum += texture(u_Texture, clamp(v_TexCoord + vec2(-dd.x, dd.y), 0.0, 1.0)).rgb * 0.07;

    vec3 base = texture(u_Texture, v_TexCoord).rgb;
    color = vec4(mix(base, sum, smoothstep(0.0, 1.0, strength)), 1.0);
}
