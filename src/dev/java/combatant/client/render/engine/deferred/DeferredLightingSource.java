/* Copyright (c) 2026 pivosos2007. GPL-3.0-only. */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.uniform.impl.DeferredLightingUniforms;

import java.util.ArrayList;

final class DeferredLightingSource {
    private DeferredLightingSource() { }

    static void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.lighting.neutral", DeferredStage.LIGHTING)
                .read(DeferredResource.GBUFFER_SURFACE, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_MATERIAL, DeferredResource.GBUFFER_DEPTH,
                        DeferredResource.RESOLVED_DEPTH, DeferredResource.ENVIRONMENT_IRRADIANCE)
                .optionalRead(DeferredResource.AMBIENT_OCCLUSION)
                .write(DeferredResource.DIRECT_LIGHTING_COLOR)
                .when(context -> context.isValid(DeferredResource.ENVIRONMENT_IRRADIANCE)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.primaryView().current() != null)
                .execute(DeferredLightingSource::shade)
                .build());
        passes.add(DeferredPassSpec.builder("world.lighting.publish", DeferredStage.LIGHTING)
                .priority(100)
                .read(DeferredResource.DIRECT_LIGHTING_COLOR, DeferredResource.GBUFFER_AUXILIARY,
                        DeferredResource.GBUFFER_DEPTH)
                .write(DeferredResource.SCENE_COLOR)
                .when(context -> context.isValid(DeferredResource.DIRECT_LIGHTING_COLOR))
                .execute(DeferredLightingSource::publish)
                .build());
    }

    private static void shade(DeferredPassContext context) {
        var resources = context.resources();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var surface = resources.texture(DeferredResource.GBUFFER_SURFACE);
        boolean ambientValid = context.isValid(DeferredResource.AMBIENT_OCCLUSION);
        DeferredLightingUniforms.update(DevDeferredRuntime.world().lightingState(), context.worldState(),
                context.primaryView().current(), context.rhi().capabilities().zeroToOneDepth(), false, ambientValid);
        context.rhi().drawFullscreen(FullscreenDrawCommand.builder("Combatant deferred terrain lighting")
                .colorAttachment(resources.texture(DeferredResource.DIRECT_LIGHTING_COLOR))
                .pipeline(DeferredRuntimeAssets.terrainLighting())
                .uniform("DeferredLighting", DeferredLightingUniforms.get())
                .sampler("u_GbufferSurface", surface, sampler)
                .sampler("u_GbufferGeometry", resources.texture(DeferredResource.GBUFFER_GEOMETRY), sampler)
                .sampler("u_GbufferMaterial", resources.texture(DeferredResource.GBUFFER_MATERIAL), sampler)
                .sampler("u_GbufferDepth", resources.texture(DeferredResource.GBUFFER_DEPTH), sampler)
                .sampler("u_ResolvedDepth", resources.texture(DeferredResource.RESOLVED_DEPTH), sampler)
                .sampler("u_EnvironmentIrradiance", resources.texture(DeferredResource.ENVIRONMENT_IRRADIANCE), sampler)
                .sampler("u_ShadowVisibility", surface, sampler)
                .sampler("u_CloudShadowVisibility", surface, sampler)
                .sampler("u_AmbientVisibility", ambientValid
                        ? resources.texture(DeferredResource.AMBIENT_OCCLUSION) : surface, sampler)
                .build());
    }

    private static void publish(DeferredPassContext context) {
        var resources = context.resources();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.rhi().drawFullscreen(FullscreenDrawCommand.builder("Combatant deferred terrain publish")
                .colorAttachment(resources.texture(DeferredResource.SCENE_COLOR))
                .pipeline(DeferredRuntimeAssets.terrainPublish())
                .uniform("DeferredLighting", DeferredLightingUniforms.get())
                .sampler("u_Source", resources.texture(DeferredResource.DIRECT_LIGHTING_COLOR), sampler)
                .sampler("u_GbufferAuxiliary", resources.texture(DeferredResource.GBUFFER_AUXILIARY), sampler)
                .sampler("u_GbufferDepth", resources.texture(DeferredResource.GBUFFER_DEPTH), sampler)
                .build());
    }
}
