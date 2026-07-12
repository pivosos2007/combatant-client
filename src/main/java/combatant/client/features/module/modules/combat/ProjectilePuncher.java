/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.util.aiming.features.processors.RotationProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.data.RotationWithVector;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.anglesmooth.AngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SigmoidAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SmartAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SpookyAngleSmooth;
import combatant.client.util.aiming.preference.LeastDifferencePreference;
import combatant.client.util.aiming.preference.RotationPreference;
import combatant.client.util.aiming.raytrace.RotationRaytrace;
import combatant.client.util.click.ClickScheduler;
import combatant.client.util.raycast.RaycastUtil;
import combatant.client.util.target.TargetingUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

//todo Description
@ModuleInfo(
        id = "projectilepuncher",
        displayName = "ProjectilePuncher",
        category = ModuleCategory.COMBAT
)
public final class ProjectilePuncher extends Module {

    private static final String PROJECTILE_FIREBALL = "fireball";
    private static final String PROJECTILE_SHULKER_BULLET = "shulker_bullet";
    private static final int ROTATION_PRIORITY = 10;
    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Double> range =
            numCommon(
                    "projectilePuncherRange",
                    "range",
                    CommonSettingSchemas.COMBAT_RANGE,
                    3.0,
                    2.0,
                    6.0
            );
    private final NumberValue<Double> wallRange =
            numCommon(
                    "projectilePuncherWallRange",
                    "wall_range",
                    CommonSettingSchemas.COMBAT_WALL_RANGE,
                    0.0,
                    0.0,
                    6.0
            );
    private final BooleanMapValue projectiles =
            common(
                    group(
                            "projectilePuncherProjectiles",
                            "projectiles",
                            projectileDefaults()
                    ),
                    CommonSettingSchemas.ITEMS_PROJECTILES.commonI18nKey()
            );
    private final BooleanValue ignoreOpenInventory =
            boolCommon(
                    "projectilePuncherIgnoreOpenInventory",
                    "ignore_open_inventory",
                    CommonSettingSchemas.INTERACTION_IGNORE_OPEN_INVENTORY,
                    true
            );
    private final EnumValue<KillAura.RaycastMode> raycast =
            enumCommon(
                    "projectilePuncherRaycast",
                    "raycast",
                    CommonSettingSchemas.COMBAT_RAYCAST,
                    KillAura.RaycastMode.STRICT,
                    KillAura.RaycastMode.values()
            );
    private final EnumValue<KillAura.RotationTiming> rotationTiming =
            enumCommon(
                    "projectilePuncherRotationTiming",
                    "rotation_timing",
                    CommonSettingSchemas.ROTATION_TIMING,
                    KillAura.RotationTiming.NORMAL,
                    KillAura.RotationTiming.values()
            );
    private final EnumValue<MovementCorrection> movementCorrection =
            enumCommon(
                    "projectilePuncherMovementCorrection",
                    "movement_correction",
                    CommonSettingSchemas.ROTATION_MOVEMENT_CORRECTION,
                    MovementCorrection.STRICT,
                    MovementCorrection.values()
            );
    private final EnumValue<KillAura.AngleSmoothMode> rotationMode =
            enumCommon(
                    "projectilePuncherRotationMode",
                    "rotation_mode",
                    CommonSettingSchemas.ROTATION_MODE,
                    KillAura.AngleSmoothMode.SMART,
                    KillAura.AngleSmoothMode.values()
            );
    private final NumberValue<Integer> rotationResetTicks =
            numCommon(
                    "projectilePuncherRotationResetTicks",
                    "rotation_reset_ticks",
                    CommonSettingSchemas.ROTATION_RESET_TICKS,
                    2,
                    1,
                    20
            );
    private final NumberValue<Float> rotationResetThreshold =
            numCommon(
                    "projectilePuncherRotationResetThreshold",
                    "rotation_reset_threshold",
                    CommonSettingSchemas.ROTATION_RESET_THRESHOLD,
                    2.0f,
                    0.1f,
                    15.0f
            );
    private final BooleanValue rotationThroughWalls =
            boolCommon(
                    "projectilePuncherRotationThroughWalls",
                    "rotation_through_walls",
                    CommonSettingSchemas.ROTATION_THROUGH_WALLS,
                    false
            );
    private final NumberValue<Integer> predictTicks =
            num(
                    "projectilePuncherPredictTicks",
                    "predict_ticks",
                    1,
                    0,
                    5
            );
    private final NumberValue<Integer> cpsMin =
            numCommon(
                    "projectilePuncherCpsMin",
                    "cps_min",
                    CommonSettingSchemas.COMBAT_CPS_MIN,
                    20,
                    1,
                    20
            );
    private final NumberValue<Integer> cpsMax =
            numCommon(
                    "projectilePuncherCpsMax",
                    "cps_max",
                    CommonSettingSchemas.COMBAT_CPS_MAX,
                    20,
                    1,
                    20
            );
    private final ClickScheduler clicker = new ClickScheduler(cpsMin.get(), cpsMax.get());
    private final NumberValue<Float> linearHorMin =
            numCommon(
                    "projectilePuncherLinearHorizontalMin",
                    "linear_horizontal_min",
                    CommonSettingSchemas.ANGLESMOOTH_HORIZONTAL_MIN,
                    180f,
                    0f,
                    180f
            );
    private final NumberValue<Float> linearHorMax =
            numCommon(
                    "projectilePuncherLinearHorizontalMax",
                    "linear_horizontal_max",
                    CommonSettingSchemas.ANGLESMOOTH_HORIZONTAL_MAX,
                    180f,
                    0f,
                    180f
            );
    private final NumberValue<Float> linearVertMin =
            numCommon(
                    "projectilePuncherLinearVerticalMin",
                    "linear_vertical_min",
                    CommonSettingSchemas.ANGLESMOOTH_VERTICAL_MIN,
                    180f,
                    0f,
                    180f
            );
    private final NumberValue<Float> linearVertMax =
            numCommon(
                    "projectilePuncherLinearVerticalMax",
                    "linear_vertical_max",
                    CommonSettingSchemas.ANGLESMOOTH_VERTICAL_MAX,
                    180f,
                    0f,
                    180f
            );
    private final NumberValue<Float> sigmoidHorMin =
            numCommon(
                    "projectilePuncherSigmoidHorizontalMin",
                    "sigmoid_horizontal_min",
                    CommonSettingSchemas.ANGLESMOOTH_HORIZONTAL_MIN,
                    180f,
                    0f,
                    180f
            );
    private final NumberValue<Float> sigmoidHorMax =
            numCommon(
                    "projectilePuncherSigmoidHorizontalMax",
                    "sigmoid_horizontal_max",
                    CommonSettingSchemas.ANGLESMOOTH_HORIZONTAL_MAX,
                    180f,
                    0f,
                    180f
            );
    private final NumberValue<Float> sigmoidVertMin =
            numCommon(
                    "projectilePuncherSigmoidVerticalMin",
                    "sigmoid_vertical_min",
                    CommonSettingSchemas.ANGLESMOOTH_VERTICAL_MIN,
                    180f,
                    0f,
                    180f
            );
    private final NumberValue<Float> sigmoidVertMax =
            numCommon(
                    "projectilePuncherSigmoidVerticalMax",
                    "sigmoid_vertical_max",
                    CommonSettingSchemas.ANGLESMOOTH_VERTICAL_MAX,
                    180f,
                    0f,
                    180f
            );
    private final NumberValue<Float> sigmoidSteepness =
            numCommon(
                    "projectilePuncherSigmoidSteepness",
                    "sigmoid_steepness",
                    CommonSettingSchemas.ANGLESMOOTH_STEEPNESS,
                    10f,
                    0f,
                    20f
            );
    private final NumberValue<Float> sigmoidMidpoint =
            numCommon(
                    "projectilePuncherSigmoidMidpoint",
                    "sigmoid_midpoint",
                    CommonSettingSchemas.ANGLESMOOTH_MIDPOINT,
                    0.3f,
                    0f,
                    1f
            );
    private final NumberValue<Float> smartYawMin =
            numCommon(
                    "projectilePuncherSmartYawMin",
                    "smart_yaw_min",
                    CommonSettingSchemas.ANGLESMOOTH_YAW_MIN,
                    70f,
                    1f,
                    180f
            );
    private final NumberValue<Float> smartYawMax =
            numCommon(
                    "projectilePuncherSmartYawMax",
                    "smart_yaw_max",
                    CommonSettingSchemas.ANGLESMOOTH_YAW_MAX,
                    95f,
                    1f,
                    180f
            );
    private final NumberValue<Float> smartPitchMin =
            numCommon(
                    "projectilePuncherSmartPitchMin",
                    "smart_pitch_min",
                    CommonSettingSchemas.ANGLESMOOTH_PITCH_MIN,
                    45f,
                    1f,
                    180f
            );
    private final NumberValue<Float> smartPitchMax =
            numCommon(
                    "projectilePuncherSmartPitchMax",
                    "smart_pitch_max",
                    CommonSettingSchemas.ANGLESMOOTH_PITCH_MAX,
                    70f,
                    1f,
                    180f
            );
    private final NumberValue<Float> smartSnapThreshold =
            numCommon(
                    "projectilePuncherSmartSnapThreshold",
                    "smart_snap_threshold",
                    CommonSettingSchemas.ANGLESMOOTH_SNAP_THRESHOLD,
                    1.2f,
                    0.05f,
                    10.0f
            );
    private final NumberValue<Float> smartJitterYaw =
            numCommon(
                    "projectilePuncherSmartJitterYaw",
                    "smart_jitter_yaw",
                    CommonSettingSchemas.ANGLESMOOTH_JITTER_YAW,
                    0.8f,
                    0.0f,
                    6.0f
            );
    private final NumberValue<Float> smartJitterPitch =
            numCommon(
                    "projectilePuncherSmartJitterPitch",
                    "smart_jitter_pitch",
                    CommonSettingSchemas.ANGLESMOOTH_JITTER_PITCH,
                    0.4f,
                    0.0f,
                    4.0f
            );
    private final BooleanValue smartDecelerateEnabled =
            boolCommon(
                    "projectilePuncherSmartDecelerateEnabled",
                    "smart_decelerate_enabled",
                    CommonSettingSchemas.ANGLESMOOTH_DECELERATE_ENABLED,
                    true
            );
    private final NumberValue<Float> smartDecelerateAngle =
            numCommon(
                    "projectilePuncherSmartDecelerateAngle",
                    "smart_decelerate_angle",
                    CommonSettingSchemas.ANGLESMOOTH_DECELERATE_ANGLE,
                    35.0f,
                    1.0f,
                    120.0f
            );
    private final NumberValue<Float> smartDecelerateMinFactor =
            numCommon(
                    "projectilePuncherSmartDecelerateMinFactor",
                    "smart_decelerate_min_factor",
                    CommonSettingSchemas.ANGLESMOOTH_DECELERATE_MIN_FACTOR,
                    0.22f,
                    0.05f,
                    1.0f
            );
    private final LinearAngleSmooth linearSmooth = new LinearAngleSmooth(
            linearHorMin, linearHorMax, linearVertMin, linearVertMax
    );
    private final SigmoidAngleSmooth sigmoidSmooth = new SigmoidAngleSmooth(
            sigmoidHorMin, sigmoidHorMax, sigmoidVertMin, sigmoidVertMax,
            sigmoidSteepness, sigmoidMidpoint
    );
    private final SmartAngleSmooth smartSmooth = new SmartAngleSmooth(
            smartYawMin, smartYawMax, smartPitchMin, smartPitchMax,
            smartSnapThreshold, smartJitterYaw, smartJitterPitch,
            smartDecelerateEnabled, smartDecelerateAngle, smartDecelerateMinFactor
    );
    private final SpookyAngleSmooth spookySmooth = new SpookyAngleSmooth();
    private Entity currentTarget;
    private Vec3 lastAimPoint;
    private int lastAimEntityId = -1;
    private Rotation lastRotation;
    private int lastRotationTick = -1;
    private int lastAttackExecutionAge = -1;
    private boolean rotationReleased = true;

