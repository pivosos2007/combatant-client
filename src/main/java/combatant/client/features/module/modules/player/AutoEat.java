/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.draggable.impl.Itemizer;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.item.FoodUtil;
import combatant.client.util.player.inventory.InventorySwap;

//todo Description
@ModuleInfo(
        id = "autoeat",
        displayName = "AutoEat",
        category = ModuleCategory.PLAYER
)
public class AutoEat extends Module {

    private static final String SETTING_EAT_MODE = "eat_mode";
    private static final String SETTING_HAND_MODE = "hand_mode";
    private static final String SETTING_AVOID_FOODS = "avoid_foods";
    private static final String SETTING_HP_THRESHOLD = "hp_threshold";

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<EatMode> eatMode =
            enumSetting("autoEatMode", SETTING_EAT_MODE, EatMode.INSTANT, EatMode.INSTANT, EatMode.EXACT);
    private final EnumValue<HandMode> handMode =
            enumSetting("autoEatHand", SETTING_HAND_MODE, HandMode.MAINHAND, HandMode.MAINHAND, HandMode.OFFHAND);
    private final NumberValue<Integer> hpThreshold =
            num("autoEatHpThreshold", SETTING_HP_THRESHOLD, 8, 1, 20);
    private final BooleanMapValue avoidFoods = group(
            "autoEatAvoidFoods",
            SETTING_AVOID_FOODS,
            new java.util.LinkedHashMap<>() {{
                put("golden_apple", true);
                put("enchanted_golden_apple", true);
            }}
    );
    private boolean forcingUse = false;
    private boolean useKeyWasPressed = false;
    private int prevSelectedSlot = -1;
    private boolean restoreSelected = false;
    private int restoreInvSlot = -1;
    private int restoreHotbarSlot = -1;
    private boolean restoreMainPending = false;
    private int restoreOffhandSlot = -1;
    private boolean restoreOffhandPending = false;

