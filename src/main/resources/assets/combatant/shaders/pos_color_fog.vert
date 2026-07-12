#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec4 Position;
layout (location = 1) in vec4 Color;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

layout (std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

out vec4 v_Color;
out float v_SphericalDistance;
out float v_CylindricalDistance;

void main() {
    vec4 viewPos = u_ModelView * Position;
    gl_Position = u_Proj * viewPos;

    v_Color = Color;

    vec3 vp = viewPos.xyz;
    v_SphericalDistance = length(vp);
    float distXZ = length(vp.xz);
    float distY = abs(vp.y);
    v_CylindricalDistance = max(distXZ, distY);
}
