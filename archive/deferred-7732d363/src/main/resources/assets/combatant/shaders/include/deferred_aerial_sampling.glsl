#ifndef COMBATANT_DEFERRED_AERIAL_SAMPLING_GLSL
#define COMBATANT_DEFERRED_AERIAL_SAMPLING_GLSL

const float COMBATANT_AERIAL_PI = 3.14159265358979323846;

vec4 combatant_aerial_texel(sampler2D source, int azimuthIndex, int zenithIndex, int distanceIndex) {
    ivec2 size = textureSize(source, 0);
    int zenithCount = max(1, int(u_Data.aerialLayout.x));
    int azimuthCount = max(1, int(u_Data.aerialLayout.y));
    azimuthIndex = (azimuthIndex % azimuthCount + azimuthCount) % azimuthCount;
    zenithIndex = clamp(zenithIndex, 0, zenithCount - 1);
    distanceIndex = clamp(distanceIndex, 0, max(size.y - 1, 0));
    int x = azimuthIndex * zenithCount + zenithIndex;
    return texelFetch(source, ivec2(clamp(x, 0, max(size.x - 1, 0)), distanceIndex), 0);
}

vec4 combatant_sample_aerial(sampler2D source, vec3 direction, float distanceKm) {
    direction = normalize(direction);
    int zenithCount = max(1, int(u_Data.aerialLayout.x));
    int azimuthCount = max(1, int(u_Data.aerialLayout.y));
    float zenithCoord = clamp(direction.y * 0.5 + 0.5, 0.0, 1.0) * float(zenithCount) - 0.5;
    float azimuth01 = fract(atan(direction.z, direction.x) / (2.0 * COMBATANT_AERIAL_PI) + 1.0);
    float azimuthCoord = azimuth01 * float(azimuthCount) - 0.5;
    float distance01 = sqrt(clamp(distanceKm / max(u_Data.aerialLayout.z, 1e-4), 0.0, 1.0));
    ivec2 aerialSize = textureSize(source, 0);
    float distanceCoord = distance01 * float(aerialSize.y) - 0.5;

    int z0 = int(floor(zenithCoord));
    int a0 = int(floor(azimuthCoord));
    int d0 = int(floor(distanceCoord));
    float zf = fract(zenithCoord);
    float af = fract(azimuthCoord);
    float df = fract(distanceCoord);

    vec4 c000 = combatant_aerial_texel(source, a0, z0, d0);
    vec4 c100 = combatant_aerial_texel(source, a0 + 1, z0, d0);
    vec4 c010 = combatant_aerial_texel(source, a0, z0 + 1, d0);
    vec4 c110 = combatant_aerial_texel(source, a0 + 1, z0 + 1, d0);
    vec4 c001 = combatant_aerial_texel(source, a0, z0, d0 + 1);
    vec4 c101 = combatant_aerial_texel(source, a0 + 1, z0, d0 + 1);
    vec4 c011 = combatant_aerial_texel(source, a0, z0 + 1, d0 + 1);
    vec4 c111 = combatant_aerial_texel(source, a0 + 1, z0 + 1, d0 + 1);

    vec4 near0 = mix(mix(c000, c100, af), mix(c010, c110, af), zf);
    vec4 far0 = mix(mix(c001, c101, af), mix(c011, c111, af), zf);
    return mix(near0, far0, df);
}

#endif
