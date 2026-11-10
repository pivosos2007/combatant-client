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

/** Carries the local player's mount-jump charge for modification by event handlers. */
@Getter
public final class MountJumpChargeEvent extends Event {
    private final LocalPlayer player;
    @Setter
    private float charge;

    public MountJumpChargeEvent(LocalPlayer player, float charge) {
        this.player = player;
        this.charge = charge;
    }

}
