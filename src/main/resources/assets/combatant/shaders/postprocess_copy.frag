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
layout(location = 0) in vec2 v_TexCoord;

void main() {
    color = vec4(texture(u_Texture, v_TexCoord).rgb, 1.0);
}