    @Override
    public void onDisable() {
        if (mc != null && mc.player != null) {
            stopAutoUse(mc.player);
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc == null || mc.player == null || mc.gameMode == null) return;

        LocalPlayer player = mc.player;

        if (player.isSpectator() || player.getAbilities().instabuild) {
            stopAutoUse(player);
            return;
        }

        if (player.isUsingItem()) {
            if (FoodUtil.isFood(player.getUseItem())) {
                if (forcingUse && !mc.options.keyUse.isDown()) {
                    mc.options.keyUse.setDown(true);
                }
                return;
            }
            stopAutoUse(player);
            return;
        }

        int hunger = player.getFoodData().getFoodLevel();
        int missing = 20 - hunger;
        int hp = Math.round(player.getHealth() + player.getAbsorptionAmount());
        boolean emergency = hp <= hpThreshold.get();
        if (missing <= 0 && !emergency) {
            stopAutoUse(player);
            return;
        }

        boolean exact = isExactMode();
        InteractionHand desiredHand = resolveDesiredHand(player);

        ItemStack offhand = player.getOffhandItem();
        ItemStack mainhand = player.getMainHandItem();
        boolean offhandCandidate = desiredHand == InteractionHand.OFF_HAND
                && shouldEatWith(offhand, missing, exact, emergency);
        boolean mainhandCandidate = desiredHand == InteractionHand.MAIN_HAND
                && shouldEatWith(mainhand, missing, exact, emergency);
        float offhandScore = offhandCandidate ? FoodUtil.scoreFood(offhand) : Float.NEGATIVE_INFINITY;
        float mainhandScore = mainhandCandidate ? FoodUtil.scoreFood(mainhand) : Float.NEGATIVE_INFINITY;

        int bestSlot = handMode.get() == HandMode.OFFHAND
                ? findBestFoodSlotPreferInventory(player, missing, exact, emergency)
                : findBestFoodSlot(player, missing, exact, emergency);
        if (bestSlot == -1 && (offhandCandidate || mainhandCandidate)) {
            startUse(player, offhandCandidate ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            return;
        }
        if (bestSlot == -1) {
            stopAutoUse(player);
            return;
        }

        float bestScore = FoodUtil.scoreFood(player.getInventory().getItem(bestSlot));
        if (offhandCandidate && offhandScore >= bestScore) {
            startUse(player, InteractionHand.OFF_HAND);
            return;
        }
        if (mainhandCandidate && mainhandScore >= bestScore) {
            startUse(player, InteractionHand.MAIN_HAND);
            return;
        }

        boolean handReady = desiredHand == InteractionHand.OFF_HAND
                ? ensureFoodInOffhand(player, bestSlot, bestScore)
                : ensureFoodInMainhand(player, bestSlot, bestScore);

        if (!handReady) {
            stopAutoUse(player);
            return;
        }

        ItemStack active = desiredHand == InteractionHand.OFF_HAND
                ? player.getOffhandItem()
                : player.getMainHandItem();

        if (!shouldEatWith(active, missing, exact, emergency)) {
            stopAutoUse(player);
            return;
        }

        startUse(player, desiredHand);
    }

    private boolean isExactMode() {
        return eatMode.get() == EatMode.EXACT;
    }

    private InteractionHand resolveDesiredHand(LocalPlayer player) {
        boolean offhandBlocked = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        if (handMode.get() == HandMode.OFFHAND && !offhandBlocked) {
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    private boolean shouldEatWith(ItemStack stack, int missing, boolean exact, boolean emergency) {
        if (!FoodUtil.isFood(stack)) return false;
        if (!isFoodAllowed(stack)) return false;
        if (!exact || emergency) {
            if (missing > 0) return true;
            return emergency && FoodUtil.canAlwaysEat(stack);
        }
        return FoodUtil.getNutrition(stack) <= missing;
    }

    private int findBestFoodSlot(LocalPlayer player, int missing, boolean exact, boolean emergency) {
        int bestSlot = -1;
        float bestScore = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!FoodUtil.isFood(stack)) continue;
            if (!isFoodAllowed(stack)) continue;
            if (exact && !emergency && FoodUtil.getNutrition(stack) > missing) continue;
            if (emergency && missing <= 0 && !FoodUtil.canAlwaysEat(stack)) continue;

            float score = FoodUtil.scoreFood(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private int findBestFoodSlotPreferInventory(LocalPlayer player, int missing, boolean exact, boolean emergency) {
        int bestSlot = -1;
        float bestScore = Float.NEGATIVE_INFINITY;

        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!FoodUtil.isFood(stack)) continue;
            if (!isFoodAllowed(stack)) continue;
            if (exact && !emergency && FoodUtil.getNutrition(stack) > missing) continue;
            if (emergency && missing <= 0 && !FoodUtil.canAlwaysEat(stack)) continue;
            float score = FoodUtil.scoreFood(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if (bestSlot != -1) return bestSlot;
        return findBestFoodSlot(player, missing, exact, emergency);
    }

    private boolean ensureFoodInMainhand(LocalPlayer player, int bestSlot, float bestScore) {
        PlayerInventoryAccessor inv = (PlayerInventoryAccessor) player.getInventory();

        if (restoreMainPending) {
            inv.combatant$setSelectedSlot(restoreHotbarSlot);
            return true;
        }

        ItemStack main = player.getMainHandItem();
        float mainScore = FoodUtil.scoreFood(main);
        if (FoodUtil.isFood(main) && mainScore >= bestScore) return true;

        if (bestSlot < 0) return false;

        if (bestSlot < 9) {
            int selected = inv.combatant$getSelectedSlot();
            if (selected != bestSlot) {
                prevSelectedSlot = selected;
                restoreSelected = true;
                inv.combatant$setSelectedSlot(bestSlot);
            }
            return true;
        }

        int targetHotbar = findEmptyHotbarSlot(player);
        int selected = inv.combatant$getSelectedSlot();
        if (targetHotbar == -1) targetHotbar = selected;

        if (targetHotbar != selected) {
            prevSelectedSlot = selected;
            restoreSelected = true;
        }
        inv.combatant$setSelectedSlot(targetHotbar);

        if (player.inventoryMenu == null) return false;

        restoreInvSlot = bestSlot;
        restoreHotbarSlot = targetHotbar;
        restoreMainPending = true;

        InventorySwap.INSTANCE.swapScreenSlots(InventorySwap.mapInventoryToScreenSlot(bestSlot), InventorySwap.mapHotbarToScreenSlot(targetHotbar));
        return true;
    }

    private boolean ensureFoodInOffhand(LocalPlayer player, int bestSlot, float bestScore) {
        if (restoreOffhandPending) return true;

        ItemStack off = player.getOffhandItem();
        float offScore = FoodUtil.scoreFood(off);
        if (FoodUtil.isFood(off) && offScore >= bestScore) return true;

        if (bestSlot < 0) return false;
        if (player.inventoryMenu == null) return false;

        restoreOffhandSlot = bestSlot;
        restoreOffhandPending = true;

        InventorySwap.INSTANCE.swapInventoryToOffhand(bestSlot);
        return true;
    }

    private int findEmptyHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private boolean isFoodAllowed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return true;
        String key = id.getPath();
        if (!avoidFoods.getAll().containsKey(key)) return true;
        return !avoidFoods.get(key);
    }

    private void startUse(LocalPlayer player, InteractionHand hand) {
        if (!forcingUse) {
            useKeyWasPressed = mc.options.keyUse.isDown();
        }

        if (!mc.options.keyUse.isDown()) {
            mc.options.keyUse.setDown(true);
        }
        forcingUse = true;

        ItemStack used = hand == InteractionHand.OFF_HAND
                ? player.getOffhandItem().copy()
                : player.getMainHandItem().copy();
        InteractionResult r = mc.gameMode.useItem(player, hand);
        if (r.consumesAction()) {
            player.swing(hand);
            Itemizer.showAutoEat(used);
        }
    }

    private void stopAutoUse(LocalPlayer player) {
        if (forcingUse && !useKeyWasPressed) {
            mc.options.keyUse.setDown(false);
        }
        forcingUse = false;

        if (restoreMainPending) {
            restoreMainPending = false;
            restoreMainSwap(player);
        }

        if (restoreOffhandPending) {
            restoreOffhandPending = false;
            restoreOffhandSwap(player);
        }

        if (restoreSelected) {
            restoreSelected = false;
            if (prevSelectedSlot >= 0) {
                ((PlayerInventoryAccessor) player.getInventory()).combatant$setSelectedSlot(prevSelectedSlot);
            }
            prevSelectedSlot = -1;
        }
    }

    private void restoreMainSwap(LocalPlayer player) {
        if (restoreInvSlot < 0 || restoreHotbarSlot < 0) return;
        if (player.inventoryMenu == null) return;

        InventorySwap.INSTANCE.swapScreenSlots(InventorySwap.mapInventoryToScreenSlot(restoreInvSlot), InventorySwap.mapHotbarToScreenSlot(restoreHotbarSlot));

        restoreInvSlot = -1;
        restoreHotbarSlot = -1;
    }

    private void restoreOffhandSwap(LocalPlayer player) {
        if (restoreOffhandSlot < 0) return;
        if (player.inventoryMenu == null) return;

        InventorySwap.INSTANCE.swapInventoryToOffhand(restoreOffhandSlot);
        restoreOffhandSlot = -1;
    }

    @Getter
    @RequiredArgsConstructor
    private enum EatMode implements EnumValue.IdProvider {
        INSTANT("INSTANT"),
        EXACT("EXACT");

        private final String id;
    }

    @Getter
    @RequiredArgsConstructor
    private enum HandMode implements EnumValue.IdProvider {
        MAINHAND("MAINHAND"),
        OFFHAND("OFFHAND");

        private final String id;
    }
}
