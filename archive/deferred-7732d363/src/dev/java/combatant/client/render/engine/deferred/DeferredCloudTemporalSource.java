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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Independent wind-aware temporal histories for low/mid, high and convective cloud domains. */
final class DeferredCloudTemporalSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier RESOLVE_SHADER = id("deferred/cloud_temporal");
    private static final Identifier HISTORY_SHADER = id("deferred/cloud_history_store");

    private static final Std430StructLayout DATA_LAYOUT = Std430StructLayout.builder()
            .member("currentInverseProjection", Std430Type.MAT4)
            .member("currentInverseView", Std430Type.MAT4)
            .member("previousView", Std430Type.MAT4)
            .member("previousProjection", Std430Type.MAT4)
            .member("cameraDelta", Std430Type.VEC4)
            .member("timing", Std430Type.VEC4)
            .member("policy", Std430Type.VEC4)
            .member("thresholds", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout RESOLVE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout HISTORY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private static final TemporalGroup LOW_MID = new TemporalGroup(
            "low_mid", DeferredTemporalHistoryId.CLOUDS,
            DeferredResource.CLOUD_RADIANCE, DeferredResource.CLOUD_DEPTH, DeferredResource.CLOUD_REPROJECTION_DATA,
            DeferredResource.CLOUD_TEMPORAL_RADIANCE, DeferredResource.CLOUD_TEMPORAL_DEPTH, DeferredResource.CLOUD_TEMPORAL_CONFIDENCE,
            DeferredResource.HISTORY_CLOUD_RADIANCE, DeferredResource.HISTORY_CLOUD_REPROJECTION_DEPTH, DeferredResource.HISTORY_CLOUD_CONFIDENCE
    );
    private static final TemporalGroup HIGH = new TemporalGroup(
            "high", DeferredTemporalHistoryId.CLOUDS_HIGH,
            DeferredResource.CLOUD_HIGH_RADIANCE, DeferredResource.CLOUD_HIGH_DEPTH, DeferredResource.CLOUD_HIGH_REPROJECTION_DATA,
            DeferredResource.CLOUD_HIGH_TEMPORAL_RADIANCE, DeferredResource.CLOUD_HIGH_TEMPORAL_DEPTH, DeferredResource.CLOUD_HIGH_TEMPORAL_CONFIDENCE,
            DeferredResource.HISTORY_CLOUD_HIGH_RADIANCE, DeferredResource.HISTORY_CLOUD_HIGH_REPROJECTION_DEPTH, DeferredResource.HISTORY_CLOUD_HIGH_CONFIDENCE
    );
    private static final TemporalGroup CONVECTIVE = new TemporalGroup(
            "convective", DeferredTemporalHistoryId.CLOUDS_CONVECTIVE,
            DeferredResource.CLOUD_CONVECTIVE_RADIANCE, DeferredResource.CLOUD_CONVECTIVE_DEPTH, DeferredResource.CLOUD_CONVECTIVE_REPROJECTION_DATA,
            DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_RADIANCE, DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_DEPTH, DeferredResource.CLOUD_CONVECTIVE_TEMPORAL_CONFIDENCE,
            DeferredResource.HISTORY_CLOUD_CONVECTIVE_RADIANCE, DeferredResource.HISTORY_CLOUD_CONVECTIVE_REPROJECTION_DEPTH, DeferredResource.HISTORY_CLOUD_CONVECTIVE_CONFIDENCE
    );

    private final DeferredCloudConfig config = DeferredCloudConfig.current();
    private CombatantRhi owner;
    private RhiComputePipeline resolvePipeline;
    private RhiComputePipeline historyPipeline;
    private RhiStorageBuffer data;

    void install(ArrayList<DeferredPassSpec> passes) {
        installGroup(passes, LOW_MID, 35, 40);
        installGroup(passes, HIGH, 36, 41);
        installGroup(passes, CONVECTIVE, 37, 42);
    }

    private void installGroup(ArrayList<DeferredPassSpec> passes, TemporalGroup group, int resolvePriority, int historyPriority) {
        passes.add(DeferredPassSpec.builder("world.cloud.temporal." + group.name(), DeferredStage.SKY_COMPOSITE)
                .priority(resolvePriority)
                .feature(DeferredFeature.CLOUDS)
                .read(group.rawRadiance(), group.rawDepth(), group.reprojection(),
                        group.historyRadiance(), group.historyDepth(), group.historyConfidence())
                .write(group.temporalRadiance(), group.temporalDepth(), group.temporalConfidence())
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(group.rawRadiance())
                        && context.isValid(group.rawDepth())
                        && context.isValid(group.reprojection())
                        && context.primaryView().current() != null)
                .execute(context -> resolve(context, group))
                .build());
        passes.add(DeferredPassSpec.builder("world.cloud.history." + group.name(), DeferredStage.SKY_COMPOSITE)
                .priority(historyPriority)
                .feature(DeferredFeature.CLOUDS)
                .read(group.temporalRadiance(), group.temporalDepth(), group.temporalConfidence(), group.reprojection())
                .write(group.historyRadiance(), group.historyDepth(), group.historyConfidence())
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(group.temporalRadiance())
                        && context.isValid(group.temporalDepth())
                        && context.isValid(group.temporalConfidence())
                        && context.isValid(group.reprojection()))
                .execute(context -> storeHistory(context, group))
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        resolvePipeline();
        historyPipeline();
        data();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void resolve(DeferredPassContext context, TemporalGroup group) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        DeferredPrimaryViewSource.FrameView previous = context.primaryView().previous();
        if (current == null) return;

        GpuTextureView currentRadiance = requireTexture(context, group.rawRadiance());
        GpuTextureView currentDepth = requireTexture(context, group.rawDepth());
        GpuTextureView currentReprojectionData = requireTexture(context, group.reprojection());
        DeferredTemporalHistoryDescriptor cloudHistory = context.history(group.historyId());
        GpuTextureView historyRadiance = context.resources().texture(group.historyRadiance());
        GpuTextureView historyDepth = context.resources().texture(group.historyDepth());
        GpuTextureView historyConfidence = context.resources().texture(group.historyConfidence());
        boolean historyValid = config.temporalEnabled()
                && cloudHistory.valid()
                && previous != null
                && historyRadiance != null
                && historyDepth != null
                && historyConfidence != null;
        if (!historyValid) {
            historyRadiance = currentRadiance;
            historyDepth = currentDepth;
            historyConfidence = currentDepth;
        }

        RhiStorageImage outputRadiance = requireImage(context, group.temporalRadiance());
        RhiStorageImage outputDepth = requireImage(context, group.temporalDepth());
        RhiStorageImage outputConfidence = requireImage(context, group.temporalConfidence());
        Vec3 cameraDelta = previous == null ? Vec3.ZERO : current.cameraPosition().subtract(previous.cameraPosition());
        float deltaSeconds = Math.max(0.0f, Math.min(context.frame().frameDeltaSeconds(), 0.25f));

        Std430Writer writer = new Std430Writer(DATA_LAYOUT, 1)
                .putMat4(0, "currentInverseProjection", current.inverseProjection())
                .putMat4(0, "currentInverseView", current.inverseView())
                .putMat4(0, "previousView", previous == null ? current.view() : previous.view())
                .putMat4(0, "previousProjection", previous == null ? current.projection() : previous.projection())
                .putVec4(0, "cameraDelta", (float) cameraDelta.x, (float) cameraDelta.y, (float) cameraDelta.z, 0.0f)
                .putVec4(0, "timing", deltaSeconds, 0.0f, 0.0f, 0.0f)
                .putVec4(0, "policy", zeroToOneDepth(context) ? 1.0f : 0.0f, historyValid ? 1.0f : 0.0f,
                        config.temporalHistoryWeight(), config.temporalDepthThresholdFraction())
                .putVec4(0, "thresholds", config.temporalMinDepthThresholdBlocks(), 0.20f, 0.10f, 0.04f);
        RhiStorageBuffer buffer = data();
        buffer.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant cloud temporal resolve " + group.name(), resolvePipeline(),
                groups(outputRadiance.descriptor().width()), groups(outputRadiance.descriptor().height()), 1,
                List.of(new StorageBinding(9, buffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, currentRadiance, linear),
                        new SampledTextureBinding(1, currentDepth, nearest),
                        new SampledTextureBinding(2, currentReprojectionData, linear),
                        new SampledTextureBinding(3, historyRadiance, linear),
                        new SampledTextureBinding(4, historyDepth, nearest),
                        new SampledTextureBinding(5, historyConfidence, linear)
                ),
                List.of(
                        new StorageImageBinding(6, outputRadiance, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(7, outputDepth, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(8, outputConfidence, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void storeHistory(DeferredPassContext context, TemporalGroup group) {
        ensureOwner(context.rhi());
        GpuTextureView radiance = requireTexture(context, group.temporalRadiance());
        GpuTextureView depth = requireTexture(context, group.temporalDepth());
        GpuTextureView confidence = requireTexture(context, group.temporalConfidence());
        GpuTextureView reprojectionData = requireTexture(context, group.reprojection());
        RhiStorageImage historyRadiance = requireImage(context, group.historyRadiance());
        RhiStorageImage historyDepth = requireImage(context, group.historyDepth());
        RhiStorageImage historyConfidence = requireImage(context, group.historyConfidence());
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant cloud history store " + group.name(), historyPipeline(),
                groups(historyRadiance.descriptor().width()), groups(historyRadiance.descriptor().height()), 1,
                List.of(),
                List.of(
                        new SampledTextureBinding(0, radiance, linear),
                        new SampledTextureBinding(1, depth, nearest),
                        new SampledTextureBinding(2, confidence, nearest),
                        new SampledTextureBinding(3, reprojectionData, linear)
                ),
                List.of(
                        new StorageImageBinding(4, historyRadiance, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(5, historyDepth, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(6, historyConfidence, StorageAccess.WRITE_ONLY)
                )
        ));
        context.temporalHistory().commit(group.historyId());
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline resolvePipeline() {
        if (resolvePipeline == null) resolvePipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-cloud-temporal", RESOLVE_SHADER, RESOLVE_LAYOUT)
        );
        return resolvePipeline;
    }

    private RhiComputePipeline historyPipeline() {
        if (historyPipeline == null) historyPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-cloud-history-store", HISTORY_SHADER, HISTORY_LAYOUT)
        );
        return historyPipeline;
    }

    private RhiStorageBuffer data() {
        if (data == null) data = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-temporal-data", DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
        ));
        return data;
    }

    private void closeOwned() {
        close(resolvePipeline); resolvePipeline = null;
        close(historyPipeline); historyPipeline = null;
        close(data); data = null;
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

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }

    private record TemporalGroup(
            String name,
            DeferredTemporalHistoryId historyId,
            DeferredResource rawRadiance,
            DeferredResource rawDepth,
            DeferredResource reprojection,
            DeferredResource temporalRadiance,
            DeferredResource temporalDepth,
            DeferredResource temporalConfidence,
            DeferredResource historyRadiance,
            DeferredResource historyDepth,
            DeferredResource historyConfidence
    ) {
    }
}
