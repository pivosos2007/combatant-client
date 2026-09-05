/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.*;
import combatant.client.features.module.modules.combat.autocrystal.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.common.impl.TargetFilters;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.WorldTextRenderer;
import combatant.client.render.engine.world.WorldUiPresentationService;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.data.RotationWithVector;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.raytrace.RotationRaytrace;
import combatant.client.util.combat.CombatRotationModeUtil;
import combatant.client.util.combat.CombatBlockUseUtil;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.entity.simulation.PositionExtrapolation;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.simulation.PlayerSimulationCache;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.TargetingUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//todo Description
@ModuleInfo(
        id = "autocrystal",
        displayName = "AutoCrystal",
        aliases = "CrystalAura",
        category = ModuleCategory.COMBAT
)
public class AutoCrystal extends Module {

    private static final int ROTATION_PRIORITY = 35;
    private static final int HOTBAR_RESET_TICKS = 2;
    private static final String ACTION_BASE = "base";

    private static final double INTERACT_MARKER_RADIUS = 0.05;
    private static final long DAMAGE_RESET_MS = 1000L;
    private static final int BASE_HOTBAR_RESET_TICKS = 2;
    private static final float BASE_INITIATIVE_MIN_DAMAGE = 1.5f;
    private static final double TEXT_Y_OFFSET = 0.10;
    private static final double TEXT_WORLD_SCALE = 0.025;
    private static final double TEXT_SCALE = 1.0;
    private static final double TEXT_GAP_SCALE = 1.0;
    private static final double TARGET_SEARCH_RANGE = 12.0;

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue renderEnabled =
            boolCommon(
                    "autocrystalRender",
                    "render",
                    CommonSettingSchemas.PLACEMENT_RENDER,
                    true
            );
    private final EnumValue<RenderMode> renderMode =
            enumCommon(
                    "autocrystalRenderMode",
                    "render_mode",
                    CommonSettingSchemas.PLACEMENT_RENDER_MODE,
                    RenderMode.FADE,
                    RenderMode.values()
            );
    private final BooleanValue renderSelfDamage = boolCommon("autocrystalRenderSelfDamage", CommonSettingSchemas.RENDER_SELF_DAMAGE, true);
    private final BooleanValue drawDamage = boolCommon("autocrystalRenderDamage", CommonSettingSchemas.RENDER_DAMAGE, true);
    private final BooleanValue placeEnabled = boolCommon("autocrystalPlace", CommonSettingSchemas.PLACE, true);
    private final NumberValue<Integer> placeDelay =
            visibleWhen(numCommon(
                    "autocrystalPlaceDelay",
                    "place_delay",
                    CommonSettingSchemas.PLACEMENT_DELAY,
                    0,
                    0,
                    1000
            ), placeEnabled::get);
    private final BooleanValue breakEnabled = boolCommon("autocrystalBreak", CommonSettingSchemas.BREAK, true);
    private final NumberValue<Integer> breakDelay =
            visibleWhen(numCommon(
                    "autocrystalBreakDelay",
                    "break_delay",
                    CommonSettingSchemas.PLACEMENT_BREAK_DELAY,
                    0,
                    0,
                    1000
            ), breakEnabled::get);
    private final NumberValue<Float> placeRange =
            numCommon(
                    "autocrystalPlaceRange",
                    "place_range",
                    CommonSettingSchemas.PLACEMENT_RANGE,
                    5.0f,
                    1.0f,
                    6.0f
            );
    private final EnumValue<AutoCrystalPlacementMode> placementMode =
            visibleWhen(enumCommon(
                    "autocrystalPlacementMode",
                    "placement_mode",
                    CommonSettingSchemas.PLACEMENT_MODE,
                    AutoCrystalPlacementMode.DEFAULT,
                    AutoCrystalPlacementMode.values()
            ), placeEnabled::get);
    private final NumberValue<Float> wallRange =
            visibleWhen(numCommon(
                    "autocrystalWallRange",
                    "wall_range",
                    CommonSettingSchemas.PLACEMENT_WALL_RANGE,
                    5.0f,
                    0.0f,
                    6.0f
            ), () -> placeEnabled.get() && placementMode.get() == AutoCrystalPlacementMode.DEFAULT);
    private final NumberValue<Float> breakRange =
            numCommon(
                    "autocrystalBreakRange",
                    "break_range",
                    CommonSettingSchemas.PLACEMENT_BREAK_RANGE,
                    5.0f,
                    1.0f,
                    6.0f
            );
    private final NumberValue<Float> breakWallRange =
            numCommon(
                    "autocrystalBreakWallRange",
                    "break_wall_range",
                    CommonSettingSchemas.PLACEMENT_BREAK_WALL_RANGE,
                    5.0f,
                    0.0f,
                    6.0f
            );
    private final BooleanMapValue targetToggles = groupCommon(
            "autocrystalTargets",
            "targets",
            CommonSettingSchemas.TARGET_FILTERS
    );
    private final EnumValue<TargetingUtil.TargetPriority> priority =
            enumCommon(
                    "autocrystalPriority",
                    "priority",
                    CommonSettingSchemas.COMBAT_PRIORITY,
                    TargetingUtil.TargetPriority.DISTANCE,
                    TargetingUtil.TargetPriority.values()
            );
    private final NumberValue<Float> minDamage =
            numCommon("autocrystalMinDamage", CommonSettingSchemas.MIN_DAMAGE, 4.0f, 0.0f, 36.0f);
    private final NumberValue<Float> faceplaceHealth =
            numCommon("autocrystalFaceplaceHealth", CommonSettingSchemas.FACEPLACE_HEALTH, 10.0f, 0.0f, 36.0f);
    private final NumberValue<Float> maxSelfDamage =
            numCommon("autocrystalMaxSelfDamage", CommonSettingSchemas.MAX_SELF_DAMAGE, 8.0f, 0.0f, 36.0f);
    private final NumberValue<Integer> confirmTime =
            visibleWhen(num("autocrystalConfirmTime", 150, 10, 2000), placeEnabled::get);
    private final NumberValue<Integer> predictTicks =
            numCommon("autocrystalPredictTicks", CommonSettingSchemas.PLACEMENT_PREDICTION, 0, 0, 20);
    private final NumberValue<Integer> selfPredictTicks =
            numCommon("autocrystalSelfPredictTicks", CommonSettingSchemas.SELF_PREDICT_TICKS, 0, 0, 20);
    private final BooleanValue ignoreTerrain =
            bool("autocrystalIgnoreTerrain", true);
    private final ModeValue attackMode =
            modeCommon(
                    "autocrystalAttackMode",
                    "attack_mode",
                    CommonSettingSchemas.COMBAT_ATTACK_MODE,
                    CombatRotationModeUtil.MODE_ROTATIONS,
                    CombatRotationModeUtil.MODE_ROTATIONS,
                    CombatRotationModeUtil.MODE_NO_ROTATIONS
            );
    private final EnumValue<MovementCorrection> movementCorrection =
            visibleWhen(enumCommon(
                    "autocrystalMovementCorrection",
                    "movement_correction",
                    CommonSettingSchemas.PLACEMENT_MOVEMENT_CORRECTION,
                    MovementCorrection.SILENT,
                    MovementCorrection.values()
            ), () -> placeEnabled.get() && usesRotationSettings());
    private final NumberValue<Integer> rotationResetTicks =
            visibleWhen(numCommon(
                    "autocrystalRotationResetTicks",
                    "rotation_reset_ticks",
                    CommonSettingSchemas.ROTATION_RESET_TICKS,
                    1,
                    1,
                    20
            ), () -> placeEnabled.get() && usesRotationSettings());
    private final NumberValue<Float> rotationResetThreshold =
            visibleWhen(numCommon(
                    "autocrystalRotationResetThreshold",
                    "rotation_reset_threshold",
                    CommonSettingSchemas.ROTATION_RESET_THRESHOLD,
                    2.0f,
                    0.1f,
                    15.0f
            ), () -> placeEnabled.get() && usesRotationSettings());
    private final RGBAColorValue fillColor =
            common(color("autocrystalFillColor", "#285A9CFF"), CommonSettingSchemas.FILL_COLOR);
    private final RGBAColorValue lineColor =
            common(color("autocrystalLineColor", "#FF9BE4FF"), CommonSettingSchemas.LINE_COLOR);
    private final NumberValue<Float> lineWidth =
            numCommon("autocrystalLineWidth", CommonSettingSchemas.LINE_WIDTH, 2.0f, 1.0f, 6.0f);
    private final RGBAColorValue textColor =
            common(color("autocrystalTextColor", "#FFFFFFFF"), CommonSettingSchemas.TEXT_COLOR);
    private final NumberValue<Float> billboardSize = visibleWhen(numCommon(
            "autocrystalBillboardSize", "billboard_size", CommonSettingSchemas.BILLBOARD_SIZE, 0.72f, 0.35f, 1.50f),
            drawDamage::get);
    private final BooleanValue billboardDynamicScale = visibleWhen(boolCommon(
            "autocrystalBillboardDynamicScale", "billboard_dynamic_scale", CommonSettingSchemas.BILLBOARD_DYNAMIC_SCALE, true),
            drawDamage::get);
    private final NumberValue<Float> billboardDynamicScaleCoefficient = visibleWhen(numCommon(
            "autocrystalBillboardDynamicScaleCoefficient", "billboard_dynamic_scale_coefficient",
            CommonSettingSchemas.BILLBOARD_DYNAMIC_SCALE_COEFFICIENT, 0.25f, 0.0f, 1.0f),
            () -> drawDamage.get() && billboardDynamicScale.get());
    private final NumberValue<Integer> slideDelay =
            visibleWhen(numCommon("autocrystalSlideDelay", CommonSettingSchemas.SLIDE_DELAY, 200, 1, 1000),
                    () -> renderMode.get() == RenderMode.SLIDE);
    private final NumberValue<Integer> fadeTime =
            visibleWhen(numCommon("autocrystalFadeTime", CommonSettingSchemas.FADE_TIME, 500, 100, 2000),
                    () -> renderMode.get() == RenderMode.FADE);
    private final BooleanValue extrapolationEnabled =
            bool("autocrystalRenderExtrapolation", false);
    private final RGBAColorValue extrapolationColor =
            visibleWhen(color("autocrystalExtrapolationColor", "#FFFFFFFF"), extrapolationEnabled::get);
    private final NumberValue<Float> extrapolationTicks =
            visibleWhen(num("autocrystalExtrapolationTicks", 1.0f, 0.0f, 20.0f),
                    extrapolationEnabled::get);
    private final BooleanValue interactEnabled =
            bool("autocrystalRenderInteractVector", true);
    private final RGBAColorValue interactColor =
            visibleWhen(color("autocrystalInteractColor", "#FFFF5555"), interactEnabled::get);
    private final FunctionBindSetting baseToggle =
            action(ACTION_BASE, "NONE", BindMode.PRESS)
                    .hudLabel("AutoCrystalBase")
                    .hudToggle(this::isBaseActive);
    private final NumberValue<Float> baseMinDamageDelta =
            numCommon("autocrystalBaseMinDamageDelta", "base_min_damage_delta", CommonSettingSchemas.MIN_DAMAGE, 5.0f, 0.0f, 20.0f);
    private final NumberValue<Integer> basePlaceDelay =
            numCommon("autocrystalBasePlaceDelay", "base_place_delay", CommonSettingSchemas.PLACEMENT_DELAY, 0, 0, 3000);
    private final NumberValue<Integer> baseCalcDelay =
            numCommon("autocrystalBaseCalcDelay", "base_calc_delay", CommonSettingSchemas.CALC_DELAY, 50, 0, 3000);
    private final BooleanValue baseNotify =
            bool("autocrystalBaseNotify", "base_notify", true);
    private final BooleanValue baseDisableNoObby =
            bool("autocrystalBaseDisableNoObby", "base_disable_no_obby", false);

