/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.renderpearl.api.GpuFormat;
import combatant.client.render.engine.framegraph.FrameGraphResourceKey;

import static combatant.client.render.engine.deferred.DeferredTextureSpec.SamplePolicy.MATCH_SCENE;

/** Logical world resources. Physical images/buffers are allocated separately by the RHI. */
public enum DeferredResource {
    AMBIENT_BENT_NORMAL(FrameGraphResourceKey.transientTexture("world.ambient_bent_normal"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    AMBIENT_OCCLUSION(FrameGraphResourceKey.transientTexture("world.ambient_occlusion"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.FULL, false)),
    DIRECT_LIGHTING_COLOR(FrameGraphResourceKey.transientTexture("world.direct_lighting_color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    ENVIRONMENT_IRRADIANCE(FrameGraphResourceKey.transientTexture("world.environment.irradiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    GBUFFER_AUXILIARY(FrameGraphResourceKey.external("world.gbuffer.auxiliary"), null),
    GBUFFER_MATERIAL(FrameGraphResourceKey.external("world.gbuffer.material"), null),
    GBUFFER_SURFACE(FrameGraphResourceKey.external("world.gbuffer.surface"), null),
    GTAO_DEPTH_REJECTION(FrameGraphResourceKey.transientTexture("world.gtao.depth_rejection"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_HISTORY_VALIDITY(FrameGraphResourceKey.transientTexture("world.gtao.history_validity"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_HISTORY_WEIGHT(FrameGraphResourceKey.transientTexture("world.gtao.history_weight"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_OFFCENTER_REJECTION(FrameGraphResourceKey.transientTexture("world.gtao.offcenter_rejection"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_RADIUS_FOOTPRINT(FrameGraphResourceKey.transientTexture("world.gtao.radius_footprint"),
            DeferredTextureSpec.compute(GpuFormat.RG16_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_RAW_DIAGNOSTICS(FrameGraphResourceKey.transientTexture("world.gtao.raw_diagnostics"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_RAW_SIGNAL(FrameGraphResourceKey.transientTexture("world.gtao.raw_signal"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_REPROJECTED_UV(FrameGraphResourceKey.transientTexture("world.gtao.reprojected_uv"),
            DeferredTextureSpec.compute(GpuFormat.RG16_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_TEMPORAL_AGE(FrameGraphResourceKey.transientTexture("world.gtao.temporal_age"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_TEMPORAL_DEPTH(FrameGraphResourceKey.transientTexture("world.gtao.temporal_depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_TEMPORAL_SIGNAL(FrameGraphResourceKey.transientTexture("world.gtao.temporal_signal"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_VIEW_DEPTH(FrameGraphResourceKey.transientTexture("world.gtao.view_depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    GTAO_VIEW_NORMAL(FrameGraphResourceKey.transientTexture("world.gtao.view_normal"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    HISTORY_GTAO_AGE(FrameGraphResourceKey.persistentTexture("world.history.gtao.age"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    HISTORY_GTAO_DEPTH(FrameGraphResourceKey.persistentTexture("world.history.gtao.depth"),
            DeferredTextureSpec.compute(GpuFormat.R32_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    HISTORY_GTAO_SIGNAL(FrameGraphResourceKey.persistentTexture("world.history.gtao.signal"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.AMBIENT_OCCLUSION, false)),
    HISTORY_INDIRECT(FrameGraphResourceKey.persistentTexture("world.history.indirect"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    HISTORY_INDIRECT_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.indirect_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    HISTORY_REFLECTION(FrameGraphResourceKey.persistentTexture("world.history.reflection"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_HISTORY, false)),
    HISTORY_REFLECTION_CONFIDENCE(FrameGraphResourceKey.persistentTexture("world.history.reflection_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_HISTORY, false)),
    INDIRECT_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.indirect_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    INDIRECT_LIGHT(FrameGraphResourceKey.transientTexture("world.indirect_light"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    INDIRECT_TRACE_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.indirect.trace_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    INDIRECT_TRACE_DATA(FrameGraphResourceKey.transientBuffer("world.indirect_trace_data"), null),
    INDIRECT_TRACE_LIGHT(FrameGraphResourceKey.transientTexture("world.indirect.trace_light"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.INDIRECT_LIGHT, false)),
    LIGHTING_COLOR(FrameGraphResourceKey.transientTexture("world.lighting_color"),
            DeferredTextureSpec.computeAttachment(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL)),
    LOCAL_LIGHTING_COLOR(FrameGraphResourceKey.transientTexture("world.local_lighting_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    LOCAL_LIGHT_CULL_DATA(FrameGraphResourceKey.transientBuffer("world.local_light.cull_data"), null),
    LOCAL_LIGHT_DATA(FrameGraphResourceKey.transientBuffer("world.local_light.data"), null),
    LOCAL_LIGHT_SHADOW_DATA(FrameGraphResourceKey.transientBuffer("world.local_light.shadow_data"), null),
    LOCAL_LIGHT_SHADOW_DEPTH(FrameGraphResourceKey.transientTexture("world.local_light.shadow_depth"), null),
    LOCAL_LIGHT_TILE_COUNTS(FrameGraphResourceKey.transientBuffer("world.local_light.tile_counts"), null),
    LOCAL_LIGHT_TILE_INDICES(FrameGraphResourceKey.transientBuffer("world.local_light.tile_indices"), null),
    OPAQUE_BASE_RADIANCE(FrameGraphResourceKey.transientTexture("world.opaque_base_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    OPAQUE_REFLECTED_RADIANCE(FrameGraphResourceKey.transientTexture("world.opaque_reflected_radiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
    REFLECTION_CASCADE_COLOR(FrameGraphResourceKey.external("world.reflection.cascade_color"), null),
    REFLECTION_CASCADE_DATA(FrameGraphResourceKey.external("world.reflection.cascade_data"), null),
    REFLECTION_CASCADE_DEPTH(FrameGraphResourceKey.external("world.reflection.cascade_depth"), null),
    REFLECTION_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_RESOLVED_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.resolved_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_RESOLVED_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.resolved_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_TEMPORAL_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.temporal_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_TEMPORAL_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.temporal_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_OUTPUT, false)),
    REFLECTION_TRACE_COLOR(FrameGraphResourceKey.transientTexture("world.reflection.trace_color"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.REFLECTION_TRACE, false)),
    REFLECTION_TRACE_CONFIDENCE(FrameGraphResourceKey.transientTexture("world.reflection.trace_confidence"),
            DeferredTextureSpec.compute(GpuFormat.R8_UNORM, DeferredTextureSpec.ResolutionClass.REFLECTION_TRACE, false)),
    REFLECTION_TRACE_DATA(FrameGraphResourceKey.transientBuffer("world.reflection.trace_data"), null),
    SCENE_COLOR(FrameGraphResourceKey.external("world.scene_color"), null),
    MAIN_DEPTH(FrameGraphResourceKey.external("world.main_depth"), null),
    GBUFFER_GEOMETRY(FrameGraphResourceKey.external("world.gbuffer.geometry"), null),
    GBUFFER_MATERIAL_ID(FrameGraphResourceKey.external("world.gbuffer.material_id"), null),
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
    BLOCK_LIGHT_VOLUME_SEED(FrameGraphResourceKey.transientTexture("world.block_light.volume_seed"), null),
    BLOCK_LIGHT_VOLUME_RADIANCE(FrameGraphResourceKey.transientTexture("world.block_light.volume_radiance"), null),
    BLOCK_LIGHT_IRRADIANCE(FrameGraphResourceKey.transientTexture("world.environment.block_light_irradiance"),
            DeferredTextureSpec.compute(GpuFormat.RGBA16_FLOAT, DeferredTextureSpec.ResolutionClass.FULL, false)),
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
    /** Persistent eye-adaptation state; never encoded into a color-history pixel. */
    EXPOSURE(FrameGraphResourceKey.persistentBuffer("world.exposure"), null)
;

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

    /** True when this internal resource is physically owned by the generic frame-graph pool. */
    public boolean graphManagedPhysicalResource() {
        return physicalSpec != null;
    }
}
