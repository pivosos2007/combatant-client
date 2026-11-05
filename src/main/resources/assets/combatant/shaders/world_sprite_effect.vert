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
layout(location = 3) in vec4 Params; // precomputed animation parameters, intensity, signed variant

layout(std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) out vec4 v_Params;
layout(location = 3) out vec3 v_ViewPosition;
layout(location = 4) out vec3 v_ViewNormal;

void main() {
    vec4 view = u_ModelView * vec4(Position, 1.0);
    gl_Position = u_Proj * view;
    v_TexCoord = UV0;
    v_Color = Color;
    v_Params = Params;
    v_ViewPosition = view.xyz;
    // Negative variant IDs encode camera-facing billboards without an extra vertex field.
    v_ViewNormal = Params.w < -0.5 ? vec3(0.0, 0.0, 1.0)
            : normalize(mat3(u_ModelView) * vec3(0.0, 1.0, 0.0));
}
