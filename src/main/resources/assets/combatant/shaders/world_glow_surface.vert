#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec4 Position;
layout (location = 1) in vec2 UV0;      // face-local 0..1, v runs bottom -> top on walls
layout (location = 2) in vec4 Color;    // rgb = tint, a = fill alpha
layout (location = 3) in vec4 Params;   // xyz = face normal, w = vertical fade (1 bottom -> 0 top)
layout (location = 4) in vec4 Params2;  // x = time seconds, y = phase, z = scan strength, w = rim alpha

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

out vec4 v_Color;
out vec2 v_UV;
out vec3 v_ViewNormal;
out vec3 v_ViewPosition;
out vec4 v_Fx;   // x = vertical fade, y = time, z = phase, w = scan strength
out float v_Rim;

void main() {
    vec4 viewPosition = u_ModelView * Position;
    gl_Position = u_Proj * viewPosition;

    v_Color = Color;
    v_UV = UV0;
    v_ViewNormal = normalize(mat3(u_ModelView) * Params.xyz);
    v_ViewPosition = viewPosition.xyz;
    v_Fx = vec4(Params.w, Params2.x, Params2.y, Params2.z);
    v_Rim = Params2.w;
}
