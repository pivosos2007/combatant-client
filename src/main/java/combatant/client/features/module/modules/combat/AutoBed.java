/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.common.impl.TargetFilters;
import combatant.client.config.values.*;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.module.modules.combat.autobed.*;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.WorldTextRenderer;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.combat.CombatRotationModeUtil;
import combatant.client.util.combat.CombatEntityQuery;
import combatant.client.util.combat.ExplosionDamageRules;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.combat.RubberHandUseUtil;
import combatant.client.util.item.FoodUtil;
import combatant.client.util.entity.simulation.PositionExtrapolation;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import combatant.client.util.player.simulation.PlayerSimulationCache;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.TargetingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "autobed",
        displayName = "AutoBed",
        aliases = {"BedAura", "Bed"},
        category = ModuleCategory.COMBAT,
        description = "module.autobed.description"
)
public class AutoBed extends Module {
    private static final int ROTATION_PRIORITY = 33;
    private static final long DAMAGE_RESET_MS = 1000L;
    private static final long PENDING_BED_PLACE_CONFIRM_TIMEOUT_MS = 180L;
    private static final long PENDING_BED_EXPLODE_CONFIRM_TIMEOUT_MS = 320L;
    private static final double TEXT_Y_OFFSET = 0.10;
    private static final double TEXT_WORLD_SCALE = 0.025;
    private static final double TEXT_SCALE = 1.0;
    private static final double TEXT_GAP_SCALE = 1.0;

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue placeEnabled = boolCommon("autobedPlace", "place", CommonSettingSchemas.PLACE, true);
    private final BooleanValue explodeEnabled = boolCommon("autobedExplode", "explode", CommonSettingSchemas.EXPLODE, true);
    private final BooleanValue instantExplode = bool("autobedInstantExplode", "instant_explode", true);
    private final EnumValue<Timing> timing = enumCommon("autobedTiming", "timing", CommonSettingSchemas.TIMING, Timing.NORMAL);
    private final NumberValue<Integer> calcDelay = numCommon("autobedCalcDelay", "calc_delay", CommonSettingSchemas.CALC_DELAY, 100, 0, 500);
    private final NumberValue<Integer> placeDelay = numCommon(
            "autobedPlaceDelay",
            "place_delay",
            CommonSettingSchemas.PLACEMENT_DELAY,
            0,
            0,
            1000
    );
    private final NumberValue<Integer> lowPlaceDelay = numCommon("autobedLowPlaceDelay", "low_place_delay", CommonSettingSchemas.LOW_PLACE_DELAY, 550, 0, 1000);
    private final NumberValue<Integer> explodeDelay = numCommon(
            "autobedExplodeDelay",
            "explode_delay",
            CommonSettingSchemas.PLACEMENT_BREAK_DELAY,
            0,
            0,
            1000
    );
    private final NumberValue<Integer> lowExplodeDelay = numCommon("autobedLowExplodeDelay", "low_explode_delay", CommonSettingSchemas.LOW_EXPLODE_DELAY, 550, 0, 1000);
    private final NumberValue<Float> placeRange = numCommon(
            "autobedPlaceRange",
            "place_range",
            CommonSettingSchemas.PLACEMENT_RANGE,
            5.0f,
            1.0f,
            6.0f
    );
    private final NumberValue<Float> wallRange = numCommon(
            "autobedWallRange",
            "wall_range",
            CommonSettingSchemas.PLACEMENT_WALL_RANGE,
            3.5f,
            0.0f,
            6.0f
    );
    private final EnumValue<AutoBedPlacementMode> placementMode = enumCommon(
            "autobedPlacementMode",
            "placement_mode",
            CommonSettingSchemas.PLACEMENT_MODE,
            AutoBedPlacementMode.DEFAULT,
            AutoBedPlacementMode.values()
    );
    private final NumberValue<Float> targetRange = numCommon(
            "autobedTargetRange",
            "target_range",
            CommonSettingSchemas.COMBAT_RANGE,
            10.0f,
            1.0f,
            15.0f
    );
    private final BooleanMapValue targetToggles = groupCommon(
            "autobedTargets",
            "targets",
            CommonSettingSchemas.TARGET_FILTERS
    );
    private final EnumValue<TargetingUtil.TargetPriority> priority = enumCommon(
            "autobedPriority",
            "priority",
            CommonSettingSchemas.COMBAT_PRIORITY,
            TargetingUtil.TargetPriority.DISTANCE,
            TargetingUtil.TargetPriority.values()
    );
    private final NumberValue<Float> minDamage = numCommon("autobedMinDamage", "min_damage", CommonSettingSchemas.MIN_DAMAGE, 6.0f, 0.0f, 36.0f);
    private final NumberValue<Float> faceplaceHealth = numCommon("autobedFaceplaceHealth", "faceplace_health", CommonSettingSchemas.FACEPLACE_HEALTH, 5.0f, 0.0f, 36.0f);
    private final NumberValue<Float> maxSelfDamage = numCommon("autobedMaxSelfDamage", "max_self_damage", CommonSettingSchemas.MAX_SELF_DAMAGE, 10.0f, 0.0f, 36.0f);
    private final NumberValue<Integer> predictTicks = numCommon(
            "autobedPredictTicks",
            "predict_ticks",
            CommonSettingSchemas.PLACEMENT_PREDICTION,
            3,
            0,
            20
    );
    private final NumberValue<Integer> selfPredictTicks = numCommon("autobedSelfPredictTicks", "self_predict_ticks", CommonSettingSchemas.SELF_PREDICT_TICKS, 3, 0, 20);
    private final BooleanValue pauseMining = boolCommon("autobedPauseMining", "pause_mining", CommonSettingSchemas.PAUSE_MINING, true);
    private final BooleanValue pauseEating = boolCommon("autobedPauseEating", "pause_eating", CommonSettingSchemas.PAUSE_EATING, true);
    private final NumberValue<Float> pauseHealth = numCommon(
            "autobedPauseHealth",
            "pause_health",
            CommonSettingSchemas.PLAYER_HEALTH_THRESHOLD,
            8.0f,
            0.0f,
            20.0f
    );
    private final EnumValue<InventorySearchScope> swapScope = enumCommon(
            "autobedSwapScope",
            "swap_scope",
            CommonSettingSchemas.INVENTORY_SEARCH_SCOPE,
            InventorySearchScope.FULL,
            InventorySearchScope.values()
    );
    private final EnumValue<InventorySwapVisibility> swapVisibility = enumCommon(
            "autobedSwapVisibility",
            "swap_visibility",
            CommonSettingSchemas.INVENTORY_SWAP_VISIBILITY,
            InventorySwapVisibility.SILENT,
            InventorySwapVisibility.values()
    );
    private final BooleanValue restoreItem = boolCommon(
            "autobedRestoreItem",
            "restore_item",
            CommonSettingSchemas.INVENTORY_RESTORE_ITEM,
            true
    );
    private final ModeValue attackMode = modeCommon(
            "autobedAttackMode",
            "attack_mode",
            CommonSettingSchemas.COMBAT_ATTACK_MODE,
            CombatRotationModeUtil.MODE_ROTATIONS,
            CombatRotationModeUtil.MODE_ROTATIONS,
            CombatRotationModeUtil.MODE_NO_ROTATIONS
    );
    private final EnumValue<MovementCorrection> movementCorrection = visibleWhen(enumCommon(
            "autobedMovementCorrection",
            "movement_correction",
            CommonSettingSchemas.PLACEMENT_MOVEMENT_CORRECTION,
            MovementCorrection.SILENT,
            MovementCorrection.values()
    ), this::usesRotationSettings);
    private final NumberValue<Integer> rotationResetTicks = visibleWhen(numCommon(
            "autobedRotationResetTicks",
            "rotation_reset_ticks",
            CommonSettingSchemas.ROTATION_RESET_TICKS,
            1,
            1,
            20
    ), this::usesRotationSettings);
    private final NumberValue<Float> rotationResetThreshold = visibleWhen(numCommon(
            "autobedRotationResetThreshold",
            "rotation_reset_threshold",
            CommonSettingSchemas.ROTATION_RESET_THRESHOLD,
            2.0f,
            0.1f,
            15.0f
    ), this::usesRotationSettings);
    private final BooleanValue renderEnabled = boolCommon(
            "autobedRender",
            "render",
            CommonSettingSchemas.PLACEMENT_RENDER,
            true
    );
    private final EnumValue<RenderMode> renderMode = enumCommon(
            "autobedRenderMode",
            "render_mode",
            CommonSettingSchemas.PLACEMENT_RENDER_MODE,
            RenderMode.FADE,
            RenderMode.values()
    );
    private final BooleanValue renderSelfDamage = boolCommon("autobedRenderSelfDamage", "render_self_damage", CommonSettingSchemas.RENDER_SELF_DAMAGE, true);
    private final BooleanValue drawDamage = boolCommon("autobedRenderDamage", "render_damage", CommonSettingSchemas.RENDER_DAMAGE, true);
    private final RGBAColorValue fillColor = common(color("autobedFillColor", "#5A285AFF"), CommonSettingSchemas.FILL_COLOR);
    private final RGBAColorValue lineColor = common(color("autobedLineColor", "#FFAA55FF"), CommonSettingSchemas.LINE_COLOR);
    private final NumberValue<Float> lineWidth = numCommon("autobedLineWidth", "line_width", CommonSettingSchemas.LINE_WIDTH, 2.0f, 1.0f, 6.0f);
    private final RGBAColorValue textColor = common(color("autobedTextColor", "#FFFFFFFF"), CommonSettingSchemas.TEXT_COLOR);
    private final NumberValue<Integer> slideDelay = visibleWhen(numCommon("autobedSlideDelay", "slide_delay", CommonSettingSchemas.SLIDE_DELAY, 200, 1, 1000),
            () -> renderMode.get() == RenderMode.SLIDE);
    private final NumberValue<Integer> fadeTime = visibleWhen(numCommon("autobedFadeTime", "fade_time", CommonSettingSchemas.FADE_TIME, 500, 100, 2000),
            () -> renderMode.get() == RenderMode.FADE);

