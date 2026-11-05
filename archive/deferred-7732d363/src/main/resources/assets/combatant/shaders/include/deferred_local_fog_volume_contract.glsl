#ifndef COMBATANT_DEFERRED_LOCAL_FOG_VOLUME_CONTRACT_GLSL
#define COMBATANT_DEFERRED_LOCAL_FOG_VOLUME_CONTRACT_GLSL

#ifndef COMBATANT_LOCAL_FOG_VOLUME_BINDING
#define COMBATANT_LOCAL_FOG_VOLUME_BINDING 13
#endif
#ifndef COMBATANT_LOCAL_FOG_BIN_COUNT_BINDING
#define COMBATANT_LOCAL_FOG_BIN_COUNT_BINDING 14
#endif
#ifndef COMBATANT_LOCAL_FOG_BIN_INDEX_BINDING
#define COMBATANT_LOCAL_FOG_BIN_INDEX_BINDING 15
#endif

struct LocalFogVolume {
    vec4 centerShape;
    vec4 extentDensity;
    vec4 scatteringG;
    vec4 absorptionEdge;
    vec4 emission;
};

layout(std430, binding = COMBATANT_LOCAL_FOG_VOLUME_BINDING) readonly buffer LocalFogVolumes {
    LocalFogVolume volumes[];
} u_LocalFogVolumes;
#ifdef COMBATANT_LOCAL_FOG_BIN_WRITE
layout(std430, binding = COMBATANT_LOCAL_FOG_BIN_COUNT_BINDING) writeonly buffer LocalFogBinCounts {
    uint counts[];
} u_LocalFogBinCounts;
layout(std430, binding = COMBATANT_LOCAL_FOG_BIN_INDEX_BINDING) writeonly buffer LocalFogBinIndices {
    uint indices[];
} u_LocalFogBinIndices;
#else
layout(std430, binding = COMBATANT_LOCAL_FOG_BIN_COUNT_BINDING) readonly buffer LocalFogBinCounts {
    uint counts[];
} u_LocalFogBinCounts;
layout(std430, binding = COMBATANT_LOCAL_FOG_BIN_INDEX_BINDING) readonly buffer LocalFogBinIndices {
    uint indices[];
} u_LocalFogBinIndices;
#endif

uint combatant_local_fog_bin_index(ivec3 froxelCoord, ivec3 froxelDimensions) {
    ivec3 binSize = ivec3(
        max(int(u_Data.localFogBinning.x + 0.5), 1),
        max(int(u_Data.localFogBinning.x + 0.5), 1),
        max(int(u_Data.localFogBinning.y + 0.5), 1)
    );
    ivec3 binCount = (froxelDimensions + binSize - ivec3(1)) / binSize;
    ivec3 bin = clamp(froxelCoord / binSize, ivec3(0), max(binCount - ivec3(1), ivec3(0)));
    return uint((bin.z * binCount.y + bin.y) * binCount.x + bin.x);
}

#endif
