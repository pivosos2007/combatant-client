/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.offhand;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.PvpCooldowns;
import combatant.client.util.item.FoodUtil;
import combatant.client.util.player.inventory.InventoryMover;
import combatant.client.util.player.inventory.InventorySlotLocks;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.pvp.client.CooldownsState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

import static combatant.client.features.module.modules.player.offhand.OffhandSettings.*;

/** Inventory searches and item choices (which totem, fallback, gapple or food to bring). Slots are inventory indices. */
public final class OffhandItems {
    public enum ChoiceKind { GAPPLE, FOOD }

    /** A consumable picked from the inventory; {@code slot} is -1 when it is already in the offhand. */
    public record ItemChoice(int slot, ItemStack stack, ChoiceKind kind) {
        public boolean matches(ItemStack other) {
            if (other == null || other.isEmpty()) return false;
            return switch (kind) {
                case GAPPLE -> other.is(Items.GOLDEN_APPLE) || other.is(Items.ENCHANTED_GOLDEN_APPLE);
                case FOOD -> FoodUtil.isFood(other) && !other.is(Items.GOLDEN_APPLE) && !other.is(Items.ENCHANTED_GOLDEN_APPLE);
            };
        }
    }

    private final OffhandSettings settings;
    private final OffhandThreats threats;

    public OffhandItems(OffhandSettings settings, OffhandThreats threats) {
        this.settings = settings;
        this.threats = threats;
    }

