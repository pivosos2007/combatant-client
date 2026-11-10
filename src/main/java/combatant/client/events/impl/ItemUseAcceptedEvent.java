/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import net.minecraft.world.item.Item;
import lombok.RequiredArgsConstructor;

/** Local item use after vanilla reports a consumed interaction. */
@Getter
@RequiredArgsConstructor
public final class ItemUseAcceptedEvent extends Event {
    private final Item item;
}
