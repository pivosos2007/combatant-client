#version 330 core

uniform sampler2D u_WaterReflectionColor;
uniform sampler2D u_WaterReflectionConfidence;
uniform sampler2D u_SceneRadiance;
uniform sampler2D u_ResolvedDepth;
uniform sampler2D u_SkySpecular;

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

layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outVelocity;
layout(location = 2) out vec4 outMotionValidity;
layout(location = 3) out vec4 outTemporalCoverage;
layout(location = 4) out vec4 outReactiveMask;

float waterDepthToNdc(float depth) {
    return depth * u_DepthTransform.x + u_DepthTransform.y;
}

vec3 reconstructView(vec2 uv, float depth) {
    vec4 h = u_CurrentInverseProjection * vec4(uv * 2.0 - 1.0, waterDepthToNdc(depth), 1.0);
    if (abs(h.w) < 1.0e-7) return vec3(0.0);
    return h.xyz / h.w;
}

bool projectUv(vec3 viewPosition, out vec2 uv) {
    vec4 clip = u_CurrentProjection * vec4(viewPosition, 1.0);
    if (clip.w <= 1.0e-7) { uv = vec2(0.5); return false; }
    uv = clip.xy / clip.w * 0.5 + 0.5;
    return all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)));
}

vec2 latLongFromDirection(vec3 d) {
    d = normalize(d);
    return vec2(atan(d.z, d.x) / (2.0 * COMBATANT_WATER_PI) + 0.5,
                acos(clamp(d.y, -1.0, 1.0)) / COMBATANT_WATER_PI);
}

vec3 reflectionHierarchy(vec2 screenUv, vec3 normal, vec3 viewDir, float roughness, vec3 neutralEnvironment) {
    bool hasWaterReflection = u_MediumReflection.z > 0.5;
    bool hasSky = u_MediumReflection.w > 0.5;
    vec3 reflectedView = normalize(reflect(-viewDir, normal));
    vec3 reflectedWorld = normalize(mat3(u_CurrentInverseView) * reflectedView);
    vec3 fallback = neutralEnvironment;
    if (hasSky) {
        float mipCount = max(u_ReflectionMeta.x, 1.0);
        float skyLod = roughness * max(mipCount - 1.0, 0.0);
        fallback = max(textureLod(u_SkySpecular, latLongFromDirection(reflectedWorld), skyLod).rgb, vec3(0.0));
    }
    if (!hasWaterReflection) return fallback;
    vec3 traced = max(textureLod(u_WaterReflectionColor, screenUv, 0.0).rgb, vec3(0.0));
    float confidence = clamp(textureLod(u_WaterReflectionConfidence, screenUv, 0.0).r, 0.0, 1.0);
    return mix(fallback, traced, confidence);
}

void main() {
    vec3 producerTint = combatantWaterTint(v_Color.rgb);
    vec3 baseWorldNormal = normalize(mat3(u_CurrentInverseView) * v_ViewNormal);
    vec3 waterWorldNormal = combatantWaterSurfaceNormal(
            v_WorldPosition, baseWorldNormal, v_Params.xy,
            v_SkyLight, u_CurrentCameraTime.w, u_OpticalScattering.w);
    vec3 normal = normalize(mat3(u_CurrentView) * waterWorldNormal);
    vec3 viewDir = normalize(-v_ViewPosition);
    if (dot(normal, viewDir) < 0.0) normal = -normal;

    float ndv = clamp(dot(normal, viewDir), 0.0, 1.0);
    float fresnel = COMBATANT_WATER_F0 + (1.0 - COMBATANT_WATER_F0) * pow(1.0 - ndv, 5.0);
    bool cameraInsideWater = u_MediumReflection.y > 0.5;
    vec2 screenUv = gl_FragCoord.xy * u_Viewport.zw;

    vec3 incident = normalize(v_ViewPosition);
    vec3 boundaryNormal = normal;
    float eta = cameraInsideWater ? COMBATANT_WATER_IOR : 1.0 / COMBATANT_WATER_IOR;
    vec3 refracted = refract(incident, boundaryNormal, eta);
    bool totalInternalReflection = dot(refracted, refracted) <= 1.0e-8;

    float fallbackThickness = max(v_Optical.y, 0.0);
    float thickness = fallbackThickness;
    float thicknessConfidence = 0.0;
    vec2 refractedUv = screenUv;
    bool refractedUvValid = false;
    if (!totalInternalReflection) {
        float probeDistance = max(u_OpticalAbsorption.w, 0.01);
        if (fallbackThickness > 0.0) probeDistance = min(probeDistance, fallbackThickness);
        refractedUvValid = projectUv(v_ViewPosition + normalize(refracted) * probeDistance, refractedUv);
    }

    if (!cameraInsideWater && refractedUvValid) {
        float opaqueDepth = textureLod(u_ResolvedDepth, refractedUv, 0.0).r;
        if (opaqueDepth > 0.0) {
            vec3 opaqueView = reconstructView(refractedUv, opaqueDepth);
            float surfaceDistance = length(v_ViewPosition);
            float opaqueDistance = length(opaqueView);
            if (opaqueDistance > surfaceDistance + 1.0e-3) {
                thickness = max(opaqueDistance - surfaceDistance, 0.0);
                thicknessConfidence = 1.0;
            } else {
                refractedUvValid = false;
            }
        } else {
            refractedUvValid = false;
        }
    }

    vec2 backgroundUv = refractedUvValid && !totalInternalReflection ? refractedUv : screenUv;
    vec3 background = max(textureLod(u_SceneRadiance, backgroundUv, 0.0).rgb, vec3(0.0));
    float resolvedThickness = mix(fallbackThickness, thickness, thicknessConfidence);
    float opticalDistance = cameraInsideWater ? 0.0 : max(resolvedThickness, 0.0);
    vec3 absorption = max(u_OpticalAbsorption.rgb, combatantWaterAbsorption(producerTint));
    vec3 scattering = max(u_OpticalScattering.rgb, producerTint * 0.01);
    vec3 beer = exp(-absorption * opticalDistance);
    vec3 transmitted = background * beer + scattering * (vec3(1.0) - beer);

    vec3 reflected = reflectionHierarchy(screenUv, normal, viewDir, COMBATANT_WATER_ROUGHNESS,
                                         combatantWaterNeutralEnvironment(producerTint));
    float transmissionWeight = totalInternalReflection ? 0.0 : (1.0 - fresnel);
    vec3 color = reflected * fresnel + transmitted * transmissionWeight;
    outColor = vec4(max(color, vec3(0.0)), 1.0);

    vec2 velocity = vec2(0.0);
    if (u_ReflectionMeta.w > 0.5 && abs(v_CurrentClip.w) > 1.0e-7 && abs(v_PreviousClip.w) > 1.0e-7) {
        vec2 currentUv = v_CurrentClip.xy / v_CurrentClip.w * 0.5 + 0.5;
        vec2 previousUv = v_PreviousClip.xy / v_PreviousClip.w * 0.5 + 0.5;
        velocity = currentUv - previousUv;
    }
    outVelocity = vec4(velocity, 0.0, 1.0);
    outMotionValidity = vec4(u_ReflectionMeta.w > 0.5 ? 1.0 : 0.0, 0.0, 0.0, 1.0);
    outTemporalCoverage = vec4(1.0, 0.0, 0.0, 1.0);
    outReactiveMask = vec4(1.0, 0.0, 0.0, 1.0);
}
