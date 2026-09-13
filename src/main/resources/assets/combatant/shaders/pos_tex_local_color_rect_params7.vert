#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec4 Position;
layout (location = 1) in vec2 UV0;
layout (location = 2) in vec4 Local;
layout (location = 3) in vec4 Color;
layout (location = 4) in vec4 Rect;
layout (location = 5) in vec4 Params;
layout (location = 6) in vec4 Params2;
layout (location = 7) in vec4 Params3;
layout (location = 8) in vec4 Params4;
layout (location = 9) in vec4 Params5;
layout (location = 10) in vec4 Params6;
layout (location = 11) in vec4 Params7;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

out vec2 v_TexCoord;
out vec4 v_Local;
out vec4 v_Color;
out vec4 v_Rect;
out vec4 v_Params;
out vec4 v_Params2;
out vec4 v_Params3;
out vec4 v_Params4;
out vec4 v_Params5;
out vec4 v_Params6;
out vec4 v_Params7;

void main() {
    gl_Position = u_Proj * u_ModelView * Position;
    v_TexCoord = UV0;
    v_Local = Local;
    v_Color = Color;
    v_Rect = Rect;
    v_Params = Params;
    v_Params2 = Params2;
    v_Params3 = Params3;
    v_Params4 = Params4;
    v_Params5 = Params5;
    v_Params6 = Params6;
    v_Params7 = Params7;
}
