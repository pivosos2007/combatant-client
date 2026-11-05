/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.EntityControl;
import combatant.client.features.module.modules.movement.EntityFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoat.class)
public abstract class EntityControlBoatMixin {
    @Shadow private float deltaRotation;
    @Shadow private boolean inputLeft;
    @Shadow private boolean inputRight;

    @Inject(method = "controlBoat", at = @At("HEAD"))
    private void combatant$entityControlClearRudder(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        AbstractBoat boat = (AbstractBoat) (Object) this;
        EntityControl control = Modules.get(EntityControl.class);
        EntityFlight flight = Modules.get(EntityFlight.class);
        boolean controlled = control != null && control.useBoatStrafing(player, boat);
        boolean flightControlled = flight != null && flight.controlsBoat(player, boat);
        if (!controlled && !flightControlled) return;
        deltaRotation = 0.0F;
        inputLeft = false;
        inputRight = false;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void combatant$entityControlSnapRotation(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        AbstractBoat boat = (AbstractBoat) (Object) this;
        EntityControl control = Modules.get(EntityControl.class);
        if (control != null) control.applyBoatRotation(player, boat);
        EntityFlight flight = Modules.get(EntityFlight.class);
        if (flight != null) flight.applyBoatRotation(player, boat);
    }
}
