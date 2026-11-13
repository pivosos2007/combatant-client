/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.EventSync;
import combatant.client.events.impl.ManualAttackEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.NoFall;
import combatant.client.features.module.modules.player.Offhand;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.click.AttackPressing;
import combatant.client.util.combat.AttackUtil;
import combatant.client.util.combat.SmashTrajectory;
import combatant.client.util.combat.protocol.CombatProtocolHeuristics;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.player.ResetAttackCooldown;
import combatant.client.util.player.ElytraTargetReachability;
import combatant.client.util.logging.ElytraDiveTrace;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapRequest;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySlotLocks;
import combatant.client.util.player.inventory.PacketWindow;
import combatant.client.util.combat.protocol.ProtocolUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        id = "attributeswap",
        displayName = "AttributeSwap",
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.ATTACK,
        description = "module.attributeswap.description")
public class AttributeSwap extends Module {

    private static final int HOTBAR_RESET_TICKS = 0;
    private static final double MIN_MEANINGFUL_MACE_FALL_DISTANCE = 1.6;
    private static final long FAST_AURA_MACE_DELAY_MIN_MS = 140L;
    private static final long FAST_AURA_MACE_DELAY_MAX_MS = 230L;
    private static final int CHEST_SCREEN_SLOT = 6;
    private static final int CYCLE_NONE = 0;
    private static final int CYCLE_WAIT_CHEST_SWAP = 1;
    private static final int CYCLE_WAIT_MACE_SYNC = 2;
    private static final int CYCLE_WAIT_ELYTRA_RESTORE = 3;
    private static final int TARGET_DIVE_TIMEOUT_TICKS = 30;
    private static final int WIND_BURST_ABSORPTION_MIN_TICKS = 200;
    private static final int WIND_BURST_PREDICTION_TICKS = 140;
    private static final double WIND_BURST_EXPLOSION_RADIUS = 3.5;
    private static final double WIND_BURST_SWEEP_MARGIN = 0.04;
    private static final String ACTION_SHIELD_BREAKER = "shield_breaker";
    private static final String ACTION_HEIGHT_MACE = "height_mace";
    private static final String ACTION_BEST_DAMAGE_WEAPON = "best_damage_weapon";
    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanMapValue actions =
            group(
                    "attributeSwapActions",
                    "actions",
                    actionDefaults()
            );
    private final BooleanMapValue protocolHeuristicSources =
            group(
                    "attributeSwapProtocolHeuristics",
                    "protocol_heuristics",
                    CombatProtocolHeuristics.defaultSourceToggles()
            );
    private final EnumValue<BestDamageWeaponMode> bestDamageWeaponMode =
            visibleWhen(enumSetting(
                    "attributeSwapBestDamageWeaponMode",
                    "best_damage_weapon_mode",
                    BestDamageWeaponMode.BOTH,
                    BestDamageWeaponMode.values()
            ), this::isBestDamageWeaponEnabled);
    private final BooleanValue onlyPlayers =
            boolCommon(
                    "attributeSwapOnlyPlayers",
                    "only_players",
                    CommonSettingSchemas.COMBAT_ONLY_PLAYERS,
                    true
            );
    private final NumberValue<Double> minFallDistance =
            visibleWhen(numCommon(
                    "attributeSwapMinFallDistance",
                    "min_fall_distance",
                    CommonSettingSchemas.COMBAT_MIN_FALL_DISTANCE,
                    2.0,
                    MIN_MEANINGFUL_MACE_FALL_DISTANCE,
                    20.0
            ), this::isHeightMaceEnabled);
    private final BooleanValue restoreItem =
            boolCommon(
                    "attributeSwapRestoreItem",
                    "restore_item",
                    CommonSettingSchemas.INVENTORY_RESTORE_ITEM,
                    true
            );
    private final BooleanValue autoElytraCycle =
            visibleWhen(bool(
                    "attributeSwapAutoElytraCycle",
                    "auto_elytra_cycle",
                    true
            ), this::isHeightMaceEnabled);
    private final BooleanValue autoGlideAfterRestore =
            visibleWhen(bool(
                    "attributeSwapAutoGlideAfterRestore",
                    "auto_glide_after_restore",
                    false
            ), this::isAutoElytraCycleEnabled);
    private final NumberValue<Integer> autoGlideDelay =
            visibleWhen(num(
                    "attributeSwapAutoGlideDelay",
                    "auto_glide_delay",
                    2,
                    1,
                    10
            ), () -> isAutoElytraCycleEnabled() && autoGlideAfterRestore.get());
    private final NumberValue<Double> minDownVelocity =
            visibleWhen(num(
                    "attributeSwapMinDownVelocity",
                    "min_down_velocity",
                    0.08,
                    0.01,
                    1.0
            ), this::isAutoElytraCycleEnabled);
    private final NumberValue<Double> minApproachSpeed =
            visibleWhen(num(
                    "attributeSwapMinApproachSpeed",
                    "min_approach_speed",
                    0.25,
                    0.0,
                    3.0
            ), this::isAutoElytraCycleEnabled);
    private final NumberValue<Integer> predictionTicks =
            visibleWhen(num(
                    "attributeSwapPredictionTicks",
                    "prediction_ticks",
                    8,
                    1,
                    20
            ), this::isAutoElytraCycleEnabled);
    private final NumberValue<Double> maxPredictedHorizontalDistance =
            visibleWhen(num(
                    "attributeSwapMaxPredictedHorizontalDistance",
                    "max_predicted_horizontal_distance",
                    4.5,
                    1.5,
                    8.0
            ), this::isAutoElytraCycleEnabled);
    private final NumberValue<Integer> elytraCycleTimeout =
            visibleWhen(num(
                    "attributeSwapElytraCycleTimeout",
                    "elytra_cycle_timeout",
                    5,
                    1,
                    12
            ), this::isAutoElytraCycleEnabled);
    private final BooleanValue windBurstSafety =
            visibleWhen(bool(
                    "attributeSwapWindBurstSafety",
                    "wind_burst_safety",
                    true
            ), this::isHeightMaceEnabled);
    private final NumberValue<Float> windBurstMinRemainingHealth =
            visibleWhen(num(
                    "attributeSwapWindBurstMinRemainingHealth",
                    "wind_burst_min_remaining_health",
                    6.0f,
                    0.5f,
                    40.0f
            ), this::isWindBurstSafetyEnabled);
    private final BooleanValue windBurstStrictElytra =
            visibleWhen(bool(
                    "attributeSwapWindBurstStrictElytra",
                    "wind_burst_strict_elytra",
                    false
            ), this::isWindBurstSafetyEnabled);
    private final BooleanValue windBurstDebugLog =
            visibleWhen(bool(
                    "attributeSwapWindBurstDebugLog",
                    "wind_burst_debug_log",
                    false
            ), this::isWindBurstSafetyEnabled);
    private int pendingTargetId = -1;
    private boolean pendingKeepSprint = false;
    private boolean elytraCycleActive;
    private int elytraCycleTargetId = -1;
    private int elytraReturnInventorySlot = -1;
    private int elytraCycleExpireAge = -1;
    private int elytraCycleStage = CYCLE_NONE;
    private int elytraRestoreAge = -1;
    private boolean elytraCycleKeepSprint;
    private boolean elytraCycleFromTarget;
    private int cyclePredictionTicks;
    private boolean cycleInventoryPending;
    private int cycleMaceFromSlot = -1;
    private int cycleMaceToSlot = -1;
    private LocalPlayer cyclePlayer;
    private Vec3 cycleReportedEye;
    private int cycleLostPredictionTicks;
    private int lastElytraCycleAttackAge = -1;
    private int lastElytraCycleAttackTargetId = -1;
    private LocalPlayer lastElytraCycleAttackPlayer;
    private int lastElytraCycleEndAge = -1;
    private LocalPlayer lastElytraCycleEndPlayer;
    private final CycleAttackWindow cycleAttackWindow = new CycleAttackWindow();
    private final Object cycleLifecycle = new Object() {
        @EventHandler(priority = Integer.MAX_VALUE - 2)
        private void onGameTick(GameTickEvent event) {
            if (!elytraCycleActive) return;
            if (mc.player != cyclePlayer || mc.level == null) {
                InventorySwap.INSTANCE.cancelQueuedInventoryActions(AttributeSwap.this);
                clearElytraCycle();
                return;
            }
            InventorySwap.INSTANCE.leaseInventory(AttributeSwap.this, 2);
            if (!isEnabled()) restoreElytraIfNeeded();
            else tickElytraCycle();
        }

        @EventHandler(priority = -20002)
        private void onMovementSent(PacketEvent.Send event) {
            if (!event.isCancelled() && event.getPacket() instanceof ServerboundMovePlayerPacket packet
                    && packet.hasPosition() && mc.player == cyclePlayer && cyclePlayer != null
                    && elytraCycleStage == CYCLE_WAIT_MACE_SYNC) {
                cycleReportedEye = new Vec3(packet.getX(cyclePlayer.getX()), packet.getY(cyclePlayer.getY()),
                        packet.getZ(cyclePlayer.getZ())).add(0, cyclePlayer.getEyeHeight(), 0);
                cycleAttackWindow.notePosition(cyclePlayer.fallDistance, cyclePlayer.isFallFlying(), packet.isOnGround());
            }
        }
    };
    private boolean queuedRestoreGlide;
    private int restoreGlideAge = -1;
    private int restoreGlideExpireAge = -1;
    private long lastFastAuraMaceAttackMs;
    private long nextFastAuraMaceDelayMs = randomFastAuraMaceDelay();
    private boolean externalTargetBlocking = false;
    private int auraControlTicks = 0;
    private boolean attributeSwapSequence = false;
    private int lastAttributeSwapTargetId = -1;
    private int lastAttributeSwapAge = -1;