    private final AutoCrystalBasePlanner basePlanner = new AutoCrystalBasePlanner();
    private final AutoCrystalBaseContext baseContext = new AutoCrystalBaseContext();
    private final AutoCrystalPlacementPlanner placementPlanner = new AutoCrystalPlacementPlanner();
    private final AutoCrystalPlacementPlanner.Context placementContext = new AutoCrystalPlacementContext();
    private final Map<BlockPos, Long> renderPositions = new ConcurrentHashMap<>();
    private final AutoCrystalTracker crystalTracker = new AutoCrystalTracker();
    private final AutoCrystalEntityBlocker entityBlocker = new AutoCrystalEntityBlocker();

    private LivingEntity target;
    private AutoCrystalPlaceData bestCandidate;
    private AutoCrystalCrystalData bestCrystal;
    private EndCrystal secondaryCrystal;
    private AutoCrystalPlaceData currentData;
    private AutoCrystalBasePlanner.BaseData bestBaseCandidate;
    private BlockPos renderPos;
    private BlockPos prevRenderPos;
    private BlockPos bestPosition;
    private Vec3 interactVector;
    private int renderTick;
    private int renderStartTick;
    private long lastDamageUpdateMs;
    private long lastPlaceMs;
    private long lastBreakMs;
    private long lastBaseCalcMs;
    private long lastBasePlaceMs;
    private float renderDamage;
    private float renderSelfDamageValue;
    private boolean baseActive;
    private long lastDebugStateLogMs;
    private int lastPlaceCandidateCount;
    private int lastPlaceSafeCount;
    private int lastBreakCandidateCount;
    private int lastBreakSafeCount;
    private int lastPlaceSelfDamageRejectCount;
    private int lastBreakSelfDamageRejectCount;
    private int lastBaseCandidateCount;
    private int lastBaseScannedCount;
    private int lastBaseSafeCount;
    private int lastBaseWorthCount;
    private int lastBaseDeltaCount;

