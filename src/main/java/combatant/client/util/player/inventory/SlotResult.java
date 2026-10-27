/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

import net.minecraft.world.item.ItemStack;

/** A found item; {@code slot} is always the player-inventory index (0-8 hotbar, 9-35 main, 40 offhand). */
public record SlotResult(int slot, boolean found, ItemStack stack) {
    public static SlotResult empty() {
        return new SlotResult(-1, false, ItemStack.EMPTY);
    }

    public boolean isHotbar() {
        return found && slot >= 0 && slot <= 8;
    }

    public boolean isInventory() {
        return found && slot > 8;
    }
}
