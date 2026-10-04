/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.NoSlow;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Connects {@link NoSlow} to the vanilla item-use slowdown. The module's input methods had no caller, so
 * every mode left eating, drawing a bow or blocking at 20% speed.
 *
 * <p>The vanilla multiplier is neutralised first, then the mode decides what the movement input looks like
 * (unchanged, scaled per axis, or slowed on alternate ticks).</p>
 */
@Mixin(LocalPlayer.class)
public class NoSlowInputMixin {

    @ModifyExpressionValue(
            method = "modifyInput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;itemUseSpeedMultiplier()F")
    )
    private float combatant$noSlowNeutralize(float original) {
        NoSlow noSlow = Modules.get(NoSlow.class);
        if (noSlow != null && noSlow.shouldModifyInput((LocalPlayer) (Object) this)) return 1.0f;
        return original;
    }

    @Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
    private void combatant$noSlowApply(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
        NoSlow noSlow = Modules.get(NoSlow.class);
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (noSlow == null || !noSlow.shouldModifyInput(self)) return;
        cir.setReturnValue(noSlow.getModifiedInput(cir.getReturnValue(), self));
    }
}
