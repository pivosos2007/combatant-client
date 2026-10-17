#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec2 Position;
layout (location = 1) in vec4 Local;
layout (location = 2) in vec4 Color;
layout (location = 3) in vec4 Rect;
layout (location = 4) in vec4 Params;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

layout(location = 1) out vec4 v_Local;
layout(location = 2) out vec4 v_Color;
layout(location = 3) out vec4 v_Rect;
layout(location = 4) out vec4 v_Params;

void main() {
    gl_Position = u_Proj * u_ModelView * vec4(Position, 0.0, 1.0);
    v_Local = Local;
    v_Color = Color;
    v_Rect = Rect;
    v_Params = Params;
}
