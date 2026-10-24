/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;

@ModuleInfo(
        id = "autoreplenish",
        displayName = "AutoReplenish",
        aliases = {"AutoRefill", "HotbarRefill", "PotRefill", "SoupRefill", "CartRefill", "AutoCartRefill"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.autoreplenish.description")
public final class AutoReplenish extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> threshold = num("autoReplenishThreshold", "threshold", 16, 1, 63);
    private final NumberValue<Integer> delayTicks = num("autoReplenishDelayTicks", "delay_ticks", 2, 1, 20);
    private final BooleanValue refillEmpty = bool("autoReplenishRefillEmpty", "refill_empty", true);
    private final BooleanValue stockPotions = bool("autoReplenishStockPotions", "stock_potions", false);
    private final BooleanValue stockSoups = visibleWhen(
            bool("autoReplenishStockSoups", "stock_soups", false), stockPotions::get);
    private final NumberValue<Integer> potionTarget = visibleWhen(
            num("autoReplenishPotionTarget", "potion_target", 4, 1, 9), stockPotions::get);
    private final BooleanValue potionOnlyEmpty = visibleWhen(
            bool("autoReplenishPotionOnlyEmpty", "potion_only_empty", true), stockPotions::get);

    private final ItemStack[] lastHotbar = new ItemStack[9];
    private ClientLevel level;
    private int cooldown;
    private int generation;
    private boolean refillPending;

    public AutoReplenish() {
        Arrays.fill(lastHotbar, ItemStack.EMPTY);
    }

    @Override
    public void onEnable() {
        resetState();
        level = mc.level;
    }

    @Override
    public void onDisable() {
        generation++;
        resetState();
        level = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            generation++;
            resetState();
            level = null;
            return;
        }
        if (level != mc.level) {
            generation++;
            resetState();
            level = mc.level;
        }
        if (ClientScreen.current() instanceof AbstractContainerScreen<?>) return;
        if (refillPending) return;
        if (cooldown > 0) {
            cooldown--;
            rememberHotbar(player);
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            ItemStack expected = desiredTemplate(slot, current);
            if (expected == null) {
                if (!current.isEmpty()) lastHotbar[slot] = current.copy();
                continue;
            }
            int minimumCount = current.isEmpty() ? 0 : current.getCount();
            if (findBestSource(player, expected, minimumCount) < 0) continue;
            if (queueRefill(slot)) return;
        }
        if (stockPotions.get()) stockPotions(player);
    }

    // Keeps `potionTarget` splash potions (and optionally soups) in the hotbar; they do not stack, so the stack-size
    // logic above cannot see them running low.
    private void stockPotions(LocalPlayer player) {
        int inHotbar = 0;
        int emptySlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                if (emptySlot < 0) emptySlot = slot;
            } else if (isConsumable(stack)) {
                inHotbar++;
            }
        }
        if (inHotbar >= potionTarget.get()) return;

        int source = -1;
        int bestCount = 0;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !isConsumable(stack) || stack.getCount() <= bestCount) continue;
            source = slot;
            bestCount = stack.getCount();
        }
        if (source < 0) return;

        int destination = emptySlot;
        if (destination < 0) {
            if (potionOnlyEmpty.get()) return;
            int selected = player.getInventory().getSelectedSlot();
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (slot != selected && !stack.isEmpty() && !isConsumable(stack)) {
                    destination = slot;
                    break;
                }
            }
            if (destination < 0) return;
        }
        queueConsumableSwap(source, destination);
    }

    private void queueConsumableSwap(int source, int destination) {
        int ticket = ++generation;
        refillPending = true;
        boolean accepted = InventorySwap.INSTANCE.command(
                InventoryActionKind.INVENTORY_CLICK,
                InventorySwapPolicy.LEGIT,
                () -> {
                    try {
                        if (!isEnabled() || generation != ticket || mc.player == null || mc.level == null) return;
                        if (ClientScreen.current() instanceof AbstractContainerScreen<?>) return;
                        ItemStack stack = mc.player.getInventory().getItem(source);
                        if (stack.isEmpty() || !isConsumable(stack)) return;
                        int sourceScreen = InventorySwap.mapInventoryToScreenSlot(source);
                        if (sourceScreen < 0 || !InventorySwap.INSTANCE.clickSwap(sourceScreen, destination)) return;
                        cooldown = delayTicks.get();
                    } finally {
                        if (generation == ticket) refillPending = false;
                    }
                });
        if (!accepted) refillPending = false;
    }

    private boolean isConsumable(ItemStack stack) {
        if (stack.is(Items.SPLASH_POTION)) return true;
        return stockSoups.get() && (stack.is(Items.MUSHROOM_STEW) || stack.is(Items.RABBIT_STEW)
                || stack.is(Items.BEETROOT_SOUP) || stack.is(Items.SUSPICIOUS_STEW));
    }

    private ItemStack desiredTemplate(int hotbarSlot, ItemStack current) {
        if (!current.isEmpty()) {
            lastHotbar[hotbarSlot] = current.copy();
            if (!current.isStackable() || current.getCount() > threshold.get() || current.getCount() >= current.getMaxStackSize()) {
                return null;
            }
            return current;
        }
        if (!refillEmpty.get()) return null;
        ItemStack previous = lastHotbar[hotbarSlot];
        return previous == null || previous.isEmpty() ? null : previous;
    }

    private boolean queueRefill(int hotbarSlot) {
        int ticket = ++generation;
        refillPending = true;
        boolean accepted = InventorySwap.INSTANCE.command(
                InventoryActionKind.INVENTORY_CLICK,
                InventorySwapPolicy.LEGIT,
                () -> {
                    try {
                        if (!isEnabled() || generation != ticket || mc.player == null || mc.level == null) return;
                        if (ClientScreen.current() instanceof AbstractContainerScreen<?>) return;
                        performRefill(mc.player, hotbarSlot);
                    } finally {
                        if (generation == ticket) refillPending = false;
                    }
                }
        );
        if (!accepted) {
            refillPending = false;
            return false;
        }
        return true;
    }

    private void performRefill(LocalPlayer player, int hotbarSlot) {
        ItemStack current = player.getInventory().getItem(hotbarSlot);
        ItemStack target = desiredTemplate(hotbarSlot, current);
        if (target == null) return;

        int source = findBestSource(player, target, current.isEmpty() ? 0 : current.getCount());
        if (source < 0) return;

        int sourceScreen = InventorySwap.mapInventoryToScreenSlot(source);
        if (sourceScreen < 0 || !InventorySwap.INSTANCE.clickSwap(sourceScreen, hotbarSlot)) return;
        cooldown = delayTicks.get();
    }

    private int findBestSource(LocalPlayer player, ItemStack target, int minimumCount) {
        int bestSlot = -1;
        int bestCount = minimumCount;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, target)) continue;
            if (stack.getCount() <= bestCount) continue;
            bestSlot = slot;
            bestCount = stack.getCount();
        }
        return bestSlot;
    }

    private void rememberHotbar(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) lastHotbar[slot] = stack.copy();
        }
    }

    private void resetState() {
        Arrays.fill(lastHotbar, ItemStack.EMPTY);
        cooldown = 0;
        refillPending = false;
    }
}
