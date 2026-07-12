#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec2 Position;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

out vec2 v_TexCoord;

void main() {
    vec4 p = vec4(Position, 0.0, 1.0);
    gl_Position = u_Proj * u_ModelView * p;
    v_TexCoord = Position * 0.5 + 0.5;
}
