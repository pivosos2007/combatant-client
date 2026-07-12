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
    vec4 base = texture(u_Texture, v_TexCoord);
    vec3 col = base.rgb;

    float desat = clamp(u_Params.y, 0.0, 1.0);
    float contrast = clamp(u_Params.z, 0.0, 1.0);
    float strength = clamp(u_Params.x, 0.0, 2.0);

    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(col, vec3(luma), desat);

    col = (col - 0.5) * (1.0 + contrast) + 0.5;
    col = clamp(col, 0.0, 1.0);

    float dist = distance(v_TexCoord, vec2(0.5));
    float edge = smoothstep(0.35, 0.9, dist);
    float vignette = clamp(edge * strength, 0.0, 1.0);

    vec3 tint = vec3(0.35, 0.55, 1.0);
    col = mix(col, col * tint, vignette);
    col *= (1.0 - vignette * 0.18);
    col *= (1.0 - strength * 0.22);

    color = vec4(col, 1.0);
}
