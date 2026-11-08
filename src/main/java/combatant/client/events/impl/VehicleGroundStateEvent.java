/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.Entity;

@Getter
public final class VehicleGroundStateEvent extends Event {
    private final Entity vehicle;
    @Setter
    private boolean onGround;

    public VehicleGroundStateEvent(Entity vehicle, boolean onGround) {
        this.vehicle = vehicle;
        this.onGround = onGround;
    }
}
