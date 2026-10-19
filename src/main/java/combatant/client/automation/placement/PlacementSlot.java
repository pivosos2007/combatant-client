/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.placement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Where the item to place sits: a hotbar slot for the main hand, or {@code -1} for the offhand.
 */
public record PlacementSlot(int hotbarSlot, InteractionHand hand, ItemStack stack) {

    /** True when the item has to be selected in the hotbar before clicking. */
    public boolean needsHotbar() {
        return hand == InteractionHand.MAIN_HAND && hotbarSlot >= 0;
    }

    /** Offhand first, then the hotbar left to right; null when nothing matches. */
    public static PlacementSlot find(LocalPlayer player, Predicate<ItemStack> accepts) {
        if (player == null) return null;
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && accepts.test(offhand)) {
            return new PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && accepts.test(stack)) {
                return new PlacementSlot(slot, InteractionHand.MAIN_HAND, stack);
            }
        }
        return null;
    }

    /** True while the slot still holds the same kind of item. */
    public boolean stillHolds(LocalPlayer player) {
        ItemStack current = hand == InteractionHand.OFF_HAND
                ? player.getOffhandItem()
                : player.getInventory().getItem(hotbarSlot);
        return !current.isEmpty() && ItemStack.isSameItem(current, stack);
    }
}
