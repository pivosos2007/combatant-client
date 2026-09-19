/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import org.jetbrains.annotations.Nullable;

/**
 * Typed debug-view identities. The compositor resolves resources by this table; pass names and
 * string conventions are never used to infer semantics.
 */
public enum DeferredDebugView {
    OFF(null, SourceKind.NONE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, null),

    GBUFFER_BASE(DeferredResource.GBUFFER_SURFACE, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, null),
    GBUFFER_NORMAL_ENCODED(DeferredResource.GBUFFER_GEOMETRY, SourceKind.TEXTURE, DeferredDebugDecodeMode.NORMAL_OCT_ENCODED, -1, null),
    GBUFFER_NORMAL(DeferredResource.GBUFFER_GEOMETRY, SourceKind.TEXTURE, DeferredDebugDecodeMode.NORMAL_OCT, -1, null),
    GBUFFER_NORMAL_LENGTH_ERROR(DeferredResource.GBUFFER_GEOMETRY, SourceKind.TEXTURE, DeferredDebugDecodeMode.NORMAL_LENGTH_ERROR, -1, null),
    GBUFFER_LIGHT_BASELINE(DeferredResource.GBUFFER_GEOMETRY, SourceKind.TEXTURE, DeferredDebugDecodeMode.LIGHT_PAIR, -1, null),
    GBUFFER_MATERIAL_AO(DeferredResource.GBUFFER_SURFACE, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 3, null),
    GBUFFER_ROUGHNESS(DeferredResource.GBUFFER_MATERIAL, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 0, null),
    GBUFFER_METALLIC(DeferredResource.GBUFFER_MATERIAL, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 1, null),
    GBUFFER_F0(DeferredResource.GBUFFER_MATERIAL, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 2, null),
    GBUFFER_MATERIAL(DeferredResource.GBUFFER_MATERIAL, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, null),
    GBUFFER_MATERIAL_ID(DeferredResource.GBUFFER_MATERIAL_ID, SourceKind.UINT_TEXTURE, DeferredDebugDecodeMode.INTEGER_ID, -1, null),
    GBUFFER_DEPTH(DeferredResource.GBUFFER_DEPTH, SourceKind.TEXTURE, DeferredDebugDecodeMode.DEPTH, 0, null),
    GBUFFER_DEPTH_RAW(DeferredResource.GBUFFER_DEPTH, SourceKind.TEXTURE, DeferredDebugDecodeMode.DEPTH, 0, null),
    GBUFFER_DEPTH_LINEAR(DeferredResource.GBUFFER_DEPTH, SourceKind.TEXTURE, DeferredDebugDecodeMode.LINEAR_DEPTH, 0, null),
    RESOLVED_DEPTH_RAW(DeferredResource.RESOLVED_DEPTH, SourceKind.TEXTURE, DeferredDebugDecodeMode.DEPTH, 0, null),

    REFLECTION_ELIGIBILITY_MASK(null, SourceKind.SHARED_INPUTS, DeferredDebugDecodeMode.SHARED_REFLECTION_ELIGIBILITY, -1, null),
    RECONSTRUCTED_VIEW_POSITION(null, SourceKind.SHARED_INPUTS, DeferredDebugDecodeMode.SHARED_RECONSTRUCTED_POSITION, -1, null),
    REPROJECTED_UV_ERROR(null, SourceKind.SHARED_INPUTS, DeferredDebugDecodeMode.SHARED_REPROJECTION_ERROR, -1, null),
    DEPTH_DISCONTINUITY(null, SourceKind.SHARED_INPUTS, DeferredDebugDecodeMode.SHARED_DEPTH_DISCONTINUITY, -1, null),
    RENDER_TO_OUTPUT_UV_SCALE(null, SourceKind.SHARED_INPUTS, DeferredDebugDecodeMode.SHARED_UV_SCALE, -1, null),

    FINAL_VELOCITY(DeferredResource.FINAL_VELOCITY, SourceKind.TEXTURE, DeferredDebugDecodeMode.SIGNED_VELOCITY, -1, null),
    MOTION_VALIDITY(DeferredResource.FINAL_MOTION_VALIDITY, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, null),
    REACTIVE(DeferredResource.FINAL_REACTIVE_MASK, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, null),
    DISOCCLUSION(DeferredResource.FINAL_DISOCCLUSION_MASK, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, null),
    TAA_HISTORY_WEIGHT(DeferredResource.TAA_HISTORY_WEIGHT, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.TAA),
    TAA_REJECTION(DeferredResource.TAA_REJECTION_MASK, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.TAA),
    TAA_CONFIDENCE(DeferredResource.TAA_CONFIDENCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.TAA),
    TAA_LOCK(DeferredResource.TAA_LOCK, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.TAA),

