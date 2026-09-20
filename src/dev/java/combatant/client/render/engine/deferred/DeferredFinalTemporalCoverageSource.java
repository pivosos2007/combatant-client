/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the raster-to-single-sample boundary for final temporal consumers. Raster producers write
 * scene-sample-count velocity/validity/reactive attachments; this source resolves them as a paired
 * semantic contract and overlays them on the pre-translucency signals.
 */
final class DeferredFinalTemporalCoverageSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SINGLE = id("deferred/final_temporal_coverage_single");
    private static final Identifier MSAA = id("deferred/final_temporal_coverage_msaa");
    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline single;
    private RhiComputePipeline msaa;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.temporal.raster-coverage.begin", DeferredStage.WATER_SURFACE)
                .priority(-1000)
                .write(DeferredResource.RASTER_MOTION_VELOCITY,
                        DeferredResource.RASTER_MOTION_VALIDITY,
                        DeferredResource.RASTER_TEMPORAL_COVERAGE,
                        DeferredResource.RASTER_REACTIVE_MASK)
                .when(context -> context.resources().texture(DeferredResource.SCENE_COLOR) != null)
                .execute(this::beginRasterCoverage)
                .build());

        passes.add(DeferredPassSpec.builder("world.temporal.final-coverage.resolve", DeferredStage.POST_TRANSLUCENCY)
                .priority(0)
                .read(DeferredResource.VELOCITY,
                        DeferredResource.MOTION_VALIDITY,
                        DeferredResource.REACTIVE_MASK,
                        DeferredResource.RASTER_MOTION_VELOCITY,
                        DeferredResource.RASTER_MOTION_VALIDITY,
                        DeferredResource.RASTER_TEMPORAL_COVERAGE,
                        DeferredResource.RASTER_REACTIVE_MASK)
                .write(DeferredResource.FINAL_VELOCITY,
                        DeferredResource.FINAL_MOTION_VALIDITY,
                        DeferredResource.FINAL_REACTIVE_MASK)
                .requires(RhiShaderStage.COMPUTE)
                .when(this::available)
                .execute(this::resolve)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        single();
        msaa();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
        DeferredTemporalCoverageBridge.reset();
    }

    private void beginRasterCoverage(DeferredPassContext context) {
        GpuTextureView velocity = requireTexture(context, DeferredResource.RASTER_MOTION_VELOCITY);
        GpuTextureView validity = requireTexture(context, DeferredResource.RASTER_MOTION_VALIDITY);
        GpuTextureView coverage = requireTexture(context, DeferredResource.RASTER_TEMPORAL_COVERAGE);
        GpuTextureView reactive = requireTexture(context, DeferredResource.RASTER_REACTIVE_MASK);
        int sampleCount = samples(velocity);
        if (samples(validity) != sampleCount || samples(coverage) != sampleCount || samples(reactive) != sampleCount) {
            throw new IllegalStateException("Temporal raster coverage sample-count mismatch");
        }

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        Vector4f zero = new Vector4f(0.0f);
        encoder.clearColorTexture(velocity.texture(), zero);
        encoder.clearColorTexture(validity.texture(), zero);
        encoder.clearColorTexture(coverage.texture(), zero);
        encoder.clearColorTexture(reactive.texture(), zero);
        DeferredTemporalCoverageBridge.publish(context.frame().frameId(), velocity, validity, coverage, reactive, sampleCount);
    }

    private boolean available(DeferredPassContext context) {
        GpuTextureView rasterValidity = context.resources().texture(DeferredResource.RASTER_MOTION_VALIDITY);
        GpuTextureView rasterCoverage = context.resources().texture(DeferredResource.RASTER_TEMPORAL_COVERAGE);
        return context.isValid(DeferredResource.VELOCITY)
                && context.isValid(DeferredResource.MOTION_VALIDITY)
                && context.isValid(DeferredResource.REACTIVE_MASK)
                && rasterValidity != null
                && rasterCoverage != null;
    }

    private void resolve(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView baseVelocity = requireTexture(context, DeferredResource.VELOCITY);
        GpuTextureView baseValidity = requireTexture(context, DeferredResource.MOTION_VALIDITY);
        GpuTextureView baseReactive = requireTexture(context, DeferredResource.REACTIVE_MASK);
        GpuTextureView rasterVelocity = requireTexture(context, DeferredResource.RASTER_MOTION_VELOCITY);
        GpuTextureView rasterValidity = requireTexture(context, DeferredResource.RASTER_MOTION_VALIDITY);
        GpuTextureView rasterCoverage = requireTexture(context, DeferredResource.RASTER_TEMPORAL_COVERAGE);
        GpuTextureView rasterReactive = requireTexture(context, DeferredResource.RASTER_REACTIVE_MASK);
        RhiStorageImage finalVelocity = requireImage(context, DeferredResource.FINAL_VELOCITY);
        RhiStorageImage finalValidity = requireImage(context, DeferredResource.FINAL_MOTION_VALIDITY);
        RhiStorageImage finalReactive = requireImage(context, DeferredResource.FINAL_REACTIVE_MASK);

        int sampleCount = samples(rasterValidity);
        if (samples(rasterVelocity) != sampleCount || samples(rasterCoverage) != sampleCount
                || samples(rasterReactive) != sampleCount) {
            throw new IllegalStateException("Temporal raster resolve requires matching velocity/validity/coverage/reactive samples");
        }

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant final temporal coverage resolve",
                sampleCount > 1 ? msaa() : single(),
                groups(finalVelocity.descriptor().width()), groups(finalVelocity.descriptor().height()), 1,
                List.of(),
                List.of(
                        new SampledTextureBinding(0, baseVelocity, nearest),
                        new SampledTextureBinding(1, baseValidity, nearest),
                        new SampledTextureBinding(2, baseReactive, nearest),
                        new SampledTextureBinding(3, rasterVelocity, nearest),
                        new SampledTextureBinding(4, rasterValidity, nearest),
                        new SampledTextureBinding(5, rasterReactive, nearest),
                        new SampledTextureBinding(6, rasterCoverage, nearest)
                ),
                List.of(
                        new StorageImageBinding(7, finalVelocity, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(8, finalValidity, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(9, finalReactive, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline single() {
        if (owner == null) throw new IllegalStateException("Final temporal coverage source has no RHI owner");
        if (single == null) {
            single = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-final-temporal-coverage-single", SINGLE, LAYOUT));
        }
        return single;
    }

    private RhiComputePipeline msaa() {
        if (owner == null) throw new IllegalStateException("Final temporal coverage source has no RHI owner");
        if (msaa == null) {
            msaa = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-final-temporal-coverage-msaa", MSAA, LAYOUT));
        }
        return msaa;
    }

    private void closeOwned() {
        if (single != null) {
            try { single.close(); } catch (Throwable ignored) { }
            single = null;
        }
        if (msaa != null) {
            try { msaa.close(); } catch (Throwable ignored) { }
            msaa = null;
        }
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
        DeferredTemporalCoverageBridge.reset();
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return value;
    }

    private static int samples(GpuTextureView view) {
        return view.texture() instanceof IMsaaTexture msaa ? Math.max(1, msaa.combatant$getSamples()) : 1;
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
