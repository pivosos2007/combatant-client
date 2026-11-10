/* Copyright (c) 2026 pivosos2007. GPL-3.0-only. */
package combatant.client.mixins.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import combatant.client.render.engine.deferred.DevDeferredRuntime;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Sodium 0.9.2 RenderPearl pipeline layout extension; Iris/shaderpack paths are not involved. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class DevDeferredSodiumShaderChunkRendererMixin {
    @Shadow(remap = false) @Final
    private static Map<TerrainRenderPass, RenderPipeline> programs;
    @Unique private static long combatant$previousLayout = Long.MIN_VALUE;
    @Unique private static boolean combatant$previousEnabled;

    @Inject(method = "compileProgram", at = @At("HEAD"), remap = false)
    private void combatant$refreshGbufferLayout(TerrainRenderPass pass,
                                                  net.minecraft.client.renderer.oit.OitStage oitStage,
                                                  CallbackInfoReturnable<RenderPipeline> cir) {
        var world = DevDeferredRuntime.world();
        long generation = world.geometryPipelineGeneration();
        boolean enabled = world.geometryCaptureEnabled();
        if (generation != combatant$previousLayout || enabled != combatant$previousEnabled) {
            programs.clear();
            combatant$previousLayout = generation;
            combatant$previousEnabled = enabled;
        }
    }

    @WrapOperation(method = "createShader", at = @At(value = "INVOKE", target =
            "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;build()Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;"),
            remap = false)
    private RenderPipeline combatant$configureOpaqueMrt(RenderPipeline.Builder builder,
                                                       Operation<RenderPipeline> original,
                                                       @Local(argsOnly = true) TerrainRenderPass pass) {
        if (!pass.isTranslucent() && DevDeferredRuntime.world().geometryCaptureEnabled()) {
            DevDeferredRuntime.world().configureGeometryPipeline(builder);
        }
        return original.call(builder);
    }
}
