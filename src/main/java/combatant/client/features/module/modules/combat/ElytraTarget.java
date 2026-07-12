/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.events.impl.*;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.*;
import combatant.client.features.module.*;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.ElytraRecastUtil;
import combatant.client.util.player.inventory.FireworkUseController;
import combatant.client.util.player.simulation.PlayerSimulationCache;
import combatant.client.util.target.TargetManager;
import combatant.client.util.world.ExplosionDamageUtil;

import java.util.List;

//todo Description
@ModuleInfo(
        id = "elytratarget",
        displayName = "ElytraTarget",
        category = ModuleCategory.COMBAT
)
public final class ElytraTarget extends Module {

    private static final int PURSUIT_ROTATION_PRIORITY = 80;
    private static final int CRYSTAL_FEAR_ROTATION_PRIORITY = 130;
    private static final int BLOCK_DODGE_ROTATION_PRIORITY = 140;
    private static final int FIREWORK_SYNC_PRIORITY = -1000;
    private static final int FIREWORK_SPEED_FACTOR = 72;
    private static final int FALL_FLYING_RESTART_DELAY_TICKS = 5;
    private static final int HIT_STATUS_READY_TICKS = 5;
    private static final int AUTO_TAKEOFF_COOLDOWN_TICKS = 12;
    private static final double SIDE_RAYCAST_DISTANCE = 14.0;
    private static final double BLOCK_DODGE_SWEEP_MARGIN = 0.08;
    private static final double COLLISION_CLEARANCE_MARGIN = 1.25;
    private static final double GROUND_TARGET_STILL_SPEED = 0.08;
    private static final double ORBIT_MIN_RADIUS = 1.15;
    private static final double ORBIT_MAX_RADIUS = 3.35;
    private static final double ORBIT_ATTACK_RANGE_MARGIN = 0.22;
    private static final double CRYSTAL_FEAR_SWEEP_MARGIN = 0.35;
    private static final double CRYSTAL_FEAR_SAFE_DISTANCE = 7.25;
    private static final double ORBIT_SIDE_SWITCH_DOT = 0.18;
    private static final int TARGET_FOCUS_HOLD_TICKS = 45;
    private static final double TARGET_FOCUS_RANGE_MULTIPLIER = 1.35;
    private static final double[] BLOCK_DODGE_CANDIDATE_ANGLES = {
            -65.0, 65.0, -105.0, 105.0, -35.0, 35.0, 180.0
    };
    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Integer> speedThreshold =
            numCommon(
                    "elytratargetSpeedThreshold",
                    "firework_speed",
                    CommonSettingSchemas.ELYTRA_FIREWORK_SPEED,
                    30,
                    0,
                    100
            );
    private final BooleanValue swapBack =
            boolCommon(
                    "elytratargetSwapBack",
                    "swap_back",
                    CommonSettingSchemas.INVENTORY_RESTORE_ITEM,
                    true
            );
    private final BooleanValue silentSwap =
            visibleWhen(boolCommon(
                    "elytratargetSilentSwap",
                    "silent_swap",
                    CommonSettingSchemas.ELYTRA_FIREWORK_SILENT_SWAP,
                    true
            ), swapBack::get);
    private final ModeValue targetMode =
            modeSetting(
                    "elytratargetTargetMode",
                    "target_mode",
                    "both",
                    "both",
                    "flying",
                    "grounded"
            );
    private final BooleanValue prepareRotations =
            boolCommon(
                    "elytratargetPrepareRotations",
                    "prepare_rotations",
                    CommonSettingSchemas.ELYTRA_PREPARE_ROTATIONS,
                    true
            );
    private final ModeValue pursuitMode =
            modeSetting(
                    "elytratargetPursuitMode",
                    "pursuit_mode",
                    "auto",
                    "off",
                    "chase",
                    "high_pass",
                    "auto"
            );
    private final NumberValue<Float> idealChaseDistance =
            visibleWhen(num(
                    "elytratargetIdealChaseDistance",
                    "ideal_chase_distance",
                    7.0f,
                    2.0f,
                    18.0f
            ), this::isPursuitEnabled);
    private final NumberValue<Float> overtakeHeight =
            visibleWhen(num(
                    "elytratargetOvertakeHeight",
                    "overtake_height",
                    7.0f,
                    2.0f,
                    18.0f
            ), this::usesHighPassSettings);
    private final NumberValue<Float> overtakeForward =
            visibleWhen(num(
                    "elytratargetOvertakeForward",
                    "overtake_forward",
                    4.0f,
                    0.0f,
                    16.0f
            ), this::usesHighPassSettings);
    private final NumberValue<Float> diveHorizontalDistance =
            visibleWhen(num(
                    "elytratargetDiveHorizontalDistance",
                    "dive_horizontal_distance",
                    5.0f,
                    1.0f,
                    12.0f
            ), this::usesHighPassSettings);
    private final NumberValue<Float> attackSetupSpeed =
            visibleWhen(num(
                    "elytratargetAttackSetupSpeed",
                    "attack_setup_speed",
                    0.75f,
                    0.15f,
                    3.0f
            ), this::usesHighPassSettings);
    private final ModeValue recastMode =
            modeSetting(
                    "elytratargetRecastMode",
                    "recast_mode",
                    "grim",
                    "normal",
                    "grim"
            );
    private final BooleanValue autoTakeoff =
            bool(
                    "elytratargetAutoTakeoff",
                    "auto_takeoff",
                    false
            );
    private final NumberValue<Float> targetRange =
            visibleWhen(num(
                    "elytratargetTargetRange",
                    "target_range",
                    42.0f,
                    6.0f,
                    96.0f
            ), this::hasTargetingPurpose);
    private final NumberValue<Float> targetFov =
            visibleWhen(num(
                    "elytratargetTargetFov",
                    "target_fov",
                    120.0f,
                    10.0f,
                    180.0f
            ), this::hasTargetingPurpose);
    private final NumberValue<Integer> pursuitPredictionTicks =
            visibleWhen(num(
                    "elytratargetPursuitPredictionTicks",
                    "pursuit_prediction_ticks",
                    6,
                    0,
                    20
            ), this::hasTargetingPurpose);
    private final BooleanValue solidBlockDodge =
            bool(
                    "elytratargetSolidBlockDodge",
                    "solid_block_dodge",
                    false
            );
    private final NumberValue<Integer> blockDodgePredictionTicks =
            visibleWhen(num(
                    "elytratargetBlockDodgePredictionTicks",
                    "block_dodge_prediction_ticks",
                    10,
                    3,
                    30
            ), solidBlockDodge::get);
    private final NumberValue<Float> blockDodgeMinDamage =
            visibleWhen(num(
                    "elytratargetBlockDodgeMinDamage",
                    "block_dodge_min_damage",
                    5.0f,
                    1.0f,
                    20.0f
            ), solidBlockDodge::get);
    private final NumberValue<Float> blockDodgeHealthBuffer =
            visibleWhen(num(
                    "elytratargetBlockDodgeHealthBuffer",
                    "block_dodge_health_buffer",
                    8.0f,
                    0.0f,
                    20.0f
            ), solidBlockDodge::get);
    private final BooleanValue crystalFear =
            bool(
                    "elytratargetCrystalFear",
                    "crystal_fear",
                    true
            );
    private final NumberValue<Float> crystalFearRange =
            visibleWhen(num(
                    "elytratargetCrystalFearRange",
                    "crystal_fear_range",
                    7.0f,
                    3.0f,
                    12.0f
            ), crystalFear::get);
    private final NumberValue<Integer> crystalFearPredictionTicks =
            visibleWhen(num(
                    "elytratargetCrystalFearPredictionTicks",
                    "crystal_fear_prediction_ticks",
                    3,
                    0,
                    12
            ), crystalFear::get);
    private final NumberValue<Float> crystalFearMinDamage =
            visibleWhen(num(
                    "elytratargetCrystalFearMinDamage",
                    "crystal_fear_min_damage",
                    6.0f,
                    1.0f,
                    20.0f
            ), crystalFear::get);
    private final NumberValue<Float> crystalFearHealthBuffer =
            visibleWhen(num(
                    "elytratargetCrystalFearHealthBuffer",
                    "crystal_fear_health_buffer",
                    7.0f,
                    0.0f,
                    20.0f
            ), crystalFear::get);
    private LivingEntity target;
    private boolean preparingRotation;
    private Rotation pendingRotation;
    private Direction lastDirection;
    private boolean attackWindowActive;
    private PursuitPhase pursuitPhase = PursuitPhase.NONE;
    private boolean pursuitRotationActive;
    private int restartFallFlyingTicks;
    private int hitStatusTicks = HIT_STATUS_READY_TICKS;
    private boolean queuedFireworkUse;
    private int orbitSide = 1;
    private int focusedTargetId = -1;
    private int targetFocusTicks;
    private boolean autoTakeoffPending;
    private boolean queuedAutoTakeoffJump;
    private boolean queuedAutoTakeoffRelease;
    private boolean queuedAutoTakeoffStartGlide;
    private int autoTakeoffGlideDelayTicks;
    private int autoTakeoffCooldownTicks;

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0, vector.z);
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static Vec3 rotateHorizontal(Vec3 vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vec3(
                vector.x * cos - vector.z * sin,
                0.0,
                vector.x * sin + vector.z * cos
        );
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

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
        ElytraRecastUtil.INSTANCE.reset(this);
        closeAttackWindow();
        resetState();
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @EventHandler
    private void onPostSync(EventPostSync event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null) {
            resetState();
            return;
        }

        LocalPlayer player = mc.player;
        if (isAttributeSwapElytraCycleActive()) {
            target = null;
            TargetManager.setElytraTarget(null);
            clearPursuitState();
            clearAutoTakeoffState();
            queuedFireworkUse = false;
            ElytraRecastUtil.INSTANCE.reset(this);
            releaseRotationState();
            return;
        }

        LivingEntity combatTarget = resolveCombatTarget(player);
        boolean validSelf = isValidSelf(player);
        boolean validTarget = isValidTarget(combatTarget);

        target = validSelf && validTarget ? combatTarget : null;
        updateTargetFocus(target);
        TargetManager.setElytraTarget(target);
        boolean emergencyAvoidQueued = queueEmergencyAvoidanceRotation(player);

        if (!validSelf || !validTarget) {
            clearPursuitState();
            queuedFireworkUse = false;
            if (!emergencyAvoidQueued) {
                releaseRotationState();
            }
        } else {
            updatePursuitPhase(player, target);
            tickRestartFallFlying(player);
        }

        if (emergencyAvoidQueued) {
            clearPursuitState();
            queuedFireworkUse = false;
        }
    }

    @EventHandler(priority = -10)
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            resetState();
            return;
        }

        if (hitStatusTicks < HIT_STATUS_READY_TICKS) {
            hitStatusTicks++;
        }
        ElytraRecastUtil.INSTANCE.tick(this, player);

        if (isAttributeSwapElytraCycleActive()) {
            queuedFireworkUse = false;
            clearPursuitState();
            clearAutoTakeoffState();
            ElytraRecastUtil.INSTANCE.reset(this);
            releaseRotationState();
            return;
        }

        tickAutoTakeoff(player);

        boolean emergencyAvoidQueued = queueEmergencyAvoidanceRotation(player);

        if (!isRunning()) {
            queuedFireworkUse = false;
            clearPursuitState();
            if (!ElytraRecastUtil.INSTANCE.isActive(this)) {
                ElytraRecastUtil.INSTANCE.reset(this);
            }
            if (!emergencyAvoidQueued) {
                releaseRotationState();
            }
            return;
        }

        if (emergencyAvoidQueued) {
            queuedFireworkUse = false;
            clearPursuitState();
            return;
        }

        updatePursuitPhase(player, target);
        if (shouldUseFirework(player)) {
            queuedFireworkUse = true;
        }
    }

    @EventHandler(priority = FIREWORK_SYNC_PRIORITY)
    private void onSyncFirework(EventSync event) {
        if (!queuedFireworkUse || !isEnabled()) return;
        queuedFireworkUse = false;

        LocalPlayer player = mc.player;
        if (player == null || !isRunning()) return;
        if (!shouldUseFirework(player)) return;

        FireworkUseController.INSTANCE.use(
                event.getYaw(),
                event.getPitch(),
                true,
                swapBack.get(),
                silentSwap.get(),
                speedThreshold.get(),
                true
        );
    }

    @EventHandler(priority = FIREWORK_SYNC_PRIORITY - 20)
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled()) return;
        if (handleAutoTakeoffMovementInput(event)) return;
        ElytraRecastUtil.INSTANCE.handleMovementInput(this, event);
    }

    @EventHandler(priority = FIREWORK_SYNC_PRIORITY - 20)
    private void onSyncRecast(EventSync event) {
        if (!isEnabled()) return;
        if (handleAutoTakeoffSync(event)) return;
        ElytraRecastUtil.INSTANCE.handleSync(this, event);
    }

    @EventHandler(priority = 0)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled() || event.getType() != RotationUpdateEvent.Type.PRE) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clearPursuitState();
            return;
        }

        if (queueEmergencyAvoidanceRotation(player)) {
            pursuitRotationActive = false;
            return;
        }

        if (!isRunning()) {
            pursuitRotationActive = false;
            return;
        }

        updatePursuitPhase(player, target);
        Rotation pursuitRotation = calculatePursuitRotation(player, target);
        if (pursuitRotation == null) {
            pursuitRotationActive = false;
            return;
        }

        pendingRotation = pursuitRotation;
        pursuitRotationActive = true;
        RotationTarget plan = new RotationTarget(
                pursuitRotation.normalize(),
                target,
                List.of(),
                2,
                2.0f,
                true,
                MovementCorrection.SILENT,
                true,
                null
        );
        RotationManager.INSTANCE.setRotationTarget(plan, PURSUIT_ROTATION_PRIORITY, this);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;
        if (!(event.getPacket() instanceof ClientboundEntityEventPacket packet)) return;
        LivingEntity currentTarget = target;
        if (currentTarget == null || mc.level == null) return;

        if (packet.getEntity(mc.level) == currentTarget
                && (packet.getEventId() == 0 || packet.getEventId() == 3)) {
            hitStatusTicks = 0;
        }
    }

    public LivingEntity getTarget() {
        return target;
    }

    public boolean isRunning() {
        return isEnabled()
                && mc.player != null
                && mc.player.isFallFlying()
                && target != null
                && hasKillAuraGate()
                && !isAttributeSwapElytraCycleActive();
    }

    public boolean canIgnoreKillAuraRotations() {
        return isRunning() && (pursuitRotationActive || preparingRotation || attackWindowActive || pendingRotation != null);
    }

    public boolean isManagingFireworks() {
        return isRunning();
    }

    private void resetState() {
        ElytraRecastUtil.INSTANCE.reset(this);
        target = null;
        TargetManager.setElytraTarget(null);
        preparingRotation = false;
        pendingRotation = null;
        lastDirection = null;
        attackWindowActive = false;
        pursuitPhase = PursuitPhase.NONE;
        pursuitRotationActive = false;
        restartFallFlyingTicks = 0;
        hitStatusTicks = HIT_STATUS_READY_TICKS;
        queuedFireworkUse = false;
        orbitSide = 1;
        focusedTargetId = -1;
        targetFocusTicks = 0;
        autoTakeoffPending = false;
        queuedAutoTakeoffJump = false;
        queuedAutoTakeoffRelease = false;
        queuedAutoTakeoffStartGlide = false;
        autoTakeoffGlideDelayTicks = 0;
        autoTakeoffCooldownTicks = 0;
    }

    private LivingEntity resolveCombatTarget(LocalPlayer player) {
        if (player == null || !hasKillAuraGate() || !hasTargetingPurpose()) {
            return null;
        }

        KillAura killAura = Modules.get(KillAura.class);

        TargetCandidate focused = evaluateFocusedTargetCandidate(player, target);
        if (focused != null) {
            return focused.entity();
        }

        TargetCandidate recentlyAttacked = evaluateFocusedTargetCandidate(player, resolveRecentAttackTarget());
        if (recentlyAttacked != null) {
            return recentlyAttacked.entity();
        }

        LivingEntity auraTarget = killAura.getCurrentTarget();
        TargetCandidate current = evaluateTargetCandidate(player, auraTarget, true, false);
        if (current != null) {
            return current.entity();
        }

        LivingEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (LivingEntity candidate : killAura.findElytraTargetCandidates(targetRange.get(), targetFov.get())) {
            TargetCandidate evaluated = evaluateTargetCandidate(player, candidate, false, false);
            if (evaluated == null) continue;
            if (evaluated.score() < bestScore) {
                bestScore = evaluated.score();
                best = evaluated.entity();
            }
        }
        return best;
    }

    private boolean hasKillAuraGate() {
        KillAura killAura = Modules.get(KillAura.class);
        return killAura != null && killAura.isEnabled();
    }

    private void updateTargetFocus(LivingEntity currentTarget) {
        if (currentTarget != null) {
            focusedTargetId = currentTarget.getId();
            targetFocusTicks = TARGET_FOCUS_HOLD_TICKS;
            return;
        }

        if (targetFocusTicks > 0) {
            targetFocusTicks--;
            if (targetFocusTicks <= 0) {
                focusedTargetId = -1;
            }
        } else {
            focusedTargetId = -1;
        }
    }

    private boolean isFocusedCandidate(LivingEntity candidate) {
        return candidate != null
                && targetFocusTicks > 0
                && focusedTargetId >= 0
                && candidate.getId() == focusedTargetId;
    }

    private LivingEntity resolveRecentAttackTarget() {
        if (TargetManager.getTargetSource() != TargetManager.Source.ATTACK) {
            return null;
        }
        return TargetManager.getTarget();
    }

    private boolean isRecentAttackTarget(LivingEntity candidate) {
        LivingEntity attacked = resolveRecentAttackTarget();
        return candidate != null && attacked != null && candidate.getId() == attacked.getId();
    }

    private TargetCandidate evaluateFocusedTargetCandidate(LocalPlayer player, LivingEntity candidate) {
        if (candidate == null) return null;
        if (!isFocusedCandidate(candidate) && !isRecentAttackTarget(candidate)) return null;
        return evaluateTargetCandidate(player, candidate, true, true);
    }

    private TargetCandidate evaluateTargetCandidate(LocalPlayer player, LivingEntity candidate, boolean currentAuraTarget, boolean focusBypass) {
        if (player == null || !isValidTarget(candidate)) return null;

        boolean focused = focusBypass && (isFocusedCandidate(candidate) || isRecentAttackTarget(candidate));
        Vec3 predictedCenter = predictTargetCenter(candidate, pursuitPredictionTicks.get());
        double maxRange = Math.max(1.0, targetRange.get());
        if (focused) {
            maxRange *= TARGET_FOCUS_RANGE_MULTIPLIER;
        }
        double distanceSq = player.getEyePosition().distanceToSqr(predictedCenter);
        if (distanceSq > maxRange * maxRange) return null;

        float angle = angleToPredictedPoint(player, predictedCenter);
        float maxFov = Mth.clamp(targetFov.get(), 1.0f, 180.0f);
        if (!focused && maxFov < 180.0f && angle > maxFov * 0.5f) return null;

        Reachability reachability = evaluateReachability(player, candidate, predictedCenter);
        if (reachability == null) return null;

        double score = distanceSq
                + (focused ? 0.0 : angle * angle * 0.18)
                + (reachability.raised() ? 36.0 : 0.0)
                + (currentAuraTarget ? -64.0 : 0.0)
                + (focused ? -192.0 : 0.0)
                + (candidate == target ? -48.0 : 0.0);
        return new TargetCandidate(candidate, predictedCenter, reachability.point(), score);
    }

    private float angleToPredictedPoint(LocalPlayer player, Vec3 point) {
        Rotation rotation = Rotation.lookingAt(point, player.getEyePosition()).normalize();
        return Math.abs(Mth.wrapDegrees(rotation.yaw() - player.getYRot()));
    }

    private Reachability evaluateReachability(LocalPlayer player, LivingEntity candidate, Vec3 predictedCenter) {
        if (player == null || candidate == null || mc.level == null) return null;

        if (isApproachCorridorClear(player, predictedCenter)) {
            return new Reachability(predictedCenter, false);
        }

        Vec3 playerPos = player.position();
        Vec3 horizontalToTarget = horizontal(predictedCenter.subtract(playerPos));
        Vec3 direction = horizontalToTarget.lengthSqr() > 1.0E-5
                ? horizontalToTarget.normalize()
                : horizontal(player.getViewVector(1.0f)).normalize();
        if (direction.lengthSqr() < 1.0E-5) {
            direction = new Vec3(0.0, 0.0, 1.0);
        }

        double highPassHeight = usesHighPassSettings()
                ? Math.max(2.0, overtakeHeight.get())
                : Math.max(3.0, candidate.getBoundingBox().getYsize() + 2.0);
        double forward = usesHighPassSettings()
                ? Math.max(0.0, overtakeForward.get())
                : 3.0;
        Vec3 highApproach = predictedCenter
                .add(direction.scale(forward))
                .add(0.0, highPassHeight, 0.0);

        if (isApproachCorridorClear(player, highApproach) && isRayClear(player, highApproach, predictedCenter)) {
            return new Reachability(highApproach, true);
        }

        return null;
    }

    private boolean isAttributeSwapElytraCycleActive() {
        AttributeSwap attributeSwap = Modules.get(AttributeSwap.class);
        return attributeSwap != null && attributeSwap.isElytraCycleActive();
    }

    private boolean isValidSelf(LocalPlayer player) {
        return player != null
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
                && player.isFallFlying();
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (!(entity instanceof Player playerTarget)) return false;
        if (!entity.isAlive() || entity.isRemoved()) return false;

        String mode = String.valueOf(targetMode.get());
        if ("flying".equals(mode)) {
            return playerTarget.isFallFlying();
        }
        if ("grounded".equals(mode)) {
            return !playerTarget.isFallFlying();
        }
        return true;
    }

    private boolean isPursuitEnabled() {
        return !"off".equals(pursuitMode.get());
    }

    private boolean isAutoTakeoffEnabled() {
        return autoTakeoff.get();
    }

    private boolean hasTargetingPurpose() {
        return isPursuitEnabled() || isAutoTakeoffEnabled();
    }

    private boolean usesHighPassSettings() {
        String mode = pursuitMode.get();
        return "high_pass".equals(mode) || "auto".equals(mode);
    }

    private void updatePursuitPhase(LocalPlayer player, LivingEntity currentTarget) {
        if (!isPursuitEnabled() || player == null || currentTarget == null) {
            clearPursuitState();
            return;
        }

        boolean wantsHighPass = wantsHighPass(player, currentTarget);
        if (!wantsHighPass) {
            pursuitPhase = PursuitPhase.CHASE;
            preparingRotation = false;
            attackWindowActive = false;
            return;
        }

        Vec3 targetPoint = predictTargetCenter(currentTarget, pursuitPredictionTicks.get());
        Vec3 playerPos = player.position();
        double horizontalDistance = horizontalDistance(playerPos, targetPoint);
        double heightAboveTarget = playerPos.y - targetPoint.y;
        double horizontalSpeed = player.getDeltaMovement().horizontalDistance();
        double wantedHeight = Math.max(1.0, overtakeHeight.get());
        double diveDistance = Math.max(1.0, diveHorizontalDistance.get());

        boolean highEnough = heightAboveTarget >= wantedHeight * 0.55;
        boolean closeEnough = horizontalDistance <= diveDistance;
        boolean fastEnough = horizontalSpeed >= attackSetupSpeed.get() || player.fallDistance >= 1.4f;
        boolean descending = player.getDeltaMovement().y <= -0.03 || player.fallDistance >= 1.2f;

        if (highEnough && closeEnough && (fastEnough || descending)) {
            pursuitPhase = PursuitPhase.DIVE;
            preparingRotation = false;
            attackWindowActive = true;
            return;
        }

        if (heightAboveTarget < wantedHeight * 0.35 || player.getDeltaMovement().y < -0.18) {
            pursuitPhase = PursuitPhase.CLIMB;
        } else {
            pursuitPhase = PursuitPhase.OVERPASS;
        }
        preparingRotation = prepareRotations.get();
        attackWindowActive = false;
    }

    private void clearPursuitState() {
        preparingRotation = false;
        pendingRotation = null;
        attackWindowActive = false;
        pursuitPhase = PursuitPhase.NONE;
        pursuitRotationActive = false;
    }

    private void closeAttackWindow() {
        attackWindowActive = false;
    }

    private Rotation calculatePursuitRotation(LocalPlayer player, LivingEntity currentTarget) {
        if (!isPursuitEnabled() || player == null || currentTarget == null || pursuitPhase == PursuitPhase.NONE) {
            return null;
        }

        Vec3 point = calculatePursuitPoint(player, currentTarget);
        if (point == null) return null;

        point = adjustPursuitPointForCrystalFear(player, point);
        if (solidBlockDodge.get()) {
            point = adjustPursuitPointForCollision(player, point);
        }

        return Rotation.lookingAt(point, player.getEyePosition()).normalize();
    }

    private Vec3 calculatePursuitPoint(LocalPlayer player, LivingEntity currentTarget) {
        Vec3 targetCenter = predictTargetCenter(currentTarget, pursuitPredictionTicks.get());
        Vec3 playerPos = player.position();
        Vec3 horizontalToTarget = horizontal(targetCenter.subtract(playerPos));
        double horizontalDistance = horizontalToTarget.length();
        Vec3 direction = horizontalDistance > 1.0E-4
                ? horizontalToTarget.scale(1.0 / horizontalDistance)
                : horizontal(player.getViewVector(1.0f));
        if (direction.lengthSqr() < 1.0E-5) {
            direction = new Vec3(0.0, 0.0, 1.0);
        } else {
            direction = direction.normalize();
        }

        return switch (pursuitPhase) {
            case CLIMB -> targetCenter
                    .add(direction.scale(Math.max(1.0, overtakeForward.get() * 0.65)))
                    .add(0.0, Math.max(2.0, overtakeHeight.get()), 0.0);
            case OVERPASS -> targetCenter
                    .add(direction.scale(Math.max(0.0, overtakeForward.get())))
                    .add(0.0, Math.max(1.5, overtakeHeight.get() * 0.75), 0.0);
            case DIVE -> targetCenter.add(0.0, currentTarget.getBoundingBox().getYsize() * -0.15, 0.0);
            case CHASE -> calculateDirectChasePoint(player, currentTarget, targetCenter, direction, horizontalDistance);
            case NONE -> null;
        };
    }

    private Vec3 calculateDirectChasePoint(LocalPlayer player,
                                           LivingEntity currentTarget,
                                           Vec3 targetCenter,
                                           Vec3 direction,
                                           double horizontalDistance) {
        if (shouldUseNoRotationOrbit(player, currentTarget, horizontalDistance)) {
            return calculateNoRotationOrbitPoint(player, currentTarget, targetCenter, direction, horizontalDistance);
        }

        double attackBand = getAuraAttackBand(currentTarget);
        double configuredDistance = Math.max(2.0, idealChaseDistance.get());
        double idealDistance = Mth.clamp(configuredDistance, attackBand * 0.85, Math.max(attackBand * 1.55, attackBand + 1.0));
        double passThrough = horizontalDistance < idealDistance
                ? Mth.clamp(idealDistance - horizontalDistance, 0.45, Math.max(1.2, attackBand * 0.7))
                : Math.min(Math.max(1.15, attackBand * 0.55), horizontalDistance * 0.16);
        double lift = player.getDeltaMovement().y < -0.22 ? 1.5 : 0.0;
        return targetCenter.add(direction.scale(passThrough)).add(0.0, lift, 0.0);
    }

    private boolean shouldUseNoRotationOrbit(LocalPlayer player, LivingEntity currentTarget, double horizontalDistance) {
        if (!shouldOwnAuraNoRotation()) return false;
        if (player == null || currentTarget == null) return false;
        if (currentTarget.isFallFlying()) return false;

        double attackBand = getAuraAttackBand(currentTarget);
        boolean targetIsStill = currentTarget.getDeltaMovement().horizontalDistance() <= GROUND_TARGET_STILL_SPEED;
        boolean targetIsGrounded = currentTarget.onGround() || currentTarget.fallDistance < 0.15f;
        boolean alreadyInCombatBand = horizontalDistance <= attackBand * 2.35;
        return (targetIsStill || targetIsGrounded) && alreadyInCombatBand;
    }

    private Vec3 calculateNoRotationOrbitPoint(LocalPlayer player,
                                               LivingEntity currentTarget,
                                               Vec3 targetCenter,
                                               Vec3 direction,
                                               double horizontalDistance) {
        updateOrbitSide(player, direction);

        double attackBand = getAuraAttackBand(currentTarget);
        double attackRange = getAuraAttackRange(currentTarget);
        double maxOrbitRadius = Math.max(ORBIT_MIN_RADIUS, Math.min(ORBIT_MAX_RADIUS, attackRange - ORBIT_ATTACK_RANGE_MARGIN));
        double orbitRadius = Mth.clamp(attackBand * 0.38, ORBIT_MIN_RADIUS, maxOrbitRadius);
        if (horizontalDistance < attackBand * 0.78) {
            orbitRadius = Math.min(maxOrbitRadius, orbitRadius + (attackBand * 0.78 - horizontalDistance) * 0.22);
        }
        if (horizontalDistance > attackBand * 1.45) {
            orbitRadius *= 0.55;
        }

        double forwardLead = Mth.clamp(horizontalDistance - attackBand * 0.72, 0.35, Math.max(1.15, attackBand * 0.78));
        if (currentTarget.getDeltaMovement().horizontalDistance() <= GROUND_TARGET_STILL_SPEED) {
            forwardLead = Math.min(forwardLead, Math.max(0.75, attackRange * 0.55));
        }

        Vec3 tangent = new Vec3(-direction.z, 0.0, direction.x).normalize().scale(orbitSide * orbitRadius);
        double targetHeight = currentTarget.getBoundingBox().getYsize();
        double verticalLead = Mth.clamp((targetCenter.y - player.getEyeY()) * 0.35, -0.65, 1.15);
        if (player.getDeltaMovement().y < -0.18) {
            verticalLead += 0.65;
        }

        return targetCenter
                .add(direction.scale(forwardLead))
                .add(tangent)
                .add(0.0, Math.max(-0.35, targetHeight * 0.18 + verticalLead), 0.0);
    }

    private void updateOrbitSide(LocalPlayer player, Vec3 direction) {
        if (player == null || direction == null || direction.lengthSqr() < 1.0E-5) return;

        Vec3 tangent = new Vec3(-direction.z, 0.0, direction.x).normalize();
        Vec3 velocity = horizontal(player.getDeltaMovement());
        if (velocity.lengthSqr() > 0.015 * 0.015) {
            double dot = velocity.normalize().dot(tangent);
            if (Math.abs(dot) > ORBIT_SIDE_SWITCH_DOT) {
                orbitSide = dot >= 0.0 ? 1 : -1;
                return;
            }
        }

        Vec3 look = horizontal(player.getViewVector(1.0f));
        if (look.lengthSqr() > 1.0E-5) {
            double dot = look.normalize().dot(tangent);
            if (Math.abs(dot) > ORBIT_SIDE_SWITCH_DOT * 1.35) {
                orbitSide = dot >= 0.0 ? 1 : -1;
            }
        }
    }

    private boolean shouldOwnAuraNoRotation() {
        KillAura killAura = Modules.get(KillAura.class);
        return killAura != null && killAura.isNoRotationMode();
    }

    private double getAuraAttackRange(LivingEntity currentTarget) {
        KillAura killAura = Modules.get(KillAura.class);
        if (killAura == null || currentTarget == null) {
            return 3.0;
        }
        return Mth.clamp(killAura.getEffectiveRange(currentTarget), 1.0, 6.0);
    }

    private double getAuraAttackBand(LivingEntity currentTarget) {
        double targetRadius = currentTarget == null
                ? 0.3
                : Math.max(currentTarget.getBoundingBox().getXsize(), currentTarget.getBoundingBox().getZsize()) * 0.5;
        return Math.max(1.4, getAuraAttackRange(currentTarget) + targetRadius - 0.18);
    }

    private Vec3 adjustPursuitPointForCrystalFear(LocalPlayer player, Vec3 point) {
        if (player == null || point == null || !crystalFear.get()) return point;
        CrystalThreat threat = findCrystalThreat(player, point);
        if (threat == null) return point;

        Vec3 away = horizontal(point.subtract(threat.pos()));
        if (away.lengthSqr() < 1.0E-5) {
            away = horizontal(player.getEyePosition().subtract(threat.pos()));
        }
        if (away.lengthSqr() < 1.0E-5) {
            away = horizontal(player.getDeltaMovement());
        }
        if (away.lengthSqr() < 1.0E-5) {
            away = horizontal(player.getViewVector(1.0f));
        }
        if (away.lengthSqr() < 1.0E-5) {
            return point.add(0.0, 3.0, 0.0);
        }

        Vec3 safeDirection = away.normalize();
        double lift = threat.damage() >= player.getHealth() + player.getAbsorptionAmount() * 0.5f ? 4.0 : 2.25;
        return point
                .add(safeDirection.scale(Math.max(2.0, CRYSTAL_FEAR_SAFE_DISTANCE - threat.distance())))
                .add(0.0, lift, 0.0);
    }

    private Vec3 adjustPursuitPointForCollision(LocalPlayer player, Vec3 point) {
        if (mc.level == null) return point;
        if (isRayPathClear(player, point)) return point;

        double[] lifts = {2.0, 4.0, 7.0, 10.0};
        for (double lift : lifts) {
            Vec3 lifted = point.add(0.0, lift, 0.0);
            if (isRayPathClear(player, lifted)) {
                return lifted;
            }
        }

        Rotation side = findFreeSideRotation(player);
        Vec3 sideDirection = side.directionVector();
        return player.getEyePosition()
                .add(sideDirection.scale(SIDE_RAYCAST_DISTANCE))
                .add(0.0, Math.max(2.0, overtakeHeight.get() * 0.5), 0.0);
    }

    private boolean isRayPathClear(LocalPlayer player, Vec3 point) {
        return isApproachCorridorClear(player, point);
    }

    private boolean isApproachCorridorClear(LocalPlayer player, Vec3 point) {
        if (player == null || mc.level == null || point == null) return false;

        Vec3 eye = player.getEyePosition();
        if (!isRayClear(player, eye, point)) return false;

        Vec3 direction = horizontal(point.subtract(eye));
        if (direction.lengthSqr() < 1.0E-5) return true;
        direction = direction.normalize();
        Vec3 side = new Vec3(-direction.z, 0.0, direction.x).scale(0.42);
        Vec3 body = player.getBoundingBox().getCenter();
        Vec3 low = new Vec3(body.x, player.getBoundingBox().minY + 0.35, body.z);
        Vec3 high = new Vec3(body.x, player.getBoundingBox().minY + player.getBoundingBox().getYsize() * 0.85, body.z);

        return isRayClear(player, low.add(side), point.add(side))
                && isRayClear(player, low.subtract(side), point.subtract(side))
                && isRayClear(player, high, point.add(0.0, 0.35, 0.0));
    }

    private boolean isRayClear(LocalPlayer player, Vec3 from, Vec3 to) {
        if (player == null || mc.level == null || from == null || to == null) return false;
        HitResult hit = mc.level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return hit.getLocation().distanceToSqr(from) + COLLISION_CLEARANCE_MARGIN * COLLISION_CLEARANCE_MARGIN
                >= to.distanceToSqr(from);
    }

    private boolean wantsHighPass(LocalPlayer player, LivingEntity currentTarget) {
        String mode = pursuitMode.get();
        if ("high_pass".equals(mode)) return prepareRotations.get();
        if (!"auto".equals(mode) || !prepareRotations.get()) return false;
        if (currentTarget instanceof Player targetPlayer && targetPlayer.isFallFlying()) {
            return false;
        }
        return canSetupMaceDive(player);
    }

    private boolean canSetupMaceDive(LocalPlayer player) {
        AttributeSwap attributeSwap = Modules.get(AttributeSwap.class);
        if (attributeSwap == null || !attributeSwap.isEnabled()) return false;
        if (player == null || player.isUsingItem()) return false;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).is(Items.MACE)) {
                return true;
            }
        }
        return player.getMainHandItem().is(Items.MACE) || player.getOffhandItem().is(Items.MACE);
    }

    private Vec3 predictTargetCenter(LivingEntity currentTarget, int ticks) {
        int safeTicks = Mth.clamp(ticks, 0, 20);
        Vec3 base = currentTarget.getBoundingBox().getCenter();
        if (safeTicks <= 0) {
            return base;
        }

        if (currentTarget instanceof Player playerTarget) {
            PlayerSimulationCache.SimulatedPlayerCache simulation = PlayerSimulationCache.getSimulationForOtherPlayers(playerTarget);
            PlayerSimulationCache.SimulatedPlayerSnapshot snapshot = simulation.getSnapshotAt(safeTicks);
            Vec3 delta = snapshot.pos().subtract(playerTarget.position());
            return base.add(delta);
        }

        return base.add(currentTarget.getDeltaMovement().scale(safeTicks));
    }

    private void tickAutoTakeoff(LocalPlayer player) {
        if (autoTakeoffCooldownTicks > 0) {
            autoTakeoffCooldownTicks--;
        }
        if (autoTakeoffGlideDelayTicks > 0) {
            autoTakeoffGlideDelayTicks--;
        }

        if (!isAutoTakeoffEnabled()) {
            clearAutoTakeoffState();
            return;
        }

        if (player == null || mc.level == null || isAttributeSwapElytraCycleActive()) {
            clearAutoTakeoffState();
            return;
        }

        if (player.isFallFlying()) {
            if (autoTakeoffPending) {
                autoTakeoffCooldownTicks = AUTO_TAKEOFF_COOLDOWN_TICKS;
            }
            clearAutoTakeoffState();
            return;
        }

        if (!canAutoTakeoffSelf(player) || !hasAutoTakeoffTarget(player)) {
            clearAutoTakeoffState();
            return;
        }

        if (!autoTakeoffPending) {
            if (autoTakeoffCooldownTicks > 0) return;
            autoTakeoffPending = true;
            if (canAutoTakeoffJumpNow(player)) {
                queueAutoTakeoffJump();
                autoTakeoffGlideDelayTicks = player.onGround() ? 1 : 0;
            }
        }

        if (player.onGround() || player.isInWater() || player.isInLava()) {
            queueAutoTakeoffJump();
            queuedAutoTakeoffStartGlide = false;
            queuedAutoTakeoffRelease = false;
            if (autoTakeoffGlideDelayTicks <= 0) {
                autoTakeoffGlideDelayTicks = 1;
            }
            return;
        }

        if (autoTakeoffGlideDelayTicks > 0) return;
        if (player.getDeltaMovement().y > 0.0 && player.fallDistance <= 0.0f) return;
        if (queuedAutoTakeoffStartGlide || queuedAutoTakeoffRelease) return;

        queuedAutoTakeoffRelease = true;
        queuedAutoTakeoffStartGlide = true;
    }

    private boolean canAutoTakeoffSelf(LocalPlayer player) {
        if (player == null) return false;
        if (player.isFallFlying()) return false;
        if (player.isPassenger()) return false;
        if (player.getAbilities().flying) return false;
        if (player.onClimbable()) return false;
        return player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private boolean hasAutoTakeoffTarget(LocalPlayer player) {
        if (player == null || !hasKillAuraGate()) return false;
        LivingEntity candidate = resolveCombatTarget(player);
        return isValidTarget(candidate);
    }

    private boolean canAutoTakeoffJumpNow(LocalPlayer player) {
        return player != null
                && !player.isPassenger()
                && !player.getAbilities().flying
                && (player.onGround() || player.isInWater() || player.isInLava());
    }

    private void queueAutoTakeoffJump() {
        queuedAutoTakeoffJump = true;
    }

    private void clearAutoTakeoffState() {
        autoTakeoffPending = false;
        queuedAutoTakeoffJump = false;
        queuedAutoTakeoffRelease = false;
        queuedAutoTakeoffStartGlide = false;
        autoTakeoffGlideDelayTicks = 0;
    }

    private boolean handleAutoTakeoffMovementInput(MovementInputEvent event) {
        if (queuedAutoTakeoffRelease) {
            event.setJump(false);
            queuedAutoTakeoffRelease = false;
            return true;
        }

        if (queuedAutoTakeoffJump) {
            event.setJump(true);
            queuedAutoTakeoffJump = false;
            return true;
        }

        return false;
    }

    private boolean handleAutoTakeoffSync(EventSync event) {
        if (!queuedAutoTakeoffStartGlide) return false;

        LocalPlayer player = mc.player;
        if (player == null) {
            clearAutoTakeoffState();
            return false;
        }

        if (player.isFallFlying()) {
            clearAutoTakeoffState();
            autoTakeoffCooldownTicks = AUTO_TAKEOFF_COOLDOWN_TICKS;
            return true;
        }

        if (!canAutoTakeoffSelf(player)) {
            clearAutoTakeoffState();
            return false;
        }

        if (!player.onGround()
                && !player.isInWater()
                && !player.isInLava()
                && player.getDeltaMovement().y <= 0.0) {
            queuedAutoTakeoffStartGlide = false;

            if (player.tryToStartFallFlying()) {
                queuedAutoTakeoffJump = true;
            }

            if (player.isFallFlying() && mc.getConnection() != null) {
                mc.getConnection().send(
                        new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
                );
                clearAutoTakeoffState();
                autoTakeoffCooldownTicks = AUTO_TAKEOFF_COOLDOWN_TICKS;
            } else {
                autoTakeoffGlideDelayTicks = 1;
            }
            return true;
        }

        if (player.onGround() || player.isInWater() || player.isInLava()) {
            queuedAutoTakeoffStartGlide = false;
            queuedAutoTakeoffRelease = false;
        }

        return false;
    }

    private void releaseRotationState() {
        RotationManager.INSTANCE.release(this, true);
    }

    private void tickRestartFallFlying(LocalPlayer player) {
        if (restartFallFlyingTicks > 0) {
            restartFallFlyingTicks--;
            return;
        }

        if (player == null || player.onGround() || player.isFallFlying()) return;
        if (!player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;
        if (!hasFireworkRocket(player)) return;
        if (ElytraRecastUtil.INSTANCE.isActive(this)) return;

        ElytraRecastUtil.INSTANCE.request(
                this,
                player,
                ElytraRecastUtil.Mode.from(recastMode.get()),
                false,
                false,
                false,
                0
        );
        ElytraRecastUtil.INSTANCE.tick(this, player);
        restartFallFlyingTicks = FALL_FLYING_RESTART_DELAY_TICKS;
    }

    private boolean shouldUseFirework(LocalPlayer player) {
        if (player == null || !player.isFallFlying()) return false;
        if (player.isUsingItem()) return false;
        if (!hasFireworkRocket(player)) return false;
        int threshold = speedThreshold.get();
        if (threshold <= 0) return false;
        if (FireworkUseController.INSTANCE.isCooling(threshold)) return false;

        double currentSpeed = player.getDeltaMovement().horizontalDistance() * FIREWORK_SPEED_FACTOR;
        if (currentSpeed < threshold) return true;

        if (pursuitPhase == PursuitPhase.CLIMB || pursuitPhase == PursuitPhase.OVERPASS) {
            return currentSpeed < threshold * 1.25;
        }
        return false;
    }

    private boolean hasFireworkRocket(LocalPlayer player) {
        if (player == null) return false;
        if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) return true;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).is(Items.FIREWORK_ROCKET)) return true;
        }
        return false;
    }

    private Rotation findFreeSideRotation(LocalPlayer player) {
        if (player == null || mc.level == null) {
            return new Rotation(0.0f, -88.0f);
        }

        Vec3 eye = player.getEyePosition();
        for (Direction direction : Direction.values()) {
            if (direction == lastDirection) continue;

            Vec3 offset = Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(SIDE_RAYCAST_DISTANCE);
            Vec3 lowEnd = eye.add(offset);
            Vec3 highStart = eye.add(0.0, player.getBoundingBox().getYsize(), 0.0);
            Vec3 highEnd = highStart.add(offset);

            HitResult lowRay = mc.level.clip(new ClipContext(
                    eye,
                    lowEnd,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            HitResult highRay = mc.level.clip(new ClipContext(
                    highStart,
                    highEnd,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));

            if (lowRay.getType() == HitResult.Type.MISS && highRay.getType() == HitResult.Type.MISS) {
                lastDirection = direction;
                return Rotation.lookingAt(lowEnd, eye).normalize();
            }
        }

        Rotation server = RotationManager.INSTANCE.getServerRotation();
        float yaw = server != null ? server.yaw() : player.getYRot();
        return new Rotation(yaw, -88.0f).normalize();
    }

    private boolean queueEmergencyAvoidanceRotation(LocalPlayer player) {
        if (queueBlockDodgeRotation(player)) {
            return true;
        }
        return queueCrystalFearRotation(player);
    }

    private boolean queueCrystalFearRotation(LocalPlayer player) {
        if (!crystalFear.get()) return false;
        if (!isValidCrystalFearSelf(player)) return false;

        CrystalThreat threat = findCrystalThreat(player, null);
        if (threat == null) return false;

        Rotation crystalFearRotation = findCrystalFearRotation(player, threat);
        if (crystalFearRotation == null) return false;

        RotationTarget plan = new RotationTarget(
                crystalFearRotation.normalize(),
                null,
                List.of(),
                2,
                2.0f,
                true,
                MovementCorrection.SILENT,
                false,
                null
        );
        RotationManager.INSTANCE.setRotationTarget(plan, CRYSTAL_FEAR_ROTATION_PRIORITY, this);
        return true;
    }

    private boolean isValidCrystalFearSelf(LocalPlayer player) {
        return player != null
                && mc.level != null
                && player.isFallFlying()
                && !player.isPassenger()
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private CrystalThreat findCrystalThreat(LocalPlayer player, Vec3 plannedPoint) {
        if (player == null || mc.level == null) return null;

        double range = Math.max(1.0, crystalFearRange.get());
        double rangeSq = range * range;
        Vec3 eye = player.getEyePosition();
        Vec3 predictedSelf = predictSelfCrystalPosition(player);
        Vec3 planned = plannedPoint != null ? plannedPoint : predictedSelf;
        CrystalThreat best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (!crystal.isAlive() || crystal.isRemoved()) continue;

            Vec3 crystalPos = crystal.position();
            double distanceSq = crystalPos.distanceToSqr(eye);
            double plannedDistanceSq = crystalPos.distanceToSqr(planned);
            if (distanceSq > rangeSq && plannedDistanceSq > rangeSq) continue;

            float damage = ExplosionDamageUtil.getCrystalDamage(
                    player,
                    crystalPos,
                    Mth.clamp(crystalFearPredictionTicks.get(), 0, 12),
                    true
            );
            if (!isDangerousCrystalDamage(player, damage)) continue;

            double distance = Math.sqrt(Math.min(distanceSq, plannedDistanceSq));
            double score = damage * 100.0 + Math.max(0.0, range - distance) * 5.0;
            if (plannedDistanceSq < distanceSq) {
                score += 24.0;
            }
            if (score > bestScore) {
                bestScore = score;
                best = new CrystalThreat(crystal, crystalPos, damage, distance);
            }
        }

        return best;
    }

    private Vec3 predictSelfCrystalPosition(LocalPlayer player) {
        Vec3 predicted = player.getEyePosition();
        Vec3 velocity = player.getDeltaMovement();
        int ticks = Mth.clamp(crystalFearPredictionTicks.get(), 0, 12);
        for (int i = 0; i < ticks; i++) {
            predicted = predicted.add(velocity);
            velocity = approximateNextElytraVelocity(velocity);
        }
        return predicted;
    }

    private boolean isDangerousCrystalDamage(LocalPlayer player, float damage) {
        if (damage < crystalFearMinDamage.get()) return false;

        float health = player.getHealth() + player.getAbsorptionAmount();
        double healthThreat = Math.max(crystalFearMinDamage.get(), health * 0.30);
        return damage >= healthThreat || health - damage <= crystalFearHealthBuffer.get();
    }

    private Rotation findCrystalFearRotation(LocalPlayer player, CrystalThreat threat) {
        Vec3 velocity = player.getDeltaMovement();
        Vec3 away = horizontal(player.getEyePosition().subtract(threat.pos()));
        if (away.lengthSqr() < 1.0E-5) {
            away = horizontal(velocity);
        }
        if (away.lengthSqr() < 1.0E-5) {
            away = horizontal(player.getViewVector(1.0f));
        }
        if (away.lengthSqr() < 1.0E-5) {
            return findFreeSideRotation(player);
        }

        Vec3 baseDirection = away.normalize();
        double horizontalSpeed = Math.max(0.24, horizontal(velocity).length());
        Vec3 bestDirection = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double[] candidateAngles = {0.0, -35.0, 35.0, -70.0, 70.0, -115.0, 115.0, 180.0};

        for (double angle : candidateAngles) {
            Vec3 candidateDirection = rotateHorizontal(baseDirection, angle).normalize();
            Vec3 candidateVelocity = new Vec3(
                    candidateDirection.x * horizontalSpeed,
                    Math.max(velocity.y, 0.02),
                    candidateDirection.z * horizontalSpeed
            );
            double score = scoreCrystalFearDirection(player, threat, candidateVelocity, angle);
            if (score > bestScore) {
                bestScore = score;
                bestDirection = candidateDirection;
            }
        }

        if (bestDirection == null) {
            return findFreeSideRotation(player);
        }

        Vec3 eye = player.getEyePosition();
        double lift = threat.damage() >= player.getHealth() + player.getAbsorptionAmount() * 0.5f ? 4.5 : 2.8;
        Rotation rotation = Rotation.lookingAt(
                eye.add(bestDirection.scale(20.0)).add(0.0, lift, 0.0),
                eye
        ).normalize();
        float pitch = Mth.clamp(rotation.pitch(), -48.0f, -4.0f);
        return new Rotation(rotation.yaw(), pitch).normalize();
    }

    private double scoreCrystalFearDirection(LocalPlayer player, CrystalThreat threat, Vec3 initialVelocity, double angle) {
        AABB baseBox = player.getBoundingBox();
        Vec3 offset = Vec3.ZERO;
        Vec3 velocity = initialVelocity;
        double minDistanceSq = Double.POSITIVE_INFINITY;
        int ticks = Math.max(3, Mth.clamp(crystalFearPredictionTicks.get() + 3, 3, 14));

        for (int tick = 1; tick <= ticks; tick++) {
            Vec3 previousOffset = offset;
            offset = offset.add(velocity);
            Vec3 center = baseBox.move(offset).getCenter();
            minDistanceSq = Math.min(minDistanceSq, center.distanceToSqr(threat.pos()));

            AABB previousBox = baseBox.move(previousOffset);
            AABB nextBox = baseBox.move(offset);
            AABB swept = union(previousBox, nextBox).inflate(CRYSTAL_FEAR_SWEEP_MARGIN);
            if (solidBlockDodge.get() && hasSolidCollision(player, swept)) {
                return -10_000.0 - Math.abs(angle);
            }

            velocity = approximateNextElytraVelocity(velocity);
        }

        double minDistance = Math.sqrt(minDistanceSq);
        double safeBonus = minDistance >= CRYSTAL_FEAR_SAFE_DISTANCE ? 120.0 : minDistance * 12.0;
        return safeBonus - Math.abs(angle) * 0.35;
    }

    private boolean queueBlockDodgeRotation(LocalPlayer player) {
        if (!solidBlockDodge.get()) return false;
        if (!isValidBlockDodgeSelf(player)) return false;

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.horizontalDistance() < 0.18) return false;

        PredictedBlockImpact impact = predictBlockImpact(player, velocity, blockDodgePredictionTicks.get());
        if (impact == null) return false;

        double damage = estimateBlockImpactDamage(player, impact.velocity());
        if (!isDangerousBlockImpact(player, damage)) return false;

        Rotation dodgeRotation = findBlockDodgeRotation(player, impact);
        if (dodgeRotation == null) return false;

        RotationTarget plan = new RotationTarget(
                dodgeRotation.normalize(),
                null,
                List.of(),
                2,
                2.0f,
                true,
                MovementCorrection.SILENT,
                false,
                null
        );
        RotationManager.INSTANCE.setRotationTarget(plan, BLOCK_DODGE_ROTATION_PRIORITY, this);
        return true;
    }

    private boolean isValidBlockDodgeSelf(LocalPlayer player) {
        return player != null
                && mc.level != null
                && player.isFallFlying()
                && !player.onGround()
                && !player.isPassenger()
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private PredictedBlockImpact predictBlockImpact(LocalPlayer player, Vec3 initialVelocity, int ticks) {
        if (player == null || mc.level == null) return null;

        AABB baseBox = player.getBoundingBox();
        Vec3 offset = Vec3.ZERO;
        Vec3 velocity = initialVelocity;
        int safeTicks = Math.max(1, ticks);

        for (int tick = 1; tick <= safeTicks; tick++) {
            Vec3 previousOffset = offset;
            offset = offset.add(velocity);

            AABB previousBox = baseBox.move(previousOffset);
            AABB nextBox = baseBox.move(offset);
            AABB swept = union(previousBox, nextBox).inflate(BLOCK_DODGE_SWEEP_MARGIN);
            if (hasSolidCollision(player, swept)) {
                return new PredictedBlockImpact(tick, velocity);
            }

            velocity = approximateNextElytraVelocity(velocity);
        }

        return null;
    }

    private Vec3 approximateNextElytraVelocity(Vec3 velocity) {
        return new Vec3(
                velocity.x * 0.99,
                velocity.y * 0.98,
                velocity.z * 0.99
        );
    }

    private boolean hasSolidCollision(LocalPlayer player, AABB box) {
        for (var shape : mc.level.getBlockCollisions(player, box)) {
            if (!shape.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Rotation findBlockDodgeRotation(LocalPlayer player, PredictedBlockImpact impact) {
        Vec3 velocity = player.getDeltaMovement();
        Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
        if (horizontal.lengthSqr() < 1.0E-5) {
            Rotation movement = RotationManager.INSTANCE.getMovementRotation();
            Vec3 direction = movement != null ? movement.directionVector() : player.getViewVector(1.0f);
            horizontal = new Vec3(direction.x, 0.0, direction.z);
        }
        if (horizontal.lengthSqr() < 1.0E-5) {
            return findFreeSideRotation(player);
        }

        Vec3 baseDirection = horizontal.normalize();
        double horizontalSpeed = Math.max(0.18, horizontal.length());
        Vec3 bestDirection = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (double angle : BLOCK_DODGE_CANDIDATE_ANGLES) {
            Vec3 candidateDirection = rotateHorizontal(baseDirection, angle).normalize();
            Vec3 candidateVelocity = new Vec3(
                    candidateDirection.x * horizontalSpeed,
                    velocity.y,
                    candidateDirection.z * horizontalSpeed
            );
            PredictedBlockImpact candidateImpact = predictBlockImpact(player, candidateVelocity, blockDodgePredictionTicks.get());
            double score = candidateImpact == null
                    ? 10_000.0 - Math.abs(angle)
                    : candidateImpact.ticks() * 100.0 - Math.abs(angle);
            if (score > bestScore) {
                bestScore = score;
                bestDirection = candidateDirection;
            }
        }

        if (bestDirection == null) {
            return findFreeSideRotation(player);
        }

        Vec3 eye = player.getEyePosition();
        double lift = impact.ticks() <= 3 || velocity.y < -0.04 ? 4.0 : 2.0;
        Rotation rotation = Rotation.lookingAt(
                eye.add(bestDirection.scale(20.0)).add(0.0, lift, 0.0),
                eye
        ).normalize();
        float maxPitch = impact.ticks() <= 3 || velocity.y < -0.04 ? -28.0f : -10.0f;
        float pitch = Mth.clamp(Math.min(rotation.pitch(), maxPitch), -55.0f, 5.0f);
        return new Rotation(rotation.yaw(), pitch).normalize();
    }

    private double estimateBlockImpactDamage(LocalPlayer player, Vec3 impactVelocity) {
        double raw = Math.max(0.0, impactVelocity.horizontalDistance() * 10.0 - 3.0);
        if (raw <= 0.0) return 0.0;

        int featherLevel = getFeatherFallingLevel(player);
        double reduction = Math.min(20.0, featherLevel * 3.0) / 25.0;
        return raw * (1.0 - reduction);
    }

    private boolean isDangerousBlockImpact(LocalPlayer player, double damage) {
        if (damage < blockDodgeMinDamage.get()) return false;

        float health = player.getHealth() + player.getAbsorptionAmount();
        double healthThreat = Math.max(blockDodgeMinDamage.get(), health * 0.32);
        return damage >= healthThreat || health - damage <= blockDodgeHealthBuffer.get();
    }

    private int getFeatherFallingLevel(LocalPlayer player) {
        if (player == null || mc.level == null) return 0;
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        Holder<Enchantment> feather = getFeatherFallingEnchant();
        return feather == null || boots == null || boots.isEmpty()
                ? 0
                : EnchantmentHelper.getItemEnchantmentLevel(feather, boots);
    }

    private Holder<Enchantment> getFeatherFallingEnchant() {
        if (mc.level == null) return null;
        var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.getValue(Enchantments.FEATHER_FALLING);
        return enchantment != null ? registry.wrapAsHolder(enchantment) : null;
    }

    private enum PursuitPhase {
        NONE,
        CHASE,
        CLIMB,
        OVERPASS,
        DIVE
    }

    private record TargetCandidate(LivingEntity entity, Vec3 predictedCenter, Vec3 approachPoint, double score) {
    }

    private record Reachability(Vec3 point, boolean raised) {
    }

    private record CrystalThreat(EndCrystal crystal, Vec3 pos, float damage, double distance) {
    }

    private record PredictedBlockImpact(int ticks, Vec3 velocity) {
    }
}
