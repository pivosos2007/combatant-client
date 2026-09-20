#version 450 core

layout(binding = 7) uniform sampler2D u_SceneRadiance;
layout(binding = 8) uniform sampler2D u_ResolvedDepth;

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

#moj_import <combatant:deferred_water_surface_model.glsl>

layout(location = 0) in vec2 te_Uv;
layout(location = 1) in vec2 te_LocalSurface;
layout(location = 2) in vec4 te_Color;
layout(location = 3) in vec4 te_Params;
layout(location = 4) in vec4 te_Optical;
layout(location = 5) in vec3 te_ViewPosition;
layout(location = 6) in vec3 te_WorldPosition;
layout(location = 7) in vec3 te_ViewNormal;
layout(location = 8) in vec4 te_CurrentClip;
layout(location = 9) in vec4 te_PreviousClip;
layout(location = 10) flat in uint te_MaterialId;
layout(location = 11) flat in uint te_FluidTypeId;
layout(location = 12) flat in uint te_MapMask;
layout(location = 13) flat in uint te_FeatureMask;
layout(location = 14) flat in uint te_SurfaceFlags;
layout(location = 15) flat in uint te_Surface;
layout(location = 17) in float te_SkyLight;

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

vec2 clampViewportUv(vec2 uv) {
    vec2 halfPixel = 0.5 * u_Viewport.zw;
    return clamp(uv, halfPixel, vec2(1.0) - halfPixel);
}

float viewportEdgeFade(vec2 uv) {
    vec2 pixel = max(u_Viewport.zw, vec2(1.0e-7));
    float edgePixels = min(
        min(uv.x, 1.0 - uv.x) / pixel.x,
        min(uv.y, 1.0 - uv.y) / pixel.y
    );
    return smoothstep(1.5, 8.0, edgePixels);
}

float measuredLayerDistance(vec2 uv, float surfaceDistance, float fallbackDistance) {
    float opaqueDepth = textureLod(u_ResolvedDepth, uv, 0.0).r;
    if (opaqueDepth > 0.0) {
        vec3 opaqueView = reconstructView(uv, opaqueDepth);
        float measured = length(opaqueView) - surfaceDistance;
        if (measured > 1.0e-3) return min(measured, 16.0);
    }
    return clamp(fallbackDistance, 0.0, 4.0);
}

vec2 waterSurfaceRefractionUv(vec2 screenUv, vec2 normalTangent,
                             float viewDistance, float layerDistance, float intensity) {
    vec2 offset = normalTangent
            * (0.1 * max(intensity, 0.0))
            * min(layerDistance, 8.0)
            / max(viewDistance, 1.0);

    // Never sample outside the actual render target. Fade only within the last few pixels so the
    // refraction itself cannot manufacture an apparent water cutoff at the viewport boundary.
    offset *= viewportEdgeFade(screenUv);
    vec2 halfPixel = 0.5 * u_Viewport.zw;
    return clamp(screenUv + offset, halfPixel, vec2(1.0) - halfPixel);
}

void main() {
    vec3 producerTint = combatantWaterTint(te_Color.rgb);
    vec3 baseWorldNormal = normalize(mat3(u_CurrentInverseView) * te_ViewNormal);
    vec3 waterWorldNormal = combatantWaterSurfaceNormal(
            te_WorldPosition, baseWorldNormal, te_Params.xy,
            te_SkyLight, u_CurrentCameraTime.w, u_OpticalScattering.w);

    vec3 tangent;
    vec3 bitangent;
    combatantWaterBasis(baseWorldNormal, tangent, bitangent);
    vec2 normalTangent = vec2(
            dot(waterWorldNormal, tangent),
            dot(waterWorldNormal, bitangent));

    bool cameraInsideWater = u_MediumReflection.y > 0.5;
    vec2 screenUv = clampViewportUv(gl_FragCoord.xy * u_Viewport.zw);
    float surfaceDistance = length(te_ViewPosition);
    float fallbackThickness = max(te_Optical.y, 0.0);
    float layerDistance = measuredLayerDistance(screenUv, surfaceDistance, fallbackThickness);

    vec2 backgroundUv = waterSurfaceRefractionUv(
            screenUv, normalTangent, surfaceDistance, layerDistance, u_OpticalAbsorption.w);

    // Reject a displaced sample that lands on geometry in front of the water surface. This is the
    // same ordering invariant as the reference path, expressed through reconstructed view distance
    // so it remains valid with Combatant reversed-Z.
    float refractedDepth = textureLod(u_ResolvedDepth, backgroundUv, 0.0).r;
    if (refractedDepth > 0.0) {
        vec3 refractedView = reconstructView(backgroundUv, refractedDepth);
        float refractedDistance = length(refractedView);
        if (refractedDistance <= surfaceDistance + 1.0e-3) {
            backgroundUv = screenUv;
        } else {
            layerDistance = min(refractedDistance - surfaceDistance, 16.0);
        }
    }

    vec3 background = max(textureLod(u_SceneRadiance, backgroundUv, 0.0).rgb, vec3(0.0));
    vec3 absorption = combatantWaterAbsorption(producerTint, u_OpticalAbsorption.rgb);
    float opticalDistance = cameraInsideWater ? 0.0 : clamp(layerDistance, 0.0, 16.0);
    vec3 transmission = exp(-absorption * opticalDistance);

    // The reflection hierarchy is intentionally disabled in this foundation pass. Do not subtract
    // Fresnel energy here: without a valid reflected source that would turn grazing water black and
    // visually erase it near the viewport/horizon. Fresnel will become energy-conserving again when
    // the real reflection source is connected.
    vec3 color = background * transmission;
    outColor = vec4(max(color, vec3(0.0)), 1.0);

    vec2 velocity = vec2(0.0);
    if (u_ReflectionMeta.w > 0.5 && abs(te_CurrentClip.w) > 1.0e-7 && abs(te_PreviousClip.w) > 1.0e-7) {
        vec2 currentUv = te_CurrentClip.xy / te_CurrentClip.w * 0.5 + 0.5;
        vec2 previousUv = te_PreviousClip.xy / te_PreviousClip.w * 0.5 + 0.5;
        velocity = currentUv - previousUv;
    }
    outVelocity = vec4(velocity, 0.0, 1.0);
    outMotionValidity = vec4(u_ReflectionMeta.w > 0.5 ? 1.0 : 0.0, 0.0, 0.0, 1.0);
    outTemporalCoverage = vec4(1.0, 0.0, 0.0, 1.0);
    outReactiveMask = vec4(1.0, 0.0, 0.0, 1.0);
}
