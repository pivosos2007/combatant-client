/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.iris.IrisCombatantFrameHooks;
import combatant.client.render.iris.IrisSceneDepth;

@Pseudo
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class IrisRenderingPipelineFinalizeMixin {
    @Shadow
    @Final
    private RenderTargets renderTargets;

    @Inject(method = "beginLevelRendering", at = @At("HEAD"), remap = false)
    private void combatant$resetIrisSceneDepth(CallbackInfo ci) {
        IrisSceneDepth.resetFrame();
    }

    @Inject(method = "beginTranslucents", at = @At("HEAD"), remap = false)
    private void combatant$capturePreTranslucentDepth(CallbackInfo ci) {
        PreTranslucentDepth.capture();
    }

    @Inject(method = "finalizeLevelRendering", at = @At("HEAD"), remap = false)
    private void combatant$captureIrisSceneBeforeFinalPass(CallbackInfo ci) {
        IrisSceneDepth.capture(renderTargets);
    }

    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"), remap = false)
    private void combatant$renderAfterIrisFinalPass(CallbackInfo ci) {
        IrisCombatantFrameHooks.renderAfterIrisFinalization();
    }

    @Inject(method = "destroy", at = @At("HEAD"), remap = false)
    private void combatant$shutdownDepth(CallbackInfo ci) {
        IrisSceneDepth.shutdown();
    }
}
