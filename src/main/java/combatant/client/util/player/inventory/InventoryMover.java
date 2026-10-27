/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

/**
 * The one "move an item to the offhand / a hotbar slot" path every module uses. Every click goes through the
 * serialized {@link InventorySwap#command} queue, so Grim's stationary-player and click-ordering rules hold.
 * {@code stillValid} is re-checked when the click actually runs (it may have been queued) against the source stack;
 * {@code after} runs only if the click was sent.
 */
public final class InventoryMover {
    private static final int OFFHAND_BUTTON = 40;

    private InventoryMover() {
    }

    public static boolean isTotem(ItemStack stack) {
        return stack != null && stack.is(Items.TOTEM_OF_UNDYING);
    }

    /** Swaps player-inventory slot {@code inventorySlot} (0-35) with the offhand. */
    public static boolean toOffhand(Object owner, int inventorySlot, Predicate<ItemStack> stillValid, Runnable after) {
        if (inventorySlot < 0 || inventorySlot >= 36) return false;
        if (InventorySlotLocks.blocks(InventorySlotLocks.OFFHAND, owner)) return false;
        int screenSlot = InventorySwap.mapInventoryToScreenSlot(inventorySlot);
        if (screenSlot < 0) return false;
        return InventorySwap.INSTANCE.command(owner, InventoryActionKind.INVENTORY_CLICK, () -> {
            if (!sourceValid(inventorySlot, stillValid)) return;
            if (InventorySwap.INSTANCE.clickSwap(screenSlot, OFFHAND_BUTTON) && after != null) after.run();
        });
    }

    /**
     * Moves player-inventory slot {@code inventorySlot} into hotbar slot {@code hotbarSlot} without changing the
     * selected slot. With {@code merge} and an equal stack already there, the stacks merge (pickup clicks)
     * instead of trading places.
     */
    public static boolean toHotbarSlot(Object owner, int inventorySlot, int hotbarSlot, boolean merge,
                                       Predicate<ItemStack> stillValid, Runnable after) {
        if (hotbarSlot < 0 || hotbarSlot > 8 || inventorySlot < 0 || inventorySlot >= 36
                || inventorySlot == hotbarSlot) return false;
        int source = InventorySwap.mapInventoryToScreenSlot(inventorySlot);
        int target = InventorySwap.mapHotbarToScreenSlot(hotbarSlot);
        if (source < 0 || target < 0) return false;
        return InventorySwap.INSTANCE.command(owner, InventoryActionKind.INVENTORY_CLICK, () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || !sourceValid(inventorySlot, stillValid)) return;
            ItemStack source1 = player.getInventory().getItem(inventorySlot);
            ItemStack current = player.getInventory().getItem(hotbarSlot);
            boolean merging = merge && !current.isEmpty() && ItemStack.isSameItemSameComponents(source1, current);
            boolean sent = merging ? InventorySwap.INSTANCE.pickupSwap(source, target)
                    : InventorySwap.INSTANCE.clickSwap(source, hotbarSlot);
            if (sent && after != null) after.run();
        });
    }

    /**
     * Swaps hotbar slot {@code hotbarSlot} with main-inventory slot {@code inventorySlot} (9-35) whatever either
     * holds: the way back after {@link #toHotbarSlot} borrowed a hotbar slot (the borrowed item may have been used
     * up meanwhile). Two empty slots count as already swapped: {@code after} runs without a click.
     */
    public static boolean exchangeWithHotbar(Object owner, int hotbarSlot, int inventorySlot, Runnable after) {
        if (hotbarSlot < 0 || hotbarSlot > 8 || inventorySlot < 9 || inventorySlot >= 36) return false;
        int target = InventorySwap.mapInventoryToScreenSlot(inventorySlot);
        if (target < 0) return false;
        return InventorySwap.INSTANCE.command(owner, InventoryActionKind.INVENTORY_CLICK, () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            boolean nothingToMove = player.getInventory().getItem(hotbarSlot).isEmpty()
                    && player.getInventory().getItem(inventorySlot).isEmpty();
            if ((nothingToMove || InventorySwap.INSTANCE.clickSwap(target, hotbarSlot)) && after != null) after.run();
        });
    }

    /**
     * Swaps an arbitrary menu slot (e.g. the one under the cursor) with the offhand ({@code button} 40) or a hotbar
     * slot ({@code button} 0-8). The menu id is re-checked when the click runs.
     */
    public static boolean menuSlotToButton(Object owner, int menuId, int menuSlot, int button,
                                           Predicate<ItemStack> stillValid, Runnable after) {
        if (button != OFFHAND_BUTTON && (button < 0 || button > 8)) return false;
        if (button == OFFHAND_BUTTON && InventorySlotLocks.blocks(InventorySlotLocks.OFFHAND, owner)) return false;
        return InventorySwap.INSTANCE.command(owner, InventoryActionKind.INVENTORY_CLICK, () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            AbstractContainerMenu menu = player.containerMenu;
            if (menu.containerId != menuId || menuSlot < 0 || menuSlot >= menu.slots.size()) return;
            if (stillValid != null && !stillValid.test(menu.getSlot(menuSlot).getItem())) return;
            if (InventorySwap.INSTANCE.clickSwap(menuSlot, button) && after != null) after.run();
        });
    }

    /**
     * Picks the player-inventory slot (0-35) a totem should be taken from, or -1.
     *
     * @param saveEnchanted use an enchanted totem only when no plain one exists
     * @param preferHotbar  scan the hotbar before the main inventory
     * @param excludedSlot  slot never chosen (-1 for none)
     */
    public static int pickTotemSlot(LocalPlayer player, boolean saveEnchanted, boolean preferHotbar, int excludedSlot) {
        boolean[] totem = new boolean[36];
        boolean[] enchanted = new boolean[36];
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isTotem(stack)) continue;
            totem[slot] = true;
            enchanted[slot] = stack.isEnchanted() || stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
        return TotemSlotPicker.pick(totem, enchanted, saveEnchanted, preferHotbar, excludedSlot);
    }

    private static boolean sourceValid(int inventorySlot, Predicate<ItemStack> stillValid) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        return !stack.isEmpty() && (stillValid == null || stillValid.test(stack));
    }
}
