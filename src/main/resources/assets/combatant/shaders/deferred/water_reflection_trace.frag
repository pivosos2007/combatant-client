#version 450 core

layout(binding = 5) uniform sampler2D u_SceneRadiance;
layout(binding = 6) uniform sampler2D u_ResolvedDepth;
layout(binding = 7) uniform sampler2D u_GbufferDepth;
layout(binding = 8) uniform sampler2D u_DepthPyramid;
layout(binding = 9) uniform sampler2D u_GbufferGeometry;
layout(binding = 12) uniform sampler2D u_CascadeColor;
layout(binding = 13) uniform sampler2D u_CascadeDepth;

layout(std430, binding = 11) readonly buffer WaterFrame {
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

layout(std430, binding = 14) readonly buffer ReflectionTraceData {
    mat4 inverseProjection;
    mat4 inverseView;
    mat4 projection;
    vec4 depthTransform;
    vec4 traceExtentAndFar;
    vec4 traceParams0;
    vec4 traceParams1;
    vec4 resolveParams;
} u_Trace;

struct CombatantReflectionCascadeFace {
    mat4 viewProjection;
    vec4 atlasScaleBias;
    vec4 rangeFace;
    vec4 originDelta;
};

layout(std430, binding = 15) readonly buffer ReflectionCascadeData {
    CombatantReflectionCascadeFace faces[];
} u_Cascade;

#define COMBATANT_REFLECTION_CASCADE_FACE(i) u_Cascade.faces[i]
#moj_import <combatant:deferred_reflection_trace.glsl>
#moj_import <combatant:deferred_reflection_cascade.glsl>
#undef COMBATANT_REFLECTION_CASCADE_FACE
#moj_import <combatant:deferred_water_surface_model.glsl>

layout(location = 0) in vec2 te_Uv;
layout(location = 2) in vec4 te_Color;
layout(location = 3) in vec4 te_Params;
layout(location = 4) in vec4 te_Optical;
layout(location = 5) in vec3 te_ViewPosition;
layout(location = 6) in vec3 te_WorldPosition;
layout(location = 7) in vec3 te_ViewNormal;
layout(location = 8) in vec4 te_CurrentClip;
layout(location = 9) in vec4 te_PreviousClip;
layout(location = 12) flat in uint te_MapMask;
layout(location = 15) flat in uint te_Surface;
layout(location = 16) in vec3 te_PreviousViewPosition;
layout(location = 17) in float te_SkyLight;

layout(location = 0) out vec4 outTraceReflection;
layout(location = 1) out vec4 outTraceConfidence;
layout(location = 2) out vec4 outReprojection;
layout(location = 3) out vec4 outGeometry;
layout(location = 4) out vec4 outDepths;
layout(location = 5) out vec4 outCascadeReflection;
layout(location = 6) out vec4 outCascadeConfidence;

CombatantReflectionTracePolicy tracePolicy() {
    CombatantReflectionTracePolicy policy;
    policy.inverseProjection = u_Trace.inverseProjection;
    policy.projection = u_Trace.projection;
    policy.depthTransform = u_Trace.depthTransform;
    policy.traceExtentAndFar = u_Trace.traceExtentAndFar;
    policy.traceParams0 = u_Trace.traceParams0;
    policy.traceParams1 = u_Trace.traceParams1;
    return policy;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 extent = textureSize(u_ResolvedDepth, 0);
    if (any(lessThan(pixel, ivec2(0))) || any(greaterThanEqual(pixel, extent))) discard;
    float opaqueDepth = texelFetch(u_ResolvedDepth, pixel, 0).r;
    if (opaqueDepth > 0.0 && gl_FragCoord.z + 1.0e-6 < opaqueDepth) discard;

    vec3 baseWorldNormal = normalize(mat3(u_CurrentInverseView) * te_ViewNormal);
    vec3 waterWorldNormal = combatantWaterSurfaceNormal(
            te_WorldPosition, baseWorldNormal, te_Params.xy,
            te_SkyLight, u_CurrentCameraTime.w, u_OpticalScattering.w);
    vec3 normal = normalize(mat3(u_CurrentView) * waterWorldNormal);
    vec3 incident = normalize(te_ViewPosition);
    vec3 orientedNormal = dot(incident, normal) > 0.0 ? -normal : normal;
    vec3 rayDirection = normalize(reflect(incident, orientedNormal));
    float roughness = COMBATANT_WATER_ROUGHNESS;

    CombatantReflectionTracePolicy policy = tracePolicy();
    CombatantReflectionTraceResult hit = combatantTraceScreenReflection(
            policy, u_ResolvedDepth, u_GbufferDepth, u_DepthPyramid, u_GbufferGeometry,
            te_ViewPosition, orientedNormal, rayDirection, roughness);

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
    if (u_ReflectionMeta.y > 0.5) {
        vec3 currentWorldRelative = (u_Trace.inverseView * vec4(te_ViewPosition, 0.0)).xyz;
        vec3 reflectedWorld = normalize((u_Trace.inverseView * vec4(rayDirection, 0.0)).xyz);
        int totalFaces = clamp(int(u_Cascade.faces[0].rangeFace.w + 0.5), 0, 24);
        if (totalFaces > 0 && combatantSampleReflectionCascade(u_CascadeColor, u_CascadeDepth,
                currentWorldRelative, reflectedWorld, length(te_ViewPosition), totalFaces, cascadeColor)) {
            cascadeColor = max(cascadeColor, vec3(0.0));
            cascadeConfidence = clamp(u_Trace.resolveParams.y, 0.0, 1.0);
        }
    }

    vec2 previousUv = vec2(-1.0);
    float motionValid = 0.0;
    if (u_ReflectionMeta.w > 0.5 && te_CurrentClip.w > 1.0e-7 && te_PreviousClip.w > 1.0e-7) {
        previousUv = te_PreviousClip.xy / te_PreviousClip.w * 0.5 + 0.5;
        motionValid = all(greaterThanEqual(previousUv, vec2(0.0)))
                && all(lessThanEqual(previousUv, vec2(1.0))) ? 1.0 : 0.0;
    }

    outTraceReflection = vec4(ssrColor, 1.0);
    outTraceConfidence = vec4(clamp(ssrConfidence, 0.0, 1.0), 0.0, 0.0, 1.0);
    outReprojection = vec4(previousUv, motionValid, 1.0);
    outGeometry = vec4(combatantReflectionEncodeOctahedral(orientedNormal), roughness, 1.0);
    outDepths = vec4(abs(te_ViewPosition.z), abs(te_PreviousViewPosition.z), hitViewDepth, previousHitViewDepth);
    outCascadeReflection = vec4(cascadeColor, 1.0);
    outCascadeConfidence = vec4(cascadeConfidence, 0.0, 0.0, 1.0);
}
