/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.compat.litematica;

import net.minecraft.world.item.ItemStack;

/**
 * One line of Litematica's material list.
 *
 * @param stack     the item (count 1)
 * @param total     how many the schematic needs
 * @param missing   how many are still missing from the world
 * @param available how many the player carries
 */
public record MaterialCount(ItemStack stack, int total, int missing, int available) {
}
