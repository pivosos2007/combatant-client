/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.events.Events;
import combatant.client.events.impl.VehicleGroundStateEvent;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies {@link VehicleGroundStateEvent} to the ground flag of outbound
 * vehicle movement packets. A replacement packet is allocated only if the flag changes.
 */
@Mixin(ServerboundMoveVehiclePacket.class)
public abstract class VehicleGroundStateMixin {
    @Inject(method = "fromEntity", at = @At("RETURN"), cancellable = true)
    private static void combatant$vehicleGroundState(Entity vehicle,
                                                      CallbackInfoReturnable<ServerboundMoveVehiclePacket> cir) {
        if (!Events.BUS.hasListeners(VehicleGroundStateEvent.class)) return;
        ServerboundMoveVehiclePacket original = cir.getReturnValue();
        VehicleGroundStateEvent event = Events.BUS.post(
                new VehicleGroundStateEvent(vehicle, original.onGround()));
        if (event.isOnGround() != original.onGround()) {
            cir.setReturnValue(new ServerboundMoveVehiclePacket(original.movingTo(), event.isOnGround()));
        }
    }
}
