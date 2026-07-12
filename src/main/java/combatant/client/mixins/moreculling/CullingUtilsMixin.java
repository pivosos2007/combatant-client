/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.moreculling;

import combatant.client.render.ShaderEspRenderContext;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "ca.fxco.moreculling.utils.CullingUtils", remap = false)
public abstract class CullingUtilsMixin {

    @Inject(method = "shouldCullBack", at = @At("HEAD"), cancellable = true, remap = false)
    private static void combatant$shaderEspKeepsItemFrameBack(ItemFrameRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (ShaderEspRenderContext.isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldShowMapFace", at = @At("HEAD"), cancellable = true, remap = false)
    private static void combatant$shaderEspKeepsMapFace(Direction direction, ItemFrameRenderState state, Vec3 cameraPos, CallbackInfoReturnable<Boolean> cir) {
        if (ShaderEspRenderContext.isActive()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "shouldCullPaintingBack", at = @At("HEAD"), cancellable = true, remap = false)
    private static void combatant$shaderEspKeepsPaintingBack(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (ShaderEspRenderContext.isActive()) {
            cir.setReturnValue(false);
        }
    }
}
