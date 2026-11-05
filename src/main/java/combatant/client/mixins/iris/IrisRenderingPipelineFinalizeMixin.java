/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import com.google.common.collect.ImmutableSet;
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
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.iris.IrisShaderpackMsaaIntegration;
import combatant.client.render.iris.IrisShaderpackTemporalIntegration;

@Pseudo
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class IrisRenderingPipelineFinalizeMixin {
    @Shadow
    @Final
    private RenderTargets renderTargets;

    @Shadow @Final private ImmutableSet<Integer> flippedAfterTranslucent;

    @Inject(method = "beginLevelRendering", at = @At("HEAD"), remap = false)
    private void combatant$resetIrisSceneDepth(CallbackInfo ci) {
        IrisRuntime.observePipeline(this);
        IrisShaderpackMsaaIntegration.beforeFrameClear();
        IrisSceneDepth.resetFrame();
    }

    @Inject(method = "onBeginClear", at = @At("HEAD"), remap = false)
    private void combatant$seedMsaaAfterMainClear(CallbackInfo ci) {
        IrisShaderpackMsaaIntegration.beginFrame(IrisRuntime.integrationEpoch());
    }

    @Inject(method = "beginHand", at = @At("HEAD"), remap = false)
    private void combatant$renderImportedOpaqueBeforeHand(CallbackInfo ci) {
        // This is the final opaque-world boundary before Iris snapshots depthtex2/no-hand.
        // The first-person hand itself is suppressed in Iris and submitted only after finalization.
        IrisSceneDepth.capture(renderTargets);
        IrisRuntime.renderImportedGeometryPrimary();
        combatant.client.features.playermodel.PlayerModelGpuRenderer.renderIris(false);
        IrisShaderpackMsaaIntegration.resolveDepth();
    }

    @Inject(method = "beginTranslucents", at = @At("HEAD"), remap = false)
    private void combatant$capturePreTranslucentDepth(CallbackInfo ci) {
        // Publish a valid single-sample representative for Iris' depth copies and pre-deferred
        // helper passes. Packed colortex1/2 use sample-0 resolve; d4 shades every real MSAA sample.
        IrisShaderpackMsaaIntegration.resolveWorld();
        IrisSceneDepth.capture(renderTargets);
        PreTranslucentDepth.capture();
    }

    @Inject(method = "beginTranslucents", at = @At("TAIL"), remap = false)
    private void combatant$seedMsaaAfterDeferred(CallbackInfo ci) {
        // Deferred shading has already consumed the opaque multisample G-buffer. Only initialize
        // the targets written by the upcoming forward pass; reseeding all targets here flattened
        // per-sample depth/coverage and reintroduced stale scene data.
        IrisShaderpackMsaaIntegration.seedForward();
        IrisRuntime.renderImportedGeometryTranslucent();
        combatant.client.features.playermodel.PlayerModelGpuRenderer.renderIris(true);
        // Deferred is complete; Iris has not yet drawn forward translucent entities.
        combatant.client.render.iris.IrisEarlyWorldBridge.render(renderTargets, flippedAfterTranslucent);
    }

    @Inject(method = "finalizeLevelRendering", at = @At("HEAD"), remap = false)
    private void combatant$captureIrisSceneBeforeFinalPass(CallbackInfo ci) {
        IrisShaderpackMsaaIntegration.resolveForward();
        IrisSceneDepth.capture(renderTargets);
        IrisShaderpackTemporalIntegration.beginFrame(renderTargets, IrisRuntime.integrationEpoch());
    }

    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"), remap = false)
    private void combatant$renderAfterIrisFinalPass(CallbackInfo ci) {
        IrisCombatantFrameHooks.renderAfterIrisFinalization();
    }

    @Inject(method = "destroy", at = @At("HEAD"), remap = false)
    private void combatant$shutdownDepth(CallbackInfo ci) {
        IrisRuntime.pipelineDestroyed(this);
        IrisShaderpackMsaaIntegration.shutdown();
        IrisShaderpackTemporalIntegration.shutdown();
        IrisSceneDepth.shutdown();
    }
}
