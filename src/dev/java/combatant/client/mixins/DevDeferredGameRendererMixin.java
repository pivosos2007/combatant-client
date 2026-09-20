/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import combatant.client.render.engine.deferred.DevDeferredRuntime;
import combatant.client.render.engine.depth.WorldSceneDepth;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render-stage bridge owned exclusively by the dev deferred module. */
@Mixin(GameRenderer.class)
public abstract class DevDeferredGameRendererMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void combatant$devDeferredFrameBoundary(DeltaTracker tickCounter, CallbackInfo ci) {
        DevDeferredRuntime.serviceFrameBoundary();
    }

    @Inject(
            method = "renderLevel",
            at = @At(value = "INVOKE_STRING",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
                    args = "ldc=hand", shift = At.Shift.BEFORE)
    )
    private void combatant$devDeferredBeforePost(DeltaTracker tickCounter, CallbackInfo ci) {
        combatant$runCameraPost(false);
    }

    @Inject(
            method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",
            at = @At("HEAD")
    )
    private void combatant$devDeferredAfterPost(CameraRenderState camera, float tickDelta,
                                                Matrix4fc positionMatrix, CallbackInfo ci) {
        combatant$runCameraPost(true);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void combatant$devDeferredFinalComposite(DeltaTracker tickCounter, CallbackInfo ci) {
        if (minecraft == null || minecraft.gameRenderer == null) return;
        var main = minecraft.gameRenderer.mainRenderTarget();
        if (main == null) return;
        var depth = WorldSceneDepth.hasMain() ? WorldSceneDepth.mainDepthView() : main.getDepthTextureView();
        DevDeferredRuntime.world().finalComposite(main.getColorTextureView(), depth);
    }

    @Unique
    private void combatant$runCameraPost(boolean afterProductionPost) {
        if (minecraft == null || minecraft.gameRenderer == null) return;
        var main = minecraft.gameRenderer.mainRenderTarget();
        if (main == null) return;
        var depth = WorldSceneDepth.hasMain() ? WorldSceneDepth.mainDepthView() : main.getDepthTextureView();
        if (afterProductionPost) {
            DevDeferredRuntime.world().afterPostProcess(main.getColorTextureView(), depth);
        } else {
            DevDeferredRuntime.world().beforePostProcess(main.getColorTextureView(), depth);
        }
    }
}
