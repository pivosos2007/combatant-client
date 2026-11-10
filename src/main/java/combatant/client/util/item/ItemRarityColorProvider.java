/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import net.minecraft.world.item.ItemStack;

/**
 * Supplies a display color for an item
 * based on its rarity or other properties.
 */
public interface ItemRarityColorProvider {

    /**
     * Returns RGBA channels normalized to [0, 1].
     *
     * @param stack item whose color is requested
     * @return four channels in R, G, B, A order
     */
    float[] getRarityColor(ItemStack stack);
}