    private static Map<String, Boolean> projectileDefaults() {
        return Map.of(
                PROJECTILE_FIREBALL, true,
                PROJECTILE_SHULKER_BULLET, true
        );
    }

    private static Vec3 clampToBox(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    @Override
    public void onEnable() {
        clicker.reset();
        currentTarget = null;
        lastRotation = null;
        lastRotationTick = -1;
        lastAttackExecutionAge = -1;
        rotationReleased = true;
    }

    @Override
    public void onDisable() {
        clearTargetImmediate();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            clearTargetImmediate();
        }
    }

    @EventHandler(priority = ROTATION_PRIORITY)
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null) {
            clearTargetImmediate();
            return;
        }

        if (event.getType() == RotationUpdateEvent.Type.POST) {
            tryAttackPost();
            return;
        }

        clicker.setCps(cpsMin.get(), cpsMax.get());
        clicker.tick();

        List<Entity> candidates = findCandidates();
        for (Entity entity : candidates) {
            if (processTarget(entity, range.get().floatValue(), wallRange.get().floatValue())) {
                currentTarget = entity;
                return;
            }
        }

        clearTarget();
    }

    private void tryAttackPost() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.player.tickCount == lastAttackExecutionAge) {
            return;
        }

        Entity target = currentTarget;
        if (target == null || target.isRemoved()) return;
        if (!isInRange(target)) return;

        Rotation rotation = resolveAttackRotation(target);
        if (rotation == null) {
            clearTarget();
            return;
        }
        if (!passesRaycast(target, rotation.yaw(), rotation.pitch())) {
            return;
        }

        int clickCount = clicker.getClicksAt(0);
        if (clickCount <= 0) return;

        lastAttackExecutionAge = mc.player.tickCount;
        for (int i = 0; i < clickCount; i++) {
            if (target.isRemoved()) {
                break;
            }
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private List<Entity> findCandidates() {
        double searchRange = Math.max(range.get(), wallRange.get()) + 8.0;
        List<Entity> candidates = mc.level.getEntities(
                mc.player,
                mc.player.getBoundingBox().inflate(searchRange),
                this::isSupportedProjectile
        );

        candidates.removeIf(entity -> !shouldAttack(entity));
        candidates.sort(Comparator.comparingDouble(this::predictedDistanceSq));
        return candidates;
    }

    private boolean processTarget(Entity target, float range, float wallsRange) {
        RotationWithVector rotation = findRotation(target, range, wallsRange);
        if (rotation == null) return false;

        lastRotation = rotation.rotation();
        lastRotationTick = mc.player != null ? mc.player.tickCount : -1;
        rotationReleased = false;

        KillAura.RotationTiming timing = rotationTiming.get();
        int rotTicks = calculateTicks(rotation.rotation());

        if (timing == KillAura.RotationTiming.SNAP && !clicker.willClickAt(Math.max(1, rotTicks))) {
            return true;
        }

        RotationTarget plan = buildRotationTarget(rotation.rotation(), target);
        RotationManager.INSTANCE.setRotationTarget(plan, ROTATION_PRIORITY, this);
        return true;
    }

    private Rotation resolveAttackRotation(Entity target) {
        if (mc.player == null) return null;

        Rotation rotation;
        KillAura.RotationTiming timing = rotationTiming.get();
        if (timing == KillAura.RotationTiming.ON_TICK) {
            rotation = lastRotationTick == mc.player.tickCount ? lastRotation : null;
            if (rotation == null) {
                RotationWithVector rot = findRotation(target, range.get().floatValue(), wallRange.get().floatValue());
                rotation = rot != null ? rot.rotation() : null;
                if (rotation == null) {
                    return null;
                }
            }
        } else {
            Rotation current = RotationManager.INSTANCE.getCurrentRotation();
            rotation = current != null ? current : new Rotation(mc.player.getYRot(), mc.player.getXRot(), true);
        }

        return rotation.normalize();
    }

    private boolean passesRaycast(Entity target, float yaw, float pitch) {
        KillAura.RaycastMode mode = raycast.get();
        if (mode == KillAura.RaycastMode.NONE) return true;

        double maxRange = range.get();
        if (mode == KillAura.RaycastMode.THROUGH_WALLS) {
            return RaycastUtil.isLookingAtEntity(mc.player, target, yaw, pitch, maxRange, wallRange.get()) != null;
        }

        return RaycastUtil.isLookingAtEntity(mc.player, target, yaw, pitch, maxRange, 0.0) != null;
    }

    private boolean isInRange(Entity target) {
        return TargetingUtil.distanceToEntityBoxSq(mc.player.getEyePosition(), target) <= range.get() * range.get();
    }

    private RotationWithVector findRotation(Entity target, float range, float wallsRange) {
        if (mc.player == null) return null;

        Vec3 eyes = mc.player.getEyePosition();
        AABB targetBox = predictBox(target);
        RotationPreference preference = lastAimEntityId == target.getId() && lastAimPoint != null
                ? LeastDifferencePreference.leastDifferenceToLastPoint(eyes, lastAimPoint)
                : LeastDifferencePreference.LEAST_DISTANCE_TO_CURRENT_ROTATION;

        RotationWithVector rot = RotationRaytrace.raytraceBox(
                eyes,
                targetBox,
                range,
                wallsRange,
                RotationRaytrace.OUTLINE_VISIBILITY,
                preference,
                true
        );

        if (rot == null && rotationThroughWalls.get()) {
            rot = RotationRaytrace.raytraceBox(
                    eyes,
                    targetBox,
                    range,
                    range,
                    RotationRaytrace.OUTLINE_VISIBILITY,
                    preference,
                    true
            );
        }

        if (rot != null) {
            rot = stabilizeRotationResult(target, eyes, targetBox, rot);
            lastAimPoint = rot.vec();
            lastAimEntityId = target.getId();
        }

        return rot;
    }

    private RotationWithVector stabilizeRotationResult(Entity target, Vec3 eyes, AABB targetBox, RotationWithVector rotation) {
        if (target == null || eyes == null || targetBox == null || rotation == null) {
            return rotation;
        }

        Vec3 stablePoint = rotation.vec();
        if (lastAimEntityId == target.getId() && lastAimPoint != null) {
            Vec3 previousPoint = clampToBox(lastAimPoint, targetBox);
            double distance = previousPoint.distanceTo(stablePoint);
            double blend = distance > 0.50 ? 0.55 : distance > 0.20 ? 0.35 : 0.20;
            stablePoint = new Vec3(
                    Mth.lerp(blend, previousPoint.x, stablePoint.x),
                    Mth.lerp(blend, previousPoint.y, stablePoint.y),
                    Mth.lerp(blend, previousPoint.z, stablePoint.z)
            );
            stablePoint = clampToBox(stablePoint, targetBox);
        }

        Rotation stableRotation = Rotation.lookingAt(stablePoint, eyes).normalize();
        return new RotationWithVector(stableRotation, stablePoint);
    }

    private AABB predictBox(Entity entity) {
        int ticks = Math.max(0, predictTicks.get());
        if (ticks <= 0) {
            return entity.getBoundingBox();
        }

        return entity.getBoundingBox().move(entity.getDeltaMovement().scale(ticks));
    }

    private double predictedDistanceSq(Entity entity) {
        return TargetingUtil.distanceToBoxSq(mc.player.getEyePosition(), predictBox(entity));
    }

    private boolean shouldAttack(Entity entity) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-7) {
            return false;
        }

        Vec3 entityPos = entity.position();
        Vec3 vecToPlayer = mc.player.getBoundingBox().getCenter().subtract(entityPos);
        double dot = vecToPlayer.dot(velocity);
        boolean movingTowardsPlayer = dot > 0.5 * velocity.length() * vecToPlayer.length();

        AABB extendedHitbox = mc.player.getBoundingBox().inflate(entity.getBoundingBox().getXsize() * 0.5);
        boolean touchesHitbox = extendedHitbox.clip(entityPos, entityPos.add(velocity.scale(20.0))).isPresent();
        boolean willHitPlayer = !extendedHitbox.contains(entityPos) && touchesHitbox;

        if (!movingTowardsPlayer && !willHitPlayer) {
            return false;
        }

        return predictedDistanceSq(entity) <= range.get() * range.get();
    }

    private boolean isSupportedProjectile(Entity entity) {
        if (entity == null || entity.isRemoved()) return false;
        EntityType<?> type = entity.getType();
        if (type == net.minecraft.world.entity.EntityTypes.FIREBALL) {
            return projectiles.get(PROJECTILE_FIREBALL);
        }
        if (type == net.minecraft.world.entity.EntityTypes.SHULKER_BULLET) {
            return projectiles.get(PROJECTILE_SHULKER_BULLET);
        }
        return false;
    }

    private RotationTarget buildRotationTarget(Rotation rotation, Entity target) {
        List<RotationProcessor> processors = new ArrayList<>();
        AngleSmooth smooth = selectAngleSmooth();
        if (smooth != null) {
            processors.add(smooth);
        }

        return new RotationTarget(
                rotation,
                target,
                processors,
                rotationResetTicks.get(),
                rotationResetThreshold.get(),
                !ignoreOpenInventory.get(),
                movementCorrection.get(),
                null
        );
    }

    private int calculateTicks(Rotation rotation) {
        AngleSmooth smooth = selectAngleSmooth();
        if (smooth == null) return 0;
        Rotation base = RotationManager.INSTANCE.getServerRotation();
        if (base == null) return 0;
        return smooth.calculateTicks(base, rotation);
    }

    private AngleSmooth selectAngleSmooth() {
        return switch (rotationMode.get()) {
            case LINEAR -> linearSmooth;
            case SIGMOID -> sigmoidSmooth;
            case SMART -> smartSmooth;
            case SPOOKY -> spookySmooth;
            case NONE -> null;
        };
    }

    private void clearTarget() {
        currentTarget = null;
        lastAttackExecutionAge = -1;
        clearRotationState(true);
    }

    private void clearTargetImmediate() {
        currentTarget = null;
        lastAttackExecutionAge = -1;
        clearRotationState(false);
    }

    private void clearRotationState(boolean smoothReturn) {
        if (smoothReturn && rotationReleased) {
            return;
        }

        RotationManager.INSTANCE.release(this, smoothReturn);
        lastAimPoint = null;
        lastAimEntityId = -1;
        lastRotation = null;
        lastRotationTick = -1;
        rotationReleased = true;
    }
}
