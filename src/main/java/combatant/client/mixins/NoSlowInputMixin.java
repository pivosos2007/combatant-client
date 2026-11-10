/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import combatant.client.events.Events;
import combatant.client.events.impl.ItemUseInputMultiplierEvent;
import combatant.client.events.impl.ItemUseInputVectorEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Publishes the two item-use movement input values before returning them to Minecraft. */
@Mixin(LocalPlayer.class)
public class NoSlowInputMixin {

    @ModifyExpressionValue(
            method = "modifyInput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;itemUseSpeedMultiplier()F")
    )
    private float combatant$noSlowNeutralize(float original) {
        if (!Events.BUS.hasListeners(ItemUseInputMultiplierEvent.class)) return original;
        LocalPlayer self = (LocalPlayer) (Object) this;
        return Events.BUS.post(new ItemUseInputMultiplierEvent(self, original)).getMultiplier();
    }

    @Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
    private void combatant$noSlowApply(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
        if (!Events.BUS.hasListeners(ItemUseInputVectorEvent.class)) return;
        LocalPlayer self = (LocalPlayer) (Object) this;
        Vec2 original = cir.getReturnValue();
        Vec2 modified = Events.BUS.post(new ItemUseInputVectorEvent(self, original)).getInput();
        if (modified != original) cir.setReturnValue(modified);
    }
}
