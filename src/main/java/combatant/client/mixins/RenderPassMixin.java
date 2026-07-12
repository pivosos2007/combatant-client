/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.util.logging.DebugLog;

@Mixin(RenderPass.class)
public abstract class RenderPassMixin {
    @Inject(method = "setPipeline", at = @At("RETURN"))
    private void combatant$applyRhiPipelineState(RenderPipeline pipeline, CallbackInfo ci) {
        try {
            CombatantRenderSystem.rhi().pipelineState().applyPipelineState(pipeline);
        } catch (Throwable t) {
            DebugLog.error("[ShapeClip] pipeline state hook failed for " + pipeline, t);
        }
    }
}
