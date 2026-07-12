/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Shared helpers for enchantment validation/formatting.
 */
public enum EnchantUtil {
    ;

    /**
     * Checks if the provided enchantment level exceeds its max level.
     */
    public static boolean isInvalidLevel(Holder<Enchantment> enchant, int level) {
        if (enchant == null || level <= 0) return false;
        Enchantment value = enchant.value();
        int max = value.getMaxLevel();
        return level > max;
    }
}