    private final AutoBedPlanner planner = new AutoBedPlanner();
    private final AutoBedPlanner.Context plannerContext = new PlannerContext();
    private final Map<BlockPos, Long> renderPositions = new ConcurrentHashMap<>();

    private LivingEntity target;
    private AutoBedData bestPlace;
    private AutoBedData bestExplode;
    private BlockPos renderFootPos;
    private BlockPos renderHeadPos;
    private BlockPos prevRenderFootPos;
    private BlockPos prevRenderHeadPos;
    private BlockPos bestFootPos;
    private BlockPos bestHeadPos;
    private int renderTick;
    private int renderStartTick;
    private long lastPlaceMs;
    private long lastExplodeMs;
    private long lastDamageUpdateMs;
    private long lastCalcMs;
    private AutoBedData pendingBedData;
    private long pendingBedSinceMs;
    private boolean pendingBedPlaceSent;
    private boolean pendingBedExplodeSent;
    private long pendingBedExplodeSentMs;
    private float renderDamage;
    private float renderSelfDamageValue;
    private boolean lowDamageMode;
    private int lastScanned;
    private int lastCandidates;
    private int lastSafeCandidates;
    private int lastSelfDamageRejects;
    private String disabledReason;

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    @Override
    protected String getUnavailableReason() {
        if (mc != null && mc.level != null && !isExplosiveBedDimension(mc.level)) {
            return "Nether/End only";
        }
        return "";
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        RotationManager.INSTANCE.clear(this);
        TargetManager.setAutoBedTarget(null);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        renderTick++;
        InventorySwap.INSTANCE.tick();
        purgeExpiredRenderPositions();
        if (System.currentTimeMillis() - lastDamageUpdateMs > DAMAGE_RESET_MS && renderPositions.isEmpty()) {
            renderDamage = 0.0f;
            renderSelfDamageValue = 0.0f;
        }
    }

