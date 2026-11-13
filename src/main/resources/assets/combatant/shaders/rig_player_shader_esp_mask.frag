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
layout(std140) uniform RigPlayerLighting {
    vec4 u_LightCoord;
    vec4 u_EntityTint;
};
layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;

void main() {
    if ((texture(u_Texture, v_TexCoord) * v_Color).a < 0.1) discard;
    color = vec4(u_EntityTint.rgb, 1.0);
}
