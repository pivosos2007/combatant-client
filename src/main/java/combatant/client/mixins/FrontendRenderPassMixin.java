/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.hdr.HdrPipelineVariants;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.util.logging.DebugLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendRenderPass.class)
public abstract class FrontendRenderPassMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private java.util.List<com.mojang.renderpearl.api.commands.RenderPassDescriptor.Attachment<
            java.util.Optional<org.joml.Vector4fc>>> colorAttachments;

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true)
    private CompiledRenderPipeline combatant$attachmentPipeline(CompiledRenderPipeline pipeline) {
        return HdrPipelineVariants.select(pipeline, colorAttachments);
    }

    @Inject(
            method = "setPipeline(Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V",
            at = @At("RETURN")
    )
    private void combatant$applyRhiPipelineState(CompiledRenderPipeline compiled, CallbackInfo ci) {
        if (!(compiled instanceof FrontendRenderPipeline frontend)) return;
        RenderPipeline pipeline = HdrPipelineVariants.sourceOf(compiled);
        if (pipeline == null) pipeline = RenderPipelineRegistry.global().nativeById(frontend.name());
        if (pipeline == null) return;
        try {
            CombatantRenderSystem.rhi().pipelineState().applyPipelineState(pipeline);
        } catch (Throwable t) {
            DebugLog.error("[ShapeClip] pipeline state hook failed for " + pipeline, t);
        }
    }
}
