/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.draggable.impl.Itemizer;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.function.Predicate;

//todo Description
@ModuleInfo(
        id = "autototem",
        displayName = "AutoTotem",
        category = ModuleCategory.PLAYER
)
public class AutoTotem extends Module {
    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> healthThreshold =
            numCommon(
                    "autototem_threshold",
                    "health_threshold",
                    CommonSettingSchemas.PLAYER_HEALTH_THRESHOLD,
                    10.0f,
                    1.0f,
                    40.0f
            );

    private final NumberValue<Float> elytraHealth =
            numCommon(
                    "autototem_elytra_health",
                    "elytra_health",
                    CommonSettingSchemas.PLAYER_ELYTRA_HEALTH,
                    8.5f,
                    1.0f,
                    40.0f
            );

    private final NumberValue<Float> crystalDistance =
            numCommon(
                    "autototem_crystal_distance",
                    "crystal_distance",
                    CommonSettingSchemas.COMBAT_CRYSTAL_DISTANCE,
                    4.0f,
                    1.0f,
                    6.0f
            );

    private final BooleanValue fallCheck =
            boolCommon(
                    "autototem_fall_check",
                    "fall_check",
                    CommonSettingSchemas.PLAYER_FALL_CHECK,
                    true
            );

    private final BooleanValue saveTaliks =
            boolCommon(
                    "autototem_save_taliks",
                    "save_taliks",
                    CommonSettingSchemas.ITEMS_SAVE_UNENCHANTED,
                    true
            );

    private final BooleanValue returnItem =
            boolCommon(
                    "autototem_return_item",
                    "return_item",
                    CommonSettingSchemas.INVENTORY_RESTORE_ITEM,
                    true
            );

    private ItemStack previousOffhand = ItemStack.EMPTY;
    private int previousOffhandSlot = -1;
    private boolean usingTotem = false;
    private boolean offhandSwapPending = false;

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        if (mc.player == null || mc.level == null || mc.isPaused()) {
            offhandSwapPending = false;
            return;
        }

        if (offhandSwapPending) {
            return;
        }

        LocalPlayer player = mc.player;

        float health = player.getHealth() + player.getAbsorptionAmount();

        boolean shouldHoldTotem = shouldHoldTotem(player, health);
        if (shouldHoldTotem) {
            equipTotem(player);
            return;
        }

