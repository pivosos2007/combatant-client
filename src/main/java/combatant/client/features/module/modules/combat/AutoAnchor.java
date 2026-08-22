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
import combatant.client.features.module.modules.combat.autoanchor.*;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@ModuleInfo(
        id = "autoanchor",
        displayName = "AutoAnchor",
        aliases = {"AnchorAura", "Anchor"},
        category = ModuleCategory.COMBAT,
        description = "module.autoanchor.description"
)
public class AutoAnchor extends Module {
    private static final int ROTATION_PRIORITY = 34;
    private static final long DAMAGE_RESET_MS = 1000L;
    private static final long PENDING_ANCHOR_PLACE_CONFIRM_TIMEOUT_MS = 2500L;
    private static final long PENDING_ANCHOR_ACTION_CONFIRM_TIMEOUT_MS = 750L;
    private static final double TEXT_Y_OFFSET = 0.10;
    private static final double TEXT_WORLD_SCALE = 0.025;
    private static final double TEXT_SCALE = 1.0;
    private static final double TEXT_GAP_SCALE = 1.0;
    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue placeEnabled = boolCommon("autoanchorPlace", CommonSettingSchemas.PLACE, true);
    private final BooleanValue explodeEnabled = boolCommon("autoanchorExplode", CommonSettingSchemas.EXPLODE, true);
    private final EnumValue<Timing> timing = enumCommon("autoanchorTiming", "timing", CommonSettingSchemas.TIMING, Timing.NORMAL);
    private final NumberValue<Integer> calcDelay = numCommon("autoanchorCalcDelay", "calc_delay", CommonSettingSchemas.CALC_DELAY, 100, 0, 500);
    private final NumberValue<Integer> placeDelay = numCommon(
            "autoanchorPlaceDelay",
            "place_delay",
            CommonSettingSchemas.PLACEMENT_DELAY,
            0,
            0,
            1000
    );
    private final NumberValue<Integer> lowPlaceDelay = numCommon("autoanchorLowPlaceDelay", "low_place_delay", CommonSettingSchemas.LOW_PLACE_DELAY, 550, 0, 1000);
    private final NumberValue<Integer> explodeDelay = numCommon(
            "autoanchorExplodeDelay",
            "explode_delay",
            CommonSettingSchemas.PLACEMENT_BREAK_DELAY,
            0,
            0,
            1000
    );
    private final NumberValue<Integer> lowExplodeDelay = numCommon("autoanchorLowExplodeDelay", "low_explode_delay", CommonSettingSchemas.LOW_EXPLODE_DELAY, 550, 0, 1000);
    private final NumberValue<Float> placeRange = numCommon(
            "autoanchorPlaceRange",
            "place_range",
            CommonSettingSchemas.PLACEMENT_RANGE,
            5.0f,
            1.0f,
            6.0f
    );
    private final NumberValue<Float> wallRange = numCommon(
            "autoanchorWallRange",
            "wall_range",
            CommonSettingSchemas.PLACEMENT_WALL_RANGE,
            3.5f,
            0.0f,
            6.0f
    );
    private final EnumValue<AutoAnchorPlacementMode> placementMode = enumCommon(
            "autoanchorPlacementMode",
            "placement_mode",
            CommonSettingSchemas.PLACEMENT_MODE,
            AutoAnchorPlacementMode.DEFAULT,
            AutoAnchorPlacementMode.values()
    );
    private final BooleanValue airPlace = boolCommon("autoanchorAirPlace", "air_place", CommonSettingSchemas.AIR_PLACE, false);
    private final NumberValue<Float> targetRange = numCommon(
            "autoanchorTargetRange",
            "target_range",
            CommonSettingSchemas.COMBAT_RANGE,
            10.0f,
            1.0f,
            15.0f
    );
    private final BooleanMapValue targetToggles = groupCommon(
            "autoanchorTargets",
            "targets",
            CommonSettingSchemas.TARGET_FILTERS
    );
    private final EnumValue<TargetingUtil.TargetPriority> priority = enumCommon(
            "autoanchorPriority",
            "priority",
            CommonSettingSchemas.COMBAT_PRIORITY,
            TargetingUtil.TargetPriority.DISTANCE,
            TargetingUtil.TargetPriority.values()
    );
    private final NumberValue<Float> minDamage = numCommon("autoanchorMinDamage", "min_damage", CommonSettingSchemas.MIN_DAMAGE, 6.0f, 0.0f, 36.0f);
    private final NumberValue<Float> faceplaceHealth = numCommon("autoanchorFaceplaceHealth", "faceplace_health", CommonSettingSchemas.FACEPLACE_HEALTH, 5.0f, 0.0f, 36.0f);
    private final NumberValue<Float> maxSelfDamage = numCommon("autoanchorMaxSelfDamage", "max_self_damage", CommonSettingSchemas.MAX_SELF_DAMAGE, 10.0f, 0.0f, 36.0f);
    private final NumberValue<Integer> predictTicks = numCommon(
            "autoanchorPredictTicks",
            "predict_ticks",
            CommonSettingSchemas.PLACEMENT_PREDICTION,
            3,
            0,
            20
    );
    private final NumberValue<Integer> selfPredictTicks = numCommon("autoanchorSelfPredictTicks", "self_predict_ticks", CommonSettingSchemas.SELF_PREDICT_TICKS, 3, 0, 20);
    private final BooleanValue unsafeDimensionOnly = boolCommon("autoanchorUnsafeDimensionOnly", "unsafe_dimension_only", CommonSettingSchemas.UNSAFE_DIMENSION_ONLY, true);
    private final BooleanValue pauseMining = boolCommon("autoanchorPauseMining", "pause_mining", CommonSettingSchemas.PAUSE_MINING, true);
    private final BooleanValue pauseEating = boolCommon("autoanchorPauseEating", "pause_eating", CommonSettingSchemas.PAUSE_EATING, true);
    private final NumberValue<Float> pauseHealth = numCommon(
            "autoanchorPauseHealth",
            "pause_health",
            CommonSettingSchemas.PLAYER_HEALTH_THRESHOLD,
            8.0f,
            0.0f,
            20.0f
    );
    private final EnumValue<InventorySearchScope> swapScope = enumCommon(
            "autoanchorSwapScope",
            "swap_scope",
            CommonSettingSchemas.INVENTORY_SEARCH_SCOPE,
            InventorySearchScope.FULL,
            InventorySearchScope.values()
    );
    private final EnumValue<InventorySwapVisibility> swapVisibility = enumCommon(
            "autoanchorSwapVisibility",
            "swap_visibility",
            CommonSettingSchemas.INVENTORY_SWAP_VISIBILITY,
            InventorySwapVisibility.SILENT,
            InventorySwapVisibility.values()
    );
    private final BooleanValue restoreItem = boolCommon(
            "autoanchorRestoreItem",
            "restore_item",
            CommonSettingSchemas.INVENTORY_RESTORE_ITEM,
            true
    );
    private final ModeValue attackMode = modeCommon(
            "autoanchorAttackMode",
            "attack_mode",
            CommonSettingSchemas.COMBAT_ATTACK_MODE,
            CombatRotationModeUtil.MODE_ROTATIONS,
            CombatRotationModeUtil.MODE_ROTATIONS,
            CombatRotationModeUtil.MODE_NO_ROTATIONS
    );
    private final EnumValue<MovementCorrection> movementCorrection = visibleWhen(enumCommon(
            "autoanchorMovementCorrection",
            "movement_correction",
            CommonSettingSchemas.PLACEMENT_MOVEMENT_CORRECTION,
            MovementCorrection.SILENT,
            MovementCorrection.values()
    ), this::usesRotationSettings);
    private final NumberValue<Integer> rotationResetTicks = visibleWhen(numCommon(
            "autoanchorRotationResetTicks",
            "rotation_reset_ticks",
            CommonSettingSchemas.ROTATION_RESET_TICKS,
            1,
            1,
            20
    ), this::usesRotationSettings);
    private final NumberValue<Float> rotationResetThreshold = visibleWhen(numCommon(
            "autoanchorRotationResetThreshold",
            "rotation_reset_threshold",
            CommonSettingSchemas.ROTATION_RESET_THRESHOLD,
            2.0f,
            0.1f,
            15.0f
    ), this::usesRotationSettings);
    private final BooleanValue renderEnabled = boolCommon(
            "autoanchorRender",
            "render",
            CommonSettingSchemas.PLACEMENT_RENDER,
            true
    );
    private final EnumValue<RenderMode> renderMode = enumCommon(
            "autoanchorRenderMode",
            "render_mode",
            CommonSettingSchemas.PLACEMENT_RENDER_MODE,
            RenderMode.FADE,
            RenderMode.values()
    );
    private final BooleanValue renderSelfDamage = boolCommon("autoanchorRenderSelfDamage", CommonSettingSchemas.RENDER_SELF_DAMAGE, true);
    private final BooleanValue drawDamage = boolCommon("autoanchorRenderDamage", CommonSettingSchemas.RENDER_DAMAGE, true);
    private final RGBAColorValue fillColor = common(color("autoanchorFillColor", "#5A285AFF"), CommonSettingSchemas.FILL_COLOR);
    private final RGBAColorValue lineColor = common(color("autoanchorLineColor", "#FFAA55FF"), CommonSettingSchemas.LINE_COLOR);
    private final NumberValue<Float> lineWidth = numCommon("autoanchorLineWidth", CommonSettingSchemas.LINE_WIDTH, 2.0f, 1.0f, 6.0f);
    private final RGBAColorValue textColor = common(color("autoanchorTextColor", "#FFFFFFFF"), CommonSettingSchemas.TEXT_COLOR);
    private final NumberValue<Integer> slideDelay = visibleWhen(numCommon("autoanchorSlideDelay", CommonSettingSchemas.SLIDE_DELAY, 200, 1, 1000),
            () -> renderMode.get() == RenderMode.SLIDE);
    private final NumberValue<Integer> fadeTime = visibleWhen(numCommon("autoanchorFadeTime", CommonSettingSchemas.FADE_TIME, 500, 100, 2000),
            () -> renderMode.get() == RenderMode.FADE);

