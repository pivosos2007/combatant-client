/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.common.impl.TargetFilters;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.FreeLook;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.anglesmooth.AngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SigmoidAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SmartAngleSmooth;
import combatant.client.util.aiming.point.PointInsideBox;
import combatant.client.util.aiming.point.PointTracker;
import combatant.client.util.screen.ClientScreen;
import combatant.client.util.target.TargetingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Real-camera aim assist. Uses the same AngleSmooth processors as KillAura but
 * schedules their discrete 20 TPS step across render frames. Never submits
 * a synthetic rotation request to RotationManager.
 */
@ModuleInfo(
        id = "aimassist",
        displayName = "AimAssist",
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.LEGIT,
        description = "module.aimassist.description")
public final class AimAssist extends Module {
    private static final float DEGREES_PER_MOUSE_UNIT = 0.15f;
    private static final float TICK_SECONDS = 1.0f / 20.0f;
    private static final float MAX_FRAME_SECONDS = 0.1f;
    private static final double NEAREST_EDGE_MARGIN = 0.1;
    private static final float MANUAL_INPUT_EPSILON = 0.01f;
    private static final float ASSIST_RECOVERY_PER_TICK = 0.25f;

    private final NumberValue<Float> range = num("range", 4.5f, 1.0f, 8.0f);
    private final NumberValue<Float> fov = num("fov", 90.0f, 10.0f, 180.0f);
    private final NumberValue<Float> maxTurnSpeed = num("max_turn_speed", 180.0f, 10.0f, 720.0f);
    private final EnumValue<AimPoint> aimPoint = enumMode("aim_point", AimPoint.MULTIPOINT);
    private final BooleanValue vertical = bool("vertical", true);
    private final BooleanValue onlyWhileClicking = bool("only_while_clicking", false);
    private final BooleanValue weaponOnly = bool("weapon_only", false);
    private final BooleanValue pauseOnTarget = bool("pause_on_target", true);
    private final BooleanValue stickyTarget = bool("sticky_target", true);
    private final BooleanValue pauseWhileMining = bool("pause_while_mining", true);
    private final NumberValue<Float> randomization = num("randomization", 0.3f, 0.0f, 1.0f);
    private final NumberValue<Float> pointStickiness = visibleWhen(
            num("point_stickiness", 0.7f, 0.0f, 1.0f), () -> aimPoint.get() == AimPoint.MULTIPOINT);

    private final EnumValue<Smoothing> smoothing = enumMode("rotation_mode", Smoothing.SMART);
    // Shared per-axis bounds, used by all three existing AngleSmooth processors.
    // These values are degrees per *tick*, not degrees per frame.
    private final NumberValue<Float> yawMin = num("yaw_min", 4.0f, 0.1f, 45.0f);
    private final NumberValue<Float> yawMax = num("yaw_max", 8.0f, 0.1f, 45.0f);
    private final NumberValue<Float> pitchMin = num("pitch_min", 2.0f, 0.1f, 45.0f);
    private final NumberValue<Float> pitchMax = num("pitch_max", 5.0f, 0.1f, 45.0f);

    private final NumberValue<Float> sigmoidSteepness = visibleWhen(
            num("sigmoid_steepness", 6.0f, 0.1f, 20.0f), () -> smoothing.get() == Smoothing.SIGMOID);
    private final NumberValue<Float> sigmoidMidpoint = visibleWhen(
            num("sigmoid_midpoint", 0.10f, 0.0f, 1.0f), () -> smoothing.get() == Smoothing.SIGMOID);

    private final NumberValue<Float> smartSnapThreshold = visibleWhen(
            num("smart_snap_threshold", 0.55f, 0.05f, 5.0f), () -> smoothing.get() == Smoothing.SMART);
    private final NumberValue<Float> smartJitterYaw = visibleWhen(
            num("smart_jitter_yaw", 0.25f, 0.0f, 3.0f), () -> smoothing.get() == Smoothing.SMART);
    private final NumberValue<Float> smartJitterPitch = visibleWhen(
            num("smart_jitter_pitch", 0.15f, 0.0f, 3.0f), () -> smoothing.get() == Smoothing.SMART);
    private final BooleanValue smartDecelerate = visibleWhen(
            bool("smart_decelerate", true), () -> smoothing.get() == Smoothing.SMART);
    private final NumberValue<Float> smartDecelerateAngle = visibleWhen(
            num("smart_decelerate_angle", 24.0f, 1.0f, 120.0f),
            () -> smoothing.get() == Smoothing.SMART && smartDecelerate.get());
    private final NumberValue<Float> smartDecelerateMin = visibleWhen(
            num("smart_decelerate_min_factor", 0.23f, 0.05f, 1.0f),
            () -> smoothing.get() == Smoothing.SMART && smartDecelerate.get());

