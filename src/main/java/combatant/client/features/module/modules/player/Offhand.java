/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.actions.HudAction;
import combatant.client.features.gui.hud.actions.HudActionRegistry;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.player.offhand.InventoryLeaseKeeper;
import combatant.client.features.module.modules.player.offhand.MainhandSwitcher;
import combatant.client.features.module.modules.player.offhand.OffhandConsumer;
import combatant.client.features.module.modules.player.offhand.OffhandItems;
import combatant.client.features.module.modules.player.offhand.OffhandRefillSession;
import combatant.client.features.module.modules.player.offhand.OffhandSettings;
import combatant.client.features.module.modules.player.offhand.OffhandSettings.CombatConsume;
import combatant.client.features.module.modules.player.offhand.OffhandSettings.ConsumeControl;
import combatant.client.features.module.modules.player.offhand.OffhandSettings.ConsumeHand;
import combatant.client.features.module.modules.player.offhand.OffhandSettings.EatMode;
import combatant.client.features.module.modules.player.offhand.OffhandSettings.SafeOffhand;
import combatant.client.features.module.modules.player.offhand.OffhandSwapper;
import combatant.client.features.module.modules.player.offhand.OffhandThreats;
import combatant.client.util.player.inventory.InventorySlotLocks;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;

import static combatant.client.features.module.modules.player.offhand.OffhandSettings.*;

/**
 * Offhand safety and consumption. This class declares the settings, the HUD action, the tick order and the public
 * API other modules use; the work lives in {@code player.offhand}:
 * <ul>
 *     <li>{@link OffhandThreats} decides when a totem is needed,</li>
 *     <li>{@link OffhandItems} finds and chooses items,</li>
 *     <li>{@link OffhandSwapper} moves one item into the offhand,</li>
 *     <li>{@link OffhandRefillSession} refills totems through an opened inventory,</li>
 *     <li>{@link OffhandConsumer} runs the eat/gapple lifecycle,</li>
 *     <li>{@link MainhandSwitcher} brings a consumable to the main hand and puts everything back.</li>
 * </ul>
 * Every click and held-slot change goes through {@code InventorySwap}/{@code InventoryMover}.
 */
