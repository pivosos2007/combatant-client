/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

/** Chooses which player-inventory slot (0..35) a totem should be taken from. */
public final class TotemSlotPicker {
    private TotemSlotPicker() {
    }

    /**
     * @param totem          per slot, whether it holds a totem (length 36, hotbar first)
     * @param enchanted      per slot, whether that totem carries enchantment glint/enchantments
     * @param saveEnchanted  use an enchanted totem only when no plain one exists
     * @param preferHotbar   scan the hotbar before the main inventory
     * @param excludedSlot   slot never chosen (-1 for none)
     * @return the slot, or -1
     */
    public static int pick(boolean[] totem, boolean[] enchanted, boolean saveEnchanted, boolean preferHotbar,
                           int excludedSlot) {
        int fallback = -1;
        for (int i = 0; i < 36; i++) {
            int slot = preferHotbar ? i : (i + 9) % 36;
            if (slot == excludedSlot || slot >= totem.length || !totem[slot]) continue;
            if (!saveEnchanted || !enchanted[slot]) return slot;
            if (fallback < 0) fallback = slot;
        }
        return fallback;
    }
}
