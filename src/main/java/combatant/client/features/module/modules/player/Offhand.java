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
import combatant.client.features.gui.hud.draggable.impl.Itemizer;
import combatant.client.features.gui.hud.actions.HudAction;
import combatant.client.features.gui.hud.actions.HudActionRegistry;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.AutoCrystal;
import combatant.client.features.module.modules.combat.PvpCooldowns;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.item.FoodUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.pvp.client.CooldownsState;
import combatant.client.util.target.TargetingUtil;
import combatant.client.util.world.ExplosionDamageUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.function.Predicate;

@ModuleInfo(
        id = "offhand",
        displayName = "Offhand",
        aliases = {"AutoTotem", "AutoEat", "AutoGApple"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.offhand.description")
public final class Offhand extends Module {
    private static final String THREAT_BROKEN_ARMOR = "broken_armor";
    private static final String THREAT_MACE = "mace";
    private static final String THREAT_PROJECTILES = "projectiles";
    private static final String FALLBACK_SHIELD = "shield";
    private static final String FALLBACK_CRYSTAL = "crystal";
    private static final String PROFILE_TOTEM = "totem";
    private static final String PROFILE_SHIELD = "shield";
    private static final String PROFILE_CRYSTAL = "crystal";
    private static final String PROFILE_GAPPLE = "golden_apple";
    private static final String PROFILE_ENCHANTED = "enchanted_golden_apple";
    private static final String PROFILE_FOOD = "food";
    private static final String ACTION_CONSUME = "consume";
    private static final int INVENTORY_CONFIRM_TICKS = 2;
    private static final int INVENTORY_TIMEOUT_TICKS = 10;
    // After a totem was needed, stay on it this long before going back to a crystal, so a fight that
    // hovers around the threshold does not bounce the offhand every tick.
    private static final int CRYSTAL_SAFE_HOLD_TICKS = 20;
    // Explosion damage (power 6) reaches 12 blocks.
    private static final double CRYSTAL_DAMAGE_RANGE = 12.0;

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
            }}
    );

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

    {
        addAction(ACTION_CONSUME, "V");
    }

    private boolean semiConsumeArmed;
    private int semiConsumeDeadlineTick = -1;
    private boolean suggestedReady;
    private ItemStack suggestedStack = ItemStack.EMPTY;
    private ItemStack consumingStack = ItemStack.EMPTY;
    private int consumeRequestTick = -1;
    private int consumeDisplayUntilTick = -1;
    private int appleAbsorptionGuardUntilTick = -1;
    private boolean offhandSwapPending;
    private ItemStack expectedOffhand = ItemStack.EMPTY;
    private boolean expectOffhandEmpty;
    private int offhandSwapIssuedTick = -1;
    private int offhandSwapDeadlineTick = -1;
    private Runnable offhandSwapConfirmedAction;
    private int inventoryQuietUntilTick = -1;
    private int mainhandReadyTick = -1;
    private int mainhandDeadlineTick = -1;
    private int autoSelectedSlot = -1;
    private ItemStack expectedMainhand = ItemStack.EMPTY;
    private int lastUseObservedTick = -1;
    private int inventoryLeaseReleaseTick = -1;
    private int forcedTotemUntilTick = -1;
    private boolean forcingUse;
    private boolean offhandConsumeActive;
    private boolean useWasObserved;
    private int consumeLockedUntilTick = -1;
    private boolean gappleConsumption;
    private boolean gappleWasEnchanted;
    private int previousSelectedSlot = -1;
    private boolean restoreSelected;
    private int restoreInventorySlot = -1;
    private int restoreHotbarSlot = -1;
    private boolean restoreInventoryPending;
    private int crystalUnsafeUntilTick = -1;
    // Actual tick scheduling belongs exclusively to InventorySwap.
    private boolean useActionQueued;
    private boolean restoreActionQueued;

    @HudAction(id = "totem_priority", label = "Totem", description = "Offhand survival priority", icon = "shield")
    public HudActionRegistry.State hudTotem() {
        LocalPlayer player = mc.player;
        boolean danger = player != null && shouldHoldTotem(player, effectiveHealth(player));
        return new HudActionRegistry.State(new ItemStack(Items.TOTEM_OF_UNDYING),
                player != null && findTotemSlot(player) != -1 || player != null && isTotemInOffhand(player),
                player != null && isTotemInOffhand(player), danger, 0.0f);
    }

    @HudAction(id = "consume", label = "Consume", description = "Eat suitable food or an apple when offered", icon = "apple")
    public HudActionRegistry.State hudConsume() {
        LocalPlayer player = mc.player;
        boolean ready = player != null && isEnabled() && suggestedReady;
        boolean inProgress = player != null && (forcingUse || offhandConsumeActive
                || useActionQueued || restoreActionQueued
                || semiConsumeArmed && player.tickCount <= semiConsumeDeadlineTick);
        ItemStack display = inProgress && !consumingStack.isEmpty() ? consumingStack : suggestedStack;
        boolean linger = player != null && player.tickCount < consumeDisplayUntilTick;
        if (display.isEmpty() && linger) display = consumingStack;
        boolean semi = consumeControl.get() == ConsumeControl.SEMI;
        float cooldown = player == null ? 0.0f
                : Math.max(0.0f, Math.min(1.0f,
                    (consumeLockedUntilTick - player.tickCount) / (float) Math.max(1, consumeCooldown.get())));
        float progress = inProgress && player != null && player.isUsingItem()
                ? Math.min(1.0f, player.getTicksUsingItem() / 32.0f) : 0.0f;
        return new HudActionRegistry.State(display, ready || inProgress || linger,
                inProgress, ready && semi && !inProgress, cooldown, inProgress, progress);
    }

    @Override
    public String getConfigName() {
        return "autototem";
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.isPaused()) {
            resetOffhandSwapState();
            InventorySwap.INSTANCE.cancelQueuedInventoryActions(this);
            InventorySwap.INSTANCE.releaseInventory(this);
            inventoryLeaseReleaseTick = -1;
            forcedTotemUntilTick = -1;
            resetConsumeState();
            semiConsumeArmed = false;
            clearSuggestion();
            return;
        }

        releaseInventoryLeaseIfDue(player);
        if (restoreInventoryPending || offhandSwapPending) {
            InventorySwap.INSTANCE.leaseInventory(this, 80);
        }
        // A queued passive swap must never prevent a new emergency totem request.
        if (offhandSwapPending && offhandSwapIssuedTick < 0
                && !expectedOffhand.is(Items.TOTEM_OF_UNDYING)
                && shouldHoldTotem(player, effectiveHealth(player))) {
            InventorySwap.INSTANCE.cancelQueuedInventoryActions(this, InventoryActionKind.INVENTORY_CLICK);
            resetOffhandSwapState();
            if (!restoreInventoryPending) InventorySwap.INSTANCE.releaseInventory(this);
        }
        if (updatePendingOffhandSwap(player)) return;

        if (useWasObserved && !player.isUsingItem()) {
            consumeLockedUntilTick = Math.max(consumeLockedUntilTick,
                    player.tickCount + (gappleWasEnchanted ? Math.max(120, consumeCooldown.get()) : consumeCooldown.get()));
            if (gappleConsumption) appleAbsorptionGuardUntilTick = Math.max(
                    appleAbsorptionGuardUntilTick, player.tickCount + (gappleWasEnchanted ? 160 : 80));
            consumeDisplayUntilTick = Math.max(consumeDisplayUntilTick, player.tickCount + 12);
            stopAutoUse(player);
            semiConsumeArmed = false;
            useWasObserved = false;
            offhandConsumeActive = false;
            consumeRequestTick = -1;
        }
        if (consumeRequestTick >= 0 && !useWasObserved && !player.isUsingItem()
                && player.tickCount - consumeRequestTick > 10) {
            consumeLockedUntilTick = Math.max(consumeLockedUntilTick, player.tickCount + consumeCooldown.get());
            stopAutoUse(player);
            semiConsumeArmed = false;
            consumeRequestTick = -1;
        }
        boolean danger = shouldHoldTotem(player, effectiveHealth(player));
        // Crystals nearby always count as danger above; with a crystal offhand that is the point.
        if (danger && passiveSwaps.get() && crystalWhenSafe.get() && isSafeForCrystal(player)) danger = false;
        if (danger && offhandConsumeActive) {
            stopAutoUse(player);
            offhandConsumeActive = false;
        }
        if (danger && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            player.releaseUsingItem();
        }
        if (danger && !isTotemInOffhand(player) && forcingUse) {
            if (player.isUsingItem()) player.releaseUsingItem();
            forcingUse = false;
        }

        if (!offhandConsumeActive || danger) updateOffhandPolicy(player, danger);
        if (offhandSwapPending || player.tickCount <= inventoryQuietUntilTick) {
            if (!semiConsumeArmed && !forcingUse && !offhandConsumeActive) clearSuggestion();
            return;
        }
        updateSuggestion(player, danger);

        boolean consumptionEnabled = autoConsume.get()
                && Itemizer.isActionEnabled(name(), ACTION_CONSUME)
                && consumeControl.get() != ConsumeControl.DISABLED;
        if (consumptionEnabled && consumeControl.get() == ConsumeControl.SEMI) {
            if (isActionPressedOnce(ACTION_CONSUME) && canOfferConsumption(player, danger)) {
                semiConsumeArmed = true;
                semiConsumeDeadlineTick = player.tickCount + 80;
            }
            if (semiConsumeArmed && player.tickCount > semiConsumeDeadlineTick
                    && !forcingUse && !player.isUsingItem()) semiConsumeArmed = false;
        }
        if (consumptionEnabled && (consumeControl.get() == ConsumeControl.AUTO || semiConsumeArmed)) {
            updateConsumption(player, danger);
        } else if (forcingUse || restoreInventoryPending || restoreSelected) {
            stopAutoUse(player);
        }
        if (!consumptionEnabled) semiConsumeArmed = false;
        if (!consumptionEnabled && useActionQueued) {
            InventorySwap.INSTANCE.cancelQueuedInventoryActions(this, InventoryActionKind.USE_ITEM);
            useActionQueued = false;
        }
    }

    @Override
    public void onDisable() {
        LocalPlayer player = mc.player;
        if (player != null) stopAutoUse(player, true);
        resetConsumeState();
        semiConsumeArmed = false;
        clearSuggestion();
        InventorySwap.INSTANCE.cancelQueuedInventoryActions(this);
        resetOffhandSwapState();
        InventorySwap.INSTANCE.releaseInventory(this);
        inventoryLeaseReleaseTick = -1;
        forcedTotemUntilTick = -1;
    }

    public boolean shouldHoldTotemNow(LocalPlayer player) {
        if (!isEnabled() || player == null) return false;
        return shouldHoldTotem(player, effectiveHealth(player));
    }

    public boolean isTotemPriorityActive(LocalPlayer player) {
        if (!isEnabled() || player == null) return false;
        if (shouldHoldTotemNow(player) || offhandSwapPending) return true;
        if (!isItemUsable(player, Items.TOTEM_OF_UNDYING.getDefaultInstance())) return false;
        return isTotemInOffhand(player) || findTotemSlot(player) != -1;
    }

    public boolean canProvideTotemNow(LocalPlayer player) {
        if (!isEnabled() || player == null || mc.gameMode == null) return false;
        if (!isItemUsable(player, Items.TOTEM_OF_UNDYING.getDefaultInstance())) return false;
        if (isTotemInOffhand(player)) return true;
        if (offhandSwapPending) return false;
        return findTotemSlot(player) != -1;
    }

    public boolean ensureTotemForDanger(LocalPlayer player) {
        if (!canProvideTotemNow(player)) return false;
        forcedTotemUntilTick = Math.max(forcedTotemUntilTick,
                player.tickCount + InventorySwap.INSTANCE.queuedMaxTicks() + 8);
        if (isTotemInOffhand(player)) return true;
        int slot = findTotemSlot(player);
        return slot != -1 && requestOffhandSwap(slot,
                () -> Itemizer.showAutoTotem(player.getOffhandItem().copy()), true);
    }

    public boolean isTotemSwapPending() {
        return offhandSwapPending;
    }

    private void updateOffhandPolicy(LocalPlayer player, boolean danger) {
        if (!danger) {
            if (!passiveSwaps.get()) {
                return;
            }
            int preferred = -1;
            if (itemProfiles.get(PROFILE_CRYSTAL) && crystalWhenSafe.get() && isSafeForCrystal(player)) {
                preferred = findUsable(player, stack -> stack.is(Items.END_CRYSTAL));
            }
            if (preferred < 0 && safeOffhand.get() == SafeOffhand.TOTEM
                    && itemProfiles.get(PROFILE_TOTEM)) {
                preferred = findTotemSlot(player);
            } else if (preferred < 0 && safeOffhand.get() != SafeOffhand.KEEP
                    && safeOffhand.get() != SafeOffhand.TOTEM) {
                preferred = preferredSafeOffhand(player);
            }
            if (preferred >= 0) {
                ItemStack wanted = player.getInventory().getItem(preferred);
                if (!ItemStack.isSameItemSameComponents(player.getOffhandItem(), wanted)) {
                    requestOffhandSwap(preferred, null);
                }
            }
            return;
        }

        int totemSlot = findTotemSlot(player);
        if (isItemUsable(player, Items.TOTEM_OF_UNDYING.getDefaultInstance())
                && (isTotemInOffhand(player) || totemSlot != -1)) {
            if (!isTotemInOffhand(player) && totemSlot != -1) {
                ItemStack display = player.getInventory().getItem(totemSlot).copy();
                requestOffhandSwap(totemSlot, () -> Itemizer.showAutoTotem(display), true);
            }
            return;
        }

        if (itemProfiles.get(PROFILE_SHIELD) && fallbacks.get(FALLBACK_SHIELD)) {
            int shield = findUsable(player, stack -> stack.is(Items.SHIELD) && shieldHealthy(stack));
            if (shield != -1) {
                if (!player.getOffhandItem().is(Items.SHIELD)) requestOffhandSwap(shield, null);
                return;
            }
        }
        if (itemProfiles.get(PROFILE_CRYSTAL) && fallbacks.get(FALLBACK_CRYSTAL) && isCrystalContext()) {
            int crystal = findUsable(player, stack -> stack.is(Items.END_CRYSTAL));
            if (crystal != -1 && !player.getOffhandItem().is(Items.END_CRYSTAL)) {
                requestOffhandSwap(crystal, null);
            }
        }
    }

    private int preferredSafeOffhand(LocalPlayer player) {
        return switch (safeOffhand.get()) {
            case KEEP, TOTEM -> -1;
            case SHIELD -> itemProfiles.get(PROFILE_SHIELD)
                    ? findUsable(player, stack -> stack.is(Items.SHIELD) && shieldHealthy(stack)) : -1;
            case CRYSTAL -> itemProfiles.get(PROFILE_CRYSTAL) && isSafeForCrystal(player)
                    ? findUsable(player, stack -> stack.is(Items.END_CRYSTAL)) : -1;
            case GAPPLE -> {
                ItemChoice choice = useGapples.get() && effectiveGappleHealth(player) <= gappleHealth.get()
                        ? findBestGapple(player) : null;
                yield choice == null ? -1 : choice.slot();
            }
            case FOOD -> {
                ItemChoice choice = player.getFoodData().getFoodLevel() <= hungerThreshold.get()
                        && itemProfiles.get(PROFILE_FOOD)
                        ? findBestFood(player, 20 - player.getFoodData().getFoodLevel(), false, true) : null;
                yield choice == null ? -1 : choice.slot();
            }
            case CONTEXTUAL -> {
                int slot = isCrystalContext() && itemProfiles.get(PROFILE_CRYSTAL)
                        && isSafeForCrystal(player)
                        ? findUsable(player, stack -> stack.is(Items.END_CRYSTAL)) : -1;
                if (slot == -1 && isPvpContext(player) && itemProfiles.get(PROFILE_SHIELD)) {
                    slot = findUsable(player, stack -> stack.is(Items.SHIELD) && shieldHealthy(stack));
                }
                yield slot;
            }
        };
    }

    private void updateSuggestion(LocalPlayer player, boolean danger) {
        suggestedReady = autoConsume.get() && consumeControl.get() != ConsumeControl.DISABLED
                && Itemizer.isActionEnabled(name(), ACTION_CONSUME)
                && canOfferConsumption(player, danger);
        ItemChoice choice = suggestedReady ? selectConsumable(player, danger) : null;
        if (choice != null) suggestedStack = choice.stack();
        else if (!forcingUse && !offhandConsumeActive && !semiConsumeArmed
                && player.tickCount >= consumeDisplayUntilTick) suggestedStack = ItemStack.EMPTY;
    }

    private void clearSuggestion() {
        suggestedReady = false;
        suggestedStack = ItemStack.EMPTY;
    }

    private boolean canOfferConsumption(LocalPlayer player, boolean danger) {
        if (player == null || mc.level == null || mc.gameMode == null || mc.isPaused()
                || player.isSpectator() || player.getAbilities().instabuild
                || danger && consumeHand.get() != ConsumeHand.MAIN_HAND
                || player.tickCount < consumeLockedUntilTick || player.isUsingItem()
                || offhandSwapPending || restoreInventoryPending) return false;
        boolean combat = isPvpContext(player);
        if (combat && (consumeCombat.get() == CombatConsume.OUT_OF_COMBAT
                || consumeCombat.get() == CombatConsume.EMERGENCY_ONLY
                && effectiveHealth(player) > foodEmergencyHealth.get())) return false;
        return selectConsumable(player, danger) != null;
    }

    private void updateConsumption(LocalPlayer player, boolean danger) {
        if (player.isSpectator() || player.getAbilities().instabuild) {
            stopAutoUse(player);
            return;
        }
        if (player.isUsingItem()) {
            if (forcingUse && FoodUtil.isFood(player.getUseItem())) {
                useWasObserved = true;
                lastUseObservedTick = player.tickCount;
                return;
            }
            if (forcingUse) stopAutoUse(player);
            return;
        }
        if (consumeRequestTick >= 0 && !useWasObserved && player.tickCount - consumeRequestTick <= 10) return;
        if (useActionQueued) return;
        if (player.tickCount < consumeLockedUntilTick) {
            if (forcingUse) stopAutoUse(player);
            return;
        }
        boolean combat = isPvpContext(player);
        if (combat && (consumeCombat.get() == CombatConsume.OUT_OF_COMBAT
                || (consumeCombat.get() == CombatConsume.EMERGENCY_ONLY
                    && effectiveHealth(player) > foodEmergencyHealth.get()))) {
            if (forcingUse) stopAutoUse(player);
            return;
        }
        ItemChoice choice = selectConsumable(player, danger);
        if (choice == null) {
            if (forcingUse) stopAutoUse(player);
            offhandConsumeActive = false;
            semiConsumeArmed = false;
            return;
        }
        if (danger && consumeHand.get() != ConsumeHand.MAIN_HAND) {
            offhandConsumeActive = false;
            semiConsumeArmed = false;
            return;
        }
        boolean useOffhand = !danger && (consumeHand.get() == ConsumeHand.OFF_HAND
                || consumeHand.get() == ConsumeHand.AUTO && !combat);
        if (useOffhand) {
            if (!choice.matches(player.getOffhandItem())) {
                if (!offhandConsumeActive && choice.slot() >= 0) {
                    offhandConsumeActive = requestOffhandSwap(choice.slot(), null);
                }
                return;
            }
            offhandConsumeActive = true;
            if (!isItemUsable(player, player.getOffhandItem())) return;
            queueUse(player.getOffhandItem().copy(), InteractionHand.OFF_HAND);
            return;
        }
        if (offhandConsumeActive) {
            offhandConsumeActive = false;
            return;
        }
        if (restoreInventoryPending && expectedMainhand.isEmpty() && !choice.matches(player.getMainHandItem())) {
            stopAutoUse(player);
            return;
        }
        MainhandState mainhandState = ensureMainhand(player, choice.slot(), choice.stack());
        if (mainhandState == MainhandState.PENDING) return;
        if (mainhandState == MainhandState.FAILED) {
            stopAutoUse(player);
            return;
        }
        ItemStack active = player.getMainHandItem();
        if (!choice.matches(active) || !isItemUsable(player, active)) {
            stopAutoUse(player);
            return;
        }
        queueUse(active.copy(), InteractionHand.MAIN_HAND);
    }

    private ItemChoice selectConsumable(LocalPlayer player, boolean danger) {
        if (useGapples.get() && player.tickCount >= appleAbsorptionGuardUntilTick
                && effectiveGappleHealth(player) <= gappleHealth.get()
                && (player.getAbsorptionAmount() < 4.0f || effectiveHealth(player) <= 6.0f)) {
            ItemChoice gapple = findBestGapple(player);
            if (gapple != null) return gapple;
        }

        if (!useFood.get() || !itemProfiles.get(PROFILE_FOOD)) return null;
        int hunger = player.getFoodData().getFoodLevel();
        boolean emergency = effectiveHealth(player) <= foodEmergencyHealth.get();
        if (hunger > hungerThreshold.get() && !emergency) return null;

        int missing = 20 - hunger;
        return findBestFood(player, missing, emergency, eatMode.get() == EatMode.EXACT);
    }

    private ItemChoice findBestGapple(LocalPlayer player) {
        ItemChoice best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isGapple(stack) || !isItemUsable(player, stack)) continue;
            if (stack.is(Items.GOLDEN_APPLE) && !itemProfiles.get(PROFILE_GAPPLE)) continue;
            if (stack.is(Items.ENCHANTED_GOLDEN_APPLE) && !itemProfiles.get(PROFILE_ENCHANTED)) continue;
            int score = stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                    ? (effectiveHealth(player) <= 6.0f ? 220 : 80) : 150;
            if (i < 9) score += 10;
            if (score > bestScore) {
                bestScore = score;
                best = new ItemChoice(i, stack.copy(), ChoiceKind.GAPPLE);
            }
        }
        ItemStack main = player.getMainHandItem();
        if (isGapple(main) && isItemUsable(player, main)
                && (main.is(Items.GOLDEN_APPLE) && itemProfiles.get(PROFILE_GAPPLE)
                || main.is(Items.ENCHANTED_GOLDEN_APPLE) && itemProfiles.get(PROFILE_ENCHANTED))) {
            int score = main.is(Items.ENCHANTED_GOLDEN_APPLE)
                    ? (effectiveHealth(player) <= 6.0f ? 240 : 90) : 160;
            if (score >= bestScore) best = new ItemChoice(selectedSlot(player), main.copy(), ChoiceKind.GAPPLE);
        }
        ItemStack offhand = player.getOffhandItem();
        if (isGapple(offhand) && isItemUsable(player, offhand)) {
            int score = offhand.is(Items.ENCHANTED_GOLDEN_APPLE) ? 70 : 170;
            if ((offhand.is(Items.GOLDEN_APPLE) && itemProfiles.get(PROFILE_GAPPLE)
                    || offhand.is(Items.ENCHANTED_GOLDEN_APPLE) && itemProfiles.get(PROFILE_ENCHANTED))
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

    private MainhandState ensureMainhand(LocalPlayer player, int slot, ItemStack wanted) {
        if (wanted == null || wanted.isEmpty()) return MainhandState.FAILED;

        if (!expectedMainhand.isEmpty()) {
            if (ItemStack.isSameItemSameComponents(player.getMainHandItem(), expectedMainhand)) {
                if (player.tickCount < mainhandReadyTick) return MainhandState.PENDING;
                expectedMainhand = ItemStack.EMPTY;
                mainhandReadyTick = -1;
                mainhandDeadlineTick = -1;
                return MainhandState.READY;
            }
            if (player.tickCount <= mainhandDeadlineTick) return MainhandState.PENDING;
            expectedMainhand = ItemStack.EMPTY;
            mainhandReadyTick = -1;
            mainhandDeadlineTick = -1;
            return MainhandState.FAILED;
        }

        if (ItemStack.isSameItemSameComponents(player.getMainHandItem(), wanted)) return MainhandState.READY;
        if (slot < 0 || slot >= 36) return MainhandState.FAILED;

        int selected = InventorySwap.INSTANCE.clientSelectedSlot();
        if (slot < 9) {
            if (returnSlot.get() && !restoreSelected && selected != slot) {
                previousSelectedSlot = selected;
                restoreSelected = true;
            }
            if (!InventorySwap.INSTANCE.selectHotbar(slot)) return MainhandState.FAILED;
            autoSelectedSlot = slot;
            expectedMainhand = wanted.copy();
            mainhandReadyTick = player.tickCount + INVENTORY_CONFIRM_TICKS;
            mainhandDeadlineTick = player.tickCount + INVENTORY_TIMEOUT_TICKS;
            return MainhandState.PENDING;
        }

        if (restoreInventoryPending) return MainhandState.PENDING;
        if (!InventorySwap.INSTANCE.leaseInventory(this, 80)) return MainhandState.PENDING;

        int targetHotbar = findEmptyHotbarSlot(player);
        if (targetHotbar == -1) targetHotbar = selected;
        if (returnSlot.get() && !restoreSelected && targetHotbar != selected) {
            previousSelectedSlot = selected;
            restoreSelected = true;
        }

        int sourceScreen = InventorySwap.mapInventoryToScreenSlot(slot);
        int targetScreen = InventorySwap.mapHotbarToScreenSlot(targetHotbar);
        if (sourceScreen < 0 || targetScreen < 0) {
            InventorySwap.INSTANCE.releaseInventory(this);
            return MainhandState.FAILED;
        }

        restoreInventorySlot = slot;
        restoreHotbarSlot = targetHotbar;
        restoreInventoryPending = true;
        expectedMainhand = wanted.copy();
        mainhandReadyTick = player.tickCount + INVENTORY_CONFIRM_TICKS;
        mainhandDeadlineTick = player.tickCount + INVENTORY_TIMEOUT_TICKS;

        if (!InventorySwap.INSTANCE.swapScreenSlots(sourceScreen, targetScreen, InventorySwapPolicy.NONE, this)) {
            restoreInventoryPending = false;
            restoreInventorySlot = -1;
            restoreHotbarSlot = -1;
            expectedMainhand = ItemStack.EMPTY;
            mainhandReadyTick = -1;
            mainhandDeadlineTick = -1;
            InventorySwap.INSTANCE.releaseInventory(this);
            return MainhandState.PENDING;
        }
        if (!InventorySwap.INSTANCE.selectHotbar(targetHotbar)) {
            expectedMainhand = ItemStack.EMPTY;
            mainhandReadyTick = -1;
            mainhandDeadlineTick = -1;
            return MainhandState.FAILED;
        }
        autoSelectedSlot = targetHotbar;
        return MainhandState.PENDING;
    }

    private void queueUse(ItemStack display, InteractionHand hand) {
        if (useActionQueued || display.isEmpty()) return;
        useActionQueued = true;
        consumingStack = display.copy();
        boolean accepted = InventorySwap.INSTANCE.enqueueInventoryAction(this, InventoryActionKind.USE_ITEM,
                false,
                () -> {
                    LocalPlayer player = mc.player;
                    return isEnabled() && player != null && !player.isUsingItem()
                            && player.tickCount >= consumeLockedUntilTick
                            && autoConsume.get() && Itemizer.isActionEnabled(name(), ACTION_CONSUME)
                            && consumeControl.get() != ConsumeControl.DISABLED
                            && (consumeControl.get() == ConsumeControl.AUTO || semiConsumeArmed)
                            && !offhandSwapPending && !restoreInventoryPending
                            && ItemStack.isSameItemSameComponents(
                                    hand == InteractionHand.OFF_HAND ? player.getOffhandItem() : player.getMainHandItem(),
                                    display)
                            && (!shouldHoldTotem(player, effectiveHealth(player))
                                || hand == InteractionHand.MAIN_HAND);
                },
                () -> {
                    useActionQueued = false;
                    LocalPlayer player = mc.player;
                    if (player != null) startUse(player, display, hand);
                },
                () -> useActionQueued = false);
        if (!accepted) useActionQueued = false;
    }

    private void startUse(LocalPlayer player, ItemStack display, InteractionHand hand) {
        if (consumeRequestTick >= 0 && player.tickCount - consumeRequestTick <= 10) return;
        forcingUse = true;
        lastUseObservedTick = player.tickCount;
        InteractionResult result = InventorySwap.INSTANCE.useItem(hand);
        if (result.consumesAction()) {
            consumeRequestTick = player.tickCount;
            consumingStack = display.copy();
            consumeDisplayUntilTick = player.tickCount + 16;
            player.swing(hand);
            gappleConsumption = isGapple(display);
            gappleWasEnchanted = display.is(Items.ENCHANTED_GOLDEN_APPLE);
            if (gappleConsumption) appleAbsorptionGuardUntilTick = Math.max(
                    appleAbsorptionGuardUntilTick, player.tickCount + (gappleWasEnchanted ? 160 : 80));
            Itemizer.showAutoEat(display);
        } else {
            stopAutoUse(player);
        }
    }

    private void stopAutoUse(LocalPlayer player) {
        stopAutoUse(player, false);
    }

    private void stopAutoUse(LocalPlayer player, boolean forceRestore) {
        forcingUse = false;
        if (useActionQueued) {
            InventorySwap.INSTANCE.cancelQueuedInventoryActions(this, InventoryActionKind.USE_ITEM);
            useActionQueued = false;
        }
        offhandConsumeActive = false;

        if ((restoreInventoryPending || restoreSelected) && !forceRestore) {
            if (lastUseObservedTick >= 0 && player.tickCount <= lastUseObservedTick + 1) return;
            if (restoreActionQueued) return;
            restoreActionQueued = true;
            boolean accepted = InventorySwap.INSTANCE.enqueueInventoryAction(this,
                    InventoryActionKind.INVENTORY_CLICK, false,
                    () -> isEnabled() && mc.player != null,
                    () -> {
                        restoreActionQueued = false;
                        LocalPlayer current = mc.player;
                        if (current != null) stopAutoUse(current, true);
                    },
                    () -> restoreActionQueued = false);
            if (!accepted) restoreActionQueued = false;
            return;
        }
        if (forceRestore && restoreActionQueued) {
            InventorySwap.INSTANCE.cancelQueuedInventoryActions(this, InventoryActionKind.INVENTORY_CLICK);
            restoreActionQueued = false;
        }

        if (restoreInventoryPending) {
            int source = InventorySwap.mapInventoryToScreenSlot(restoreInventorySlot);
            int target = InventorySwap.mapHotbarToScreenSlot(restoreHotbarSlot);
            if (source < 0 || target < 0
                    || !InventorySwap.INSTANCE.swapScreenSlots(source, target, InventorySwapPolicy.NONE, this)) {
                return;
            }
            restoreInventoryPending = false;
            restoreInventorySlot = -1;
            restoreHotbarSlot = -1;
            inventoryQuietUntilTick = Math.max(inventoryQuietUntilTick, player.tickCount + INVENTORY_CONFIRM_TICKS);
            scheduleInventoryLeaseRelease(player, INVENTORY_CONFIRM_TICKS);
        }

        if (restoreSelected) {
            int current = InventorySwap.INSTANCE.clientSelectedSlot();
            if (previousSelectedSlot >= 0 && (autoSelectedSlot < 0 || current == autoSelectedSlot)) {
                InventorySwap.INSTANCE.selectHotbar(previousSelectedSlot);
            }
            restoreSelected = false;
            previousSelectedSlot = -1;
        }
        autoSelectedSlot = -1;
        lastUseObservedTick = -1;
    }

    private void resetConsumeState() {
        forcingUse = false;
        restoreSelected = false;
        previousSelectedSlot = -1;
        restoreInventoryPending = false;
        restoreInventorySlot = -1;
        restoreHotbarSlot = -1;
        expectedMainhand = ItemStack.EMPTY;
        mainhandReadyTick = -1;
        mainhandDeadlineTick = -1;
        lastUseObservedTick = -1;
        useActionQueued = false;
        restoreActionQueued = false;
        InventorySwap.INSTANCE.cancelQueuedInventoryActions(this);
        autoSelectedSlot = -1;
        consumeRequestTick = -1;
        consumingStack = ItemStack.EMPTY;
        consumeDisplayUntilTick = -1;
        appleAbsorptionGuardUntilTick = -1;
        if (!offhandSwapPending) InventorySwap.INSTANCE.releaseInventory(this);
        inventoryLeaseReleaseTick = -1;
    }

    private boolean shouldHoldTotem(LocalPlayer player, float health) {
        if (player.tickCount <= forcedTotemUntilTick) return true;
        if (player.isFallFlying() && health <= elytraHealth.get()) return true;
        if (health <= healthThreshold.get()) return true;
        if (fallCheck.get() && player.fallDistance > 10.0f) return true;
        if (getClosestCrystalDistance(player) <= crystalDistance.get()) return true;
        if (threats.get(THREAT_BROKEN_ARMOR) && hasBrokenArmor(player) && hasNearbyCombatPlayer(player, 10.0)) return true;
        if (threats.get(THREAT_MACE) && hasMaceThreat(player)) return true;
        return threats.get(THREAT_PROJECTILES) && hasProjectileThreat(player);
    }

    /**
     * Safe enough to trade the totem for a crystal. The normal danger check treats any crystal within
     * a few blocks as a threat, which is every tick of a crystal fight, so this asks how much the
     * crystals actually there would deal instead. Everything else that forces a totem still does.
     */
    private boolean isSafeForCrystal(LocalPlayer player) {
        if (!isSafeForCrystalNow(player)) {
            crystalUnsafeUntilTick = player.tickCount + CRYSTAL_SAFE_HOLD_TICKS;
            return false;
        }
        return player.tickCount > crystalUnsafeUntilTick;
    }

    private boolean isSafeForCrystalNow(LocalPlayer player) {
        if (player.tickCount <= forcedTotemUntilTick) return false;
        float health = effectiveHealth(player);
        if (health <= Math.max(healthThreshold.get(), crystalSafeHealth.get())) return false;
        if (player.isFallFlying() && health <= elytraHealth.get()) return false;
        if (fallCheck.get() && player.fallDistance > 3.0f) return false;
        if (threats.get(THREAT_BROKEN_ARMOR) && hasBrokenArmor(player) && hasNearbyCombatPlayer(player, 10.0)) return false;
        if (threats.get(THREAT_MACE) && hasMaceThreat(player)) return false;
        if (threats.get(THREAT_PROJECTILES) && hasProjectileThreat(player)) return false;
        return health - worstCrystalDamage(player) > crystalSafeHealth.get();
    }

    private float worstCrystalDamage(LocalPlayer player) {
        float worst = 0.0f;
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(CRYSTAL_DAMAGE_RANGE),
                crystal -> crystal != null && !crystal.isRemoved()
        )) {
            worst = Math.max(worst, ExplosionDamageUtil.getCrystalDamage(player, crystal.position(), 0, false));
        }
        return worst;
    }

    private boolean isItemUsable(LocalPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (player.getCooldowns().isOnCooldown(stack)) return false;
        Item item = stack.getItem();
        if (CooldownsState.MANAGER.isCooling(item)) return false;
        PvpCooldowns pvp = Modules.get(PvpCooldowns.class);
        return pvp == null || !pvp.shouldBlockItemUse(item);
    }

    private boolean isPvpContext(LocalPlayer player) {
        return CooldownsState.MANAGER.isInPvp() || hasNearbyCombatPlayer(player, 8.0);
    }

    private boolean isCrystalContext() {
        AutoCrystal crystal = Modules.get(AutoCrystal.class);
        return crystal != null && crystal.isEnabled() && CooldownsState.MANAGER.isInPvp();
    }

    private boolean shieldHealthy(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.SHIELD)) return false;
        return !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 1;
    }

    private void clearManagedOffhandIfUseless(LocalPlayer player) {
        ItemStack off = player.getOffhandItem();
        if (!off.is(Items.SHIELD) && !off.is(Items.END_CRYSTAL) && !off.is(Items.TOTEM_OF_UNDYING)) return;
        if (off.is(Items.TOTEM_OF_UNDYING) && isItemUsable(player, off)) return;
        if (off.is(Items.SHIELD) && fallbacks.get(FALLBACK_SHIELD) && isPvpContext(player) && shieldHealthy(off) && isItemUsable(player, off)) return;
        if (off.is(Items.END_CRYSTAL) && fallbacks.get(FALLBACK_CRYSTAL) && isCrystalContext() && isItemUsable(player, off)) return;
        if (off.is(Items.END_CRYSTAL) && crystalWhenSafe.get()) return;
        int empty = find(player, ItemStack::isEmpty);
        if (empty != -1) requestOffhandSwap(empty, null);
    }

    private boolean requestOffhandSwap(int slot, Runnable afterSwap) {
        return requestOffhandSwap(slot, afterSwap, false);
    }

    private boolean requestOffhandSwap(int slot, Runnable afterSwap, boolean urgent) {
        if (offhandSwapPending || slot < 0 || slot >= 36 || mc.player == null) return false;
        if (!InventorySwap.INSTANCE.leaseInventory(this, 24)) return false;
        ItemStack wanted = mc.player.getInventory().getItem(slot);
        if (wanted == null) {
            InventorySwap.INSTANCE.releaseInventory(this);
            return false;
        }
        offhandSwapPending = true;
        expectedOffhand = wanted.copy();
        expectOffhandEmpty = wanted.isEmpty();
        offhandSwapIssuedTick = -1;
        offhandSwapDeadlineTick = -1;
        offhandSwapConfirmedAction = afterSwap;

        boolean accepted = InventorySwap.INSTANCE.enqueueInventoryAction(this,
                InventoryActionKind.INVENTORY_CLICK, urgent,
                () -> {
                    LocalPlayer player = mc.player;
                    return isEnabled() && player != null && offhandSwapPending
                            && player.getInventory().getItem(slot) != null
                            && ItemStack.isSameItemSameComponents(player.getInventory().getItem(slot), expectedOffhand)
                            && (urgent ? shouldHoldTotem(player, effectiveHealth(player))
                                       : !shouldHoldTotem(player, effectiveHealth(player)));
                },
                () -> {
                    boolean sent = InventorySwap.INSTANCE.swapInventoryToOffhand(
                            slot, InventorySwapPolicy.NONE, this, null);
                    LocalPlayer player = mc.player;
                    if (sent && player != null) {
                        offhandSwapIssuedTick = player.tickCount;
                        offhandSwapDeadlineTick = player.tickCount + INVENTORY_TIMEOUT_TICKS;
                    } else {
                        resetOffhandSwapState();
                        if (!restoreInventoryPending) InventorySwap.INSTANCE.releaseInventory(this);
                    }
                },
                () -> {
                    if (offhandSwapPending && offhandSwapIssuedTick < 0) {
                        resetOffhandSwapState();
                        if (!restoreInventoryPending) InventorySwap.INSTANCE.releaseInventory(this);
                    }
                });
        if (!accepted) {
            resetOffhandSwapState();
            if (!restoreInventoryPending) InventorySwap.INSTANCE.releaseInventory(this);
        }
        return accepted;
    }

    private boolean updatePendingOffhandSwap(LocalPlayer player) {
        if (!offhandSwapPending) return false;
        if (player == null) {
            resetOffhandSwapState();
            return false;
        }

        if (offhandSwapIssuedTick < 0) {
            if (!InventorySwap.INSTANCE.hasQueuedInventoryAction(this, InventoryActionKind.INVENTORY_CLICK)) {
                resetOffhandSwapState();
                if (!restoreInventoryPending) InventorySwap.INSTANCE.releaseInventory(this);
                return false;
            }
            return true;
        }

        boolean confirmedItem = expectOffhandEmpty
                ? player.getOffhandItem().isEmpty()
                : !expectedOffhand.isEmpty() && ItemStack.isSameItemSameComponents(player.getOffhandItem(), expectedOffhand);
        if (offhandSwapIssuedTick >= 0
                && player.tickCount >= offhandSwapIssuedTick + INVENTORY_CONFIRM_TICKS
                && confirmedItem) {
            Runnable confirmed = offhandSwapConfirmedAction;
            resetOffhandSwapState();
            inventoryQuietUntilTick = player.tickCount + INVENTORY_CONFIRM_TICKS;
            if (!restoreInventoryPending) scheduleInventoryLeaseRelease(player, 1);
            if (confirmed != null) confirmed.run();
            return false;
        }

        if (player.tickCount > offhandSwapDeadlineTick) {
            resetOffhandSwapState();
            offhandConsumeActive = false;
            inventoryQuietUntilTick = player.tickCount;
            if (!restoreInventoryPending) InventorySwap.INSTANCE.releaseInventory(this);
            return false;
        }
        return true;
    }

    private void scheduleInventoryLeaseRelease(LocalPlayer player, int delayTicks) {
        if (player == null) return;
        inventoryLeaseReleaseTick = Math.max(inventoryLeaseReleaseTick, player.tickCount + Math.max(0, delayTicks));
    }

    private void releaseInventoryLeaseIfDue(LocalPlayer player) {
        if (player == null || inventoryLeaseReleaseTick < 0 || player.tickCount <= inventoryLeaseReleaseTick) return;
        if (!offhandSwapPending && !restoreInventoryPending) {
            InventorySwap.INSTANCE.releaseInventory(this);
            inventoryLeaseReleaseTick = -1;
        }
    }

    private void resetOffhandSwapState() {
        offhandSwapPending = false;
        expectedOffhand = ItemStack.EMPTY;
        expectOffhandEmpty = false;
        offhandSwapIssuedTick = -1;
        offhandSwapDeadlineTick = -1;
        offhandSwapConfirmedAction = null;
    }

    private int findTotemSlot(LocalPlayer player) {
        if (!isItemUsable(player, Items.TOTEM_OF_UNDYING.getDefaultInstance())) return -1;
        if (saveTaliks.get()) {
            int plain = find(player, stack -> stack.is(Items.TOTEM_OF_UNDYING) && !stack.isEnchanted());
            if (plain != -1) return plain;
        }
        return find(player, Items.TOTEM_OF_UNDYING);
    }

    private int findUsable(LocalPlayer player, Predicate<ItemStack> predicate) {
        return find(player, stack -> predicate.test(stack) && isItemUsable(player, stack));
    }

    private int find(LocalPlayer player, Item item) {
        return find(player, stack -> stack.is(item));
    }

    private int find(LocalPlayer player, Predicate<ItemStack> predicate) {
        if (player == null || predicate == null) return -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (predicate.test(stack)) return i;
        }
        return -1;
    }

    private int findEmptyHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) if (player.getInventory().getItem(i).isEmpty()) return i;
        return -1;
    }

    private int selectedSlot(LocalPlayer player) {
        return ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
    }

    private float effectiveHealth(LocalPlayer player) {
        return player.getHealth() + player.getAbsorptionAmount();
    }

    private float effectiveGappleHealth(LocalPlayer player) {
        return player.getHealth() + player.getAbsorptionAmount();
    }

    private boolean isGapple(ItemStack stack) {
        return stack != null && (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE));
    }

    private boolean hasBrokenArmor(LocalPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                || player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private boolean hasNearbyCombatPlayer(LocalPlayer player, double range) {
        double rangeSq = range * range;
        for (Player other : mc.level.players()) {
            if (!isCombatThreatPlayer(player, other)) continue;
            if (other.distanceToSqr(player) <= rangeSq) return true;
        }
        return false;
    }

    private boolean hasMaceThreat(LocalPlayer player) {
        for (Player other : mc.level.players()) {
            if (!isCombatThreatPlayer(player, other)) continue;
            if (!other.getMainHandItem().is(Items.MACE) && !other.getOffhandItem().is(Items.MACE)) continue;
            if (other.getY() <= player.getY()) continue;
            if (other.distanceToSqr(player) > 49.0) continue;
            if (other.getDeltaMovement().y < 0.0 && other.fallDistance > 3.0f) return true;
        }
        return false;
    }

    private boolean isCombatThreatPlayer(LocalPlayer self, Player other) {
        if (other == null || other == self || !other.isAlive() || other.isSpectator()) return false;
        CategoryType type = CategoryRules.determine(other.getGameProfile().name());
        return type != CategoryType.FRIEND && type != CategoryType.STAFF && type != CategoryType.BEDWARS_SELF;
    }

    private boolean hasProjectileThreat(LocalPlayer player) {
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Projectile projectile)) continue;
            if (projectile.getOwner() == player) continue;
            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() < 1.0E-7) continue;
            Vec3 from = projectile.position();
            Vec3 toPlayer = player.getBoundingBox().getCenter().subtract(from);
            if (velocity.dot(toPlayer) <= 0.0) continue;
            Vec3 direction = velocity.normalize();
            double along = toPlayer.dot(direction);
            if (along < 0.0 || along > 16.0) continue;
            Vec3 closest = from.add(direction.scale(along));
            if (TargetingUtil.distanceToBoxSq(closest, player.getBoundingBox().inflate(0.35)) <= 0.09) return true;
        }
        return false;
    }

    private double getClosestCrystalDistance(LocalPlayer player) {
        double minDist = Double.MAX_VALUE;
        double range = crystalDistance.get();
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(range),
                crystal -> crystal != null && !crystal.isRemoved()
        )) {
            double dist = player.position().distanceTo(crystal.position());
            if (dist < minDist) minDist = dist;
        }
        return minDist;
    }

    private boolean isTotemInOffhand(LocalPlayer player) {
        return player != null && player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private enum SafeOffhand implements EnumValue.IdProvider {
        KEEP("keep"), TOTEM("totem"), CONTEXTUAL("contextual"), SHIELD("shield"),
        CRYSTAL("crystal"), GAPPLE("gapple"), FOOD("food");
        private final String id;
        SafeOffhand(String id) { this.id = id; }
        @Override public String id() { return id; }
    }

    private enum ConsumeControl implements EnumValue.IdProvider {
        AUTO("auto"), SEMI("semi"), DISABLED("disabled");
        private final String id;
        ConsumeControl(String id) { this.id = id; }
        @Override public String id() { return id; }
    }

    private enum ConsumeHand implements EnumValue.IdProvider {
        MAIN_HAND("main_hand"), OFF_HAND("off_hand"), AUTO("auto");
        private final String id;
        ConsumeHand(String id) { this.id = id; }
        @Override public String id() { return id; }
    }

    private enum CombatConsume implements EnumValue.IdProvider {
        OUT_OF_COMBAT("out_of_combat"), EMERGENCY_ONLY("emergency_only"), ALLOW("allow");
        private final String id;
        CombatConsume(String id) { this.id = id; }
        @Override public String id() { return id; }
    }

    private enum ChoiceKind { GAPPLE, FOOD }

    private enum MainhandState {
        READY,
        PENDING,
        FAILED
    }

    private record ItemChoice(int slot, ItemStack stack, ChoiceKind kind) {
        private boolean matches(ItemStack other) {
            if (other == null || other.isEmpty()) return false;
            return switch (kind) {
                case GAPPLE -> other.is(Items.GOLDEN_APPLE) || other.is(Items.ENCHANTED_GOLDEN_APPLE);
                case FOOD -> FoodUtil.isFood(other) && !other.is(Items.GOLDEN_APPLE) && !other.is(Items.ENCHANTED_GOLDEN_APPLE);
            };
        }
    }

    @Getter
    @RequiredArgsConstructor
    private enum EatMode implements EnumValue.IdProvider {
        INSTANT("instant"),
        EXACT("exact");

        private final String id;
    }
}
