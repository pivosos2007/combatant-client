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

out vec2 v_TexCoord;
out vec4 v_Color;
out float v_SphericalDistance;
out float v_CylindricalDistance;
out float v_Horizon;

void main() {
    vec4 viewPos = u_ModelView * Position;
    gl_Position = u_Proj * viewPos;

    v_TexCoord = UV0;
    v_Color = Color;

    vec3 vp = viewPos.xyz;
    v_SphericalDistance = length(vp);
    v_CylindricalDistance = length(vp.xz);

    vec3 dir = normalize(vp);
    v_Horizon = 1.0 - smoothstep(0.035, 0.42, abs(dir.y));
}
