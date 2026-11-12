/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.hdr.HdrScene;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.runtime.RuntimeGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The world color scope includes PRE_HAND post-processing and ends before hand rendering. */
@Mixin(GameRenderer.class)
public abstract class HdrGameRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderTarget mainRenderTarget;

    @WrapMethod(method = "renderLevel")
    private void combatant$hdrScene(Operation<Void> original) {
        HdrScene.reset();
        if (!RuntimeGate.isPanic() && !IrisRuntime.isModLoaded()
                && !ClickGuiRenderer.suppressesBackgroundRendering()
                && !combatant.client.features.gui.preview.VisualPreviewRuntime.isActive() && HdrScene.requested()) {
            var tick = minecraft.getDeltaTracker();
            CombatantRenderSystem.beginFrame(tick.getGameTimeDeltaPartialTick(true),
                    tick.getGameTimeDeltaTicks(), tick.getRealtimeDeltaTicks(), new Matrix4f(), new Matrix4f());
            HdrScene.begin(mainRenderTarget);
        }
        boolean completed = false;
        try {
            original.call();
            completed = true;
        } finally {
            if (!IrisRuntime.isModLoaded()) MsaaWorldTarget.resolveToMain(minecraft);
            if (completed) HdrScene.present();
            else HdrScene.reset();
        }
    }

    /**
     * PRE_HAND is dispatched inside render3dHud before this call. Keep HDR active until its
     * post-process graph has written back to the scene; the hand and screen effects use SDR.
     */
    @WrapOperation(method = "render3dHud", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(" +
                    "Lnet/minecraft/client/renderer/state/level/CameraRenderState;" +
                    "Lnet/minecraft/client/renderer/state/level/PlayerRenderState;" +
                    "Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
    private void combatant$presentHdrBeforeHand(GameRenderer renderer,
                                                CameraRenderState camera,
                                                PlayerRenderState player,
                                                GpuTextureView handDepth,
                                                Operation<Void> original) {
        HdrScene.present();
        original.call(renderer, camera, player, handDepth);
    }

    // render3dHud chooses the hand depth before PRE_HAND. It must use the SDR target,
    // not the HDR world target which is still active until the hand invocation.
    @ModifyExpressionValue(method = {"renderItemInHand", "integrate3DHudDepth"},
            at = @At(value = "FIELD", target =
                    "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget combatant$linearHudTarget(RenderTarget original) {
        return HdrScene.resolveTarget(original);
    }
}
