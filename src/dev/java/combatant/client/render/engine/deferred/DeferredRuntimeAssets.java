/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import combatant.client.render.engine.light.BlockLightEmitterRegistry;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.asset.AssetAutoLoader;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Runtime assets retained by reusable temporal/post renderer services. */
public enum DeferredRuntimeAssets {
    ;

    public static final String SCOPE = "render.deferred";
    public static final Identifier TEMPORAL_PRESENT_FRAGMENT =
            Identifier.fromNamespaceAndPath("combatant", "shaders/deferred_temporal_present.frag");

    private static RenderPipeline temporalPresent;
    private static long preparedResourceGeneration = Long.MIN_VALUE;

    public static boolean active() { return AssetAutoLoader.isScopeActive(SCOPE); }

    public static boolean shouldPublishExtendedShader(Identifier resourceId) {
        if (resourceId == null || !"combatant".equals(resourceId.getNamespace())) return true;
        String path = resourceId.getPath();
        boolean serviceOwned = path.equals(TEMPORAL_PRESENT_FRAGMENT.getPath()) || path.startsWith("shaders/deferred/");
        return !serviceOwned || active();
    }

    public static RenderPipeline temporalPresent() {
        if (!active() || temporalPresent == null) {
            throw new IllegalStateException("Renderer-service temporal present asset is not active");
        }
        return temporalPresent;
    }

    @AssetLoad(value = AssetLoadPhase.ACTIVATE, scope = SCOPE, order = 100)
    public static void activate(ResourceManager resources) { prepare(resources, false); }

    @AssetLoad(value = AssetLoadPhase.POST_RELOAD, scope = SCOPE, order = 100)
    public static void reload(ResourceManager resources) {
        prepare(resources, true);
        DevDeferredRuntime.world().requestHistoryReset(DeferredHistoryResetReason.RESOURCE_RELOAD);
    }

    public static void reprepareAfterBackendSwitch(ResourceManager resources) { prepare(resources, false); }

    private static void prepare(ResourceManager resources, boolean rebuildNativePrograms) {
        if (resources == null) throw new IllegalArgumentException("resources");
        RenderSystem.assertOnRenderThread();
        BlockLightEmitterRegistry.global().reload(resources);
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
        GpuDevice device = RenderSystem.getDevice();
        device.precompilePipeline(temporalPresent, (identifier, shaderType) -> {
            String source = CombatantShaderSources.load(resources, identifier, shaderType);
            ShaderCostRegistry.analyze(identifier, shaderType, source);
            return source;
        });
        if (rebuildNativePrograms) DevDeferredRuntime.graph().releaseBackendResources(CombatantRenderSystem.rhi());
        DevDeferredRuntime.graph().prepareBackendResources();
        preparedResourceGeneration = combatant.client.util.resources.RenderResourceReadiness.generation();
        DebugLog.renderThreadOnChange("combatant.renderer.services.assets", "ready|" + preparedResourceGeneration,
                "[RendererServices] runtime assets prepared (resource generation=%d)", preparedResourceGeneration);
    }

    @AssetLoad(value = AssetLoadPhase.DEACTIVATE, scope = SCOPE, order = 100)
    public static void release(ResourceManager resources) {
        RenderSystem.assertOnRenderThread();
        DevDeferredRuntime.graph().releaseBackendResources(CombatantRenderSystem.rhi());
        preparedResourceGeneration = Long.MIN_VALUE;
    }
}
