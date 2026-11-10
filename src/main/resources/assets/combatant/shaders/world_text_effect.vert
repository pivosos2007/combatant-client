#version 330 core
#extension GL_ARB_separate_shader_objects : require
/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec4 Params;
layout(location = 4) in vec4 Params2;
layout(std140) uniform MeshData { mat4 u_Proj; mat4 u_ModelView; };
layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) flat out vec4 v_Bounds;
layout(location = 2) out vec4 v_Color;
layout(location = 3) flat out vec4 v_Outline;
void main() {
    gl_Position = u_Proj * u_ModelView * vec4(Position, 1.0);
    v_TexCoord = UV0;
    v_Bounds = Params;
    v_Color = Color;
    v_Outline = Params2;
}
