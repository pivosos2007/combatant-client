#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* PlayerModelCompiler produces deformation-free vertices bound to at most two bones.
 * Keep the shared rig vertex ABI, but do not execute the generic ribbon/deform shader
 * or fetch two unused skin matrices for every vertex of high-poly player models. */
#define MAX_RIG_BONES 64
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec3 Normal;
layout(location = 3) in vec4 Color;
layout(location = 4) in uvec4 BoneIndices;
layout(location = 5) in vec4 BoneWeights;

layout(std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};
layout(std140) uniform RigBones {
    mat4 u_SkinMatrices[MAX_RIG_BONES];
};
#ifdef COMBATANT_WORLD_FOG
#include <minecraft:fog.glsl>
layout(location = 6) out vec2 v_FogDistances;
#endif

layout(location = 0) out vec2 v_TexCoord;
layout(location = 2) out vec4 v_Color;
layout(location = 4) out vec3 v_Normal;

void main() {
    mat4 skin = u_SkinMatrices[int(BoneIndices.x)];
    if (BoneWeights.y > 0.0) {
        skin = skin * BoneWeights.x
             + u_SkinMatrices[int(BoneIndices.y)] * BoneWeights.y;
    }
    vec4 skinned = skin * vec4(Position, 1.0);
    vec4 viewPosition = u_ModelView * skinned;
    gl_Position = u_Proj * viewPosition;
#ifdef COMBATANT_WORLD_FOG
    v_FogDistances = vec2(fog_spherical_distance(viewPosition.xyz),
                          fog_cylindrical_distance(viewPosition.xyz));
#endif
    v_TexCoord = UV0;
    v_Color = Color;
    // Lighting.Light*_Direction lives in view space. Skinning alone leaves
    // normals in camera-relative model space, so rotating the view (or entering
    // a GUI that switches render paths) otherwise changes the apparent lighting.
    // Non-uniform model fitting stretches triangle normals unless the inverse
    // transpose is applied. The skeletal pose contributes primarily rotations.
    mat3 normalView = transpose(inverse(mat3(u_ModelView)));
    v_Normal = normalize(normalView * (mat3(skin) * Normal));
}
