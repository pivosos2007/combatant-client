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
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the canonical directional shadow map into scalar visibility.
 *
 * <p>The production path owns both near/mid geometric PCF and the terminal distant
 * screen-space continuation near and beyond the finite map boundary.</p>
 */
final class DeferredShadowResolveSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier CASCADE_RESOLVE_SHADER = id("deferred/shadow_resolve");

    private static final Std430StructLayout CAMERA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("projection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .member("viewportAndFar", Std430Type.VEC4)
            .member("shadowParams0", Std430Type.VEC4)
            .member("shadowParams1", Std430Type.VEC4)
            .member("directionalLight", Std430Type.VEC4)
            .member("shadowBiasParams", Std430Type.VEC4)
            .member("shadowFallbackParams", Std430Type.VEC4)
            .member("ssrtLightDirection", Std430Type.VEC4)
            .member("cameraWorldAndSsrt", Std430Type.VEC4)
            .member("shadowSsrtParams", Std430Type.VEC4)
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
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline cascadePipeline;
    private RhiStorageBuffer cameraBuffer;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.shadow.directional.resolve", DeferredStage.SHADOW_CASCADE_RESOLVE)
                .feature(DeferredFeature.SHADOWS)
                .read(DeferredResource.RESOLVED_DEPTH, DeferredResource.GBUFFER_DEPTH,
                        DeferredResource.GBUFFER_GEOMETRY, DeferredResource.SHADOW_DEPTH,
                        DeferredResource.SHADOW_CASCADE_DATA)
                .write(DeferredResource.SHADOW_CASCADE_VISIBILITY, DeferredResource.SHADOW_HARD_VISIBILITY,
                        DeferredResource.SHADOW_CASCADE_INDEX, DeferredResource.SHADOW_COLOR)
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
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        cascadePipeline();
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
        RhiStorageImage productionOutput = requireImage(context, DeferredResource.SHADOW_COLOR);
        RhiStorageBuffer cascades = context.resources().buffer(DeferredResource.SHADOW_CASCADE_DATA);
        if (cascades == null) throw new IllegalStateException("Shadow cascade metadata is not bound");

        DeferredRuntimeConfig.Snapshot settings = context.settings();
        Vector3f worldLight = new Vector3f(
                context.worldState().directionalLight().directionX(),
                context.worldState().directionalLight().directionY(),
                context.worldState().directionalLight().directionZ());
        Vector3f viewLight = transformDirection(current.view(), worldLight, new Vector3f());
        if (viewLight.lengthSquared() > 1.0e-8f) viewLight.normalize();
        boolean nearOnly = DeferredShadowBringupConfig.nearOnly();
        boolean zeroToOne = zeroToOneDepth(context);
        float distantFallbackStart = DeferredShadowBringupConfig.distantSkylightFallbackStart();
        float distantFallbackEnd = Math.max(
                distantFallbackStart + 1.0f / 255.0f,
                DeferredShadowBringupConfig.distantSkylightFallbackEnd());

        Std430Writer camera = new Std430Writer(CAMERA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", current.inverseProjection())
                .putMat4(0, "projection", current.projection())
                .putMat4(0, "inverseView", current.inverseView())
                .putVec4(0, "depthTransform",
                        zeroToOne ? 1.0f : 2.0f,
                        zeroToOne ? 0.0f : -1.0f,
                        zeroToOne ? 1.0f : 0.5f,
                        zeroToOne ? 0.0f : 0.5f)
                .putVec4(0, "viewportAndFar",
                        resolvedDepth.getWidth(0), resolvedDepth.getHeight(0), current.farPlane(), 0.0f)
                .putVec4(0, "shadowParams0",
                        0.0f,
                        nearOnly ? 0.0f : settings.shadowNormalOffsetTexels(),
                        DeferredShadowBringupConfig.nearBiasTexels(),
                        nearOnly ? 0.0f : settings.shadowFilterRadiusTexels())
                .putVec4(0, "shadowParams1",
                        settings.shadowBlockerSearchRadiusTexels(),
                        settings.shadowPenumbraScaleTexels(),
                        settings.shadowMaxPenumbraTexels(),
                        nearOnly ? 0.0f : DeferredShadowBringupConfig.farFadeFraction())
                .putVec4(0, "directionalLight",
                        context.worldState().directionalLight().directionX(),
                        context.worldState().directionalLight().directionY(),
                        context.worldState().directionalLight().directionZ(),
                        context.worldState().directionalLight().valid() ? 1.0f : 0.0f)
                .putVec4(0, "shadowBiasParams",
                        DeferredShadowBringupConfig.nearSlopeBiasTexels(),
                        DeferredShadowBringupConfig.nearMaxBiasTexels(),
                        0.20f,
                        DeferredShadowBringupConfig.maxAdaptiveFilterRadiusTexels())
                .putVec4(0, "shadowFallbackParams",
                        distantFallbackStart,
                        Math.min(1.0f, distantFallbackEnd),
                        context.worldState().baselineLightState().hasSkyLight() ? 1.0f : 0.0f,
                        DeferredShadowBringupConfig.lowSkylightLeakEnd())
                .putVec4(0, "ssrtLightDirection",
                        viewLight.x, viewLight.y, viewLight.z,
                        context.worldState().directionalLight().valid() ? 1.0f : 0.0f)
                .putVec4(0, "cameraWorldAndSsrt",
                        (float) current.cameraPosition().x,
                        (float) current.cameraPosition().y,
                        (float) current.cameraPosition().z,
                        !nearOnly && DeferredShadowBringupConfig.distantSsrtEnabled() ? 1.0f : 0.0f)
                .putVec4(0, "shadowSsrtParams",
                        DeferredShadowBringupConfig.distantSsrtSteps(),
                        2.0f,
                        DeferredShadowBringupConfig.distantSsrtThickness(),
                        0.0f);
        RhiStorageBuffer cameraBuffer = cameraBuffer();
        cameraBuffer.upload(camera.buffer(), 0L);

        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant directional shadow + distant SSRT resolve",
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
                        new StorageImageBinding(8, cascadeIndexOutput, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(9, productionOutput, StorageAccess.WRITE_ONLY)
                )
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

    private static Vector3f transformDirection(Matrix4f matrix, Vector3f source, Vector3f dest) {
        float x = source.x;
        float y = source.y;
        float z = source.z;
        return dest.set(
                matrix.m00() * x + matrix.m10() * y + matrix.m20() * z,
                matrix.m01() * x + matrix.m11() * y + matrix.m21() * z,
                matrix.m02() * x + matrix.m12() * y + matrix.m22() * z
        );
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