    private static void debugLog(String pattern, Object... args) {
        // if (!DebugLog.isEnabled()) {
        //     return;
        // }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    @Override
    public void onEnable() {
        resetVisualState();
    }

    @Override
    public void onDisable() {
        resetVisualState();
        RotationManager.INSTANCE.clear(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        TargetManager.setAutoCrystalTarget(null);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        renderTick++;
        if (isActionPressedOnce(ACTION_BASE)) {
            baseActive = !baseActive;
            bestBaseCandidate = null;
            Notifier.state("AutoCrystalBase", baseActive);
        }
        InventorySwap.INSTANCE.tick();
        if (isBaseActive() && baseDisableNoObby.get() && mc.player != null && findObsidianHand(mc.player) == null) {
            Notifier.warning("AutoCrystalBase: no obsidian");
            baseActive = false;
            bestBaseCandidate = null;
            return;
        }
        crystalTracker.tick(mc);
        purgeExpiredRenderPositions();
        if (System.currentTimeMillis() - lastDamageUpdateMs > DAMAGE_RESET_MS && renderPositions.isEmpty()) {
            renderDamage = 0.0f;
            renderSelfDamageValue = 0.0f;
        }
    }

    @EventHandler
    private void onPacketReceivePost(PacketEvent.ReceivePost event) {
        if (!isEnabled() || mc.level == null) {
            return;
        }

        if (event.getPacket() instanceof ClientboundAddEntityPacket spawn) {
            if (spawn.getType() != net.minecraft.world.entity.EntityTypes.END_CRYSTAL) {
                return;
            }

            var entity = mc.level.getEntity(spawn.getId());
            if (entity instanceof EndCrystal crystal) {
                crystalTracker.removeAwaitingPositionsNear(crystal);
            }
        }
    }

    @EventHandler(priority = 25)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) {
            return;
        }

        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            clearPlacementState();
            TargetManager.setAutoCrystalTarget(null);
            return;
        }

        target = findCombatTarget();
        TargetManager.setAutoCrystalTarget(target);
        updateBestPosition();
        updateBestCrystal();
        updateBestBasePosition();
        debugState();

        if (tryBreakCrystal()) {
            return;
        }

        if (tryPlaceBase()) {
            return;
        }

        if (!placeEnabled.get()) {
            RotationManager.INSTANCE.clear(this);
            return;
        }

        AutoCrystalPlaceData candidate = bestCandidate;
        if (candidate == null) {
            RotationManager.INSTANCE.clear(this);
            return;
        }

        if (!hasPlaceDelayElapsed()) {
            return;
        }

        AutoCrystalHand crystalHand = findCrystalHand(mc.player);
        if (crystalHand == null) {
            return;
        }

        if (!usesRotationSettings()) {
            RotationManager.INSTANCE.clear(this);
            tryPlaceCrystal(candidate.pos());
            return;
        }

        BlockHitResult bestHitResult = candidate.bhr();
        if (bestHitResult == null) {
            return;
        }

        RotationTarget rotationTarget = new RotationTarget(
                Rotation.lookingAt(bestHitResult.getLocation(), mc.player.getEyePosition()).normalize(),
                target,
                List.of(),
                rotationResetTicks.get(),
                rotationResetThreshold.get(),
                true,
                movementCorrection.get(),
                new RestrictedSingleUseAction(() -> tryPlaceCrystal(candidate.pos()))
        );
        RotationManager.INSTANCE.setRotationTarget(rotationTarget, ROTATION_PRIORITY, this);
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        if (renderEnabled.get()) {
            renderPlacement(renderer, tickDelta);
        }

        if (extrapolationEnabled.get() && target != null) {
            renderExtrapolation(renderer, target);
        }

