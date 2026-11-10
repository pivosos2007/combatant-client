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

/** Carries a mutable travel velocity for the local player while gliding. */
@Getter
public final class ElytraTravelEvent extends Event {
    private final LocalPlayer player;
    @Setter
    private Vec3 velocity;

    public ElytraTravelEvent(LocalPlayer player, Vec3 velocity) {
        this.player = player;
        this.velocity = velocity;
    }

}
