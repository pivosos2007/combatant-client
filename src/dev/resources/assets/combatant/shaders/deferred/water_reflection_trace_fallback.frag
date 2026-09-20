#version 330 core

uniform sampler2D u_SceneRadiance;
uniform sampler2D u_ResolvedDepth;
uniform sampler2D u_GbufferDepth;
uniform sampler2D u_DepthPyramid;
uniform sampler2D u_GbufferGeometry;
uniform sampler2D u_CascadeColor;
uniform sampler2D u_CascadeDepth;

layout(std140) uniform WaterFrame {
    mat4 u_CurrentView;
    mat4 u_CurrentProjection;
    mat4 u_CurrentInverseProjection;
    mat4 u_CurrentInverseView;
    mat4 u_PreviousView;
    mat4 u_PreviousProjection;
    vec4 u_CurrentCameraTime;
    vec4 u_PreviousCameraTime;
    vec4 u_Viewport;
    vec4 u_DepthTransform;
    vec4 u_Deformation0;
    vec4 u_Deformation1;
    vec4 u_MediumReflection;
    vec4 u_MediumBoundary;
    vec4 u_OpticalAbsorption;
    vec4 u_OpticalScattering;
    vec4 u_ReflectionMeta;
};

struct CombatantReflectionCascadeFace {
    mat4 viewProjection;
    vec4 atlasScaleBias;
    vec4 rangeFace;
    vec4 originDelta;
};

layout(std140) uniform WaterReflectionTrace {
    vec4 u_TraceExtentAndFar;
    vec4 u_TraceParams0;
    vec4 u_TraceParams1;
    vec4 u_TraceResolveParams;
    vec4 u_CascadeMeta;
    CombatantReflectionCascadeFace u_CascadeFaces[24];
};

#define COMBATANT_REFLECTION_CASCADE_FACE(i) u_CascadeFaces[i]
#moj_import <combatant:deferred_reflection_trace.glsl>
#moj_import <combatant:deferred_reflection_cascade.glsl>
#undef COMBATANT_REFLECTION_CASCADE_FACE
#moj_import <combatant:deferred_water_surface_model.glsl>

in vec2 v_Uv;
in vec2 v_LocalSurface;
in vec4 v_Color;
in vec4 v_Params;
in vec4 v_Optical;
in vec3 v_ViewPosition;
in vec3 v_WorldPosition;
in vec3 v_ViewNormal;
in vec4 v_CurrentClip;
in vec4 v_PreviousClip;
in vec3 v_PreviousViewPosition;
in float v_SkyLight;
flat in uint v_MaterialId;
flat in uint v_FluidTypeId;
flat in uint v_MapMask;
flat in uint v_FeatureMask;
flat in uint v_SurfaceFlags;
flat in uint v_Surface;

layout(location = 0) out vec4 outTraceReflection;
layout(location = 1) out vec4 outTraceConfidence;
layout(location = 2) out vec4 outReprojection;
layout(location = 3) out vec4 outGeometry;
layout(location = 4) out vec4 outDepths;
layout(location = 5) out vec4 outCascadeReflection;
layout(location = 6) out vec4 outCascadeConfidence;

