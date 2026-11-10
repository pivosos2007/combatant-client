/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import lombok.RequiredArgsConstructor;

/** Main-thread totem activation after the pop counter is updated. */
@Getter
@RequiredArgsConstructor
public final class TotemActivationEvent extends Event {
    private final Player player;
    private final boolean local;
}
