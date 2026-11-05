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
 * Neutral one-bounce screen-space radiance source.
 *
 * <p>The output is not multiplied by an artistic GI strength or tint. It represents
 * sampled incoming scene radiance plus a confidence channel kept separately for future temporal,
 * denoise and fallback producers.</p>
 */
final class DeferredIndirectLightSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = id("deferred/indirect_light_trace");

    private static final Std430StructLayout DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("projection", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .member("params0", Std430Type.VEC4)
            .member("params1", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiStorageBuffer data;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.indirect.prepare", DeferredStage.INDIRECT_PREPARE)
                .feature(DeferredFeature.INDIRECT_LIGHT)
                .read(DeferredResource.OPAQUE_BASE_RADIANCE, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.DEPTH_PYRAMID, DeferredResource.GBUFFER_GEOMETRY)
                .write(DeferredResource.INDIRECT_TRACE_DATA)
                .requires(RhiShaderStage.COMPUTE)
                .when(this::available)
                .execute(this::prepareFrame)
                .build());
        passes.add(DeferredPassSpec.builder("world.indirect.trace", DeferredStage.INDIRECT_TRACE)
                .feature(DeferredFeature.INDIRECT_LIGHT)
                .read(DeferredResource.OPAQUE_BASE_RADIANCE, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.DEPTH_PYRAMID, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.INDIRECT_TRACE_DATA)
                .write(DeferredResource.INDIRECT_TRACE_LIGHT, DeferredResource.INDIRECT_TRACE_CONFIDENCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> available(context) && context.isValid(DeferredResource.INDIRECT_TRACE_DATA))
                .execute(this::trace)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        pipeline();
        data();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private boolean available(DeferredPassContext context) {
        return context.featureEnabled(DeferredFeature.INDIRECT_LIGHT)
                && context.primaryView().current() != null
                && context.isValid(DeferredResource.OPAQUE_BASE_RADIANCE)
                && context.isValid(DeferredResource.RESOLVED_DEPTH)
                && context.isValid(DeferredResource.DEPTH_PYRAMID)
                && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null;
    }

    private void prepareFrame(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        if (current == null) return;

        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        DeferredRuntimeConfig.Snapshot settings = context.settings();
        boolean zeroToOne = zeroToOneDepth(context);
        Std430Writer writer = new Std430Writer(DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", current.inverseProjection())
                .putMat4(0, "projection", current.projection())
                .putVec4(0, "depthTransform",
                        zeroToOne ? 1.0f : 2.0f,
                        zeroToOne ? 0.0f : -1.0f,
                        zeroToOne ? 1.0f : 0.5f,
                        zeroToOne ? 0.0f : 0.5f)
                .putVec4(0, "params0",
                        settings.indirectLightSampleCount(),
                        settings.indirectLightRadius(),
                        settings.indirectLightBias(),
                        settings.indirectLightMipBias())
                .putVec4(0, "params1",
                        settings.indirectLightMaxMip(),
                        depth.getWidth(0), depth.getHeight(0), 0.0f);
        RhiStorageBuffer buffer = data();
        buffer.upload(writer.buffer(), 0L);
        context.resources().bindBuffer(DeferredResource.INDIRECT_TRACE_DATA, buffer);
    }

    private void trace(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView radiance = requireTexture(context, DeferredResource.OPAQUE_BASE_RADIANCE);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView pyramid = requireTexture(context, DeferredResource.DEPTH_PYRAMID);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        RhiStorageImage indirect = requireImage(context, DeferredResource.INDIRECT_TRACE_LIGHT);
        RhiStorageImage confidence = requireImage(context, DeferredResource.INDIRECT_TRACE_CONFIDENCE);
        RhiStorageBuffer data = requireBuffer(context, DeferredResource.INDIRECT_TRACE_DATA);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant indirect radiance trace",
                pipeline(),
                groups(indirect.descriptor().width()), groups(indirect.descriptor().height()), 1,
                List.of(new StorageBinding(6, data, 0L, data.descriptor().byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, radiance, linear),
                        new SampledTextureBinding(1, depth, nearest),
                        new SampledTextureBinding(2, pyramid, nearest),
                        new SampledTextureBinding(3, geometry, nearest)
                ),
                List.of(
                        new StorageImageBinding(4, indirect, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(5, confidence, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline pipeline() {
        if (owner == null) throw new IllegalStateException("Indirect light source has no RHI owner");
        if (pipeline == null) {
            pipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-indirect-light-trace", SHADER, LAYOUT
            ));
        }
        return pipeline;
    }

    private RhiStorageBuffer data() {
        if (owner == null) throw new IllegalStateException("Indirect light source has no RHI owner");
        if (data == null) {
            data = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-indirect-light-data", DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return data;
    }

    private void closeOwned() {
        if (pipeline != null) {
            try { pipeline.close(); } catch (Throwable ignored) { }
            pipeline = null;
        }
        if (data != null) {
            try { data.close(); } catch (Throwable ignored) { }
            data = null;
        }
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