    @EventHandler(priority = 23)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!isEnabled()) return;

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            clearCombatState();
            TargetManager.setAutoBedTarget(null);
            return;
        }

        if (!isExplosiveBedDimension(mc.level)) {
            disabledReason = "Wrong dimension";
            clearCombatState();
            TargetManager.setAutoBedTarget(null);
            RotationManager.INSTANCE.clear(this);
            return;
        }
        disabledReason = null;

        target = findCombatTarget();
        TargetManager.setAutoBedTarget(target);
        updateCandidates();

        if (timing.get() == Timing.NORMAL) {
            runCombatCycle();
        }
    }

    @EventHandler(priority = -23)
    private void onRotationUpdatePost(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.POST) return;
        if (!isEnabled() || timing.get() != Timing.SEQUENTIAL) return;
        runCombatCycle();
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (renderEnabled.get()) {
            renderPlacement(renderer, tickDelta);
        }
    }

    private void updateCandidates() {
        if (target == null || shouldPause()) {
            bestPlace = null;
            bestExplode = null;
            lastCalcMs = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        long delay = Math.max(0, calcDelay.get());
        if (delay > 0L && now - lastCalcMs < delay) {
            return;
        }
        lastCalcMs = now;

        AutoBedPlanner.ScanResult result = planner.scan(
                plannerContext,
                target,
                resolvePredictedPosition(target, predictTicks.get()),
                Mth.ceil(placeRange.get())
        );
        bestPlace = result.bestPlace();
        bestExplode = result.bestExplode();
        lastScanned = result.scanned();
        lastCandidates = result.candidates();
        lastSafeCandidates = result.safeCandidates();
        lastSelfDamageRejects = result.selfDamageRejects();

        AutoBedData renderData = bestExplode != null ? bestExplode : bestPlace;
        if (renderData != null) {
            setRenderCandidate(renderData);
            lowDamageMode = !ExplosionDamageRules.shouldOverrideMinDamage(target, renderData.damage(), faceplaceHealth.get())
                    && renderData.damage() < minDamage.get();
        } else if (renderPositions.isEmpty()) {
            bestFootPos = null;
            bestHeadPos = null;
        }
    }

    private void runCombatCycle() {
        if (mc.player == null || mc.level == null) {
            clearPendingBed();
            RotationManager.INSTANCE.clear(this);
            return;
        }
        if (shouldPause()) {
            RotationManager.INSTANCE.clear(this);
            return;
        }

        if (continuePendingBedCycle()) {
            return;
        }

        if (target == null) {
            RotationManager.INSTANCE.clear(this);
            return;
        }

        if (explodeEnabled.get() && bestExplode != null && hasExplodeDelayElapsed()) {
            executeWithRotation(bestExplode, false, () -> explodeBed(bestExplode));
            return;
        }

        if (placeEnabled.get() && bestPlace != null && hasPlaceDelayElapsed()) {
            executeWithRotation(bestPlace, true, () -> placeBed(bestPlace));
            return;
        }

        RotationManager.INSTANCE.clear(this);
    }

    private void executeWithRotation(AutoBedData data, boolean placing, Runnable action) {
        if (data == null || action == null || mc.player == null) return;
        if (!usesRotationSettings()) {
            RotationManager.INSTANCE.clear(this);
            action.run();
            return;
        }

        Rotation rotation = rotationFor(data, placing);
        RotationTarget rotationTarget = new RotationTarget(
                rotation,
                target,
                List.of(),
                rotationResetTicks.get(),
                rotationResetThreshold.get(),
                true,
                movementCorrection.get(),
                new RestrictedSingleUseAction(action)
        );
        RotationManager.INSTANCE.setRotationTarget(rotationTarget, ROTATION_PRIORITY, this);
    }

    private Rotation rotationFor(AutoBedData data, boolean placing) {
        BlockHitResult hit = placing ? data.placeHitResult() : data.explodeHitResult();
        Vec3 point = hit != null ? hit.getLocation() : data.explosionVec();
        Rotation look = Rotation.lookingAt(point, mc.player.getEyePosition());
        if (placing) {
            return new Rotation(AutoBedInteractionUtil.yawForFacing(data.facing()), look.pitch()).normalize();
        }
        return look.normalize();
    }

    private void placeBed(AutoBedData data) {
        if (data == null || data.existingBed() || !hasPlaceDelayElapsed() || shouldPause()) return;
        AutoBedData refreshed = planner.evaluate(plannerContext, data, target);
        if (refreshed == null || refreshed.existingBed()) {
            clearPendingBedIfMatches(data);
            return;
        }

        setPendingBed(refreshed, true);
        boolean placed = AutoBedActionUtil.placeBed(
                mc,
                this,
                refreshed.placeHitResult(),
                swapScope.get(),
                swapVisibility.get(),
                restoreItem.get()
        );
        if (!placed) {
            clearPendingBedIfMatches(refreshed);
            return;
        }

        lastPlaceMs = System.currentTimeMillis();
        pendingBedSinceMs = lastPlaceMs;
        markPlaced(refreshed);
    }

    private void explodeBed(AutoBedData data) {
        if (data == null || !hasExplodeDelayElapsed() || shouldPause()) return;
        AutoBedData refreshed = planner.evaluate(plannerContext, data, target);
        if (refreshed == null || !refreshed.existingBed()) {
            clearPendingBedIfMatches(data);
            return;
        }

        setPendingBed(refreshed, false);
        detonatePendingBed(refreshed);
    }

    private boolean continuePendingBedCycle() {
        if (pendingBedData == null) {
            return false;
        }
        if (mc.player == null || mc.level == null) {
            clearPendingBed();
            return false;
        }

        long now = System.currentTimeMillis();
        BlockPos bedPos = findPendingBedInteractPos();
        if (bedPos == null) {
            if (pendingBedExplodeSent) {
                clearPendingBed();
                return false;
            }
            if (pendingBedPlaceSent && now - pendingBedSinceMs < PENDING_BED_PLACE_CONFIRM_TIMEOUT_MS) {
                RotationManager.INSTANCE.clear(this);
                return true;
            }
            clearPendingBed();
            return false;
        }

        AutoBedData pendingData = resolvePendingBedData(bedPos);
        if (pendingData == null) {
            if (now - pendingBedSinceMs < PENDING_BED_PLACE_CONFIRM_TIMEOUT_MS) {
                RotationManager.INSTANCE.clear(this);
                return true;
            }
            clearPendingBed();
            return false;
        }

        pendingBedData = pendingData;
        pendingBedSinceMs = now;

        if (pendingBedExplodeSent) {
            if (isPendingBedBlock(bedPos) && now - pendingBedExplodeSentMs < PENDING_BED_EXPLODE_CONFIRM_TIMEOUT_MS) {
                RotationManager.INSTANCE.clear(this);
                return true;
            }
            pendingBedExplodeSent = false;
            pendingBedExplodeSentMs = 0L;
            if (!isPendingBedBlock(bedPos)) {
                clearPendingBed();
                return false;
            }
        }

        if (!explodeEnabled.get()) {
            clearPendingBed();
            return false;
        }

        if (hasPendingExplodeDelayElapsed()) {
            executeWithRotation(pendingData, false, () -> detonatePendingBed(pendingData));
            return true;
        }

        RotationManager.INSTANCE.clear(this);
        return true;
    }

    private void detonatePendingBed(AutoBedData data) {
        if (data == null || data.explodeHitResult() == null || !hasPendingExplodeDelayElapsed() || shouldPause()) return;

        boolean exploded = AutoBedActionUtil.explodeBed(
                mc,
                this,
                data.explodeHitResult(),
                swapScope.get(),
                swapVisibility.get(),
                restoreItem.get()
        );
        if (!exploded) return;

        lastExplodeMs = System.currentTimeMillis();
        pendingBedExplodeSent = true;
        pendingBedExplodeSentMs = lastExplodeMs;
        markPlaced(data);
    }

    private AutoBedData resolvePendingBedData(BlockPos bedPos) {
        if (pendingBedData == null || bedPos == null || mc.player == null || mc.level == null) {
            return null;
        }

        BlockHitResult hit = findPendingBedInteractResult(bedPos);
        if (hit == null) {
            return null;
        }

        var state = mc.level.getBlockState(hit.getBlockPos());
        if (!AutoBedInteractionUtil.isBedBlock(state)) {
            return null;
        }

        BlockPos footPos = AutoBedInteractionUtil.resolveFootPos(state, hit.getBlockPos());
        BlockPos headPos = AutoBedInteractionUtil.resolveHeadPos(state, hit.getBlockPos());
        if (footPos == null || headPos == null) {
            return null;
        }

        AutoBedData template = new AutoBedData(
                footPos,
                headPos,
                AutoBedInteractionUtil.bedFacing(state),
                hit.getBlockPos(),
                pendingBedData.placeHitResult(),
                hit,
                AutoBedInteractionUtil.bedExplosionVec(hit.getBlockPos()),
                pendingBedData.damage(),
                pendingBedData.selfDamage(),
                pendingBedData.overrideDamage(),
                true
        );
        AutoBedData evaluated = target != null ? planner.evaluate(plannerContext, template, target) : null;
        if (evaluated != null && evaluated.existingBed()) {
            return evaluated;
        }

        return template;
    }

    private BlockPos findPendingBedInteractPos() {
        if (pendingBedData == null || mc.level == null) {
            return null;
        }

        BlockPos preferred = pendingBedData.interactPos();
        if (isPendingBedBlock(preferred)) return preferred;
        if (isPendingBedBlock(pendingBedData.footPos())) return pendingBedData.footPos();
        if (isPendingBedBlock(pendingBedData.headPos())) return pendingBedData.headPos();

        // If placement yaw was not applied exactly, vanilla can still place the bed at the same clicked foot
        // but with another horizontal head direction. Adopt that real client-side bed immediately instead of
        // sitting in Waiting bed until the confirm timeout expires.
        BlockPos adopted = findBedTouchingPendingPlacement();
        if (adopted != null) return adopted;
        return null;
    }

    private BlockHitResult findPendingBedInteractResult(BlockPos preferred) {
        if (preferred != null && isPendingBedBlock(preferred)) {
            BlockHitResult hit = getBedInteractResult(preferred);
            if (hit != null) return hit;
        }

        BlockPos foot = pendingBedData.footPos();
        if (foot != null && isPendingBedBlock(foot)) {
            BlockHitResult hit = getBedInteractResult(foot);
            if (hit != null) return hit;
        }

        BlockPos head = pendingBedData.headPos();
        if (head != null && isPendingBedBlock(head)) {
            BlockHitResult hit = getBedInteractResult(head);
            if (hit != null) return hit;
        }

        BlockPos adopted = findBedTouchingPendingPlacement();
        if (adopted != null) {
            BlockHitResult hit = getBedInteractResult(adopted);
            if (hit != null) return hit;
        }
        return null;
    }

    private BlockPos findBedTouchingPendingPlacement() {
        if (pendingBedData == null || mc.level == null) return null;
        BlockPos foot = pendingBedData.footPos();
        BlockPos head = pendingBedData.headPos();

        BlockPos direct = findBedAtOrAdjacent(foot);
        if (direct != null) return direct;
        return findBedAtOrAdjacent(head);
    }

    private BlockPos findBedAtOrAdjacent(BlockPos origin) {
        if (origin == null || mc.level == null) return null;
        if (isAdoptablePendingBedBlock(origin)) return origin;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = origin.relative(direction);
            if (isAdoptablePendingBedBlock(pos)) {
                return pos;
            }
        }
        return null;
    }

    private boolean isPendingBedBlock(BlockPos pos) {
        if (pos == null || mc.level == null || pendingBedData == null || !mc.level.isInWorldBounds(pos)) return false;
        var state = mc.level.getBlockState(pos);
        if (!AutoBedInteractionUtil.isBedBlock(state)) return false;
        BlockPos footPos = AutoBedInteractionUtil.resolveFootPos(state, pos);
        BlockPos headPos = AutoBedInteractionUtil.resolveHeadPos(state, pos);
        if (positionsEqual(footPos, pendingBedData.footPos()) && positionsEqual(headPos, pendingBedData.headPos())) {
            return true;
        }
        return pendingBedPlaceSent && overlapsPendingBed(footPos, headPos);
    }

    private boolean isAdoptablePendingBedBlock(BlockPos pos) {
        if (pos == null || mc.level == null || pendingBedData == null || !mc.level.isInWorldBounds(pos)) return false;
        var state = mc.level.getBlockState(pos);
        if (!AutoBedInteractionUtil.isBedBlock(state)) return false;
        BlockPos footPos = AutoBedInteractionUtil.resolveFootPos(state, pos);
        BlockPos headPos = AutoBedInteractionUtil.resolveHeadPos(state, pos);
        return overlapsPendingBed(footPos, headPos);
    }

    private boolean overlapsPendingBed(BlockPos footPos, BlockPos headPos) {
        if (pendingBedData == null) return false;
        return positionsEqual(footPos, pendingBedData.footPos())
                || positionsEqual(footPos, pendingBedData.headPos())
                || positionsEqual(headPos, pendingBedData.footPos())
                || positionsEqual(headPos, pendingBedData.headPos());
    }

    private boolean positionsEqual(BlockPos first, BlockPos second) {
        return first != null && first.equals(second);
    }

    private void setPendingBed(AutoBedData data, boolean placeSent) {
        if (data == null) {
            clearPendingBed();
            return;
        }
        if (pendingBedData == null
                || !positionsEqual(data.footPos(), pendingBedData.footPos())
                || !positionsEqual(data.headPos(), pendingBedData.headPos())) {
            pendingBedSinceMs = System.currentTimeMillis();
            pendingBedExplodeSent = false;
            pendingBedExplodeSentMs = 0L;
        }
        pendingBedData = data;
        pendingBedPlaceSent = placeSent || !data.existingBed();
    }

    private void clearPendingBedIfMatches(AutoBedData data) {
        if (data == null || pendingBedData == null) return;
        if (positionsEqual(data.footPos(), pendingBedData.footPos())
                && positionsEqual(data.headPos(), pendingBedData.headPos())) {
            clearPendingBed();
        }
    }

    private void clearPendingBed() {
        pendingBedData = null;
        pendingBedSinceMs = 0L;
        pendingBedPlaceSent = false;
        pendingBedExplodeSent = false;
        pendingBedExplodeSentMs = 0L;
    }

    private boolean shouldPause() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return true;
        if (!isExplosiveBedDimension(mc.level)) {
            disabledReason = "Wrong dimension";
            return true;
        }
        disabledReason = null;
        if (pauseMining.get() && mc.gameMode.isDestroying()) return true;
        if (pauseEating.get()
                && mc.player.isUsingItem()
                && FoodUtil.isFood(mc.player.getUseItem())
                && !RubberHandUseUtil.canBypassCurrentUse(mc)) return true;
        return mc.player.getHealth() + mc.player.getAbsorptionAmount() < pauseHealth.get();
    }

    private boolean isExplosiveBedDimension(ClientLevel level) {
        return level != null && (level.dimension() == Level.NETHER || level.dimension() == Level.END);
    }

    private boolean hasPlaceDelayElapsed() {
        return System.currentTimeMillis() - lastPlaceMs >= Math.max(0, lowDamageMode ? lowPlaceDelay.get() : placeDelay.get());
    }

    private boolean hasExplodeDelayElapsed() {
        return System.currentTimeMillis() - lastExplodeMs >= Math.max(0, lowDamageMode ? lowExplodeDelay.get() : explodeDelay.get());
    }

    private boolean hasPendingExplodeDelayElapsed() {
        return (pendingBedPlaceSent && instantExplode.get()) || hasExplodeDelayElapsed();
    }

    private long getPlaceDelayRemainingMs() {
        return Math.max(0L, Math.max(0, lowDamageMode ? lowPlaceDelay.get() : placeDelay.get()) - (System.currentTimeMillis() - lastPlaceMs));
    }

    private long getExplodeDelayRemainingMs() {
        return Math.max(0L, Math.max(0, lowDamageMode ? lowExplodeDelay.get() : explodeDelay.get()) - (System.currentTimeMillis() - lastExplodeMs));
    }

    private long getPendingExplodeDelayRemainingMs() {
        if (pendingBedPlaceSent && instantExplode.get()) return 0L;
        return getExplodeDelayRemainingMs();
    }

    private void setRenderCandidate(AutoBedData data) {
        bestFootPos = data.footPos();
        bestHeadPos = data.headPos();
        renderDamage = Math.max(0.0f, data.damage());
        renderSelfDamageValue = Math.max(0.0f, data.selfDamage());
        lastDamageUpdateMs = System.currentTimeMillis();
    }

    private void markPlaced(AutoBedData data) {
        if (data == null || data.footPos() == null || data.headPos() == null) return;
        if (!data.footPos().equals(renderFootPos) || !data.headPos().equals(renderHeadPos)) {
            renderStartTick = renderTick;
            prevRenderFootPos = renderFootPos;
            prevRenderHeadPos = renderHeadPos;
            renderFootPos = data.footPos();
            renderHeadPos = data.headPos();
        }
        setRenderCandidate(data);
        renderPositions.put(data.footPos(), System.currentTimeMillis());
        renderPositions.put(data.headPos(), System.currentTimeMillis());
    }

    private void renderPlacement(Renderer3D renderer, float tickDelta) {
        String damageText = buildDamageText();
        if (damageText.isEmpty() && renderPositions.isEmpty() && renderFootPos == null) {
            return;
        }

        if (renderMode.get() == RenderMode.FADE) {
            renderFade(renderer);
            return;
        }

        BlockPos activeFoot = renderPositions.isEmpty() ? bestFootPos : renderFootPos;
        BlockPos activeHead = renderPositions.isEmpty() ? bestHeadPos : renderHeadPos;
        if (activeFoot == null || activeHead == null) return;

        if (renderMode.get() == RenderMode.SLIDE) {
            BlockPos slideFoot = renderPositions.isEmpty() ? bestFootPos : renderFootPos;
            BlockPos slideHead = renderPositions.isEmpty() ? bestHeadPos : renderHeadPos;
            BlockPos fromFoot = prevRenderFootPos != null ? prevRenderFootPos : slideFoot;
            BlockPos fromHead = prevRenderHeadPos != null ? prevRenderHeadPos : slideHead;
            renderBox(renderer, ExplosionRenderUtil.lerpBox(bedBox(fromFoot, fromHead), bedBox(slideFoot, slideHead), getSlideProgress(tickDelta)), 1.0f);
        } else {
            renderBox(renderer, bedBox(activeFoot, activeHead), 1.0f);
        }
    }

    private AABB bedBox(BlockPos foot, BlockPos head) {
        return new AABB(foot).minmax(new AABB(head));
    }

    private float getSlideProgress(float tickDelta) {
        float clampedTickDelta = Mth.clamp(tickDelta, 0.0f, 1.0f);
        float durationTicks = Math.max(1.0f, slideDelay.get() / 50.0f);
        return Mth.clamp((renderTick - renderStartTick + clampedTickDelta) / durationTicks, 0.0f, 1.0f);
    }

    private void renderFade(Renderer3D renderer) {
        long now = System.currentTimeMillis();
        int lifetime = Math.max(1, fadeTime.get());
        List<Map.Entry<BlockPos, Long>> entries = new ArrayList<>(renderPositions.entrySet());
        for (Map.Entry<BlockPos, Long> entry : entries) {
            long age = now - entry.getValue();
            if (age > lifetime) {
                renderPositions.remove(entry.getKey());
                continue;
            }
            float alpha = 1.0f - (age / (float) lifetime);
            renderBox(renderer, new AABB(entry.getKey()), alpha);
        }
    }

    private void renderBox(Renderer3D renderer, AABB box, float alpha) {
        int fillArgb = ExplosionRenderUtil.applyOpacity(fillColor.getArgb(), alpha);
        int lineArgb = ExplosionRenderUtil.applyOpacity(lineColor.getArgb(), alpha);
        ExplosionRenderUtil.addFilledBox(renderer, box, fillArgb);

        float prevWidth = RenderState.lineWidth;
        RenderState.lineWidth = Math.max(0.5f, lineWidth.get());
        try {
            ExplosionRenderUtil.addOutlineBox(renderer, box, lineArgb);
        } finally {
            RenderState.lineWidth = prevWidth;
        }

        if (drawDamage.get()) {
            renderDamageText(renderer, box.getCenter(), alpha);
        }
    }

    private void renderDamageText(Renderer3D renderer, Vec3 anchor, float alpha) {
        String mainText = ExplosionRenderUtil.formatDamage(renderDamage);
        if (mainText.isEmpty()) return;

        int mainArgb = ExplosionRenderUtil.applyOpacity(textColor.getArgb(), alpha);
        TextRenderer bold = Fonts.renderer("Iosevka", FontInfo.Type.Bold, TextRenderer.get());
        WorldTextRenderer.Options baseOptions = WorldTextRenderer.Options.defaults()
                .withScale(TEXT_SCALE)
                .withWorldScale(TEXT_WORLD_SCALE)
                .withDepthMode(Renderer3D.DepthMode.NONE)
                .withShadow(true)
                .withOffset(0.0, TEXT_Y_OFFSET);

        if (!renderSelfDamage.get() || renderSelfDamageValue <= 0.0f) {
            WorldTextRenderer.drawBillboard(
                    renderer,
                    bold,
                    mainText,
                    anchor,
                    baseOptions.withCentered(true).withColor(new RenderColor(mainArgb))
            );
            return;
        }

        String tailText = " / " + ExplosionRenderUtil.formatDamage(renderSelfDamageValue);
        TextRenderer medium = Fonts.renderer("Iosevka", FontInfo.Type.Regular, TextRenderer.get());
        double mainWidth = ExplosionRenderUtil.measureWidth(bold, mainText, TEXT_SCALE);
        double tailWidth = ExplosionRenderUtil.measureWidth(medium, tailText, TEXT_GAP_SCALE);
        double startX = -(mainWidth + tailWidth) * 0.5;

        double drawnMain = WorldTextRenderer.drawBillboard(
                renderer,
                bold,
                mainText,
                anchor,
                baseOptions.withCentered(false)
                        .withOffset(startX, TEXT_Y_OFFSET)
                        .withColor(new RenderColor(mainArgb))
        );

        WorldTextRenderer.drawBillboard(
                renderer,
                medium,
                tailText,
                anchor,
                baseOptions.withCentered(false)
                        .withScale(TEXT_GAP_SCALE)
                        .withOffset(startX + drawnMain, TEXT_Y_OFFSET)
                        .withColor(new RenderColor(mainArgb))
        );
    }

    private void purgeExpiredRenderPositions() {
        long now = System.currentTimeMillis();
        long lifetime = Math.max(1, fadeTime.get());
        renderPositions.entrySet().removeIf(entry -> now - entry.getValue() > lifetime);
    }

    public String getTargetHudStatus(LivingEntity hudTarget) {
        if (!isEnabled()) return null;
        if (disabledReason != null) return disabledReason;
        if (hudTarget == null || target == null || hudTarget != target) return null;
        if (mc.player == null || mc.level == null) return "Idle";
        if (!isExplosiveBedDimension(mc.level)) return "Wrong dimension";
        if (shouldPause()) return "Paused";
        if (!AutoBedActionUtil.hasBed(mc, swapScope.get())) return "No beds";

        if (pendingBedData != null) {
            if (pendingBedExplodeSent) return "Confirming bed";
            if (findPendingBedInteractPos() != null) {
                long wait = getPendingExplodeDelayRemainingMs();
                if (wait > 0L) return "Bed in " + wait + "ms";
                return usesRotationSettings() ? "Rotate -> bed" : "Exploding bed";
            }
            return "Waiting bed";
        }
        if (explodeEnabled.get() && bestExplode != null) {
            long wait = getExplodeDelayRemainingMs();
            if (wait > 0L) return "Bed in " + wait + "ms";
            return usesRotationSettings() ? "Rotate -> bed" : "Exploding bed";
        }
        if (placeEnabled.get() && bestPlace != null) {
            long wait = getPlaceDelayRemainingMs();
            if (wait > 0L) return "Bed in " + wait + "ms";
            return usesRotationSettings() ? "Rotate -> bed" : "Placing bed";
        }
        if (lastSelfDamageRejects > 0 || (lastCandidates > 0 && lastSafeCandidates <= 0)) return "Self dmg";
        if (lastScanned <= 0) return "No scan";
        return "No bed";
    }

    private LivingEntity findCombatTarget() {
        if (mc.player == null || mc.level == null) return null;
        TargetingUtil.TargetingSettings settings = new TargetingUtil.TargetingSettings(
                targetRange.get(),
                180.0f,
                targetToggles.get(TargetFilters.PLAYERS_ONLY),
                targetToggles.get(TargetFilters.IGNORE_FRIENDS),
                targetToggles.get(TargetFilters.IGNORE_STAFF),
                targetToggles.get(TargetFilters.IGNORE_ENEMIES),
                targetToggles.get(TargetFilters.IGNORE_NAKED),
                targetToggles.get(TargetFilters.IGNORE_ENTITIES),
                targetToggles.get(TargetFilters.VISIBLE_ONLY),
                priority.get()
        );
        return TargetingUtil.findBestTarget(mc, settings);
    }

    private Vec3 resolvePredictedPosition(LivingEntity entity, int ticks) {
        if (entity == null || ticks <= 0) {
            return entity != null ? entity.position() : Vec3.ZERO;
        }
        if (entity instanceof Player player) {
            PlayerSimulationCache.SimulatedPlayerCache simulation =
                    player == mc.player
                            ? PlayerSimulationCache.getSimulationForLocalPlayer()
                            : PlayerSimulationCache.getSimulationForOtherPlayers(player);
            if (simulation != null) {
                return simulation.getSnapshotAt(ticks).pos();
            }
        }
        return PositionExtrapolation.getBestForEntity(entity).getPositionInTicks(ticks);
    }

    private boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage) {
        return ExplosionDamageRules.shouldOverrideMaxSelfDamage(
                mc.player,
                target,
                damage,
                selfDamage,
                maxSelfDamage.get()
        );
    }

    private boolean isSafe(float damage, float selfDamage, boolean overrideDamage) {
        return ExplosionDamageRules.isSafe(mc.player, selfDamage, overrideDamage);
    }

    private BlockHitResult getPlaceInteractResult(BlockPos footPos, BlockPos headPos) {
        return AutoBedInteractionUtil.getPlaceSupportInteract(
                mc.level,
                mc.player,
                footPos,
                headPos,
                placementMode.get(),
                placeRange.get(),
                wallRange.get()
        );
    }

    private BlockHitResult getBedInteractResult(BlockPos pos) {
        return AutoBedInteractionUtil.getBedInteractResult(
                mc.level,
                mc.player,
                pos,
                placementMode.get(),
                placeRange.get(),
                wallRange.get()
        );
    }

    private boolean usesRotationSettings() {
        return CombatRotationModeUtil.usesRotations(attackMode);
    }

    private String buildDamageText() {
        String main = ExplosionRenderUtil.formatDamage(renderDamage);
        if (main.isEmpty()) return "";
        if (!renderSelfDamage.get() || renderSelfDamageValue <= 0.0f) return main;
        return main + " / " + ExplosionRenderUtil.formatDamage(renderSelfDamageValue);
    }

    private void clearCombatState() {
        target = null;
        bestPlace = null;
        bestExplode = null;
        bestFootPos = null;
        bestHeadPos = null;
        clearPendingBed();
        RotationManager.INSTANCE.clear(this);
        lastScanned = 0;
        lastCandidates = 0;
        lastSafeCandidates = 0;
        lastSelfDamageRejects = 0;
    }

    private void resetState() {
        target = null;
        bestPlace = null;
        bestExplode = null;
        renderFootPos = null;
        renderHeadPos = null;
        prevRenderFootPos = null;
        prevRenderHeadPos = null;
        bestFootPos = null;
        bestHeadPos = null;
        renderTick = 0;
        renderStartTick = 0;
        lastPlaceMs = 0L;
        lastExplodeMs = 0L;
        lastDamageUpdateMs = 0L;
        lastCalcMs = 0L;
        clearPendingBed();
        renderDamage = 0.0f;
        renderSelfDamageValue = 0.0f;
        lowDamageMode = false;
        renderPositions.clear();
        lastScanned = 0;
        lastCandidates = 0;
        lastSafeCandidates = 0;
        lastSelfDamageRejects = 0;
        disabledReason = null;
    }

    private final class PlannerContext implements AutoBedPlanner.Context {
        @Override
        public LocalPlayer player() {
            return mc.player;
        }

        @Override
        public ClientLevel level() {
            return mc.level;
        }

        @Override
        public LivingEntity target() {
            return AutoBed.this.target;
        }

        @Override
        public Vec3 resolvePredictedPosition(LivingEntity entity, int ticks) {
            return AutoBed.this.resolvePredictedPosition(entity, ticks);
        }

        @Override
        public BlockHitResult getPlaceInteractResult(BlockPos footPos, BlockPos headPos) {
            return AutoBed.this.getPlaceInteractResult(footPos, headPos);
        }

        @Override
        public BlockHitResult getBedInteractResult(BlockPos pos) {
            return AutoBed.this.getBedInteractResult(pos);
        }

        @Override
        public boolean isBlockedByEntity(BlockPos footPos, BlockPos headPos) {
            return CombatEntityQuery.isBlocked(mc.level, new AABB(footPos).minmax(new AABB(headPos)));
        }

        @Override
        public boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage) {
            return AutoBed.this.shouldOverrideMaxSelfDamage(damage, selfDamage);
        }

        @Override
        public boolean isSafe(float damage, float selfDamage, boolean overrideDamage) {
            return AutoBed.this.isSafe(damage, selfDamage, overrideDamage);
        }

        @Override
        public float minDamage() {
            return minDamage.get();
        }

        @Override
        public float faceplaceHealth() {
            return faceplaceHealth.get();
        }

        @Override
        public float maxSelfDamage() {
            return maxSelfDamage.get();
        }

        @Override
        public int predictTicks() {
            return predictTicks.get();
        }

        @Override
        public int selfPredictTicks() {
            return selfPredictTicks.get();
        }

        @Override
        public float placeRange() {
            return placeRange.get();
        }
    }

    private enum Timing {
        NORMAL,
        SEQUENTIAL
    }

    private enum RenderMode {
        FADE,
        SLIDE,
        DEFAULT
    }
}
