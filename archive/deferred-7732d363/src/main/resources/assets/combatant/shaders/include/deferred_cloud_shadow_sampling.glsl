#ifndef COMBATANT_DEFERRED_CLOUD_SHADOW_SAMPLING_GLSL
#define COMBATANT_DEFERRED_CLOUD_SHADOW_SAMPLING_GLSL

const int COMBATANT_CLOUD_SHADOW_MAX_ALTITUDE_SLICES = 16;

float combatant_sample_cloud_shadow_slice(sampler2D shadowMap,
                                           vec2 mapUv,
                                           int sliceIndex,
                                           int sliceCount) {
    ivec2 atlasSize = textureSize(shadowMap, 0);
    int sliceHeight = max(1, atlasSize.y / max(sliceCount, 1));
    sliceIndex = clamp(sliceIndex, 0, sliceCount - 1);
    vec2 clampedUv = clamp(mapUv, vec2(0.0), vec2(1.0));
    float x = clampedUv.x * float(max(atlasSize.x - 1, 0)) + 0.5;
    float localY = clampedUv.y * float(max(sliceHeight - 1, 0)) + 0.5;
    float y = float(sliceIndex * sliceHeight) + localY;
    return textureLod(shadowMap, vec2(x / float(atlasSize.x), y / float(atlasSize.y)), 0.0).r;
}

float combatant_sample_cloud_shadow(sampler2D shadowMap,
                                     vec3 relativeWorld,
                                     float worldY,
                                     vec4 mapDomain,
                                     vec4 sunDirection,
                                     vec4 cloudBounds) {
    if (sunDirection.w <= 0.5 || sunDirection.y <= 0.02) return 1.0;
    float minCloudY = cloudBounds.x;
    float maxCloudY = cloudBounds.y;
    if (!(maxCloudY > minCloudY) || worldY >= maxCloudY) return 1.0;

    vec3 sun = normalize(sunDirection.xyz);
    vec2 referenceRelative = relativeWorld.xz - sun.xz * (worldY - mapDomain.w) / sun.y;
    vec2 mapUv = (referenceRelative - mapDomain.xy) / max(mapDomain.z, 1e-4);
    if (any(lessThan(mapUv, vec2(0.0))) || any(greaterThan(mapUv, vec2(1.0)))) return 1.0;

    int sliceCount = clamp(int(cloudBounds.w), 1, COMBATANT_CLOUD_SHADOW_MAX_ALTITUDE_SLICES);
    float height01 = clamp((worldY - minCloudY) / max(maxCloudY - minCloudY, 1.0), 0.0, 1.0);
    float slicePosition = height01 * float(max(sliceCount - 1, 0));
    int slice0 = int(floor(slicePosition));
    int slice1 = min(slice0 + 1, sliceCount - 1);
    float sliceBlend = fract(slicePosition);
    float visibility0 = combatant_sample_cloud_shadow_slice(shadowMap, mapUv, slice0, sliceCount);
    float visibility1 = combatant_sample_cloud_shadow_slice(shadowMap, mapUv, slice1, sliceCount);
    return clamp(mix(visibility0, visibility1, sliceBlend), 0.0, 1.0);
}

#endif