    SHADOW_DEPTH_ATLAS(DeferredResource.SHADOW_DEPTH, SourceKind.TEXTURE, DeferredDebugDecodeMode.DEPTH, 0, DeferredFeature.SHADOWS),
    DIRECTIONAL_SHADOW_HARD_VISIBILITY(DeferredResource.SHADOW_HARD_VISIBILITY, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 0, DeferredFeature.SHADOWS),
    SHADOW_CASCADE_INDEX(DeferredResource.SHADOW_CASCADE_INDEX, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 0, DeferredFeature.SHADOWS),
    DIRECTIONAL_SHADOW_VISIBILITY(DeferredResource.SHADOW_CASCADE_VISIBILITY, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 0, DeferredFeature.SHADOWS),
    CONTACT_SHADOW(null, SourceKind.NONE, DeferredDebugDecodeMode.SCALAR, 0, DeferredFeature.CONTACT_SHADOWS),
    GTAO_RAW(DeferredResource.GTAO_RAW_SIGNAL, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 0, DeferredFeature.GTAO),
    GTAO_HORIZON_ANGLE(DeferredResource.GTAO_RAW_DIAGNOSTICS, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.GTAO),
    GTAO_SAMPLE_VALIDITY(DeferredResource.GTAO_RAW_DIAGNOSTICS, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 2, DeferredFeature.GTAO),
    GTAO_VIEW_NORMAL(DeferredResource.GTAO_VIEW_NORMAL, SourceKind.TEXTURE, DeferredDebugDecodeMode.SIGNED_VECTOR, -1, DeferredFeature.GTAO),
    GTAO_VIEW_DEPTH(DeferredResource.GTAO_VIEW_DEPTH, SourceKind.TEXTURE, DeferredDebugDecodeMode.LINEAR_VALUE, 0, DeferredFeature.GTAO),
    GTAO_RADIUS_FOOTPRINT(DeferredResource.GTAO_RADIUS_FOOTPRINT, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.GTAO),
    GTAO_REPROJECTED_UV(DeferredResource.GTAO_REPROJECTED_UV, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.GTAO),
    GTAO_HISTORY_VALIDITY(DeferredResource.GTAO_HISTORY_VALIDITY, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.GTAO),
    GTAO_DEPTH_REJECTION(DeferredResource.GTAO_DEPTH_REJECTION, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.GTAO),
    GTAO_OFFCENTER_REJECTION(DeferredResource.GTAO_OFFCENTER_REJECTION, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.GTAO),
    GTAO_HISTORY_WEIGHT(DeferredResource.GTAO_HISTORY_WEIGHT, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.GTAO),
    GTAO_HISTORY_AGE(DeferredResource.GTAO_TEMPORAL_AGE, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.GTAO),
    GTAO_TEMPORAL(DeferredResource.GTAO_TEMPORAL_SIGNAL, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 0, DeferredFeature.GTAO),
    AO(DeferredResource.AMBIENT_OCCLUSION, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 0, DeferredFeature.GTAO),
    AO_BENT_NORMAL(DeferredResource.AMBIENT_BENT_NORMAL, SourceKind.TEXTURE, DeferredDebugDecodeMode.SIGNED_VECTOR, -1, DeferredFeature.GTAO),
    INDIRECT(DeferredResource.INDIRECT_LIGHT, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.INDIRECT_LIGHT),
    COLORED_BLOCK_LIGHT(DeferredResource.BLOCK_LIGHT_IRRADIANCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.COLORED_BLOCK_LIGHT),
    SKY_DIFFUSE_IRRADIANCE(DeferredResource.SKY_DIFFUSE_IRRADIANCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.SKY),
    ENVIRONMENT_IRRADIANCE(DeferredResource.ENVIRONMENT_IRRADIANCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, null),
    REFLECTION_COLOR(DeferredResource.REFLECTION_COLOR, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.REFLECTIONS),
    REFLECTION_CONFIDENCE(DeferredResource.REFLECTION_CONFIDENCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.REFLECTIONS),

    WATER_REFLECTION_RAW_CONFIDENCE(DeferredResource.WATER_REFLECTION_TRACE_CONFIDENCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.WATER),
    WATER_REFLECTION_FINAL_CONFIDENCE(DeferredResource.WATER_REFLECTION_CONFIDENCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.WATER),
    WATER_REPROJECTION(DeferredResource.WATER_REFLECTION_REPROJECTION, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.WATER),
    WATER_MOTION_VALIDITY(DeferredResource.WATER_REFLECTION_REPROJECTION, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 2, DeferredFeature.WATER),
    WATER_TEMPORAL_REJECTION(DeferredResource.WATER_REFLECTION_REJECTION, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.WATER),
    WATER_MEDIUM_BOUNDARY(DeferredResource.WATER_MEDIUM_BOUNDARY, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.WATER),

