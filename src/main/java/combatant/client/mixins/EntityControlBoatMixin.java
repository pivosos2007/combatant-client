/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import combatant.client.events.Events;
import combatant.client.events.impl.BoatRudderControlEvent;
import combatant.client.events.impl.BoatRotationUpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears vanilla rudder state when a local movement module controls the boat,
 * then allows module-owned rotation to be applied after the boat tick.
 * Without an active controller, vanilla steering state remains untouched.
 */
@Mixin(AbstractBoat.class)
public abstract class EntityControlBoatMixin {
    @Shadow private float deltaRotation;
    @Shadow private boolean inputLeft;
    @Shadow private boolean inputRight;

    @Inject(method = "controlBoat", at = @At("HEAD"))
    private void combatant$entityControlClearRudder(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (!Events.BUS.hasListeners(BoatRudderControlEvent.class)) return;
        if (!Events.BUS.post(new BoatRudderControlEvent(player, boat)).isControlled()) return;
        deltaRotation = 0.0F;
        inputLeft = false;
        inputRight = false;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void combatant$entityControlSnapRotation(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (Events.BUS.hasListeners(BoatRotationUpdateEvent.class)) {
            Events.BUS.post(new BoatRotationUpdateEvent(player, boat));
        }
    }
}
