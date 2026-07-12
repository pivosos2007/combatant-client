/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import net.minecraft.world.item.ItemStack;

/**
 * Интерфейс для классов, которые могут извлекать цвет предмета
 * на основе его редкости или других свойств.
 */
public interface ItemRarityColorProvider {

    /**
     * Возвращает RGBA цвет (0..1) для данного предмета.
     *
     * @param stack предмет
     * @return массив float[4] {r,g,b,a}
     */
    float[] getRarityColor(ItemStack stack);
}
