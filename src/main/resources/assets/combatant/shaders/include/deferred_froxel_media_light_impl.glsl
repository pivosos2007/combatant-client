#ifndef COMBATANT_FROXEL_DIRECTIONAL_SHADOWS
#error COMBATANT_FROXEL_DIRECTIONAL_SHADOWS must be defined before importing deferred_froxel_media_light_impl.glsl
#endif

layout(local_size_x = 4, local_size_y = 4, local_size_z = 4) in;
layout(binding = 1, rgba16f) uniform image3D u_SegmentRadiance;
layout(binding = 2, rgba16f) readonly uniform image3D u_MediaProperties;
layout(binding = 3) uniform sampler2D u_CloudShadowMap;
layout(binding = 7, rgba16f) readonly uniform image3D u_SegmentTransmittance;

#moj_import <combatant:deferred_froxel_media_data.glsl>

layout(std430, binding = 4) readonly buffer SkyDiffuseSh {
    vec4 coefficients[];
} u_SkySh;

#if COMBATANT_FROXEL_DIRECTIONAL_SHADOWS
layout(binding = 5) uniform sampler2D u_ShadowDepth;
struct ShadowCascade {
    mat4 viewProjection;
    vec4 atlasScaleBias;
    vec4 splitRange;
    vec4 shadowTexel;
};
layout(std430, binding = 6) readonly buffer ShadowCascadeData {
    ShadowCascade cascades[];
} u_Shadow;
#endif

#moj_import <combatant:deferred_froxel_transport.glsl>
#moj_import <combatant:deferred_cloud_shadow_sampling.glsl>

const float PI = 3.14159265358979323846;
const float SH_Y00 = 0.28209479177387814;

vec3 viewRay(vec2 uv) {
    float ndcZ = u_Data.policy.y > 0.5 ? 0.5 : 0.0;
    vec4 view = u_Data.inverseProjection * vec4(uv * 2.0 - 1.0, ndcZ, 1.0);
    return normalize(view.xyz / max(abs(view.w), 1e-7) * sign(view.w));
}

float hgPhase(float cosTheta, float g) {
    float g2 = g * g;
    float denominator = max(1.0 + g2 - 2.0 * g * cosTheta, 1e-4);
    return (1.0 - g2) / (4.0 * PI * denominator * sqrt(denominator));
}

vec3 environmentAmbientRadiance() {
    if (u_SkySh.coefficients.length() < 1) return vec3(0.0);
    vec3 diffuseIrradiance = max(u_SkySh.coefficients[0].rgb * SH_Y00, vec3(0.0));
    return diffuseIrradiance / PI;
}

#if COMBATANT_FROXEL_DIRECTIONAL_SHADOWS
const float COMBATANT_FROXEL_SHADOW_DISTORTION = 0.85;

float combatant_froxel_shadow_quartic_length(vec2 value) {
    vec2 squared = value * value;
    vec2 fourth = squared * squared;
    return sqrt(sqrt(fourth.x + fourth.y));
}

float combatant_froxel_shadow_distortion_factor(vec2 shadowNdc) {
    return combatant_froxel_shadow_quartic_length(shadowNdc) * COMBATANT_FROXEL_SHADOW_DISTORTION
            + (1.0 - COMBATANT_FROXEL_SHADOW_DISTORTION);
}

float cascadeVisibility(int cascadeIndex, vec3 worldRelative) {
    ShadowCascade cascade = u_Shadow.cascades[cascadeIndex];
    vec4 clip = cascade.viewProjection * vec4(worldRelative, 1.0);
    if (abs(clip.w) < 1e-7) return 1.0;
    vec3 ndc = clip.xyz / clip.w;
    float distortionFactor = max(combatant_froxel_shadow_distortion_factor(ndc.xy), 1.0e-4);
    ndc.xy /= distortionFactor;
    vec2 localUv = ndc.xy * 0.5 + 0.5;
    if (any(lessThan(localUv, vec2(0.0))) || any(greaterThan(localUv, vec2(1.0)))) return 1.0;

    vec2 atlasUv = localUv * cascade.atlasScaleBias.xy + cascade.atlasScaleBias.zw;
    float receiverDepth = u_Data.policy.y > 0.5 ? ndc.z : ndc.z * 0.5 + 0.5;
    vec2 atlasTexel = max(cascade.shadowTexel.xy, vec2(1e-7));
    const vec2 OFFSETS[4] = vec2[4](
        vec2(-0.5, -0.5), vec2(0.5, -0.5), vec2(-0.5, 0.5), vec2(0.5, 0.5)
    );
    float visibility = 0.0;
    for (int i = 0; i < 4; ++i) {
        vec2 uv = atlasUv + OFFSETS[i] * atlasTexel;
        vec2 minimum = cascade.atlasScaleBias.zw + atlasTexel * 0.5;
        vec2 maximum = cascade.atlasScaleBias.zw + cascade.atlasScaleBias.xy - atlasTexel * 0.5;
        float storedDepth = textureLod(u_ShadowDepth, clamp(uv, minimum, maximum), 0.0).r;
        bool blocked = storedDepth > 0.0 && storedDepth > receiverDepth + 1e-5;
        visibility += blocked ? 0.0 : 1.0;
    }
    return visibility * 0.25;
}