    private final EnumValue<TargetingUtil.TargetPriority> priority = enumCommon(
            "priority", CommonSettingSchemas.COMBAT_PRIORITY,
            TargetingUtil.TargetPriority.ANGLE, TargetingUtil.TargetPriority.values());
    private final BooleanMapValue targets = groupCommon("targets", CommonSettingSchemas.TARGET_FILTERS);

    private final LinearAngleSmooth linearSmooth = new LinearAngleSmooth(yawMin, yawMax, pitchMin, pitchMax);
    private final SigmoidAngleSmooth sigmoidSmooth = new SigmoidAngleSmooth(
            yawMin, yawMax, pitchMin, pitchMax, sigmoidSteepness, sigmoidMidpoint);
    private SmartAngleSmooth smartSmooth = newSmartSmooth();
    private final PointTracker pointTracker = new PointTracker();
    private final Minecraft mc = Minecraft.getInstance();
    private LivingEntity target;
    private float pendingYaw;
    private float pendingPitch;
    private float fractionYaw;
    private float fractionPitch;
    private float remainingSeconds;
    private float speedEnvelope = 1.0f;
    private float speedDestination = 1.0f;
    private int speedRetargetTicks;
    private Smoothing lastSmoothing;
    // MouseHandler supplies these *actual user* deltas, never our player.turn calls.
    private float manualYawSinceTick;
    private float manualPitchSinceTick;
    private float yawAssistWeight = 1.0f;
    private float pitchAssistWeight = 1.0f;

    @Override
    public void onDisable() {
        resetTracking();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        float manualYaw = manualYawSinceTick;
        float manualPitch = manualPitchSinceTick;
        manualYawSinceTick = 0.0f;
        manualPitchSinceTick = 0.0f;
        if (!canAssist(player)) {
            resetMotion();
            target = null;
            pointTracker.reset();
            return;
        }

        LivingEntity candidate = selectTarget();
        if (target != candidate) {
            pointTracker.reset();
            resetMotion();
            resetSmoothing();
        }
        target = candidate;
        if (target == null) {
            resetMotion();
            return;
        }

        Vec3 eye = player.getEyePosition();
        AABB box = target.getBoundingBox();
        if (pauseOnTarget.get() && isLookingAt(player, eye, box, 1.0f)) {
            resetMotion();
            return;
        }

        Vec3 point = resolveAimPoint(player, eye, box, target);
        if (point == null) {
            resetMotion();
            return;
        }
        float[] angles = RotationUtil.getRotations(eye, point);
        Rotation start = new Rotation(player.getYRot(), player.getXRot());
        Rotation desired = new Rotation(angles[0], vertical.get() ? angles[1] : start.pitch());

        Smoothing mode = smoothing.get();
        if (lastSmoothing != mode) {
            resetSmoothing();
            lastSmoothing = mode;
        }
        AngleSmooth processor = switch (mode) {
            case LINEAR -> linearSmooth;
            case SIGMOID -> sigmoidSmooth;
            case SMART -> smartSmooth;
        };
        // Smart retains a virtual rotation for silent/server-controlled requests.
        // Here the camera is owned by the player and frames can leave a planned
        // step partially unapplied. Never subtract an old virtual orientation
        // from the player's new, mouse-modified orientation.
        if (mode == Smoothing.SMART) {
            smartSmooth.rebaseToActualRotation(start);
        }
        // Invoke the shared processor on the game tick, and let the render frames
        // distribute its result. This avoids FPS-dependent Smart/Linear internal state.
        RotationTarget plan = new RotationTarget(
                desired, target, List.of(processor), 0, 0.5f,
                false, MovementCorrection.OFF, null);
        Rotation smooth = plan.towards(start, false);
        float yawDelta = RotationUtil.angleDifference(smooth.yaw(), start.yaw());
        float pitchDelta = vertical.get() ? smooth.pitch() - start.pitch() : 0.0f;

        // Correlated speed variance: the same envelope persists for several ticks,
        // rather than choosing a new random easing factor every frame.
        float variance = randomization.get();
        if (--speedRetargetTicks <= 0) {
            speedDestination = 1.0f + variance * ThreadLocalRandom.current().nextFloat(-0.18f, 0.18f);
            speedRetargetTicks = ThreadLocalRandom.current().nextInt(3, 8);
        }
        speedEnvelope = Mth.lerp(0.20f, speedEnvelope, speedDestination);
        float maxStep = maxTurnSpeed.get() * TICK_SECONDS;
        // A user actively turning away from the suggested correction owns that
        // axis. Re-enter gradually after the input stops instead of pulling the
        // mouse back toward the target in a single frame.
        if (opposesManualInput(manualYaw, yawDelta)) {
            yawAssistWeight = 0.0f;
            pendingYaw = 0.0f;
            fractionYaw = 0.0f;
        } else {
            yawAssistWeight = Math.min(1.0f, yawAssistWeight + ASSIST_RECOVERY_PER_TICK);
            pendingYaw = Mth.clamp(yawDelta * speedEnvelope * yawAssistWeight, -maxStep, maxStep);
        }
        if (opposesManualInput(manualPitch, pitchDelta)) {
            pitchAssistWeight = 0.0f;
            pendingPitch = 0.0f;
            fractionPitch = 0.0f;
        } else {
            pitchAssistWeight = Math.min(1.0f, pitchAssistWeight + ASSIST_RECOVERY_PER_TICK);
            pendingPitch = Mth.clamp(pitchDelta * speedEnvelope * pitchAssistWeight, -maxStep, maxStep);
        }
        remainingSeconds = TICK_SECONDS;
    }

