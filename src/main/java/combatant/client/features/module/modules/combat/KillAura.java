/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.*;
import combatant.client.util.combat.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.common.impl.TargetFilters;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.events.impl.SprintControlEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.TargetStrafe;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.data.RotationWithVector;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.LazyAimProcessor;
import combatant.client.util.aiming.features.processors.RotationProcessor;
import combatant.client.util.aiming.features.processors.ShakeAimProcessor;
import combatant.client.util.aiming.features.processors.ShortStopRotationProcessor;
import combatant.client.util.aiming.features.processors.anglesmooth.AngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SigmoidAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SmartAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SpookyAngleSmooth;
import combatant.client.util.aiming.point.PointInsideBox;
import combatant.client.util.aiming.point.PointTracker;
import combatant.client.util.aiming.preference.LeastDifferencePreference;
import combatant.client.util.aiming.preference.RotationPreference;
import combatant.client.util.aiming.raytrace.RotationRaytrace;
import combatant.client.util.click.AttackPressing;
import combatant.client.util.click.ClickScheduler;
import combatant.client.util.combat.protocol.CombatProtocolHeuristics;
import combatant.client.util.combat.protocol.ProtocolUtil;
import combatant.client.util.raycast.RaycastUtil;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.TargetingUtil;
import combatant.client.util.target.TargetingUtil.TargetingSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

//todo Description
@ModuleInfo(
        id = "killaura",
        displayName = "KillAura",
        category = ModuleCategory.COMBAT
)
public class KillAura extends Module {