        if (usingTotem && returnItem.get()) {
            restorePrevious(player);
        }
    }

    @Override
    public void onDisable() {
        offhandSwapPending = false;
        clearRestoreState();
    }

    private boolean shouldHoldTotem(LocalPlayer player, float health) {
        if (player.isFallFlying() && health <= elytraHealth.get()) {
            return true;
        }

        if (health <= healthThreshold.get()) {
            return true;
        }

        if (fallCheck.get() && player.fallDistance > 10.0f) {
            return true;
        }

        return getClosestCrystalDistance(player) <= crystalDistance.get();
    }

    public boolean shouldHoldTotemNow(LocalPlayer player) {
        if (!isEnabled() || player == null) return false;
        return shouldHoldTotem(player, player.getHealth() + player.getAbsorptionAmount());
    }

    public boolean canProvideTotemNow(LocalPlayer player) {
        if (!isEnabled()) return false;
        if (player == null || mc.gameMode == null) return false;
        if (isTotemInOffhand(player)) return true;
        if (offhandSwapPending) return false;
        return findTotemSlot(player) != -1;
    }

    public boolean ensureTotemForDanger(LocalPlayer player) {
        if (!canProvideTotemNow(player)) return false;
        if (isTotemInOffhand(player)) return true;

        int slot = findTotemSlot(player);
        if (slot == -1) return false;

        rememberPreviousOffhand(player, slot);
        return swapTotemToOffhand(player, slot);
    }


    public boolean isTotemSwapPending() {
        return offhandSwapPending;
    }

    private void equipTotem(LocalPlayer player) {
        if (player == null || mc.gameMode == null) return;
        if (isTotemInOffhand(player)) return;
        int slot = findTotemSlot(player);
        if (slot == -1) return;

        rememberPreviousOffhand(player, slot);
        swapTotemToOffhand(player, slot);
    }

    private boolean swapTotemToOffhand(LocalPlayer player, int slot) {
        if (player == null || mc.gameMode == null || offhandSwapPending) return false;

        offhandSwapPending = true;
        ItemStack displayStack = player.getInventory().getItem(slot).copy();
        boolean accepted = InventorySwap.INSTANCE.swapInventoryToOffhand(slot, () -> {
            usingTotem = true;
            offhandSwapPending = false;
            Itemizer.showAutoTotem(displayStack);
        });
        if (!accepted) {
            offhandSwapPending = false;
        }
        return accepted;
    }

    private void restorePrevious(LocalPlayer player) {
        if (player == null || mc.gameMode == null) return;

        if (previousOffhand.isEmpty()) {
            clearRestoreState();
            return;
        }

        if (!player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            clearRestoreState();
            return;
        }

        int restoreSlot = resolveRestoreSlot(player);
        if (restoreSlot == -1) {
            clearRestoreState();
            return;
        }

        requestOffhandSwap(restoreSlot, this::clearRestoreState);
    }

    private void rememberPreviousOffhand(LocalPlayer player, int totemSlot) {
        if (player == null) return;
        if (usingTotem) return;

        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            previousOffhand = ItemStack.EMPTY;
            previousOffhandSlot = -1;
            return;
        }

        previousOffhand = offhand.copy();
        previousOffhandSlot = totemSlot;
    }

    private boolean requestOffhandSwap(int slot, Runnable afterSwap) {
        if (offhandSwapPending) return false;

        offhandSwapPending = true;
        boolean accepted = InventorySwap.INSTANCE.swapInventoryToOffhand(slot, () -> {
            if (afterSwap != null) {
                afterSwap.run();
            }
            offhandSwapPending = false;
        });
        if (!accepted) {
            offhandSwapPending = false;
        }
        return accepted;
    }

    private int findTotemSlot(LocalPlayer player) {
        if (saveTaliks.get()) {
            int nonEnchanted = find(player, stack ->
                    stack.is(Items.TOTEM_OF_UNDYING) && !stack.isEnchanted()
            );

            if (nonEnchanted != -1) {
                return nonEnchanted;
            }
        }

        return find(player, Items.TOTEM_OF_UNDYING);
    }

    private int find(LocalPlayer player, Item item) {
        return find(player, stack -> stack.is(item));
    }

    private int find(LocalPlayer player, Predicate<ItemStack> predicate) {
        if (player == null || predicate == null) return -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return i;
            }
        }

        return -1;
    }

    private int findItem(LocalPlayer player, ItemStack target) {
        if (player == null || target == null || target.isEmpty()) return -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target)) {
                return i;
            }
        }

        return -1;
    }

    private int resolveRestoreSlot(LocalPlayer player) {
        if (previousOffhandSlot >= 0 && previousOffhandSlot < 36) {
            ItemStack stack = player.getInventory().getItem(previousOffhandSlot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, previousOffhand)) {
                return previousOffhandSlot;
            }
        }

        return findItem(player, previousOffhand);
    }

    private double getClosestCrystalDistance(LocalPlayer player) {
        if (player == null || mc.level == null) {
            return Double.MAX_VALUE;
        }

        double minDist = Double.MAX_VALUE;
        double range = crystalDistance.get();

        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(range),
                crystal -> crystal != null && !crystal.isRemoved()
        )) {
            double dist = player.position().distanceTo(crystal.position());
            if (dist < minDist) {
                minDist = dist;
            }
        }

        return minDist;
    }

    private boolean isTotemInOffhand(LocalPlayer player) {
        return player != null && player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private void clearRestoreState() {
        previousOffhand = ItemStack.EMPTY;
        previousOffhandSlot = -1;
        usingTotem = false;
    }
}