    /**
     * Called for physical mouse input by MouseMixin before vanilla applies it.
     * Does not intercept or modify player input. An already scheduled correction
     * must not continue fighting input in the opposite direction this frame.
     */
    public void onManualMouseTurn(double dx, double dy) {
        if (!isEnabled()) return;
        float yaw = (float) (dx * DEGREES_PER_MOUSE_UNIT);
        float pitch = (float) (dy * DEGREES_PER_MOUSE_UNIT);
        manualYawSinceTick += yaw;
        manualPitchSinceTick += pitch;
        if (opposesManualInput(yaw, pendingYaw)) {
            pendingYaw = 0.0f;
            fractionYaw = 0.0f;
            yawAssistWeight = 0.0f;
        }
        if (opposesManualInput(pitch, pendingPitch)) {
            pendingPitch = 0.0f;
            fractionPitch = 0.0f;
            pitchAssistWeight = 0.0f;
        }
    }

    private static boolean opposesManualInput(float manualDegrees, float correctionDegrees) {
        return Math.abs(manualDegrees) > MANUAL_INPUT_EPSILON
                && Math.abs(correctionDegrees) > MANUAL_INPUT_EPSILON
                && Math.signum(manualDegrees) != Math.signum(correctionDegrees);
    }

    @Override
    public void onFrame(float tickDelta) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (target == null || !canAssist(player) || !target.isAlive() || target.isRemoved()) {
            resetMotion();
            return;
        }
        if (remainingSeconds <= 0.0f) return;
        float dt = Mth.clamp(AnimationUtility.deltaTime(), 0.0f, MAX_FRAME_SECONDS);
        if (dt <= 0.0f) return;
        float portion = Mth.clamp(dt / remainingSeconds, 0.0f, 1.0f);
        float yawSlice = pendingYaw * portion;
        float pitchSlice = pendingPitch * portion;
        pendingYaw -= yawSlice;
        pendingPitch -= pitchSlice;
        remainingSeconds = Math.max(0.0f, remainingSeconds - dt);

