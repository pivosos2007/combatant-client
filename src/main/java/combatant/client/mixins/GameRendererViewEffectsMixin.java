/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.features.module.modules.visuals.NoRender;

@Mixin(GameRenderer.class)
public abstract class GameRendererViewEffectsMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void disableHurtCamera(CameraRenderState cameraRenderState, PoseStack matrices, CallbackInfo ci) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null && noRender.off("camera_shake")) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void disableViewBobbing(CameraRenderState cameraRenderState, PoseStack matrices, CallbackInfo ci) {
        if (Modules.get(Freecam.class) != null && Modules.get(Freecam.class).isEnabled()) {
            ci.cancel();
            return;
        }

        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null && noRender.off("view_bob")) {
            ci.cancel();
        }
    }
}
