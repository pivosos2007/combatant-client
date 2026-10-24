/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.moreculling;

import combatant.client.render.ShaderEspRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps item-frame, map and painting backs visible while a shader ESP pass renders. The handlers take no target
 * arguments, so they bind to any More Culling signature (1.9.0 added a second {@code Direction} to
 * {@code shouldCullPaintingBack}); {@code require = 0} turns a future rename into a skipped hook, not a crash.
 */
@Pseudo
@Mixin(targets = "ca.fxco.moreculling.utils.CullingUtils", remap = false)
public abstract class CullingUtilsMixin {

    @Inject(method = "shouldCullBack", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void combatant$shaderEspKeepsItemFrameBack(CallbackInfoReturnable<Boolean> cir) {
        if (ShaderEspRenderContext.isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldShowMapFace", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void combatant$shaderEspKeepsMapFace(CallbackInfoReturnable<Boolean> cir) {
        if (ShaderEspRenderContext.isActive()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "shouldCullPaintingBack", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void combatant$shaderEspKeepsPaintingBack(CallbackInfoReturnable<Boolean> cir) {
        if (ShaderEspRenderContext.isActive()) {
            cir.setReturnValue(false);
        }
    }
}
