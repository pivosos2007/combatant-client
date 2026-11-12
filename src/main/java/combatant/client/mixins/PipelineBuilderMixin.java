/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import combatant.client.render.engine.hdr.HdrPipelineVariants;
import combatant.client.render.engine.shader.CombatantShaderSources;
import combatant.client.render.iris.IrisRuntime;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PipelineBuilder.class, remap = false)
public abstract class PipelineBuilderMixin {
    @ModifyReturnValue(method = "compilePipeline", at = @At("RETURN"), remap = false)
    private CompletableFuture<CompiledRenderPipeline.Pending> combatant$rememberPipeline(
            CompletableFuture<CompiledRenderPipeline.Pending> pending, RenderPipeline pipeline,
            ShaderSource source, Executor executor) {
        return IrisRuntime.isModLoaded() ? pending : HdrPipelineVariants.trackCompilation(pipeline, pending);
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "generateBackendCreateInfo", remap = false,
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/frontend/shaders/PipelineBuilder;loadShaderSource(Lnet/minecraft/resources/Identifier;Lcom/mojang/renderpearl/api/pipeline/ShaderType;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;)Ljava/lang/String;"))
    private String combatant$linearSceneShader(Identifier id, ShaderType type, ShaderSource source,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<String> original,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline) {
        String text = original.call(id, type, source);
        if (type != ShaderType.FRAGMENT || !pipeline.getShaderDefines().flags().contains(
                combatant.client.render.engine.hdr.HdrPipelineVariants.LEGACY_COLOR)) return text;
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        try {
            String expanded = CombatantShaderSources.expandIncludes(resources,
                    CombatantShaderSources.vanillaResourceId(id, type), text, new java.util.HashSet<>());
            String functions = CombatantShaderSources.load(resources,
                    Identifier.fromNamespaceAndPath("combatant", "shaders/include/scene_color.glsl"));
            return combatant.client.render.engine.hdr.HdrShaderSources.linearize(expanded, functions);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to linearize scene shader " + id, failure);
        }
    }

    @Inject(
            method = "loadShaderSource(Lnet/minecraft/resources/Identifier;Lcom/mojang/renderpearl/api/pipeline/ShaderType;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void combatant$loadExtendedShaderSource(Identifier id,
                                                            ShaderType type,
                                                            ShaderSource source,
                                                            CallbackInfoReturnable<String> cir) {
        if (!CombatantShaderSources.isCombatantExtendedSource(id)) return;

        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager resources = minecraft != null ? minecraft.getResourceManager() : null;
        if (resources == null) return;

        cir.setReturnValue(CombatantShaderSources.load(resources, id, type));
    }
}
