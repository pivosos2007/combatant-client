#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;

in vec2 v_TexCoord;
in vec4 v_Color;
in vec3 v_Normal;

void main() {
    vec4 sampled = texture(u_Texture, v_TexCoord) * v_Color;
    if (sampled.a <= (0.5 / 255.0)) discard;
    color = sampled;
}
