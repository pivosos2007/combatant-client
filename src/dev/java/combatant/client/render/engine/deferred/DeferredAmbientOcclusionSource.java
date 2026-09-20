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
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Independent GTAO pipeline: half-resolution raw horizon evaluation, AO-owned temporal history,
 * then a depth/normal-aware full-resolution presentation for deferred ambient lighting.
 */
final class DeferredAmbientOcclusionSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final float MAX_ACCUMULATED_FRAMES = 10.0f;
    private static final float DEPTH_REJECTION_STRENGTH = 16.0f;
    private static final float OFFCENTER_REJECTION_STRENGTH = 0.25f;

    private static final Identifier RAW_SHADER = id("deferred/ambient_occlusion");
    private static final Identifier TEMPORAL_SHADER = id("deferred/ambient_occlusion_temporal");
    private static final Identifier RESOLVE_SHADER = id("deferred/ambient_occlusion_resolve");
    private static final Identifier HISTORY_SHADER = id("deferred/ambient_occlusion_history_store");

    private static final Std430StructLayout RAW_DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("projection", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .member("params0", Std430Type.VEC4)
            .build();

    private static final Std430StructLayout TEMPORAL_DATA_LAYOUT = Std430StructLayout.builder()
            .member("currentInverseProjection", Std430Type.MAT4)
            .member("previousInverseProjection", Std430Type.MAT4)
            .member("currentInverseView", Std430Type.MAT4)
            .member("currentView", Std430Type.MAT4)
            .member("previousView", Std430Type.MAT4)
            .member("previousProjection", Std430Type.MAT4)
            .member("previousInverseView", Std430Type.MAT4)
            .member("cameraDelta", Std430Type.VEC4)
            .member("depthTransform", Std430Type.VEC4)
            .member("policy", Std430Type.VEC4)
            .build();

    private static final Std430StructLayout RESOLVE_DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout RAW_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout TEMPORAL_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(10, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(12, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(13, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(14, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(15, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout RESOLVE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout HISTORY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline rawPipeline;
    private RhiComputePipeline temporalPipeline;
    private RhiComputePipeline resolvePipeline;
    private RhiComputePipeline historyPipeline;
    private RhiStorageBuffer rawData;
    private RhiStorageBuffer temporalData;
    private RhiStorageBuffer resolveData;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.gtao.raw", DeferredStage.AMBIENT_OCCLUSION)
                .priority(-40)
                .feature(DeferredFeature.GTAO)
                .read(DeferredResource.RESOLVED_DEPTH, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_DEPTH)
                .write(DeferredResource.GTAO_RAW_SIGNAL,
                        DeferredResource.GTAO_RAW_DIAGNOSTICS,
                        DeferredResource.GTAO_VIEW_NORMAL,
                        DeferredResource.GTAO_VIEW_DEPTH,
                        DeferredResource.GTAO_RADIUS_FOOTPRINT)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.GTAO)
                        && context.primaryView().current() != null
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_GEOMETRY)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH))
                .execute(this::evaluateRaw)
                .build());

        passes.add(DeferredPassSpec.builder("world.gtao.temporal", DeferredStage.AMBIENT_OCCLUSION)
                .priority(-30)
                .feature(DeferredFeature.GTAO)
                .read(DeferredResource.GTAO_RAW_SIGNAL,
                        DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_DEPTH)
                .optionalRead(DeferredResource.HISTORY_GTAO_SIGNAL,
                        DeferredResource.HISTORY_GTAO_DEPTH,
                        DeferredResource.HISTORY_GTAO_AGE)
                .write(DeferredResource.GTAO_TEMPORAL_SIGNAL,
                        DeferredResource.GTAO_TEMPORAL_DEPTH,
                        DeferredResource.GTAO_TEMPORAL_AGE,
                        DeferredResource.GTAO_REPROJECTED_UV,
                        DeferredResource.GTAO_HISTORY_VALIDITY,
                        DeferredResource.GTAO_DEPTH_REJECTION,
                        DeferredResource.GTAO_OFFCENTER_REJECTION,
                        DeferredResource.GTAO_HISTORY_WEIGHT)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.GTAO)
                        && context.primaryView().current() != null
                        && context.isValid(DeferredResource.GTAO_RAW_SIGNAL)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_GEOMETRY)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH))
                .execute(this::accumulateTemporal)
                .build());

        passes.add(DeferredPassSpec.builder("world.gtao.resolve", DeferredStage.AMBIENT_OCCLUSION)
                .priority(-20)
                .feature(DeferredFeature.GTAO)
                .read(DeferredResource.GTAO_TEMPORAL_SIGNAL,
                        DeferredResource.GTAO_TEMPORAL_DEPTH,
                        DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_DEPTH)
                .write(DeferredResource.AMBIENT_OCCLUSION, DeferredResource.AMBIENT_BENT_NORMAL)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.GTAO)
                        && context.primaryView().current() != null
                        && context.isValid(DeferredResource.GTAO_TEMPORAL_SIGNAL)
                        && context.isValid(DeferredResource.GTAO_TEMPORAL_DEPTH)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_GEOMETRY)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH))
                .execute(this::resolvePresentation)
                .build());

        passes.add(DeferredPassSpec.builder("world.gtao.history", DeferredStage.AMBIENT_OCCLUSION)
                .priority(-10)
                .feature(DeferredFeature.GTAO)
                .read(DeferredResource.GTAO_TEMPORAL_SIGNAL,
                        DeferredResource.GTAO_TEMPORAL_DEPTH,
                        DeferredResource.GTAO_TEMPORAL_AGE)
                .write(DeferredResource.HISTORY_GTAO_SIGNAL,
                        DeferredResource.HISTORY_GTAO_DEPTH,
                        DeferredResource.HISTORY_GTAO_AGE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.GTAO)
                        && context.isValid(DeferredResource.GTAO_TEMPORAL_SIGNAL)
                        && context.isValid(DeferredResource.GTAO_TEMPORAL_DEPTH)
                        && context.isValid(DeferredResource.GTAO_TEMPORAL_AGE))
                .execute(this::storeHistory)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        rawPipeline();
        temporalPipeline();
        resolvePipeline();
        historyPipeline();
        rawData();
        temporalData();
        resolveData();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void evaluateRaw(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        if (current == null) return;

        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        RhiStorageImage rawSignal = requireImage(context, DeferredResource.GTAO_RAW_SIGNAL);
        RhiStorageImage rawDiagnostics = requireImage(context, DeferredResource.GTAO_RAW_DIAGNOSTICS);
        RhiStorageImage viewNormal = requireImage(context, DeferredResource.GTAO_VIEW_NORMAL);
        RhiStorageImage viewDepth = requireImage(context, DeferredResource.GTAO_VIEW_DEPTH);
        RhiStorageImage radiusFootprint = requireImage(context, DeferredResource.GTAO_RADIUS_FOOTPRINT);

        Std430Writer writer = new Std430Writer(RAW_DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", current.inverseProjection())
                .putMat4(0, "projection", current.projection())
                .putVec4(0, "depthTransform", depthTransformScale(context), depthTransformBias(context), 0.0f, 0.0f)
                .putVec4(0, "params0", context.settings().ambientOcclusionRadius(),
                        (float) (current.frameId() & 0x00ffffffL), depth.getWidth(0), depth.getHeight(0));
        RhiStorageBuffer data = rawData();
        data.upload(writer.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant GTAO raw", rawPipeline(),
                groups(rawSignal.descriptor().width()), groups(rawSignal.descriptor().height()), 1,
                List.of(new StorageBinding(8, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, depth, nearest),
                        new SampledTextureBinding(1, geometry, nearest),
                        new SampledTextureBinding(2, gbufferDepth, nearest)
                ),
                List.of(
                        new StorageImageBinding(3, rawSignal, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(4, rawDiagnostics, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(5, viewNormal, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(6, viewDepth, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(7, radiusFootprint, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void accumulateTemporal(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        DeferredPrimaryViewSource.FrameView previous = context.primaryView().previous();
        if (current == null) return;

        GpuTextureView rawSignal = requireTexture(context, DeferredResource.GTAO_RAW_SIGNAL);
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        DeferredTemporalHistoryDescriptor history = context.history(DeferredTemporalHistoryId.AMBIENT_OCCLUSION);
        GpuTextureView historySignal = context.resources().texture(DeferredResource.HISTORY_GTAO_SIGNAL);
        GpuTextureView historyDepth = context.resources().texture(DeferredResource.HISTORY_GTAO_DEPTH);
        GpuTextureView historyAge = context.resources().texture(DeferredResource.HISTORY_GTAO_AGE);
        boolean historyValid = history.valid()
                && previous != null
                && historySignal != null
                && historyDepth != null
                && historyAge != null;
        if (!historyValid) {
            historySignal = rawSignal;
            historyDepth = resolvedDepth;
            historyAge = rawSignal;
        }

        RhiStorageImage temporalSignal = requireImage(context, DeferredResource.GTAO_TEMPORAL_SIGNAL);
        RhiStorageImage temporalDepth = requireImage(context, DeferredResource.GTAO_TEMPORAL_DEPTH);
        RhiStorageImage temporalAge = requireImage(context, DeferredResource.GTAO_TEMPORAL_AGE);
        RhiStorageImage reprojectedUv = requireImage(context, DeferredResource.GTAO_REPROJECTED_UV);
        RhiStorageImage historyValidity = requireImage(context, DeferredResource.GTAO_HISTORY_VALIDITY);
        RhiStorageImage depthRejection = requireImage(context, DeferredResource.GTAO_DEPTH_REJECTION);
        RhiStorageImage offcenterRejection = requireImage(context, DeferredResource.GTAO_OFFCENTER_REJECTION);
        RhiStorageImage historyWeight = requireImage(context, DeferredResource.GTAO_HISTORY_WEIGHT);

        DeferredPrimaryViewSource.FrameView previousFrame = previous == null ? current : previous;
        net.minecraft.world.phys.Vec3 cameraDelta = previous == null
                ? net.minecraft.world.phys.Vec3.ZERO
                : current.cameraPosition().subtract(previous.cameraPosition());
        Std430Writer writer = new Std430Writer(TEMPORAL_DATA_LAYOUT, 1)
                .putMat4(0, "currentInverseProjection", current.inverseProjection())
                .putMat4(0, "previousInverseProjection", previousFrame.inverseProjection())
                .putMat4(0, "currentInverseView", current.inverseView())
                .putMat4(0, "currentView", current.view())
                .putMat4(0, "previousView", previousFrame.view())
                .putMat4(0, "previousProjection", previousFrame.projection())
                .putMat4(0, "previousInverseView", previousFrame.inverseView())
                .putVec4(0, "cameraDelta", (float) cameraDelta.x, (float) cameraDelta.y, (float) cameraDelta.z, 0.0f)
                .putVec4(0, "depthTransform", depthTransformScale(context), depthTransformBias(context), 0.0f, 0.0f)
                .putVec4(0, "policy", historyValid ? 1.0f : 0.0f, MAX_ACCUMULATED_FRAMES,
                        DEPTH_REJECTION_STRENGTH, OFFCENTER_REJECTION_STRENGTH);
        RhiStorageBuffer data = temporalData();
        data.upload(writer.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant GTAO temporal", temporalPipeline(),
                groups(temporalSignal.descriptor().width()), groups(temporalSignal.descriptor().height()), 1,
                List.of(new StorageBinding(15, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, rawSignal, nearest),
                        new SampledTextureBinding(1, resolvedDepth, nearest),
                        new SampledTextureBinding(2, geometry, nearest),
                        new SampledTextureBinding(3, gbufferDepth, nearest),
                        new SampledTextureBinding(4, historySignal, linear),
                        new SampledTextureBinding(5, historyDepth, nearest),
                        new SampledTextureBinding(6, historyAge, linear)
                ),
                List.of(
                        new StorageImageBinding(7, temporalSignal, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(8, temporalDepth, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(9, temporalAge, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(10, reprojectedUv, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(11, historyValidity, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(12, depthRejection, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(13, offcenterRejection, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(14, historyWeight, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void resolvePresentation(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        if (current == null) return;

        GpuTextureView temporalSignal = requireTexture(context, DeferredResource.GTAO_TEMPORAL_SIGNAL);
        GpuTextureView temporalDepth = requireTexture(context, DeferredResource.GTAO_TEMPORAL_DEPTH);
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        RhiStorageImage visibility = requireImage(context, DeferredResource.AMBIENT_OCCLUSION);
        RhiStorageImage bentNormal = requireImage(context, DeferredResource.AMBIENT_BENT_NORMAL);

        Std430Writer writer = new Std430Writer(RESOLVE_DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", current.inverseProjection())
                .putVec4(0, "depthTransform", depthTransformScale(context), depthTransformBias(context), 0.0f, 0.0f);
        RhiStorageBuffer data = resolveData();
        data.upload(writer.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant GTAO presentation", resolvePipeline(),
                groups(visibility.descriptor().width()), groups(visibility.descriptor().height()), 1,
                List.of(new StorageBinding(7, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, temporalSignal, nearest),
                        new SampledTextureBinding(1, temporalDepth, nearest),
                        new SampledTextureBinding(2, resolvedDepth, nearest),
                        new SampledTextureBinding(3, geometry, nearest),
                        new SampledTextureBinding(4, gbufferDepth, nearest)
                ),
                List.of(
                        new StorageImageBinding(5, visibility, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(6, bentNormal, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void storeHistory(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView signal = requireTexture(context, DeferredResource.GTAO_TEMPORAL_SIGNAL);
        GpuTextureView depth = requireTexture(context, DeferredResource.GTAO_TEMPORAL_DEPTH);
        GpuTextureView age = requireTexture(context, DeferredResource.GTAO_TEMPORAL_AGE);
        RhiStorageImage historySignal = requireImage(context, DeferredResource.HISTORY_GTAO_SIGNAL);
        RhiStorageImage historyDepth = requireImage(context, DeferredResource.HISTORY_GTAO_DEPTH);
        RhiStorageImage historyAge = requireImage(context, DeferredResource.HISTORY_GTAO_AGE);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant GTAO history store", historyPipeline(),
                groups(historySignal.descriptor().width()), groups(historySignal.descriptor().height()), 1,
                List.of(),
                List.of(
                        new SampledTextureBinding(0, signal, nearest),
                        new SampledTextureBinding(1, depth, nearest),
                        new SampledTextureBinding(2, age, nearest)
                ),
                List.of(
                        new StorageImageBinding(3, historySignal, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(4, historyDepth, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(5, historyAge, StorageAccess.WRITE_ONLY)
                )
        ));
        context.temporalHistory().commit(DeferredTemporalHistoryId.AMBIENT_OCCLUSION);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline rawPipeline() {
        if (owner == null) throw new IllegalStateException("Ambient occlusion source has no RHI owner");
        if (rawPipeline == null) rawPipeline = createPipeline("combatant-gtao-raw", RAW_SHADER, RAW_LAYOUT);
        return rawPipeline;
    }

    private RhiComputePipeline temporalPipeline() {
        if (owner == null) throw new IllegalStateException("Ambient occlusion source has no RHI owner");
        if (temporalPipeline == null) temporalPipeline = createPipeline("combatant-gtao-temporal", TEMPORAL_SHADER, TEMPORAL_LAYOUT);
        return temporalPipeline;
    }

    private RhiComputePipeline resolvePipeline() {
        if (owner == null) throw new IllegalStateException("Ambient occlusion source has no RHI owner");
        if (resolvePipeline == null) resolvePipeline = createPipeline("combatant-gtao-resolve", RESOLVE_SHADER, RESOLVE_LAYOUT);
        return resolvePipeline;
    }

    private RhiComputePipeline historyPipeline() {
        if (owner == null) throw new IllegalStateException("Ambient occlusion source has no RHI owner");
        if (historyPipeline == null) historyPipeline = createPipeline("combatant-gtao-history", HISTORY_SHADER, HISTORY_LAYOUT);
        return historyPipeline;
    }

    private RhiComputePipeline createPipeline(String name, Identifier shader, ShaderResourceLayout layout) {
        return owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(name, shader, layout));
    }

    private RhiStorageBuffer rawData() {
        if (rawData == null) rawData = createBuffer("combatant-gtao-raw-data", RAW_DATA_LAYOUT);
        return rawData;
    }

    private RhiStorageBuffer temporalData() {
        if (temporalData == null) temporalData = createBuffer("combatant-gtao-temporal-data", TEMPORAL_DATA_LAYOUT);
        return temporalData;
    }

    private RhiStorageBuffer resolveData() {
        if (resolveData == null) resolveData = createBuffer("combatant-gtao-resolve-data", RESOLVE_DATA_LAYOUT);
        return resolveData;
    }

    private RhiStorageBuffer createBuffer(String name, Std430StructLayout layout) {
        if (owner == null) throw new IllegalStateException("Ambient occlusion source has no RHI owner");
        return owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                name, layout, 1, StorageAccess.READ_ONLY, false
        ));
    }

    private void closeOwned() {
        if (rawPipeline != null) { try { rawPipeline.close(); } catch (Throwable ignored) { } rawPipeline = null; }
        if (temporalPipeline != null) { try { temporalPipeline.close(); } catch (Throwable ignored) { } temporalPipeline = null; }
        if (resolvePipeline != null) { try { resolvePipeline.close(); } catch (Throwable ignored) { } resolvePipeline = null; }
        if (historyPipeline != null) { try { historyPipeline.close(); } catch (Throwable ignored) { } historyPipeline = null; }
        if (rawData != null) { try { rawData.close(); } catch (Throwable ignored) { } rawData = null; }
        if (temporalData != null) { try { temporalData.close(); } catch (Throwable ignored) { } temporalData = null; }
        if (resolveData != null) { try { resolveData.close(); } catch (Throwable ignored) { } resolveData = null; }
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
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

    private static float depthTransformScale(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth() ? 1.0f : 2.0f;
    }

    private static float depthTransformBias(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth() ? 0.0f : -1.0f;
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
