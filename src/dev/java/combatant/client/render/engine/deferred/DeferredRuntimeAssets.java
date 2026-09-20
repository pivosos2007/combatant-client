/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.pipeline.DepthTestFunction;
import combatant.client.render.engine.pipeline.ExtendedRenderPipelineBuilder;
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.shader.CombatantShaderSources;
import combatant.client.render.engine.shader.ShaderCostRegistry;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import combatant.client.util.logging.DebugLog;
import combatant.client.render.engine.light.BlockLightEmitterRegistry;
import combatant.client.util.resources.asset.AssetAutoLoader;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Runtime-owned assets for the optional deferred world path.
 *
 * <p>Nothing in this class is touched by boot-time pipeline precompile while the scope is inactive.
 * Native compute stages are compiled by {@link DeferredPassGraph#prepareBackendResources()} only
 * after activation, and the ordinary full-screen lighting pipeline is also created/precompiled here.</p>
 */
public enum DeferredRuntimeAssets {
    ;

    public static final String SCOPE = "render.deferred";
    public static final Identifier TERRAIN_LIGHTING_FRAGMENT =
            Identifier.fromNamespaceAndPath("combatant", "shaders/deferred_terrain_lighting.frag");
    public static final Identifier TERRAIN_PUBLISH_FRAGMENT =
            Identifier.fromNamespaceAndPath("combatant", "shaders/deferred_terrain_publish.frag");
    public static final Identifier OPAQUE_PUBLISH_FRAGMENT =
            Identifier.fromNamespaceAndPath("combatant", "shaders/deferred_opaque_publish.frag");
    public static final Identifier TEMPORAL_PRESENT_FRAGMENT =
            Identifier.fromNamespaceAndPath("combatant", "shaders/deferred_temporal_present.frag");

    private static RenderPipeline terrainLighting;
    private static RenderPipeline terrainPublish;
    private static RenderPipeline opaquePublish;
    private static RenderPipeline temporalPresent;
    private static long preparedResourceGeneration = Long.MIN_VALUE;

    public static boolean active() {
        return AssetAutoLoader.isScopeActive(SCOPE);
    }

    /** ShaderManager must not eagerly read deferred-only graphics sources while the scope is off. */
    public static boolean shouldPublishExtendedShader(Identifier resourceId) {
        if (resourceId == null) return true;
        if (!"combatant".equals(resourceId.getNamespace())) return true;
        String path = resourceId.getPath();
        boolean waterGraphicsFallback = path.equals("shaders/deferred/water_surface_fallback.vert")
                || path.equals("shaders/deferred/water_surface_fallback.frag")
                || path.equals("shaders/deferred/water_medium_boundary_fallback.frag")
                || path.equals("shaders/deferred/water_reflection_trace_fallback.frag");
        if (waterGraphicsFallback) {
            // These stages back Blaze3D RenderPipeline fallbacks created after the deferred scope
            // becomes active. ShaderManager must already know their sources at that point.
            return true;
        }

        boolean deferredOnly = path.equals(TERRAIN_LIGHTING_FRAGMENT.getPath())
                || path.equals(TERRAIN_PUBLISH_FRAGMENT.getPath())
                || path.equals(OPAQUE_PUBLISH_FRAGMENT.getPath())
                || path.equals(TEMPORAL_PRESENT_FRAGMENT.getPath())
                || path.startsWith("shaders/deferred/");
        return !deferredOnly || active();
    }

    public static RenderPipeline terrainLighting() {
        if (!active() || terrainLighting == null) {
            throw new IllegalStateException("Deferred terrain lighting assets are not active");
        }
        return terrainLighting;
    }

    public static RenderPipeline terrainPublish() {
        if (!active() || terrainPublish == null) {
            throw new IllegalStateException("Deferred terrain publish assets are not active");
        }
        return terrainPublish;
    }

    public static RenderPipeline opaquePublish() {
        if (!active() || opaquePublish == null) {
            throw new IllegalStateException("Deferred opaque publish assets are not active");
        }
        return opaquePublish;
    }

    public static RenderPipeline temporalPresent() {
        if (!active() || temporalPresent == null) {
            throw new IllegalStateException("Deferred temporal present assets are not active");
        }
        return temporalPresent;
    }

    @AssetLoad(value = AssetLoadPhase.ACTIVATE, scope = SCOPE, order = 100)
    public static void activate(ResourceManager resources) {
        prepare(resources, false);
    }

    @AssetLoad(value = AssetLoadPhase.POST_RELOAD, scope = SCOPE, order = 100)
    public static void reload(ResourceManager resources) {
        prepare(resources, true);
        DevDeferredRuntime.world().requestHistoryReset(DeferredHistoryResetReason.RESOURCE_RELOAD);
    }

    /** Device/backend switches happen outside the ordinary resource-reload hook sequence. */
    public static void reprepareAfterBackendSwitch(ResourceManager resources) {
        prepare(resources, false);
    }

    private static void prepare(ResourceManager resources, boolean rebuildNativePrograms) {
        if (resources == null) throw new IllegalArgumentException("resources");
        RenderSystem.assertOnRenderThread();
        BlockLightEmitterRegistry.global().reload(resources);

        if (terrainLighting == null) {
            terrainLighting = new ExtendedRenderPipelineBuilder(CombatantRenderPipelines.meshUniforms())
                    .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/deferred_terrain_lighting"))
                    .withDomain(PipelineDomain.FULLSCREEN)
                    .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
                    .withVertexShader(CombatantRenderPipelines.SHADER_DAMAGE_TINT_VERT)
                    .withFragmentShader(TERRAIN_LIGHTING_FRAGMENT)
                    .withSampler("u_GbufferSurface")
                    .withSampler("u_GbufferGeometry")
                    .withSampler("u_GbufferMaterial")
                    .withSampler("u_GbufferDepth")
                    .withSampler("u_EnvironmentIrradiance")
                    .withSampler("u_ResolvedDepth")
                    .withSampler("u_ShadowVisibility")
                    .withSampler("u_CloudShadowVisibility")
                    .withSampler("u_AmbientVisibility")
                    .withUniform("DeferredLighting", UniformType.UNIFORM_BUFFER)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withoutBlend()
                    // DIRECT_LIGHTING_COLOR is an HDR graph-owned target; the graphics pipeline
                    // must declare the same attachment format instead of inheriting RGBA8_UNORM.
                    .withColorTarget(0, GpuFormat.RGBA16_FLOAT)
                    .withCull(false)
                    .build();
            RenderPipelineRegistry.global().registerNative(terrainLighting);
        }
        if (terrainPublish == null) {
            terrainPublish = new ExtendedRenderPipelineBuilder(CombatantRenderPipelines.meshUniforms())
                    .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/deferred_terrain_publish"))
                    .withDomain(PipelineDomain.FULLSCREEN)
                    .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
                    .withVertexShader(CombatantRenderPipelines.SHADER_DAMAGE_TINT_VERT)
                    .withFragmentShader(TERRAIN_PUBLISH_FRAGMENT)
                    .withSampler("u_Source")
                    .withSampler("u_GbufferAuxiliary")
                    .withSampler("u_GbufferDepth")
                    .withUniform("DeferredLighting", UniformType.UNIFORM_BUFFER)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withoutBlend()
                    .withCull(false)
                    .build();
            RenderPipelineRegistry.global().registerNative(terrainPublish);
        }
        if (opaquePublish == null) {
            opaquePublish = new ExtendedRenderPipelineBuilder(CombatantRenderPipelines.meshUniforms())
                    .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/deferred_opaque_publish"))
                    .withDomain(PipelineDomain.FULLSCREEN)
                    .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
                    .withVertexShader(CombatantRenderPipelines.SHADER_DAMAGE_TINT_VERT)
                    .withFragmentShader(OPAQUE_PUBLISH_FRAGMENT)
                    .withSampler("u_Source")
                    .withSampler("u_GbufferAuxiliary")
                    .withSampler("u_GbufferDepth")
                    .withSampler("u_CurrentDepth")
                    .withUniform("DeferredLighting", UniformType.UNIFORM_BUFFER)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withoutBlend()
                    .withCull(false)
                    .build();
            RenderPipelineRegistry.global().registerNative(opaquePublish);
        }
        if (temporalPresent == null) {
            temporalPresent = new ExtendedRenderPipelineBuilder(CombatantRenderPipelines.meshUniforms())
                    .withLocation(Identifier.fromNamespaceAndPath("combatant", "pipeline/deferred_temporal_present"))
                    .withDomain(PipelineDomain.FULLSCREEN)
                    .withVertexFormat(CombatantVertexFormats.POS2, com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
                    .withVertexShader(CombatantRenderPipelines.SHADER_DAMAGE_TINT_VERT)
                    .withFragmentShader(TEMPORAL_PRESENT_FRAGMENT)
                    .withSampler("u_Source")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withoutBlend()
                    .withCull(false)
                    .build();
            RenderPipelineRegistry.global().registerNative(temporalPresent);
        }

        final GpuDevice device = RenderSystem.getDevice();
        device.precompilePipeline(terrainLighting, (identifier, shaderType) -> {
            String source = CombatantShaderSources.load(resources, identifier, shaderType);
            ShaderCostRegistry.analyze(identifier, shaderType, source);
            return source;
        });
        device.precompilePipeline(terrainPublish, (identifier, shaderType) -> {
            String source = CombatantShaderSources.load(resources, identifier, shaderType);
            ShaderCostRegistry.analyze(identifier, shaderType, source);
            return source;
        });
        device.precompilePipeline(opaquePublish, (identifier, shaderType) -> {
            String source = CombatantShaderSources.load(resources, identifier, shaderType);
            ShaderCostRegistry.analyze(identifier, shaderType, source);
            return source;
        });
        device.precompilePipeline(temporalPresent, (identifier, shaderType) -> {
            String source = CombatantShaderSources.load(resources, identifier, shaderType);
            ShaderCostRegistry.analyze(identifier, shaderType, source);
            return source;
        });

        // Native .comp programs are Combatant-owned rather than ShaderManager-owned. Resource
        // reload must therefore explicitly discard/recompile them while the scope is active.
        if (rebuildNativePrograms) {
            DevDeferredRuntime.graph().releaseBackendResources(CombatantRenderSystem.rhi());
        }
        DevDeferredRuntime.graph().prepareBackendResources();
        preparedResourceGeneration = combatant.client.util.resources.RenderResourceReadiness.generation();
        DebugLog.renderThreadOnChange(
                "combatant.deferred.assets",
                "ready|" + preparedResourceGeneration,
                "[Deferred] runtime assets prepared (resource generation=%d)",
                preparedResourceGeneration
        );
    }

    @AssetLoad(value = AssetLoadPhase.DEACTIVATE, scope = SCOPE, order = 100)
    public static void release(ResourceManager resources) {
        RenderSystem.assertOnRenderThread();
        DevDeferredRuntime.graph().releaseBackendResources(CombatantRenderSystem.rhi());
        preparedResourceGeneration = Long.MIN_VALUE;
        // The immutable RenderPipeline descriptor can stay registered. Combatant-owned native
        // programs are released here; Mojang may retain its graphics pipeline cache until the next
        // device/resource cache invalidation, but no deferred source is loaded at cold startup.
        DebugLog.renderThreadOnChange(
                "combatant.deferred.assets",
                "inactive",
                "[Deferred] runtime assets released"
        );
    }
}
