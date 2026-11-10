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
import net.minecraft.world.phys.Vec2;

/** Final input vector returned by local-player item-use input processing. */
@Getter
public final class ItemUseInputVectorEvent extends Event {
    private final LocalPlayer player;
    @Setter
    private Vec2 input;

    public ItemUseInputVectorEvent(LocalPlayer player, Vec2 input) {
        this.player = player;
        this.input = input;
    }
}
