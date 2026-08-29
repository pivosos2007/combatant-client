#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#define MAX_RIG_BONES 64
#define MAX_RIG_DEFORMS 16

#define RIG_DEFORM_BEND  1u
#define RIG_DEFORM_TWIST 2u

layout (location = 0) in vec3 Position;
layout (location = 1) in vec2 UV0;
layout (location = 2) in vec3 Normal;
layout (location = 3) in vec4 Color;
layout (location = 4) in uvec4 BoneIndices;
layout (location = 5) in vec4 BoneWeights;
layout (location = 6) in vec4 DeformCoord;
layout (location = 7) in uint DeformMeta;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

layout (std140) uniform RigBones {
    mat4 u_SkinMatrices[MAX_RIG_BONES];
};

layout (std140) uniform RigDeform {
    vec4 u_DeformOriginLength[MAX_RIG_DEFORMS];
    vec4 u_DeformAxisFlags[MAX_RIG_DEFORMS];
    vec4 u_DeformBendAxis[MAX_RIG_DEFORMS];
    vec4 u_DeformRanges[MAX_RIG_DEFORMS];
    vec4 u_DeformParams[MAX_RIG_DEFORMS];
};

out vec2 v_TexCoord;
out vec4 v_Color;
out vec3 v_Normal;

vec3 rotateAroundAxis(vec3 value, vec3 axis, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return value * c + cross(axis, value) * s + axis * dot(axis, value) * (1.0 - c);
}

float deformProfile(float u, float start, float end, float falloff) {
    float width = max(end - start, 1.0e-6);
    float t = clamp((u - start) / width, 0.0, 1.0);
    float smoothT = t * t * (3.0 - 2.0 * t);
    return mix(t, smoothT, clamp(falloff, 0.0, 1.0));
}

void applyLocalDeform(inout vec3 position, inout vec3 normal) {
    uint encodedId = DeformMeta & 0xFFFFu;
    if (encodedId == 0u) return;

    uint deformId = encodedId - 1u;
    if (deformId >= uint(MAX_RIG_DEFORMS)) return;

    uint vertexFlags = DeformMeta >> 16u;
    int id = int(deformId);

    vec4 originLength = u_DeformOriginLength[id];
    vec4 axisFlags = u_DeformAxisFlags[id];
    vec4 bendAxisData = u_DeformBendAxis[id];
    vec4 ranges = u_DeformRanges[id];
    vec4 params = u_DeformParams[id];

    uint runtimeFlags = uint(max(0.0, floor(axisFlags.w + 0.5)));
    uint flags = vertexFlags & runtimeFlags;
    if (flags == 0u || originLength.w <= 1.0e-6) return;

    vec3 axis = normalize(axisFlags.xyz);
    vec3 bendAxis = normalize(bendAxisData.xyz);
    vec3 origin = originLength.xyz;
    float length = originLength.w;
    float u = clamp(DeformCoord.x, 0.0, 1.0);
    float longitudinal = u * length;

    vec3 restCenter = origin + axis * longitudinal;
    vec3 crossSection = position - restCenter;

    // Twist the local cross-section first. Bend then rotates that already-twisted frame,
    // so twist follows the curved limb instead of remaining around the original straight axis.
    if ((flags & RIG_DEFORM_TWIST) != 0u && abs(params.y) > 1.0e-7) {
        float twistT = deformProfile(u, ranges.z, ranges.w, params.w);
        float twistAngle = params.y * twistT;
        crossSection = rotateAroundAxis(crossSection, axis, twistAngle);
        normal = rotateAroundAxis(normal, axis, twistAngle);
    }

    vec3 deformedCenter = restCenter;
    float localBendAngle = 0.0;

    if ((flags & RIG_DEFORM_BEND) != 0u && abs(params.x) > 1.0e-7) {
        float start = ranges.x;
        float end = ranges.y;
        float startDistance = start * length;
        float endDistance = end * length;
        float activeLength = max(endDistance - startDistance, 1.0e-6);
        float fullAngle = params.x;
        vec3 bendDirection = normalize(cross(bendAxis, axis));
        vec3 startCenter = origin + axis * startDistance;

        if (u <= start) {
            deformedCenter = restCenter;
            localBendAngle = 0.0;
        } else {
            float profile = deformProfile(u, start, end, params.z);
            localBendAngle = fullAngle * profile;
            float radius = activeLength / fullAngle;

            if (u < end) {
                deformedCenter = startCenter
                        + axis * (sin(localBendAngle) * radius)
                        + bendDirection * ((1.0 - cos(localBendAngle)) * radius);
            } else {
                vec3 endCenter = startCenter
                        + axis * (sin(fullAngle) * radius)
                        + bendDirection * ((1.0 - cos(fullAngle)) * radius);
                vec3 endTangent = rotateAroundAxis(axis, bendAxis, fullAngle);
                deformedCenter = endCenter + endTangent * (longitudinal - endDistance);
                localBendAngle = fullAngle;
            }
        }

        crossSection = rotateAroundAxis(crossSection, bendAxis, localBendAngle);
        normal = rotateAroundAxis(normal, bendAxis, localBendAngle);
    }

    position = deformedCenter + crossSection;
    normal = normalize(normal);
}

void main() {
    vec3 localPosition = Position;
    vec3 localNormal = normalize(Normal);
    applyLocalDeform(localPosition, localNormal);

    vec4 weights = BoneWeights;
    float weightSum = dot(weights, vec4(1.0));
    if (weightSum <= 1.0e-6) {
        weights = vec4(1.0, 0.0, 0.0, 0.0);
    } else {
        weights /= weightSum;
    }

    mat4 skin = u_SkinMatrices[int(BoneIndices.x)] * weights.x
              + u_SkinMatrices[int(BoneIndices.y)] * weights.y
              + u_SkinMatrices[int(BoneIndices.z)] * weights.z
              + u_SkinMatrices[int(BoneIndices.w)] * weights.w;

    vec4 skinnedPosition = skin * vec4(localPosition, 1.0);
    vec3 skinnedNormal = normalize(mat3(skin) * localNormal);

    gl_Position = u_Proj * u_ModelView * skinnedPosition;
    v_TexCoord = UV0;
    v_Color = Color;
    v_Normal = skinnedNormal;
}
