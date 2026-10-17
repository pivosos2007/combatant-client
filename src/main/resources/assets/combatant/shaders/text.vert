#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

layout (location = 0) in vec3 Position;
layout (location = 1) in vec2 UV0;
layout (location = 2) in vec4 Color;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

layout(location = 0) out vec2 v_TexCoord;
layout(location = 2) out vec4 v_Color;

void main() {
    gl_Position = u_Proj * u_ModelView * vec4(Position, 1.0);
    v_TexCoord = UV0;
    v_Color = Color;
}
