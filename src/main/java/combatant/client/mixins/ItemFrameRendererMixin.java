/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NoRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemFrameRenderer.class, priority = 1200)
public abstract class ItemFrameRendererMixin {
    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void combatant$noRenderItemFrame(
            ItemFrameRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null && noRender.offEntity("item_frames")) {
            ci.cancel();
        }
    }

    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/decoration/ItemFrame;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$noRenderItemFrameName(ItemFrame frame, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null && noRender.offEntity("item_frame_name_tags")) {
            cir.setReturnValue(false);
        }
    }
}
