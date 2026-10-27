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
import combatant.client.util.player.inventory.InventoryMover;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.PacketWindow;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.Objects;

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
        resetState();
        level = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            resetState();
            level = null;
            return;
        }
        if (level != mc.level) {
            resetState();
            level = mc.level;
        }
        if (ClientScreen.current() instanceof AbstractContainerScreen<?>) return;
        if (InventorySwap.INSTANCE.hasQueuedInventoryAction(this, InventoryActionKind.INVENTORY_CLICK)) return;
        if (cooldown > 0) {
            cooldown--;
            rememberHotbar(player);
            return;
        }
        // A refill click or hotbar swap would cancel eating / a bow draw, and a click needs a stationary player.
        if (player.isUsingItem() || !PacketWindow.INSTANCE.stationaryForClick()) {
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
            int source = findBestSource(player, expected, minimumCount);
            if (source >= 0 && refill(source, slot, expected)) return;
        }
        if (stockPotions.get()) stockPotions(player);
    }

    private boolean refill(int source, int hotbarSlot, ItemStack template) {
        return InventoryMover.toHotbarSlot(this, source, hotbarSlot, true,
                stack -> ItemStack.isSameItemSameComponents(stack, template),
                () -> cooldown = delayTicks.get());
    }

    // Keeps `potionTarget` splash potions (and optionally soups) of one kind in the hotbar; they do not stack, so
    // the stack-size logic above cannot see them running low.
    private void stockPotions(LocalPlayer player) {
        ItemStack kind = referenceConsumable(player);
        if (kind.isEmpty()) return;

        int inHotbar = 0;
        int emptySlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                if (emptySlot < 0) emptySlot = slot;
            } else if (sameConsumable(stack, kind)) {
                inHotbar++;
            }
        }
        if (inHotbar >= potionTarget.get()) return;

        int source = -1;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && sameConsumable(stack, kind)) {
                source = slot;
                break;
            }
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
        InventoryMover.toHotbarSlot(this, source, destination, false,
                stack -> sameConsumable(stack, kind), () -> cooldown = delayTicks.get());
    }

    /** The kind to stock: what the hotbar already holds, else what the inventory holds. */
    private ItemStack referenceConsumable(LocalPlayer player) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && isConsumable(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private boolean isConsumable(ItemStack stack) {
        if (stack.is(Items.SPLASH_POTION)) return true;
        return stockSoups.get() && (stack.is(Items.MUSHROOM_STEW) || stack.is(Items.RABBIT_STEW)
                || stack.is(Items.BEETROOT_SOUP) || stack.is(Items.SUSPICIOUS_STEW));
    }

    /** Same item and, for potions, the same effects: a splash of Healing is not a splash of Water. */
    private boolean sameConsumable(ItemStack stack, ItemStack kind) {
        if (!isConsumable(stack) || !stack.is(kind.getItem())) return false;
        return !stack.is(Items.SPLASH_POTION)
                || Objects.equals(stack.get(DataComponents.POTION_CONTENTS), kind.get(DataComponents.POTION_CONTENTS));
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
        InventorySwap.INSTANCE.cancelQueuedInventoryActions(this);
        Arrays.fill(lastHotbar, ItemStack.EMPTY);
        cooldown = 0;
    }
}