    BLOOM(DeferredResource.BLOOM_COLOR, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.BLOOM),
    DOF_COC(DeferredResource.DOF_COC, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 0, DeferredFeature.DEPTH_OF_FIELD),
    MOTION_TILE_MAX(DeferredResource.MOTION_TILE_MAX, SourceKind.TEXTURE, DeferredDebugDecodeMode.SIGNED_VELOCITY, -1, DeferredFeature.MOTION_BLUR),
    MOTION_NEIGHBOR_MAX(DeferredResource.MOTION_NEIGHBOR_MAX, SourceKind.TEXTURE, DeferredDebugDecodeMode.SIGNED_VELOCITY, -1, DeferredFeature.MOTION_BLUR),
    POST_HDR_COLOR(DeferredResource.POST_HDR_COLOR, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, null),
    EXPOSURE_VALUE(DeferredResource.EXPOSURE, SourceKind.BUFFER, DeferredDebugDecodeMode.EXPOSURE_VALUE, -1, DeferredFeature.EXPOSURE),
    EXPOSURE_HISTOGRAM(DeferredResource.EXPOSURE_HISTOGRAM, SourceKind.BUFFER, DeferredDebugDecodeMode.HISTOGRAM, -1, DeferredFeature.EXPOSURE),

    FROXEL_SCATTERING(DeferredResource.FROXEL_MEDIA_PROPERTIES, SourceKind.VOLUME, DeferredDebugDecodeMode.VOLUME_COLOR, -1, DeferredFeature.PARTICIPATING_MEDIA),
    FROXEL_ANISOTROPY(DeferredResource.FROXEL_MEDIA_PROPERTIES, SourceKind.VOLUME, DeferredDebugDecodeMode.VOLUME_SCALAR, 3, DeferredFeature.PARTICIPATING_MEDIA),
    FROXEL_TRANSMITTANCE(DeferredResource.FROXEL_MEDIA_SEGMENT_TRANSMITTANCE, SourceKind.VOLUME, DeferredDebugDecodeMode.VOLUME_COLOR, -1, DeferredFeature.PARTICIPATING_MEDIA),
    FROXEL_INTEGRATED_RADIANCE(DeferredResource.FROXEL_MEDIA_INTEGRATED_RADIANCE, SourceKind.VOLUME, DeferredDebugDecodeMode.VOLUME_COLOR, -1, DeferredFeature.PARTICIPATING_MEDIA),

    // Hooks only. The cloud/sky/weather branch may publish/retarget these without changing this API.
    CLOUD_RADIANCE(DeferredResource.CLOUD_TEMPORAL_RADIANCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.CLOUDS),
    CLOUD_CONFIDENCE(DeferredResource.CLOUD_TEMPORAL_CONFIDENCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.CLOUDS),
    CLOUD_OCCUPANCY_SLICE(DeferredResource.CLOUD_OCCUPANCY, SourceKind.VOLUME, DeferredDebugDecodeMode.VOLUME_SCALAR, 0, DeferredFeature.CLOUDS),
    SKY_RADIANCE(DeferredResource.SKY_RADIANCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.SKY),
    WEATHER_SKY_VISIBILITY(DeferredResource.SKY_VISIBILITY, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 0, DeferredFeature.WEATHER);

    public enum SourceKind { NONE, TEXTURE, UINT_TEXTURE, VOLUME, BUFFER, SHARED_INPUTS }

    private final @Nullable DeferredResource resource;
    private final SourceKind sourceKind;
    private final DeferredDebugDecodeMode decodeMode;
    private final int channel;
    private final @Nullable DeferredFeature feature;

    DeferredDebugView(@Nullable DeferredResource resource, SourceKind sourceKind,
                      DeferredDebugDecodeMode decodeMode, int channel, @Nullable DeferredFeature feature) {
        this.resource = resource;
        this.sourceKind = sourceKind;
        this.decodeMode = decodeMode;
        this.channel = channel;
        this.feature = feature;
    }

    public @Nullable DeferredResource resource() { return resource; }
    public SourceKind sourceKind() { return sourceKind; }
    public DeferredDebugDecodeMode decodeMode() { return decodeMode; }
    public int channel() { return channel; }
    public @Nullable DeferredFeature feature() { return feature; }
}
