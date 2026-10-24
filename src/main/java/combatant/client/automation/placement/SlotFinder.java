/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.placement;

import net.minecraft.core.BlockPos;

/** Picks what to place with. {@code targetPos} is null when only the item matters. */
@FunctionalInterface
public interface SlotFinder {
    PlacementSlot find(BlockPos targetPos);
}
