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

/** Item-use slowdown multiplier before local-player input is applied. */
@Getter
public final class ItemUseInputMultiplierEvent extends Event {
    private final LocalPlayer player;
    @Setter
    private float multiplier;

    public ItemUseInputMultiplierEvent(LocalPlayer player, float multiplier) {
        this.player = player;
        this.multiplier = multiplier;
    }
}