CombatantReflectionTracePolicy tracePolicy() {
    CombatantReflectionTracePolicy policy;
    policy.inverseProjection = u_CurrentInverseProjection;
    policy.projection = u_CurrentProjection;
    policy.depthTransform = u_DepthTransform;
    policy.traceExtentAndFar = u_TraceExtentAndFar;
    policy.traceParams0 = u_TraceParams0;
    policy.traceParams1 = u_TraceParams1;
    return policy;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 extent = textureSize(u_ResolvedDepth, 0);
    if (any(lessThan(pixel, ivec2(0))) || any(greaterThanEqual(pixel, extent))) discard;
    float opaqueDepth = texelFetch(u_ResolvedDepth, pixel, 0).r;
    if (opaqueDepth > 0.0 && gl_FragCoord.z + 1.0e-6 < opaqueDepth) discard;

    vec3 baseWorldNormal = normalize(mat3(u_CurrentInverseView) * v_ViewNormal);
    vec3 waterWorldNormal = combatantWaterSurfaceNormal(
            v_WorldPosition, baseWorldNormal, v_Params.xy,
            v_SkyLight, u_CurrentCameraTime.w, u_OpticalScattering.w);
    vec3 normal = normalize(mat3(u_CurrentView) * waterWorldNormal);
    vec3 incident = normalize(v_ViewPosition);
    vec3 orientedNormal = dot(incident, normal) > 0.0 ? -normal : normal;
    vec3 rayDirection = normalize(reflect(incident, orientedNormal));
    float roughness = COMBATANT_WATER_ROUGHNESS;

    CombatantReflectionTracePolicy policy = tracePolicy();
    CombatantReflectionTraceResult hit = combatantTraceScreenReflection(
            policy, u_ResolvedDepth, u_GbufferDepth, u_DepthPyramid, u_GbufferGeometry,
            v_ViewPosition, orientedNormal, rayDirection, roughness);

    vec3 ssrColor = vec3(0.0);
    float ssrConfidence = 0.0;
    float hitViewDepth = 0.0;
    float previousHitViewDepth = 0.0;
    if (hit.valid > 0.5) {
        ssrColor = max(textureLod(u_SceneRadiance, hit.hitUv, 0.0).rgb, vec3(0.0));
        ssrConfidence = hit.confidence;
        float hitDepth = textureLod(u_ResolvedDepth, hit.hitUv, 0.0).r;
        if (hitDepth > 0.0) {
            vec3 hitViewPosition = combatantReflectionReconstructView(policy, hit.hitUv, hitDepth);
            hitViewDepth = abs(hitViewPosition.z);
            vec3 hitCurrentRelative = (u_CurrentInverseView * vec4(hitViewPosition, 1.0)).xyz;
            vec3 hitWorldPosition = hitCurrentRelative + u_CurrentCameraTime.xyz;
            vec3 hitPreviousRelative = hitWorldPosition - u_PreviousCameraTime.xyz;
            vec3 hitPreviousView = (u_PreviousView * vec4(hitPreviousRelative, 1.0)).xyz;
            previousHitViewDepth = abs(hitPreviousView.z);
        }
    }

    vec3 cascadeColor = vec3(0.0);
    float cascadeConfidence = 0.0;
    int totalFaces = clamp(int(u_CascadeMeta.x + 0.5), 0, 24);
    if (u_ReflectionMeta.y > 0.5 && totalFaces > 0) {
        vec3 currentWorldRelative = (u_CurrentInverseView * vec4(v_ViewPosition, 0.0)).xyz;
        vec3 reflectedWorld = normalize((u_CurrentInverseView * vec4(rayDirection, 0.0)).xyz);
        if (combatantSampleReflectionCascade(u_CascadeColor, u_CascadeDepth,
                currentWorldRelative, reflectedWorld, length(v_ViewPosition), totalFaces, cascadeColor)) {
            cascadeColor = max(cascadeColor, vec3(0.0));
            cascadeConfidence = clamp(u_TraceResolveParams.y, 0.0, 1.0);
        }
    }

    vec2 previousUv = vec2(-1.0);
    float motionValid = 0.0;
    if (u_ReflectionMeta.w > 0.5 && v_CurrentClip.w > 1.0e-7 && v_PreviousClip.w > 1.0e-7) {
        previousUv = v_PreviousClip.xy / v_PreviousClip.w * 0.5 + 0.5;
        motionValid = all(greaterThanEqual(previousUv, vec2(0.0)))
                && all(lessThanEqual(previousUv, vec2(1.0))) ? 1.0 : 0.0;
    }

    outTraceReflection = vec4(ssrColor, 1.0);
    outTraceConfidence = vec4(clamp(ssrConfidence, 0.0, 1.0), 0.0, 0.0, 1.0);
    outReprojection = vec4(previousUv, motionValid, 1.0);
    outGeometry = vec4(combatantReflectionEncodeOctahedral(orientedNormal), roughness, 1.0);
    outDepths = vec4(abs(v_ViewPosition.z), abs(v_PreviousViewPosition.z), hitViewDepth, previousHitViewDepth);
    outCascadeReflection = vec4(cascadeColor, 1.0);
    outCascadeConfidence = vec4(cascadeConfidence, 0.0, 0.0, 1.0);
}
