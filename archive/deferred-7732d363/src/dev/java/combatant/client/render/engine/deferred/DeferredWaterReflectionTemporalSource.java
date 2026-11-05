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
 * Water-domain temporal/spatial reconstruction for dedicated forward SSR.
 *
 * <p>The lifecycle is owned by {@link DeferredTemporalHistoryRegistry}; only the reprojection
 * semantics are water-specific. History follows the deformed water surface through the explicit
 * previous-UV contract published by the water trace raster pass and never samples opaque velocity.</p>
 */
final class DeferredWaterReflectionTemporalSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier TEMPORAL_SHADER = id("deferred/water_reflection_temporal");
    private static final Identifier COPY_SHADER = id("deferred/water_reflection_temporal_copy");
    private static final Identifier DENOISE_SHADER = id("deferred/water_reflection_denoise");
    private static final Identifier HISTORY_SHADER = id("deferred/water_reflection_history_store");
    private static final Identifier RESOLVE_SHADER = id("deferred/water_reflection_resolve");

    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("params", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout TEMPORAL_LAYOUT = new ShaderResourceLayout(List.of(
            slot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(8, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(9, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(10, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(11, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(12, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout COPY_LAYOUT = new ShaderResourceLayout(List.of(
            slot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout DENOISE_LAYOUT = new ShaderResourceLayout(List.of(
            slot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout HISTORY_LAYOUT = new ShaderResourceLayout(List.of(
            slot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout RESOLVE_LAYOUT = new ShaderResourceLayout(List.of(
            slot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            slot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            slot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline temporalPipeline;
    private RhiComputePipeline copyPipeline;
    private RhiComputePipeline denoisePipeline;
    private RhiComputePipeline historyPipeline;
    private RhiComputePipeline resolvePipeline;
    private RhiStorageBuffer params;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.water.reflection.temporal", DeferredStage.WATER_REFLECTION_TEMPORAL)
                .feature(DeferredFeature.WATER)
                .read(DeferredResource.WATER_REFLECTION_TRACE_COLOR,
                        DeferredResource.WATER_REFLECTION_TRACE_CONFIDENCE,
                        DeferredResource.WATER_REFLECTION_REPROJECTION,
                        DeferredResource.WATER_REFLECTION_GEOMETRY,
                        DeferredResource.WATER_REFLECTION_DEPTHS,
                        DeferredResource.HISTORY_WATER_REFLECTION,
                        DeferredResource.HISTORY_WATER_REFLECTION_CONFIDENCE,
                        DeferredResource.HISTORY_WATER_REFLECTION_SOURCE_DEPTH,
                        DeferredResource.HISTORY_WATER_REFLECTION_HIT_DEPTH)
                .write(DeferredResource.WATER_REFLECTION_TEMPORAL_COLOR,
                        DeferredResource.WATER_REFLECTION_TEMPORAL_CONFIDENCE,
                        DeferredResource.WATER_REFLECTION_REJECTION)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.REFLECTIONS)
                        && context.isValid(DeferredResource.WATER_REFLECTION_TRACE_COLOR)
                        && context.isValid(DeferredResource.WATER_REFLECTION_TRACE_CONFIDENCE)
                        && context.isValid(DeferredResource.WATER_REFLECTION_REPROJECTION)
                        && context.isValid(DeferredResource.WATER_REFLECTION_GEOMETRY)
                        && context.isValid(DeferredResource.WATER_REFLECTION_DEPTHS))
                .execute(this::temporalResolve)
                .build());

        passes.add(DeferredPassSpec.builder("world.water.reflection.denoise", DeferredStage.WATER_REFLECTION_DENOISE)
                .feature(DeferredFeature.WATER)
                .read(DeferredResource.WATER_REFLECTION_TEMPORAL_COLOR,
                        DeferredResource.WATER_REFLECTION_TEMPORAL_CONFIDENCE,
                        DeferredResource.WATER_REFLECTION_GEOMETRY,
                        DeferredResource.WATER_REFLECTION_DEPTHS)
                .write(DeferredResource.WATER_REFLECTION_FILTERED_COLOR,
                        DeferredResource.WATER_REFLECTION_FILTERED_CONFIDENCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.REFLECTIONS)
                        && context.isValid(DeferredResource.WATER_REFLECTION_TEMPORAL_COLOR)
                        && context.isValid(DeferredResource.WATER_REFLECTION_TEMPORAL_CONFIDENCE)
                        && context.isValid(DeferredResource.WATER_REFLECTION_GEOMETRY)
                        && context.isValid(DeferredResource.WATER_REFLECTION_DEPTHS))
                .execute(this::denoise)
                .build());

        passes.add(DeferredPassSpec.builder("world.water.reflection.history", DeferredStage.WATER_REFLECTION_HISTORY)
                .feature(DeferredFeature.WATER)
                .read(DeferredResource.WATER_REFLECTION_FILTERED_COLOR,
                        DeferredResource.WATER_REFLECTION_FILTERED_CONFIDENCE,
                        DeferredResource.WATER_REFLECTION_DEPTHS)
                .write(DeferredResource.HISTORY_WATER_REFLECTION,
                        DeferredResource.HISTORY_WATER_REFLECTION_CONFIDENCE,
                        DeferredResource.HISTORY_WATER_REFLECTION_SOURCE_DEPTH,
                        DeferredResource.HISTORY_WATER_REFLECTION_HIT_DEPTH)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.REFLECTIONS)
                        && context.isValid(DeferredResource.WATER_REFLECTION_FILTERED_COLOR)
                        && context.isValid(DeferredResource.WATER_REFLECTION_FILTERED_CONFIDENCE)
                        && context.isValid(DeferredResource.WATER_REFLECTION_DEPTHS))
                .execute(this::storeHistory)
                .build());

        passes.add(DeferredPassSpec.builder("world.water.reflection.resolve", DeferredStage.WATER_REFLECTION_RESOLVE)
                .feature(DeferredFeature.WATER)
                .read(DeferredResource.WATER_REFLECTION_FILTERED_COLOR,
                        DeferredResource.WATER_REFLECTION_FILTERED_CONFIDENCE,
                        DeferredResource.WATER_REFLECTION_CASCADE_COLOR,
                        DeferredResource.WATER_REFLECTION_CASCADE_CONFIDENCE)
                .write(DeferredResource.WATER_REFLECTION_COLOR,
                        DeferredResource.WATER_REFLECTION_CONFIDENCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.REFLECTIONS)
                        && context.isValid(DeferredResource.WATER_REFLECTION_FILTERED_COLOR)
                        && context.isValid(DeferredResource.WATER_REFLECTION_FILTERED_CONFIDENCE)
                        && context.isValid(DeferredResource.WATER_REFLECTION_CASCADE_COLOR)
                        && context.isValid(DeferredResource.WATER_REFLECTION_CASCADE_CONFIDENCE))
                .execute(this::resolveHierarchy)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        temporalPipeline();
        copyPipeline();
        denoisePipeline();
        historyPipeline();
        resolvePipeline();
        params();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void temporalResolve(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView currentColor = requireTexture(context, DeferredResource.WATER_REFLECTION_TRACE_COLOR);
        GpuTextureView currentConfidence = requireTexture(context, DeferredResource.WATER_REFLECTION_TRACE_CONFIDENCE);
        GpuTextureView reprojection = requireTexture(context, DeferredResource.WATER_REFLECTION_REPROJECTION);
        GpuTextureView geometry = requireTexture(context, DeferredResource.WATER_REFLECTION_GEOMETRY);
        GpuTextureView depths = requireTexture(context, DeferredResource.WATER_REFLECTION_DEPTHS);
        RhiStorageImage outputColor = requireImage(context, DeferredResource.WATER_REFLECTION_TEMPORAL_COLOR);
        RhiStorageImage outputConfidence = requireImage(context, DeferredResource.WATER_REFLECTION_TEMPORAL_CONFIDENCE);
        RhiStorageImage rejection = requireImage(context, DeferredResource.WATER_REFLECTION_REJECTION);

        DeferredTemporalHistoryDescriptor history = context.history(DeferredTemporalHistoryId.WATER_REFLECTIONS);
        boolean historyValid = context.settings().reflectionTemporalEnabled()
                && history.valid()
                && context.resources().texture(DeferredResource.HISTORY_WATER_REFLECTION) != null
                && context.resources().texture(DeferredResource.HISTORY_WATER_REFLECTION_CONFIDENCE) != null
                && context.resources().texture(DeferredResource.HISTORY_WATER_REFLECTION_SOURCE_DEPTH) != null
                && context.resources().texture(DeferredResource.HISTORY_WATER_REFLECTION_HIT_DEPTH) != null;

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        if (!historyValid) {
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant water reflection temporal copy",
                    copyPipeline(), groups(outputColor.descriptor().width()), groups(outputColor.descriptor().height()), 1,
                    List.of(),
                    List.of(
                            new SampledTextureBinding(0, currentColor, linear),
                            new SampledTextureBinding(1, currentConfidence, nearest),
                            new SampledTextureBinding(2, reprojection, nearest)
                    ),
                    List.of(
                            new StorageImageBinding(3, outputColor, StorageAccess.WRITE_ONLY),
                            new StorageImageBinding(4, outputConfidence, StorageAccess.WRITE_ONLY),
                            new StorageImageBinding(5, rejection, StorageAccess.WRITE_ONLY)
                    )
            ));
            return;
        }

        DeferredRuntimeConfig.Snapshot settings = context.settings();
        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putVec4(0, "params",
                        settings.reflectionTemporalHistoryWeight(),
                        settings.reflectionTemporalDepthThreshold(),
                        Math.max(0.01f, settings.reflectionScreenConfidenceThreshold() * 0.25f),
                        settings.reflectionScreenConfidenceThreshold());
        RhiStorageBuffer paramBuffer = params();
        paramBuffer.upload(writer.buffer(), 0L);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant water reflection temporal resolve",
                temporalPipeline(), groups(outputColor.descriptor().width()), groups(outputColor.descriptor().height()), 1,
                List.of(new StorageBinding(12, paramBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, currentColor, linear),
                        new SampledTextureBinding(1, currentConfidence, nearest),
                        new SampledTextureBinding(2, reprojection, nearest),
                        new SampledTextureBinding(3, geometry, nearest),
                        new SampledTextureBinding(4, depths, nearest),
                        new SampledTextureBinding(5, requireTexture(context, DeferredResource.HISTORY_WATER_REFLECTION), linear),
                        new SampledTextureBinding(6, requireTexture(context, DeferredResource.HISTORY_WATER_REFLECTION_CONFIDENCE), nearest),
                        new SampledTextureBinding(7, requireTexture(context, DeferredResource.HISTORY_WATER_REFLECTION_SOURCE_DEPTH), nearest),
                        new SampledTextureBinding(8, requireTexture(context, DeferredResource.HISTORY_WATER_REFLECTION_HIT_DEPTH), nearest)
                ),
                List.of(
                        new StorageImageBinding(9, outputColor, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(10, outputConfidence, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(11, rejection, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void denoise(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageImage outputColor = requireImage(context, DeferredResource.WATER_REFLECTION_FILTERED_COLOR);
        RhiStorageImage outputConfidence = requireImage(context, DeferredResource.WATER_REFLECTION_FILTERED_CONFIDENCE);
        DeferredRuntimeConfig.Snapshot settings = context.settings();
        int radius = settings.reflectionDenoiseEnabled() ? settings.reflectionDenoiseRadius() : 0;
        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putVec4(0, "params", radius,
                        settings.reflectionDenoiseDepthThreshold(),
                        settings.reflectionDenoiseNormalThreshold(),
                        settings.reflectionTemporalDepthThreshold());
        RhiStorageBuffer paramBuffer = params();
        paramBuffer.upload(writer.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant water reflection spatial denoise",
                denoisePipeline(), groups(outputColor.descriptor().width()), groups(outputColor.descriptor().height()), 1,
                List.of(new StorageBinding(6, paramBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, requireTexture(context, DeferredResource.WATER_REFLECTION_TEMPORAL_COLOR), linear),
                        new SampledTextureBinding(1, requireTexture(context, DeferredResource.WATER_REFLECTION_TEMPORAL_CONFIDENCE), nearest),
                        new SampledTextureBinding(2, requireTexture(context, DeferredResource.WATER_REFLECTION_GEOMETRY), nearest),
                        new SampledTextureBinding(3, requireTexture(context, DeferredResource.WATER_REFLECTION_DEPTHS), nearest)
                ),
                List.of(
                        new StorageImageBinding(4, outputColor, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(5, outputConfidence, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void storeHistory(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        RhiStorageImage historyColor = requireImage(context, DeferredResource.HISTORY_WATER_REFLECTION);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant water reflection history store",
                historyPipeline(), groups(historyColor.descriptor().width()), groups(historyColor.descriptor().height()), 1,
                List.of(),
                List.of(
                        new SampledTextureBinding(0, requireTexture(context, DeferredResource.WATER_REFLECTION_FILTERED_COLOR), linear),
                        new SampledTextureBinding(1, requireTexture(context, DeferredResource.WATER_REFLECTION_FILTERED_CONFIDENCE), nearest),
                        new SampledTextureBinding(2, requireTexture(context, DeferredResource.WATER_REFLECTION_DEPTHS), nearest)
                ),
                List.of(
                        new StorageImageBinding(3, historyColor, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(4, requireImage(context, DeferredResource.HISTORY_WATER_REFLECTION_CONFIDENCE), StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(5, requireImage(context, DeferredResource.HISTORY_WATER_REFLECTION_SOURCE_DEPTH), StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(6, requireImage(context, DeferredResource.HISTORY_WATER_REFLECTION_HIT_DEPTH), StorageAccess.WRITE_ONLY)
                )
        ));
        context.temporalHistory().commit(DeferredTemporalHistoryId.WATER_REFLECTIONS);
    }

    private void resolveHierarchy(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageImage outputColor = requireImage(context, DeferredResource.WATER_REFLECTION_COLOR);
        RhiStorageImage outputConfidence = requireImage(context, DeferredResource.WATER_REFLECTION_CONFIDENCE);
        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putVec4(0, "params", context.settings().reflectionScreenConfidenceThreshold(), 0.0f, 0.0f, 0.0f);
        RhiStorageBuffer paramBuffer = params();
        paramBuffer.upload(writer.buffer(), 0L);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant water reflection hierarchy resolve",
                resolvePipeline(), groups(outputColor.descriptor().width()), groups(outputColor.descriptor().height()), 1,
                List.of(new StorageBinding(6, paramBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, requireTexture(context, DeferredResource.WATER_REFLECTION_FILTERED_COLOR), linear),
                        new SampledTextureBinding(1, requireTexture(context, DeferredResource.WATER_REFLECTION_FILTERED_CONFIDENCE), nearest),
                        new SampledTextureBinding(2, requireTexture(context, DeferredResource.WATER_REFLECTION_CASCADE_COLOR), linear),
                        new SampledTextureBinding(3, requireTexture(context, DeferredResource.WATER_REFLECTION_CASCADE_CONFIDENCE), nearest)
                ),
                List.of(
                        new StorageImageBinding(4, outputColor, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(5, outputConfidence, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline temporalPipeline() {
        if (temporalPipeline == null) temporalPipeline = pipeline("combatant-water-reflection-temporal", TEMPORAL_SHADER, TEMPORAL_LAYOUT);
        return temporalPipeline;
    }

    private RhiComputePipeline copyPipeline() {
        if (copyPipeline == null) copyPipeline = pipeline("combatant-water-reflection-temporal-copy", COPY_SHADER, COPY_LAYOUT);
        return copyPipeline;
    }

    private RhiComputePipeline denoisePipeline() {
        if (denoisePipeline == null) denoisePipeline = pipeline("combatant-water-reflection-denoise", DENOISE_SHADER, DENOISE_LAYOUT);
        return denoisePipeline;
    }

    private RhiComputePipeline historyPipeline() {
        if (historyPipeline == null) historyPipeline = pipeline("combatant-water-reflection-history", HISTORY_SHADER, HISTORY_LAYOUT);
        return historyPipeline;
    }

    private RhiComputePipeline resolvePipeline() {
        if (resolvePipeline == null) resolvePipeline = pipeline("combatant-water-reflection-resolve", RESOLVE_SHADER, RESOLVE_LAYOUT);
        return resolvePipeline;
    }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout) {
        if (owner == null) throw new IllegalStateException("Water reflection temporal source has no RHI owner");
        return owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(label, shader, layout));
    }

    private RhiStorageBuffer params() {
        if (owner == null) throw new IllegalStateException("Water reflection temporal source has no RHI owner");
        if (params == null) {
            params = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-water-reflection-temporal-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        return params;
    }

    private void closeOwned() {
        temporalPipeline = close(temporalPipeline);
        copyPipeline = close(copyPipeline);
        denoisePipeline = close(denoisePipeline);
        historyPipeline = close(historyPipeline);
        resolvePipeline = close(resolvePipeline);
        if (params != null) {
            try { params.close(); } catch (Throwable ignored) { }
            params = null;
        }
    }

    private static RhiComputePipeline close(RhiComputePipeline value) {
        if (value == null) return null;
        try { value.close(); } catch (Throwable ignored) { }
        return null;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static ShaderResourceSlot slot(int binding, ShaderResourceKind kind, StorageAccess access) {
        return new ShaderResourceSlot(binding, kind, access);
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
