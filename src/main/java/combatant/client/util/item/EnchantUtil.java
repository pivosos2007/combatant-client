/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * Shared helpers for enchantment lookup/validation/formatting.
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

    /** The registry holder for {@code key} in {@code level}'s enchantment registry, or null. */
    public static Holder<Enchantment> holder(Level level, ResourceKey<Enchantment> key) {
        if (level == null || key == null) return null;
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
    }

    /** Level of {@code key} on {@code stack}, 0 when absent or unknown. */
    public static int level(Level level, ResourceKey<Enchantment> key, ItemStack stack) {
        Holder<Enchantment> holder = holder(level, key);
        return holder == null || stack == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }
}