float directionalGeometryVisibility(vec3 worldRelative, float viewDistance) {
    int cascadeCount = u_Shadow.cascades.length() > 0
            ? clamp(int(u_Shadow.cascades[0].splitRange.w + 0.5), 0, 8)
            : 0;
    int cascadeIndex = -1;
    for (int i = 0; i < 8; ++i) {
        if (i >= cascadeCount) break;
        vec2 split = u_Shadow.cascades[i].splitRange.xy;
        if (viewDistance >= split.x && viewDistance <= split.y) {
            cascadeIndex = i;
            break;
        }
    }
    if (cascadeIndex < 0) return 1.0;

    float visibility = cascadeVisibility(cascadeIndex, worldRelative);
    float blendFraction = clamp(u_Data.policy.w, 0.0, 0.49);
    if (blendFraction > 0.0 && cascadeIndex + 1 < cascadeCount) {
        vec2 split = u_Shadow.cascades[cascadeIndex].splitRange.xy;
        float blendWidth = max((split.y - split.x) * blendFraction, 1e-5);
        float blend = smoothstep(split.y - blendWidth, split.y, viewDistance);
        if (blend > 0.0) {
            visibility = mix(visibility, cascadeVisibility(cascadeIndex + 1, worldRelative), blend);
        }
    }
    return visibility;
}
#else
float directionalGeometryVisibility(vec3 worldRelative, float viewDistance) {
    return 1.0;
}
#endif

vec3 integratedSource(vec3 sourcePerBlock, vec3 segmentTransmittance, float segmentLength) {
    vec3 t = clamp(segmentTransmittance, vec3(0.0), vec3(1.0));
    vec3 sigma = -log(max(t, vec3(1e-6))) / max(segmentLength, 1e-5);
    vec3 factor = vec3(segmentLength);
    for (int channel = 0; channel < 3; ++channel) {
        if (sigma[channel] > 1e-6) factor[channel] = (1.0 - t[channel]) / sigma[channel];
    }
    return sourcePerBlock * factor;
}

void main() {
    ivec3 coord = ivec3(gl_GlobalInvocationID.xyz);
    ivec3 dimensions = imageSize(u_SegmentRadiance);
    if (any(greaterThanEqual(coord, dimensions))) return;

    vec4 properties = imageLoad(u_MediaProperties, coord);
    vec3 sigmaS = max(properties.rgb, vec3(0.0));
    if (max(max(sigmaS.r, sigmaS.g), sigmaS.b) <= 1e-8) return;

    vec2 uv = (vec2(coord.xy) + 0.5) / vec2(dimensions.xy);
    vec3 rayView = viewRay(uv);
    vec3 rayWorld = normalize(mat3(u_Data.inverseView) * rayView);
    float nearDistance = combatant_froxel_boundary_distance(
            coord.z, dimensions.z, u_Data.froxel.w, u_Data.policy.x);
    float farDistance = combatant_froxel_boundary_distance(
            coord.z + 1, dimensions.z, u_Data.froxel.w, u_Data.policy.x);
    float centerDistance = 0.5 * (nearDistance + farDistance);
    float segmentLength = max(farDistance - nearDistance, 1e-4);
    vec3 relativeWorld = rayWorld * centerDistance;

    vec3 incident = environmentAmbientRadiance() * max(u_Data.mediumLighting.x, 0.0);
    if (u_Data.directionalDirection.w > 0.5 && u_Data.directionalRadiance.w > 0.5) {
        vec3 lightDirection = normalize(u_Data.directionalDirection.xyz);
        float cloudVisibility = combatant_sample_cloud_shadow(
                u_CloudShadowMap, relativeWorld, u_Data.cameraTime.y + relativeWorld.y,
                u_Data.shadowMapDomain, u_Data.shadowSun, u_Data.cloudBounds);
        float viewDistance = max(0.0, -rayView.z * centerDistance);
        float geometryVisibility = directionalGeometryVisibility(relativeWorld, viewDistance);
        float phase = hgPhase(dot(rayWorld, lightDirection), clamp(properties.a, -0.95, 0.95));
        incident += max(u_Data.directionalRadiance.rgb, vec3(0.0))
                * max(u_Data.mediumLighting.y, 0.0)
                * phase * cloudVisibility * geometryVisibility;
    }

    vec4 currentRadiance = imageLoad(u_SegmentRadiance, coord);
    vec3 segmentT = imageLoad(u_SegmentTransmittance, coord).rgb;
    vec3 source = sigmaS * incident;
    vec3 added = integratedSource(source, segmentT, segmentLength);
    added *= exp(-max(currentRadiance.a, 0.0) * segmentLength * 0.5);
    imageStore(u_SegmentRadiance, coord, vec4(max(currentRadiance.rgb + added, vec3(0.0)), currentRadiance.a));
}