    private static final int MAX_CPS = 100;
    private static final String IGNORE_BOTS = "ignore_bots";
    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Double> range =
            numCommon(
                    "killauraRange",
                    "range",
                    CommonSettingSchemas.COMBAT_RANGE,
                    3.2,
                    1.0,
                    6.0
            );
    private final EnumValue<Reach.Mode> reachMode =
            enumCommon(
                    "killauraReachMode",
                    "reach_mode",
                    CommonSettingSchemas.COMBAT_REACH_MODE,
                    Reach.Mode.NORMAL,
                    Reach.Mode.values()
            );
    private final NumberValue<Float> fov =
            numCommon(
                    "killauraFov",
                    "fov",
                    CommonSettingSchemas.COMBAT_FOV,
                    180f,
                    30f,
                    180f
            );
    private final EnumValue<TargetingUtil.TargetPriority> priority =
            enumCommon(
                    "killauraPriority",
                    "priority",
                    CommonSettingSchemas.COMBAT_PRIORITY,
                    TargetingUtil.TargetPriority.DISTANCE,
                    TargetingUtil.TargetPriority.values()
            );
    private final EnumValue<RaycastMode> raycast =
            enumCommon(
                    "killauraRaycast",
                    "raycast",
                    CommonSettingSchemas.COMBAT_RAYCAST,
                    RaycastMode.STRICT,
                    RaycastMode.values()
            );
    private final ModeValue attackMode = modeCommon(
            "killauraAttackMode",
            "attack_mode",
            CommonSettingSchemas.COMBAT_ATTACK_MODE,
            CombatRotationModeUtil.MODE_ROTATIONS,
            CombatRotationModeUtil.MODE_ROTATIONS,
            CombatRotationModeUtil.MODE_NO_ROTATIONS
    );
    private final NumberValue<Double> acquireRangeIncrement =
            visibleWhen(numCommon(
                    "killauraAcquireRangeIncrement",
                    "range_increment",
                    CommonSettingSchemas.COMBAT_RANGE_INCREMENT,
                    1.5,
                    0.0,
                    3.0
            ), this::usesRotationSettings);
    private final NumberValue<Double> wallRange =
            visibleWhen(numCommon(
                    "killauraWallRange",
                    "wall_range",
                    CommonSettingSchemas.COMBAT_WALL_RANGE,
                    2.5,
                    0.0,
                    6.0
            ), this::usesRotationSettings);
    private final NumberValue<Integer> cpsMin =
            numCommon(
                    "killauraCpsMin",
                    "cps_min",
                    CommonSettingSchemas.COMBAT_CPS_MIN,
                    8,
                    1,
                    MAX_CPS
            );
    private final NumberValue<Integer> cpsMax =
            numCommon(
                    "killauraCpsMax",
                    "cps_max",
                    CommonSettingSchemas.COMBAT_CPS_MAX,
                    12,
                    1,
                    MAX_CPS
            );
    private final BooleanValue breakShields =
            boolCommon(
                    "killauraBreakShields",
                    "break_shields",
                    CommonSettingSchemas.COMBAT_BREAK_SHIELDS,
                    true
            );
    private final EnumValue<RotationTiming> rotationTiming =
            visibleWhen(enumCommon(
                    "killauraRotationTiming",
                    "rotation_timing",
                    CommonSettingSchemas.ROTATION_TIMING,
                    RotationTiming.NORMAL,
                    RotationTiming.values()
            ), this::usesRotationSettings);
    private final EnumValue<CorrectionType> correctionType =
            visibleWhen(enumCommon(
                    "killauraCorrectionType",
                    "correction_type",
                    CommonSettingSchemas.COMBAT_CORRECTION_TYPE,
                    CorrectionType.FOCUSED,
                    CorrectionType.values()
            ), this::usesRotationSettings);
    private final EnumValue<ProtocolMode> protocolMode =
            enumCommon(
                    "killauraProtocolMode",
                    "protocol_mode",
                    CommonSettingSchemas.COMBAT_PROTOCOL,
                    ProtocolMode.AUTO,
                    ProtocolMode.values()
            );
    private final BooleanMapValue protocolHeuristicSources =
            visibleWhen(protocolHeuristics(
                    "killauraProtocolHeuristics",
                    "protocol_heuristics",
                    CommonSettingSchemas.COMBAT_PROTOCOL_HEURISTICS
            ), () -> protocolMode.get() == ProtocolMode.AUTO);
    private final EnumValue<SprintResetMode> sprintResetMode =
            enumCommon(
                    "killauraSprintResetMode",
                    "sprint_reset_mode",
                    CommonSettingSchemas.COMBAT_SPRINT_RESET,
                    SprintResetMode.DEFAULT,
                    SprintResetMode.values()
            );
    private final EnumValue<Criticals.SelectionMode> criticalsSelection =
            visibleWhen(enumCommon(
                    "killauraCriticals",
                    "criticals",
                    CommonSettingSchemas.COMBAT_CRITICALS,
                    Criticals.SelectionMode.SMART,
                    Criticals.SelectionMode.values()
            ), () -> !isLegacyAttackProtocol());
    private final EnumValue<AngleSmoothMode> rotationMode =
            visibleWhen(enumCommon(
                    "killauraRotationMode",
                    "rotation_mode",
                    CommonSettingSchemas.ROTATION_MODE,
                    AngleSmoothMode.SMART,
                    AngleSmoothMode.values()
            ), this::usesRotationSettings);
    // Linear smoothing
    private final NumberValue<Float> linearHorMin =
            visibleWhen(numCommon(
                    "linearHorizontalMin",
                    "linear_horizontal_min",
                    CommonSettingSchemas.ANGLESMOOTH_HORIZONTAL_MIN,
                    180f,
                    0f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.LINEAR);
    private final NumberValue<Float> linearHorMax =
            visibleWhen(numCommon(
                    "linearHorizontalMax",
                    "linear_horizontal_max",
                    CommonSettingSchemas.ANGLESMOOTH_HORIZONTAL_MAX,
                    180f,
                    0f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.LINEAR);
    private final NumberValue<Float> linearVertMin =
            visibleWhen(numCommon(
                    "linearVerticalMin",
                    "linear_vertical_min",
                    CommonSettingSchemas.ANGLESMOOTH_VERTICAL_MIN,
                    180f,
                    0f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.LINEAR);
    private final NumberValue<Float> linearVertMax =
            visibleWhen(numCommon(
                    "linearVerticalMax",
                    "linear_vertical_max",
                    CommonSettingSchemas.ANGLESMOOTH_VERTICAL_MAX,
                    180f,
                    0f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.LINEAR);
    private final LinearAngleSmooth linearSmooth = new LinearAngleSmooth(
            linearHorMin, linearHorMax, linearVertMin, linearVertMax
    );
    // Sigmoid smoothing
    private final NumberValue<Float> sigmoidHorMin =
            visibleWhen(numCommon(
                    "sigmoidHorizontalMin",
                    "sigmoid_horizontal_min",
                    CommonSettingSchemas.ANGLESMOOTH_HORIZONTAL_MIN,
                    180f,
                    0f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SIGMOID);
    private final NumberValue<Float> sigmoidHorMax =
            visibleWhen(numCommon(
                    "sigmoidHorizontalMax",
                    "sigmoid_horizontal_max",
                    CommonSettingSchemas.ANGLESMOOTH_HORIZONTAL_MAX,
                    180f,
                    0f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SIGMOID);
    private final NumberValue<Float> sigmoidVertMin =
            visibleWhen(numCommon(
                    "sigmoidVerticalMin",
                    "sigmoid_vertical_min",
                    CommonSettingSchemas.ANGLESMOOTH_VERTICAL_MIN,
                    180f,
                    0f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SIGMOID);
    private final NumberValue<Float> sigmoidVertMax =
            visibleWhen(numCommon(
                    "sigmoidVerticalMax",
                    "sigmoid_vertical_max",
                    CommonSettingSchemas.ANGLESMOOTH_VERTICAL_MAX,
                    180f,
                    0f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SIGMOID);
    private final NumberValue<Float> sigmoidSteepness =
            visibleWhen(numCommon(
                    "sigmoidSteepness",
                    "sigmoid_steepness",
                    CommonSettingSchemas.ANGLESMOOTH_STEEPNESS,
                    10f,
                    0f,
                    20f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SIGMOID);
    private final NumberValue<Float> sigmoidMidpoint =
            visibleWhen(numCommon(
                    "sigmoidMidpoint",
                    "sigmoid_midpoint",
                    CommonSettingSchemas.ANGLESMOOTH_MIDPOINT,
                    0.3f,
                    0f,
                    1f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SIGMOID);
    private final SigmoidAngleSmooth sigmoidSmooth = new SigmoidAngleSmooth(
            sigmoidHorMin, sigmoidHorMax, sigmoidVertMin, sigmoidVertMax,
            sigmoidSteepness, sigmoidMidpoint
    );
    // Smart smoothing
    private final NumberValue<Float> smartYawMin =
            visibleWhen(numCommon(
                    "smartYawMin",
                    "smart_yaw_min",
                    CommonSettingSchemas.ANGLESMOOTH_YAW_MIN,
                    70f,
                    1f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SMART);
    private final NumberValue<Float> smartYawMax =
            visibleWhen(numCommon(
                    "smartYawMax",
                    "smart_yaw_max",
                    CommonSettingSchemas.ANGLESMOOTH_YAW_MAX,
                    95f,
                    1f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SMART);
    private final NumberValue<Float> smartPitchMin =
            visibleWhen(numCommon(
                    "smartPitchMin",
                    "smart_pitch_min",
                    CommonSettingSchemas.ANGLESMOOTH_PITCH_MIN,
                    45f,
                    1f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SMART);
    private final NumberValue<Float> smartPitchMax =
            visibleWhen(numCommon(
                    "smartPitchMax",
                    "smart_pitch_max",
                    CommonSettingSchemas.ANGLESMOOTH_PITCH_MAX,
                    70f,
                    1f,
                    180f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SMART);
    private final NumberValue<Float> smartSnapThreshold =
            visibleWhen(numCommon(
                    "smartSnapThreshold",
                    "smart_snap_threshold",
                    CommonSettingSchemas.ANGLESMOOTH_SNAP_THRESHOLD,
                    1.2f,
                    0.05f,
                    10.0f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SMART);
    private final NumberValue<Float> smartJitterYaw =
            visibleWhen(numCommon(
                    "smartJitterYaw",
                    "smart_jitter_yaw",
                    CommonSettingSchemas.ANGLESMOOTH_JITTER_YAW,
                    0.8f,
                    0.0f,
                    6.0f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SMART);
    private final NumberValue<Float> smartJitterPitch =
            visibleWhen(numCommon(
                    "smartJitterPitch",
                    "smart_jitter_pitch",
                    CommonSettingSchemas.ANGLESMOOTH_JITTER_PITCH,
                    0.4f,
                    0.0f,
                    4.0f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SMART);
    private final BooleanValue smartDecelerateEnabled =
            visibleWhen(boolCommon(
                    "smartDecelerateEnabled",
                    "smart_decelerate_enabled",
                    CommonSettingSchemas.ANGLESMOOTH_DECELERATE_ENABLED,
                    true
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SMART);
    private final NumberValue<Float> smartDecelerateAngle =
            visibleWhen(numCommon(
                    "smartDecelerateAngle",
                    "smart_decelerate_angle",
                    CommonSettingSchemas.ANGLESMOOTH_DECELERATE_ANGLE,
                    35.0f,
                    1.0f,
                    120.0f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SMART && smartDecelerateEnabled.get());
    private final NumberValue<Float> smartDecelerateMinFactor =
            visibleWhen(numCommon(
                    "smartDecelerateMinFactor",
                    "smart_decelerate_min_factor",
                    CommonSettingSchemas.ANGLESMOOTH_DECELERATE_MIN_FACTOR,
                    0.22f,
                    0.05f,
                    1.0f
            ), () -> usesRotationSettings() && rotationMode.get() == AngleSmoothMode.SMART && smartDecelerateEnabled.get());
    private final SmartAngleSmooth smartSmooth = new SmartAngleSmooth(
            smartYawMin, smartYawMax, smartPitchMin, smartPitchMax,
            smartSnapThreshold, smartJitterYaw, smartJitterPitch,
            smartDecelerateEnabled, smartDecelerateAngle, smartDecelerateMinFactor
    );
    private final NumberValue<Integer> rotationResetTicks =
            visibleWhen(numCommon(
                    "killauraRotationResetTicks",
                    "rotation_reset_ticks",
                    CommonSettingSchemas.ROTATION_RESET_TICKS,
                    2,
                    1,
                    20
            ), this::usesRotationSettings);
    private final NumberValue<Float> rotationResetThreshold =
            visibleWhen(numCommon(
                    "killauraRotationResetThreshold",
                    "rotation_reset_threshold",
                    CommonSettingSchemas.ROTATION_RESET_THRESHOLD,
                    2.0f,
                    0.1f,
                    15.0f
            ), this::usesRotationSettings);
    private final BooleanValue rotationThroughWalls =
            visibleWhen(boolCommon(
                    "killauraRotationThroughWalls",
                    "rotation_through_walls",
                    CommonSettingSchemas.ROTATION_THROUGH_WALLS,
                    false
            ), this::usesRotationSettings);
    private final ClickScheduler clicker = new ClickScheduler(cpsMin.get(), cpsMax.get());
    private final PointTracker pointTracker = new PointTracker();
    // Optional aim processors
    private final BooleanValue lazyAimEnabled =
            visibleWhen(boolCommon(
                    "lazyAimEnabled",
                    "lazy_aim_enabled",
                    CommonSettingSchemas.LAZY_AIM_ENABLED,
                    false
            ), this::usesRotationSettings);
    private final NumberValue<Float> lazyAimThreshold =
            visibleWhen(numCommon(
                    "lazyAimThreshold",
                    "lazy_aim_threshold",
                    CommonSettingSchemas.LAZY_AIM_THRESHOLD,
                    1.8f,
                    0.1f,
                    10.0f
            ), () -> usesRotationSettings() && lazyAimEnabled.get());
    private final NumberValue<Float> lazyAimFollowFactor =
            visibleWhen(numCommon(
                    "lazyAimFollowFactor",
                    "lazy_aim_follow_factor",
                    CommonSettingSchemas.LAZY_AIM_FOLLOW_FACTOR,
                    0.65f,
                    0.1f,
                    2.0f
            ), () -> usesRotationSettings() && lazyAimEnabled.get());
    private final LazyAimProcessor lazyAimProcessor = new LazyAimProcessor(lazyAimThreshold, lazyAimFollowFactor);
    private final BooleanValue shakeAimEnabled =
            visibleWhen(boolCommon(
                    "shakeAimEnabled",
                    "shake_aim_enabled",
                    CommonSettingSchemas.SHAKE_AIM_ENABLED,
                    false
            ), this::usesRotationSettings);
    private final NumberValue<Float> shakeAimYawAmplitude =
            visibleWhen(numCommon(
                    "shakeAimYawAmplitude",
                    "shake_aim_yaw_amplitude",
                    CommonSettingSchemas.SHAKE_AIM_YAW_AMPLITUDE,
                    0.6f,
                    0.0f,
                    5.0f
            ), () -> usesRotationSettings() && shakeAimEnabled.get());
    private final NumberValue<Float> shakeAimPitchAmplitude =
            visibleWhen(numCommon(
                    "shakeAimPitchAmplitude",
                    "shake_aim_pitch_amplitude",
                    CommonSettingSchemas.SHAKE_AIM_PITCH_AMPLITUDE,
                    0.3f,
                    0.0f,
                    3.0f
            ), () -> usesRotationSettings() && shakeAimEnabled.get());
    private final NumberValue<Float> shakeAimSpeed =
            visibleWhen(numCommon(
                    "shakeAimSpeed",
                    "shake_aim_speed",
                    CommonSettingSchemas.SHAKE_AIM_SPEED,
                    0.25f,
                    0.01f,
                    2.0f
            ), () -> usesRotationSettings() && shakeAimEnabled.get());
    private final ShakeAimProcessor shakeAimProcessor = new ShakeAimProcessor(
            shakeAimYawAmplitude, shakeAimPitchAmplitude, shakeAimSpeed
    );
    // ShortStop
    private final BooleanValue shortStopEnabled =
            visibleWhen(boolCommon(
                    "shortStopEnabled",
                    "short_stop_enabled",
                    CommonSettingSchemas.SHORT_STOP_ENABLED,
                    false
            ), this::usesRotationSettings);
    private final NumberValue<Integer> shortStopRate =
            visibleWhen(numCommon(
                    "shortStopRate",
                    "short_stop_rate",
                    CommonSettingSchemas.SHORT_STOP_RATE,
                    3,
                    1,
                    25
            ), () -> usesRotationSettings() && shortStopEnabled.get());
    private final NumberValue<Integer> shortStopDurMin =
            visibleWhen(numCommon(
                    "shortStopDurationMin",
                    "short_stop_duration_min",
                    CommonSettingSchemas.SHORT_STOP_DURATION_MIN,
                    1,
                    1,
                    5
            ), () -> usesRotationSettings() && shortStopEnabled.get());
    private final NumberValue<Integer> shortStopDurMax =
            visibleWhen(numCommon(
                    "shortStopDurationMax",
                    "short_stop_duration_max",
                    CommonSettingSchemas.SHORT_STOP_DURATION_MAX,
                    2,
                    1,
                    5
            ), () -> usesRotationSettings() && shortStopEnabled.get());
    private final ShortStopRotationProcessor shortStopProcessor = new ShortStopRotationProcessor(
            shortStopEnabled, shortStopRate, shortStopDurMin, shortStopDurMax
    );
    private final BooleanMapValue toggles = common(
            group("killaura_toggles", "toggles", targetFilterDefaults()),
            CommonSettingSchemas.TARGET_FILTERS.commonI18nKey()
    );
    private final SpookyAngleSmooth spookySmooth = new SpookyAngleSmooth();
    private LivingEntity currentTarget;
    private Vec3 lastAimPoint;
    private int lastAimEntityId = -1;
    private Rotation lastRotation;
    private int lastRotationTick = -1;
    private int lastAttackExecutionAge = -1;

    private static LinkedHashMap<String, Boolean> targetFilterDefaults() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(TargetFilters.PLAYERS_ONLY, false);
        defaults.put(TargetFilters.IGNORE_FRIENDS, true);
        defaults.put(TargetFilters.IGNORE_STAFF, true);
        defaults.put(IGNORE_BOTS, true);
        defaults.put(TargetFilters.IGNORE_ENEMIES, false);
        defaults.put(TargetFilters.IGNORE_NAKED, false);
        defaults.put(TargetFilters.IGNORE_ENTITIES, true);
        return defaults;
    }

    private boolean usesRotationSettings() {
        return CombatRotationModeUtil.usesRotations(attackMode);
    }

    @Override
    public void onEnable() {
        clicker.reset();
        currentTarget = null;
        lastRotation = null;
        lastRotationTick = -1;
        SprintController.INSTANCE.clearSprintBlock();
    }

    @Override
    public void onDisable() {
        currentTarget = null;
        TargetManager.setPredictionTarget(null);
        TargetManager.setModuleTarget(null);
        AttributeSwap.clearAuraControlIfActive();
        clearRotationState(false);
        lastAttackExecutionAge = -1;
        SprintController.INSTANCE.clearSprintBlock();
    }

    @EventHandler(priority = 10)
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null) {
            clearTargetImmediate();
            return;
        }

        switch (event.getType()) {
            case PRE -> {
                if (isLegacyAttackProtocol()) {
                    clicker.setCps(cpsMin.get(), cpsMax.get());
                    clicker.tick();
                }

                TargetingSettings settings = new TargetingSettings(
                        getAcquireRange(),
                        fov.get(),
                        toggles.get(TargetFilters.PLAYERS_ONLY),
                        toggles.get(TargetFilters.IGNORE_FRIENDS),
                        toggles.get(TargetFilters.IGNORE_STAFF),
                        toggles.get(TargetFilters.IGNORE_ENEMIES),
                        toggles.get(TargetFilters.IGNORE_NAKED),
                        toggles.get(TargetFilters.IGNORE_ENTITIES),
                        false,
                        priority.get()
                );

                List<LivingEntity> targets = filterDynamicReachTargets(filterAntiBotTargets(TargetingUtil.findTargets(mc, settings)));
                syncPredictionTarget(targets);
                boolean currentTargetCandidate = isCurrentTargetCandidate(targets);
                if (CombatRotationModeUtil.isNoRotations(attackMode)) {
                    clearRotationState(true);
                    if (currentTargetCandidate && processLockedTargetNoRotations()) {
                        return;
                    }
                    for (LivingEntity target : targets) {
                        if (processTargetNoRotations(target)) {
                            setActiveTarget(target);
                            return;
                        }
                    }

                    clearTargetRuntime();
                    return;
                }

                if (currentTargetCandidate && processLockedTarget()) {
                    return;
                }
                for (LivingEntity target : targets) {
                    if (processTarget(target)) {
                        setActiveTarget(target);
                        return;
                    }
                }

                clearTargetRuntime();
            }
            case POST -> tryAttackPost();
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null) {
            clearTargetImmediate();
            return;
        }

        syncModuleTargetState();
    }

    private void tryAttackPost() {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.tickCount == lastAttackExecutionAge) return;

        LivingEntity target = currentTarget;
        if (target == null) return;

        syncModuleTargetState();

        if (!isInRange(target)) return;
        boolean legacyProtocol = isLegacyAttackProtocol();

        if (CombatRotationModeUtil.usesRotations(attackMode)) {
            Rotation rotation = resolveAttackRotation(target);
            if (rotation == null) return;
            if (!passesRaycast(target, rotation.yaw(), rotation.pitch())) return;
        }

        int clickCount = CombatStrikeController.INSTANCE.resolveClickCount(clicker, legacyProtocol);
        if (clickCount <= 0) return;

        CombatStrikeController.SprintResetMode resetMode = resolveStrikeResetMode();

        boolean attacked = CombatStrikeController.INSTANCE.tryAttack(
                mc,
                target,
                legacyProtocol,
                clickCount,
                resetMode,
                true,
                () -> criticalHitAllowed(target)
        );

        if (!attacked) {
            return;
        }

        lastAttackExecutionAge = mc.player.tickCount;

        AttributeSwap.tryBreakShieldPostAttack(
                target,
                true,
                breakShields.get()
        );
    }

    public boolean willAttackInTicks(int ticks) {
        if (!isEnabled()) {
            return false;
        }

        int safeTicks = Math.max(0, ticks);
        if (isLegacyAttackProtocol()) {
            return clicker.willClickAt(safeTicks);
        }

        return AttackPressing.INSTANCE.willCooldownComplete(mc.player, currentTarget, false, safeTicks);
    }

    public ClickScheduler getClickScheduler() {
        return clicker;
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    public boolean isNoRotationMode() {
        return isEnabled() && CombatRotationModeUtil.isNoRotations(attackMode);
    }

    public List<LivingEntity> findElytraTargetCandidates(double range, float fov) {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return List.of();
        }

        double safeRange = Math.max(1.0, range);
        float safeFov = Math.max(1.0f, Math.min(180.0f, fov));
        TargetingSettings settings = new TargetingSettings(
                safeRange,
                safeFov,
                toggles.get(TargetFilters.PLAYERS_ONLY),
                toggles.get(TargetFilters.IGNORE_FRIENDS),
                toggles.get(TargetFilters.IGNORE_STAFF),
                toggles.get(TargetFilters.IGNORE_ENEMIES),
                toggles.get(TargetFilters.IGNORE_NAKED),
                toggles.get(TargetFilters.IGNORE_ENTITIES),
                false,
                priority.get()
        );

        return filterAntiBotTargets(TargetingUtil.findTargets(mc, settings));
    }

    @EventHandler
    private void onSprintControl(SprintControlEvent event) {
        if (!isEnabled()) return;
        if (event.getSource() != SprintControlEvent.Source.MOVEMENT_TICK
                && event.getSource() != SprintControlEvent.Source.INPUT) {
            return;
        }

        if (shouldBlockSprinting(currentTarget)) {
            event.setSprint(false);
        }
    }

    private void clearTargetImmediate() {
        currentTarget = null;
        TargetManager.setPredictionTarget(null);
        TargetManager.setModuleTarget(null);
        AttributeSwap.clearAuraControlIfActive();
        SprintController.INSTANCE.clearSprintBlock();
        clearRotationState(false);
        lastAttackExecutionAge = -1;
    }

    private void clearTargetRuntime() {
        currentTarget = null;
        TargetManager.setPredictionTarget(null);
        TargetManager.setModuleTarget(null);
        AttributeSwap.handleAuraTarget(null, false);
        SprintController.INSTANCE.clearSprintBlock();
        lastAttackExecutionAge = -1;
        clearRotationState(true);
    }

    private boolean isCurrentTargetCandidate(List<LivingEntity> targets) {
        LivingEntity target = currentTarget;
        if (target == null || targets == null || targets.isEmpty()) {
            return false;
        }
        int targetId = target.getId();
        for (LivingEntity candidate : targets) {
            if (candidate != null && candidate.getId() == targetId) {
                return true;
            }
        }
        return false;
    }

    private void syncModuleTargetState() {
        LivingEntity target = currentTarget;
        if (target == null || !target.isAlive() || target.isRemoved()) {
            if (target != null) {
                clearTargetRuntime();
            } else {
                TargetManager.setModuleTarget(null);
            }
            return;
        }

        TargetManager.setModuleTarget(target);
    }

    private void syncPredictionTarget(List<LivingEntity> targets) {
        if (targets == null || targets.isEmpty()) {
            TargetManager.setPredictionTarget(null);
            return;
        }

        TargetManager.setPredictionTarget(targets.getFirst());
    }

    private List<LivingEntity> filterDynamicReachTargets(List<LivingEntity> targets) {
        if (!usesDynamicReach() || targets == null || targets.isEmpty() || mc.player == null) {
            return targets;
        }

        Vec3 eyePos = mc.player.getEyePosition();
        List<LivingEntity> filtered = new ArrayList<>(targets.size());
        for (LivingEntity target : targets) {
            double targetRange = getEffectiveAcquireRange(target);
            double distSq = TargetingUtil.distanceToEntityBoxSq(eyePos, target);
            if (distSq <= targetRange * targetRange) {
                filtered.add(target);
            }
        }
        return filtered;
    }

    private List<LivingEntity> filterAntiBotTargets(List<LivingEntity> targets) {
        if (!toggles.get(IGNORE_BOTS) || targets == null || targets.isEmpty()) {
            return targets;
        }

        List<LivingEntity> filtered = new ArrayList<>(targets.size());
        for (LivingEntity target : targets) {
            if (target instanceof Player player && AntiBotTracker.INSTANCE.isBot(player)) {
                continue;
            }
            filtered.add(target);
        }
        return filtered;
    }

    private void clearRotationState(boolean smoothReturn) {
        RotationManager.INSTANCE.release(this, smoothReturn);
        pointTracker.reset();
        lastAimPoint = null;
        lastAimEntityId = -1;
        lastRotation = null;
        lastRotationTick = -1;
    }

    private void setActiveTarget(LivingEntity target) {
        currentTarget = target;
        TargetManager.setPredictionTarget(null);
        TargetManager.setModuleTarget(target);
        AttributeSwap.handleAuraTarget(target, breakShields.get());
    }

    private boolean processLockedTargetNoRotations() {
        LivingEntity target = currentTarget;
        if (mc.player == null || target == null) return false;
        if (!target.isAlive() || target.isRemoved()) return false;
        double lockRange = getEffectiveAcquireRange(target);
        double distSq = TargetingUtil.distanceToEntityBoxSq(mc.player.getEyePosition(), target);
        if (distSq > lockRange * lockRange) return false;
        if (!processTargetNoRotations(target)) return false;

        setActiveTarget(target);
        return true;
    }

    private boolean processLockedTarget() {
        LivingEntity target = currentTarget;
        if (mc.player == null || target == null) return false;
        if (!target.isAlive() || target.isRemoved()) return false;
        double lockRange = getEffectiveAcquireRange(target);
        double distSq = TargetingUtil.distanceToEntityBoxSq(mc.player.getEyePosition(), target);
        if (distSq > lockRange * lockRange) return false;
        if (processTarget(target)) {
            setActiveTarget(target);
            return true;
        }
        return false;
    }

    private Rotation resolveAttackRotation(LivingEntity target) {
        if (mc.player == null) return null;

        Rotation rotation;
        RotationTiming timing = rotationTiming.get();
        if (timing == RotationTiming.ON_TICK) {
            rotation = lastRotationTick == mc.player.tickCount ? lastRotation : null;
            if (rotation == null) {
                RotationWithVector rot = findAttackRotation(target);
                if (rot == null) {
                    ElytraTarget elytraTarget = Modules.get(ElytraTarget.class);
                    if (elytraTarget != null && elytraTarget.canIgnoreKillAuraRotations()) {
                        Rotation current = RotationManager.INSTANCE.getCurrentRotation();
                        rotation = current != null ? current : new Rotation(mc.player.getYRot(), mc.player.getXRot(), true);
                    } else {
                        return null;
                    }
                } else {
                    rotation = rot.rotation();
                }
            }
        } else {
            Rotation current = RotationManager.INSTANCE.getCurrentRotation();
            if (current != null) {
                rotation = current;
            } else if (lastRotation != null && lastAimEntityId == target.getId()) {
                rotation = lastRotation;
            } else {
                RotationWithVector rot = findAttackRotation(target);
                if (rot == null) {
                    ElytraTarget elytraTarget = Modules.get(ElytraTarget.class);
                    if (elytraTarget != null && elytraTarget.canIgnoreKillAuraRotations()) {
                        Rotation server = RotationManager.INSTANCE.getServerRotation();
                        rotation = server;
                    } else {
                        return null;
                    }
                } else {
                    lastRotation = rot.rotation();
                    lastRotationTick = mc.player.tickCount;
                    rotation = rot.rotation();
                }
            }
        }

        return rotation != null ? rotation.normalize() : null;
    }

    private boolean processTarget(LivingEntity target) {
        double targetRange = getEffectiveAcquireRange(target);
        double targetWallRange = getEffectiveWallRange(target, targetRange);
        RotationWithVector rotation = findRotation(target, (float) targetRange, (float) targetWallRange);
        if (rotation == null) return false;

        lastRotation = rotation.rotation();
        lastRotationTick = mc.player != null ? mc.player.tickCount : -1;

        RotationTarget plan = buildRotationTarget(rotation.rotation(), target);
        RotationManager.INSTANCE.setRotationTarget(plan, 10, this);
        return true;
    }

    private RotationWithVector findAttackRotation(LivingEntity target) {
        double targetRange = getEffectiveAttackRange(target);
        double targetWallRange = getEffectiveWallRange(target, targetRange);
        return findRotation(target, (float) targetRange, (float) targetWallRange);
    }

    private boolean processTargetNoRotations(LivingEntity target) {
        double attackRange = getEffectiveAttackRange(target);
        boolean requireLineOfSight = raycast.get() == RaycastMode.STRICT;
        return CombatRotationModeUtil.canAttackWithoutRotations(mc, target, attackRange, requireLineOfSight);
    }

    private boolean isLegacyAttackProtocol() {
        return switch (protocolMode.get()) {
            case AUTO -> ProtocolUtil.isLegacyAttackProtocol(protocolHeuristicSources.getAll());
            case FORCE_1_8 -> true;
            case FORCE_1_9 -> false;
        };
    }

    private boolean isInRange(Entity target) {
        double distSq = TargetingUtil.distanceToEntityBoxSq(mc.player.getEyePosition(), target);
        double targetRange = getEffectiveAttackRange(target);
        return distSq <= targetRange * targetRange;
    }

    private boolean passesRaycast(Entity target, float yaw, float pitch) {
        ElytraTarget elytraTarget = Modules.get(ElytraTarget.class);
        if (elytraTarget != null && elytraTarget.canIgnoreKillAuraRotations()) {
            return true;
        }

        RaycastMode mode = raycast.get();
        if (mode == RaycastMode.NONE) return true;

        if (mc.player == null || target == null) {
            return false;
        }

        double attackRange = Math.max(0.0, getEffectiveAttackRange(target));
        double throughWallsRange = mode == RaycastMode.THROUGH_WALLS
                ? getEffectiveWallRange(target, attackRange)
                : 0.0;

        return RaycastUtil.isLookingAtEntity(
                mc.player,
                target,
                yaw,
                pitch,
                attackRange,
                throughWallsRange
        ) != null;
    }

    private boolean isLookingAtTargetBox(LivingEntity target, Rotation rotation) {
        if (mc.player == null || target == null || rotation == null) {
            return false;
        }

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = rotation.directionVector();
        AABB targetBox = target.getBoundingBox();
        Vec3 endVec = eyePos.add(lookVec.scale(getEffectiveAttackRange(target)));
        return targetBox.contains(eyePos) || targetBox.clip(eyePos, endVec).isPresent();
    }

    private RotationWithVector findRotation(Entity target, float range, float wallsRange) {
        if (mc.player == null) return null;
        Vec3 eyes = mc.player.getEyePosition();
        pointTracker.setOptions(
                false,
                0,
                0,
                false,
                0.0,
                0.0,
                false,
                0.0,
                0.0,
                0,
                0.15
        );
        Rotation initialRotation = RotationManager.INSTANCE.getCurrentRotation();
        if (initialRotation == null) {
            initialRotation = new Rotation(mc.player.getYRot(), mc.player.getXRot(), true);
        }

        RaycastMode pointMode = raycast.get();
        boolean ignoreWallsForPoint = pointMode == RaycastMode.NONE || pointMode == RaycastMode.THROUGH_WALLS;
        PointInsideBox point = pointTracker.findPoint(
                eyes,
                target,
                0,
                initialRotation,
                range,
                ignoreWallsForPoint
        );
        AABB targetBox = point.box();

        RotationPreference preference = LeastDifferencePreference.leastDifferenceToLastPoint(eyes, point.pos());

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

        if (rot == null) {
            Rotation fallback = Rotation.lookingAt(point.pos(), eyes).normalize();
            rot = new RotationWithVector(fallback, point.pos());
        }

        if (rot != null) {
            lastAimPoint = rot.vec();
            lastAimEntityId = target.getId();
        }
        return rot;
    }

    private RotationTarget buildRotationTarget(Rotation rotation, Entity target) {
        List<RotationProcessor> processors = new ArrayList<>();
        AngleSmooth smooth = selectAngleSmooth();
        if (smooth != null) {
            processors.add(smooth);
        }
        boolean spookyMode = rotationMode.get() == AngleSmoothMode.SPOOKY;
        if (!spookyMode && lazyAimEnabled.get()) {
            processors.add(lazyAimProcessor);
        }
        if (!spookyMode && shakeAimEnabled.get()) {
            processors.add(shakeAimProcessor);
        }
        if (shortStopEnabled.get()) {
            processors.add(shortStopProcessor);
        }
        boolean freeCorrection = correctionType.get() == CorrectionType.FREE;
        MovementCorrection movementCorrection;
        if (correctionType.get() == CorrectionType.NOT_VISIBLE) {
            movementCorrection = MovementCorrection.OFF;
        } else {
            movementCorrection = MovementCorrection.SILENT;
        }

        TargetStrafe targetStrafe = Modules.get(TargetStrafe.class);
        if (freeCorrection && targetStrafe != null && targetStrafe.shouldDisableAuraFreeCorrection()) {
            freeCorrection = false;
        }

        return new RotationTarget(
                rotation,
                target,
                processors,
                rotationResetTicks.get(),
                rotationResetThreshold.get(),
                true,
                movementCorrection,
                freeCorrection,
                null
        );
    }

    private boolean shouldBlockSprinting(LivingEntity target) {
        ElytraTarget elytraTarget = Modules.get(ElytraTarget.class);
        if (elytraTarget != null && elytraTarget.isRunning()) {
            return false;
        }
        return false;
    }

    public double getRange() {
        return range.get();
    }

    public double getAcquireRange() {
        return range.get() + Math.max(0.0, acquireRangeIncrement.get());
    }

    public boolean usesDynamicReach() {
        return reachMode.get() == Reach.Mode.VULCAN_297;
    }

    public double getEffectiveRange(Entity target) {
        return getEffectiveAttackRange(target);
    }

    private double getEffectiveAttackRange(Entity target) {
        return resolveDynamicReach(target, range.get());
    }

    private double getEffectiveAcquireRange(Entity target) {
        return Math.max(0.0, getAcquireRange());
    }

    private double getEffectiveWallRange(Entity target, double effectiveRange) {
        double requestedWallRange = Math.max(0.0, wallRange.get());
        return usesDynamicReach() && target instanceof Player
                ? Math.min(requestedWallRange, Math.max(0.0, effectiveRange))
                : requestedWallRange;
    }

    private double resolveDynamicReach(Entity target, double requestedReach) {
        double safeReach = Math.max(0.0, requestedReach);
        if (usesDynamicReach() && target instanceof Player) {
            return VulcanReachController.INSTANCE.clampReach(target, safeReach);
        }
        return safeReach;
    }

    private boolean criticalHitAllowed(LivingEntity target) {
        if (target == null || mc.player == null) return true;
        if (isLegacyAttackProtocol()) return true;
        if (mc.player.isFallFlying()) return true;

        Criticals criticals = Modules.get(Criticals.class);
        if (criticals == null) return true;
        ClickScheduler gateClicker = isLegacyAttackProtocol() ? clicker : null;
        CombatStrikeController.SprintResetMode resolvedSprintReset =
                CombatStrikeController.INSTANCE.resolveSprintResetMode(resolveStrikeResetMode());
        boolean ignoreLocalSprintAfterReset = resolvedSprintReset != CombatStrikeController.SprintResetMode.NONE;
        return criticals.shouldAttackForKillAura(
                gateClicker,
                target,
                criticalsSelection.get(),
                ignoreLocalSprintAfterReset
        );
    }

    private int calculateTicks(Rotation rotation) {
        AngleSmooth smooth = selectAngleSmooth();
        if (smooth == null) return 0;
        Rotation base = RotationManager.INSTANCE.getServerRotation();
        if (base == null) return 0;
        return smooth.calculateTicks(base, rotation);
    }

    private CombatStrikeController.SprintResetMode resolveStrikeResetMode() {
        return switch (sprintResetMode.get()) {
            case DEFAULT -> CombatStrikeController.SprintResetMode.DEFAULT;
            case LEGIT -> CombatStrikeController.SprintResetMode.LEGIT;
            case PACKET -> CombatStrikeController.SprintResetMode.PACKET;
            case NONE -> CombatStrikeController.SprintResetMode.NONE;
        };
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

    public enum RaycastMode {
        NONE,
        STRICT,
        THROUGH_WALLS
    }

    public enum RotationTiming {
        NORMAL,
        SNAP,
        ON_TICK
    }

    @Getter
    @RequiredArgsConstructor
    public enum AngleSmoothMode implements EnumValue.IdProvider {
        LINEAR("Linear"),
        SIGMOID("Sigmoid"),
        SMART("Smart"),
        SPOOKY("Spooky"),
        NONE("None");

        private final String id;
    }

    @Getter
    @RequiredArgsConstructor
    public enum ProtocolMode implements EnumValue.IdProvider {
        AUTO("auto"),
        FORCE_1_8("1.8"),
        FORCE_1_9("1.9");

        private final String id;
    }

    public enum SprintResetMode {
        DEFAULT,
        LEGIT,
        PACKET,
        NONE
    }

    public enum CorrectionType {
        FREE,
        FOCUSED,
        NOT_VISIBLE
    }

    // attack handled by AttackUtil
}
