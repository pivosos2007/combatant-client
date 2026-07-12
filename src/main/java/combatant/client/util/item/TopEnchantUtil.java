/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.config.values.RGBColorValue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Shared helper for highlighting top (max-level) legal enchants used by DropESP/NameTags/BetterChat.
 * Illegal enchantments always take priority (see IllegalItemUtil).
 */
public enum TopEnchantUtil {
    ;

    private static final RGBColorValue TOP_COLOR = new RGBColorValue("top_enchant_color", "#B84DFF");
    private static final ItemIdSetValue IGNORE_TOP = new ItemIdSetValue("top_enchant_ignore", buildDefaultIgnore());

    public static RGBColorValue topColorValue() {
        return TOP_COLOR;
    }

    public static ItemIdSetValue ignoreValue() {
        return IGNORE_TOP;
    }

    public static int topColor() {
        return TOP_COLOR.getArgb();
    }

    /**
     * @return true if the stack has at least one enchant at its vanilla max level,
     * while not being illegal and not in the ignore list.
     */
    public static boolean hasTopEnchant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (IllegalItemUtil.isIllegal(stack)) return false; // illegal has higher priority
        ItemEnchantments enchComp = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        Set<String> ignore = IGNORE_TOP.get();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchComp.entrySet()) {
            Holder<Enchantment> ref = entry.getKey();
            String id = safeId(ref);
            if (ignore.contains(id)) continue;
            int level = entry.getIntValue();
            if (level <= 0) continue;
            Enchantment ench = ref.value();
            if (level == ench.getMaxLevel()) return true;
        }
        return false;
    }

    /**
     * Collects (label, isTop) pairs, skipping ignore-list entries.
     */
    public static void collectTopEnchants(ItemStack stack, BiConsumer<String, Boolean> consumer) {
        if (stack == null || stack.isEmpty() || consumer == null) return;
        Set<String> ignore = IGNORE_TOP.get();
        ItemEnchantments enchComp = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchComp.entrySet()) {
            Holder<Enchantment> ref = entry.getKey();
            String id = safeId(ref);
            if (ignore.contains(id)) continue;
            int level = entry.getIntValue();
            if (level <= 0) continue;
            Enchantment ench = ref.value();
            boolean isTop = level == ench.getMaxLevel();
            String label = Enchantment.getFullname(ref, level).getString();
            consumer.accept(label, isTop);
        }
    }

    private static String safeId(Holder<Enchantment> ref) {
        return ref.unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse("");
    }

    private static Set<String> buildDefaultIgnore() {
        Set<String> result = new LinkedHashSet<>();
        // Known single-level enchants
        result.add("minecraft:binding_curse");
        result.add("minecraft:vanishing_curse");
        result.add("minecraft:mending");
        result.add("minecraft:infinity");
        result.add("minecraft:silk_touch");
        result.add("minecraft:aqua_affinity");
        result.add("minecraft:flame");
        result.add("minecraft:channeling");
        result.add("minecraft:multishot");
        // Explicitly exclude Unbreaking as top (lvl 3)
        result.add("minecraft:unbreaking");
        return result;
    }
}