        if (interactEnabled.get() && interactVector != null) {
            renderInteractVector(renderer, interactVector, interactColor.getArgb());
        }
    }

    public void setRenderCandidate(BlockPos pos, float damage, float selfDamage, Vec3 interactVec) {
        bestPosition = pos;
        interactVector = interactVec;
        renderDamage = Math.max(0.0f, damage);
        renderSelfDamageValue = Math.max(0.0f, selfDamage);
        lastDamageUpdateMs = System.currentTimeMillis();
    }

    public void clearRenderCandidate() {
        bestCandidate = null;
        currentData = null;
        bestPosition = null;
        interactVector = null;
        if (renderPositions.isEmpty()) {
            renderDamage = 0.0f;
            renderSelfDamageValue = 0.0f;
            renderPos = null;
            prevRenderPos = null;
        }
    }

    public void markPlaced(BlockPos pos) {
        if (pos == null) return;
        long now = System.currentTimeMillis();
        if (!pos.equals(renderPos)) {
            renderStartTick = renderTick;
            prevRenderPos = renderPos;
            renderPos = pos;
        }
        renderPositions.put(pos, now);
        lastDamageUpdateMs = now;
    }

    public void markPlaced(BlockPos pos, float damage, float selfDamage, Vec3 interactVec) {
        setRenderCandidate(pos, damage, selfDamage, interactVec);
        markPlaced(pos);
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
        if (activePos == null) {
            return;
        }

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
        double billboardWorldScale = resolveDamageBillboardScale(anchor);
        String mainText = ExplosionRenderUtil.formatDamage(renderDamage);
        if (mainText.isEmpty()) return;

        int mainArgb = ExplosionRenderUtil.applyOpacity(textColor.getArgb(), alpha);
        TextRenderer bold = Fonts.renderer("Iosevka", FontInfo.Type.Bold, TextRenderer.get());
        WorldTextRenderer.Options baseOptions = WorldTextRenderer.Options.defaults()
                .withScale(TEXT_SCALE)
                .withWorldScale(billboardWorldScale)
                .withDepthMode(Renderer3D.DepthMode.NONE)
                .withShadow(true)
                .withOffset(0.0, TEXT_Y_OFFSET);

        if (!renderSelfDamage.get()) {
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
        if (renderSelfDamageValue <= 0.0f) {
            WorldTextRenderer.drawBillboard(
                    renderer,
                    bold,
                    mainText,
                    anchor,
                    baseOptions.withCentered(true).withColor(new RenderColor(mainArgb))
            );
            return;
        }

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

    private void renderExtrapolation(Renderer3D renderer, LivingEntity living) {
        AABB box = PositionExtrapolation.getBestForEntity(living).getBoxInTicks(extrapolationTicks.get());
        int argb = extrapolationColor.getArgb();
        float prevWidth = RenderState.lineWidth;
        RenderState.lineWidth = 1.0f;
        try {
            ExplosionRenderUtil.addOutlineBox(renderer, box, argb);
        } finally {
            RenderState.lineWidth = prevWidth;
        }
    }

    private void renderInteractVector(Renderer3D renderer, Vec3 vec, int argb) {
        AABB box = new AABB(
                vec.x - INTERACT_MARKER_RADIUS,
                vec.y - INTERACT_MARKER_RADIUS,
                vec.z - INTERACT_MARKER_RADIUS,
                vec.x + INTERACT_MARKER_RADIUS,
                vec.y + INTERACT_MARKER_RADIUS,
                vec.z + INTERACT_MARKER_RADIUS
        );
        ExplosionRenderUtil.addFilledBox(renderer, box, ExplosionRenderUtil.applyOpacity(argb, 0.6f));

        float prevWidth = RenderState.lineWidth;
        RenderState.lineWidth = 1.0f;
        try {
            ExplosionRenderUtil.addOutlineBox(renderer, box, argb);
        } finally {
            RenderState.lineWidth = prevWidth;
        }
    }

    private double resolveDamageBillboardScale(Vec3 anchor) {
        double distance = RenderState.cameraPos != null ? RenderState.cameraPos.distanceTo(anchor) : 0.0;
        return WorldUiPresentationService.resolveWorldScale(
                TEXT_WORLD_SCALE,
                distance,
                12.0,
                4.0,
                billboardSize.get(),
                billboardDynamicScale.get(),
                billboardDynamicScaleCoefficient.get()
        );
    }

    private void purgeExpiredRenderPositions() {
        long now = System.currentTimeMillis();
        long lifetime = Math.max(1, fadeTime.get());
        renderPositions.entrySet().removeIf(entry -> now - entry.getValue() > lifetime);
    }

    private void updateBestPosition() {
        if (mc.player == null || mc.level == null || target == null) {
            clearRenderCandidate();
            currentData = null;
            return;
        }

        AutoCrystalPlacementPlanner.PlaceScanResult result = placementPlanner.findBestPlace(
                placementContext,
                target,
                mc.player.position(),
                Mth.ceil(placeRange.get())
        );
        lastPlaceCandidateCount = result.scannedCandidates();
        lastPlaceSelfDamageRejectCount = result.selfDamageRejects();
        lastPlaceSafeCount = result.safeCandidates();
        debugLog("place-candidates total=%d scan=%s", result.safeCandidates(), result.debug());

        AutoCrystalPlaceData best = result.best();
        if (best == null) {
            clearRenderCandidate();
            currentData = null;
            return;
        }

        bestCandidate = best;
        currentData = best;
        setRenderCandidate(best.pos(), best.damage(), best.selfDamage(), best.bhr().getLocation());
    }

    private void updateBestBasePosition() {
        if (!isBaseActive() || !placeEnabled.get() || mc.player == null || mc.level == null || target == null) {
            bestBaseCandidate = null;
            lastBaseCandidateCount = 0;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBaseCalcMs < Math.max(0, baseCalcDelay.get())) {
            return;
        }
        lastBaseCalcMs = now;

        if (findObsidianHand(mc.player) == null) {
            bestBaseCandidate = null;
            lastBaseCandidateCount = 0;
            return;
        }

        bestBaseCandidate = basePlanner.findBest(
                baseContext,
                target,
                List.of(target.position()),
                Mth.ceil(placeRange.get()),
                baseMinDamageDelta.get()
        );
        lastBaseScannedCount = basePlanner.lastScanned();
        lastBaseCandidateCount = basePlanner.lastCandidates();
        lastBaseSafeCount = basePlanner.lastSafeCandidates();
        lastBaseWorthCount = basePlanner.lastWorthCandidates();
        lastBaseDeltaCount = basePlanner.lastDeltaCandidates();
    }

    private AutoCrystalBasePlanner.BaseData getBasePlaceData(BlockPos pos, LivingEntity currentTarget) {
        return AutoCrystalBaseEvaluator.getBaseData(baseContext, pos, currentTarget);
    }

    private boolean canEvaluateBaseAt(BlockPos pos) {
        return AutoCrystalBaseEvaluator.canEvaluateBaseAt(mc.level, pos);
    }

    private void updateBestCrystal() {
        if (mc.player == null || mc.level == null || target == null) {
            bestCrystal = null;
            secondaryCrystal = null;
            return;
        }

        if (secondaryCrystal != null) {
            if (canAttackCrystal(secondaryCrystal)) {
                AutoCrystalCrystalData crystalData = placementPlanner.evaluateCrystal(placementContext, target, secondaryCrystal);
                bestCrystal = crystalData;
                if (crystalData != null) {
                    renderDamage = crystalData.damage();
                    renderSelfDamageValue = crystalData.selfDamage();
                }
                lastBreakCandidateCount = crystalData != null ? 1 : 0;
                lastBreakSelfDamageRejectCount = crystalData != null
                        && crystalData.selfDamage() > maxSelfDamage.get()
                        && !crystalData.overrideDamage() ? 1 : 0;
                lastBreakSafeCount = crystalData != null
                        && isSafe(crystalData.damage(), crystalData.selfDamage(), crystalData.overrideDamage()) ? 1 : 0;
            } else {
                bestCrystal = null;
                lastBreakCandidateCount = 0;
                lastBreakSafeCount = 0;
                lastBreakSelfDamageRejectCount = 0;
            }
            secondaryCrystal = null;
            debugLog("break-candidates total=%d", bestCrystal != null ? 1 : 0);
            return;
        }

        AutoCrystalPlacementPlanner.BreakScanResult result = placementPlanner.findBestCrystal(placementContext, target);
        bestCrystal = result.best();
        if (bestCrystal != null) {
            renderDamage = bestCrystal.damage();
            renderSelfDamageValue = bestCrystal.selfDamage();
        }
        lastBreakCandidateCount = result.scannedCandidates();
        lastBreakSafeCount = result.safeCandidates();
        debugLog("break-candidates total=%d", result.safeCandidates());
    }

    private AutoCrystalPlaceValidationResult validatePlaceCrystal(BlockPos pos, boolean calcPhase) {
        if (mc.level == null) {
            return AutoCrystalPlaceValidationResult.INVALID_BASE;
        }

        var state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
            return AutoCrystalPlaceValidationResult.INVALID_BASE;
        }

        if (!mc.level.isEmptyBlock(pos.above())) {
            return AutoCrystalPlaceValidationResult.BLOCKED_AIR;
        }

        if (isPositionBlockedByEntity(pos, calcPhase)) {
            return AutoCrystalPlaceValidationResult.BLOCKED_ENTITY;
        }

        return AutoCrystalPlaceValidationResult.OK;
    }


    private boolean isPositionBlockedByEntity(BlockPos pos, boolean calcPhase) {
        AutoCrystalEntityBlocker.Result result = entityBlocker.check(
                mc.level,
                mc.player,
                pos,
                calcPhase,
                this::isDeadCrystal,
                this::isCrystalBlocked,
                this::canAttackCrystal
        );
        if (result.secondaryCrystal() != null) {
            secondaryCrystal = result.secondaryCrystal();
        }
        return result.blocked();
    }

    private boolean canAttackCrystal(EndCrystal crystal) {
        return placementPlanner.canAttackCrystal(placementContext, target, crystal);
    }

    private boolean shouldOverrideMinDamage(LivingEntity currentTarget, float damage) {
        return AutoCrystalDamageRules.shouldOverrideMinDamage(currentTarget, damage, faceplaceHealth.get());
    }

    private boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage) {
        return AutoCrystalDamageRules.shouldOverrideMaxSelfDamage(
                mc.player,
                target,
                damage,
                selfDamage,
                maxSelfDamage.get()
        );
    }

    private boolean isSafe(float damage, float selfDamage, boolean overrideDamage) {
        return AutoCrystalDamageRules.isSafe(mc.player, selfDamage, overrideDamage);
    }

    private boolean tryBreakCrystal() {
        if (!breakEnabled.get() || !hasBreakDelayElapsed() || mc.player == null) {
            return false;
        }

        AutoCrystalCrystalData candidate = bestCrystal;
        if (candidate == null || candidate.crystal() == null || !candidate.crystal().isAlive()) {
            debugLog("break-skip reason=no-candidate");
            return false;
        }

        EndCrystal crystal = candidate.crystal();
        if (!usesRotationSettings()) {
            RotationManager.INSTANCE.clear(this);
            attackCrystal(crystal, null);
            return true;
        }

        RotationWithVector rotation = RotationRaytrace.raytraceBox(
                mc.player.getEyePosition(),
                crystal.getBoundingBox(),
                breakRange.get(),
                breakWallRange.get()
        );
        if (rotation == null) {
            rotation = new RotationWithVector(
                    Rotation.lookingAt(crystal.position(), mc.player.getEyePosition()).normalize(),
                    crystal.position()
            );
        }

        Rotation attackRotation = rotation.rotation().normalize();
        RotationTarget rotationTarget = new RotationTarget(
                attackRotation,
                crystal,
                List.of(),
                rotationResetTicks.get(),
                rotationResetThreshold.get(),
                true,
                movementCorrection.get(),
                new RestrictedSingleUseAction(() -> attackCrystal(crystal, attackRotation))
        );
        RotationManager.INSTANCE.setRotationTarget(rotationTarget, ROTATION_PRIORITY, this);
        return true;
    }

    private boolean tryPlaceBase() {
        if (!isBaseActive() || !placeEnabled.get() || !hasBasePlaceDelayElapsed() || mc.player == null) {
            return false;
        }

        AutoCrystalBasePlanner.BaseData candidate = bestBaseCandidate;
        if (candidate == null) {
            return false;
        }

        if (findObsidianHand(mc.player) == null) {
            return false;
        }

        AutoCrystalBasePlanner.BaseData refreshed = getBasePlaceData(candidate.position(), target);
        if (refreshed == null
                || !isWorthBaseDamage(refreshed.damage())
                || !isEnoughBaseDamageDelta(refreshed.damage(), baseMinDamageDelta.get())) {
            return false;
        }

        if (!usesRotationSettings()) {
            placeBase(refreshed);
            return true;
        }

        RotationTarget rotationTarget = new RotationTarget(
                Rotation.lookingAt(refreshed.hitResult().getLocation(), mc.player.getEyePosition()).normalize(),
                target,
                List.of(),
                rotationResetTicks.get(),
                rotationResetThreshold.get(),
                true,
                movementCorrection.get(),
                new RestrictedSingleUseAction(() -> placeBase(refreshed))
        );
        RotationManager.INSTANCE.setRotationTarget(rotationTarget, ROTATION_PRIORITY, this);
        return true;
    }

    private void placeBase(AutoCrystalBasePlanner.BaseData data) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || data == null) {
            return;
        }

        AutoCrystalBasePlanner.BaseData refreshed = getBasePlaceData(data.position(), target);
        if (refreshed == null || !hasBasePlaceDelayElapsed()) {
            return;
        }
        if (!isWorthBaseDamage(refreshed.damage())
                || !isEnoughBaseDamageDelta(refreshed.damage(), baseMinDamageDelta.get())) {
            return;
        }

        AutoCrystalHand hand = findObsidianHand(player);
        if (hand == null) {
            return;
        }

        if (hand.hotbarSlot() >= 0) {
            InventorySwap.INSTANCE.leaseHotbar(this, hand.hotbarSlot(), BASE_HOTBAR_RESET_TICKS);
        }

        float damageDelta = refreshed.damage() - getCurrentCrystalDamage();
        CombatBlockUseUtil.useOn(mc, hand.hand(), refreshed.hitResult());
        lastBasePlaceMs = System.currentTimeMillis();
        bestBaseCandidate = null;
        markPlaced(refreshed.position(), refreshed.damage(), refreshed.selfDamage(), refreshed.hitResult().getLocation());

        if (baseNotify.get()) {
            Notifier.info("AutoCrystalBase placed at X:" + refreshed.position().getX()
                    + " Y:" + refreshed.position().getY()
                    + " Z:" + refreshed.position().getZ()
                    + " +" + ExplosionRenderUtil.formatDamage(damageDelta));
        }
    }

    private void tryPlaceCrystal(BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || pos == null) {
            debugLog("place-skip reason=invalid-state pos=%s", pos);
            return;
        }

        LivingEntity currentTarget = target;
        AutoCrystalPlaceData placeData = currentTarget != null ? getPlaceData(pos, currentTarget) : null;
        if (!hasPlaceDelayElapsed() || placeData == null) {
            debugLog("place-skip reason=delay-or-invalid pos=%s", pos);
            return;
        }

        if (isPositionBlockedByEntity(pos, false)) {
            debugLog("place-skip reason=blocked-entity pos=%s", pos);
            return;
        }

        AutoCrystalHand hand = findCrystalHand(player);
        if (hand == null) {
            debugLog("place-skip reason=no-crystal-hand");
            return;
        }

        BlockHitResult hitResult = placeData.bhr();
        debugLog("place-attempt pos=%s hand=%s slot=%d hit=%s side=%s",
                pos, hand.hand(), hand.hotbarSlot(), hitResult.getBlockPos(), hitResult.getDirection());

        if (hand.hotbarSlot() >= 0) {
            InventorySwap.INSTANCE.leaseHotbar(this, hand.hotbarSlot(), HOTBAR_RESET_TICKS);
        }

        CombatBlockUseUtil.useOn(mc, hand.hand(), hitResult);
        debugLog("place-result pos=%s packet=sent", pos);
        lastPlaceMs = System.currentTimeMillis();
        crystalTracker.addAwaitingPosition(mc, mc.level, pos.immutable());

        markPlaced(pos, placeData.damage(), placeData.selfDamage(), hitResult.getLocation());
    }

    private void attackCrystal(EndCrystal crystal, Rotation attackRotation) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || crystal == null || mc.getConnection() == null) {
            debugLog("break-skip reason=invalid-state");
            return;
        }

        if (!hasBreakDelayElapsed() || !crystal.isAlive() || crystal.isRemoved() || !canAttackCrystal(crystal)) {
            debugLog("break-skip reason=delay-or-dead id=%d", crystal.getId());
            return;
        }

        debugLog("break-attempt id=%d pos=%.2f %.2f %.2f", crystal.getId(), crystal.getX(), crystal.getY(), crystal.getZ());
        mc.gameMode.attack(player, crystal);
        player.swing(InteractionHand.MAIN_HAND);
        lastBreakMs = System.currentTimeMillis();
        crystalTracker.onCrystalAttack(mc, crystal);
        crystalTracker.markNearbyCrystalsDead(mc.level, crystal);
    }

    private boolean isCrystalBlocked(int id) {
        return crystalTracker.isCrystalBlocked(mc, id);
    }

    private boolean isDeadCrystal(int id) {
        return crystalTracker.isDeadCrystal(id);
    }

    private AutoCrystalPlaceData getPlaceData(BlockPos pos, LivingEntity currentTarget) {
        return placementPlanner.getPlaceData(placementContext, pos, currentTarget);
    }

    private BlockHitResult getInteractResult(BlockPos pos, Vec3 crystalVec) {
        return AutoCrystalInteractionUtil.getCrystalInteractResult(
                mc.level,
                mc.player,
                pos,
                crystalVec,
                placementMode.get(),
                placeRange.get(),
                wallRange.get()
        );
    }

    private AutoCrystalHand findCrystalHand(LocalPlayer player) {
        return AutoCrystalInteractionUtil.findCrystalHand(player);
    }

    private AutoCrystalHand findObsidianHand(LocalPlayer player) {
        return AutoCrystalInteractionUtil.findObsidianHand(player);
    }

    private boolean hasPlaceDelayElapsed() {
        return System.currentTimeMillis() - lastPlaceMs >= Math.max(0, placeDelay.get());
    }

    private boolean hasBreakDelayElapsed() {
        return System.currentTimeMillis() - lastBreakMs >= Math.max(0, breakDelay.get());
    }

    private boolean hasBasePlaceDelayElapsed() {
        return System.currentTimeMillis() - lastBasePlaceMs >= Math.max(0, basePlaceDelay.get());
    }

    private long getPlaceDelayRemainingMs() {
        return Math.max(0L, Math.max(0, placeDelay.get()) - (System.currentTimeMillis() - lastPlaceMs));
    }

    private long getBreakDelayRemainingMs() {
        return Math.max(0L, Math.max(0, breakDelay.get()) - (System.currentTimeMillis() - lastBreakMs));
    }

    private long getBasePlaceDelayRemainingMs() {
        return Math.max(0L, Math.max(0, basePlaceDelay.get()) - (System.currentTimeMillis() - lastBasePlaceMs));
    }

    public String getTargetHudStatus(LivingEntity hudTarget) {
        if (!isEnabled() || hudTarget == null) {
            return null;
        }
        if (target == null || hudTarget != target) {
            return null;
        }
        if (mc.player == null || mc.level == null) {
            return "Idle";
        }

        if (breakEnabled.get()) {
            AutoCrystalCrystalData crystalData = bestCrystal;
            EndCrystal crystal = crystalData != null ? crystalData.crystal() : null;
            if (crystal != null && crystal.isAlive() && !crystal.isRemoved()) {
                long breakWait = getBreakDelayRemainingMs();
                if (breakWait > 0L) {
                    return "Attack in " + breakWait + "ms";
                }
                return usesRotationSettings() ? "Rotate -> attack" : "Attacking";
            }
            if (lastBreakSelfDamageRejectCount > 0 || (lastBreakCandidateCount > 0 && lastBreakSafeCount <= 0)) {
                return "Self dmg";
            }
        }

        if (placeEnabled.get()) {
            String baseFailureStatus = null;
            if (isBaseActive()) {
                if (findObsidianHand(mc.player) == null) {
                    return "No obsidian";
                }
                if (bestBaseCandidate != null) {
                    long baseWait = getBasePlaceDelayRemainingMs();
                    if (baseWait > 0L) {
                        return "Base in " + baseWait + "ms";
                    }
                    return usesRotationSettings() ? "Rotate -> base" : "Placing base";
                }

                baseFailureStatus = baseStatus();
                if (shouldShowBaseStatus(baseFailureStatus)) {
                    return baseFailureStatus;
                }
            }

            if (findCrystalHand(mc.player) == null) {
                return "No crystals";
            }

            if (bestCandidate != null) {
                long placeWait = getPlaceDelayRemainingMs();
                if (placeWait > 0L) {
                    return "Attack in " + placeWait + "ms";
                }
                return usesRotationSettings() ? "Rotate -> attack" : "Attacking";
            }
            if (lastPlaceSelfDamageRejectCount > 0 || (lastPlaceCandidateCount > 0 && lastPlaceSafeCount <= 0)) {
                return "Self dmg";
            }

            return baseFailureStatus != null ? baseFallbackStatus(baseFailureStatus) : "No place";
        }

        if (breakEnabled.get()) {
            return "No crystal";
        }

        return "Idle";
    }

    private String baseStatus() {
        if (lastBaseScannedCount <= 0) return "No base scan";
        if (lastBaseCandidateCount <= 0) return "No base interact";
        if (lastBaseSafeCount <= 0) return "Base self dmg";
        if (lastBaseWorthCount <= 0) return "Base low dmg";
        if (lastBaseDeltaCount <= 0) return "Base delta";
        return "No base";
    }

    private boolean shouldShowBaseStatus(String status) {
        return !"Base delta".equals(status);
    }

    private String baseFallbackStatus(String status) {
        return "Base delta".equals(status) ? "No place" : status;
    }

    private LivingEntity findCombatTarget() {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        TargetingUtil.TargetingSettings settings = new TargetingUtil.TargetingSettings(
                TARGET_SEARCH_RANGE,
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

    private void debugState() {
    }

    private boolean isSpawnNearPendingPlace(ClientboundAddEntityPacket spawn, BlockPos pos) {
        AABB crystalBox = AutoCrystalInteractionUtil.predictedCrystalBox(pos);
        return crystalBox.contains(spawn.getX(), spawn.getY(), spawn.getZ());
    }

    private Vec3 resolvePredictedPosition(LivingEntity entity, int ticks) {
        if (entity == null || ticks <= 0) {
            return entity != null ? entity.position() : Vec3.ZERO;
        }

        if (entity instanceof net.minecraft.world.entity.player.Player player) {
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

    private void clearPlacementState() {
        clearRenderCandidate();
        bestCrystal = null;
        bestBaseCandidate = null;
        target = null;
        crystalTracker.clear();
        RotationManager.INSTANCE.clear(this);
        lastPlaceCandidateCount = 0;
        lastPlaceSafeCount = 0;
        lastBreakCandidateCount = 0;
        lastBreakSafeCount = 0;
        lastPlaceSelfDamageRejectCount = 0;
        lastBreakSelfDamageRejectCount = 0;
        lastBaseCandidateCount = 0;
        lastBaseScannedCount = 0;
        lastBaseSafeCount = 0;
        lastBaseWorthCount = 0;
        lastBaseDeltaCount = 0;
    }

    private void resetVisualState() {
        target = null;
        bestCandidate = null;
        bestCrystal = null;
        bestBaseCandidate = null;
        renderPos = null;
        prevRenderPos = null;
        bestPosition = null;
        interactVector = null;
        renderTick = 0;
        renderStartTick = 0;
        lastDamageUpdateMs = 0L;
        lastPlaceMs = 0L;
        lastBreakMs = 0L;
        lastBaseCalcMs = 0L;
        lastBasePlaceMs = 0L;
        renderDamage = 0.0f;
        renderSelfDamageValue = 0.0f;
        baseActive = false;
        renderPositions.clear();
        crystalTracker.clear();
        lastPlaceCandidateCount = 0;
        lastPlaceSafeCount = 0;
        lastBreakCandidateCount = 0;
        lastBreakSafeCount = 0;
        lastPlaceSelfDamageRejectCount = 0;
        lastBreakSelfDamageRejectCount = 0;
        lastBaseCandidateCount = 0;
        lastBaseScannedCount = 0;
        lastBaseSafeCount = 0;
        lastBaseWorthCount = 0;
        lastBaseDeltaCount = 0;
    }

    private String buildDamageText() {
        String main = ExplosionRenderUtil.formatDamage(renderDamage);
        if (main.isEmpty()) return "";
        if (!renderSelfDamage.get() || renderSelfDamageValue <= 0.0f) {
            return main;
        }
        return main + " / " + ExplosionRenderUtil.formatDamage(renderSelfDamageValue);
    }

    private float getCurrentCrystalDamage() {
        if (currentData != null) {
            return currentData.damage();
        }
        if (bestCrystal != null) {
            return bestCrystal.damage();
        }
        return 0.0f;
    }

    private boolean hasCurrentCrystalPlan() {
        return currentData != null || bestCrystal != null;
    }

    private boolean isWorthBaseDamage(float damage) {
        if (!hasCurrentCrystalPlan()) {
            return shouldOverrideMinDamage(target, damage) || damage >= BASE_INITIATIVE_MIN_DAMAGE;
        }
        return shouldOverrideMinDamage(target, damage) || damage > minDamage.get();
    }

    private boolean isEnoughBaseDamageDelta(float damage, float minDamageDelta) {
        float delta = Math.max(0.0f, minDamageDelta);
        if (!hasCurrentCrystalPlan()) {
            return damage >= Math.max(BASE_INITIATIVE_MIN_DAMAGE, delta);
        }
        return damage >= getCurrentCrystalDamage() + delta;
    }

    private boolean usesRotationSettings() {
        return CombatRotationModeUtil.usesRotations(attackMode);
    }

    private boolean isBaseActive() {
        return baseActive;
    }

    private final class AutoCrystalBaseContext implements AutoCrystalBasePlanner.Context, AutoCrystalBaseEvaluator.Context {
        @Override
        public boolean canEvaluateBaseAt(BlockPos pos) {
            return AutoCrystal.this.canEvaluateBaseAt(pos);
        }

        @Override
        public AutoCrystalBasePlanner.BaseData getBaseData(BlockPos pos, LivingEntity target) {
            return getBasePlaceData(pos, target);
        }

        @Override
        public boolean isSafe(float damage, float selfDamage, boolean overrideDamage) {
            return AutoCrystal.this.isSafe(damage, selfDamage, overrideDamage);
        }

        @Override
        public boolean hasCurrentCrystalPlan() {
            return AutoCrystal.this.hasCurrentCrystalPlan();
        }

        @Override
        public float currentCrystalDamage() {
            return getCurrentCrystalDamage();
        }

        @Override
        public boolean isWorthBaseDamage(float damage) {
            return AutoCrystal.this.isWorthBaseDamage(damage);
        }

        @Override
        public boolean isEnoughBaseDamageDelta(float damage, float minDamageDelta) {
            return AutoCrystal.this.isEnoughBaseDamageDelta(damage, minDamageDelta);
        }

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
            return AutoCrystal.this.resolvePredictedPosition(entity, ticks);
        }

        @Override
        public boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage) {
            return AutoCrystal.this.shouldOverrideMaxSelfDamage(damage, selfDamage);
        }

        @Override
        public float placeRange() {
            return placeRange.get();
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
        public boolean ignoreTerrain() {
            return ignoreTerrain.get();
        }
    }

    private final class AutoCrystalPlacementContext implements AutoCrystalPlacementPlanner.Context {
        @Override
        public LocalPlayer player() {
            return mc.player;
        }

        @Override
        public Iterable<Entity> entities() {
            return mc.level != null ? mc.level.entitiesForRendering() : List.<Entity>of();
        }

        @Override
        public Vec3 resolvePredictedPosition(LivingEntity entity, int ticks) {
            return AutoCrystal.this.resolvePredictedPosition(entity, ticks);
        }

        @Override
        public AutoCrystalPlaceValidationResult validatePlaceCrystal(BlockPos pos, boolean calcPhase) {
            return AutoCrystal.this.validatePlaceCrystal(pos, calcPhase);
        }

        @Override
        public BlockHitResult getInteractResult(BlockPos pos, Vec3 crystalVec) {
            return AutoCrystal.this.getInteractResult(pos, crystalVec);
        }

        @Override
        public boolean isDeadCrystal(int id) {
            return AutoCrystal.this.isDeadCrystal(id);
        }

        @Override
        public boolean isCrystalBlocked(int id) {
            return AutoCrystal.this.isCrystalBlocked(id);
        }

        @Override
        public boolean shouldOverrideMinDamage(LivingEntity target, float damage) {
            return AutoCrystal.this.shouldOverrideMinDamage(target, damage);
        }

        @Override
        public boolean shouldOverrideMaxSelfDamage(float damage, float selfDamage) {
            return AutoCrystal.this.shouldOverrideMaxSelfDamage(damage, selfDamage);
        }

        @Override
        public boolean isSafe(float damage, float selfDamage, boolean overrideDamage) {
            return AutoCrystal.this.isSafe(damage, selfDamage, overrideDamage);
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
        public float breakRange() {
            return breakRange.get();
        }

        @Override
        public float breakWallRange() {
            return breakWallRange.get();
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
        public boolean ignoreTerrain() {
            return ignoreTerrain.get();
        }
    }

    private enum RenderMode {
        FADE,
        SLIDE,
        DEFAULT
    }

}
