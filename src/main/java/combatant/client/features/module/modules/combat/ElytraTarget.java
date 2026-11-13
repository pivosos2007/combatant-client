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
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
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
import combatant.client.util.player.ElytraTargetReachability;
import combatant.client.util.player.ElytraFlightPhysics;
import combatant.client.util.player.ElytraFlightPhysics.FlightModel;
import net.minecraft.world.effect.MobEffects;
import combatant.client.util.player.inventory.FireworkUseController;
import combatant.client.features.module.modules.movement.SuperFirework;
import combatant.client.util.player.simulation.PlayerSimulationCache;
import combatant.client.util.logging.ElytraDiveTrace;
import combatant.client.util.target.TargetManager;
import combatant.client.util.world.ExplosionDamageUtil;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

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
    private static final int TAKEOFF_JUMP_RETRY_TICKS = 5;
    private static final int TAKEOFF_PACKET_RETRY_TICKS = 5;
    private static final int MACE_JUMPER_TARGET_FORECAST_TICKS = 8;
    private static final int MACE_RECOVERY_TIMEOUT_TICKS = 100;
    private static final int ROUTE_RECHECK_TICKS = 5;
    private static final int MACE_JUMPER_MIN_CLIMB_TICKS = 4;
    private static final int MACE_JUMPER_TURN_TICKS = 6;
    private static final double COLLISION_CLEARANCE_MARGIN = 1.25;
    private static final double GROUND_TARGET_STILL_SPEED = 0.08;
    private static final double ORBIT_MIN_RADIUS = 1.15;
    private static final double ORBIT_MAX_RADIUS = 3.35;
    private static final double ORBIT_ATTACK_RANGE_MARGIN = 0.22;
    private static final double ORBIT_SIDE_SWITCH_DOT = 0.18;
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
    private final BooleanValue maceJumper =
            visibleWhen(bool(
                    "elytratargetMaceJumper",
                    "mace_jumper",
                    false
            ), this::isPursuitEnabled);
    private final NumberValue<Float> maceJumperHeight =
            visibleWhen(num(
                    "elytratargetMaceJumperHeight",
                    "mace_jumper_height",
                    10.0f,
                    4.0f,
                    24.0f
            ), this::isMaceJumperEnabled);
    private final NumberValue<Integer> maceJumperDelay =
            visibleWhen(num(
                    "elytratargetMaceJumperDelay",
                    "mace_jumper_delay",
                    8,
                    0,
                    40
            ), this::isMaceJumperEnabled);
    private final NumberValue<Integer> maceJumperMaxRepeats =
            visibleWhen(num(
                    "elytratargetMaceJumperMaxRepeats",
                    "mace_jumper_max_repeats",
                    2,
                    0,
                    10
            ), this::isMaceJumperEnabled);
    private final NumberValue<Float> maceJumperMaxTargetSpeed =
            visibleWhen(num(
                    "elytratargetMaceJumperMaxTargetSpeed",
                    "mace_jumper_max_target_speed",
                    0.35f,
                    0.08f,
                    1.5f
            ), this::isMaceJumperEnabled);
    private final NumberValue<Float> maceJumperMaxTargetShift =
            visibleWhen(num(
                    "elytratargetMaceJumperMaxTargetShift",
                    "mace_jumper_max_target_shift",
                    5.0f,
                    1.0f,
                    16.0f
            ), this::isMaceJumperEnabled);
    private final NumberValue<Integer> maceJumperTimeout =
            visibleWhen(num(
                    "elytratargetMaceJumperTimeout",
                    "mace_jumper_timeout",
                    100,
                    30,
                    200
            ), this::isMaceJumperEnabled);
    private final NumberValue<Float> targetRange =
            visibleWhen(num(
                    "elytratargetTargetRange",
                    "target_range",
                    42.0f,
                    6.0f,
                    96.0f
            ), this::hasTargetingPurpose);
    private final NumberValue<Integer> preparationLeadTicks =
            visibleWhen(num(
                    "elytratargetPreparationLeadTicks",
                    "preparation_lead_ticks",
                    4,
                    0,
                    20
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
    private boolean autoTakeoffPending;
    private boolean queuedAutoTakeoffJump;
    private int autoTakeoffGlideDelayTicks;
    private int autoTakeoffCooldownTicks;
    private int lastAutoTakeoffJumpAge = Integer.MIN_VALUE;
    private int firstAutoTakeoffGlideAge = -1;
    private int lastAutoTakeoffGlidePacketAge = Integer.MIN_VALUE;
    private MaceJumperPhase maceJumperPhase = MaceJumperPhase.NONE;
    private int maceJumperTargetId = -1;
    private int maceJumperExpiryAge = -1;
    private int maceJumperNextClimbAge;
    private int maceJumperClimbAge;
    private int maceJumperTurnAge = -1;
    private float maceJumperTurnStartPitch;
    private float maceJumperTurnStartYaw;
    private float maceJumperTurnTargetYaw;
    private double maceJumperClimbGoalY = Double.NaN;
    private Vec3 maceJumperClimbHeading;
    private int maceJumperLastAttackAge = -1;
    private int maceCycleLastEndAge = -1;
    private int maceJumperRepeatedAttacks;
    private int maceJumperCountedTargetId = -1;
    private Vec3 maceJumperTargetOrigin;
    private boolean maceJumperRepeatAllowed;
    private boolean maceRecoveryPending;
    private int maceRecoveryExpiryAge = -1;
    private double maceRecoveryOriginY;
    private final Map<Integer, ReachabilityState> reachableTargets = new HashMap<>();

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
        if (ElytraDiveTrace.INSTANCE.active()) ElytraDiveTrace.INSTANCE.finish("module_disabled");
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
            LivingEntity oldTarget = target;
            target = entity instanceof LivingEntity living && isValidTarget(living) ? living : null;
            if (ElytraDiveTrace.INSTANCE.active() && oldTarget != target) {
                ElytraDiveTrace.INSTANCE.event("swapTarget old=" + (oldTarget == null ? -1 : oldTarget.getId())
                        + " new=" + (target == null ? -1 : target.getId()));
            }
            TargetManager.publish(this, target, TargetManager.Source.MODULE, 900);
            queuedFireworkUse = false;
            ElytraRecastUtil.INSTANCE.reset(this);
            return;
        }

        LivingEntity combatTarget = resolveCombatTarget(player);
        boolean validSelf = isValidSelf(player);
        boolean validTarget = isValidTarget(combatTarget);

        LivingEntity oldTarget = target;
        target = validSelf && validTarget ? combatTarget : null;
        if (ElytraDiveTrace.INSTANCE.active() && oldTarget != target) {
            ElytraDiveTrace.INSTANCE.event("targetChange old=" + (oldTarget == null ? -1 : oldTarget.getId())
                    + " new=" + (target == null ? -1 : target.getId())
                    + " selfValid=" + validSelf + " targetValid=" + validTarget);
        }
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

        boolean tracing = traceMaceTick(player);
        try {
            if (hitStatusTicks < HIT_STATUS_READY_TICKS) {
                hitStatusTicks++;
            }
            ElytraRecastUtil.INSTANCE.tick(this, player);
            captureMaceCycleAttack(player);
            captureMaceCycleEnd(player);
            tickMaceRecovery(player);
            tickMaceJumper(player);

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

            updatePursuitPhase(player, target);
            if (!maceRecoveryPending && maceJumperPhase != MaceJumperPhase.CLIMB
                    && maceJumperPhase != MaceJumperPhase.TURN
                    && maceJumperPhase != MaceJumperPhase.RECOVER
                    && tryPrepareMaceDive(player)) return;
            if (isPursuitEnabled() && shouldUseFirework(player)) {
                queuedFireworkUse = true;
            }
        } finally {
            if (tracing) {
                ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                        "end phase=%s pursuit=%s target=%d lock=%d y=%.3f vy=%.3f goalY=%.3f recovery=%s flying=%s readySwap=%s queueRocket=%s",
                        maceJumperPhase, pursuitPhase, target == null ? -1 : target.getId(), maceJumperTargetId,
                        player.getY(), player.getDeltaMovement().y, maceJumperClimbGoalY,
                        maceRecoveryPending, player.isFallFlying(), isAttributeSwapElytraCycleActive(), queuedFireworkUse));
            }
        }
    }

    @EventHandler(priority = FIREWORK_SYNC_PRIORITY)
    private void onSyncFirework(EventSync event) {
        if (!queuedFireworkUse || !isEnabled()) return;
        queuedFireworkUse = false;

        LocalPlayer player = mc.player;
        if (player == null || !isRunning() || isAttributeSwapElytraCycleActive()) return;
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
        if (ElytraDiveTrace.INSTANCE.active()) {
            ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                    "firework launched=%s phase=%s yaw=%.2f pitch=%.2f",
                    launched, maceJumperPhase, fireworkRotation.yaw(), fireworkRotation.pitch()));
        }
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
        Rotation resolved = event.getMovementRotation();
        if (ElytraDiveTrace.INSTANCE.active() && resolved != null) {
            ElytraDiveTrace.INSTANCE.advanceTick(player.tickCount);
            ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                    "resolvedRotation yaw=%.2f pitch=%.2f ownedByElytraTarget=%s",
                    resolved.yaw(), resolved.pitch(), RotationManager.INSTANCE.ownsActiveRotation(this)));
        }
        emergencyAvoidQueued = queueEmergencyAvoidanceRotation(player, resolved);
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
            if (!maceRecoveryPending && maceJumperPhase != MaceJumperPhase.CLIMB
                    && maceJumperPhase != MaceJumperPhase.TURN
                    && maceJumperPhase != MaceJumperPhase.RECOVER)
                tryPrepareMaceDive(player);
            pursuitRotation = calculatePursuitRotation(player, target);
            if (ElytraDiveTrace.INSTANCE.active() && pursuitRotation != null) {
                ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                        "rotation phase=%s pursuit=%s yaw=%.2f pitch=%.2f owns=%s",
                        maceJumperPhase, pursuitPhase, pursuitRotation.yaw(), pursuitRotation.pitch(),
                        RotationManager.INSTANCE.ownsActiveRotation(this)));
            }
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
        return isRunning() && (maceJumperPhase != MaceJumperPhase.NONE
                || pursuitRotationActive || preparingRotation || attackWindowActive || pendingRotation != null);
    }

    /** The mace swap, landing and relaunch must not compete with aura attacks or smart-crit jumps. */
    public boolean shouldSuspendKillAura() {
        if (!isEnabled() || mc.player == null || mc.level == null) return false;
        AttributeSwap swap = Modules.get(AttributeSwap.class);
        // A cycle may finish on the same tick as KillAura's rotation PRE event.
        // Don't leave a one-tick ground-combat gap before captureMaceCycleEnd().
        boolean justEndedCycle = swap != null && !mc.player.isFallFlying()
                && swap.getLastElytraCycleEndAge(mc.player) == mc.player.tickCount;
        return isAttributeSwapElytraCycleActive()
                || justEndedCycle
                || maceJumperPhase != MaceJumperPhase.NONE
                || maceRecoveryPending
                || autoTakeoffPending && hasKillAuraGate();
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
        return isEnabled() && (isAttributeSwapElytraCycleActive()
                || maceJumperPhase != MaceJumperPhase.NONE
                || maceRecoveryPending
                || emergencyAvoidQueued || (isRunning() && isPursuitEnabled()));
    }

    private void resetState() {
        if (ElytraDiveTrace.INSTANCE.active()) {
            ElytraDiveTrace.INSTANCE.finish("reset");
        }
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
        autoTakeoffPending = false;
        queuedAutoTakeoffJump = false;
        autoTakeoffGlideDelayTicks = 0;
        autoTakeoffCooldownTicks = 0;
        lastAutoTakeoffJumpAge = Integer.MIN_VALUE;
        firstAutoTakeoffGlideAge = -1;
        lastAutoTakeoffGlidePacketAge = Integer.MIN_VALUE;
        clearMaceJumper();
        maceRecoveryPending = false;
        maceRecoveryExpiryAge = -1;
        maceRecoveryOriginY = 0.0;
        reachableTargets.clear();
        maceJumperLastAttackAge = -1;
        maceCycleLastEndAge = -1;
        maceJumperRepeatedAttacks = 0;
        maceJumperCountedTargetId = -1;
    }

    private LivingEntity resolveCombatTarget(LocalPlayer player) {
        if (player == null || !hasKillAuraGate() || !hasTargetingPurpose()) {
            return null;
        }

        KillAura killAura = Modules.get(KillAura.class);

        List<LivingEntity> candidates = killAura.findElytraTargetCandidates(targetRange.get());
        if (maceJumperPhase != MaceJumperPhase.NONE && mc.level != null) {
            var lockedEntity = mc.level.getEntity(maceJumperTargetId);
            if (lockedEntity instanceof LivingEntity locked
                    && (candidates.contains(locked) && isTargetReachable(player, locked)
                    || canRetainMaceJumperTarget(player, locked))) return locked;
            if ((maceJumperPhase == MaceJumperPhase.CLIMB || maceJumperPhase == MaceJumperPhase.TURN
                    || maceJumperPhase == MaceJumperPhase.DIVE)
                    && lockedEntity instanceof LivingEntity locked && locked.isAlive() && !locked.isRemoved()) {
                return null;
            }
        }
        LivingEntity auraTarget = killAura.getCurrentTarget();
        if (candidates.contains(auraTarget) && isTargetReachable(player, auraTarget)) return auraTarget;
        if (candidates.contains(target) && isTargetReachable(player, target)) return target;
        for (LivingEntity candidate : candidates) {
            if (isTargetReachable(player, candidate)) return candidate;
        }
        return null;
    }

    private ElytraTargetReachability.Route routeToTarget(LocalPlayer player, LivingEntity entity) {
        if (player == null || entity == null || mc.level == null) return null;
        int age = player.tickCount;
        ReachabilityState cached = reachableTargets.get(entity.getId());
        if (cached != null && age - cached.tick() >= 0 && age - cached.tick() < ROUTE_RECHECK_TICKS
                && player.position().distanceToSqr(cached.playerPosition()) < 6.25
                && entity.position().distanceToSqr(cached.targetPosition()) < 2.25) {
            return cached.route();
        }
        ElytraTargetReachability.Route route = ElytraTargetReachability.findRoute(player, entity);
        if (reachableTargets.size() > 64) reachableTargets.clear();
        reachableTargets.put(entity.getId(), new ReachabilityState(player.position(), entity.position(), age, route));
        return route;
    }

    private boolean isTargetReachable(LocalPlayer player, LivingEntity entity) {
        return entity != null && !ElytraTargetReachability.isSubmerged(entity)
                && routeToTarget(player, entity) != null;
    }

    private Vec3 routeWaypoint(LocalPlayer player, LivingEntity entity) {
        ElytraTargetReachability.Route route = routeToTarget(player, entity);
        if (route == null || route.waypoints().isEmpty()
                || ElytraTargetReachability.hasDirectContact(player, entity)) return null;
        Vec3 eye = player.getEyePosition();
        // Skip previously reached route segments without steering back to their old origins.
        for (int i = route.waypoints().size() - 1; i >= 0; i--) {
            Vec3 waypoint = route.waypoints().get(i);
            if (eye.distanceToSqr(waypoint) < 2.25) continue;
            if (ElytraTargetReachability.hasClearSegment(player, eye, waypoint)) return waypoint;
        }
        return route.waypoints().getFirst();
    }

    private boolean hasKillAuraGate() {
        KillAura killAura = Modules.get(KillAura.class);
        return killAura != null && killAura.isEnabled();
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
        if (entity == null || !entity.isAlive() || entity.isRemoved()) return false;
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (maceJumperPhase != MaceJumperPhase.NONE && entity.getId() == maceJumperTargetId
                && canRetainMaceJumperTarget(player, entity)) return true;
        KillAura aura = Modules.get(KillAura.class);
        return aura != null && aura.findElytraTargetCandidates(targetRange.get()).contains(entity)
                && isTargetReachable(player, entity);
    }

    private boolean canRetainMaceJumperTarget(LocalPlayer player, LivingEntity entity) {
        if (player == null || entity == null || !entity.isAlive() || entity.isRemoved()
                || ElytraTargetReachability.isSubmerged(entity)) return false;

        // The acquisition range is spherical, but during a committed vertical
        // maneuver the target must stay locked as long as it is still nearby in XZ.
        double horizontalRange = Math.max(targetRange.get(), getAuraAttackBand(entity) * 2.0);
        if (horizontalDistance(player.position(), entity.position()) > horizontalRange) return false;
        double verticalGap = Math.max(0.0, Math.max(
                entity.getBoundingBox().minY - player.getEyeY(),
                player.getEyeY() - entity.getBoundingBox().maxY));
        double retainedRange = Math.hypot(horizontalRange, verticalGap) + 2.0;
        KillAura aura = Modules.get(KillAura.class);
        if (aura == null || !aura.canRetainElytraTarget(entity, retainedRange)) return false;

        // Re-check reachability before the dive, not during the ongoing climb.
        return true;
    }

    private boolean isPursuitEnabled() {
        return !"off".equals(pursuitMode.get());
    }

    private boolean isMaceJumperEnabled() {
        return maceJumper.get() && isPursuitEnabled();
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

        if (maceRecoveryPending && player.isFallFlying()
                || maceJumperPhase == MaceJumperPhase.RECOVER && player.isFallFlying()
                || maceJumperPhase == MaceJumperPhase.CLIMB || maceJumperPhase == MaceJumperPhase.TURN
                || maceJumperPhase == MaceJumperPhase.DIVE) {
            boolean dive = maceJumperPhase == MaceJumperPhase.DIVE;
            pursuitPhase = dive ? PursuitPhase.DIVE : PursuitPhase.CLIMB;
            preparingRotation = !dive;
            attackWindowActive = dive;
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
        if (ElytraDiveTrace.INSTANCE.active()) {
            ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                    "steerPoint phase=%s x=%.3f y=%.3f z=%.3f target=%d",
                    maceJumperPhase, point.x, point.y, point.z, currentTarget.getId()));
        }

        Vec3 steeringOrigin = maceJumperPhase == MaceJumperPhase.CLIMB
                || maceJumperPhase == MaceJumperPhase.TURN
                || maceJumperPhase == MaceJumperPhase.RECOVER
                ? player.getEyePosition() : getPursuitSteeringOrigin(player, true);
        return Rotation.lookingAt(point, steeringOrigin).normalize();
    }

    private Vec3 calculatePursuitPoint(LocalPlayer player, LivingEntity currentTarget) {
        Vec3 targetCenter = predictPursuitTargetCenter(player, currentTarget);
        if (maceJumperPhase == MaceJumperPhase.DIVE) {
            return targetCenter.add(0.0, -currentTarget.getBoundingBox().getYsize() * 0.15, 0.0);
        }
        if (maceJumperPhase == MaceJumperPhase.TURN) {
            return calculateMaceJumperTurnPoint(player);
        }
        if (maceJumperPhase == MaceJumperPhase.CLIMB
                || maceJumperPhase == MaceJumperPhase.RECOVER && player.isFallFlying()) {
            ensureMaceJumperClimbGuidance(player, currentTarget);
            return calculateMaceJumperClimbPoint(player, targetCenter);
        }
        if (maceRecoveryPending && player.isFallFlying()) {
            Vec3 current = player.position();
            Vec3 correction = horizontal(targetCenter.subtract(current));
            if (correction.lengthSqr() > 1.0E-4) {
                correction = correction.normalize().scale(Math.min(0.65, correction.length() * 0.12));
            }
            double climbHeight = isMaceJumperEnabled() ? maceJumperHeight.get()
                    : Math.max(6.0, overtakeHeight.get());
            double top = Math.max(current.y + 5.0,
                    currentTarget.getBoundingBox().maxY + climbHeight);
            return new Vec3(current.x + correction.x, top, current.z + correction.z);
        }
        Vec3 waypoint = routeWaypoint(player, currentTarget);
        if (waypoint != null) return waypoint;
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

    private void ensureMaceJumperClimbGuidance(LocalPlayer player, LivingEntity currentTarget) {
        if (Double.isFinite(maceJumperClimbGoalY) && maceJumperClimbHeading != null) return;
        maceJumperClimbGoalY = Math.max(player.getY(),
                currentTarget.getBoundingBox().maxY + maceJumperHeight.get());
        Vec3 heading = horizontal(player.getDeltaMovement());
        if (heading.lengthSqr() < 0.01) {
            heading = horizontal(player.getViewVector(1.0f));
        }
        if (heading.lengthSqr() <= 1.0E-5) {
            double yaw = Math.toRadians(player.getYRot());
            heading = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        maceJumperClimbHeading = heading.normalize();
        ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                "jumperClimbAnchor goalY=%.3f headingX=%.3f headingZ=%.3f",
                maceJumperClimbGoalY, maceJumperClimbHeading.x, maceJumperClimbHeading.z));
    }

    private Vec3 calculateMaceJumperClimbPoint(LocalPlayer player, Vec3 targetCenter) {
        Vec3 origin = player.getEyePosition();
        Vec3 towardTarget = horizontal(targetCenter.subtract(player.position()));
        double distance = towardTarget.length();
        Vec3 correction = distance > 1.0E-4
                ? towardTarget.scale(Math.min(0.65, distance * 0.12) / distance) : Vec3.ZERO;
        Vec3 heading = maceJumperClimbHeading != null
                ? maceJumperClimbHeading : horizontal(player.getViewVector(1.0f)).normalize();
        return origin.add(heading.scale(0.50)).add(correction).add(0.0, 6.0, 0.0);
    }

    private Vec3 calculateMaceJumperTurnPoint(LocalPlayer player) {
        float progress = (float) Mth.clamp((player.tickCount - maceJumperTurnAge)
                / (double) MACE_JUMPER_TURN_TICKS, 0.0, 1.0);
        double pitch = maceJumperTurnStartPitch + (65.0 - maceJumperTurnStartPitch) * progress;
        // Reblending horizontal direction every tick could switch its sign
        // as the player overtook the target, causing an instant 150+ degree yaw.
        float yaw = Mth.rotLerp(progress, maceJumperTurnStartYaw, maceJumperTurnTargetYaw);
        double horizontalDistance = 4.0;
        double radians = Math.toRadians(yaw);
        return player.getEyePosition().add(
                -Math.sin(radians) * horizontalDistance,
                -Math.tan(Math.toRadians(pitch)) * horizontalDistance,
                Math.cos(radians) * horizontalDistance);
    }

    private void beginMaceJumperTurn(LocalPlayer player, LivingEntity currentTarget, float initialPitch) {
        Vec3 heading = maceJumperClimbHeading != null
                ? maceJumperClimbHeading : horizontal(player.getViewVector(1.0f)).normalize();
        if (heading.lengthSqr() <= 1.0E-5) heading = new Vec3(0.0, 0.0, 1.0);
        maceJumperTurnStartYaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
        Vec3 toTarget = horizontal(currentTarget.getBoundingBox().getCenter().subtract(player.getEyePosition()));
        maceJumperTurnTargetYaw = toTarget.lengthSqr() > 1.0E-4
                ? (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z))
                : maceJumperTurnStartYaw;
        maceJumperTurnStartPitch = initialPitch;
        maceJumperTurnAge = player.tickCount;
        MaceJumperPhase previousPhase = maceJumperPhase;
        maceJumperPhase = MaceJumperPhase.TURN;
        maceRecoveryPending = false;
        ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                "jumperTransition %s->TURN pitch=%.2f yaw=%.2f targetYaw=%.2f",
                previousPhase, initialPitch, maceJumperTurnStartYaw, maceJumperTurnTargetYaw));
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

    private boolean tryPrepareMaceDive(LocalPlayer player) {
        if (emergencyAvoidQueued || !canSetupMaceDive(player)
                || target == null || !ElytraTargetReachability.hasDirectContact(player, target)) return false;
        AttributeSwap swap = Modules.get(AttributeSwap.class);
        if (swap == null || !swap.prepareElytraTargetDive(player, target,
                adaptivePreparationLeadTicks(player, target))) return false;
        queuedFireworkUse = false;
        clearAutoTakeoffState();
        ElytraRecastUtil.INSTANCE.reset(this);
        return true;
    }

    private int adaptivePreparationLeadTicks(LocalPlayer player, LivingEntity currentTarget) {
        Vec3 toTarget = currentTarget.getBoundingBox().getCenter().subtract(player.getEyePosition());
        if (toTarget.lengthSqr() < 1.0E-6) return preparationLeadTicks.get();
        Vec3 direction = toTarget.normalize();
        double approachSpeed = Math.max(0.0,
                player.getDeltaMovement().subtract(currentTarget.getDeltaMovement()).dot(direction));
        FireworkUseController.BoostState boost = FireworkUseController.INSTANCE.getBoostState(player);
        if (Modules.enabled(SuperFirework.class) && (boost.active() || boost.awaitingSpawn())) {
            Vec3 accelerated = predictSuperFireworkVelocity(player.getDeltaMovement(), direction);
            approachSpeed = Math.max(approachSpeed,
                    accelerated.subtract(currentTarget.getDeltaMovement()).dot(direction));
        }
        // The inventory swap consumes several movement ticks. At high closing speeds
        // the same time budget represents a much larger distance to the target.
        int speedLead = (int) Math.ceil(Math.max(0.0, approachSpeed - 0.65) * 2.5);
        return Mth.clamp(preparationLeadTicks.get() + speedLead, 0, 36);
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

    /** Advances pursuit interception with the same flight/rocket model as collision forecasts. */
    private Vec3 predictPursuitTargetCenter(LocalPlayer player, LivingEntity currentTarget) {
        int maxTicks = Mth.clamp(pursuitPredictionTicks.get(), 0, 20);
        if (maxTicks == 0) return predictTargetCenter(currentTarget, 0);
        Vec3 origin = player.getEyePosition();
        Vec3 velocity = player.getDeltaMovement();
        FireworkUseController.BoostState boost = FireworkUseController.INSTANCE.getBoostState(player);
        int poweredTicks = boost.active() ? Math.max(0, boost.expectedTicks()) : 0;
        if (boost.awaitingSpawn()) poweredTicks = Math.max(poweredTicks, 10);
        if (poweredTicks == 0 && hasSuperFireworkSteering() && shouldUseFirework(player)) {
            poweredTicks = 10;
        }
        FlightModel physics = flightPhysics(player);
        Vec3 lastTarget = predictTargetCenter(currentTarget, 0);
        double attackBand = getAuraAttackBand(currentTarget);
        for (int tick = 1; tick <= maxTicks; tick++) {
            Vec3 nextTarget = predictTargetCenter(currentTarget, tick);
            Vec3 delta = nextTarget.subtract(origin);
            Rotation heading = delta.lengthSqr() > 1.0E-6
                    ? Rotation.fromRotationVec(delta) : Rotation.fromRotationVec(player.getLookAngle());
            Vec3 movement = physics.glide(velocity, heading);
            Vec3 nextOrigin = origin.add(movement);
            Vec3 relativeStart = origin.subtract(lastTarget);
            Vec3 relativeMove = nextOrigin.subtract(origin).subtract(nextTarget.subtract(lastTarget));
            double lengthSq = relativeMove.lengthSqr();
            double fraction = lengthSq > 1.0E-6
                    ? Mth.clamp(-relativeStart.dot(relativeMove) / lengthSq, 0.0, 1.0) : 0.0;
            if (relativeStart.add(relativeMove.scale(fraction)).lengthSqr() <= attackBand * attackBand) {
                return lastTarget.lerp(nextTarget, fraction);
            }
            origin = nextOrigin;
            velocity = physics.afterMove(movement, heading, tick, poweredTicks);
            lastTarget = nextTarget;
        }
        return lastTarget;
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

    /** Captures flight and cycle state once per game tick while a target or recovery is active. */
    private boolean traceMaceTick(LocalPlayer player) {
        ElytraDiveTrace trace = ElytraDiveTrace.INSTANCE;
        AttributeSwap swap = Modules.get(AttributeSwap.class);
        boolean swapCycle = swap != null && swap.isElytraCycleActive();
        if (target == null && maceJumperPhase == MaceJumperPhase.NONE && !maceRecoveryPending && !swapCycle) {
            if (trace.active()) trace.finish("idle");
            return false;
        }
        int activeId = maceJumperTargetId >= 0 ? maceJumperTargetId
                : swapCycle ? swap.getElytraCycleTargetId()
                : target == null ? -1 : target.getId();
        trace.sample(player.tickCount, activeId, "ElytraTarget", String.format(Locale.ROOT,
                "phase=%s pursuit=%s target=%d lock=%d recovery=%s flying=%s ground=%s chestElytra=%s"
                        + " y=%.3f vy=%.3f vxz=%.3f goalY=%.3f climbAge=%d turnAge=%d"
                        + " repeats=%d allowed=%s swapCycle=%s",
                maceJumperPhase, pursuitPhase, target == null ? -1 : target.getId(), maceJumperTargetId,
                maceRecoveryPending, player.isFallFlying(), player.onGround(),
                player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA),
                player.getY(), player.getDeltaMovement().y, player.getDeltaMovement().horizontalDistance(),
                maceJumperClimbGoalY, maceJumperClimbAge, maceJumperTurnAge,
                maceJumperRepeatedAttacks, maceJumperRepeatAllowed, swapCycle));
        return true;
    }

    private void captureMaceCycleAttack(LocalPlayer player) {
        AttributeSwap swap = Modules.get(AttributeSwap.class);
        if (swap == null || player == null || !hasKillAuraGate()) return;
        int age = swap.getLastElytraCycleAttackAge(player);
        if (age < 0 || age <= maceJumperLastAttackAge || age > player.tickCount
                || player.tickCount - age > 3) return;
        maceJumperLastAttackAge = age;
        ElytraDiveTrace.INSTANCE.event("cycleAttack age=" + age);
        int targetId = swap.getLastElytraCycleAttackTargetId(player);
        if (targetId < 0) return;

        // Recovery is independent of the victim and of the optional follow-up smash.
        // After a killing blow, the new target must not hand control back to ground aura.
        maceRecoveryPending = true;
        maceRecoveryExpiryAge = player.tickCount + MACE_RECOVERY_TIMEOUT_TICKS;
        maceRecoveryOriginY = player.getY();
        autoTakeoffCooldownTicks = 0;
        clearAutoTakeoffState();

        if (!isMaceJumperEnabled()) {
            clearMaceJumper();
            return;
        }
        if (targetId != maceJumperCountedTargetId) {
            maceJumperCountedTargetId = targetId;
            maceJumperRepeatedAttacks = 0;
        } else {
            maceJumperRepeatedAttacks++;
        }
        maceJumperTargetId = targetId;
        maceJumperPhase = MaceJumperPhase.RECOVER;
        maceJumperExpiryAge = player.tickCount + maceJumperTimeout.get();
        maceJumperNextClimbAge = player.tickCount + maceJumperDelay.get();
        maceJumperTargetOrigin = null;
        maceJumperClimbGoalY = Double.NaN;
        maceJumperClimbHeading = null;
        maceJumperTurnAge = -1;
        maceJumperRepeatAllowed = maceJumperMaxRepeats.get() == 0
                || maceJumperRepeatedAttacks < maceJumperMaxRepeats.get();
        ElytraDiveTrace.INSTANCE.event("jumperStart target=" + targetId + " repeated="
                + maceJumperRepeatedAttacks + " allowed=" + maceJumperRepeatAllowed
                + " nextClimb=" + maceJumperNextClimbAge);
    }

    private void captureMaceCycleEnd(LocalPlayer player) {
        AttributeSwap swap = Modules.get(AttributeSwap.class);
        if (swap == null || player == null || !hasKillAuraGate()) return;
        int age = swap.getLastElytraCycleEndAge(player);
        if (age < 0 || age <= maceCycleLastEndAge || age > player.tickCount
                || player.tickCount - age > 3) return;
        maceCycleLastEndAge = age;
        ElytraDiveTrace.INSTANCE.event("cycleEnd age=" + age + " flying=" + player.isFallFlying());
        if (!player.isFallFlying()) {
            maceRecoveryPending = true;
            maceRecoveryExpiryAge = player.tickCount + MACE_RECOVERY_TIMEOUT_TICKS;
            maceRecoveryOriginY = player.getY();
            autoTakeoffCooldownTicks = 0;
        }
    }

    private void tickMaceRecovery(LocalPlayer player) {
        if (!maceRecoveryPending) return;
        if (player == null || mc.level == null || player.tickCount >= maceRecoveryExpiryAge
                || !hasKillAuraGate()) {
            ElytraDiveTrace.INSTANCE.event("recoveryEnd reason=expired_or_unavailable");
            maceRecoveryPending = false;
            return;
        }
        if (isAttributeSwapElytraCycleActive()) return;
        if (player.isFallFlying() && !player.onGround()
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            // Reaching gliding=true is only the first step. Hold ownership until
            // there is enough altitude for another dive; otherwise the next aura
            // target immediately turns the rocket back towards the ground.
            double aboveLaunch = player.getY() - maceRecoveryOriginY;
            LivingEntity nextTarget = target;
            double aboveTarget = nextTarget != null
                    ? player.getY() - nextTarget.getBoundingBox().maxY : aboveLaunch;
            double heightGoal = isMaceJumperEnabled()
                    ? maceJumperHeight.get() * 0.70 : Math.max(4.0, overtakeHeight.get() * 0.70);
            if (maceJumperPhase != MaceJumperPhase.RECOVER
                    && aboveLaunch >= 3.5 && aboveTarget >= heightGoal) {
                ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                        "recoveryEnd reason=height_ready aboveLaunch=%.2f aboveTarget=%.2f goal=%.2f",
                        aboveLaunch, aboveTarget, heightGoal));
                maceRecoveryPending = false;
            }
        }
    }

    private void tickMaceJumper(LocalPlayer player) {
        if (maceJumperPhase == MaceJumperPhase.NONE) return;
        if (!isMaceJumperEnabled() || !hasKillAuraGate() || mc.level == null || player == null
                || player.tickCount >= maceJumperExpiryAge) {
            ElytraDiveTrace.INSTANCE.event("jumperCancel reason=disabled_gate_or_timeout phase=" + maceJumperPhase);
            clearMaceJumper();
            return;
        }
        var entity = mc.level.getEntity(maceJumperTargetId);
        LivingEntity currentTarget = entity instanceof LivingEntity living && isValidTarget(living) ? living : null;
        if (currentTarget == null) {
            ElytraDiveTrace.INSTANCE.event("jumperReacquire reason=target_missing old=" + maceJumperTargetId);
            // The previous victim may die during the ascent. Keep the flight
            // recovery owner, then reacquire a new target rather than handing
            // the next ground contact to KillAura.
            maceJumperPhase = MaceJumperPhase.RECOVER;
            maceJumperTargetId = -1;
            maceJumperTargetOrigin = null;
            maceJumperClimbGoalY = Double.NaN;
            maceJumperClimbHeading = null;
            currentTarget = resolveCombatTarget(player);
            if (currentTarget != null) {
                maceJumperTargetId = currentTarget.getId();
                maceJumperCountedTargetId = -1;
                maceJumperRepeatedAttacks = 0;
                maceJumperRepeatAllowed = true;
                maceJumperNextClimbAge = Math.max(maceJumperNextClimbAge, player.tickCount);
            } else {
                // Reacquisition can take a few ticks; do not erase the
                // recovery phase while we are still on the ground.
                return;
            }
        }

        if (isAttributeSwapElytraCycleActive()) return;

        if (maceJumperPhase == MaceJumperPhase.RECOVER) {
            if (!player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
                    || !player.isFallFlying()) {
                ElytraDiveTrace.INSTANCE.event("jumperWait reason=flight_restoration chest="
                        + player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
                        + " flying=" + player.isFallFlying() + " ground=" + player.onGround());
                return;
            }
            ensureMaceJumperClimbGuidance(player, currentTarget);
            if (!maceJumperRepeatAllowed || !isMaceJumperTargetStable(currentTarget, null)) {
                ElytraDiveTrace.INSTANCE.event("jumperCancel reason=repeat_limit_or_unstable phase=RECOVER");
                clearMaceJumper();
                return;
            }
            if (player.tickCount < maceJumperNextClimbAge) {
                ElytraDiveTrace.INSTANCE.event("jumperWait reason=delay remaining="
                        + (maceJumperNextClimbAge - player.tickCount));
                return;
            }
            AttributeSwap swap = Modules.get(AttributeSwap.class);
            if (swap == null || !swap.canPrepareElytraTargetDive(player, currentTarget)) {
                ElytraDiveTrace.INSTANCE.event("jumperWait reason=swap_cannot_prepare");
                // Lack of a predicted smash window is not a reason to abandon
                // a fresh takeoff. Leave recovery to retry as equipment settles.
                return;
            }
            maceJumperTargetOrigin = currentTarget.position();
            maceJumperClimbAge = player.tickCount;
            maceJumperTurnAge = -1;
            ensureMaceJumperClimbGuidance(player, currentTarget);
            // If the player is already above the attack height and descending,
            // do not force a second upward boost before committing the turn.
            if (player.getY() >= currentTarget.getBoundingBox().maxY + maceJumperHeight.get()
                    && player.getDeltaMovement().y <= 0.15) {
                Rotation flightRotation = RotationManager.INSTANCE.getMovementRotation();
                beginMaceJumperTurn(player, currentTarget,
                        flightRotation != null ? flightRotation.pitch() : player.getXRot());
                return;
            }
            maceJumperPhase = MaceJumperPhase.CLIMB;
            ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                    "jumperTransition RECOVER->CLIMB goalY=%.3f target=%d", maceJumperClimbGoalY,
                    maceJumperTargetId));
            return;
        }

        if (!player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            ElytraDiveTrace.INSTANCE.event("jumperTransition " + maceJumperPhase + "->RECOVER reason=missing_elytra");
            maceJumperPhase = MaceJumperPhase.RECOVER;
            maceJumperClimbGoalY = Double.NaN;
            maceJumperClimbHeading = null;
            return;
        }
        if (!player.isFallFlying()) {
            ElytraDiveTrace.INSTANCE.event("jumperTransition " + maceJumperPhase + "->RECOVER reason=not_flying");
            maceJumperPhase = MaceJumperPhase.RECOVER;
            maceJumperTargetOrigin = null;
            maceJumperClimbGoalY = Double.NaN;
            maceJumperClimbHeading = null;
            maceRecoveryPending = true;
            maceRecoveryExpiryAge = player.tickCount + MACE_RECOVERY_TIMEOUT_TICKS;
            maceRecoveryOriginY = player.getY();
            autoTakeoffCooldownTicks = 0;
            clearAutoTakeoffState();
            return;
        }
        if (!isMaceJumperTargetStable(currentTarget, maceJumperTargetOrigin)
                || horizontalDistance(player.position(), currentTarget.position())
                > Math.max(8.0, getAuraAttackBand(currentTarget) * 2.0)) {
            ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                    "jumperCancel reason=target_unstable_or_distance distXZ=%.3f",
                    horizontalDistance(player.position(), currentTarget.position())));
            clearMaceJumper();
            return;
        }
        if (maceJumperPhase == MaceJumperPhase.DIVE) {
            AttributeSwap swap = Modules.get(AttributeSwap.class);
            if (swap == null || !swap.canPrepareElytraTargetDive(player, currentTarget)) {
                ElytraDiveTrace.INSTANCE.event("jumperCancel reason=swap_cannot_prepare phase=DIVE");
                clearMaceJumper();
            }
            return;
        }
        if (maceJumperPhase == MaceJumperPhase.CLIMB) {
            double risingSpeed = Math.max(0.0, player.getDeltaMovement().y);
            double leadRise = Math.max(1.0, Math.min(maceJumperHeight.get() * 0.4, risingSpeed * 2.5));
            boolean highEnough = player.getY() >= maceJumperClimbGoalY - leadRise
                    && player.getY() - currentTarget.getBoundingBox().maxY
                    >= Math.max(3.0, maceJumperHeight.get() * 0.6);
            ElytraDiveTrace.INSTANCE.event(String.format(Locale.ROOT,
                    "jumperClimb y=%.3f goalY=%.3f rise=%.3f lead=%.3f highEnough=%s age=%d",
                    player.getY(), maceJumperClimbGoalY, risingSpeed, leadRise, highEnough,
                    player.tickCount - maceJumperClimbAge));
            if (player.tickCount - maceJumperClimbAge >= MACE_JUMPER_MIN_CLIMB_TICKS
                    && highEnough) {
                Vec3 point = calculateMaceJumperClimbPoint(player, currentTarget.getBoundingBox().getCenter());
                beginMaceJumperTurn(player, currentTarget,
                        Rotation.lookingAt(point, player.getEyePosition()).pitch());
            }
        } else if (maceJumperPhase == MaceJumperPhase.TURN
                && player.tickCount - maceJumperTurnAge > MACE_JUMPER_TURN_TICKS) {
            if (!ElytraTargetReachability.hasDirectContact(player, currentTarget)) {
                ElytraDiveTrace.INSTANCE.event("jumperCancel reason=no_direct_contact_before_dive");
                clearMaceJumper();
                return;
            }
            maceJumperPhase = MaceJumperPhase.DIVE;
            ElytraDiveTrace.INSTANCE.event("jumperTransition TURN->DIVE");
        }
    }

    private boolean isMaceJumperTargetStable(LivingEntity currentTarget, Vec3 origin) {
        if (currentTarget.isFallFlying() || currentTarget.isInWater() || currentTarget.isInLava()
                || !currentTarget.onGround()) return false;
        double maxSpeed = maceJumperMaxTargetSpeed.get();
        if (currentTarget.getDeltaMovement().horizontalDistance() > maxSpeed) return false;
        double maxShift = maceJumperMaxTargetShift.get();
        if (origin != null && horizontalDistance(origin, currentTarget.position()) > maxShift) return false;
        if (currentTarget instanceof Player remote) {
            PlayerSimulationCache.SimulatedPlayerCache simulation = PlayerSimulationCache.getSimulationForOtherPlayers(remote);
            if (simulation.isRemoteFlightLike()) return false;
            PlayerSimulationCache.SimulatedPlayerSnapshot predicted =
                    simulation.getSnapshotAt(MACE_JUMPER_TARGET_FORECAST_TICKS);
            if (predicted.velocity().horizontalDistance() > maxSpeed
                    || horizontalDistance(predicted.pos(), remote.position()) > maxShift) return false;
            if (!predicted.onGround()
                    && Math.abs(predicted.pos().y - remote.getY()) > 1.5) return false;
        }
        return true;
    }

    private void clearMaceJumper() {
        if (maceJumperPhase != MaceJumperPhase.NONE) {
            ElytraDiveTrace.INSTANCE.event("jumperCleared phase=" + maceJumperPhase
                    + " target=" + maceJumperTargetId);
        }
        maceJumperPhase = MaceJumperPhase.NONE;
        maceJumperTargetId = -1;
        maceJumperExpiryAge = -1;
        maceJumperNextClimbAge = 0;
        maceJumperClimbAge = 0;
        maceJumperTurnAge = -1;
        maceJumperTurnStartPitch = 0.0f;
        maceJumperTurnStartYaw = 0.0f;
        maceJumperTurnTargetYaw = 0.0f;
        maceJumperClimbGoalY = Double.NaN;
        maceJumperClimbHeading = null;
        maceJumperTargetOrigin = null;
        maceJumperRepeatAllowed = false;
        maceJumperRepeatedAttacks = 0;
        maceJumperCountedTargetId = -1;
    }

    private void tickAutoTakeoff(LocalPlayer player) {
        if (autoTakeoffCooldownTicks > 0) {
            autoTakeoffCooldownTicks--;
        }
        if (autoTakeoffGlideDelayTicks > 0) {
            autoTakeoffGlideDelayTicks--;
        }

        if (!isAutoTakeoffEnabled() && !maceRecoveryPending && maceJumperPhase != MaceJumperPhase.RECOVER) {
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

        if (!canAutoTakeoffSelf(player) || !maceRecoveryPending && !hasAutoTakeoffTarget(player)) {
            clearAutoTakeoffState();
            return;
        }

        if (!autoTakeoffPending) {
            if (autoTakeoffCooldownTicks > 0 && !maceRecoveryPending) return;
            autoTakeoffPending = true;
            ElytraDiveTrace.INSTANCE.event("takeoffStart tick=" + player.tickCount
                    + " jumperPhase=" + maceJumperPhase + " recovery=" + maceRecoveryPending);
            if (canAutoTakeoffJumpNow(player)) {
                queueAutoTakeoffJump();
                autoTakeoffGlideDelayTicks = player.onGround() ? 1 : 0;
            }
        }

        if (player.onGround() || player.isInWater() || player.isInLava()) {
            // A jump held true every tick has no new rising edge. Release it
            // between takeoff attempts, including when a target changes.
            if (lastAutoTakeoffJumpAge == Integer.MIN_VALUE
                    || player.tickCount - lastAutoTakeoffJumpAge >= TAKEOFF_JUMP_RETRY_TICKS) {
                queueAutoTakeoffJump();
                autoTakeoffGlideDelayTicks = 1;
                lastAutoTakeoffJumpAge = player.tickCount;
                ElytraDiveTrace.INSTANCE.event("takeoffJump tick=" + player.tickCount);
            }
            firstAutoTakeoffGlideAge = -1;
            ElytraRecastUtil.INSTANCE.reset(this);
            return;
        }

        if (autoTakeoffGlideDelayTicks > 0) return;
        if (player.getDeltaMovement().y > 0.0 && player.fallDistance <= 0.0f) return;
        if (!ElytraRecastUtil.INSTANCE.isActive(this)) {
            ElytraRecastUtil.INSTANCE.request(this, player, ElytraRecastUtil.Mode.GRIM, false, false, false, 0);
        }
        // Keep the recast's release/press edge, but retry vanilla's start
        // command if another movement-input consumer swallowed that edge.
        if (firstAutoTakeoffGlideAge < 0) firstAutoTakeoffGlideAge = player.tickCount;
        else if (player.tickCount - firstAutoTakeoffGlideAge >= 3
                && (lastAutoTakeoffGlidePacketAge == Integer.MIN_VALUE
                    || player.tickCount - lastAutoTakeoffGlidePacketAge >= TAKEOFF_PACKET_RETRY_TICKS)
                && ElytraRecastUtil.INSTANCE.canStartGliding(player, false)
                && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(
                    player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            lastAutoTakeoffGlidePacketAge = player.tickCount;
            ElytraDiveTrace.INSTANCE.event("takeoffGlidePacket tick=" + player.tickCount);
        }
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
        firstAutoTakeoffGlideAge = -1;
        lastAutoTakeoffJumpAge = Integer.MIN_VALUE;
        lastAutoTakeoffGlidePacketAge = Integer.MIN_VALUE;
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
        if (maceJumperPhase == MaceJumperPhase.TURN || maceJumperPhase == MaceJumperPhase.DIVE
                || pursuitPhase == PursuitPhase.DIVE && canSetupMaceDive(player)) return false;

        if (FireworkUseController.INSTANCE.isCooling(FIREWORK_MIN_REBOOST_TICKS)) return false;
        FireworkUseController.BoostState boost = FireworkUseController.INSTANCE.getBoostState(player);
        if (ElytraDiveTrace.INSTANCE.active()) {
            ElytraDiveTrace.INSTANCE.event("boostState active=" + boost.active()
                    + " pending=" + boost.awaitingSpawn() + " guaranteed=" + boost.guaranteedTicks());
        }
        if (boost.awaitingSpawn()) return false;
        // Never launch a new rocket solely because an avoidance route is active:
        // acceleration can make a nearby collision unavoidable.
        if (blockDodgeRotation != null) return false;
        if (maceJumperPhase == MaceJumperPhase.CLIMB
                || maceJumperPhase == MaceJumperPhase.RECOVER) {
            double climbHeight = isMaceJumperEnabled() ? maceJumperHeight.get()
                    : Math.max(6.0, overtakeHeight.get());
            double goalY = (maceJumperPhase == MaceJumperPhase.CLIMB
                    || maceJumperPhase == MaceJumperPhase.RECOVER)
                    && Double.isFinite(maceJumperClimbGoalY)
                    ? maceJumperClimbGoalY
                    : target == null ? player.getY() : target.getBoundingBox().maxY + climbHeight;
            double remainingRise = goalY - player.getY();
            double coastRise = Math.max(0.0, player.getDeltaMovement().y) * 3.0;
            return remainingRise > Math.max(1.5, coastRise + 0.75)
                    && (!boost.active() || boost.guaranteedTicks() <= FIREWORK_END_GUARD_TICKS);
        }
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
        var world = ElytraEscapePlanner.searchWorld(player);
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

    private enum MaceJumperPhase {
        NONE,
        RECOVER,
        CLIMB,
        TURN,
        DIVE
    }

    private enum PursuitPhase {
        NONE,
        CHASE,
        CLIMB,
        OVERPASS,
        DIVE
    }

    private record ReachabilityState(Vec3 playerPosition, Vec3 targetPosition, int tick,
                                     ElytraTargetReachability.Route route) {
    }

    private record CrystalThreat(EndCrystal crystal, Vec3 pos, float damage, double distance) {
    }

}