@ModuleInfo(
        id = "offhand",
        displayName = "Offhand",
        aliases = {"AutoTotem", "AutoEat", "AutoGApple"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.offhand.description")
public final class Offhand extends Module {
    private static final String ACTION_CONSUME = "consume";

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> healthThreshold =
            numCommon("autototem_threshold", "health_threshold", CommonSettingSchemas.PLAYER_HEALTH_THRESHOLD, 10.0f, 1.0f, 40.0f);
    private final NumberValue<Float> elytraHealth =
            numCommon("autototem_elytra_health", "elytra_health", CommonSettingSchemas.PLAYER_ELYTRA_HEALTH, 8.5f, 1.0f, 40.0f);
    private final NumberValue<Float> crystalDistance =
            numCommon("autototem_crystal_distance", "crystal_distance", CommonSettingSchemas.COMBAT_CRYSTAL_DISTANCE, 4.0f, 1.0f, 6.0f);
    private final BooleanValue fallCheck =
            boolCommon("autototem_fall_check", "fall_check", CommonSettingSchemas.PLAYER_FALL_CHECK, true);
    private final BooleanValue saveTaliks =
            boolCommon("autototem_save_taliks", "save_taliks", CommonSettingSchemas.ITEMS_SAVE_UNENCHANTED, true);

    private final BooleanMapValue threats = group(
            "offhand_threats",
            "threats",
            new LinkedHashMap<>() {{
                put(THREAT_BROKEN_ARMOR, true);
                put(THREAT_MACE, true);
                put(THREAT_PROJECTILES, true);
            }}
    );

    private final BooleanMapValue fallbacks = group(
            "offhand_fallbacks",
            "fallbacks",
            new LinkedHashMap<>() {{
                put(FALLBACK_SHIELD, true);
                put(FALLBACK_CRYSTAL, false);
                put(FALLBACK_ENCHANTED, false);
                put(FALLBACK_GAPPLE, false);
                put(FALLBACK_FOOD, false);
            }}
    );

    // Higher number wins; equal priorities use stable safety-oriented order.
    private final NumberValue<Integer> shieldFallbackPriority = visibleWhen(
            num("offhand_fallback_shield_priority", "fallback_shield_priority", 10, 1, 10),
            () -> fallbacks.get(FALLBACK_SHIELD));
    private final NumberValue<Integer> crystalFallbackPriority = visibleWhen(
            num("offhand_fallback_crystal_priority", "fallback_crystal_priority", 8, 1, 10),
            () -> fallbacks.get(FALLBACK_CRYSTAL));
    private final NumberValue<Integer> enchantedFallbackPriority = visibleWhen(
            num("offhand_fallback_enchanted_priority", "fallback_enchanted_priority", 7, 1, 10),
            () -> fallbacks.get(FALLBACK_ENCHANTED));
    private final NumberValue<Integer> gappleFallbackPriority = visibleWhen(
            num("offhand_fallback_gapple_priority", "fallback_gapple_priority", 6, 1, 10),
            () -> fallbacks.get(FALLBACK_GAPPLE));
    private final NumberValue<Integer> foodFallbackPriority = visibleWhen(
            num("offhand_fallback_food_priority", "fallback_food_priority", 4, 1, 10),
            () -> fallbacks.get(FALLBACK_FOOD));

    private final BooleanValue passiveSwaps = bool("offhand_passive_swaps", "passive_swaps", false);
    // Crystal in the offhand whenever a totem is not needed, so AutoCrystal places without a hotbar swap.
    private final BooleanValue crystalWhenSafe = visibleWhen(
            bool("offhand_crystal_when_safe", "crystal_when_safe", false), passiveSwaps::get);
    // Health that must be left after the worst crystal already in range goes off.
    private final NumberValue<Float> crystalSafeHealth = visibleWhen(
            num("offhand_crystal_safe_health", "crystal_safe_health", 16.0f, 4.0f, 36.0f),
            () -> passiveSwaps.get() && crystalWhenSafe.get());

    private final BooleanMapValue itemProfiles = group("offhand_item_profiles", "item_profiles",
            new LinkedHashMap<>() {{
                put(PROFILE_TOTEM, true);
                put(PROFILE_SHIELD, true);
                put(PROFILE_CRYSTAL, true);
                put(PROFILE_GAPPLE, true);
                put(PROFILE_ENCHANTED, true);
                put(PROFILE_FOOD, true);
            }});
    private final EnumValue<SafeOffhand> safeOffhand = visibleWhen(enumSetting(
            "offhand_safe_preference", "safe_preference", SafeOffhand.KEEP, SafeOffhand.values()),
            passiveSwaps::get);
    private final EnumValue<ConsumeHand> consumeHand = enumSetting("offhand_consume_hand", "consume_hand",
            ConsumeHand.AUTO, ConsumeHand.values());
    private final EnumValue<CombatConsume> consumeCombat = enumSetting("offhand_consume_combat", "consume_combat",
            CombatConsume.OUT_OF_COMBAT, CombatConsume.values());
    private final NumberValue<Integer> consumeCooldown = num("offhand_consume_cooldown", "consume_cooldown", 80, 10, 240);
    private final BooleanValue autoConsume = bool("offhand_auto_consume", "auto_consume", true);
    private final EnumValue<ConsumeControl> consumeControl = enumSetting(
            "offhand_consume_control", "consume_control", ConsumeControl.SEMI, ConsumeControl.values());
    private final BooleanValue useGapples = bool("offhand_use_gapples", "use_gapples", true);
    private final NumberValue<Float> gappleHealth = num("offhand_gapple_health", "gapple_health", 16.0f, 1.0f, 40.0f);
    private final BooleanValue useFood = bool("offhand_use_food", "use_food", true);
    private final NumberValue<Integer> hungerThreshold = num("offhand_hunger_threshold", "hunger_threshold", 16, 1, 19);
    private final NumberValue<Float> foodEmergencyHealth = num("offhand_food_emergency_health", "food_emergency_health", 8.0f, 1.0f, 40.0f);
    private final EnumValue<EatMode> eatMode = enumSetting("offhand_eat_mode", "eat_mode", EatMode.EXACT, EatMode.values());
    private final BooleanValue returnSlot = bool("offhand_return_slot", "return_slot", true);

    // Opt-in: after a pop (or with an empty offhand) open the inventory like a human, wait a random
    // delay, move a totem to the offhand / hotbar slot through InventoryMover and close it again.
    private final BooleanValue inventoryRefill = bool("offhand_inventory_refill", "inventory_refill", false);
    private final BooleanValue refillOpenInventory = visibleWhen(
            bool("offhand_refill_open_inventory", "refill_open_inventory", true), inventoryRefill::get);
    private final NumberValue<Integer> refillMinDelay = visibleWhen(
            num("offhand_refill_min_delay", "refill_min_delay_ticks", 2, 0, 20), inventoryRefill::get);
    private final NumberValue<Integer> refillMaxDelay = visibleWhen(
            num("offhand_refill_max_delay", "refill_max_delay_ticks", 5, 0, 20), inventoryRefill::get);
    private final NumberValue<Integer> refillHotbarSlot = visibleWhen(
            num("offhand_refill_hotbar_slot", "refill_hotbar_slot", 0, 0, 9), inventoryRefill::get);
    private final BooleanValue refillSaveEnchanted = visibleWhen(
            bool("offhand_refill_save_enchanted", "refill_save_enchanted", true), inventoryRefill::get);
    private final BooleanValue refillPreferHotbar = visibleWhen(
            bool("offhand_refill_prefer_hotbar", "refill_prefer_hotbar", false), inventoryRefill::get);
    private final BooleanValue refillCloseAfter = visibleWhen(
            bool("offhand_refill_close_after", "refill_close_after", true), inventoryRefill::get);

    private final OffhandSettings settings = new OffhandSettings(
            healthThreshold, elytraHealth, crystalDistance, fallCheck, saveTaliks, threats, fallbacks,
            shieldFallbackPriority, crystalFallbackPriority, enchantedFallbackPriority, gappleFallbackPriority,
            foodFallbackPriority, passiveSwaps, crystalWhenSafe, crystalSafeHealth, itemProfiles, safeOffhand,
            consumeHand, consumeCombat, consumeCooldown, autoConsume, consumeControl, useGapples, gappleHealth,
            useFood, hungerThreshold, foodEmergencyHealth, eatMode, returnSlot, inventoryRefill,
            refillOpenInventory, refillMinDelay, refillMaxDelay, refillHotbarSlot, refillSaveEnchanted,
            refillPreferHotbar, refillCloseAfter);

    private final OffhandThreats dangers = new OffhandThreats(settings);
    private final OffhandItems items = new OffhandItems(settings, dangers);
    private final InventoryLeaseKeeper lease = new InventoryLeaseKeeper(this);
    private final OffhandSwapper swapper = new OffhandSwapper(this, this::isEnabled, dangers, items, lease,
            this::onSwapTimedOut);
    private final MainhandSwitcher switcher = new MainhandSwitcher(this, settings, items, lease);
    private final OffhandConsumer consumer = new OffhandConsumer(this, settings, this::isEnabled,
            () -> isActionPressedOnce(ACTION_CONSUME), dangers, items, swapper, switcher);
    private final OffhandRefillSession refill = new OffhandRefillSession(this, settings, this::isEnabled,
            swapper, dangers, items, switcher::isInventoryBusy);

    {
        addAction(ACTION_CONSUME, "V");
        lease.setBusy(() -> swapper.isPending() || switcher.isInventoryBusy());
    }

    @HudAction(id = "consume", label = "Consume", description = "Eat suitable food or an apple when offered", icon = "apple")
    public HudActionRegistry.State hudConsume() {
        return consumer.hudState(mc.player, isEnabled());
    }

    @Override
    public String getConfigName() {
        return "autototem";
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        // Refill first: it yields to (and aborts for) any swap Offhand's own policy has pending, so
        // only one path owns the offhand slot per tick.
        refill.tick();
        tickOffhand();
    }

    private void tickOffhand() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.isPaused()) {
            InventorySlotLocks.release(this);
            swapper.reset();
            InventorySwap.INSTANCE.cancelQueuedInventoryActions(this);
            lease.reset();
            dangers.resetForcedTotem();
            consumer.resetPaused();
            return;
        }

        boolean protectiveDanger = dangers.shouldHoldTotem(player);
        // A selected Totem preset reserves the offhand even while healthy: otherwise the
        // player can remove the totem and the next queued passive swap silently restores it.
        // Other presets leave the slot free until the protective policy actually needs it.
        boolean fixedTotemPreset = passiveSwaps.get()
                && safeOffhand.get() == SafeOffhand.TOTEM
                && itemProfiles.get(PROFILE_TOTEM);
        boolean protectiveReservation = protectiveDanger
                && (items.isTotemInOffhand(player) || items.findTotemSlot(player) >= 0
                    || items.findDangerFallbackSlot(player) >= 0 || swapper.isPending());
        if (fixedTotemPreset || protectiveReservation) {
            InventorySlotLocks.claim(this, InventorySlotLocks.OFFHAND, "Offhand");
        } else {
            InventorySlotLocks.release(this);
        }

        // Start-of-tick order matters for Grim: restores and slot selections first (they are only sent while the
        // window allows it), releases and uses of this tick afterwards.
        lease.tick(player);
        swapper.yieldToEmergency(player);
        switcher.tick(player);
        if (swapper.update(player)) return;

        consumer.observeUse(player);
        boolean danger = dangers.shouldHoldTotem(player);
        // Crystals nearby always count as danger above; with a crystal offhand that is the point.
        if (danger && passiveSwaps.get() && crystalWhenSafe.get() && dangers.isSafeForCrystal(player)) danger = false;
        if (danger) consumer.yieldToDanger(player);

        if (!consumer.holdsOffhandTurn() || danger) updateOffhandPolicy(player, danger);
        if (swapper.isPending() || swapper.isQuiet(player) || switcher.isQuiet(player)) {
            consumer.clearSuggestionIfIdle();
            return;
        }
        consumer.tick(player, danger);
    }

    @Override
    public void onDisable() {
        InventorySlotLocks.release(this);
        swapper.reset();
        refill.resetAll();
        consumer.shutdown(mc.player);
        dangers.resetForcedTotem();
        lease.reset();
    }

    @Override
    public void onEnable() {
        refill.resetAll();
    }

    private void onSwapTimedOut() {
        consumer.abandonOffhandTurn();
    }

    // ---- public API used by other modules -----------------------------------------------------------------

    public boolean shouldHoldTotemNow(LocalPlayer player) {
        if (!isEnabled() || player == null) return false;
        return dangers.shouldHoldTotem(player);
    }

    public boolean isTotemPriorityActive(LocalPlayer player) {
        if (!isEnabled() || player == null) return false;
        if (shouldHoldTotemNow(player) || swapper.isPending()) return true;
        if (!items.isItemUsable(player, Items.TOTEM_OF_UNDYING.getDefaultInstance())) return false;
        return items.isTotemInOffhand(player) || items.findTotemSlot(player) != -1;
    }

    public boolean canProvideTotemNow(LocalPlayer player) {
        if (!isEnabled() || player == null || mc.gameMode == null) return false;
        if (!items.isItemUsable(player, Items.TOTEM_OF_UNDYING.getDefaultInstance())) return false;
        if (items.isTotemInOffhand(player)) return true;
        if (swapper.isPending()) return false;
        return items.findTotemSlot(player) != -1;
    }

    public boolean ensureTotemForDanger(LocalPlayer player) {
        if (!canProvideTotemNow(player)) return false;
        dangers.forceTotemUntil(player.tickCount + InventorySwap.INSTANCE.queuedMaxTicks() + 8);
        if (items.isTotemInOffhand(player)) return true;
        int slot = items.findTotemSlot(player);
        return slot != -1 && swapper.request(slot, true);
    }

    public boolean isTotemSwapPending() {
        return swapper.isPending();
    }

    // ---- offhand policy -----------------------------------------------------------------------------------

    private void updateOffhandPolicy(LocalPlayer player, boolean danger) {
        if (!danger) {
            if (!passiveSwaps.get()) return;
            int preferred = -1;
            if (itemProfiles.get(PROFILE_CRYSTAL) && crystalWhenSafe.get() && dangers.isSafeForCrystal(player)) {
                preferred = items.findUsable(player, stack -> stack.is(Items.END_CRYSTAL));
            }
            if (preferred < 0 && safeOffhand.get() == SafeOffhand.TOTEM && itemProfiles.get(PROFILE_TOTEM)) {
                preferred = items.findTotemSlot(player);
            } else if (preferred < 0 && safeOffhand.get() != SafeOffhand.KEEP
                    && safeOffhand.get() != SafeOffhand.TOTEM) {
                preferred = items.preferredSafeOffhand(player);
            }
            if (preferred >= 0) {
                ItemStack wanted = player.getInventory().getItem(preferred);
                if (!ItemStack.isSameItemSameComponents(player.getOffhandItem(), wanted)) {
                    swapper.request(preferred, false);
                }
            }
            return;
        }

        int totemSlot = items.findTotemSlot(player);
        if (items.isItemUsable(player, Items.TOTEM_OF_UNDYING.getDefaultInstance())
                && (items.isTotemInOffhand(player) || totemSlot != -1)) {
            if (!items.isTotemInOffhand(player) && totemSlot != -1) {
                swapper.request(totemSlot, true);
            }
            return;
        }

        int fallbackSlot = items.findDangerFallbackSlot(player);
        if (fallbackSlot >= 0 && fallbackSlot < 36) {
            ItemStack wanted = player.getInventory().getItem(fallbackSlot);
            if (!ItemStack.isSameItemSameComponents(player.getOffhandItem(), wanted)) {
                swapper.request(fallbackSlot, true);
            }
        }
    }
}
