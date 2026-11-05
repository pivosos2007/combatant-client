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
layout(location = 2) in vec4 v_Color;

void main() {
    vec4 value = texture(u_Texture, v_TexCoord);
    if (value == vec4(0.0)) discard;
    color = vec4(value.rgb * v_Color.rgb * max(value.a, v_Color.a), 1.0);
}
