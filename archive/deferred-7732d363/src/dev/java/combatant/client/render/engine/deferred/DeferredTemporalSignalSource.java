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
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Shared reprojection/disocclusion contract for temporally accumulated deferred signals. */
final class DeferredTemporalSignalSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier RESOLVE_SHADER = id("deferred/temporal_signal_resolve");
    private static final Identifier COPY_SHADER = id("deferred/temporal_signal_copy");
    private static final Identifier STORE_SHADER = id("deferred/temporal_signal_store");

    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("params", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout RESOLVE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(10, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout COPY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout STORE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline resolvePipeline;
    private RhiComputePipeline copyPipeline;
    private RhiComputePipeline storePipeline;
    private RhiStorageBuffer params;

    void install(ArrayList<DeferredPassSpec> passes) {
        installSignal(passes,
                "world.indirect.temporal", DeferredStage.INDIRECT_TEMPORAL,
                DeferredResource.INDIRECT_TRACE_LIGHT, DeferredResource.INDIRECT_TRACE_CONFIDENCE,
                DeferredResource.INDIRECT_LIGHT, DeferredResource.INDIRECT_CONFIDENCE,
                DeferredResource.HISTORY_INDIRECT, DeferredResource.HISTORY_INDIRECT_CONFIDENCE,
                DeferredTemporalHistoryId.INDIRECT_LIGHT, true);
        installHistoryStore(passes,
                "world.indirect.history", DeferredStage.INDIRECT_HISTORY,
                DeferredResource.INDIRECT_LIGHT, DeferredResource.INDIRECT_CONFIDENCE,
                DeferredResource.HISTORY_INDIRECT, DeferredResource.HISTORY_INDIRECT_CONFIDENCE,
                DeferredTemporalHistoryId.INDIRECT_LIGHT, true);
        installSignal(passes,
                "world.reflection.temporal", DeferredStage.REFLECTION_TEMPORAL,
                DeferredResource.REFLECTION_RESOLVED_COLOR, DeferredResource.REFLECTION_RESOLVED_CONFIDENCE,
                DeferredResource.REFLECTION_TEMPORAL_COLOR, DeferredResource.REFLECTION_TEMPORAL_CONFIDENCE,
                DeferredResource.HISTORY_REFLECTION, DeferredResource.HISTORY_REFLECTION_CONFIDENCE,
                DeferredTemporalHistoryId.REFLECTIONS, false);
        installHistoryStore(passes,
                "world.reflection.history", DeferredStage.REFLECTION_HISTORY,
                DeferredResource.REFLECTION_COLOR, DeferredResource.REFLECTION_CONFIDENCE,
                DeferredResource.HISTORY_REFLECTION, DeferredResource.HISTORY_REFLECTION_CONFIDENCE,
                DeferredTemporalHistoryId.REFLECTIONS, false);
    }

    private void installSignal(ArrayList<DeferredPassSpec> passes, String name, DeferredStage stage,
                               DeferredResource currentColor, DeferredResource currentConfidence,
                               DeferredResource outputColor, DeferredResource outputConfidence,
                               DeferredResource historyColor, DeferredResource historyConfidence,
                               DeferredTemporalHistoryId historyId, boolean indirect) {
        passes.add(DeferredPassSpec.builder(name, stage)
                .feature(indirect ? DeferredFeature.INDIRECT_LIGHT : DeferredFeature.REFLECTIONS)
                .read(currentColor, currentConfidence, DeferredResource.HISTORY_DEPTH,
                        historyColor, historyConfidence)
                .optionalRead(DeferredResource.VELOCITY, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.DISOCCLUSION_MASK, DeferredResource.REACTIVE_MASK)
                .write(outputColor, outputConfidence)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(currentColor) && context.isValid(currentConfidence))
                .execute(context -> resolve(context, currentColor, currentConfidence, outputColor, outputConfidence,
                        historyColor, historyConfidence, historyId, indirect))
                .build());
    }

    private void installHistoryStore(ArrayList<DeferredPassSpec> passes, String name, DeferredStage stage,
                                     DeferredResource color, DeferredResource confidence,
                                     DeferredResource historyColor, DeferredResource historyConfidence,
                                     DeferredTemporalHistoryId historyId, boolean indirect) {
        passes.add(DeferredPassSpec.builder(name, stage)
                .feature(indirect ? DeferredFeature.INDIRECT_LIGHT : DeferredFeature.REFLECTIONS)
                .read(color, confidence)
                .write(historyColor, historyConfidence)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(color) && context.isValid(confidence)
                        && context.featureEnabled(indirect ? DeferredFeature.INDIRECT_LIGHT : DeferredFeature.REFLECTIONS))
                .execute(context -> store(context, color, confidence, historyColor, historyConfidence, historyId))
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        resolvePipeline();
        copyPipeline();
        storePipeline();
        params();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void resolve(DeferredPassContext context,
                         DeferredResource currentColorResource, DeferredResource currentConfidenceResource,
                         DeferredResource outputColorResource, DeferredResource outputConfidenceResource,
                         DeferredResource historyColorResource, DeferredResource historyConfidenceResource,
                         DeferredTemporalHistoryId historyId, boolean indirect) {
        ensureOwner(context.rhi());
        GpuTextureView currentColor = requireTexture(context, currentColorResource);
        GpuTextureView currentConfidence = requireTexture(context, currentConfidenceResource);
        RhiStorageImage outputColor = requireImage(context, outputColorResource);
        RhiStorageImage outputConfidence = requireImage(context, outputConfidenceResource);

        DeferredRuntimeConfig.Snapshot settings = context.settings();
        boolean temporalEnabled = indirect ? settings.indirectTemporalEnabled() : settings.reflectionTemporalEnabled();
        DeferredTemporalHistoryDescriptor signalHistory = context.history(historyId);
        DeferredTemporalHistoryDescriptor sceneHistory = context.history(DeferredTemporalHistoryId.SCENE);
        boolean historyValid = temporalEnabled
                && signalHistory.valid()
                && sceneHistory.valid()
                && context.isValid(DeferredResource.VELOCITY)
                && context.isValid(DeferredResource.MOTION_VALIDITY)
                && context.isValid(DeferredResource.RESOLVED_DEPTH)
                && context.isValid(DeferredResource.DISOCCLUSION_MASK)
                && context.isValid(DeferredResource.REACTIVE_MASK)
                && context.resources().texture(historyColorResource) != null
                && context.resources().texture(historyConfidenceResource) != null
                && context.resources().texture(DeferredResource.HISTORY_DEPTH) != null
                && context.resources().texture(DeferredResource.VELOCITY) != null
                && context.resources().texture(DeferredResource.MOTION_VALIDITY) != null
                && context.resources().texture(DeferredResource.RESOLVED_DEPTH) != null
                && context.resources().texture(DeferredResource.DISOCCLUSION_MASK) != null
                && context.resources().texture(DeferredResource.REACTIVE_MASK) != null;

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        if (!historyValid) {
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant temporal signal copy",
                    copyPipeline(), groups(outputColor.descriptor().width()), groups(outputColor.descriptor().height()), 1,
                    List.of(),
                    List.of(
                            new SampledTextureBinding(0, currentColor, linear),
                            new SampledTextureBinding(1, currentConfidence, nearest)
                    ),
                    List.of(
                            new StorageImageBinding(2, outputColor, StorageAccess.WRITE_ONLY),
                            new StorageImageBinding(3, outputConfidence, StorageAccess.WRITE_ONLY)
                    )
            ));
            return;
        }

        float historyWeight = indirect ? settings.indirectTemporalHistoryWeight() : settings.reflectionTemporalHistoryWeight();
        float depthThreshold = indirect ? settings.indirectTemporalDepthThreshold() : settings.reflectionTemporalDepthThreshold();
        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putVec4(0, "params", historyWeight, depthThreshold, 0.0f, 0.0f);
        RhiStorageBuffer paramBuffer = params();
        paramBuffer.upload(writer.buffer(), 0L);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant temporal signal resolve",
                resolvePipeline(), groups(outputColor.descriptor().width()), groups(outputColor.descriptor().height()), 1,
                List.of(new StorageBinding(10, paramBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, currentColor, linear),
                        new SampledTextureBinding(1, currentConfidence, nearest),
                        new SampledTextureBinding(2, requireTexture(context, DeferredResource.VELOCITY), nearest),
                        new SampledTextureBinding(3, requireTexture(context, DeferredResource.RESOLVED_DEPTH), nearest),
                        new SampledTextureBinding(4, requireTexture(context, historyColorResource), linear),
                        new SampledTextureBinding(5, requireTexture(context, historyConfidenceResource), nearest),
                        new SampledTextureBinding(6, requireTexture(context, DeferredResource.HISTORY_DEPTH), nearest),
                        new SampledTextureBinding(9, requireTexture(context, DeferredResource.DISOCCLUSION_MASK), nearest),
                        new SampledTextureBinding(11, requireTexture(context, DeferredResource.REACTIVE_MASK), nearest)
                ),
                List.of(
                        new StorageImageBinding(7, outputColor, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(8, outputConfidence, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void store(DeferredPassContext context,
                       DeferredResource colorResource, DeferredResource confidenceResource,
                       DeferredResource historyColorResource, DeferredResource historyConfidenceResource,
                       DeferredTemporalHistoryId historyId) {
        ensureOwner(context.rhi());
        GpuTextureView color = requireTexture(context, colorResource);
        GpuTextureView confidence = requireTexture(context, confidenceResource);
        RhiStorageImage historyColor = requireImage(context, historyColorResource);
        RhiStorageImage historyConfidence = requireImage(context, historyConfidenceResource);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant temporal signal history store",
                storePipeline(), groups(historyColor.descriptor().width()), groups(historyColor.descriptor().height()), 1,
                List.of(),
                List.of(
                        new SampledTextureBinding(0, color, linear),
                        new SampledTextureBinding(1, confidence, nearest)
                ),
                List.of(
                        new StorageImageBinding(2, historyColor, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(3, historyConfidence, StorageAccess.WRITE_ONLY)
                )
        ));
        context.temporalHistory().commit(historyId);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline resolvePipeline() {
        if (resolvePipeline == null) resolvePipeline = pipeline("combatant-temporal-signal-resolve", RESOLVE_SHADER, RESOLVE_LAYOUT);
        return resolvePipeline;
    }

    private RhiComputePipeline copyPipeline() {
        if (copyPipeline == null) copyPipeline = pipeline("combatant-temporal-signal-copy", COPY_SHADER, COPY_LAYOUT);
        return copyPipeline;
    }

    private RhiComputePipeline storePipeline() {
        if (storePipeline == null) storePipeline = pipeline("combatant-temporal-signal-store", STORE_SHADER, STORE_LAYOUT);
        return storePipeline;
    }

    private RhiStorageBuffer params() {
        if (params == null) {
            params = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-temporal-signal-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return params;
    }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout) {
        if (owner == null) throw new IllegalStateException("Temporal signal source has no RHI owner");
        return owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(label, shader, layout));
    }

    private void closeOwned() {
        resolvePipeline = close(resolvePipeline);
        copyPipeline = close(copyPipeline);
        storePipeline = close(storePipeline);
        if (params != null) {
            try { params.close(); } catch (Throwable ignored) { }
            params = null;
        }
    }

    private static RhiComputePipeline close(RhiComputePipeline pipeline) {
        if (pipeline != null) {
            try { pipeline.close(); } catch (Throwable ignored) { }
        }
        return null;
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

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
