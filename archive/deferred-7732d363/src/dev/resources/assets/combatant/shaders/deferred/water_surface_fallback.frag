#version 330 core

uniform sampler2D u_SceneRadiance;
uniform sampler2D u_ResolvedDepth;

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
    vec3 producerTint = combatantWaterTint(v_Color.rgb);
    vec3 baseWorldNormal = normalize(mat3(u_CurrentInverseView) * v_ViewNormal);
    vec3 waterWorldNormal = combatantWaterSurfaceNormal(
            v_WorldPosition, baseWorldNormal, v_Params.xy,
            v_SkyLight, u_CurrentCameraTime.w, u_OpticalScattering.w);

    vec3 tangent;
    vec3 bitangent;
    combatantWaterBasis(baseWorldNormal, tangent, bitangent);
    vec2 normalTangent = vec2(
            dot(waterWorldNormal, tangent),
            dot(waterWorldNormal, bitangent));

    bool cameraInsideWater = u_MediumReflection.y > 0.5;
    vec2 screenUv = clampViewportUv(gl_FragCoord.xy * u_Viewport.zw);
    float surfaceDistance = length(v_ViewPosition);
    float fallbackThickness = max(v_Optical.y, 0.0);
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
