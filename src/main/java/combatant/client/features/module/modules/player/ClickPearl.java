/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.player.ResetAttackCooldown;
import combatant.client.util.player.inventory.InventorySwap;

//todo Description
@ModuleInfo(
        id = "clickpearl",
        displayName = "ClickPearl",
        category = ModuleCategory.PLAYER
)
public class ClickPearl extends Module {
    private static final String ACTION_CLICKPEARL = "clickpearl";

    private final Minecraft client = Minecraft.getInstance();
    private ItemStack previousHeld = ItemStack.EMPTY;

    public ClickPearl() {
        // Default binds:
        // - Toggle module with LEFT_CTRL+Z
        // - Action key is Z (handled by module's own logic)
        setDefaultBind("LEFT_CTRL+Z");
        action(ACTION_CLICKPEARL, "Z");
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (client.player == null || client.gameMode == null) return;

        if (isActionPressedOnce(ACTION_CLICKPEARL)) {
            tryThrowPearl();
        }
    }

    // ===========================
    //      ТВОЯ ЛОГИКА — НЕ ТРОНУТА
    // ===========================
    private void tryThrowPearl() {
        LocalPlayer player = client.player;
        MultiPlayerGameMode manager = client.gameMode;
        if (player == null || manager == null) return;
        if (player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) return;

        PlayerInventoryAccessor inv = (PlayerInventoryAccessor) player.getInventory();
        int originalSlot = inv.combatant$getSelectedSlot();

        ItemStack off = player.getOffhandItem();
        if (isPearl(off)) {
            manager.useItem(player, InteractionHand.OFF_HAND);
            player.swing(InteractionHand.OFF_HAND);
            return;
        }

        int hotbarPearl = findPearlInHotbar(player);
        if (hotbarPearl != -1) {
            inv.combatant$setSelectedSlot(hotbarPearl);
            manager.useItem(player, InteractionHand.MAIN_HAND);
            player.swing(InteractionHand.MAIN_HAND);
            inv.combatant$setSelectedSlot(originalSlot);
            return;
        }

        int invPearl = findPearlInInventory(player);
        if (invPearl == -1) return;

        int emptyHotbar = findFirstEmptyHotbarSlot(player);
        var handler = player.inventoryMenu;
        if (handler == null) return;
        int syncId = handler.containerId;

        client.execute(() -> {
            if (emptyHotbar != -1) {
                int pearlScreen = InventorySwap.mapInventoryToScreenSlot(invPearl);
                int targetScreen = InventorySwap.mapHotbarToScreenSlot(emptyHotbar);

                InventorySwap.INSTANCE.swapScreenSlots(pearlScreen, targetScreen);

                inv.combatant$setSelectedSlot(emptyHotbar);
                manager.useItem(player, InteractionHand.MAIN_HAND);
                player.swing(InteractionHand.MAIN_HAND);
                inv.combatant$setSelectedSlot(originalSlot);
            } else {
                int targetScreen = InventorySwap.mapHotbarToScreenSlot(originalSlot);
                int pearlScreen = InventorySwap.mapInventoryToScreenSlot(invPearl);

                ItemStack currentHeld = player.getMainHandItem();
                previousHeld = currentHeld.isEmpty() ? ItemStack.EMPTY : currentHeld.copy();

                InventorySwap.INSTANCE.swapScreenSlots(pearlScreen, targetScreen);

                manager.useItem(player, InteractionHand.MAIN_HAND);
                player.swing(InteractionHand.MAIN_HAND);

                if (!previousHeld.isEmpty()) {
                    int restoreSlot = findItemInInventory(player, previousHeld);
                    if (restoreSlot != -1) {
                        int restoreScreen = InventorySwap.mapInventoryToScreenSlot(restoreSlot);
                        InventorySwap.INSTANCE.swapScreenSlots(restoreScreen, targetScreen);
                        ResetAttackCooldown.resetAttackCooldown(player);
                    }
                }

                inv.combatant$setSelectedSlot(originalSlot);
            }
        });
    }

    private boolean isPearl(ItemStack stack) {
        return stack != null && stack.is(Items.ENDER_PEARL);
    }

    private int findPearlInHotbar(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (isPearl(player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private int findPearlInInventory(LocalPlayer player) {
        for (int i = 0; i < 36; i++) {
            if (isPearl(player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private int findFirstEmptyHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private int findItemInInventory(LocalPlayer player, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == itemStack.getItem()) return i;
        }
        return -1;
    }
}
