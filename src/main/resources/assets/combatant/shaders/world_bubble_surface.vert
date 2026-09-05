#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec4 Position;
layout (location = 1) in vec2 UV0;
layout (location = 2) in vec4 Color;
layout (location = 3) in vec4 Params;   // xyz = unit sphere normal, w = sphere radius
layout (location = 4) in vec4 Params2;  // x = age seconds, y = particle visual phase

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

out vec4 v_Color;
out vec3 v_ViewNormal;
out vec3 v_ViewPosition;
out vec2 v_AgePhase;
out float v_Membrane;

void main() {
    vec3 normal = normalize(Params.xyz);
    float radius = max(Params.w, 0.0001);
    float age = Params2.x;
    float phase = Params2.y;

    // Two low-amplitude, non-axis-aligned waves avoid the rigid CG-sphere look without changing
    // the object's volume enough to destabilize intersections with scene depth.
    float waveA = sin(normal.y * 5.7 + normal.x * 2.3 + age * 1.55 + phase);
    float waveB = sin(normal.z * 6.9 - normal.y * 3.1 - age * 1.18 + phase * 1.37);
    float membrane = waveA * 0.0075 + waveB * 0.0055;

    vec3 deformedPosition = Position.xyz + normal * (radius * membrane);
    vec4 viewPosition = u_ModelView * vec4(deformedPosition, 1.0);

    gl_Position = u_Proj * viewPosition;

    v_Color = Color;
    v_ViewNormal = normalize(mat3(u_ModelView) * normal);
    v_ViewPosition = viewPosition.xyz;
    v_AgePhase = vec2(age, phase);
    v_Membrane = membrane;
}