    private static Map<String, Boolean> actionDefaults() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(ACTION_SHIELD_BREAKER, true);
        defaults.put(ACTION_HEIGHT_MACE, true);
        defaults.put(ACTION_BEST_DAMAGE_WEAPON, true);
        return defaults;
    }

    public static QueueResult queueAttackResult(LivingEntity target, boolean keepSprint) {
        AttributeSwap attributeSwap = Modules.get(AttributeSwap.class);
        return attributeSwap != null ? attributeSwap.queueAttack(target, keepSprint) : QueueResult.PASS;
    }

    public static boolean shouldBypassAuraCooldownFor(LocalPlayer player, LivingEntity target) {
        AttributeSwap attributeSwap = Modules.get(AttributeSwap.class);
        return attributeSwap != null && attributeSwap.shouldBypassAuraCooldown(player, target);
    }

    public static void handleAuraTarget(LivingEntity target, boolean breakShields) {
        AttributeSwap attributeSwap = Modules.get(AttributeSwap.class);
        if (attributeSwap == null || !attributeSwap.isEnabled()) return;

        attributeSwap.suppressCrosshairForAuraTick();

        if (breakShields && target instanceof Player player) {
            attributeSwap.handleExternalTarget(player);
        } else {
            attributeSwap.handleExternalTarget(null);
        }
    }

    public static void clearAuraControlIfActive() {
        AttributeSwap attributeSwap = Modules.get(AttributeSwap.class);
        if (attributeSwap != null) {
            attributeSwap.clearAuraControl();
        }
    }

    public static boolean tryBreakShieldPostAttack(LivingEntity target, boolean keepSprint, boolean enabled) {
        AttributeSwap attributeSwap = Modules.get(AttributeSwap.class);
        if (attributeSwap == null || !attributeSwap.isEnabled()) return false;
        return attributeSwap.tryPostAttackAttributeSwap(target, keepSprint, enabled);
    }

    public static boolean tryBreakShieldAfterClientAttack(Entity target) {
        AttributeSwap attributeSwap = Modules.get(AttributeSwap.class);
        if (attributeSwap == null || !attributeSwap.isEnabled() || attributeSwap.isAuraControlled()) return false;
        if (!(target instanceof LivingEntity)) return false;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof EntityHitResult ehr) || ehr.getEntity() != target) {
            return false;
        }

        return attributeSwap.tryAttributeSwapAfterClientAttack(target);
    }

    private static boolean isBlockingShieldTarget(LivingEntity target) {
        if (!(target instanceof Player player)) {
            return false;
        }
        return player.isBlocking() || isUsingShieldStatic(player);
    }

    private static boolean isUsingShieldStatic(Player target) {
        return target != null
                && target.isUsingItem()
                && target.getUseItem().is(Items.SHIELD);
    }

    private static AABB union(AABB a, AABB b) {
        return new AABB(
                Math.min(a.minX, b.minX),
                Math.min(a.minY, b.minY),
                Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX),
                Math.max(a.maxY, b.maxY),
                Math.max(a.maxZ, b.maxZ)
        );
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static String format3(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static long randomFastAuraMaceDelay() {
        return ThreadLocalRandom.current().nextLong(
                FAST_AURA_MACE_DELAY_MIN_MS,
                FAST_AURA_MACE_DELAY_MAX_MS + 1
        );
    }

    private boolean isActionEnabled(String action) {
        return actions.get(action);
    }

    private boolean isHeightMaceEnabled() {
        return isActionEnabled(ACTION_HEIGHT_MACE);
    }

    private boolean isBestDamageWeaponEnabled() {
        return isActionEnabled(ACTION_BEST_DAMAGE_WEAPON);
    }

    private boolean shouldUseBestDamageWeaponForAura() {
        return isBestDamageWeaponEnabled() && bestDamageWeaponMode.get().usesAura();
    }

    private boolean shouldUseBestDamageWeaponForNextHit() {
        return isBestDamageWeaponEnabled() && bestDamageWeaponMode.get().usesNextHit();
    }

    private boolean isAutoElytraCycleEnabled() {
        return isHeightMaceEnabled() && autoElytraCycle.get();
    }

    private boolean isWindBurstSafetyEnabled() {
        return isHeightMaceEnabled() && windBurstSafety.get();
    }

    @Override
    public void onDisable() {
        pendingTargetId = -1;
        pendingKeepSprint = false;
        restoreElytraIfNeeded();
        clearRestoreGlide();
        clearAuraControl();
        clearAttributeSwapSequence();
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @Override
    public void onTick() {
        if (isEnabled()) {
            InventorySwap.INSTANCE.tick();
            tickRestoreGlide();
            tickAuraControl();
            externalTargetBlocking = false;
        }
    }

    public QueueResult queueAttack(LivingEntity target, boolean keepSprint) {
        if (!isEnabled()) return QueueResult.PASS;
        if (mc.player == null || mc.gameMode == null || mc.level == null) return QueueResult.PASS;
        if (!isValidTarget(target)) return QueueResult.PASS;

        if (elytraCycleActive) {
            if (elytraCycleTargetId != target.getId()) {
                return QueueResult.SUPPRESS;
            }
            elytraCycleKeepSprint = keepSprint;
            // No normal hit before the smash: it would consume attack strength and
            // make the subsequent mace hit ineffective.
            return QueueResult.SUPPRESS;
        }


        if (isBlockingShieldTarget(target)) {
            return QueueResult.PASS;
        }

        if (isHeightMaceEnabled()) {
            if (tryStartElytraCycle(mc.player, target, keepSprint)) return QueueResult.SUPPRESS;
            int maceSlot = findDesiredMaceSlot(mc.player, target);
            if (maceSlot != -1) {

                boolean fastAuraMace = shouldBypassAuraCooldown(mc.player, target, maceSlot);
                if (fastAuraMace && maceSlot < 9) {
                    return attackWithLeasedHotbar(mc.player, target, keepSprint, maceSlot)
                            ? QueueResult.HANDLE
                            : QueueResult.SUPPRESS;
                }

                if (!canAttackNow(mc.player)) return QueueResult.PASS;

                queuePendingMaceAttack(target, keepSprint);
                return QueueResult.HANDLE;
            }
        }

        return tryBestDamageWeaponForAura(target, keepSprint);
    }

    public boolean shouldBypassAuraCooldown(LocalPlayer player, LivingEntity target) {
        int maceSlot = findDesiredHotbarMaceSlot(player, target);
        return shouldBypassAuraCooldown(player, target, maceSlot);
    }

    private void queuePendingMaceAttack(LivingEntity target, boolean keepSprint) {
        pendingTargetId = target.getId();
        pendingKeepSprint = keepSprint;
    }

    @EventHandler(priority = -2000)
    private void onSync(EventSync event) {
        if (!isEnabled()) return;
        tickElytraCycleSync();
        tickRestoreGlideSync();
        if (elytraCycleActive) return;
        if (pendingTargetId == -1) return;

        LocalPlayer player = mc.player;
        MultiPlayerGameMode manager = mc.gameMode;
        int targetId = pendingTargetId;
        boolean keepSprint = pendingKeepSprint;
        pendingTargetId = -1;
        pendingKeepSprint = false;
        if (player == null || manager == null || mc.level == null) return;
        if (player.isUsingItem()) return;

        Entity entity = mc.level.getEntity(targetId);
        if (!(entity instanceof LivingEntity target) || !isValidTarget(target)) return;
        KillAura aura = Modules.get(KillAura.class);
        double reach = aura != null && aura.isEnabled() ? aura.getEffectiveRange(target) : 3.0;
        Vec3 eye = player.getEyePosition();
        AABB box = target.getBoundingBox();
        if (eye.distanceToSqr(Mth.clamp(eye.x, box.minX, box.maxX),
                Mth.clamp(eye.y, box.minY, box.maxY), Mth.clamp(eye.z, box.minZ, box.maxZ)) > reach * reach) return;

        int maceSlot = findDesiredMaceSlot(player, target);
        if (maceSlot == -1) return;

        if (!attackMaceNow(player, target, keepSprint, maceSlot)) {
            queuePendingMaceAttack(target, keepSprint);
        }
    }

    private boolean attackWithLeasedHotbar(LocalPlayer player,
                                           LivingEntity target,
                                           boolean keepSprint,
                                           int hotbarSlot) {
        return attackWithLeasedHotbar(player, target, keepSprint, hotbarSlot, true);
    }

    private boolean attackWithLeasedHotbar(LocalPlayer player,
                                           LivingEntity target,
                                           boolean keepSprint,
                                           int hotbarSlot,
                                           boolean updateFastMaceDelay) {
        int resetTicks = restoreItem.get() ? 1 : HOTBAR_RESET_TICKS;
        if (restoreItem.get()) {
            if (!InventorySwap.INSTANCE.leaseHotbar(this, hotbarSlot, resetTicks)) return false;
        } else if (!InventorySwap.INSTANCE.selectHotbar(hotbarSlot, this)) return false;
        if (!AttackUtil.attackCurrentItem(mc, target, keepSprint)) return false;
        ResetAttackCooldown.resetAttackCooldown(player);
        if (updateFastMaceDelay) {
            lastFastAuraMaceAttackMs = System.currentTimeMillis();
            nextFastAuraMaceDelayMs = randomFastAuraMaceDelay();
        }
        return true;
    }

    private boolean attackWithAttributeHotbar(LocalPlayer player,
                                              LivingEntity target,
                                              boolean keepSprint,
                                              int hotbarSlot) {
        if (player == null || target == null || !InventorySwap.INSTANCE.leaseHotbar(this, hotbarSlot, 0)) {
            return false;
        }

        attributeSwapSequence = true;
        lastAttributeSwapTargetId = target.getId();
        lastAttributeSwapAge = player.tickCount;
        try {
            if (!AttackUtil.attackCurrentItem(mc, target, keepSprint)) return false;
            ResetAttackCooldown.resetAttackCooldown(player);
            return true;
        } finally {
            attributeSwapSequence = false;
            InventorySwap.INSTANCE.releaseHotbar(this);
        }
    }

    private boolean wasAttributeSwapUsedThisTick(LivingEntity target) {
        LocalPlayer player = mc.player;
        return player != null
                && target != null
                && lastAttributeSwapTargetId == target.getId()
                && lastAttributeSwapAge == player.tickCount;
    }

    private void clearAttributeSwapSequence() {
        attributeSwapSequence = false;
        lastAttributeSwapTargetId = -1;
        lastAttributeSwapAge = -1;
    }

    private void tickAuraControl() {
        if (auraControlTicks > 0) {
            auraControlTicks--;
        }
    }

    public void suppressCrosshairForAuraTick() {
        auraControlTicks = 2;
    }

    public boolean isAuraControlled() {
        return auraControlTicks > 0;
    }

    public void clearAuraControl() {
        auraControlTicks = 0;
        externalTargetBlocking = false;
    }

    public void handleExternalTarget(Player target) {
        if (!isEnabled() || mc.player == null) return;

        auraControlTicks = 2;
        externalTargetBlocking = isUsingShield(target);
    }

    public boolean tryPostAttackAttributeSwap(LivingEntity target, boolean keepSprint, boolean allowShieldBreak) {
        if (!isEnabled()) return false;
        suppressCrosshairForAuraTick();

        return allowShieldBreak && target instanceof Player player && tryBreakShield(player, keepSprint);
    }

    public boolean tryAttributeSwapAfterClientAttack(Entity target) {
        if (!isEnabled() || isAuraControlled()) return false;
        if (!(target instanceof LivingEntity living)) return false;
        if (!(mc.hitResult instanceof EntityHitResult ehr) || ehr.getEntity() != target) {
            return false;
        }

        return tryPostAttackAttributeSwap(living, false, true);
    }

    public boolean tryBreakShield(Player target, boolean keepSprint) {
        if (elytraCycleActive && (elytraCycleStage != CYCLE_WAIT_MACE_SYNC || cycleInventoryPending
                || !InventorySwap.INSTANCE.allowsDirectCombatSwap())) return false;
        if (!isActionEnabled(ACTION_SHIELD_BREAKER)) return false;
        if (attributeSwapSequence) return false;
        if (mc.player == null || mc.gameMode == null) return false;
        if (target == null || !target.isAlive() || target.isRemoved()) return false;
        if (wasAttributeSwapUsedThisTick(target)) return false;
        if (!isUsingShield(target)) return false;
        if (!isShieldFacingPlayer(target)) return false;

        int axeSlot = findAxeSlot(mc.player);
        if (axeSlot == -1) return false;

        int selected = ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$getSelectedSlot();
        if (axeSlot == selected && isAxe(mc.player.getMainHandItem())) return false;

        if (axeSlot < 9) return attackWithAttributeHotbar(mc.player, target, keepSprint, axeSlot);
        boolean[] attacked = {false};
        InventorySwap.INSTANCE.execute(InventorySwapRequest.builder(this::isAxe, () -> {
            attributeSwapSequence = true;
            try {
                attacked[0] = AttackUtil.attackCurrentItem(mc, target, keepSprint);
                if (attacked[0]) {
                    lastAttributeSwapTargetId = target.getId();
                    lastAttributeSwapAge = mc.player.tickCount;
                    ResetAttackCooldown.resetAttackCooldown(mc.player);
                }
            } finally {
                attributeSwapSequence = false;
            }
        }).scope(InventorySearchScope.INVENTORY).restore(restoreItem.get())
                .actionKind(InventoryActionKind.ATTACK_ENTITY).build(), elytraCycleActive ? this : null);
        return attacked[0];
    }

    private boolean attackMaceNow(LocalPlayer player,
                                  LivingEntity target,
                                  boolean keepSprint,
                                  int maceSlot) {
        if (maceSlot < 9) {
            return attackWithLeasedHotbar(player, target, keepSprint, maceSlot);
        }

        if (player.containerMenu != player.inventoryMenu) return false;
        ItemStack mace = player.getInventory().getItem(maceSlot).copy();
        boolean[] attacked = {false};
        InventorySwap.INSTANCE.execute(InventorySwapRequest.builder(
                stack -> ItemStack.isSameItemSameComponents(stack, mace),
                () -> {
                    if (isUnsafeWindBurstMace(player, player.getMainHandItem())) return;
                    attacked[0] = AttackUtil.attackCurrentItem(mc, target, keepSprint);
                    if (attacked[0]) ResetAttackCooldown.resetAttackCooldown(player);
                }).scope(InventorySearchScope.INVENTORY)
                .visibility(InventorySwapVisibility.NORMAL).restore(restoreItem.get())
                .actionKind(InventoryActionKind.ATTACK_ENTITY).build(), elytraCycleActive ? this : null);
        return attacked[0];
    }

    @EventHandler(priority = 2000)
    private void onManualAttack(ManualAttackEvent event) {
        if (!event.isResolved() && handleManualAttack()) event.resolve(true);
    }

    public boolean handleManualAttack() {
        if (!isEnabled()) {
            return false;
        }
        if (elytraCycleActive) return true;

        LocalPlayer player = mc.player;
        MultiPlayerGameMode manager = mc.gameMode;
        if (player == null || manager == null || mc.level == null) {
            return false;
        }

        LivingEntity target = resolveCrosshairTarget();
        if (!isValidTarget(target)) return false;
        if (isBlockingShieldTarget(target)) return false;
        if (tryStartElytraCycle(player, target, false)) return true;

        if (isHeightMaceEnabled() && canAttackNow(player)) {
            int maceSlot = findDesiredMaceSlot(player, target);
            if (maceSlot != -1) {
                int selectedSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
                if (maceSlot == selectedSlot && player.getMainHandItem().is(Items.MACE)) {
                    return false;
                }

                if (maceSlot < 9) {
                    if (!InventorySwap.INSTANCE.leaseHotbar(this, maceSlot, HOTBAR_RESET_TICKS)) return false;
                    try {
                        if (!AttackUtil.attackCurrentItem(mc, target, false)) return false;
                        ResetAttackCooldown.resetAttackCooldown(player);
                        return true;
                    } finally {
                        if (restoreItem.get()) {
                            InventorySwap.INSTANCE.releaseHotbar(this);
                        }
                    }
                }

                if (!attackMaceNow(player, target, false, maceSlot)) queuePendingMaceAttack(target, false);
                return true;
            }
        }

        BestWeaponPrepareResult bestWeaponPrepare = prepareBestDamageWeaponForNextHit(player, target);
        return bestWeaponPrepare == BestWeaponPrepareResult.FAILED;
    }

    private boolean tryStartElytraCycle(LocalPlayer player, LivingEntity target, boolean keepSprint) {
        // The Aura/crosshair path must not start a second, unrelated dive while
        // ElytraTarget owns the pursuit. The log showed fromTarget=false beginning
        // with a different target mid-flight and forcing an early landing.
        ElytraTarget flight = Modules.get(ElytraTarget.class);
        if (flight != null && flight.isControllingFlight()) return false;
        if (!canPrepareElytraTargetDive(player, target) || elytraCycleActive) return false;
        if (!player.isFallFlying() || !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return false;
        if (!hasPredictedSmashContact(player, target)) return false;
        if (!beginElytraCycle(player, target, false, elytraCycleTimeout.get(), predictionTicks.get())) return false;
        elytraCycleKeepSprint = keepSprint;
        return true;
    }

    private boolean beginElytraCycle(LocalPlayer player, LivingEntity target, boolean fromTarget, int duration, int forecastTicks) {
        int swapSlot = findElytraRemovalSlot(player);
        InventorySwap inventory = InventorySwap.INSTANCE;
        if (swapSlot < 0 || player.inventoryMenu == null || player.containerMenu != player.inventoryMenu
                || !inventory.itemUseAvailable() || !inventory.leaseInventory(this, 2)) return false;
        elytraCycleActive = true;
        ElytraDiveTrace.INSTANCE.sample(player.tickCount, target.getId(), "AttributeSwap",
                "begin fromTarget=" + fromTarget + " forecastTicks=" + forecastTicks
                        + " swapSlot=" + swapSlot + " expire=" + (player.tickCount + duration));
        pendingTargetId = -1;
        pendingKeepSprint = false;
        cycleAttackWindow.reset();
        cycleReportedEye = null;
        cycleLostPredictionTicks = 0;
        elytraCycleFromTarget = fromTarget;
        cyclePredictionTicks = forecastTicks;
        elytraCycleTargetId = target.getId();
        elytraReturnInventorySlot = swapSlot;
        elytraCycleExpireAge = player.tickCount + duration;
        elytraCycleStage = CYCLE_WAIT_CHEST_SWAP;
        elytraRestoreAge = -1;
        elytraCycleKeepSprint = true;
        cyclePlayer = player;
        Events.BUS.register(cycleLifecycle);
        cycleInventoryPending = true;
        boolean accepted = inventory.swapScreenSlots(InventorySwap.mapInventoryToScreenSlot(swapSlot),
                CHEST_SCREEN_SLOT, this,
                () -> elytraCycleStage == CYCLE_WAIT_CHEST_SWAP && isEnabled()
                        && isValidTarget(target) && player.tickCount < elytraCycleExpireAge
                        && ElytraTargetReachability.isSafeMaceTarget(target)
                        && hasPredictedSmashContact(player, target, cyclePredictionTicks),
                completed -> {
                    cycleInventoryPending = false;
                    ElytraDiveTrace.INSTANCE.event("AttributeSwap chestSwapCompleted=" + completed
                            + " chestElytra=" + player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA));
                    if (completed && !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
                            && player.getInventory().getItem(swapSlot).is(Items.ELYTRA)) {
                        elytraCycleStage = CYCLE_WAIT_MACE_SYNC;
                    } else {
                        elytraCycleStage = CYCLE_WAIT_ELYTRA_RESTORE;
                        elytraRestoreAge = player.tickCount;
                    }
                });
        if (!accepted) {
            cycleInventoryPending = false;
            clearElytraCycle();
            return false;
        }
        return true;
    }

    private void tickElytraCycleSync() {
        if (!elytraCycleActive || elytraCycleStage != CYCLE_WAIT_MACE_SYNC) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clearElytraCycle();
            return;
        }
        if (cycleInventoryPending || player.containerMenu != player.inventoryMenu) return;
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            restoreElytraIfNeeded();
            return;
        }

        Entity entity = mc.level.getEntity(elytraCycleTargetId);
        if (!(entity instanceof LivingEntity target) || !isValidTarget(target)
                || !ElytraTargetReachability.isSafeMaceTarget(target) || target.getHealth() <= 0.0f) {
            restoreElytraIfNeeded();
            return;
        }
        if (player.onGround() || player.isInWater() || player.isInLava()) {
            restoreElytraIfNeeded();
            return;
        }
        int maceSlot = findCycleMaceSlot(player);
        if (maceSlot < 0) {
            restoreElytraIfNeeded();
            return;
        }
        boolean directInventory = maceSlot >= 9 && InventorySwap.INSTANCE.allowsDirectCombatSwap();
        if (maceSlot >= 9 && !directInventory) {
            prepareCycleInventoryMace(player, target, maceSlot);
            if (cycleInventoryPending) return;
            maceSlot = findCycleMaceSlot(player);
            if (maceSlot < 0 || maceSlot >= 9) return;
        }
        if (ElytraDiveTrace.INSTANCE.active()) {
            ElytraDiveTrace.INSTANCE.event(String.format(java.util.Locale.ROOT,
                    "AttributeSwap waitMace fall=%.3f vy=%.3f smashReady=%s invPending=%s maceSlot=%d",
                    player.fallDistance, player.getDeltaMovement().y,
                    MaceItem.canSmashAttack(player), cycleInventoryPending, maceSlot));
        }
        if (!MaceItem.canSmashAttack(player)) return;
        if (player.fallDistance < minFallDistance.get()) return;
        if (player.getDeltaMovement().y >= 0) return;
        if (player.isUsingItem()) return;
        if (player.getAttackStrengthScale(0.5f) < 0.9f) {
            traceCycleGate(player, target, "attack_cooldown");
            return;
        }
        if (!isCycleContactReachable(player, target)) {
            // A long-range trajectory prediction can fluctuate during the unglided
            // descent. Give the actual contact window a chance to arrive before
            // abandoning a safe target, but never continue after lateral escape.
            SmashTrajectory.Prediction descent = predictSmashContact(player, target, cyclePredictionTicks);
            if (descent == SmashTrajectory.Prediction.BLOCKED) {
                // A leaf canopy or solid protrusion lies between the current
                // position and the attack window. This is not prediction jitter.
                traceCycleGate(player, target, "descent_obstructed");
                restoreElytraIfNeeded();
                return;
            }
            boolean predicted = descent == SmashTrajectory.Prediction.REACHABLE;
            if (predicted) {
                cycleLostPredictionTicks = 0;
            } else {
                cycleLostPredictionTicks++;
                AABB box = target.getBoundingBox();
                double gapX = Math.max(0.0, Math.max(box.minX - player.getX(), player.getX() - box.maxX));
                double gapZ = Math.max(0.0, Math.max(box.minZ - player.getZ(), player.getZ() - box.maxZ));
                boolean tooFarLaterally = Math.hypot(gapX, gapZ)
                        > maxPredictedHorizontalDistance.get() + 1.0;
                if (tooFarLaterally || cycleLostPredictionTicks >= 6) {
                    traceCycleGate(player, target, tooFarLaterally ? "lateral_escape" : "prediction_lost");
                    restoreElytraIfNeeded();
                    return;
                }
            }
            traceCycleGate(player, target, predicted ? "contact_not_yet" : "prediction_grace_" + cycleLostPredictionTicks);
            return;
        }
        cycleLostPredictionTicks = 0;
        if (PacketWindow.INSTANCE.isPostMovement()) return;
        boolean reportedReady = cycleReportedEye != null
                && cycleAttackWindow.ready(minFallDistance.get())
                && isCycleContactReachable(player, target, cycleReportedEye);
        // The server must already have received a position from which the
        // target is reachable before we change the held weapon. Selecting the
        // mace in a tick where only the live position is in range resets its
        // attack cooldown before the next (actually reachable) tick.
        if (!reportedReady) {
            traceCycleGate(player, target, "await_reported_position");
            return;
        }
        if (BlinkManager.INSTANCE.hasQueuedOutgoingMovement()) {
            traceCycleGate(player, target, "queued_movement");
            return;
        }
        if (target instanceof Player shieldTarget && isBlockingShieldTarget(target)
                && tryBreakShield(shieldTarget, elytraCycleKeepSprint)) return;
        if (directInventory) {
            int inventorySlot = maceSlot;
            cycleAttackWindow.beforeMovement(minFallDistance.get(),
                    () -> attackElytraCycleBeforeMovement(player, target, inventorySlot));
            return;
        }
        if (InventorySwap.INSTANCE.serverSelectedSlot() != maceSlot
                && !PacketWindow.INSTANCE.slotChangeAllowed()) {
            traceCycleGate(player, target, "slot_window");
            return;
        }
        if (restoreItem.get()) {
            if (!InventorySwap.INSTANCE.leaseHotbar(this, maceSlot, 2)) return;
        } else if (!InventorySwap.INSTANCE.selectHotbar(maceSlot, this)) return;
        int preparedSlot = maceSlot;
        // Keep the slot change and the attack in the same pre-movement window.
        // A deferred attack would run after movement, whereas a deferred slot
        // selection would violate PacketWindow's ordering contract.
        cycleAttackWindow.beforeMovement(minFallDistance.get(),
                () -> attackElytraCycleBeforeMovement(player, target, preparedSlot));
    }

    private void traceCycleGate(LocalPlayer player, LivingEntity target, String reason) {
        if (!ElytraDiveTrace.INSTANCE.active()) return;
        AABB box = target.getBoundingBox();
        Vec3 eye = player.getEyePosition();
        double liveGap = eye.distanceTo(new Vec3(
                Mth.clamp(eye.x, box.minX, box.maxX),
                Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ)));
        double reportedGap = Double.NaN;
        if (cycleReportedEye != null) {
            Vec3 reported = cycleReportedEye;
            reportedGap = reported.distanceTo(new Vec3(
                    Mth.clamp(reported.x, box.minX, box.maxX),
                    Mth.clamp(reported.y, box.minY, box.maxY),
                    Mth.clamp(reported.z, box.minZ, box.maxZ)));
        }
        ElytraDiveTrace.INSTANCE.event(String.format(java.util.Locale.ROOT,
                "AttributeSwap attackGate=%s liveGap=%.2f reportedGap=%.2f serverSlot=%d fall=%.2f cooldown=%.2f",
                reason, liveGap, reportedGap, InventorySwap.INSTANCE.serverSelectedSlot(),
                player.fallDistance, player.getAttackStrengthScale(0.5f)));
    }

    private void attackElytraCycleBeforeMovement(LocalPlayer player, LivingEntity target, int maceSlot) {
        if (!isEnabled() || mc.player != player || mc.level == null || !elytraCycleActive
                || elytraCycleStage != CYCLE_WAIT_MACE_SYNC || cycleInventoryPending
                || elytraCycleTargetId != target.getId() || !isValidTarget(target)) return;
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
                || maceSlot < 9 && InventorySwap.INSTANCE.serverSelectedSlot() != maceSlot
                || !player.getInventory().getItem(maceSlot).is(Items.MACE)
                || isUnsafeWindBurstMace(player, player.getInventory().getItem(maceSlot))) {
            traceCycleGate(player, target, "commit_item_not_ready");
            return;
        }
        if (player.isFallFlying() || !MaceItem.canSmashAttack(player)
                || player.fallDistance < minFallDistance.get()
                || player.getDeltaMovement().y >= 0.0) {
            traceCycleGate(player, target, "commit_fall_not_ready");
            return;
        }
        if (player.isUsingItem() || player.getAttackStrengthScale(0.5f) < 0.9f) {
            traceCycleGate(player, target, "commit_cooldown");
            return;
        }
        if (!isCycleContactReachable(player, target) || cycleReportedEye == null
                || !isCycleContactReachable(player, target, cycleReportedEye)) {
            traceCycleGate(player, target, "commit_contact_lost");
            return;
        }
        if (maceSlot >= 9) {
            if (!attackMaceNow(player, target, elytraCycleKeepSprint, maceSlot)) {
                traceCycleGate(player, target, "attack_not_sent");
                return;
            }
        } else if (!AttackUtil.attackCurrentItem(mc, target, elytraCycleKeepSprint)) {
            traceCycleGate(player, target, "attack_not_sent");
            return;
        }
        lastElytraCycleAttackAge = player.tickCount;
        ElytraDiveTrace.INSTANCE.event("AttributeSwap smashSent tick=" + player.tickCount
                + " target=" + target.getId() + " slot=" + maceSlot);
        lastElytraCycleAttackTargetId = target.getId();
        lastElytraCycleAttackPlayer = player;
        ResetAttackCooldown.resetAttackCooldown(player);
        lastFastAuraMaceAttackMs = System.currentTimeMillis();
        nextFastAuraMaceDelayMs = randomFastAuraMaceDelay();
        elytraCycleStage = CYCLE_WAIT_ELYTRA_RESTORE;
        elytraRestoreAge = player.tickCount + 1;
    }

    private boolean isCycleContactReachable(LocalPlayer player, LivingEntity target) {
        return isCycleContactReachable(player, target, player.getEyePosition());
    }

    private boolean isCycleContactReachable(LocalPlayer player, LivingEntity target, Vec3 eye) {
        KillAura aura = Modules.get(KillAura.class);
        double reach = aura != null && aura.isEnabled() ? aura.getEffectiveRange(target) : 3.0;
        AABB box = target.getBoundingBox();
        Vec3 contact = new Vec3(
                Mth.clamp(eye.x, box.minX, box.maxX),
                Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ)
        );
        if (eye.distanceToSqr(contact) > reach * reach) return false;
        HitResult obstruction = mc.level.clip(new ClipContext(
                eye, contact, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS
                || obstruction.getLocation().distanceToSqr(eye) + 0.05 >= contact.distanceToSqr(eye);
    }

    private void tickElytraCycle() {
        if (!elytraCycleActive) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clearElytraCycle();
            return;
        }

        Entity entity = mc.level.getEntity(elytraCycleTargetId);
        boolean expired = elytraCycleExpireAge >= 0 && player.tickCount >= elytraCycleExpireAge;
        if (expired || !(entity instanceof LivingEntity living) || !isValidTarget(living)
                || !ElytraTargetReachability.isSafeMaceTarget(living)) {
            ElytraDiveTrace.INSTANCE.event("AttributeSwap restore reason=expired_or_invalid_target expired=" + expired);
            restoreElytraIfNeeded();
            return;
        }

        if (elytraCycleStage == CYCLE_WAIT_ELYTRA_RESTORE && player.tickCount >= elytraRestoreAge) {
            restoreElytraIfNeeded();
        }
    }

    private void restoreElytraIfNeeded() {
        if (!elytraCycleActive) return;

        boolean alreadyRestoring = elytraCycleStage == CYCLE_WAIT_ELYTRA_RESTORE;
        if (!alreadyRestoring) {
            ElytraDiveTrace.INSTANCE.event("AttributeSwap restoreStart previousStage=" + elytraCycleStage);
        }
        elytraCycleStage = CYCLE_WAIT_ELYTRA_RESTORE;
        InventorySwap inventory = InventorySwap.INSTANCE;
        if (cycleInventoryPending) {
            if (alreadyRestoring) return;
            inventory.cancelQueuedInventoryActions(this);
            if (cycleInventoryPending) return;
        }

        LocalPlayer player = mc.player;
        if (player == null || player.inventoryMenu == null) {
            clearElytraCycle();
            return;
        }

        if (player != cyclePlayer) {
            clearElytraCycle();
            return;
        }
        inventory.leaseInventory(this, 2);
        if (player.containerMenu != player.inventoryMenu) return;
        if (cycleMaceFromSlot >= 0) {
            if (!restoreItem.get()) {
                cycleMaceFromSlot = -1;
                cycleMaceToSlot = -1;
            } else {
                requestCycleMaceRestore(player);
                return;
            }
        }
        boolean restored = false;
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            restored = true;
        } else {
            int elytraSlot = elytraReturnInventorySlot;
            if (elytraSlot < 0 || elytraSlot >= 36
                    || !player.getInventory().getItem(elytraSlot).is(Items.ELYTRA)) {
                elytraSlot = -1;
                for (int slot = 0; slot < 36; slot++) {
                    if (player.getInventory().getItem(slot).is(Items.ELYTRA)) {
                        elytraSlot = slot;
                        break;
                    }
                }
            }
            if (elytraSlot >= 0) {
                cycleInventoryPending = true;
                boolean accepted = inventory.swapScreenSlots(
                        InventorySwap.mapInventoryToScreenSlot(elytraSlot), CHEST_SCREEN_SLOT, this,
                        () -> elytraCycleActive && elytraCycleStage == CYCLE_WAIT_ELYTRA_RESTORE,
                        completed -> {
                            cycleInventoryPending = false;
                            if (completed && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                                ElytraDiveTrace.INSTANCE.event("AttributeSwap elytraRestored tick=" + player.tickCount);
                                if (isEnabled()) queueRestoreGlide(player);
                                clearElytraCycle();
                            }
                        });
                if (!accepted) cycleInventoryPending = false;
                return;
            }
        }

        if (restored) {
            if (isEnabled()) queueRestoreGlide(player);
            clearElytraCycle();
            return;
        }

        // Inventory synchronization may temporarily reject a swap. Retry on later ticks
        // rather than forgetting the chest slot while the player is still falling.
        elytraCycleStage = CYCLE_WAIT_ELYTRA_RESTORE;
        elytraRestoreAge = player.tickCount + 1;
    }

    private void prepareCycleInventoryMace(LocalPlayer player, LivingEntity target, int maceSlot) {
        if (cycleInventoryPending || cycleMaceFromSlot >= 0) return;
        int stagingSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == InventorySwap.INSTANCE.serverSelectedSlot()
                    || slot == InventorySwap.INSTANCE.clientSelectedSlot()
                    || slot == elytraReturnInventorySlot || InventorySlotLocks.blocks(slot, this)) continue;
            if (stagingSlot < 0 || player.getInventory().getItem(slot).isEmpty()) stagingSlot = slot;
            if (player.getInventory().getItem(slot).isEmpty()) break;
        }
        if (stagingSlot < 0) return;
        int hotbarSlot = stagingSlot;
        cycleInventoryPending = true;
        boolean accepted = InventorySwap.INSTANCE.swapScreenSlots(
                InventorySwap.mapInventoryToScreenSlot(maceSlot), InventorySwap.mapHotbarToScreenSlot(hotbarSlot), this,
                () -> elytraCycleStage == CYCLE_WAIT_MACE_SYNC && isEnabled() && isValidTarget(target),
                completed -> {
                    cycleInventoryPending = false;
                    if (completed) {
                        cycleMaceFromSlot = maceSlot;
                        cycleMaceToSlot = hotbarSlot;
                    }
                });
        if (!accepted) cycleInventoryPending = false;
    }

    private void requestCycleMaceRestore(LocalPlayer player) {
        cycleInventoryPending = true;
        boolean accepted = InventorySwap.INSTANCE.swapScreenSlots(
                InventorySwap.mapInventoryToScreenSlot(cycleMaceFromSlot),
                InventorySwap.mapHotbarToScreenSlot(cycleMaceToSlot), this,
                () -> elytraCycleActive && mc.player == player,
                completed -> {
                    cycleInventoryPending = false;
                    if (completed) {
                        cycleMaceFromSlot = -1;
                        cycleMaceToSlot = -1;
                    }
                });
        if (!accepted) cycleInventoryPending = false;
    }

    private void queueRestoreGlide(LocalPlayer player) {
        if (!autoGlideAfterRestore.get()) return;
        if (player == null) return;
        if (player.isFallFlying()) return;
        if (player.isPassenger()) return;
        if (player.getAbilities().flying) return;
        if (player.onGround() || player.isInWater() || player.isInLava()) return;

        int delay = Math.max(1, autoGlideDelay.get());
        queuedRestoreGlide = true;
        restoreGlideAge = player.tickCount + delay;
        restoreGlideExpireAge = player.tickCount + delay + 10;
    }

    private void tickRestoreGlide() {
        if (!queuedRestoreGlide) return;

        LocalPlayer player = mc.player;
        if (player == null || player.isFallFlying()) {
            clearRestoreGlide();
            return;
        }

        if (restoreGlideExpireAge >= 0 && player.tickCount > restoreGlideExpireAge) {
            clearRestoreGlide();
            return;
        }

        if (player.tickCount < restoreGlideAge) {
            return;
        }

        if (!canStartRestoreGlide(player)) {
            clearRestoreGlide();
        }
    }

    private void tickRestoreGlideSync() {
        if (!queuedRestoreGlide) return;

        LocalPlayer player = mc.player;
        if (player == null) {
            clearRestoreGlide();
            return;
        }

        if (player.tickCount < restoreGlideAge) return;
        if (player.isFallFlying()) {
            clearRestoreGlide();
            return;
        }

        if (!canStartRestoreGlide(player)) {
            clearRestoreGlide();
            return;
        }

        if (player.getDeltaMovement().y > 0.0) {
            restoreGlideAge = player.tickCount + 1;
            return;
        }

        if (mc.getConnection() == null) return;

        if (player.tryToStartFallFlying()) {
            if (player.isFallFlying()) {
                mc.getConnection().send(new ServerboundPlayerCommandPacket(
                        player,
                        ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
                ));
                clearRestoreGlide();
            } else {
                restoreGlideAge = player.tickCount + 1;
            }
        } else {
            restoreGlideAge = player.tickCount + 1;
        }
    }

    private boolean canStartRestoreGlide(LocalPlayer player) {
        if (player == null) return false;
        if (player.isFallFlying()) return false;
        if (player.isPassenger()) return false;
        if (player.getAbilities().flying) return false;
        if (!player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return false;
        if (player.isInWater() || player.isInLava()) return false;
        return !player.onGround();
    }

    private void clearRestoreGlide() {
        queuedRestoreGlide = false;
        restoreGlideAge = -1;
        restoreGlideExpireAge = -1;
    }

    private void clearElytraCycle() {
        if (elytraCycleActive) {
            ElytraDiveTrace.INSTANCE.event("AttributeSwap cycleCleared stage=" + elytraCycleStage
                    + " target=" + elytraCycleTargetId + " invPending=" + cycleInventoryPending);
        }
        if (elytraCycleActive && cyclePlayer != null && mc.player == cyclePlayer) {
            lastElytraCycleEndAge = cyclePlayer.tickCount;
            lastElytraCycleEndPlayer = cyclePlayer;
        }
        Events.BUS.unregister(cycleLifecycle);
        InventorySwap.INSTANCE.releaseInventory(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        cycleInventoryPending = false;
        cyclePlayer = null;
        cycleAttackWindow.reset();
        cycleReportedEye = null;
        cycleLostPredictionTicks = 0;
        cycleMaceFromSlot = -1;
        cycleMaceToSlot = -1;
        elytraCycleActive = false;
        elytraCycleTargetId = -1;
        elytraReturnInventorySlot = -1;
        elytraCycleExpireAge = -1;
        elytraCycleStage = CYCLE_NONE;
        elytraRestoreAge = -1;
        elytraCycleKeepSprint = false;
        elytraCycleFromTarget = false;
        cyclePredictionTicks = 0;
    }

    public boolean isElytraCycleActive() {
        return elytraCycleActive;
    }

    public int getElytraCycleTargetId() {
        return isElytraCycleActive() ? elytraCycleTargetId : -1;
    }

    /** The successful mace attack is published before the asynchronous chest-slot restore finishes. */
    public int getLastElytraCycleAttackAge(LocalPlayer player) {
        return player != null && player == lastElytraCycleAttackPlayer ? lastElytraCycleAttackAge : -1;
    }

    public int getLastElytraCycleAttackTargetId(LocalPlayer player) {
        return player != null && player == lastElytraCycleAttackPlayer ? lastElytraCycleAttackTargetId : -1;
    }

    public int getLastElytraCycleEndAge(LocalPlayer player) {
        return player != null && player == lastElytraCycleEndPlayer ? lastElytraCycleEndAge : -1;
    }

    public boolean canPrepareElytraTargetDive(LocalPlayer player, LivingEntity target) {
        return preparationBlocker(player, target) == null;
    }

    private String preparationBlocker(LocalPlayer player, LivingEntity target) {
        if (!isEnabled()) return "attribute_swap_disabled";
        if (!isAutoElytraCycleEnabled()) return "auto_cycle_disabled";
        if (player == null) return "no_player";
        if (player.onGround()) return "grounded";
        if (player.isInWater() || player.isInLava()) return "in_fluid";
        if (target == null || !isValidTarget(target)) return "invalid_target";
        if (!ElytraTargetReachability.isSafeMaceTarget(target)) return "target_in_fluid";
        if (player.inventoryMenu == null || player.containerMenu != player.inventoryMenu) return "container_open";
        if (player.isUsingItem()) return "using_item";
        if (findCycleMaceSlot(player) < 0) return "no_safe_mace_in_preset_scope";
        if (findElytraRemovalSlot(player) < 0) return "no_chestplate_or_free_slot_in_preset_scope";
        return null;
    }

    /** Called by ElytraTarget before contact, not by the attack callback. */
    public boolean prepareElytraTargetDive(LocalPlayer player, LivingEntity target, int leadTicks) {
        if (elytraCycleActive) return elytraCycleFromTarget && elytraCycleTargetId == target.getId();
        if (!canPrepareElytraTargetDive(player, target)) return false;
        if (!player.isFallFlying() || !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return false;
        int extraTicks = Math.max(0, leadTicks);
        int forecastTicks = predictionTicks.get() + extraTicks;
        SmashTrajectory.Prediction prediction = predictSmashContact(player, target, forecastTicks);
        if (prediction != SmashTrajectory.Prediction.REACHABLE) {
            if (prediction == SmashTrajectory.Prediction.BLOCKED
                    && ElytraDiveTrace.INSTANCE.active() && player.tickCount % 5 == 0) {
                ElytraDiveTrace.INSTANCE.event("AttributeSwap prepRejected reason=blocked_descent target="
                        + target.getId() + " forecastTicks=" + forecastTicks);
            }
            return false;
        }
        if (!beginElytraCycle(player, target, true, TARGET_DIVE_TIMEOUT_TICKS + extraTicks, forecastTicks)) return false;
        return true;
    }

    private boolean shouldBypassAuraCooldown(LocalPlayer player, LivingEntity target, int maceSlot) {
        if (!isEnabled()) return false;
        if (!isHeightMaceEnabled()) return false;
        if (player == null || !isValidTarget(target)
                || !ElytraTargetReachability.isSafeMaceTarget(target)) return false;
        if (elytraCycleActive) return false;
        if (player.isUsingItem()) return false;
        if (maceSlot < 0 || maceSlot >= 9) return false;
        if (isBlockingShieldTarget(target)) return false;
        // Fast swap attacks are only meaningful in the descending smash window.
        // Do not turn an available mace into a global cooldown bypass.
        if (!MaceItem.canSmashAttack(player) || player.getDeltaMovement().y >= 0.0) return false;
        if (player.getAttackStrengthScale(AttackPressing.ATTACK_LOOKAHEAD_TICKS) < 0.9f
                || target.hurtTime > AttackPressing.ATTACK_LOOKAHEAD_TICKS) return false;
        if (System.currentTimeMillis() - lastFastAuraMaceAttackMs < nextFastAuraMaceDelayMs) return false;
        return !player.getMainHandItem().is(Items.MACE)
                || ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot() != maceSlot;
    }

    private boolean canAttackNow(LocalPlayer player) {
        if (player == null) return false;
        if (player.isUsingItem()) return false;
        return AttackPressing.INSTANCE.isCooldownComplete(
                player, false, AttackPressing.ATTACK_LOOKAHEAD_TICKS);
    }

    private boolean canUseBestDamageWeaponNow(LocalPlayer player) {
        if (player == null) return false;
        if (player.isUsingItem()) return false;
        return isLegacyAttackProtocol() || canAttackNow(player);
    }

    private QueueResult tryBestDamageWeaponForAura(LivingEntity target, boolean keepSprint) {
        LocalPlayer player = mc.player;
        if (!shouldUseBestDamageWeaponForAura()) return QueueResult.PASS;
        if (!canUseBestDamageWeaponNow(player)) return QueueResult.PASS;

        BestWeaponPrepareResult result = prepareBestDamageWeaponForAttack(player, target);
        return switch (result) {
            case NONE, PREPARED -> QueueResult.PASS;
            case FAILED -> QueueResult.SUPPRESS;
        };
    }

    private BestWeaponPrepareResult prepareBestDamageWeaponForNextHit(LocalPlayer player, LivingEntity target) {
        if (!shouldUseBestDamageWeaponForNextHit()) return BestWeaponPrepareResult.NONE;
        if (!canUseBestDamageWeaponNow(player)) return BestWeaponPrepareResult.NONE;
        return prepareBestDamageWeaponForAttack(player, target);
    }

    private BestWeaponPrepareResult prepareBestDamageWeaponForAttack(LocalPlayer player, LivingEntity target) {
        int weaponSlot = findBestDamageWeaponSlot(player, target);
        if (weaponSlot == -1) return BestWeaponPrepareResult.NONE;
        return selectBestDamageWeaponSlot(player, weaponSlot)
                ? BestWeaponPrepareResult.PREPARED
                : BestWeaponPrepareResult.FAILED;
    }

    private boolean selectBestDamageWeaponSlot(LocalPlayer player, int hotbarSlot) {
        if (player == null || hotbarSlot < 0 || hotbarSlot >= 9) return false;
        int selectedSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
        if (selectedSlot == hotbarSlot) return true;
        return InventorySwap.INSTANCE.selectHotbar(hotbarSlot);
    }

    private boolean isLegacyAttackProtocol() {
        return ProtocolUtil.isLegacyAttackProtocol(protocolHeuristicSources.getAll());
    }

    private boolean shouldUseSmartMace(LocalPlayer player, LivingEntity target, ItemStack maceStack) {
        if (player == null || target == null || maceStack == null || !maceStack.is(Items.MACE)) return false;
        if (!isValidTarget(target)) return false;
        if (isBlockingShieldTarget(target)) return false;

        ItemStack currentStack = player.getMainHandItem();
        double currentDamage = estimateAppliedAttackDamage(player, target, currentStack);
        double maceDamage = estimateAppliedAttackDamage(player, target, maceStack);
        return maceDamage > currentDamage + 0.05;
    }

    private double estimateAppliedAttackDamage(LocalPlayer player, LivingEntity target, ItemStack stack) {
        return estimateAppliedAttackDamage(player, target, stack, 1.0);
    }

    private double estimateAppliedAttackDamage(LocalPlayer player, LivingEntity target, ItemStack stack, double cooldownProgress) {
        if (player == null || target == null) return 0.0;

        double rawDamage = estimateRawAttackDamage(player, target, stack, cooldownProgress);
        return applyEstimatedArmorReduction(target, rawDamage, stack);
    }

    private double estimateRawAttackDamage(LocalPlayer player, LivingEntity target, ItemStack stack) {
        return estimateRawAttackDamage(player, target, stack, 1.0);
    }

    private double estimateRawAttackDamage(LocalPlayer player, LivingEntity target, ItemStack stack, double cooldownProgress) {
        double safeCooldown = Mth.clamp(cooldownProgress, 0.0, 1.0);
        double damage = 1.0 + getMainHandAttackDamage(stack);
        damage += estimateWeaponEnchantmentDamageBonus(target, stack);
        if (safeCooldown < 1.0) {
            damage *= 0.2 + safeCooldown * safeCooldown * 0.8;
        }
        damage += estimateMaceBonusDamage(player, target, stack);

        if (safeCooldown > 0.9 && canEstimateCriticalHit(player, target)) {
            damage *= 1.5;
        }

        return Math.max(0.0, damage);
    }

    private double estimateWeaponEnchantmentDamageBonus(LivingEntity target, ItemStack stack) {
        if (target == null || stack == null || stack.isEmpty()) return 0.0;

        int sharpness = getEnchantmentLevel(stack, Enchantments.SHARPNESS);
        double bonus = sharpness > 0 ? 0.5 * sharpness + 0.5 : 0.0;

        int smite = getEnchantmentLevel(stack, Enchantments.SMITE);
        if (smite > 0 && target.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE)) {
            bonus += 2.5 * smite;
        }

        int bane = getEnchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS);
        if (bane > 0 && target.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            bonus += 2.5 * bane;
        }

        return bonus;
    }

    private double estimateMaceBonusDamage(LocalPlayer player, LivingEntity target, ItemStack stack) {
        if (player == null || target == null || stack == null || !stack.is(Items.MACE)) return 0.0;
        if (!shouldMaceSmashApply(player)) return 0.0;

        double fallDistance = Math.max(0.0, player.fallDistance);
        double baseSmash;
        if (fallDistance <= 3.0) {
            baseSmash = 4.0 * fallDistance;
        } else if (fallDistance <= 8.0) {
            baseSmash = 12.0 + 2.0 * (fallDistance - 3.0);
        } else {
            baseSmash = 22.0 + fallDistance - 8.0;
        }

        int densityLevel = getEnchantmentLevel(stack, Enchantments.DENSITY);
        double densityBonus = densityLevel > 0 ? 0.5 * densityLevel * fallDistance : 0.0;
        return baseSmash + densityBonus;
    }

    private boolean shouldMaceSmashApply(LocalPlayer player) {
        return player != null && player.fallDistance > 1.5f && !player.isFallFlying();
    }

    private double applyEstimatedArmorReduction(LivingEntity target, double damage, ItemStack weaponStack) {
        if (target == null || damage <= 0.0) return 0.0;

        double armor = target.getAttributeValue(Attributes.ARMOR);
        if (armor <= 0.0) return damage;

        double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double toughnessFactor = 2.0 + toughness / 4.0;
        double armorEffectiveness = Mth.clamp(
                armor - damage / toughnessFactor,
                armor * 0.2,
                20.0
        ) / 25.0;

        int breachLevel = getEnchantmentLevel(weaponStack, Enchantments.BREACH);
        if (breachLevel > 0) {
            armorEffectiveness = Mth.clamp(armorEffectiveness - 0.15 * breachLevel, 0.0, 1.0);
        }

        return damage * (1.0 - armorEffectiveness);
    }

    private boolean canEstimateCriticalHit(LocalPlayer player, LivingEntity target) {
        return player != null
                && target != null
                && player.fallDistance > 0.0f
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger()
                && !player.isSprinting();
    }

    private LivingEntity resolveCrosshairTarget() {
        if (!(mc.hitResult instanceof EntityHitResult ehr)) return null;
        if (!(ehr.getEntity() instanceof LivingEntity living)) return null;
        return living;
    }

    private boolean isValidTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || target.isRemoved()) return false;
        return !onlyPlayers.get() || target instanceof Player;
    }

    private double getMainHandAttackDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0;

        final double[] add = {0.0};
        final double[] mulBase = {0.0};
        final double[] mulTotal = {1.0};
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier, display) -> {
            if (!attribute.equals(Attributes.ATTACK_DAMAGE)) {
                return;
            }

            AttributeModifier.Operation op = modifier.operation();
            double amount = modifier.amount();
            if (op == AttributeModifier.Operation.ADD_VALUE) {
                add[0] += amount;
            } else if (op == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                mulBase[0] += amount;
            } else if (op == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                mulTotal[0] *= 1.0 + amount;
            }
        });

        return (add[0] + mulBase[0]) * mulTotal[0];
    }

    private double getMainHandAttackSpeed(LocalPlayer player, ItemStack stack) {
        return estimateMainHandAttributeValue(player, stack, Attributes.ATTACK_SPEED, 4.0);
    }

    private double estimateMainHandAttributeValue(LocalPlayer player,
                                                  ItemStack stack,
                                                  Holder<Attribute> attribute,
                                                  double fallbackBase) {
        double base = player != null ? player.getAttributeBaseValue(attribute) : fallbackBase;
        if (stack == null || stack.isEmpty()) return Math.max(0.1, base);

        final double[] add = {0.0};
        final double[] mulBase = {0.0};
        final double[] mulTotal = {1.0};
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (candidateAttribute, modifier, display) -> {
            if (!candidateAttribute.equals(attribute)) {
                return;
            }

            AttributeModifier.Operation op = modifier.operation();
            double amount = modifier.amount();
            if (op == AttributeModifier.Operation.ADD_VALUE) {
                add[0] += amount;
            } else if (op == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                mulBase[0] += amount;
            } else if (op == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                mulTotal[0] *= 1.0 + amount;
            }
        });

        double value = (base + add[0] + base * mulBase[0]) * mulTotal[0];
        return Math.max(0.1, value);
    }

    private int findBestDamageWeaponSlot(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null) return -1;
        if (!isValidTarget(target)) return -1;
        if (isBlockingShieldTarget(target)) return -1;

        ItemStack currentStack = player.getMainHandItem();

        int selectedSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
        double currentScore = estimateBestDamageWeaponScore(player, target, currentStack);
        double bestScore = currentScore + 0.05;
        int bestSlot = -1;

        for (int i = 0; i < 9; i++) {
            if (i == selectedSlot) continue;

            ItemStack stack = player.getInventory().getItem(i);
            if (!isBestDamageWeaponCandidate(stack)) continue;

            double score = estimateBestDamageWeaponScore(player, target, stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private boolean isBestDamageWeaponCandidate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return getMainHandAttackDamage(stack) > 0.0;
    }

    private double estimateBestDamageWeaponScore(LocalPlayer player, LivingEntity target, ItemStack stack) {
        if (player == null || target == null || stack == null) return 0.0;

        double damage = estimateAppliedAttackDamage(player, target, stack, 1.0);
        if (isLegacyAttackProtocol()) {
            return damage;
        }

        double attackSpeed = getMainHandAttackSpeed(player, stack);
        return damage * attackSpeed;
    }

    private int findAxeSlot(LocalPlayer player) {
        if (player == null) return -1;
        for (int i = 0; i < InventorySwap.INSTANCE.combatSearchEnd(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!InventorySlotLocks.blocks(i, this) && isAxe(stack)) return i;
        }
        return -1;
    }

    private boolean isAxe(ItemStack stack) {
        return stack != null && (
                stack.is(Items.WOODEN_AXE) ||
                        stack.is(Items.STONE_AXE) ||
                        stack.is(Items.IRON_AXE) ||
                        stack.is(Items.GOLDEN_AXE) ||
                        stack.is(Items.DIAMOND_AXE) ||
                        stack.is(Items.NETHERITE_AXE)
        );
    }

    private boolean isUsingShield(Player target) {
        return target != null
                && target.isUsingItem()
                && target.getUseItem().is(Items.SHIELD);
    }

    private boolean isShieldFacingPlayer(Player target) {
        if (mc.player == null || target == null) return false;

        Vec3 toPlayer = mc.player.getBoundingBox().getCenter().subtract(target.getEyePosition());
        float yawToPlayer = (float) Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x)) - 90.0f;
        float diff = Mth.wrapDegrees(target.getYRot() - yawToPlayer);

        return Math.abs(diff) < 90.0f;
    }

    private boolean hasPredictedSmashContact(LocalPlayer player, LivingEntity target) {
        return hasPredictedSmashContact(player, target, predictionTicks.get());
    }

    private boolean hasPredictedSmashContact(LocalPlayer player, LivingEntity target, int forecastTicks) {
        return predictSmashContact(player, target, forecastTicks) == SmashTrajectory.Prediction.REACHABLE;
    }

    private SmashTrajectory.Prediction predictSmashContact(LocalPlayer player, LivingEntity target, int forecastTicks) {
        if (!ElytraTargetReachability.isSafeMaceTarget(target)) return SmashTrajectory.Prediction.UNREACHABLE;
        KillAura aura = Modules.get(KillAura.class);
        double reach = aura != null && aura.isEnabled() ? aura.getEffectiveRange(target) : 3.0;
        return SmashTrajectory.predict(player, player.position(), player.getEyeHeight(net.minecraft.world.entity.Pose.STANDING),
                player.getBbWidth() / 2.0, player.getDeltaMovement(), target.getBoundingBox(),
                target.getDeltaMovement(), player.isFallFlying() ? 0 : player.fallDistance,
                minFallDistance.get(), minDownVelocity.get(), minApproachSpeed.get(), forecastTicks,
                maxPredictedHorizontalDistance.get(), reach, player.getGravity());
    }

    private double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private int findElytraRemovalSlot(LocalPlayer player) {
        if (player.inventoryMenu == null || !player.inventoryMenu.getSlot(CHEST_SCREEN_SLOT).mayPickup(player)) return -1;
        int bestChestSlot = -1;
        int bestChestScore = -1;
        int firstEmptySlot = -1;

        int end = InventorySwap.INSTANCE.combatSearchEnd();
        for (int i = 0; i < end; i++) {
            if (InventorySlotLocks.blocks(i, this)) continue;
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                if (firstEmptySlot == -1) firstEmptySlot = i;
                continue;
            }

            int score = getChestDefenseScore(stack);
            if (score >= 0 && !player.isCreative() && getEnchantmentLevel(stack, Enchantments.BINDING_CURSE) > 0) continue;
            if (score > bestChestScore) {
                bestChestScore = score;
                bestChestSlot = i;
            }
        }

        if (bestChestSlot != -1) return bestChestSlot;
        return firstEmptySlot;
    }

    private int getChestDefenseScore(ItemStack stack) {
        if (!isChestplate(stack)) return -1;

        int base =
                stack.is(Items.LEATHER_CHESTPLATE) ? 3 :
                        stack.is(Items.COPPER_CHESTPLATE) ? 4 :
                                stack.is(Items.CHAINMAIL_CHESTPLATE) ? 5 :
                                        stack.is(Items.GOLDEN_CHESTPLATE) ? 5 :
                                                stack.is(Items.IRON_CHESTPLATE) ? 6 :
                                                        stack.is(Items.DIAMOND_CHESTPLATE) ? 8 :
                                                                stack.is(Items.NETHERITE_CHESTPLATE) ? 9 : 0;
        return base;
    }

    private boolean isChestplate(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.is(Items.ELYTRA)) return false;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private int findCycleMaceSlot(LocalPlayer player) {
        if (player == null) return -1;
        int selected = InventorySwap.INSTANCE.serverSelectedSlot();
        if (player.getInventory().getItem(selected).is(Items.MACE)
                && !isUnsafeWindBurstMace(player, player.getInventory().getItem(selected))) {
            return selected;
        }
        int end = InventorySwap.INSTANCE.combatSearchEnd();
        for (int slot = 0; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.MACE) && !isUnsafeWindBurstMace(player, stack)) return slot;
        }
        return -1;
    }

    private int findDesiredMaceSlot(LocalPlayer player, LivingEntity target) {
        int start = 0;
        int end = InventorySwap.INSTANCE.combatSearchEnd();
        return findBestDamageMaceSlot(player, target, start, end);
    }

    private int findDesiredHotbarMaceSlot(LocalPlayer player, LivingEntity target) {
        if (player == null) return -1;
        return findBestDamageMaceSlot(player, target, 0, 9);
    }

    private int findBestDamageMaceSlot(LocalPlayer player, LivingEntity target, int startInclusive, int endExclusive) {
        if (player == null || target == null) return -1;

        int selectedSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
        double currentDamage = estimateAppliedAttackDamage(player, target, player.getMainHandItem());
        double bestDamage = currentDamage + 0.05;
        int bestSlot = -1;

        for (int i = startInclusive; i < endExclusive; i++) {
            if (i == selectedSlot) continue;

            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(Items.MACE)) continue;
            if (isUnsafeWindBurstMace(player, stack)) continue;
            if (!shouldUseSmartMace(player, target, stack)) continue;

            double damage = estimateAppliedAttackDamage(player, target, stack);
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private int getEnchantmentLevel(ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> key) {
        if (stack == null || stack.isEmpty() || mc.level == null) return 0;
        var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.getValue(key);
        if (enchantment == null) return 0;
        Holder<Enchantment> entry = registry.wrapAsHolder(enchantment);
        return EnchantmentHelper.getItemEnchantmentLevel(entry, stack);
    }

    private boolean isUnsafeWindBurstMace(LocalPlayer player, ItemStack mace) {
        if (!isWindBurstSafetyEnabled()) return false;
        if (player == null || mc.level == null || mace == null || mace.isEmpty()) return false;

        int windBurstLevel = getEnchantmentLevel(mace, Enchantments.WIND_BURST);
        if (windBurstLevel <= 0) return false;

        NoFall noFall = Modules.get(NoFall.class);
        if (noFall != null && noFall.isEnabled()) {
            logWindBurstDecision(player, windBurstLevel, null, null, "ALLOW_NOFALL", false);
            return false;
        }

        float healthBudget = getWindBurstHealthBudget(player);
        float minRemainingHealth = windBurstMinRemainingHealth.get();
        if (healthBudget <= minRemainingHealth) {
            logWindBurstDecision(player, windBurstLevel, null, null, "BLOCK_CURRENT_HP_AT_OR_BELOW_LIMIT", true);
            return true;
        }

        WindBurstImpulse impulse = calculateWindBurstImpulse(player, windBurstLevel);
        PredictedWindBurstFall fall = predictWindBurstFall(player, impulse.vertical());
        FallDamageEstimate damage = estimateFallDamage(player, fall.fallDistance(), fall.damagePerDistance());
        float predictedDamage = damage.finalDamage();
        float remainingHealth = healthBudget - predictedDamage;
        if (remainingHealth >= minRemainingHealth) {
            logWindBurstDecision(player, windBurstLevel, impulse, fall, damage, "ALLOW_PREDICTED_HP_OK", false);
            return false;
        }

        if (!windBurstStrictElytra.get() && hasElytraFallback(player)) {
            logWindBurstDecision(player, windBurstLevel, impulse, fall, damage, "ALLOW_ELYTRA_FALLBACK", false);
            return false;
        }

        requestOffhandTotemForWindBurst(player);
        boolean unsafe = !hasTotemInOffhand(player);
        logWindBurstDecision(player, windBurstLevel, impulse, fall, damage, unsafe ? "BLOCK_PREDICTED_HP_LOW" : "ALLOW_OFFHAND_TOTEM", unsafe);
        return unsafe;
    }

    private boolean hasTotemInOffhand(LocalPlayer player) {
        return player != null && player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private void requestOffhandTotemForWindBurst(LocalPlayer player) {
        Offhand offhand = Modules.get(Offhand.class);
        if (offhand != null) {
            offhand.ensureTotemForDanger(player);
        }
    }

    private PredictedWindBurstFall predictWindBurstFall(LocalPlayer player, double verticalImpulse) {
        AABB baseBox = player.getBoundingBox();
        Vec3 offset = Vec3.ZERO;
        Vec3 velocity = player.getDeltaMovement().add(0.0, verticalImpulse, 0.0);
        double fallDistance = Math.max(0.0, player.fallDistance);
        double maxRise = 0.0;
        int ceilingCuts = 0;

        for (int tick = 1; tick <= WIND_BURST_PREDICTION_TICKS; tick++) {
            Vec3 previousOffset = offset;
            offset = offset.add(velocity);
            maxRise = Math.max(maxRise, offset.y);

            AABB previousBox = baseBox.move(previousOffset);
            AABB nextBox = baseBox.move(offset);
            AABB swept = union(previousBox, nextBox).inflate(WIND_BURST_SWEEP_MARGIN);
            if (hasSolidCollision(player, swept)) {
                if (velocity.y > 0.0) {
                    ceilingCuts++;
                    offset = previousOffset;
                    velocity = new Vec3(velocity.x * 0.4, 0.0, velocity.z * 0.4);
                    continue;
                }

                BlockState landingState = getLandingBlockState(player, nextBox);
                double damageFallDistance = isStalagmiteTip(landingState) ? fallDistance + 2.5 : fallDistance;
                return new PredictedWindBurstFall(
                        damageFallDistance,
                        getFallDamagePerDistance(landingState),
                        tick,
                        blockId(landingState),
                        ceilingCuts,
                        maxRise,
                        false
                );
            }

            if (velocity.y < 0.0) {
                fallDistance += -velocity.y;
            }

            velocity = new Vec3(
                    velocity.x * 0.91,
                    (velocity.y - 0.08) * 0.9800000190734863,
                    velocity.z * 0.91
            );
        }

        return new PredictedWindBurstFall(fallDistance, 1.0f, WIND_BURST_PREDICTION_TICKS, "timeout", ceilingCuts, maxRise, true);
    }

    private WindBurstImpulse calculateWindBurstImpulse(LocalPlayer player, int windBurstLevel) {
        Vec3 explosionPos = player.position();
        Vec3 affectedPos = player.getEyePosition();
        double blastDiameter = WIND_BURST_EXPLOSION_RADIUS * 2.0;
        double distanceRatio = Math.sqrt(player.distanceToSqr(explosionPos)) / blastDiameter;
        if (distanceRatio > 1.0) {
            return new WindBurstImpulse(0.0, 0.0, distanceRatio, getWindBurstKnockbackMultiplier(windBurstLevel), 0.0);
        }

        Vec3 direction = affectedPos.subtract(explosionPos);
        if (direction.lengthSqr() < 1.0E-6) {
            direction = new Vec3(0.0, 1.0, 0.0);
        } else {
            direction = direction.normalize();
        }

        double exposure = calculateExplosionExposure(explosionPos, player);
        double resistance = player.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
        double multiplier = getWindBurstKnockbackMultiplier(windBurstLevel);
        double vertical = direction.y
                * (1.0 - distanceRatio)
                * exposure
                * multiplier
                * (1.0 - resistance);
        return new WindBurstImpulse(vertical, exposure, distanceRatio, multiplier, resistance);
    }

    private double calculateExplosionExposure(Vec3 source, LocalPlayer player) {
        AABB box = player.getBoundingBox();
        double xStep = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double yStep = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double zStep = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double xOffset = (1.0 - Math.floor(1.0 / xStep) * xStep) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zStep) * zStep) / 2.0;
        int clear = 0;
        int total = 0;

        for (double x = 0.0; x <= 1.0; x += xStep) {
            for (double y = 0.0; y <= 1.0; y += yStep) {
                for (double z = 0.0; z <= 1.0; z += zStep) {
                    Vec3 sample = new Vec3(
                            lerp(x, box.minX, box.maxX) + xOffset,
                            lerp(y, box.minY, box.maxY),
                            lerp(z, box.minZ, box.maxZ) + zOffset
                    );
                    HitResult hit = mc.level.clip(new ClipContext(
                            sample,
                            source,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            player
                    ));
                    if (hit.getType() == HitResult.Type.MISS) {
                        clear++;
                    }
                    total++;
                }
            }
        }

        return total == 0 ? 0.0 : (double) clear / total;
    }

    private double getWindBurstKnockbackMultiplier(int level) {
        return switch (level) {
            case 1 -> 1.2;
            case 2 -> 1.75;
            case 3 -> 2.2;
            default -> 1.2 + Math.max(0, level - 1) * 0.5;
        };
    }

    private FallDamageEstimate estimateFallDamage(LocalPlayer player, double fallDistance, float damagePerDistance) {
        if (player == null || fallDistance <= 0.0 || damagePerDistance <= 0.0f) {
            return new FallDamageEstimate(0.0, 0.0f, 0.0, 0.0, 0.0, 0.0f, 0);
        }

        double safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        double fallMultiplier = player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        double unsafeFallDistance = fallDistance + 1.0E-6 - safeFallDistance;
        int rawDamage = (int) Math.floor(
                unsafeFallDistance
                        * damagePerDistance
                        * fallMultiplier
        );
        if (rawDamage <= 0) {
            return new FallDamageEstimate(rawDamage, 0.0f, unsafeFallDistance, safeFallDistance, fallMultiplier, 0.0f, 0);
        }

        float amount = rawDamage;
        int resistanceReduction = 0;
        MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            resistanceReduction = (resistance.getAmplifier() + 1) * 5;
            amount = Math.max(amount * (25 - resistanceReduction) / 25.0f, 0.0f);
        }

        float protection = getFallProtectionAmount(player);
        if (protection > 0.0f) {
            amount *= 1.0f - Math.min(protection, 20.0f) / 25.0f;
        }

        return new FallDamageEstimate(rawDamage, Math.max(0.0f, amount), unsafeFallDistance, safeFallDistance, fallMultiplier, protection, resistanceReduction);
    }

    private float getFallProtectionAmount(LocalPlayer player) {
        float protection = 0.0f;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack == null || stack.isEmpty()) continue;
            protection += getEnchantmentLevel(stack, Enchantments.PROTECTION);
        }
        protection += getEnchantmentLevel(player.getItemBySlot(EquipmentSlot.FEET), Enchantments.FEATHER_FALLING) * 3.0f;
        return protection;
    }

    private float getWindBurstHealthBudget(LocalPlayer player) {
        float absorption = Math.max(0.0f, player.getAbsorptionAmount());
        MobEffectInstance absorptionEffect = player.getEffect(MobEffects.ABSORPTION);
        if (absorptionEffect != null && absorptionEffect.getDuration() < WIND_BURST_ABSORPTION_MIN_TICKS) {
            absorption = 0.0f;
        }
        return Math.max(0.0f, player.getHealth()) + absorption;
    }

    private boolean hasElytraFallback(LocalPlayer player) {
        if (player == null) return false;
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return true;

        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).is(Items.ELYTRA)) {
                return true;
            }
        }

        return false;
    }

    private float getFallDamagePerDistance(BlockState landingState) {
        if (landingState == null) return 1.0f;
        if (landingState.is(Blocks.WATER)
                || landingState.is(Blocks.POWDER_SNOW)
                || landingState.is(Blocks.COBWEB)
                || landingState.is(Blocks.SLIME_BLOCK)) {
            return 0.0f;
        }
        if (landingState.is(Blocks.HAY_BLOCK) || landingState.is(Blocks.HONEY_BLOCK)) {
            return 0.2f;
        }
        if (isStalagmiteTip(landingState)) {
            return 2.0f;
        }
        return 1.0f;
    }

    private BlockState getLandingBlockState(LocalPlayer player, AABB nextBox) {
        BlockPos pos = BlockPos.containing(nextBox.getCenter().x, nextBox.minY - 0.001, nextBox.getCenter().z);
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            state = mc.level.getBlockState(pos.below());
        }
        return state;
    }

    private boolean isStalagmiteTip(BlockState state) {
        return state != null
                && state.is(Blocks.POINTED_DRIPSTONE)
                && state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.UP
                && state.getValue(net.minecraft.world.level.block.SpeleothemBlock.THICKNESS) == SpeleothemThickness.TIP;
    }

    private boolean hasSolidCollision(LocalPlayer player, AABB box) {
        for (var shape : mc.level.getBlockCollisions(player, box)) {
            if (!shape.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String blockId(BlockState state) {
        if (state == null) return "null";
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private void logWindBurstDecision(LocalPlayer player,
                                      int windBurstLevel,
                                      WindBurstImpulse impulse,
                                      PredictedWindBurstFall fall,
                                      String reason,
                                      boolean blocked) {
        logWindBurstDecision(player, windBurstLevel, impulse, fall, null, reason, blocked);
    }

    private void logWindBurstDecision(LocalPlayer player,
                                      int windBurstLevel,
                                      WindBurstImpulse impulse,
                                      PredictedWindBurstFall fall,
                                      FallDamageEstimate damage,
                                      String reason,
                                      boolean blocked) {
        if (!windBurstDebugLog.get() || player == null) return;

        MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
        MobEffectInstance absorptionEffect = player.getEffect(MobEffects.ABSORPTION);
        float absorption = Math.max(0.0f, player.getAbsorptionAmount());
        int absorptionTicks = absorptionEffect != null ? absorptionEffect.getDuration() : -1;
        boolean absorptionCounted = absorption <= 0.0f
                || absorptionEffect == null
                || absorptionTicks >= WIND_BURST_ABSORPTION_MIN_TICKS;

        DebugLog.info(
                "[AttributeSwap][WindBurst] decision=%s blocked=%s wind=%d hp=%.2f absorption=%.2f absorptionTicks=%d absorptionCounted=%s budget=%.2f minRemain=%.2f noFall=%s elytraFallback=%s strictElytra=%s offhandTotem=%s fallDistance=%.2f velocity=(%.3f,%.3f,%.3f) impulseY=%s exposure=%s distRatio=%s kbMul=%s explosionRes=%s predictedFall=%s rawDamage=%s finalDamage=%s unsafeFall=%s safeFall=%s fallMul=%s protection=%s resistReduction=%s landing=%s tick=%s ceilingCuts=%s maxRise=%s timedOut=%s",
                reason,
                blocked,
                windBurstLevel,
                player.getHealth(),
                absorption,
                absorptionTicks,
                absorptionCounted,
                getWindBurstHealthBudget(player),
                windBurstMinRemainingHealth.get(),
                Modules.enabled(NoFall.class),
                hasElytraFallback(player),
                windBurstStrictElytra.get(),
                hasTotemInOffhand(player),
                player.fallDistance,
                player.getDeltaMovement().x,
                player.getDeltaMovement().y,
                player.getDeltaMovement().z,
                impulse != null ? format3(impulse.vertical()) : "n/a",
                impulse != null ? format3(impulse.exposure()) : "n/a",
                impulse != null ? format3(impulse.distanceRatio()) : "n/a",
                impulse != null ? format3(impulse.knockbackMultiplier()) : "n/a",
                impulse != null ? format3(impulse.explosionResistance()) : "n/a",
                fall != null ? format3(fall.fallDistance()) : "n/a",
                damage != null ? format3(damage.rawDamage()) : "n/a",
                damage != null ? format3(damage.finalDamage()) : "n/a",
                damage != null ? format3(damage.unsafeFallDistance()) : "n/a",
                damage != null ? format3(damage.safeFallDistance()) : "n/a",
                damage != null ? format3(damage.fallDamageMultiplier()) : "n/a",
                damage != null ? format3(damage.protection()) : "n/a",
                damage != null ? damage.resistanceReduction() : "n/a",
                fall != null ? fall.landingBlock() : "n/a",
                fall != null ? fall.tick() : "n/a",
                fall != null ? fall.ceilingCuts() : "n/a",
                fall != null ? format3(fall.maxRise()) : "n/a",
                fall != null ? fall.timedOut() : "n/a"
        );
    }

    private enum BestWeaponPrepareResult {
        NONE,
        PREPARED,
        FAILED
    }

    public enum QueueResult {
        PASS,
        SUPPRESS,
        HANDLE
    }

    public enum BestDamageWeaponMode implements EnumValue.IdProvider {
        AURA_NEXT_HIT("aura_next_hit", true, false),
        NEXT_HIT("next_hit", false, true),
        BOTH("both", true, true);

        private final String id;
        private final boolean aura;
        private final boolean nextHit;

        BestDamageWeaponMode(String id, boolean aura, boolean nextHit) {
            this.id = id;
            this.aura = aura;
            this.nextHit = nextHit;
        }

        @Override
        public String getId() {
            return id;
        }

        boolean usesAura() {
            return aura;
        }

        boolean usesNextHit() {
            return nextHit;
        }
    }

    private record WindBurstImpulse(double vertical,
                                    double exposure,
                                    double distanceRatio,
                                    double knockbackMultiplier,
                                    double explosionResistance) {
    }

    private record PredictedWindBurstFall(double fallDistance,
                                          float damagePerDistance,
                                          int tick,
                                          String landingBlock,
                                          int ceilingCuts,
                                          double maxRise,
                                          boolean timedOut) {
    }

    private record FallDamageEstimate(double rawDamage,
                                      float finalDamage,
                                      double unsafeFallDistance,
                                      double safeFallDistance,
                                      double fallDamageMultiplier,
                                      float protection,
                                      int resistanceReduction) {
    }
    /** Attacks before movement use fall state from an already submitted position. */
    static final class CycleAttackWindow {
        private double reportedFall;

        void reset() {
            reportedFall = 0;
        }

        void notePosition(double fallDistance, boolean gliding, boolean grounded) {
            reportedFall = gliding || grounded ? 0 : fallDistance;
        }

        boolean ready(double minimumFall) {
            return reportedFall >= minimumFall;
        }

        void beforeMovement(double minimumFall, Runnable attack) {
            if (ready(minimumFall)) attack.run();
        }
    }

}
