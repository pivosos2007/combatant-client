/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/** Lets a movement controller claim the vanilla boat rudder state for this tick. */
@Getter
public final class BoatRudderControlEvent extends Event {
    private final LocalPlayer player;
    private final AbstractBoat boat;
    private boolean controlled;

    public BoatRudderControlEvent(LocalPlayer player, AbstractBoat boat) {
        this.player = player;
        this.boat = boat;
    }

    public void claim() {
        controlled = true;
    }
}