    private final AutoAnchorPlanner planner = new AutoAnchorPlanner();
    private final AutoAnchorPlanner.Context plannerContext = new PlannerContext();
    private final Map<BlockPos, Long> renderPositions = new ConcurrentHashMap<>();

    private LivingEntity target;
    private AutoAnchorData bestPlace;
    private AutoAnchorData bestExplode;
    private BlockPos renderPos;
    private BlockPos prevRenderPos;
    private BlockPos bestPosition;
    private int renderTick;
    private int renderStartTick;
    private long lastPlaceMs;
    private long lastExplodeMs;
    private long lastDamageUpdateMs;
    private long lastCalcMs;
    private BlockPos pendingAnchorPos;
    private AutoAnchorData pendingAnchorData;
    private PendingAnchorPhase pendingAnchorPhase;
    private long pendingAnchorActionMs;
    private float renderDamage;
    private float renderSelfDamageValue;
    private boolean lowDamageMode;
    private int lastScanned;
    private int lastCandidates;
    private int lastSafeCandidates;
    private int lastSelfDamageRejects;

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        RotationManager.INSTANCE.clear(this);
        TargetManager.setAutoAnchorTarget(null);
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

    @EventHandler(priority = 24)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) {
            return;
        }
        if (!isEnabled()) return;

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            clearCombatState();
            TargetManager.setAutoAnchorTarget(null);
            return;
        }

        target = findCombatTarget();
        TargetManager.setAutoAnchorTarget(target);
        updateCandidates();

        if (timing.get() == Timing.NORMAL) {
            runCombatCycle();
        }
    }

    @EventHandler(priority = -24)
    private void onRotationUpdatePost(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.POST) {
            return;
        }
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

        AutoAnchorPlanner.ScanResult result = planner.scan(
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

        AutoAnchorData renderData = bestExplode != null ? bestExplode : bestPlace;
        if (renderData != null) {
            setRenderCandidate(renderData);
            lowDamageMode = !ExplosionDamageRules.shouldOverrideMinDamage(target, renderData.damage(), faceplaceHealth.get())
                    && renderData.damage() < minDamage.get();
        } else if (renderPositions.isEmpty()) {
            bestPosition = null;
        }
    }

    private void runCombatCycle() {
        if (mc.player == null || mc.level == null || shouldPause()) {
            RotationManager.INSTANCE.clear(this);
            return;
        }

        if (continuePendingAnchorCycle()) {
            return;
        }

        if (target == null) {
            RotationManager.INSTANCE.clear(this);
            return;
        }

        if (explodeEnabled.get() && bestExplode != null && hasExplodeDelayElapsed()) {
            beginPendingAnchor(bestExplode, bestExplode.charges() > 0
                    ? PendingAnchorPhase.DETONATING
                    : PendingAnchorPhase.CHARGING);
            continuePendingAnchorCycle();
            return;
        }

        if (placeEnabled.get() && bestPlace != null && hasPlaceDelayElapsed()) {
            beginPendingAnchor(bestPlace, PendingAnchorPhase.PLACING);
            continuePendingAnchorCycle();
            return;
        }

        RotationManager.INSTANCE.clear(this);
    }

    private void executeWithRotation(AutoAnchorData data, Runnable action) {
        if (data == null || action == null || mc.player == null) return;
        if (!usesRotationSettings()) {
            RotationManager.INSTANCE.clear(this);
            action.run();
            return;
        }

        RotationTarget rotationTarget = new RotationTarget(
                Rotation.lookingAt(data.hitResult().getLocation(), mc.player.getEyePosition()).normalize(),
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

    private void placeReservedAnchor(AutoAnchorData data) {
        if (data == null || data.pos() == null || data.existingAnchor() || !hasPlaceDelayElapsed() || shouldPause()) {
            return;
        }
        if (!data.pos().equals(pendingAnchorPos) || pendingAnchorPhase != PendingAnchorPhase.PLACING) {
            return;
        }
        if (target == null) {
            clearPendingAnchor();
            return;
        }

        AutoAnchorData refreshed = planner.evaluate(plannerContext, data.pos(), target);
        if (refreshed == null) {
            clearPendingAnchor();
            return;
        }
        if (refreshed.existingAnchor()) {
            pendingAnchorData = refreshed;
            pendingAnchorPhase = refreshed.charges() > 0 ? PendingAnchorPhase.DETONATING : PendingAnchorPhase.CHARGING;
            pendingAnchorActionMs = 0L;
            return;
        }

        boolean placed = AutoAnchorActionUtil.placeAnchor(
                mc,
                this,
                refreshed.hitResult(),
                swapScope.get(),
                swapVisibility.get(),
                restoreItem.get()
        );
        if (!placed) return;

        long now = System.currentTimeMillis();
        lastPlaceMs = now;
        pendingAnchorActionMs = now;
        pendingAnchorData = refreshed;
        pendingAnchorPhase = PendingAnchorPhase.WAITING_FOR_ANCHOR;
        markPlaced(refreshed);
    }

    private boolean continuePendingAnchorCycle() {
        if (pendingAnchorPos == null || pendingAnchorPhase == null) {
            return false;
        }
        if (mc.player == null || mc.level == null) {
            clearPendingAnchor();
            return false;
        }
        if (shouldPause()) {
            RotationManager.INSTANCE.clear(this);
            return true;
        }

        long now = System.currentTimeMillis();
        BlockState state = mc.level.getBlockState(pendingAnchorPos);
        boolean anchorPresent = state.is(Blocks.RESPAWN_ANCHOR);
        int charges = anchorPresent ? getAnchorCharges(state) : 0;

        if (anchorPresent && (pendingAnchorPhase == PendingAnchorPhase.PLACING
                || pendingAnchorPhase == PendingAnchorPhase.WAITING_FOR_ANCHOR)) {
            pendingAnchorPhase = charges > 0 ? PendingAnchorPhase.DETONATING : PendingAnchorPhase.CHARGING;
            pendingAnchorActionMs = 0L;
        }

        if (!anchorPresent && (pendingAnchorPhase == PendingAnchorPhase.CHARGING
                || pendingAnchorPhase == PendingAnchorPhase.WAITING_FOR_CHARGE
                || pendingAnchorPhase == PendingAnchorPhase.DETONATING
                || pendingAnchorPhase == PendingAnchorPhase.WAITING_FOR_DETONATION)) {
            clearPendingAnchor();
            return false;
        }

        switch (pendingAnchorPhase) {
            case PLACING -> {
                AutoAnchorData data = resolvePendingPlaceData();
                if (data == null) {
                    clearPendingAnchor();
                    return false;
                }
                pendingAnchorData = data;
                if (hasPlaceDelayElapsed()) {
                    executeWithRotation(data, () -> placeReservedAnchor(data));
                } else {
                    RotationManager.INSTANCE.clear(this);
                }
                return true;
            }
            case WAITING_FOR_ANCHOR -> {
                if (anchorPresent) {
                    pendingAnchorPhase = charges > 0 ? PendingAnchorPhase.DETONATING : PendingAnchorPhase.CHARGING;
                    pendingAnchorActionMs = 0L;
                    return continuePendingAnchorCycle();
                }
                if (now - pendingAnchorActionMs >= PENDING_ANCHOR_PLACE_CONFIRM_TIMEOUT_MS) {
                    clearPendingAnchor();
                    return false;
                }
                RotationManager.INSTANCE.clear(this);
                return true;
            }
            case CHARGING -> {
                if (!anchorPresent) {
                    clearPendingAnchor();
                    return false;
                }
                AutoAnchorData data = resolvePendingAnchorData(pendingAnchorPos, state);
                if (data == null) {
                    RotationManager.INSTANCE.clear(this);
                    return true;
                }
                pendingAnchorData = data;
                if (data.charges() > 0) {
                    pendingAnchorPhase = PendingAnchorPhase.DETONATING;
                    pendingAnchorActionMs = 0L;
                    return continuePendingAnchorCycle();
                }
                if (hasExplodeDelayElapsed()) {
                    executeWithRotation(data, () -> chargeReservedAnchor(data));
                } else {
                    RotationManager.INSTANCE.clear(this);
                }
                return true;
            }
            case WAITING_FOR_CHARGE -> {
                if (!anchorPresent) {
                    clearPendingAnchor();
                    return false;
                }
                if (charges > 0) {
                    pendingAnchorPhase = PendingAnchorPhase.DETONATING;
                    pendingAnchorActionMs = 0L;
                    return continuePendingAnchorCycle();
                }
                if (now - pendingAnchorActionMs >= PENDING_ANCHOR_ACTION_CONFIRM_TIMEOUT_MS) {
                    pendingAnchorPhase = PendingAnchorPhase.CHARGING;
                    pendingAnchorActionMs = 0L;
                    return continuePendingAnchorCycle();
                }
                RotationManager.INSTANCE.clear(this);
                return true;
            }
            case DETONATING -> {
                if (!anchorPresent) {
                    clearPendingAnchor();
                    return false;
                }
                AutoAnchorData data = resolvePendingAnchorData(pendingAnchorPos, state);
                if (data == null) {
                    RotationManager.INSTANCE.clear(this);
                    return true;
                }
                pendingAnchorData = data;
                if (data.charges() <= 0) {
                    pendingAnchorPhase = PendingAnchorPhase.CHARGING;
                    pendingAnchorActionMs = 0L;
                    return continuePendingAnchorCycle();
                }
                if (explodeEnabled.get() && hasExplodeDelayElapsed()) {
                    executeWithRotation(data, () -> detonateReservedAnchor(data));
                } else {
                    RotationManager.INSTANCE.clear(this);
                }
                return true;
            }
            case WAITING_FOR_DETONATION -> {
                if (!anchorPresent) {
                    clearPendingAnchor();
                    return false;
                }
                if (now - pendingAnchorActionMs >= PENDING_ANCHOR_ACTION_CONFIRM_TIMEOUT_MS) {
                    pendingAnchorPhase = PendingAnchorPhase.DETONATING;
                    pendingAnchorActionMs = 0L;
                    return continuePendingAnchorCycle();
                }
                RotationManager.INSTANCE.clear(this);
                return true;
            }
        }
        return true;
    }

    private AutoAnchorData resolvePendingPlaceData() {
        if (pendingAnchorPos == null || target == null) {
            return null;
        }
        AutoAnchorData evaluated = planner.evaluate(plannerContext, pendingAnchorPos, target);
        if (evaluated != null) {
            return evaluated;
        }
        return pendingAnchorData != null && pendingAnchorPos.equals(pendingAnchorData.pos())
                ? pendingAnchorData
                : null;
    }

    private AutoAnchorData resolvePendingAnchorData(BlockPos pos, BlockState state) {
        if (pos == null || state == null || mc.player == null || mc.level == null) {
            return null;
        }

        AutoAnchorData evaluated = target != null ? planner.evaluate(plannerContext, pos, target) : null;
        if (evaluated != null && evaluated.existingAnchor()) {
            return evaluated;
        }

        BlockHitResult hit = AutoAnchorInteractionUtil.getAnchorInteractResult(
                mc.level,
                mc.player,
                pos,
                placementMode.get(),
                placeRange.get(),
                wallRange.get()
        );
        if (hit == null) {
            return null;
        }

        AutoAnchorData fallback = pendingAnchorData != null && pos.equals(pendingAnchorData.pos()) ? pendingAnchorData : null;
        return new AutoAnchorData(
                pos,
                hit,
                fallback != null ? fallback.damage() : renderDamage,
                fallback != null ? fallback.selfDamage() : renderSelfDamageValue,
                fallback != null && fallback.overrideDamage(),
                true,
                getAnchorCharges(state)
        );
    }

    private void chargeReservedAnchor(AutoAnchorData data) {
        if (data == null || data.pos() == null || !hasExplodeDelayElapsed() || shouldPause()) return;
        if (!data.pos().equals(pendingAnchorPos) || pendingAnchorPhase != PendingAnchorPhase.CHARGING) return;

        BlockHitResult anchorHit = AutoAnchorInteractionUtil.getAnchorInteractResult(
                mc.level,
                mc.player,
                data.pos(),
                placementMode.get(),
                placeRange.get(),
                wallRange.get()
        );
        if (anchorHit == null) return;

        boolean charged = AutoAnchorActionUtil.chargeAnchor(
                mc,
                anchorHit,
                swapScope.get(),
                swapVisibility.get(),
                restoreItem.get()
        );
        if (!charged) return;

        long now = System.currentTimeMillis();
        lastExplodeMs = now;
        pendingAnchorActionMs = now;
        pendingAnchorPhase = PendingAnchorPhase.WAITING_FOR_CHARGE;
        pendingAnchorData = new AutoAnchorData(
                data.pos(),
                anchorHit,
                data.damage(),
                data.selfDamage(),
                data.overrideDamage(),
                true,
                Math.max(1, data.charges() + 1)
        );
        markPlaced(pendingAnchorData);
    }

    private void detonateReservedAnchor(AutoAnchorData data) {
        if (data == null || !hasExplodeDelayElapsed() || shouldPause()) return;
        if (!data.pos().equals(pendingAnchorPos) || pendingAnchorPhase != PendingAnchorPhase.DETONATING) return;

        BlockHitResult anchorHit = AutoAnchorInteractionUtil.getAnchorInteractResult(
                mc.level,
                mc.player,
                data.pos(),
                placementMode.get(),
                placeRange.get(),
                wallRange.get()
        );
        if (anchorHit == null) return;

        boolean detonated = AutoAnchorActionUtil.detonateAnchor(
                mc,
                anchorHit,
                swapScope.get(),
                swapVisibility.get(),
                restoreItem.get()
        );
        if (!detonated) return;

        long now = System.currentTimeMillis();
        lastExplodeMs = now;
        pendingAnchorActionMs = now;
        pendingAnchorPhase = PendingAnchorPhase.WAITING_FOR_DETONATION;
        pendingAnchorData = data;
        markPlaced(data);
    }

    private void beginPendingAnchor(AutoAnchorData data, PendingAnchorPhase phase) {
        if (data == null || data.pos() == null || phase == null) return;
        if (pendingAnchorPos != null && !pendingAnchorPos.equals(data.pos())) {
            return;
        }
        if (pendingAnchorPos == null) {
            pendingAnchorActionMs = 0L;
        }
        pendingAnchorPos = data.pos();
        pendingAnchorData = data;
        pendingAnchorPhase = phase;
    }


    private int getAnchorCharges(BlockState state) {
        if (state == null || !state.hasProperty(RespawnAnchorBlock.CHARGE)) {
            return 0;
        }
        return state.getValue(RespawnAnchorBlock.CHARGE);
    }

    private void clearPendingAnchor() {
        pendingAnchorPos = null;
        pendingAnchorData = null;
        pendingAnchorPhase = null;
        pendingAnchorActionMs = 0L;
    }

    private boolean shouldPause() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return true;
        if (unsafeDimensionOnly.get() && mc.level.dimension() == Level.NETHER) return true;
        if (pauseMining.get() && mc.gameMode.isDestroying()) return true;
        if (pauseEating.get()
                && mc.player.isUsingItem()
                && FoodUtil.isFood(mc.player.getUseItem())
                && !RubberHandUseUtil.canBypassCurrentUse(mc)) return true;
        return mc.player.getHealth() + mc.player.getAbsorptionAmount() < pauseHealth.get();
    }

    private boolean hasPlaceDelayElapsed() {
        return System.currentTimeMillis() - lastPlaceMs >= Math.max(0, lowDamageMode ? lowPlaceDelay.get() : placeDelay.get());
    }

    private boolean hasExplodeDelayElapsed() {
        return System.currentTimeMillis() - lastExplodeMs >= Math.max(0, lowDamageMode ? lowExplodeDelay.get() : explodeDelay.get());
    }

    private long getPlaceDelayRemainingMs() {
        return Math.max(0L, Math.max(0, lowDamageMode ? lowPlaceDelay.get() : placeDelay.get()) - (System.currentTimeMillis() - lastPlaceMs));
    }

    private long getExplodeDelayRemainingMs() {
        return Math.max(0L, Math.max(0, lowDamageMode ? lowExplodeDelay.get() : explodeDelay.get()) - (System.currentTimeMillis() - lastExplodeMs));
    }

    private void setRenderCandidate(AutoAnchorData data) {
        bestPosition = data.pos();
        renderDamage = Math.max(0.0f, data.damage());
        renderSelfDamageValue = Math.max(0.0f, data.selfDamage());
        lastDamageUpdateMs = System.currentTimeMillis();
    }

    private void markPlaced(AutoAnchorData data) {
        if (data == null || data.pos() == null) return;
        BlockPos pos = data.pos();
        if (!pos.equals(renderPos)) {
            renderStartTick = renderTick;
            prevRenderPos = renderPos;
            renderPos = pos;
        }
        setRenderCandidate(data);
        renderPositions.put(pos, System.currentTimeMillis());
    }

    private void renderPlacement(Renderer3D renderer, float tickDelta) {
        String damageText = buildDamageText();
        if (damageText.isEmpty() && renderPositions.isEmpty() && renderPos == null) {
            return;
        }

        if (renderMode.get() == RenderMode.FADE) {
            renderFade(renderer);
            return;
        }

        BlockPos activePos = renderPositions.isEmpty() ? bestPosition : renderPos;
        if (activePos == null) return;

        if (renderMode.get() == RenderMode.SLIDE) {
            BlockPos slideTo = renderPositions.isEmpty() ? bestPosition : renderPos;
            BlockPos from = prevRenderPos != null ? prevRenderPos : slideTo;
            renderBox(renderer, ExplosionRenderUtil.lerpBox(new AABB(from), new AABB(slideTo), getSlideProgress(tickDelta)), 1.0f);
        } else {
            renderBox(renderer, new AABB(activePos), 1.0f);
        }
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
        if (!isEnabled() || hudTarget == null || target == null || hudTarget != target) return null;
        if (mc.player == null || mc.level == null) return "Idle";
        if (shouldPause()) return "Paused";
        if (!AutoAnchorActionUtil.hasAnchor(mc, swapScope.get())) return "No anchors";
        if (!AutoAnchorActionUtil.hasGlowstone(mc, swapScope.get())) return "No glowstone";

        if (explodeEnabled.get() && bestExplode != null) {
            long wait = getExplodeDelayRemainingMs();
            if (wait > 0L) return "Explode in " + wait + "ms";
            return usesRotationSettings() ? "Rotate -> anchor" : "Exploding";
        }
        if (placeEnabled.get() && bestPlace != null) {
            long wait = getPlaceDelayRemainingMs();
            if (wait > 0L) return "Anchor in " + wait + "ms";
            return usesRotationSettings() ? "Rotate -> anchor" : "Placing anchor";
        }
        if (lastSelfDamageRejects > 0 || (lastCandidates > 0 && lastSafeCandidates <= 0)) return "Self dmg";
        if (lastScanned <= 0) return "No scan";
        return "No anchor";
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

    private BlockHitResult getInteractResult(BlockPos pos, boolean existingAnchor) {
        return AutoAnchorInteractionUtil.getInteractResult(
                mc.level,
                mc.player,
                pos,
                existingAnchor,
                airPlace.get(),
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
        bestPosition = null;
        clearPendingAnchor();
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
        renderPos = null;
        prevRenderPos = null;
        bestPosition = null;
        renderTick = 0;
        renderStartTick = 0;
        lastPlaceMs = 0L;
        lastExplodeMs = 0L;
        lastDamageUpdateMs = 0L;
        lastCalcMs = 0L;
        clearPendingAnchor();
        renderDamage = 0.0f;
        renderSelfDamageValue = 0.0f;
        lowDamageMode = false;
        renderPositions.clear();
        lastScanned = 0;
        lastCandidates = 0;
        lastSafeCandidates = 0;
        lastSelfDamageRejects = 0;
    }

    private final class PlannerContext implements AutoAnchorPlanner.Context {
        @Override
        public LocalPlayer player() {
            return mc.player;
        }

        @Override
        public ClientLevel level() {
            return mc.level;
        }

        @Override
        public Vec3 resolvePredictedPosition(LivingEntity entity, int ticks) {
            return AutoAnchor.this.resolvePredictedPosition(entity, ticks);
        }

        @Override
        public BlockHitResult getInteractResult(BlockPos pos, boolean existingAnchor) {
            return AutoAnchor.this.getInteractResult(pos, existingAnchor);
        }

        @Override
        public boolean isBlockedByEntity(BlockPos pos) {
            return CombatEntityQuery.isBlocked(mc.level, new AABB(pos));
        }

        @Override
        public boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage) {
            return AutoAnchor.this.shouldOverrideMaxSelfDamage(damage, selfDamage);
        }

        @Override
        public boolean isSafe(float damage, float selfDamage, boolean overrideDamage) {
            return AutoAnchor.this.isSafe(damage, selfDamage, overrideDamage);
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
        public boolean allowAirPlace() {
            return airPlace.get();
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

    private enum PendingAnchorPhase {
        PLACING,
        WAITING_FOR_ANCHOR,
        CHARGING,
        WAITING_FOR_CHARGE,
        DETONATING,
        WAITING_FOR_DETONATION
    }

    private enum RenderMode {
        FADE,
        SLIDE,
        DEFAULT
    }
}