    public static boolean isGapple(ItemStack stack) {
        return stack != null && (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE));
    }

    public boolean isItemUsable(LocalPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (player.getCooldowns().isOnCooldown(stack)) return false;
        Item item = stack.getItem();
        if (CooldownsState.MANAGER.isCooling(item)) return false;
        PvpCooldowns pvp = Modules.get(PvpCooldowns.class);
        return pvp == null || !pvp.shouldBlockItemUse(item);
    }

    public boolean isTotemInOffhand(LocalPlayer player) {
        return player != null && player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    public int findTotemSlot(LocalPlayer player) {
        if (!isItemUsable(player, Items.TOTEM_OF_UNDYING.getDefaultInstance())) return -1;
        return InventoryMover.pickTotemSlot(player, settings.saveTaliks().get(), true, -1);
    }

    public int findEmptyHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) if (player.getInventory().getItem(i).isEmpty()) return i;
        return -1;
    }

    public int findUsable(LocalPlayer player, Predicate<ItemStack> predicate) {
        return find(player, stack -> predicate.test(stack) && isItemUsable(player, stack));
    }

    private int findUsableOrHeld(LocalPlayer player, Predicate<ItemStack> predicate) {
        ItemStack held = player.getOffhandItem();
        if (predicate.test(held) && isItemUsable(player, held)) return InventorySlotLocks.OFFHAND;
        return findUsable(player, predicate);
    }

    private int find(LocalPlayer player, Predicate<ItemStack> predicate) {
        if (player == null || predicate == null) return -1;
        for (int i = 0; i < 36; i++) {
            if (predicate.test(player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    /** Only used when protection is required but there is no usable totem. */
    public int findDangerFallbackSlot(LocalPlayer player) {
        int bestSlot = -1;
        int bestPriority = -1;
        if (settings.itemProfiles().get(PROFILE_SHIELD) && settings.fallbacks().get(FALLBACK_SHIELD)) {
            int slot = findUsableOrHeld(player, stack -> stack.is(Items.SHIELD) && shieldHealthy(stack));
            if (slot >= 0 && settings.shieldFallbackPriority().get() > bestPriority) {
                bestSlot = slot;
                bestPriority = settings.shieldFallbackPriority().get();
            }
        }
        if (settings.itemProfiles().get(PROFILE_CRYSTAL) && settings.fallbacks().get(FALLBACK_CRYSTAL)
                && threats.isCrystalContext()) {
            int slot = findUsableOrHeld(player, stack -> stack.is(Items.END_CRYSTAL));
            if (slot >= 0 && settings.crystalFallbackPriority().get() > bestPriority) {
                bestSlot = slot;
                bestPriority = settings.crystalFallbackPriority().get();
            }
        }
        if (settings.itemProfiles().get(PROFILE_ENCHANTED) && settings.fallbacks().get(FALLBACK_ENCHANTED)) {
            int slot = findUsableOrHeld(player, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE));
            if (slot >= 0 && settings.enchantedFallbackPriority().get() > bestPriority) {
                bestSlot = slot;
                bestPriority = settings.enchantedFallbackPriority().get();
            }
        }
        if (settings.itemProfiles().get(PROFILE_GAPPLE) && settings.fallbacks().get(FALLBACK_GAPPLE)) {
            int slot = findUsableOrHeld(player, stack -> stack.is(Items.GOLDEN_APPLE));
            if (slot >= 0 && settings.gappleFallbackPriority().get() > bestPriority) {
                bestSlot = slot;
                bestPriority = settings.gappleFallbackPriority().get();
            }
        }
        if (settings.itemProfiles().get(PROFILE_FOOD) && settings.fallbacks().get(FALLBACK_FOOD)) {
            int slot = findUsableOrHeld(player, stack -> FoodUtil.isFood(stack) && !isGapple(stack));
            if (slot >= 0 && settings.foodFallbackPriority().get() > bestPriority) {
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    /** The offhand item a safe player should hold according to the safe-preference setting, or -1. */
    public int preferredSafeOffhand(LocalPlayer player) {
        return switch (settings.safeOffhand().get()) {
            case KEEP, TOTEM -> -1;
            case SHIELD -> settings.itemProfiles().get(PROFILE_SHIELD)
                    ? findUsable(player, stack -> stack.is(Items.SHIELD) && shieldHealthy(stack)) : -1;
            case CRYSTAL -> settings.itemProfiles().get(PROFILE_CRYSTAL) && threats.isSafeForCrystal(player)
                    ? findUsable(player, stack -> stack.is(Items.END_CRYSTAL)) : -1;
            case GAPPLE -> {
                ItemChoice choice = settings.useGapples().get()
                        && OffhandThreats.effectiveHealth(player) <= settings.gappleHealth().get()
                        ? findBestGapple(player) : null;
                yield choice == null ? -1 : choice.slot();
            }
            case FOOD -> {
                ItemChoice choice = player.getFoodData().getFoodLevel() <= settings.hungerThreshold().get()
                        && settings.itemProfiles().get(PROFILE_FOOD)
                        ? findBestFood(player, 20 - player.getFoodData().getFoodLevel(), false, true) : null;
                yield choice == null ? -1 : choice.slot();
            }
            case CONTEXTUAL -> {
                int slot = threats.isCrystalContext() && settings.itemProfiles().get(PROFILE_CRYSTAL)
                        && threats.isSafeForCrystal(player)
                        ? findUsable(player, stack -> stack.is(Items.END_CRYSTAL)) : -1;
                if (slot == -1 && threats.isPvpContext(player) && settings.itemProfiles().get(PROFILE_SHIELD)) {
                    slot = findUsable(player, stack -> stack.is(Items.SHIELD) && shieldHealthy(stack));
                }
                yield slot;
            }
        };
    }

    public ItemChoice selectConsumable(LocalPlayer player, int appleGuardUntilTick) {
        float health = OffhandThreats.effectiveHealth(player);
        if (settings.useGapples().get() && player.tickCount >= appleGuardUntilTick
                && health <= settings.gappleHealth().get()
                && (player.getAbsorptionAmount() < 4.0f || health <= 6.0f)) {
            ItemChoice gapple = findBestGapple(player);
            if (gapple != null) return gapple;
        }

        if (!settings.useFood().get() || !settings.itemProfiles().get(PROFILE_FOOD)) return null;
        int hunger = player.getFoodData().getFoodLevel();
        boolean emergency = health <= settings.foodEmergencyHealth().get();
        if (hunger > settings.hungerThreshold().get() && !emergency) return null;

        int missing = 20 - hunger;
        return findBestFood(player, missing, emergency, settings.eatMode().get() == EatMode.EXACT);
    }

    public static boolean shieldHealthy(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.SHIELD)) return false;
        return !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 1;
    }

    private ItemChoice findBestGapple(LocalPlayer player) {
        float health = OffhandThreats.effectiveHealth(player);
        ItemChoice best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isGapple(stack) || !isItemUsable(player, stack)) continue;
            if (stack.is(Items.GOLDEN_APPLE) && !settings.itemProfiles().get(PROFILE_GAPPLE)) continue;
            if (stack.is(Items.ENCHANTED_GOLDEN_APPLE) && !settings.itemProfiles().get(PROFILE_ENCHANTED)) continue;
            int score = stack.is(Items.ENCHANTED_GOLDEN_APPLE) ? (health <= 6.0f ? 220 : 80) : 150;
            if (i < 9) score += 10;
            if (score > bestScore) {
                bestScore = score;
                best = new ItemChoice(i, stack.copy(), ChoiceKind.GAPPLE);
            }
        }
        ItemStack main = player.getMainHandItem();
        if (isGapple(main) && isItemUsable(player, main)
                && (main.is(Items.GOLDEN_APPLE) && settings.itemProfiles().get(PROFILE_GAPPLE)
                || main.is(Items.ENCHANTED_GOLDEN_APPLE) && settings.itemProfiles().get(PROFILE_ENCHANTED))) {
            int score = main.is(Items.ENCHANTED_GOLDEN_APPLE) ? (health <= 6.0f ? 240 : 90) : 160;
            if (score >= bestScore) {
                best = new ItemChoice(InventorySwap.INSTANCE.clientSelectedSlot(), main.copy(), ChoiceKind.GAPPLE);
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (isGapple(offhand) && isItemUsable(player, offhand)) {
            int score = offhand.is(Items.ENCHANTED_GOLDEN_APPLE) ? 70 : 170;
            if ((offhand.is(Items.GOLDEN_APPLE) && settings.itemProfiles().get(PROFILE_GAPPLE)
                    || offhand.is(Items.ENCHANTED_GOLDEN_APPLE) && settings.itemProfiles().get(PROFILE_ENCHANTED))
                    && score >= bestScore) best = new ItemChoice(-1, offhand.copy(), ChoiceKind.GAPPLE);
        }
        return best;
    }

    private ItemChoice findBestFood(LocalPlayer player, int missing, boolean emergency, boolean exact) {
        ItemChoice best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!FoodUtil.isFood(stack) || isGapple(stack) || !isItemUsable(player, stack)) continue;
            if (exact && !emergency && FoodUtil.getNutrition(stack) > missing) continue;
            if (emergency && missing <= 0 && !FoodUtil.canAlwaysEat(stack)) continue;
            float score = FoodUtil.scoreFood(stack) + (i < 9 ? 2.0f : 0.0f);
            if (score > bestScore) {
                bestScore = score;
                best = new ItemChoice(i, stack.copy(), ChoiceKind.FOOD);
            }
        }
        return best;
    }
}
