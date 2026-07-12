/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.iris;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.Chams;
import combatant.client.render.iris.IrisRuntime;

@Mixin(value = GameRenderer.class, priority = 900)
public abstract class IrisVanillaHandInteropMixin {
    @Inject(
            method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;",
                    shift = At.Shift.BEFORE,
                    remap = false
            )
    )
    private void combatant$renderHandInFinalScene(CameraRenderState cameraRenderState,
                                                  float tickProgress,
                                                  Matrix4fc positionMatrix,
                                                  CallbackInfo ci) {
        if (!IrisRuntime.isShaderpackRendererActive()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null
                || cameraRenderState == null
                || cameraRenderState.isPanoramicMode
                || cameraRenderState.entityRenderState == null
                || cameraRenderState.entityRenderState.isSleeping
                || !client.options.getCameraType().isFirstPerson()
                || client.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }

        Chams chams = Modules.get(Chams.class);
        if (chams == null || !chams.isActive()) {
            return;
        }

        GameRenderer renderer = (GameRenderer) (Object) this;
        chams.renderIrisHandMask(renderer, cameraRenderState, positionMatrix, tickProgress);
    }
}
