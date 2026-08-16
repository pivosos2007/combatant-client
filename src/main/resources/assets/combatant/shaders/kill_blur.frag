#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform PostProcess {
    vec4 u_Params;
};

in vec2 v_TexCoord;

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

    vec3 sum = texture(u_Texture, v_TexCoord).rgb * 0.20;
    sum += texture(u_Texture, clamp(v_TexCoord + dx, 0.0, 1.0)).rgb * 0.10;
    sum += texture(u_Texture, clamp(v_TexCoord - dx, 0.0, 1.0)).rgb * 0.10;
    sum += texture(u_Texture, clamp(v_TexCoord + dy, 0.0, 1.0)).rgb * 0.10;
    sum += texture(u_Texture, clamp(v_TexCoord - dy, 0.0, 1.0)).rgb * 0.10;
    sum += texture(u_Texture, clamp(v_TexCoord + dd, 0.0, 1.0)).rgb * 0.075;
    sum += texture(u_Texture, clamp(v_TexCoord - dd, 0.0, 1.0)).rgb * 0.075;
    sum += texture(u_Texture, clamp(v_TexCoord + vec2(dd.x, -dd.y), 0.0, 1.0)).rgb * 0.075;
    sum += texture(u_Texture, clamp(v_TexCoord + vec2(-dd.x, dd.y), 0.0, 1.0)).rgb * 0.075;
    sum += texture(u_Texture, clamp(v_TexCoord + dx * 2.0, 0.0, 1.0)).rgb * 0.025;
    sum += texture(u_Texture, clamp(v_TexCoord - dx * 2.0, 0.0, 1.0)).rgb * 0.025;
    sum += texture(u_Texture, clamp(v_TexCoord + dy * 2.0, 0.0, 1.0)).rgb * 0.025;
    sum += texture(u_Texture, clamp(v_TexCoord - dy * 2.0, 0.0, 1.0)).rgb * 0.025;

    vec3 base = texture(u_Texture, v_TexCoord).rgb;
    color = vec4(mix(base, sum, smoothstep(0.0, 1.0, strength)), 1.0);
}
