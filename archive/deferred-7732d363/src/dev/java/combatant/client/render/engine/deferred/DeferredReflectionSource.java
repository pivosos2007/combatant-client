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
import java.util.Locale;

/**
 * Backend reflection source: Hi-Z screen trace first, optional off-screen cascade fallback second.
 * Temporal/spatial stabilization and material-aware composition are separate graph stages.
 */
final class DeferredReflectionSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier TRACE_SHADER = id("deferred/reflection_trace");
    private static final Identifier RESOLVE_SHADER = id("deferred/reflection_resolve");
    private static final Identifier RESOLVE_CASCADE_SHADER = id("deferred/reflection_resolve_cascade");

    private static final Std430StructLayout TRACE_DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("projection", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .member("traceExtentAndFar", Std430Type.VEC4)
            .member("traceParams0", Std430Type.VEC4)
            .member("traceParams1", Std430Type.VEC4)
            .member("resolveParams", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout TRACE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout RESOLVE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout RESOLVE_CASCADE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline tracePipeline;
    private RhiComputePipeline resolvePipeline;
    private RhiComputePipeline resolveCascadePipeline;
    private RhiStorageBuffer traceData;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.reflection.prepare", DeferredStage.REFLECTION_PREPARE)
                .feature(DeferredFeature.REFLECTIONS)
                .read(DeferredResource.LIGHTING_COLOR, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.GBUFFER_DEPTH, DeferredResource.DEPTH_PYRAMID,
                        DeferredResource.GBUFFER_GEOMETRY, DeferredResource.GBUFFER_MATERIAL)
                .write(DeferredResource.REFLECTION_TRACE_DATA)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.REFLECTIONS)
                        && context.primaryView().current() != null
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.DEPTH_PYRAMID)
                        && context.isValid(DeferredResource.LIGHTING_COLOR)
                        && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null)
                .execute(this::prepareFrame)
                .build());
        passes.add(DeferredPassSpec.builder("world.reflection.trace", DeferredStage.REFLECTION_TRACE)
                .feature(DeferredFeature.REFLECTIONS)
                .read(DeferredResource.LIGHTING_COLOR, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_MATERIAL, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.GBUFFER_DEPTH, DeferredResource.DEPTH_PYRAMID,
                        DeferredResource.REFLECTION_TRACE_DATA)
                .write(DeferredResource.REFLECTION_TRACE_COLOR, DeferredResource.REFLECTION_TRACE_CONFIDENCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.REFLECTIONS)
                        && context.isValid(DeferredResource.REFLECTION_TRACE_DATA))
                .execute(this::trace)
                .build());
        passes.add(DeferredPassSpec.builder("world.reflection.resolve", DeferredStage.REFLECTION_RESOLVE)
                .feature(DeferredFeature.REFLECTIONS)
                .read(DeferredResource.REFLECTION_TRACE_COLOR, DeferredResource.REFLECTION_TRACE_CONFIDENCE,
                        DeferredResource.GBUFFER_GEOMETRY, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.REFLECTION_TRACE_DATA,
                        DeferredResource.REFLECTION_CASCADE_COLOR, DeferredResource.REFLECTION_CASCADE_DEPTH,
                        DeferredResource.REFLECTION_CASCADE_DATA)
                .write(DeferredResource.REFLECTION_RESOLVED_COLOR, DeferredResource.REFLECTION_RESOLVED_CONFIDENCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.REFLECTIONS)
                        && context.isValid(DeferredResource.REFLECTION_TRACE_COLOR)
                        && context.isValid(DeferredResource.REFLECTION_TRACE_CONFIDENCE))
                .execute(this::resolve)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        tracePipeline();
        resolvePipeline();
        resolveCascadePipeline();
        traceData();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void prepareFrame(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        if (current == null) return;
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        boolean zeroToOne = zeroToOneDepth(context);
        Std430Writer writer = new Std430Writer(TRACE_DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", current.inverseProjection())
                .putMat4(0, "inverseView", current.inverseView())
                .putMat4(0, "projection", current.projection())
                .putVec4(0, "depthTransform",
                        zeroToOne ? 1.0f : 2.0f,
                        zeroToOne ? 0.0f : -1.0f,
                        zeroToOne ? 1.0f : 0.5f,
                        zeroToOne ? 0.0f : 0.5f)
                .putVec4(0, "traceExtentAndFar",
                        depth.getWidth(0), depth.getHeight(0), current.farPlane(),
                        context.settings().reflectionTraceMaxDistanceScale())
                .putVec4(0, "traceParams0",
                        context.settings().reflectionTraceMaxSteps(),
                        context.settings().reflectionTraceMinStep(),
                        context.settings().reflectionTraceDepthStepScale(),
                        context.settings().reflectionTraceStepGrowth())
                .putVec4(0, "traceParams1",
                        context.settings().reflectionTraceThickness(),
                        context.settings().reflectionTraceNormalBias(),
                        context.settings().reflectionTraceEdgeMargin(),
                        context.settings().reflectionTraceMipStepScale())
                .putVec4(0, "resolveParams",
                        context.settings().reflectionScreenConfidenceThreshold(),
                        context.settings().reflectionCascadeConfidence(),
                        0.0f, 0.0f);
        RhiStorageBuffer buffer = traceData();
        buffer.upload(writer.buffer(), 0L);
        context.resources().bindBuffer(DeferredResource.REFLECTION_TRACE_DATA, buffer);
    }

    private void trace(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView scene = requireTexture(context, DeferredResource.LIGHTING_COLOR);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView material = requireTexture(context, DeferredResource.GBUFFER_MATERIAL);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        GpuTextureView pyramid = requireTexture(context, DeferredResource.DEPTH_PYRAMID);
        RhiStorageBuffer data = requireBuffer(context, DeferredResource.REFLECTION_TRACE_DATA);
        RhiStorageImage color = requireImage(context, DeferredResource.REFLECTION_TRACE_COLOR);
        RhiStorageImage confidence = requireImage(context, DeferredResource.REFLECTION_TRACE_CONFIDENCE);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant reflection trace",
                tracePipeline(),
                groups(color.descriptor().width()), groups(color.descriptor().height()), 1,
                List.of(new StorageBinding(8, data, 0L, data.descriptor().byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, scene, linear),
                        new SampledTextureBinding(1, geometry, nearest),
                        new SampledTextureBinding(2, material, nearest),
                        new SampledTextureBinding(3, depth, nearest),
                        new SampledTextureBinding(4, gbufferDepth, nearest),
                        new SampledTextureBinding(5, pyramid, nearest)
                ),
                List.of(
                        new StorageImageBinding(6, color, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(7, confidence, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void resolve(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView traceColor = requireTexture(context, DeferredResource.REFLECTION_TRACE_COLOR);
        GpuTextureView traceConfidence = requireTexture(context, DeferredResource.REFLECTION_TRACE_CONFIDENCE);
        RhiStorageImage color = requireImage(context, DeferredResource.REFLECTION_RESOLVED_COLOR);
        RhiStorageImage confidence = requireImage(context, DeferredResource.REFLECTION_RESOLVED_CONFIDENCE);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        boolean hasCascade = context.isValid(DeferredResource.REFLECTION_CASCADE_COLOR)
                && context.isValid(DeferredResource.REFLECTION_CASCADE_DEPTH)
                && context.isValid(DeferredResource.REFLECTION_CASCADE_DATA)
                && context.resources().texture(DeferredResource.REFLECTION_CASCADE_COLOR) != null
                && context.resources().texture(DeferredResource.REFLECTION_CASCADE_DEPTH) != null
                && context.resources().buffer(DeferredResource.REFLECTION_CASCADE_DATA) != null;
        if (!hasCascade) {
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant reflection canonical resolve",
                    resolvePipeline(),
                    groups(color.descriptor().width()), groups(color.descriptor().height()), 1,
                    List.of(),
                    List.of(
                            new SampledTextureBinding(0, traceColor, nearest),
                            new SampledTextureBinding(1, traceConfidence, nearest)
                    ),
                    List.of(
                            new StorageImageBinding(2, color, StorageAccess.WRITE_ONLY),
                            new StorageImageBinding(3, confidence, StorageAccess.WRITE_ONLY)
                    )
            ));
            return;
        }

        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView cascadeColor = requireTexture(context, DeferredResource.REFLECTION_CASCADE_COLOR);
        GpuTextureView cascadeDepth = requireTexture(context, DeferredResource.REFLECTION_CASCADE_DEPTH);
        RhiStorageBuffer data = requireBuffer(context, DeferredResource.REFLECTION_TRACE_DATA);
        RhiStorageBuffer cascadeData = requireBuffer(context, DeferredResource.REFLECTION_CASCADE_DATA);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant reflection cascade resolve",
                resolveCascadePipeline(),
                groups(color.descriptor().width()), groups(color.descriptor().height()), 1,
                List.of(
                        new StorageBinding(8, data, 0L, data.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(9, cascadeData, 0L, cascadeData.descriptor().byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, traceColor, nearest),
                        new SampledTextureBinding(1, traceConfidence, nearest),
                        new SampledTextureBinding(2, geometry, nearest),
                        new SampledTextureBinding(3, depth, nearest),
                        new SampledTextureBinding(4, cascadeColor, linear),
                        new SampledTextureBinding(5, cascadeDepth, nearest)
                ),
                List.of(
                        new StorageImageBinding(6, color, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(7, confidence, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline tracePipeline() {
        if (tracePipeline == null) tracePipeline = pipeline("combatant-reflection-trace", TRACE_SHADER, TRACE_LAYOUT);
        return tracePipeline;
    }

    private RhiComputePipeline resolvePipeline() {
        if (resolvePipeline == null) resolvePipeline = pipeline("combatant-reflection-resolve", RESOLVE_SHADER, RESOLVE_LAYOUT);
        return resolvePipeline;
    }

    private RhiComputePipeline resolveCascadePipeline() {
        if (resolveCascadePipeline == null) {
            resolveCascadePipeline = pipeline(
                    "combatant-reflection-resolve-cascade", RESOLVE_CASCADE_SHADER, RESOLVE_CASCADE_LAYOUT
            );
        }
        return resolveCascadePipeline;
    }


    private RhiStorageBuffer traceData() {
        if (owner == null) throw new IllegalStateException("Reflection source has no RHI owner");
        if (traceData == null) {
            traceData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-reflection-trace-data", TRACE_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return traceData;
    }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout) {
        if (owner == null) throw new IllegalStateException("Reflection source has no RHI owner");
        return owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(label, shader, layout));
    }

    private void closeOwned() {
        tracePipeline = close(tracePipeline);
        resolvePipeline = close(resolvePipeline);
        resolveCascadePipeline = close(resolveCascadePipeline);
        if (traceData != null) {
            try { traceData.close(); } catch (Throwable ignored) { }
            traceData = null;
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

    private static RhiStorageBuffer requireBuffer(DeferredPassContext context, DeferredResource resource) {
        RhiStorageBuffer value = context.resources().buffer(resource);
        if (value == null) throw new IllegalStateException("Deferred storage buffer is not bound: " + resource);
        return value;
    }

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
