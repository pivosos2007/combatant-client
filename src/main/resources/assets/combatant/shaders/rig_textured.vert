#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#define MAX_RIG_BONES 64
#define MAX_RIG_DEFORMS 16
#define MAX_RIG_RIBBON_SAMPLES 16
#define MAX_RIG_RIBBON_FRAMES (MAX_RIG_DEFORMS * MAX_RIG_RIBBON_SAMPLES)

#define RIG_DEFORM_BEND   1u
#define RIG_DEFORM_TWIST  2u
#define RIG_DEFORM_RIBBON 4u

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

layout (std140) uniform RigRibbon {
    vec4 u_RibbonPosition[MAX_RIG_RIBBON_FRAMES];
    vec4 u_RibbonNormal[MAX_RIG_RIBBON_FRAMES];
    vec4 u_RibbonBinormal[MAX_RIG_RIBBON_FRAMES];
    vec4 u_RibbonSourceTangent[MAX_RIG_DEFORMS];
    vec4 u_RibbonSourceNormal[MAX_RIG_DEFORMS];
    vec4 u_RibbonSourceBinormal[MAX_RIG_DEFORMS];
    vec4 u_RibbonMeta[MAX_RIG_DEFORMS];
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

bool sampleRibbonFrame(int id,
                       float u,
                       out vec3 center,
                       out vec3 tangent,
                       out vec3 frameNormal,
                       out vec3 frameBinormal,
                       out vec3 sourceTangent,
                       out vec3 sourceNormal,
                       out vec3 sourceBinormal) {
    vec4 meta = u_RibbonMeta[id];
    int sampleCount = int(floor(meta.x + 0.5));
    if (meta.y < 0.5 || sampleCount < 2 || sampleCount > MAX_RIG_RIBBON_SAMPLES) return false;

    float scaled = clamp(u, 0.0, 1.0) * float(sampleCount - 1);
    int local0 = int(floor(scaled));
    int local1 = min(local0 + 1, sampleCount - 1);
    float blend = scaled - float(local0);
    int base = id * MAX_RIG_RIBBON_SAMPLES;

    center = mix(u_RibbonPosition[base + local0].xyz,
                 u_RibbonPosition[base + local1].xyz,
                 blend);
    frameNormal = normalize(mix(u_RibbonNormal[base + local0].xyz,
                                u_RibbonNormal[base + local1].xyz,
                                blend));
    frameBinormal = mix(u_RibbonBinormal[base + local0].xyz,
                        u_RibbonBinormal[base + local1].xyz,
                        blend);
    frameBinormal -= frameNormal * dot(frameNormal, frameBinormal);
    float binormalLength2 = dot(frameBinormal, frameBinormal);
    if (binormalLength2 <= 1.0e-10) return false;
    frameBinormal *= inversesqrt(binormalLength2);

    float handedness = meta.z < 0.0 ? -1.0 : 1.0;
    tangent = normalize(cross(frameBinormal, frameNormal) * handedness);
    sourceTangent = normalize(u_RibbonSourceTangent[id].xyz);
    sourceNormal = normalize(u_RibbonSourceNormal[id].xyz);
    sourceBinormal = normalize(u_RibbonSourceBinormal[id].xyz);
    return true;
}

bool applyRibbonDeform(int id,
                       uint runtimeFlags,
                       float u,
                       inout vec3 position,
                       inout vec3 normal) {
    vec3 center;
    vec3 tangent;
    vec3 frameNormal;
    vec3 frameBinormal;
    vec3 sourceTangent;
    vec3 sourceNormal;
    vec3 sourceBinormal;
    if (!sampleRibbonFrame(id, u, center, tangent, frameNormal, frameBinormal,
                           sourceTangent, sourceNormal, sourceBinormal)) {
        return false;
    }

    if ((runtimeFlags & RIG_DEFORM_TWIST) != 0u) {
        vec4 ranges = u_DeformRanges[id];
        vec4 params = u_DeformParams[id];
        if (abs(params.y) > 1.0e-7) {
            float twistT = deformProfile(u, ranges.z, ranges.w, params.w);
            float twistAngle = params.y * twistT;
            frameNormal = rotateAroundAxis(frameNormal, tangent, twistAngle);
            frameBinormal = rotateAroundAxis(frameBinormal, tangent, twistAngle);
        }
    }

    position = center
            + frameNormal * DeformCoord.y
            + frameBinormal * DeformCoord.z;

    float longitudinalNormal = dot(normal, sourceTangent);
    float lateralNormal = dot(normal, sourceNormal);
    float depthNormal = dot(normal, sourceBinormal);
    normal = normalize(
            tangent * longitudinalNormal
            + frameNormal * lateralNormal
            + frameBinormal * depthNormal
    );
    return true;
}

void applyLocalDeform(inout vec3 position, inout vec3 normal) {
    uint encodedId = DeformMeta & 0xFFFFu;
    if (encodedId == 0u) return;

    uint deformId = encodedId - 1u;
    if (deformId >= uint(MAX_RIG_DEFORMS)) return;

    uint vertexFlags = DeformMeta >> 16u;
    int id = int(deformId);
    float u = clamp(DeformCoord.x, 0.0, 1.0);

    vec4 axisFlags = u_DeformAxisFlags[id];
    uint runtimeFlags = uint(max(0.0, floor(axisFlags.w + 0.5)));

    // Ribbon is the higher-order centerline deformation. When active it replaces constant-curvature
    // bend, while twist still composes around the sampled ribbon tangent.
    if ((vertexFlags & RIG_DEFORM_RIBBON) != 0u) {
        if (applyRibbonDeform(id, runtimeFlags, u, position, normal)) return;
    }

    uint flags = vertexFlags & runtimeFlags & (RIG_DEFORM_BEND | RIG_DEFORM_TWIST);
    vec4 originLength = u_DeformOriginLength[id];
    if (flags == 0u || originLength.w <= 1.0e-6) return;

    vec4 bendAxisData = u_DeformBendAxis[id];
    vec4 ranges = u_DeformRanges[id];
    vec4 params = u_DeformParams[id];

    vec3 axis = normalize(axisFlags.xyz);
    vec3 bendAxis = normalize(bendAxisData.xyz);
    vec3 origin = originLength.xyz;
    float length = originLength.w;
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
