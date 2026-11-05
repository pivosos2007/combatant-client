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
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the stable opaque-radiance input used by screen-space lighting.
 *
 * <p>Terrain pixels come from the Combatant HDR direct-lighting target. Pixels replaced by later
 * compatibility-forward opaque producers come from the mutable Minecraft scene target. The
 * original G-buffer depth identifies which producer still owns each pixel.</p>
 */
final class DeferredSceneRadianceSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = id("deferred/radiance_capture");
    private static final Std430StructLayout CAPTURE_DATA_LAYOUT = Std430StructLayout.builder()
            .member("flags", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiStorageBuffer captureData;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.radiance.capture", DeferredStage.RADIANCE_CAPTURE)
                .read(DeferredResource.DIRECT_LIGHTING_COLOR,
                        DeferredResource.SCENE_COLOR, DeferredResource.GBUFFER_DEPTH, DeferredResource.RESOLVED_DEPTH)
                .optionalRead(DeferredResource.LOCAL_LIGHTING_COLOR)
                .write(DeferredResource.OPAQUE_BASE_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.DIRECT_LIGHTING_COLOR)
                        && context.resources().texture(DeferredResource.SCENE_COLOR) != null
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH))
                .execute(this::capture)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        pipeline();
        captureData();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void capture(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView directLighting = requireTexture(context, DeferredResource.DIRECT_LIGHTING_COLOR);
        boolean hasLocalLighting = context.isValid(DeferredResource.LOCAL_LIGHTING_COLOR)
                && context.resources().texture(DeferredResource.LOCAL_LIGHTING_COLOR) != null;
        GpuTextureView localLighting = hasLocalLighting
                ? requireTexture(context, DeferredResource.LOCAL_LIGHTING_COLOR) : directLighting;
        GpuTextureView compatibilityScene = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        GpuTextureView currentDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        RhiStorageImage output = requireImage(context, DeferredResource.OPAQUE_BASE_RADIANCE);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        Std430Writer writer = new Std430Writer(CAPTURE_DATA_LAYOUT, 1)
                .putVec4(0, "flags", hasLocalLighting ? 1.0f : 0.0f, 0.0f, 0.0f, 0.0f);
        RhiStorageBuffer data = captureData();
        data.upload(writer.buffer(), 0L);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant opaque base radiance",
                pipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(6, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, directLighting, linear),
                        new SampledTextureBinding(1, localLighting, linear),
                        new SampledTextureBinding(2, compatibilityScene, linear),
                        new SampledTextureBinding(3, gbufferDepth, nearest),
                        new SampledTextureBinding(4, currentDepth, nearest)
                ),
                List.of(new StorageImageBinding(5, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline pipeline() {
        if (owner == null) throw new IllegalStateException("Scene radiance source has no RHI owner");
        if (pipeline == null) {
            pipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-scene-radiance-capture", SHADER, LAYOUT
            ));
        }
        return pipeline;
    }

    private RhiStorageBuffer captureData() {
        if (owner == null) throw new IllegalStateException("Scene radiance source has no RHI owner");
        if (captureData == null) {
            captureData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-radiance-capture-data", CAPTURE_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return captureData;
    }

    private void closeOwned() {
        if (pipeline != null) {
            try { pipeline.close(); } catch (Throwable ignored) { }
            pipeline = null;
        }
        if (captureData != null) {
            try { captureData.close(); } catch (Throwable ignored) { }
            captureData = null;
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
