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
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.ElytraRecastUtil;
import combatant.client.util.player.ElytraEscapePlanner;
import combatant.client.util.player.ElytraFlightPhysics;
import combatant.client.util.player.ElytraFlightPhysics.FlightModel;
import net.minecraft.world.effect.MobEffects;
import combatant.client.util.player.inventory.FireworkUseController;
import combatant.client.features.module.modules.movement.SuperFirework;
import combatant.client.util.player.simulation.PlayerSimulationCache;
import combatant.client.util.target.TargetManager;
import combatant.client.util.world.ExplosionDamageUtil;

import java.util.List;

@ModuleInfo(
        id = "elytratarget",
        displayName = "ElytraTarget",
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.ATTACK,
        description = "module.elytratarget.description")
public final class ElytraTarget extends Module {

    private static final int PURSUIT_ROTATION_PRIORITY = 80;
    private static final int CRYSTAL_FEAR_ROTATION_PRIORITY = 130;
    private static final int BLOCK_DODGE_ROTATION_PRIORITY = 140;
    private static final int FIREWORK_SYNC_PRIORITY = -1000;
    private static final int FIREWORK_MIN_REBOOST_TICKS = 4;
    private static final int FIREWORK_END_GUARD_TICKS = 3;
    private static final int FALL_FLYING_RESTART_DELAY_TICKS = 5;
    private static final int HIT_STATUS_READY_TICKS = 5;
    private static final int AUTO_TAKEOFF_COOLDOWN_TICKS = 12;
    private static final double COLLISION_CLEARANCE_MARGIN = 1.25;
    private static final double GROUND_TARGET_STILL_SPEED = 0.08;
    private static final double ORBIT_MIN_RADIUS = 1.15;
    private static final double ORBIT_MAX_RADIUS = 3.35;
    private static final double ORBIT_ATTACK_RANGE_MARGIN = 0.22;
    private static final double ORBIT_SIDE_SWITCH_DOT = 0.18;
    private static final int TARGET_FOCUS_HOLD_TICKS = 45;
    private static final double TARGET_FOCUS_RANGE_MULTIPLIER = 1.35;
    // Minimum coasting speed in blocks/tick with no managed SuperFirework boost.
    // This is automatic behaviour, not a duplicate configurable firework speed.
    private static final double UNMANAGED_REBOOST_SPEED = 0.42;
    private final Minecraft mc = Minecraft.getInstance();
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
                    true
            );
    private final NumberValue<Integer> blockDodgePredictionTicks =
            visibleWhen(num(
                    "elytratargetBlockDodgePredictionTicks",
                    "block_dodge_prediction_ticks",
                    12,
                    3,
                    30
            ), solidBlockDodge::get);
    private final NumberValue<Float> blockDodgeClearance =
            visibleWhen(num(
                    "elytratargetBlockDodgeClearance",
                    "block_dodge_clearance",
                    5.0f,
                    2.0f,
                    12.0f
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
    private Rotation blockDodgeRotation;
    private final Object emergencyRotationProvider = new Object();
    private boolean attackWindowActive;
    private PursuitPhase pursuitPhase = PursuitPhase.NONE;
    private boolean pursuitRotationActive;
    private boolean emergencyAvoidQueued;
    private int restartFallFlyingTicks;
    private int hitStatusTicks = HIT_STATUS_READY_TICKS;
    private boolean queuedFireworkUse;
    private int orbitSide = 1;
    private int focusedTargetId = -1;
    private int targetFocusTicks;
    private boolean autoTakeoffPending;
    private boolean queuedAutoTakeoffJump;
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
            AttributeSwap swap = Modules.get(AttributeSwap.class);
            int targetId = swap != null ? swap.getElytraCycleTargetId() : -1;
            var entity = targetId >= 0 ? mc.level.getEntity(targetId) : null;
            target = entity instanceof LivingEntity living && isValidTarget(living) ? living : null;
            TargetManager.publish(this, target, TargetManager.Source.MODULE, 900);
            queuedFireworkUse = false;
            ElytraRecastUtil.INSTANCE.reset(this);
            return;
        }

        LivingEntity combatTarget = resolveCombatTarget(player);
        boolean validSelf = isValidSelf(player);
        boolean validTarget = isValidTarget(combatTarget);

        target = validSelf && validTarget ? combatTarget : null;
        updateTargetFocus(target);
        TargetManager.publish(this, target, TargetManager.Source.MODULE, 900);
        boolean emergencyAvoidQueued = this.emergencyAvoidQueued;

        if (!validSelf || !validTarget) {
            clearPursuitState();
            queuedFireworkUse = false;
            if (!emergencyAvoidQueued) {
                RotationManager.INSTANCE.release(this, false);
            }
        } else {
            if (!emergencyAvoidQueued) tickRestartFallFlying(player);
        }

        if (emergencyAvoidQueued) {
            // Do not reset pursuitPhase. The emergency planner must compare its
            // heading with normal pursuit on the next tick.
            pursuitRotationActive = false;
            pendingRotation = null;
            attackWindowActive = false;
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
            // AttributeSwap owns the equipment and the attack until the real smash.
            queuedFireworkUse = false;
            clearAutoTakeoffState();
            ElytraRecastUtil.INSTANCE.reset(this);
            return;
        }

        tickAutoTakeoff(player);

        boolean emergencyAvoidQueued = this.emergencyAvoidQueued;

        if (!isRunning()) {
            queuedFireworkUse = false;
            clearPursuitState();
            if (!ElytraRecastUtil.INSTANCE.isActive(this)) {
                ElytraRecastUtil.INSTANCE.reset(this);
            }
            if (!emergencyAvoidQueued) {
                RotationManager.INSTANCE.release(this, false);
            }
            return;
        }

        if (emergencyAvoidQueued) {
            // Avoiding a wall must not trigger an extra rocket or lose the
            // pursuit phase needed for the next risk comparison.
            queuedFireworkUse = false;
            pursuitRotationActive = false;
            pendingRotation = null;
            attackWindowActive = false;
            return;
        }

        if (pursuitPhase == PursuitPhase.DIVE && canSetupMaceDive(player)) {
            AttributeSwap swap = Modules.get(AttributeSwap.class);
            if (swap != null && swap.prepareElytraTargetDive(player, target)) {
                queuedFireworkUse = false;
                return;
            }
        }
        if (isPursuitEnabled() && shouldUseFirework(player)) {
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

        Rotation fireworkRotation = getFireworkSteeringRotation();
        if (fireworkRotation == null) return;
        if (!isBoostPathSafe(player, fireworkRotation)) return;

        boolean launched = FireworkUseController.INSTANCE.use(
                fireworkRotation.yaw(),
                fireworkRotation.pitch(),
                true,
                swapBack.get(),
                silentSwap.get(),
                FIREWORK_MIN_REBOOST_TICKS,
                true,
                needsEmergencyReboost(player)
        );
        if (launched) {
            event.setRotation(fireworkRotation.yaw(), fireworkRotation.pitch(), true);
        }
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
        ElytraRecastUtil.INSTANCE.handleSync(this, event);
    }

    @EventHandler(priority = 1000)
    private void onRotationBegin(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        // Keep the chosen escape, but resolve the ordinary flight heading afresh.
        RotationManager.INSTANCE.release(emergencyRotationProvider, false);
        emergencyAvoidQueued = false;
    }

    @EventHandler
    private void onRotationSafety(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.RESOLVED) return;
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            resetBlockDodge();
            return;
        }
        emergencyAvoidQueued = queueEmergencyAvoidanceRotation(player, event.getMovementRotation());
        if (emergencyAvoidQueued) {
            pursuitRotationActive = false;
            pendingRotation = null;
            attackWindowActive = false;
            queuedFireworkUse = false;
        }
    }

    @EventHandler(priority = 0)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled() || event.getType() != RotationUpdateEvent.Type.PRE) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clearPursuitState();
            resetBlockDodge();
            emergencyAvoidQueued = false;
            return;
        }

        Rotation pursuitRotation = null;
        if (isRunning()) {
            updatePursuitPhase(player, target);
            pursuitRotation = calculatePursuitRotation(player, target);
        }
        if (isAttributeSwapElytraCycleActive()) {
            AttributeSwap swap = Modules.get(AttributeSwap.class);
            var cycleEntity = swap != null ? mc.level.getEntity(swap.getElytraCycleTargetId()) : null;
            if (cycleEntity instanceof LivingEntity maceTarget && isValidTarget(maceTarget)) {
                Rotation aim = Rotation.lookingAt(maceTarget.getBoundingBox().getCenter(), player.getEyePosition()).normalize();
                pendingRotation = aim;
                pursuitRotationActive = true;
                RotationManager.INSTANCE.setRotationTarget(new RotationTarget(
                        aim, maceTarget, List.of(), 2, 2.0f, true, MovementCorrection.SILENT, true, null
                ), PURSUIT_ROTATION_PRIORITY, this);
            } else {
                pursuitRotationActive = false;
                releaseRotationState();
            }
            return;
        }

        if (!isRunning()) {
            pursuitRotationActive = false;
            RotationManager.INSTANCE.release(this, false);
            return;
        }

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
                && isValidTarget(target)
                && hasKillAuraGate()
                && !isAttributeSwapElytraCycleActive();
    }

    public boolean canIgnoreKillAuraRotations() {
        return isRunning() && (pursuitRotationActive || preparingRotation || attackWindowActive || pendingRotation != null);
    }

    public boolean isManagingFireworks() {
        return isEnabled() && mc.player != null && mc.player.isFallFlying()
                && (isRunning() && isPursuitEnabled() && RotationManager.INSTANCE.ownsActiveRotation(this)
                || emergencyAvoidQueued && RotationManager.INSTANCE.ownsActiveRotation(emergencyRotationProvider));
    }

    public Rotation getFireworkSteeringRotation() {
        return isManagingFireworks() ? RotationManager.INSTANCE.getMovementRotation() : null;
    }

    public boolean isControllingFlight() {
        return isEnabled() && (emergencyAvoidQueued || (isRunning() && isPursuitEnabled()));
    }

    private void resetState() {
        ElytraRecastUtil.INSTANCE.reset(this);
        target = null;
        TargetManager.clear(this);
        preparingRotation = false;
        pendingRotation = null;
        resetBlockDodge();
        attackWindowActive = false;
        pursuitPhase = PursuitPhase.NONE;
        pursuitRotationActive = false;
        emergencyAvoidQueued = false;
        restartFallFlyingTicks = 0;
        hitStatusTicks = HIT_STATUS_READY_TICKS;
        queuedFireworkUse = false;
        orbitSide = 1;
        focusedTargetId = -1;
        targetFocusTicks = 0;
        autoTakeoffPending = false;
        queuedAutoTakeoffJump = false;
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
        return TargetManager.getRecentAttackTarget();
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
        // Losing the focused target at an obstacle would disable the flight controller before it can evade.
        if (reachability == null && focused && solidBlockDodge.get()) {
            reachability = new Reachability(predictedCenter, false);
        }
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

        Vec3 targetPoint = predictPursuitTargetCenter(player, currentTarget);
        Vec3 playerPos = getPursuitSteeringOrigin(player, false);
        double horizontalDistance = horizontalDistance(playerPos, targetPoint);
        double heightAboveTarget = playerPos.y - targetPoint.y;
        double horizontalSpeed = player.getDeltaMovement().horizontalDistance();
        double wantedHeight = Math.max(1.0, overtakeHeight.get());
        double diveDistance = Math.max(1.0, diveHorizontalDistance.get());

        boolean highEnough = heightAboveTarget >= wantedHeight * 0.55;
        boolean closeEnough = horizontalDistance <= diveDistance;
        boolean descending = player.getDeltaMovement().y <= -0.03 || player.fallDistance >= 1.2f;

        boolean diveSpeedReady = horizontalSpeed >= attackSetupSpeed.get()
                || horizontalDistance <= diveDistance * 0.55
                || player.fallDistance >= 1.4f;
        if (highEnough && closeEnough && descending && diveSpeedReady) {
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

        return Rotation.lookingAt(point, getPursuitSteeringOrigin(player, true)).normalize();
    }

    private Vec3 calculatePursuitPoint(LocalPlayer player, LivingEntity currentTarget) {
        Vec3 targetCenter = predictPursuitTargetCenter(player, currentTarget);
        Vec3 playerPos = getPursuitSteeringOrigin(player, false);
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
        AttributeSwap swap = Modules.get(AttributeSwap.class);
        return swap != null && swap.canPrepareElytraTargetDive(player, target);
    }

    private boolean hasSuperFireworkSteering() {
        LocalPlayer player = mc.player;
        return isEnabled() && player != null && player.isFallFlying()
                && isPursuitEnabled() && !isAttributeSwapElytraCycleActive()
                && Modules.enabled(SuperFirework.class);
    }

    /**
     * Only SuperFirework determines the acceleration profile (Grim or Custom).
     * ElytraTarget supplies the pursuit/escape direction, never a second speed setting.
     */
    private Vec3 predictSuperFireworkVelocity(Vec3 velocity, Vec3 direction) {
        SuperFirework firework = Modules.get(SuperFirework.class);
        if (firework == null || !firework.isEnabled() || direction.lengthSqr() < 1.0E-8) {
            return velocity;
        }
        return firework.estimateBoostVelocity(velocity, Rotation.fromRotationVec(direction));
    }

    private double expectedSuperFireworkSpeed(LocalPlayer player) {
        if (player == null || !Modules.enabled(SuperFirework.class)) return 0.0;
        Vec3 direction;
        if (blockDodgeRotation != null) {
            direction = blockDodgeRotation.directionVector();
        } else if (RotationManager.INSTANCE.ownsActiveRotation(this)) {
            direction = RotationManager.INSTANCE.getMovementRotation().directionVector();
        } else if (target != null) {
            direction = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        } else {
            direction = player.getDeltaMovement();
        }
        return predictSuperFireworkVelocity(player.getDeltaMovement(), direction).length();
    }

    /**
     * Estimate interception against the actual SuperFirework thrust model, not a fixed
     * number of ticks ahead. The rocket sets velocity each tick while it burns; once
     * its guaranteed fuse ends, the estimate falls back to current glide momentum.
     */
    private Vec3 predictPursuitTargetCenter(LocalPlayer player, LivingEntity currentTarget) {
        int maxTicks = Mth.clamp(pursuitPredictionTicks.get(), 0, 20);
        if (!hasSuperFireworkSteering() || maxTicks == 0) {
            return predictTargetCenter(currentTarget, maxTicks);
        }

        Vec3 origin = player.getEyePosition();
        FireworkUseController.BoostState boost = FireworkUseController.INSTANCE.getBoostState(player);
        double rocketSpeed = expectedSuperFireworkSpeed(player);
        int poweredTicks = boost.active() ? Math.max(0, boost.expectedTicks()) : 0;
        if (poweredTicks == 0 && !boost.awaitingSpawn() && shouldUseFirework(player)) {
            // Anticipate only boosts that ElytraTarget will actually request.
            // Otherwise high-speed coasting would incorrectly be modeled as thrust.
            poweredTicks = 10;
        }
        if (boost.awaitingSpawn()) poweredTicks = 10;

        Vec3 precedingTarget = predictTargetCenter(currentTarget, 0);
        double precedingGap = precedingTarget.distanceTo(origin);
        for (int tick = 1; tick <= maxTicks; tick++) {
            Vec3 futureTarget = predictTargetCenter(currentTarget, tick);
            Vec3 toFuture = futureTarget.subtract(origin);
            double distance = toFuture.length();
            Vec3 dir = distance > 1.0E-5 ? toFuture.scale(1.0 / distance) : Vec3.ZERO;
            double momentum = Math.max(0.0, player.getDeltaMovement().dot(dir));
            double flight = Math.min(tick, poweredTicks) * rocketSpeed
                    + Math.max(0, tick - poweredTicks) * momentum * 0.85;
            double gap = distance - flight;
            if (gap <= 0.0) {
                double portion = Mth.clamp(precedingGap / Math.max(1.0E-6, precedingGap - gap), 0.0, 1.0);
                return precedingTarget.lerp(futureTarget, portion);
            }
            precedingTarget = futureTarget;
            precedingGap = gap;
        }
        return precedingTarget;
    }

    /** One-tick motion lead anticipates overshooting before the next steering update. */
    private Vec3 getPursuitSteeringOrigin(LocalPlayer player, boolean eyes) {
        Vec3 origin = eyes ? player.getEyePosition() : player.position();
        if (!hasSuperFireworkSteering()) return origin;
        Vec3 movement = player.getDeltaMovement();
        // Keep this bounded so knockback and server corrections cannot oversteer a rocket.
        double lead = Math.min(1.0, 1.25 / Math.max(0.25, movement.length()));
        return origin.add(movement.scale(lead));
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
            if (autoTakeoffGlideDelayTicks <= 0) {
                autoTakeoffGlideDelayTicks = 1;
            }
            return;
        }

        if (autoTakeoffGlideDelayTicks > 0) return;
        if (player.getDeltaMovement().y > 0.0 && player.fallDistance <= 0.0f) return;
        // Legal start: release jump for a tick, then press it so vanilla itself sends START_FALL_FLYING
        // (Grim ElytraB/C/F: fresh jump edge, no repeated starts, not on ground).
        ElytraRecastUtil.INSTANCE.request(this, player, ElytraRecastUtil.Mode.GRIM, false, false, false, 0);
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
        autoTakeoffGlideDelayTicks = 0;
    }

    private boolean handleAutoTakeoffMovementInput(MovementInputEvent event) {
        if (queuedAutoTakeoffJump) {
            event.setJump(true);
            queuedAutoTakeoffJump = false;
            return true;
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
        if (player.isUsingItem() || !hasFireworkRocket(player)) return false;
        if (isAttributeSwapElytraCycleActive()) return false;
        // Rockets during a committed mace dive cancel the fall and ruin smash damage.
        if (pursuitPhase == PursuitPhase.DIVE && canSetupMaceDive(player)) return false;

        if (FireworkUseController.INSTANCE.isCooling(FIREWORK_MIN_REBOOST_TICKS)) return false;
        FireworkUseController.BoostState boost = FireworkUseController.INSTANCE.getBoostState(player);
        if (boost.awaitingSpawn()) return false;
        // Never launch a new rocket solely because an avoidance route is active:
        // acceleration can make a nearby collision unavoidable.
        if (blockDodgeRotation != null) return false;
        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.horizontalDistance();
        double targetSpeed = Modules.enabled(SuperFirework.class)
                ? expectedSuperFireworkSpeed(player)
                : UNMANAGED_REBOOST_SPEED / 0.60;
        double reboostSpeed = Math.max(UNMANAGED_REBOOST_SPEED, targetSpeed * 0.60);
        double distance = target == null ? 0.0 : player.position().distanceTo(target.position());
        boolean climbing = pursuitPhase == PursuitPhase.CLIMB;
        boolean cruising = pursuitPhase == PursuitPhase.OVERPASS || pursuitPhase == PursuitPhase.CHASE;
        boolean farFromTarget = distance > Math.max(9.0, idealChaseDistance.get() * 1.6);

        double remainingRise = climbing && target != null
                ? target.getBoundingBox().maxY + overtakeHeight.get() - player.getY()
                : 0.0;
        double verticalBudget = Math.max(0.0, velocity.y) * 6.0;
        boolean climbNeedsBoost = climbing && remainingRise > verticalBudget + 1.0;
        boolean speedNeedsBoost = speed < reboostSpeed
                || (climbing || pursuitPhase == PursuitPhase.OVERPASS) && speed < reboostSpeed * 1.25;

        // A newly launched rocket may explode against an entity or nearby geometry.
        // Recover without waiting for velocity to decay below the speed threshold.
        boolean interrupted = boost.recentlyEndedEarly() && (farFromTarget || climbNeedsBoost);
        // The random fuse can be just ten ticks for Flight 0. Plan a replacement
        // before losing thrust, but do not waste rockets in the final attack band.
        boolean endingSoon = boost.active() && boost.guaranteedTicks() <= FIREWORK_END_GUARD_TICKS
                && (climbNeedsBoost || cruising && farFromTarget);
        boolean boostRequired = speedNeedsBoost || climbNeedsBoost || interrupted || endingSoon;
        if (!boostRequired) return false;
        if (boost.active() && boost.guaranteedTicks() > FIREWORK_END_GUARD_TICKS
                && !needsEmergencyReboost(player)) return false;
        // Rocket collision at point-blank range can end the boost on the next tick.
        // Close-range attacks and mace dives take priority over burning another rocket.
        return distance >= 4.5 || climbNeedsBoost;
    }

    private boolean needsEmergencyReboost(LocalPlayer player) {
        if (player == null || !player.isFallFlying() || target == null) return false;
        double speed = player.getDeltaMovement().horizontalDistance();
        double expected = Modules.enabled(SuperFirework.class)
                ? expectedSuperFireworkSpeed(player)
                : UNMANAGED_REBOOST_SPEED / 0.60;
        if (speed < Math.max(0.20, expected * 0.27)
                && player.getDeltaMovement().y < -0.08) return true;
        return pursuitPhase == PursuitPhase.CLIMB
                && player.getY() < target.getBoundingBox().maxY + overtakeHeight.get() * 0.65
                && player.getDeltaMovement().y < -0.10;
    }

    private boolean hasFireworkRocket(LocalPlayer player) {
        if (player == null) return false;
        if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) return true;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).is(Items.FIREWORK_ROCKET)) return true;
        }
        return false;
    }

    private boolean queueEmergencyAvoidanceRotation(LocalPlayer player, Rotation pursuit) {
        if (queueBlockDodgeRotation(player, pursuit)) return true;
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
        RotationManager.INSTANCE.setRotationTarget(plan, CRYSTAL_FEAR_ROTATION_PRIORITY, emergencyRotationProvider);
        if (RotationManager.INSTANCE.getActiveRotationTarget() != plan) {
            RotationManager.INSTANCE.release(emergencyRotationProvider, false);
            return false;
        }
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
        Rotation heading = RotationManager.INSTANCE.getMovementRotation();
        FlightModel physics = flightPhysics(player);
        for (int i = 0; i < ticks; i++) {
            velocity = physics.glide(velocity, heading);
            predicted = predicted.add(velocity);
            velocity = physics.afterMove(velocity, heading, i + 1, physics.possibleBoostTicks());
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
        Vec3 away = horizontal(player.getEyePosition().subtract(threat.pos()));
        if (away.lengthSqr() < 1.0E-5) away = horizontal(player.getDeltaMovement());
        if (away.lengthSqr() < 1.0E-5) away = horizontal(player.getLookAngle());
        Rotation preferred = Rotation.fromRotationVec(away);
        FlightModel physics = flightPhysics(player);
        Vec3 velocity = player.getDeltaMovement();
        int horizon = ElytraEscapePlanner.horizon(velocity, preferred, blockDodgeClearance.get(),
                crystalFearPredictionTicks.get(), physics);
        var world = ElytraEscapePlanner.world(player);
        Rotation best = null;
        double bestDistance = player.getBoundingBox().getCenter().distanceToSqr(threat.pos());
        double bestNearest = -1.0;
        for (float yaw : new float[]{0, -35, 35, -70, 70, -115, 115, 180}) {
            for (float pitch : new float[]{0, -30, 30, -60}) {
                Rotation candidate = new Rotation(preferred.yaw() + yaw, pitch).normalize();
                var outcome = ElytraEscapePlanner.forecast(world, player.getBoundingBox(), velocity,
                        candidate, horizon, physics, threat.pos());
                if (!outcome.safe() || outcome.blockedSteering()) continue;
                double distance = outcome.endPosition().distanceToSqr(threat.pos());
                if (outcome.nearestDistanceSquared() > bestNearest
                        || (outcome.nearestDistanceSquared() == bestNearest && distance > bestDistance)) {
                    bestDistance = distance;
                    bestNearest = outcome.nearestDistanceSquared();
                    best = candidate;
                }
            }
        }
        return best;
    }

    private boolean queueBlockDodgeRotation(LocalPlayer player, Rotation intendedRotation) {
        if (!solidBlockDodge.get() || !isValidBlockDodgeSelf(player)) {
            resetBlockDodge();
            return false;
        }
        Vec3 velocity = player.getDeltaMovement();
        FlightModel physics = flightPhysics(player);
        int horizon = ElytraEscapePlanner.horizon(velocity, intendedRotation, blockDodgeClearance.get(),
                blockDodgePredictionTicks.get(), physics);
        var world = ElytraEscapePlanner.world(player);
        AABB bounds = player.getBoundingBox();
        var intended = ElytraEscapePlanner.forecast(world, bounds, velocity, intendedRotation, horizon, physics);
        if (intended.safe()) {
            resetBlockDodge();
            return false;
        }
        var escape = ElytraEscapePlanner.findSaferEscape(world, bounds, velocity, intendedRotation,
                blockDodgeRotation, horizon, physics, intended, Rotation::normalize);
        if (escape == null) {
            resetBlockDodge();
            return false;
        }
        RotationTarget plan = new RotationTarget(
                escape.rotation(), null, List.of(), 2, 2.0f,
                false, MovementCorrection.SILENT, false, null
        );
        RotationManager.INSTANCE.setRotationTarget(plan, BLOCK_DODGE_ROTATION_PRIORITY, emergencyRotationProvider);
        if (RotationManager.INSTANCE.getActiveRotationTarget() != plan) {
            RotationManager.INSTANCE.release(emergencyRotationProvider, false);
            return false;
        }
        blockDodgeRotation = escape.rotation();
        return true;
    }

    private FlightModel flightPhysics(LocalPlayer player) {
        FireworkUseController.BoostState boost = FireworkUseController.INSTANCE.getBoostState(player);
        SuperFirework firework = Modules.get(SuperFirework.class);
        double gravity = player.isNoGravity() ? 0.0 : player.getAttributeValue(Attributes.GRAVITY);
        return new FlightModel(gravity, player.hasEffect(MobEffects.SLOW_FALLING),
                boost.active() ? boost.guaranteedTicks() : 0,
                boost.awaitingSpawn() ? Integer.MAX_VALUE : boost.active() ? Math.max(1, boost.expectedTicks()) : 0,
                firework != null && firework.isEnabled()
                        ? firework::estimateBoostVelocity : ElytraFlightPhysics::rocket);
    }

    private boolean isBoostPathSafe(LocalPlayer player, Rotation heading) {
        if (!solidBlockDodge.get()) return true;
        FlightModel current = flightPhysics(player);
        FlightModel powered = new FlightModel(current.gravity(), current.slowFalling(), 0,
                Integer.MAX_VALUE, current.boost());
        Vec3 velocity = current.boost().apply(player.getDeltaMovement(), heading);
        int horizon = ElytraEscapePlanner.horizon(velocity, heading, blockDodgeClearance.get(),
                blockDodgePredictionTicks.get(), powered);
        return ElytraEscapePlanner.forecast(ElytraEscapePlanner.world(player), player.getBoundingBox(),
                velocity, heading, horizon, powered).safe();
    }

    private void resetBlockDodge() {
        RotationManager.INSTANCE.release(emergencyRotationProvider, false);
        blockDodgeRotation = null;
    }

    private boolean isValidBlockDodgeSelf(LocalPlayer player) {
        return player != null && mc.level != null && player.isFallFlying()
                && !player.onGround() && !player.isPassenger()
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
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

}
