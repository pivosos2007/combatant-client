/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public enum OmniItemUtils {
    ;

    public static boolean isProjectileWeaponInHand(LocalPlayer player) {
        if (player == null) return false;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest != null && chest.getItem() == Items.ELYTRA) {
            return true;
        }

        for (ItemStack stack : new ItemStack[]{
                player.getMainHandItem(),
                player.getOffhandItem()
        }) {
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();

            if (item instanceof BowItem) return true;

            if (item instanceof CrossbowItem) return true;

            if (item == Items.TRIDENT) {
                ItemEnchantments ench =
                        stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                boolean hasRiptide = ench.keySet().stream().anyMatch(entry ->
                        entry.unwrapKey().isPresent() &&
                                //riptide check
                                entry.unwrapKey().get().equals(Enchantments.RIPTIDE)
                );
                if (!hasRiptide) return true;
            }

            // ==== Throwable ====
            if (item == Items.ENDER_PEARL //||
                //item == Items.SNOWBALL ||
                /*item == Items.EGG*/) return true;
        }

        return false;
    }
}