        // Preserve sub-GCD contributions across render frames (rounding every
        // frame without residuals would introduce a dead zone at high FPS).
        float yawTurn = quantize(yawSlice + fractionYaw);
        float pitchTurn = quantize(pitchSlice + fractionPitch);
        fractionYaw += yawSlice - yawTurn;
        fractionPitch += pitchSlice - pitchTurn;
        if (yawTurn != 0.0f || pitchTurn != 0.0f) {
            player.turn(yawTurn / DEGREES_PER_MOUSE_UNIT, pitchTurn / DEGREES_PER_MOUSE_UNIT);
        }
    }

    private SmartAngleSmooth newSmartSmooth() {
        return new SmartAngleSmooth(
                yawMin, yawMax, pitchMin, pitchMax, smartSnapThreshold,
                smartJitterYaw, smartJitterPitch, smartDecelerate,
                smartDecelerateAngle, smartDecelerateMin);
    }

    private void resetSmoothing() {
        smartSmooth = newSmartSmooth();
        // FactorAngleSmooth resets its internally selected factors when it has no plan.
        Rotation current = mc.player != null
                ? new Rotation(mc.player.getYRot(), mc.player.getXRot()) : Rotation.ZERO;
        linearSmooth.process(null, current, current);
    }

    private void resetMotion() {
        pendingYaw = 0.0f;
        pendingPitch = 0.0f;
        fractionYaw = 0.0f;
        fractionPitch = 0.0f;
        remainingSeconds = 0.0f;
    }

    private void resetTracking() {
        target = null;
        pointTracker.reset();
        resetMotion();
        resetSmoothing();
        speedEnvelope = 1.0f;
        speedDestination = 1.0f;
        speedRetargetTicks = 0;
        lastSmoothing = null;
        manualYawSinceTick = 0.0f;
        manualPitchSinceTick = 0.0f;
        yawAssistWeight = 1.0f;
        pitchAssistWeight = 1.0f;
    }

    private boolean canAssist(LocalPlayer player) {
        if (player == null || mc.level == null || !player.isAlive()) return false;
        if (ClientScreen.current() != null) return false;
        if (onlyWhileClicking.get() && !mc.options.keyAttack.isDown()) return false;
        if (weaponOnly.get() && !isWeapon(player.getMainHandItem())) return false;
        if (pauseWhileMining.get() && mc.gameMode != null && mc.gameMode.isDestroying()) return false;
        if (isFreecamActive() || isFreeLookActive()) return false;
        return RotationManager.INSTANCE.getActiveRotationTarget() == null;
    }

    private LivingEntity selectTarget() {
        TargetingUtil.TargetingSettings settings = new TargetingUtil.TargetingSettings(
                range.get(), fov.get(), targets.get(TargetFilters.PLAYERS_ONLY),
                targets.get(TargetFilters.IGNORE_FRIENDS), targets.get(TargetFilters.IGNORE_STAFF),
                targets.get(TargetFilters.IGNORE_ENEMIES), targets.get(TargetFilters.IGNORE_NAKED),
                targets.get(TargetFilters.IGNORE_ENTITIES), targets.get(TargetFilters.VISIBLE_ONLY), priority.get());
        List<LivingEntity> candidates = TargetingUtil.findTargets(mc, settings);
        if (candidates.isEmpty()) return null;
        if (stickyTarget.get() && target != null && candidates.contains(target)) return target;
        return candidates.get(0);
    }

    private Vec3 resolveAimPoint(LocalPlayer player, Vec3 eye, AABB box, LivingEntity entity) {
        return switch (aimPoint.get()) {
            case HEAD -> entity.getEyePosition();
            case CHEST -> box.getCenter().add(0.0, box.getYsize() * 0.15, 0.0);
            case NEAREST -> nearestPointToCrosshair(player, eye, box);
            case MULTIPOINT -> findTrackedPoint(eye, entity);
        };
    }

    private Vec3 findTrackedPoint(Vec3 eyes, LivingEntity entity) {
        float variance = randomization.get();
        // The shared tracker owns AABB sampling, selection, continuity and
        // gaussian/motion noise. AimAssist configures its own instance only.
        pointTracker.setOptions(
                false, 0, 0,
                false, 0.0, 0.0,
                variance > 0.0f,
                variance * 0.045, variance * 0.025,
                80, 0.13);
        pointTracker.setTrackingBehavior(pointStickiness.get(), variance, true);

        Rotation camera = new Rotation(mc.player.getYRot(), mc.player.getXRot());
        PointInsideBox point = pointTracker.findPoint(
                eyes, entity, 0, camera, range.get(),
                !targets.get(TargetFilters.VISIBLE_ONLY));
        return point != null ? point.pos() : null;
    }

    private static Vec3 nearestPointToCrosshair(LocalPlayer player, Vec3 eye, AABB box) {
        Vec3 projected = eye.add(player.getLookAngle().scale(eye.distanceTo(box.getCenter())));
        double mx = Math.min(NEAREST_EDGE_MARGIN, box.getXsize() * 0.25);
        double my = Math.min(NEAREST_EDGE_MARGIN, box.getYsize() * 0.25);
        double mz = Math.min(NEAREST_EDGE_MARGIN, box.getZsize() * 0.25);
        return new Vec3(
                Mth.clamp(projected.x, box.minX + mx, box.maxX - mx),
                Mth.clamp(projected.y, box.minY + my, box.maxY - my),
                Mth.clamp(projected.z, box.minZ + mz, box.maxZ - mz));
    }

    private boolean isLookingAt(LocalPlayer player, Vec3 eye, AABB box, float tickDelta) {
        Vec3 end = eye.add(player.getViewVector(tickDelta).scale(range.get()));
        return box.contains(eye) || box.clip(eye, end).isPresent();
    }

    private static float quantize(float degrees) {
        double step = RotationUtil.gcd();
        if (step <= 0.0) return degrees;
        return (float) (Math.round(degrees / step) * step);
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
                || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
    }

    private static boolean isFreecamActive() {
        Freecam freecam = Modules.get(Freecam.class);
        return freecam != null && freecam.isEnabled();
    }

    private static boolean isFreeLookActive() {
        FreeLook freeLook = Modules.get(FreeLook.class);
        return freeLook != null && freeLook.isCameraActive();
    }

    public enum Smoothing { SMART, LINEAR, SIGMOID }
    public enum AimPoint { MULTIPOINT, NEAREST, CHEST, HEAD }
}
