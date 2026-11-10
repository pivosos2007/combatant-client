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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Carries incoming and mutable resulting velocity for local-player water travel. */
@Getter
public final class PlayerWaterTravelEvent extends Event {
    private final LocalPlayer player;
    private final Vec3 incomingVelocity;
    @Setter
    private Vec3 velocity;

    public PlayerWaterTravelEvent(LocalPlayer player, Vec3 incomingVelocity, Vec3 velocity) {
        this.player = player;
        this.incomingVelocity = incomingVelocity;
        this.velocity = velocity;
    }

}
