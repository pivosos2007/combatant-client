/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import combatant.client.config.values.RGBColorValue;

import java.util.function.BiConsumer;

/**
 * Общая утилита проверки нелегальных предметов и цвета их подсветки.
 */
public enum IllegalItemUtil {
    ;

    private static final RGBColorValue ILLEGAL_COLOR = new RGBColorValue("illegal_item_color", "#FF5555");

    public static RGBColorValue illegalColorValue() {
        return ILLEGAL_COLOR;
    }

    public static int illegalColor() {
        return ILLEGAL_COLOR.getArgb();
    }

    /**
     * true, если стек содержит энчанты выше разрешенного уровня.
     */
    public static boolean isIllegal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemEnchantments enchComp = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchComp.entrySet()) {
            Holder<Enchantment> ref = entry.getKey();
            int level = entry.getIntValue();
            if (level <= 0) continue;
            if (EnchantUtil.isInvalidLevel(ref, level)) return true;
        }
        return false;
    }

    /**
     * Обход энчантов с признаком нелегальности.
     */
    public static void collectEnchants(ItemStack stack, BiConsumer<String, Boolean> consumer) {
        if (stack == null || stack.isEmpty() || consumer == null) return;
        ItemEnchantments enchComp = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchComp.entrySet()) {
            Holder<Enchantment> ref = entry.getKey();
            int level = entry.getIntValue();
            if (level <= 0) continue;
            boolean overMax = EnchantUtil.isInvalidLevel(ref, level);
            String label = Enchantment.getFullname(ref, level).getString();
            consumer.accept(label, overMax);
        }
    }
}
