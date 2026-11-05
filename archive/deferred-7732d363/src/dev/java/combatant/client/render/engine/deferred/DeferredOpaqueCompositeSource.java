/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
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
import combatant.client.render.engine.uniform.impl.DeferredLightingUniforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Neutral opaque-light composition after the individual lighting producers have finished.
 *
 * <p>{@link DeferredResource#LIGHTING_COLOR} is the pre-reflection HDR result. The final
 * reflection pass writes {@link DeferredResource#OPAQUE_REFLECTED_RADIANCE}; sky and participating
 * media composition then produce {@link DeferredResource#SCENE_RADIANCE}; publishing to the mutable
 * Minecraft scene target happens only after that resource is complete.</p>
 */
final class DeferredOpaqueCompositeSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier INDIRECT_COMPOSITE = id("deferred/indirect_composite");
    private static final Identifier REFLECTION_COMPOSITE = id("deferred/reflection_composite");

    private static final Std430StructLayout INDIRECT_DATA_LAYOUT = Std430StructLayout.builder()
            .member("flags", Std430Type.VEC4)
            .build();

    private static final Std430StructLayout REFLECTION_DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .member("flags", Std430Type.VEC4)
            .build();

    private static final Std430StructLayout SKY_STATE_LAYOUT = Std430StructLayout.builder()
            .member("state0", Std430Type.VEC4)
            .member("state1", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout INDIRECT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout REFLECTION_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(10, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline indirectPipeline;
    private RhiComputePipeline reflectionPipeline;
    private RhiStorageBuffer indirectData;
    private RhiStorageBuffer reflectionData;
    private RhiStorageBuffer fallbackSkyState;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.indirect.composite", DeferredStage.INDIRECT_COMPOSITE)
                .read(DeferredResource.OPAQUE_BASE_RADIANCE, DeferredResource.GBUFFER_SURFACE,
                        DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.GBUFFER_DEPTH, DeferredResource.RESOLVED_DEPTH)
                .optionalRead(DeferredResource.AMBIENT_OCCLUSION,
                        DeferredResource.INDIRECT_LIGHT, DeferredResource.INDIRECT_CONFIDENCE)
                .write(DeferredResource.LIGHTING_COLOR)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.OPAQUE_BASE_RADIANCE))
                .execute(this::composeIndirect)
                .build());

        passes.add(DeferredPassSpec.builder("world.reflection.composite", DeferredStage.REFLECTION_COMPOSITE)
                .read(DeferredResource.LIGHTING_COLOR, DeferredResource.GBUFFER_SURFACE,
                        DeferredResource.GBUFFER_GEOMETRY, DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.RESOLVED_DEPTH, DeferredResource.GBUFFER_DEPTH)
                .optionalRead(DeferredResource.REFLECTION_COLOR, DeferredResource.REFLECTION_CONFIDENCE,
                        DeferredResource.SKY_SPECULAR_RADIANCE, DeferredResource.SKY_ENVIRONMENT_STATE)
                .write(DeferredResource.OPAQUE_REFLECTED_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.LIGHTING_COLOR))
                .execute(this::composeReflections)
                .build());

        passes.add(DeferredPassSpec.builder("world.opaque.publish", DeferredStage.VOLUMETRIC_MEDIA_COMPOSITE)
                .priority(100)
                .read(DeferredResource.SCENE_RADIANCE, DeferredResource.GBUFFER_AUXILIARY,
                        DeferredResource.GBUFFER_DEPTH, DeferredResource.RESOLVED_DEPTH)
                .write(DeferredResource.SCENE_COLOR)
                .when(context -> context.isValid(DeferredResource.SCENE_RADIANCE)
                        && context.resources().texture(DeferredResource.SCENE_COLOR) != null
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.resources().texture(DeferredResource.GBUFFER_AUXILIARY) != null
                        && context.isValid(DeferredResource.LIGHTING_COLOR))
                .execute(this::publish)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        indirectPipeline();
        reflectionPipeline();
        indirectData();
        reflectionData();
        fallbackSkyState();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void composeIndirect(DeferredPassContext context) {
        ensureOwner(context.rhi());
        GpuTextureView base = requireTexture(context, DeferredResource.OPAQUE_BASE_RADIANCE);
        GpuTextureView surface = requireTexture(context, DeferredResource.GBUFFER_SURFACE);
        GpuTextureView material = requireTexture(context, DeferredResource.GBUFFER_MATERIAL);
        boolean hasIndirect = context.featureEnabled(DeferredFeature.INDIRECT_LIGHT)
                && context.isValid(DeferredResource.INDIRECT_LIGHT)
                && context.isValid(DeferredResource.INDIRECT_CONFIDENCE);
        boolean hasAo = context.isValid(DeferredResource.AMBIENT_OCCLUSION);
        GpuTextureView indirect = hasIndirect
                ? requireTexture(context, DeferredResource.INDIRECT_LIGHT) : base;
        GpuTextureView confidence = hasIndirect
                ? requireTexture(context, DeferredResource.INDIRECT_CONFIDENCE) : surface;
        GpuTextureView ao = hasAo
                ? requireTexture(context, DeferredResource.AMBIENT_OCCLUSION) : surface;
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        GpuTextureView currentDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        RhiStorageImage output = requireImage(context, DeferredResource.LIGHTING_COLOR);

        Std430Writer writer = new Std430Writer(INDIRECT_DATA_LAYOUT, 1)
                .putVec4(0, "flags", hasIndirect ? 1.0f : 0.0f, hasAo ? 1.0f : 0.0f, 0.0f, 0.0f);
        RhiStorageBuffer data = indirectData();
        data.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant indirect lighting composite",
                indirectPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(9, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, base, linear),
                        new SampledTextureBinding(1, indirect, linear),
                        new SampledTextureBinding(2, confidence, linear),
                        new SampledTextureBinding(3, surface, nearest),
                        new SampledTextureBinding(4, material, nearest),
                        new SampledTextureBinding(5, ao, linear),
                        new SampledTextureBinding(6, gbufferDepth, nearest),
                        new SampledTextureBinding(7, currentDepth, nearest)
                ),
                List.of(new StorageImageBinding(8, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void composeReflections(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        if (current == null) return;

        GpuTextureView base = requireTexture(context, DeferredResource.LIGHTING_COLOR);
        GpuTextureView surface = requireTexture(context, DeferredResource.GBUFFER_SURFACE);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView material = requireTexture(context, DeferredResource.GBUFFER_MATERIAL);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        boolean hasReflection = context.featureEnabled(DeferredFeature.REFLECTIONS)
                && context.isValid(DeferredResource.REFLECTION_COLOR)
                && context.isValid(DeferredResource.REFLECTION_CONFIDENCE);
        GpuTextureView reflection = hasReflection
                ? requireTexture(context, DeferredResource.REFLECTION_COLOR) : base;
        GpuTextureView confidence = hasReflection
                ? requireTexture(context, DeferredResource.REFLECTION_CONFIDENCE) : surface;
        boolean hasSkyResources = context.featureEnabled(DeferredFeature.SKY)
                && context.isValid(DeferredResource.SKY_SPECULAR_RADIANCE)
                && context.isValid(DeferredResource.SKY_ENVIRONMENT_STATE)
                && context.resources().texture(DeferredResource.SKY_SPECULAR_RADIANCE) != null
                && context.resources().buffer(DeferredResource.SKY_ENVIRONMENT_STATE) != null;
        GpuTextureView skySpecular = hasSkyResources
                ? requireTexture(context, DeferredResource.SKY_SPECULAR_RADIANCE) : base;
        RhiStorageBuffer skyState = hasSkyResources
                ? requireBuffer(context, DeferredResource.SKY_ENVIRONMENT_STATE) : fallbackSkyState();
        RhiStorageImage output = requireImage(context, DeferredResource.OPAQUE_REFLECTED_RADIANCE);
        boolean zeroToOne = zeroToOneDepth(context);

        Std430Writer writer = new Std430Writer(REFLECTION_DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", current.inverseProjection())
                .putMat4(0, "inverseView", current.inverseView())
                .putVec4(0, "depthTransform",
                        zeroToOne ? 1.0f : 2.0f,
                        zeroToOne ? 0.0f : -1.0f,
                        zeroToOne ? 1.0f : 0.5f,
                        zeroToOne ? 0.0f : 0.5f)
                .putVec4(0, "flags", hasReflection ? 1.0f : 0.0f, hasSkyResources ? 1.0f : 0.0f, 0.0f, 0.0f);
        RhiStorageBuffer data = reflectionData();
        data.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler skySampler = RenderSystem.getSamplerCache().getSampler(
                AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, true
        );
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant reflection composite",
                reflectionPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(
                        new StorageBinding(9, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(11, skyState, 0L, SKY_STATE_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, base, linear),
                        new SampledTextureBinding(1, reflection, linear),
                        new SampledTextureBinding(2, confidence, linear),
                        new SampledTextureBinding(3, surface, nearest),
                        new SampledTextureBinding(4, geometry, nearest),
                        new SampledTextureBinding(5, material, nearest),
                        new SampledTextureBinding(6, depth, nearest),
                        new SampledTextureBinding(7, gbufferDepth, nearest),
                        new SampledTextureBinding(10, skySpecular, skySampler)
                ),
                List.of(new StorageImageBinding(8, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void publish(DeferredPassContext context) {
        GpuTextureView source = requireTexture(context, DeferredResource.SCENE_RADIANCE);
        GpuTextureView target = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuTextureView auxiliary = requireTexture(context, DeferredResource.GBUFFER_AUXILIARY);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        GpuTextureView currentDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.rhi().drawFullscreen(
                FullscreenDrawCommand.builder("Combatant opaque radiance publish")
                        .colorAttachment(target)
                        .pipeline(DeferredRuntimeAssets.opaquePublish())
                        .uniform("DeferredLighting", DeferredLightingUniforms.get())
                        .sampler("u_Source", source, linear)
                        .sampler("u_GbufferAuxiliary", auxiliary, nearest)
                        .sampler("u_GbufferDepth", gbufferDepth, nearest)
                        .sampler("u_CurrentDepth", currentDepth, nearest)
                        .build()
        );
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline indirectPipeline() {
        if (owner == null) throw new IllegalStateException("Opaque composite has no RHI owner");
        if (indirectPipeline == null) {
            indirectPipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-indirect-composite", INDIRECT_COMPOSITE, INDIRECT_LAYOUT
            ));
        }
        return indirectPipeline;
    }

    private RhiComputePipeline reflectionPipeline() {
        if (owner == null) throw new IllegalStateException("Opaque composite has no RHI owner");
        if (reflectionPipeline == null) {
            reflectionPipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-reflection-composite", REFLECTION_COMPOSITE, REFLECTION_LAYOUT
            ));
        }
        return reflectionPipeline;
    }

    private RhiStorageBuffer indirectData() {
        if (owner == null) throw new IllegalStateException("Opaque composite has no RHI owner");
        if (indirectData == null) {
            indirectData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-indirect-composite-data", INDIRECT_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return indirectData;
    }

    private RhiStorageBuffer reflectionData() {
        if (owner == null) throw new IllegalStateException("Opaque composite has no RHI owner");
        if (reflectionData == null) {
            reflectionData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-reflection-composite-data", REFLECTION_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return reflectionData;
    }


    private RhiStorageBuffer fallbackSkyState() {
        if (owner == null) throw new IllegalStateException("Opaque composite has no RHI owner");
        if (fallbackSkyState == null) {
            fallbackSkyState = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-reflection-fallback-sky-state", SKY_STATE_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
            Std430Writer writer = new Std430Writer(SKY_STATE_LAYOUT, 1)
                    .putVec4(0, "state0", 0.0f, 0.0f, 0.0f, 0.0f)
                    .putVec4(0, "state1", 0.0f, 0.0f, 0.0f, 0.0f);
            fallbackSkyState.upload(writer.buffer(), 0L);
        }
        return fallbackSkyState;
    }

    private void closeOwned() {
        if (indirectPipeline != null) {
            try { indirectPipeline.close(); } catch (Throwable ignored) { }
            indirectPipeline = null;
        }
        if (reflectionPipeline != null) {
            try { reflectionPipeline.close(); } catch (Throwable ignored) { }
            reflectionPipeline = null;
        }
        if (indirectData != null) {
            try { indirectData.close(); } catch (Throwable ignored) { }
            indirectData = null;
        }
        if (reflectionData != null) {
            try { reflectionData.close(); } catch (Throwable ignored) { }
            reflectionData = null;
        }
        if (fallbackSkyState != null) {
            try { fallbackSkyState.close(); } catch (Throwable ignored) { }
            fallbackSkyState = null;
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
