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
 * Resolves directional CSM into canonical scalar visibility, then combines optional contact shadow.
 * Filtering/bias remain geometric visibility policy and never encode color or art direction.
 */
final class DeferredShadowResolveSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier CASCADE_RESOLVE_SHADER = id("deferred/shadow_resolve");
    private static final Identifier COMBINE_SHADER = id("deferred/shadow_combine");

    private static final Std430StructLayout CAMERA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .member("viewportAndFar", Std430Type.VEC4)
            .member("shadowParams0", Std430Type.VEC4)
            .member("shadowParams1", Std430Type.VEC4)
            .member("directionalLight", Std430Type.VEC4)
            .member("shadowBiasParams", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout CASCADE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout COMBINE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline cascadePipeline;
    private RhiComputePipeline combinePipeline;
    private RhiStorageBuffer cameraBuffer;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.shadow.cascade.resolve", DeferredStage.SHADOW_CASCADE_RESOLVE)
                .feature(DeferredFeature.SHADOWS)
                .read(DeferredResource.RESOLVED_DEPTH, DeferredResource.GBUFFER_DEPTH,
                        DeferredResource.GBUFFER_GEOMETRY, DeferredResource.SHADOW_DEPTH,
                        DeferredResource.SHADOW_CASCADE_DATA)
                .write(DeferredResource.SHADOW_CASCADE_VISIBILITY, DeferredResource.SHADOW_HARD_VISIBILITY,
                        DeferredResource.SHADOW_CASCADE_INDEX)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.SHADOWS)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null
                        && context.isValid(DeferredResource.SHADOW_DEPTH)
                        && context.isValid(DeferredResource.SHADOW_CASCADE_DATA)
                        && context.primaryView().current() != null)
                .execute(this::resolveCascade)
                .build());
        passes.add(DeferredPassSpec.builder("world.shadow.resolve", DeferredStage.SHADOW_RESOLVE)
                .optionalRead(DeferredResource.SHADOW_CASCADE_VISIBILITY, DeferredResource.CONTACT_SHADOW)
                .write(DeferredResource.SHADOW_COLOR)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> (context.isValid(DeferredResource.SHADOW_CASCADE_VISIBILITY)
                        || context.isValid(DeferredResource.CONTACT_SHADOW)))
                .execute(this::combine)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        cascadePipeline();
        combinePipeline();
        cameraBuffer();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void resolveCascade(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        if (current == null) return;

        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        GpuTextureView shadowDepth = requireTexture(context, DeferredResource.SHADOW_DEPTH);
        RhiStorageImage output = requireImage(context, DeferredResource.SHADOW_CASCADE_VISIBILITY);
        RhiStorageImage hardOutput = requireImage(context, DeferredResource.SHADOW_HARD_VISIBILITY);
        RhiStorageImage cascadeIndexOutput = requireImage(context, DeferredResource.SHADOW_CASCADE_INDEX);
        RhiStorageBuffer cascades = context.resources().buffer(DeferredResource.SHADOW_CASCADE_DATA);
        if (cascades == null) throw new IllegalStateException("Shadow cascade metadata is not bound");

        DeferredRuntimeConfig.Snapshot settings = context.settings();
        boolean nearOnly = DeferredShadowBringupConfig.nearOnly();
        boolean zeroToOne = zeroToOneDepth(context);
        Std430Writer camera = new Std430Writer(CAMERA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", current.inverseProjection())
                .putMat4(0, "inverseView", current.inverseView())
                .putVec4(0, "depthTransform",
                        zeroToOne ? 1.0f : 2.0f,
                        zeroToOne ? 0.0f : -1.0f,
                        zeroToOne ? 1.0f : 0.5f,
                        zeroToOne ? 0.0f : 0.5f)
                .putVec4(0, "viewportAndFar",
                        resolvedDepth.getWidth(0), resolvedDepth.getHeight(0), current.farPlane(), 0.0f)
                .putVec4(0, "shadowParams0",
                        nearOnly ? 0.0f : settings.shadowCascadeBlendFraction(),
                        nearOnly ? 0.0f : settings.shadowNormalOffsetTexels(),
                        nearOnly ? DeferredShadowBringupConfig.nearBiasTexels() : settings.shadowReceiverBiasTexels(),
                        nearOnly ? 0.0f : settings.shadowFilterRadiusTexels())
                .putVec4(0, "shadowParams1",
                        settings.shadowBlockerSearchRadiusTexels(),
                        settings.shadowPenumbraScaleTexels(),
                        settings.shadowMaxPenumbraTexels(),
                        0.0f)
                .putVec4(0, "directionalLight",
                        context.worldState().directionalLight().directionX(),
                        context.worldState().directionalLight().directionY(),
                        context.worldState().directionalLight().directionZ(),
                        context.worldState().directionalLight().valid() ? 1.0f : 0.0f)
                .putVec4(0, "shadowBiasParams",
                        nearOnly ? DeferredShadowBringupConfig.nearSlopeBiasTexels() : 0.0f,
                        nearOnly ? DeferredShadowBringupConfig.nearMaxBiasTexels() : settings.shadowReceiverBiasTexels(),
                        0.20f,
                        0.0f);
        RhiStorageBuffer cameraBuffer = cameraBuffer();
        cameraBuffer.upload(camera.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant directional CSM resolve",
                cascadePipeline(),
                groups(output.descriptor().width()),
                groups(output.descriptor().height()),
                1,
                List.of(
                        new StorageBinding(5, cascades, 0L, cascades.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(6, cameraBuffer, 0L, camera.byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, resolvedDepth, nearest),
                        new SampledTextureBinding(1, shadowDepth, nearest),
                        new SampledTextureBinding(2, geometry, nearest),
                        new SampledTextureBinding(3, gbufferDepth, nearest)
                ),
                List.of(
                        new StorageImageBinding(4, output, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(7, hardOutput, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(8, cascadeIndexOutput, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void combine(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView cascade = context.resources().texture(DeferredResource.SHADOW_CASCADE_VISIBILITY);
        GpuTextureView contact = context.resources().texture(DeferredResource.CONTACT_SHADOW);
        if (cascade == null && contact == null) return;
        if (cascade == null) cascade = contact;
        if (contact == null) contact = cascade;

        RhiStorageImage output = requireImage(context, DeferredResource.SHADOW_COLOR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant shadow source combine",
                combinePipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(),
                List.of(
                        new SampledTextureBinding(0, cascade, nearest),
                        new SampledTextureBinding(1, contact, nearest)
                ),
                List.of(new StorageImageBinding(2, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline cascadePipeline() {
        if (owner == null) throw new IllegalStateException("Shadow resolve has no RHI owner");
        if (cascadePipeline == null) {
            cascadePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-shadow-cascade-resolve", CASCADE_RESOLVE_SHADER, CASCADE_LAYOUT
            ));
        }
        return cascadePipeline;
    }

    private RhiComputePipeline combinePipeline() {
        if (owner == null) throw new IllegalStateException("Shadow resolve has no RHI owner");
        if (combinePipeline == null) {
            combinePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-shadow-combine", COMBINE_SHADER, COMBINE_LAYOUT
            ));
        }
        return combinePipeline;
    }

    private RhiStorageBuffer cameraBuffer() {
        if (owner == null) throw new IllegalStateException("Shadow resolve has no RHI owner");
        if (cameraBuffer == null) {
            cameraBuffer = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-shadow-resolve-camera", CAMERA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return cameraBuffer;
    }

    private void closeOwned() {
        cascadePipeline = close(cascadePipeline);
        combinePipeline = close(combinePipeline);
        if (cameraBuffer != null) {
            try { cameraBuffer.close(); } catch (Throwable ignored) { }
            cameraBuffer = null;
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
