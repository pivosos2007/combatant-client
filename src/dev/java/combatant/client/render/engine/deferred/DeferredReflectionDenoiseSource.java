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
 * Edge-aware spatial resolve for temporally stabilized reflections.
 *
 * <p>This stage consumes explicit resolved depth, geometric normal, material roughness and
 * reflection confidence contracts. Roughness controls the integration footprint; it is never
 * inferred from reflected color.</p>
 */
final class DeferredReflectionDenoiseSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = id("deferred/reflection_denoise");

    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("params", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiStorageBuffer params;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.reflection.denoise", DeferredStage.REFLECTION_DENOISE)
                .feature(DeferredFeature.REFLECTIONS)
                .read(DeferredResource.REFLECTION_TEMPORAL_COLOR,
                        DeferredResource.REFLECTION_TEMPORAL_CONFIDENCE,
                        DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.RESOLVED_DEPTH)
                .write(DeferredResource.REFLECTION_COLOR, DeferredResource.REFLECTION_CONFIDENCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.REFLECTION_TEMPORAL_COLOR)
                        && context.isValid(DeferredResource.REFLECTION_TEMPORAL_CONFIDENCE)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null
                        && context.resources().texture(DeferredResource.GBUFFER_MATERIAL) != null)
                .execute(this::denoise)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        pipeline();
        params();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void denoise(DeferredPassContext context) {
        ensureOwner(context.rhi());

        GpuTextureView inputColor = requireTexture(context, DeferredResource.REFLECTION_TEMPORAL_COLOR);
        GpuTextureView inputConfidence = requireTexture(context, DeferredResource.REFLECTION_TEMPORAL_CONFIDENCE);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView material = requireTexture(context, DeferredResource.GBUFFER_MATERIAL);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        RhiStorageImage outputColor = requireImage(context, DeferredResource.REFLECTION_COLOR);
        RhiStorageImage outputConfidence = requireImage(context, DeferredResource.REFLECTION_CONFIDENCE);

        DeferredRuntimeConfig.Snapshot settings = context.settings();
        int radius = settings.reflectionDenoiseEnabled() ? settings.reflectionDenoiseRadius() : 0;
        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putVec4(0, "params",
                        radius,
                        settings.reflectionDenoiseDepthThreshold(),
                        settings.reflectionDenoiseNormalThreshold(),
                        0.0f);
        RhiStorageBuffer paramBuffer = params();
        paramBuffer.upload(writer.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant reflection spatial denoise",
                pipeline(),
                groups(outputColor.descriptor().width()),
                groups(outputColor.descriptor().height()),
                1,
                List.of(new StorageBinding(6, paramBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, inputColor, linear),
                        new SampledTextureBinding(1, inputConfidence, nearest),
                        new SampledTextureBinding(2, geometry, nearest),
                        new SampledTextureBinding(3, depth, nearest),
                        new SampledTextureBinding(7, material, nearest)
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

    private RhiComputePipeline pipeline() {
        if (owner == null) throw new IllegalStateException("Reflection denoise source has no RHI owner");
        if (pipeline == null) {
            pipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-reflection-denoise", SHADER, LAYOUT
            ));
        }
        return pipeline;
    }

    private RhiStorageBuffer params() {
        if (owner == null) throw new IllegalStateException("Reflection denoise source has no RHI owner");
        if (params == null) {
            params = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-reflection-denoise-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return params;
    }

    private void closeOwned() {
        if (pipeline != null) {
            try { pipeline.close(); } catch (Throwable ignored) { }
            pipeline = null;
        }
        if (params != null) {
            try { params.close(); } catch (Throwable ignored) { }
            params = null;
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

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
