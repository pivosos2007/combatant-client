/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import combatant.client.render.engine.framegraph.FrameGraphResourceKey;

import static combatant.client.render.engine.deferred.DeferredTextureSpec.SamplePolicy.MATCH_SCENE;

/** Logical world resources. Physical images/buffers are allocated separately by the RHI. */
public enum DeferredResource {
    SCENE_COLOR(FrameGraphResourceKey.external("world.scene_color"), null),
    MAIN_DEPTH(FrameGraphResourceKey.external("world.main_depth"), null),
    GBUFFER_SURFACE(FrameGraphResourceKey.transientTexture("world.gbuffer.surface"),
            DeferredTextureSpec.attachment(GpuFormat.RGBA8_UNORM, MATCH_SCENE)),
    GBUFFER_GEOMETRY(FrameGraphResourceKey.transientTexture("world.gbuffer.geometry"),
            DeferredTextureSpec.attachment(GpuFormat.RGBA8_UNORM, MATCH_SCENE)),
    GBUFFER_AUXILIARY(FrameGraphResourceKey.transientTexture("world.gbuffer.auxiliary"),
            DeferredTextureSpec.attachment(GpuFormat.RGBA8_UNORM, MATCH_SCENE)),
    GBUFFER_MATERIAL(FrameGraphResourceKey.transientTexture("world.gbuffer.material"),
            DeferredTextureSpec.attachment(GpuFormat.RGBA8_UNORM, MATCH_SCENE)),
    GBUFFER_MATERIAL_ID(FrameGraphResourceKey.transientTexture("world.gbuffer.material_id"),
            DeferredTextureSpec.attachment(GpuFormat.R32_UINT, MATCH_SCENE)),
    /** Canonical single-sample pre-translucency velocity: currentUV - previousUV. */
    VELOCITY(FrameGraphResourceKey.transientTexture("world.velocity"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RG16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Validity paired with {@link #VELOCITY}; zero never means "stationary and valid" by itself. */
    MOTION_VALIDITY(FrameGraphResourceKey.transientTexture("world.velocity.validity"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Raster producer attachment. Matches scene MSAA and is resolved explicitly after translucency. */
    RASTER_MOTION_VELOCITY(FrameGraphResourceKey.transientTexture("world.temporal.raster_velocity"),
            DeferredTextureSpec.attachment(GpuFormat.RG16_FLOAT, MATCH_SCENE)),
    /** Per-sample raster motion validity, paired with {@link #RASTER_MOTION_VELOCITY}. */
    RASTER_MOTION_VALIDITY(FrameGraphResourceKey.transientTexture("world.temporal.raster_motion_validity"),
            DeferredTextureSpec.attachment(GpuFormat.R8_UNORM, MATCH_SCENE)),
    /** Per-sample raster ownership. Distinguishes uncovered from covered-with-invalid-motion. */
    RASTER_TEMPORAL_COVERAGE(FrameGraphResourceKey.transientTexture("world.temporal.raster_coverage"),
            DeferredTextureSpec.attachment(GpuFormat.R8_UNORM, MATCH_SCENE)),
    /** Per-sample explicit reactive signal for water/translucency/particles/portals. */
    RASTER_REACTIVE_MASK(FrameGraphResourceKey.transientTexture("world.temporal.raster_reactive"),
            DeferredTextureSpec.attachment(GpuFormat.R8_UNORM, MATCH_SCENE)),
    /** Canonical single-sample post-translucency velocity for final temporal consumers. */
    FINAL_VELOCITY(FrameGraphResourceKey.transientTexture("world.temporal.final_velocity"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RG16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Canonical post-translucency validity paired with {@link #FINAL_VELOCITY}. */
    FINAL_MOTION_VALIDITY(FrameGraphResourceKey.transientTexture("world.temporal.final_motion_validity"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL)),
    RESOLVED_DEPTH(FrameGraphResourceKey.transientTexture("world.depth.resolved"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Scene depth at the final temporal-consumer boundary, after translucent world submission. */
    FINAL_RESOLVED_DEPTH(FrameGraphResourceKey.transientTexture("world.depth.final_resolved"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    GBUFFER_DEPTH(FrameGraphResourceKey.transientTexture("world.gbuffer.depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    DEPTH_PYRAMID(FrameGraphResourceKey.transientTexture("world.depth.pyramid"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, true)),
    /** Pre-translucency disocclusion used by SSR/AO/other opaque temporal consumers. */
    DISOCCLUSION_MASK(FrameGraphResourceKey.transientTexture("world.temporal.disocclusion"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    /** Final-frame disocclusion paired with FINAL_* motion/depth for future TAA/TAAU. */
    FINAL_DISOCCLUSION_MASK(FrameGraphResourceKey.transientTexture("world.temporal.final_disocclusion"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    /**
     * Compatibility name for the pre-translucency reactive mask. Opaque temporal consumers use
     * this resource; final-frame consumers must use {@link #FINAL_REACTIVE_MASK}.
     */
    REACTIVE_MASK(FrameGraphResourceKey.transientTexture("world.temporal.reactive"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    /** Reactive coverage at the final temporal boundary from explicit forward producers. */
    FINAL_REACTIVE_MASK(FrameGraphResourceKey.transientTexture("world.temporal.reactive.final"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    SHADOW_DEPTH(FrameGraphResourceKey.transientTexture("world.shadow.depth"), null),
    SHADOW_CASCADE_DATA(FrameGraphResourceKey.transientBuffer("world.shadow.cascades"), null),
    SHADOW_CASCADE_VISIBILITY(FrameGraphResourceKey.transientTexture("world.shadow.cascade_visibility"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.SHADOW_OUTPUT, false)),
    /** Center-tap hard compare before spatial filtering; normalization/debug only. */
    SHADOW_HARD_VISIBILITY(FrameGraphResourceKey.transientTexture("world.shadow.hard_visibility"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.SHADOW_OUTPUT, false)),
    /** Selected directional cascade encoded as (index + 1) / (count + 1); zero means no cascade. */
    SHADOW_CASCADE_INDEX(FrameGraphResourceKey.transientTexture("world.shadow.cascade_index"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.SHADOW_OUTPUT, false)),
    CONTACT_SHADOW(FrameGraphResourceKey.transientTexture("world.shadow.contact"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.CONTACT_SHADOW_TRACE, false)),
    SHADOW_COLOR(FrameGraphResourceKey.transientTexture("world.shadow.color"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.SHADOW_OUTPUT, false)),
    AMBIENT_OCCLUSION(FrameGraphResourceKey.transientTexture("world.ambient_occlusion"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    AMBIENT_BENT_NORMAL(FrameGraphResourceKey.transientTexture("world.ambient_bent_normal"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    SKY_VISIBILITY(FrameGraphResourceKey.transientTexture("world.weather.sky_visibility"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    PRECIPITATION_EXPOSURE(FrameGraphResourceKey.transientTexture("world.weather.precipitation_exposure"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    SURFACE_WEATHER_STATE(FrameGraphResourceKey.transientTexture("world.weather.surface_state"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    SKY_RADIANCE(FrameGraphResourceKey.persistentTexture("world.environment.sky_radiance"), null),
    ATMOSPHERE_TRANSMITTANCE(FrameGraphResourceKey.persistentTexture("world.environment.atmosphere_transmittance"), null),
    ATMOSPHERE_MULTI_SCATTERING(FrameGraphResourceKey.persistentTexture("world.environment.atmosphere_multi_scattering"), null),
    AERIAL_PERSPECTIVE(FrameGraphResourceKey.persistentTexture("world.environment.aerial_perspective"), null),
    AERIAL_TRANSMITTANCE(FrameGraphResourceKey.persistentTexture("world.environment.aerial_transmittance"), null),
    SKY_DIFFUSE_SH(FrameGraphResourceKey.persistentBuffer("world.environment.sky_diffuse_sh"), null),
    SKY_SPECULAR_RADIANCE(FrameGraphResourceKey.persistentTexture("world.environment.sky_specular_radiance"), null),
    SKY_ENVIRONMENT_STATE(FrameGraphResourceKey.persistentBuffer("world.environment.sky_state"), null),
    SKY_DIFFUSE_IRRADIANCE(FrameGraphResourceKey.transientTexture("world.environment.sky_diffuse_irradiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    BLOCK_LIGHT_VOLUME_SEED(FrameGraphResourceKey.transientTexture("world.block_light.volume_seed"), null),
    BLOCK_LIGHT_VOLUME_RADIANCE(FrameGraphResourceKey.transientTexture("world.block_light.volume_radiance"), null),
    BLOCK_LIGHT_IRRADIANCE(FrameGraphResourceKey.transientTexture("world.environment.block_light_irradiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    ENVIRONMENT_IRRADIANCE(FrameGraphResourceKey.transientTexture("world.environment.irradiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    DIRECT_LIGHTING_COLOR(FrameGraphResourceKey.transientTexture("world.direct_lighting_color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    LOCAL_LIGHT_DATA(FrameGraphResourceKey.transientBuffer("world.local_light.data"), null),
    LOCAL_LIGHT_TILE_COUNTS(FrameGraphResourceKey.transientBuffer("world.local_light.tile_counts"), null),
    LOCAL_LIGHT_TILE_INDICES(FrameGraphResourceKey.transientBuffer("world.local_light.tile_indices"), null),
    LOCAL_LIGHT_CULL_DATA(FrameGraphResourceKey.transientBuffer("world.local_light.cull_data"), null),
    LOCAL_LIGHT_SHADOW_DEPTH(FrameGraphResourceKey.transientTexture("world.local_light.shadow_depth"), null),
    LOCAL_LIGHT_SHADOW_DATA(FrameGraphResourceKey.transientBuffer("world.local_light.shadow_data"), null),
    LOCAL_LIGHTING_COLOR(FrameGraphResourceKey.transientTexture("world.local_lighting_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    OPAQUE_BASE_RADIANCE(FrameGraphResourceKey.transientTexture("world.opaque_base_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    OPAQUE_REFLECTED_RADIANCE(FrameGraphResourceKey.transientTexture("world.opaque_reflected_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    SKY_COMPOSITED_RADIANCE(FrameGraphResourceKey.transientTexture("world.environment.sky_composited_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    /** Single-sample screen-space water/air boundary consumed by froxel medium injection. */
    WATER_MEDIUM_BOUNDARY(FrameGraphResourceKey.transientTexture("world.water.medium_boundary"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    CLOUD_RADIANCE(FrameGraphResourceKey.transientTexture("world.cloud.radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_DEPTH(FrameGraphResourceKey.transientTexture("world.cloud.depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_REPROJECTION_DATA(FrameGraphResourceKey.transientTexture("world.cloud.reprojection_data"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_TEMPORAL_RADIANCE(FrameGraphResourceKey.transientTexture("world.cloud.temporal_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_TEMPORAL_DEPTH(FrameGraphResourceKey.transientTexture("world.cloud.temporal_depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_TEMPORAL_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.cloud.temporal_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    HISTORY_CLOUD_RADIANCE(FrameGraphResourceKey.persistentTexture("world.history.cloud_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    HISTORY_CLOUD_REPROJECTION_DEPTH(FrameGraphResourceKey.persistentTexture("world.history.cloud_reprojection_depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    HISTORY_CLOUD_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.cloud_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_HIGH_RADIANCE(FrameGraphResourceKey.transientTexture("world.cloud.high.radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_HIGH_DEPTH(FrameGraphResourceKey.transientTexture("world.cloud.high.depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_HIGH_REPROJECTION_DATA(FrameGraphResourceKey.transientTexture("world.cloud.high.reprojection_data"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_HIGH_TEMPORAL_RADIANCE(FrameGraphResourceKey.transientTexture("world.cloud.high.temporal_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_HIGH_TEMPORAL_DEPTH(FrameGraphResourceKey.transientTexture("world.cloud.high.temporal_depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_HIGH_TEMPORAL_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.cloud.high.temporal_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    HISTORY_CLOUD_HIGH_RADIANCE(FrameGraphResourceKey.persistentTexture("world.history.cloud_high_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    HISTORY_CLOUD_HIGH_REPROJECTION_DEPTH(FrameGraphResourceKey.persistentTexture("world.history.cloud_high_reprojection_depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    HISTORY_CLOUD_HIGH_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.cloud_high_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_CONVECTIVE_RADIANCE(FrameGraphResourceKey.transientTexture("world.cloud.convective.radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_CONVECTIVE_DEPTH(FrameGraphResourceKey.transientTexture("world.cloud.convective.depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_CONVECTIVE_REPROJECTION_DATA(FrameGraphResourceKey.transientTexture("world.cloud.convective.reprojection_data"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_CONVECTIVE_TEMPORAL_RADIANCE(FrameGraphResourceKey.transientTexture("world.cloud.convective.temporal_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_CONVECTIVE_TEMPORAL_DEPTH(FrameGraphResourceKey.transientTexture("world.cloud.convective.temporal_depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_CONVECTIVE_TEMPORAL_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.cloud.convective.temporal_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    HISTORY_CLOUD_CONVECTIVE_RADIANCE(FrameGraphResourceKey.persistentTexture("world.history.cloud_convective_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    HISTORY_CLOUD_CONVECTIVE_REPROJECTION_DEPTH(FrameGraphResourceKey.persistentTexture("world.history.cloud_convective_reprojection_depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    HISTORY_CLOUD_CONVECTIVE_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.cloud_convective_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.CLOUD_RENDER, false)),
    CLOUD_OCCUPANCY(FrameGraphResourceKey.transientVolume("world.cloud.occupancy"), null),
    CLOUD_SHADOW_MAP(FrameGraphResourceKey.transientTexture("world.cloud.shadow_map"),
            DeferredTextureSpec.fixedCompute(GpuFormat.R32_FLOAT,
                    DeferredCloudConfig.current().shadowMapResolution(),
                    DeferredCloudConfig.current().shadowMapResolution()
                            * DeferredCloudConfig.current().shadowAltitudeSlices(), false)),
    CLOUD_SHADOW_VISIBILITY(FrameGraphResourceKey.transientTexture("world.cloud.shadow_visibility"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    FROXEL_MEDIA_SEGMENT_RADIANCE(FrameGraphResourceKey.transientVolume("world.media.froxel.segment_radiance"), null),
    FROXEL_MEDIA_SEGMENT_TRANSMITTANCE(FrameGraphResourceKey.transientVolume("world.media.froxel.segment_transmittance"), null),
    FROXEL_MEDIA_PROPERTIES(FrameGraphResourceKey.transientVolume("world.media.froxel.properties"), null),
    FROXEL_MEDIA_INTEGRATED_RADIANCE(FrameGraphResourceKey.transientVolume("world.media.froxel.integrated_radiance"), null),
    FROXEL_MEDIA_INTEGRATED_TRANSMITTANCE(FrameGraphResourceKey.transientVolume("world.media.froxel.integrated_transmittance"), null),
    SCENE_RADIANCE(FrameGraphResourceKey.transientTexture("world.scene_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    INDIRECT_TRACE_DATA(FrameGraphResourceKey.transientBuffer("world.indirect_trace_data"), null),
    INDIRECT_TRACE_LIGHT(FrameGraphResourceKey.transientTexture("world.indirect.trace_light"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    INDIRECT_TRACE_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.indirect.trace_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    INDIRECT_LIGHT(FrameGraphResourceKey.transientTexture("world.indirect_light"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    INDIRECT_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.indirect_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    LIGHTING_COLOR(FrameGraphResourceKey.transientTexture("world.lighting_color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    REFLECTION_CASCADE_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.cascade_color"), null),
    REFLECTION_CASCADE_DEPTH(FrameGraphResourceKey.transientTexture("world.reflection.cascade_depth"), null),
    REFLECTION_CASCADE_DATA(FrameGraphResourceKey.transientBuffer("world.reflection.cascade_data"), null),
    REFLECTION_TRACE_DATA(FrameGraphResourceKey.transientBuffer("world.reflection.trace_data"), null),
    REFLECTION_TRACE_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.trace_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_TRACE, false)),
    REFLECTION_TRACE_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.trace_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_TRACE, false)),
    REFLECTION_RESOLVED_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.resolved_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_RESOLVED_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.resolved_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_TEMPORAL_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.temporal_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_TEMPORAL_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.temporal_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    /** Raw dedicated water SSR radiance traced from the actual deformed forward surface. */
    WATER_REFLECTION_TRACE_COLOR(FrameGraphResourceKey.transientTexture("world.water.reflection.trace_color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Confidence of the raw water SSR hit. */
    WATER_REFLECTION_TRACE_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.water.reflection.trace_confidence"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Previous-UV and deformation-motion validity emitted by the water trace pass. */
    WATER_REFLECTION_REPROJECTION(FrameGraphResourceKey.transientTexture("world.water.reflection.reprojection"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Encoded deformed source normal and roughness used by temporal/spatial validation. */
    WATER_REFLECTION_GEOMETRY(FrameGraphResourceKey.transientTexture("world.water.reflection.geometry"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Current/previous source depth and current/previous reflected-hit depth. */
    WATER_REFLECTION_DEPTHS(FrameGraphResourceKey.transientTexture("world.water.reflection.depths"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Water-specific reflection-cascade fallback sampled using the water reflection ray. */
    WATER_REFLECTION_CASCADE_COLOR(FrameGraphResourceKey.transientTexture("world.water.reflection.cascade_color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    WATER_REFLECTION_CASCADE_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.water.reflection.cascade_confidence"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Water SSR after deformation-aware temporal reprojection. */
    WATER_REFLECTION_TEMPORAL_COLOR(FrameGraphResourceKey.transientTexture("world.water.reflection.temporal_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    WATER_REFLECTION_TEMPORAL_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.water.reflection.temporal_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    /** Debug contract: non-zero values identify temporal history rejection. */
    WATER_REFLECTION_REJECTION(FrameGraphResourceKey.transientTexture("world.water.reflection.rejection"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    /** Roughness/geometry-aware filtered dedicated water SSR, still excluding fallback hierarchy. */
    WATER_REFLECTION_FILTERED_COLOR(FrameGraphResourceKey.transientTexture("world.water.reflection.filtered_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    WATER_REFLECTION_FILTERED_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.water.reflection.filtered_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    /** Final stabilized water reflection after SSR -> reflection cascade selection. */
    WATER_REFLECTION_COLOR(FrameGraphResourceKey.transientTexture("world.water.reflection_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    /** Explicit confidence of the selected water reflection hierarchy level. */
    WATER_REFLECTION_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.water.reflection_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    TRANSLUCENT_COLOR(FrameGraphResourceKey.transientTexture("world.translucent.color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    TRANSLUCENT_DEPTH(FrameGraphResourceKey.transientTexture("world.translucent.depth"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    HISTORY_COLOR(FrameGraphResourceKey.persistentTexture("world.history.color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    HISTORY_DEPTH(FrameGraphResourceKey.persistentTexture("world.history.depth"),
            DeferredTextureSpec.computeAttachment(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    /** Final TAA/TAAU resolve at presentation resolution. */
    TAA_RESOLVED_COLOR(FrameGraphResourceKey.transientTexture("world.temporal.taa.resolved"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.OUTPUT)),
    /** Per-pixel temporal confidence produced by the final scene resolve. */
    TAA_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.temporal.taa.confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    /** Per-pixel lock state/age proxy produced by the final scene resolve. */
    TAA_LOCK(FrameGraphResourceKey.transientTexture("world.temporal.taa.lock"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    /** Debug output: actual history blend weight after every rejection/response policy. */
    TAA_HISTORY_WEIGHT(FrameGraphResourceKey.transientTexture("world.temporal.taa.history_weight"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    /** Debug output: categorical/composite rejection reason for the final temporal consumer. */
    TAA_REJECTION_MASK(FrameGraphResourceKey.transientTexture("world.temporal.taa.rejection"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    HISTORY_TAA_COLOR(FrameGraphResourceKey.persistentTexture("world.history.taa.color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    HISTORY_TAA_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.taa.confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    HISTORY_TAA_LOCK(FrameGraphResourceKey.persistentTexture("world.history.taa.lock"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    HISTORY_REFLECTION(FrameGraphResourceKey.persistentTexture("world.history.reflection"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_HISTORY, false)),
    HISTORY_REFLECTION_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.reflection_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_HISTORY, false)),
    HISTORY_WATER_REFLECTION(FrameGraphResourceKey.persistentTexture("world.history.water_reflection"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    HISTORY_WATER_REFLECTION_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.water_reflection_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    HISTORY_WATER_REFLECTION_SOURCE_DEPTH(FrameGraphResourceKey.persistentTexture("world.history.water_reflection_source_depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    HISTORY_WATER_REFLECTION_HIT_DEPTH(FrameGraphResourceKey.persistentTexture("world.history.water_reflection_hit_depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    HISTORY_INDIRECT(FrameGraphResourceKey.persistentTexture("world.history.indirect"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    HISTORY_INDIRECT_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.indirect_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    /** Transient weighted log-luminance histogram for eye adaptation. */
    EXPOSURE_HISTOGRAM(FrameGraphResourceKey.transientBuffer("world.post.exposure.histogram"), null),
    /** HDR bloom extraction/downsample chain at output-relative bloom resolution. */
    BLOOM_PYRAMID(FrameGraphResourceKey.transientTexture("world.post.bloom.pyramid"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.BLOOM, true)),
    /** Progressive upsample scratch chain. */
    BLOOM_UPSAMPLE(FrameGraphResourceKey.transientTexture("world.post.bloom.upsample"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.BLOOM, true)),
    /** Final HDR bloom radiance, kept separate for future post composition/tonemapping. */
    BLOOM_COLOR(FrameGraphResourceKey.transientTexture("world.post.bloom.color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.BLOOM, false)),
    /** Output-resolution depth sampled at the canonical post-TAA boundary. */
    POST_RESOLVED_DEPTH(FrameGraphResourceKey.transientTexture("world.post.depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    /** Persistent autofocus scalar/state; source-owned until generic persistent buffers are materialized by the graph. */
    DOF_FOCUS_STATE(FrameGraphResourceKey.persistentBuffer("world.post.dof.focus"), null),
    /** HDR result after depth-of-field. */
    DOF_COLOR(FrameGraphResourceKey.transientTexture("world.post.dof.color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    /** Signed/normalized circle-of-confusion diagnostic, exposed to the renderer debug layer. */
    DOF_COC(FrameGraphResourceKey.transientTexture("world.post.dof.coc"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    /** Per-tile largest valid final velocity in output pixels. */
    MOTION_TILE_MAX(FrameGraphResourceKey.transientTexture("world.post.motion.tile_max"),
            DeferredTextureSpec.compute(GpuFormat.RG16_FLOAT, DeferredTextureSpec.ResolutionClass.MOTION_TILE, false)),
    /** Neighbor-dilated tile velocity used by the depth-aware gather. */
    MOTION_NEIGHBOR_MAX(FrameGraphResourceKey.transientTexture("world.post.motion.neighbor_max"),
            DeferredTextureSpec.compute(GpuFormat.RG16_FLOAT, DeferredTextureSpec.ResolutionClass.MOTION_TILE, false)),
    /** Canonical HDR camera-post output before future grading/tonemap. */
    POST_HDR_COLOR(FrameGraphResourceKey.transientTexture("world.post.hdr.color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.OUTPUT, false)),
    /**
     * Generic non-mutating debug presentation target at output resolution.
     *
     * <p>The compositor writes this as a storage image and may also clear it to an explicit
     * unavailable sentinel. The clear operation is a render-attachment operation in Minecraft's
     * GPU API, so the target must advertise both usages. Debug source textures remain sample-only;
     * only the canonical presentation target owns this broader usage contract.</p>
     */
    DEBUG_PRESENTATION(FrameGraphResourceKey.transientTexture("world.debug.presentation"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.OUTPUT)),
    LIGHT_LIST(FrameGraphResourceKey.transientBuffer("world.light_list"), null),
    INDIRECT_DRAWS(FrameGraphResourceKey.transientBuffer("world.indirect_draws"), null),
    /** Persistent eye-adaptation state; never encoded into a color-history pixel. */
    EXPOSURE(FrameGraphResourceKey.persistentBuffer("world.exposure"), null);

    private final FrameGraphResourceKey key;
    private final DeferredPhysicalResourceSpec physicalSpec;

    DeferredResource(FrameGraphResourceKey key, DeferredPhysicalResourceSpec physicalSpec) {
        this.key = key;
        this.physicalSpec = physicalSpec;
    }

    public FrameGraphResourceKey key() {
        return key;
    }

    public DeferredTextureSpec textureSpec() {
        return physicalSpec instanceof DeferredTextureSpec texture ? texture : null;
    }

    public DeferredPhysicalResourceSpec physicalSpec() {
        return physicalSpec;
    }

    /**
     * True when this deferred resource is physically owned by the generic frame-graph pool.
     * The current terrain G-buffer remains a legacy raster-target path because its MSAA resolve
     * contract still requires RenderTarget wrappers; it is bound explicitly and never duplicated
     * by the graph allocator.
     */
    public boolean graphManagedPhysicalResource() {
        if (physicalSpec == null) return false;
        return switch (this) {
            case GBUFFER_SURFACE, GBUFFER_GEOMETRY, GBUFFER_AUXILIARY, GBUFFER_MATERIAL, GBUFFER_MATERIAL_ID -> false;
            default -> true;
        };
    }
}
