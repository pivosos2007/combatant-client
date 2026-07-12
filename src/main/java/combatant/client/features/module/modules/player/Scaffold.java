/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.*;
import combatant.client.events.impl.*;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import combatant.client.util.block.scaffold.*;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.SettingDef;
import combatant.client.config.values.*;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.*;
import combatant.client.features.gui.hud.nondraggable.impl.BetterTooltips;
import combatant.client.features.module.*;
import combatant.client.mixins.accessors.ServerboundMovePlayerPacketAccessor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.TickDelta;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.RotationProcessor;
import combatant.client.util.aiming.features.processors.anglesmooth.AngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.AccelerationAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.InterpolationAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SigmoidAngleSmooth;
import combatant.client.util.block.scaffold.*;
import combatant.client.util.entity.EagleUtil;
import combatant.client.util.player.MovementUtil;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

//todo Description
@ModuleInfo(id = "scaffold", displayName = "Scaffold", aliases = {"bridge", "autobridge"}, category = ModuleCategory.PLAYER)
public class Scaffold extends Module {
    private static final boolean DEBUG_LOGS = true;
    private static final List<BlockPos> NORMAL_OFFSETS = commonOffsets(0, -1, 1);
    private static final List<BlockPos> DOWN_OFFSETS = commonOffsets(0, -1, 1, -2, 2);
    private final EnumValue<RotationTiming> rotationTiming =
            new EnumValue<>("rotation_timing", RotationTiming.NORMAL, RotationTiming.values());
    private final EnumValue<Technique> technique =
            new EnumValue<>("technique", Technique.NORMAL, Technique.values());
    private final EnumValue<SameYMode> sameY =
            new EnumValue<>("same_y", SameYMode.OFF, SameYMode.values());
    private final EnumValue<MovementCorrection> movementCorrection =
            new EnumValue<>("movement_correction", MovementCorrection.SILENT, MovementCorrection.values());
    private final EnumValue<AngleSmoothMode> angleSmoothMode =
            new EnumValue<>("angle_smooth_mode", AngleSmoothMode.INTERPOLATION, AngleSmoothMode.values());
    private final EnumValue<SprintMode> clientSprintMode =
            new EnumValue<>("client_sprint_mode", SprintMode.DO_NOT_CHANGE, SprintMode.values());
    private final EnumValue<SprintMode> serverSprintMode =
            new EnumValue<>("server_sprint_mode", SprintMode.DO_NOT_CHANGE, SprintMode.values());
    private final EnumValue<TowerMode> towerMode =
            new EnumValue<>("tower_mode", TowerMode.NONE, TowerMode.values());
    private final EnumValue<TellyResetMode> tellyResetMode =
            new EnumValue<>("telly_reset_mode", TellyResetMode.RESET, TellyResetMode.values());
    private final EnumValue<ScaffoldFacePositionFactory.Mode> aimMode =
            new EnumValue<>("aim_mode", ScaffoldFacePositionFactory.Mode.STABILIZED, ScaffoldFacePositionFactory.Mode.values());
    private final EnumValue<IndicatorSide> placementIndicatorSide =
            new EnumValue<>("placement_indicator_side", IndicatorSide.LEFT, IndicatorSide.values());
    private final EnumValue<IndicatorStyle> placementIndicatorStyle =
            new EnumValue<>("placement_indicator_style", IndicatorStyle.PILL, IndicatorStyle.values());
    private final EnumValue<IndicatorCountMode> placementIndicatorCountMode =
            new EnumValue<>("placement_indicator_count_mode", IndicatorCountMode.CURRENT_STACK, IndicatorCountMode.values());
    private final BooleanValue autoBlock =
            new BooleanValue("auto_block", true);
    private final BooleanValue renderPlacement =
            new BooleanValue("render_placement", true);
    private final BooleanValue placementIndicator =
            new BooleanValue("placement_indicator", true);
    private final BooleanValue placementIndicatorBackground =
            new BooleanValue("placement_indicator_background", true);
    private final BooleanValue autoBlockAlways =
            new BooleanValue("auto_block_always", false);
    private final BooleanValue prediction =
            new BooleanValue("prediction", true);
    private final BooleanValue considerInventory =
            new BooleanValue("consider_inventory", false);
    private final BooleanValue down =
            new BooleanValue("down", false);
    private final BooleanValue eagle =
            new BooleanValue("eagle", false);
    private final BooleanValue eagleOnlyOnGround =
            new BooleanValue("eagle_only_on_ground", true);
    private final NumberValue<Integer> delay =
            new NumberValue<>("delay", 0, 0, 20);
    private final NumberValue<Integer> autoBlockSlotResetDelay =
            new NumberValue<>("auto_block_slot_reset_delay", 5, 0, 40);
    private final NumberValue<Integer> autoBlockDoNotUseBelowCount =
            new NumberValue<>("auto_block_do_not_use_below_count", 1, 0, 64);
    private final NumberValue<Integer> blocksToEagle =
            new NumberValue<>("blocks_to_eagle", 0, 0, 10);
    private final NumberValue<Integer> expandLength =
            new NumberValue<>("expand_length", 4, 1, 10);
    private final NumberValue<Double> eagleEdgeDistance =
            new NumberValue<>("eagle_edge_distance", 0.01, 0.01, 1.3);
    private final NumberValue<Double> minDist =
            new NumberValue<>("min_dist", 0.0, 0.0, 0.25);
    private final BooleanValue acceleration =
            new BooleanValue("acceleration", false);
    private final NumberValue<Double> accelerationMultiplier =
            new NumberValue<>("acceleration_multiplier", 0.6, 0.1, 3.0);
    private final BooleanValue accelerationOnlyOnGround =
            new BooleanValue("acceleration_only_on_ground", false);
    private final BooleanValue strafe =
            new BooleanValue("strafe", false);
    private final NumberValue<Double> strafeSpeed =
            new NumberValue<>("strafe_speed", 0.247, 0.0, 5.0);
    private final BooleanValue strafeHypixel =
            new BooleanValue("strafe_hypixel", false);
    private final BooleanValue strafeOnlyOnGround =
            new BooleanValue("strafe_only_on_ground", false);
    private final BooleanValue jumpStrafe =
            new BooleanValue("jump_strafe", false);
    private final NumberValue<Double> jumpStrafeStraightMin =
            new NumberValue<>("jump_strafe_straight_min", 0.48, 0.1, 1.0);
    private final NumberValue<Double> jumpStrafeStraightMax =
            new NumberValue<>("jump_strafe_straight_max", 0.49, 0.1, 1.0);
    private final NumberValue<Double> jumpStrafeDiagonalMin =
            new NumberValue<>("jump_strafe_diagonal_min", 0.48, 0.1, 1.0);
    private final NumberValue<Double> jumpStrafeDiagonalMax =
            new NumberValue<>("jump_strafe_diagonal_max", 0.49, 0.1, 1.0);
    private final BooleanValue speedLimiter =
            new BooleanValue("speed_limiter", false);
    private final BooleanValue stabilizeMovement =
            new BooleanValue("stabilize_movement", false);
    private final BooleanValue telly =
            new BooleanValue("telly", false);
    private final BooleanValue tellyAimOnTower =
            new BooleanValue("telly_aim_on_tower", true);
    private final BooleanValue ceiling =
            new BooleanValue("ceiling", false);
    private final BooleanValue headHitter =
            new BooleanValue("head_hitter", false);
    private final NumberValue<Double> speedLimit =
            new NumberValue<>("speed_limit", 0.11, 0.01, 0.4);
    private final NumberValue<Integer> tellyStraightTicks =
            new NumberValue<>("telly_straight_ticks", 0, 0, 5);
    private final NumberValue<Integer> tellyJumpTicksMin =
            new NumberValue<>("telly_jump_ticks_min", 0, 0, 10);
    private final NumberValue<Integer> tellyJumpTicksMax =
            new NumberValue<>("telly_jump_ticks_max", 0, 0, 10);
    private final NumberValue<Double> towerMotion =
            new NumberValue<>("tower_motion", 0.42, 0.0, 1.0);
    private final NumberValue<Double> towerTriggerHeight =
            new NumberValue<>("tower_trigger_height", 0.78, 0.76, 1.0);
    private final NumberValue<Double> towerSlow =
            new NumberValue<>("tower_slow", 1.0, 0.0, 3.0);
    private final RGBAColorValue renderFillColor =
            new RGBAColorValue("render_fill_color", "#3264A0A0");
    private final RGBAColorValue renderLineColor =
            new RGBAColorValue("render_line_color", "#96A0FFFF");
    private final NumberValue<Integer> renderLineWidth =
            new NumberValue<>("render_line_width", 2, 1, 5);
    private final NumberValue<Double> renderBobAmplitude =
            new NumberValue<>("render_bob_amplitude", 0.08, 0.0, 0.3);
    private final NumberValue<Double> renderBobSpeed =
            new NumberValue<>("render_bob_speed", 2.2, 0.2, 8.0);
    private final NumberValue<Double> renderPlaceAnimTime =
            new NumberValue<>("render_place_anim_time", 0.22, 0.05, 1.0);
    private final NumberValue<Double> placementIndicatorScale =
            new NumberValue<>("placement_indicator_scale", 1.0, 0.5, 3.0);
    private final NumberValue<Double> placementIndicatorOffsetX =
            new NumberValue<>("placement_indicator_offset_x", 26.0, -120.0, 120.0);
    private final NumberValue<Double> placementIndicatorOffsetY =
            new NumberValue<>("placement_indicator_offset_y", 0.0, -120.0, 120.0);
    private final NumberValue<Float> linearHorMin =
            new NumberValue<>("linear_horizontal_min", 180f, 0f, 180f);
    private final NumberValue<Float> linearHorMax =
            new NumberValue<>("linear_horizontal_max", 180f, 0f, 180f);
    private final NumberValue<Float> linearVertMin =
            new NumberValue<>("linear_vertical_min", 180f, 0f, 180f);
    private final NumberValue<Float> linearVertMax =
            new NumberValue<>("linear_vertical_max", 180f, 0f, 180f);
    private final NumberValue<Float> sigmoidHorMin =
            new NumberValue<>("sigmoid_horizontal_min", 180f, 0f, 180f);
    private final NumberValue<Float> sigmoidHorMax =
            new NumberValue<>("sigmoid_horizontal_max", 180f, 0f, 180f);
    private final NumberValue<Float> sigmoidVertMin =
            new NumberValue<>("sigmoid_vertical_min", 180f, 0f, 180f);
    private final NumberValue<Float> sigmoidVertMax =
            new NumberValue<>("sigmoid_vertical_max", 180f, 0f, 180f);
    private final NumberValue<Float> sigmoidSteepness =
            new NumberValue<>("sigmoid_steepness", 10f, 0f, 20f);
    private final NumberValue<Float> sigmoidMidpoint =
            new NumberValue<>("sigmoid_midpoint", 0.3f, 0f, 1f);
    private final NumberValue<Integer> interpHorMin =
            new NumberValue<>("interp_horizontal_min", 80, 1, 100);
    private final NumberValue<Integer> interpHorMax =
            new NumberValue<>("interp_horizontal_max", 85, 1, 100);
    private final NumberValue<Integer> interpVertMin =
            new NumberValue<>("interp_vertical_min", 20, 1, 100);
    private final NumberValue<Integer> interpVertMax =
            new NumberValue<>("interp_vertical_max", 25, 1, 100);
    private final NumberValue<Integer> interpDirMin =
            new NumberValue<>("interp_direction_change_min", 95, 0, 100);
    private final NumberValue<Integer> interpDirMax =
            new NumberValue<>("interp_direction_change_max", 100, 0, 100);
    private final NumberValue<Float> interpMidpoint =
            new NumberValue<>("interp_midpoint", 0.35f, 0.0f, 1.0f);
    private final NumberValue<Float> accelYawMin =
            new NumberValue<>("accel_yaw_min", 20f, 1f, 180f);
    private final NumberValue<Float> accelYawMax =
            new NumberValue<>("accel_yaw_max", 25f, 1f, 180f);
    private final NumberValue<Float> accelPitchMin =
            new NumberValue<>("accel_pitch_min", 20f, 1f, 180f);
    private final NumberValue<Float> accelPitchMax =
            new NumberValue<>("accel_pitch_max", 25f, 1f, 180f);
    private final BooleanValue accelDynamicEnabled =
            new BooleanValue("accel_dynamic_enabled", false);
    private final NumberValue<Float> accelCoefDistance =
            new NumberValue<>("accel_coef_distance", -1.393f, -2f, 2f);
    private final NumberValue<Float> accelYawCrossMin =
            new NumberValue<>("accel_yaw_cross_min", 17f, 1f, 180f);
    private final NumberValue<Float> accelYawCrossMax =
            new NumberValue<>("accel_yaw_cross_max", 20f, 1f, 180f);
    private final NumberValue<Float> accelPitchCrossMin =
            new NumberValue<>("accel_pitch_cross_min", 17f, 1f, 180f);
    private final NumberValue<Float> accelPitchCrossMax =
            new NumberValue<>("accel_pitch_cross_max", 20f, 1f, 180f);
    private final BooleanValue accelErrorEnabled =
            new BooleanValue("accel_error_enabled", true);
    private final NumberValue<Float> accelYawError =
            new NumberValue<>("accel_yaw_error", 0.1f, 0.01f, 1f);
    private final NumberValue<Float> accelPitchError =
            new NumberValue<>("accel_pitch_error", 0.1f, 0.01f, 1f);
    private final BooleanValue constantErrorEnabled =
            new BooleanValue("constant_error_enabled", true);
    private final NumberValue<Float> constantYawError =
            new NumberValue<>("constant_yaw_error", 0.1f, 0.01f, 1f);
    private final NumberValue<Float> constantPitchError =
            new NumberValue<>("constant_pitch_error", 0.1f, 0.01f, 1f);
    private final BooleanValue sigmoidDecelEnabled =
            new BooleanValue("sigmoid_decel_enabled", false);
    private final NumberValue<Float> sigmoidDecelSteepness =
            new NumberValue<>("sigmoid_decel_steepness", 10f, 0.0f, 20f);
    private final NumberValue<Float> sigmoidDecelMidpoint =
            new NumberValue<>("sigmoid_decel_midpoint", 0.3f, 0.0f, 1.0f);
    private final ScaffoldMovementPlanner movementPlanner = new ScaffoldMovementPlanner();
    private final ScaffoldMovementPrediction movementPrediction = new ScaffoldMovementPrediction();
    private final InterpolationAngleSmooth interpolationSmooth = new InterpolationAngleSmooth(
            interpHorMin, interpHorMax, interpVertMin, interpVertMax, interpDirMin, interpDirMax, interpMidpoint
    );
    private final LinearAngleSmooth linearSmooth = new LinearAngleSmooth(
            linearHorMin, linearHorMax, linearVertMin, linearVertMax
    );
    private final SigmoidAngleSmooth sigmoidSmooth = new SigmoidAngleSmooth(
            sigmoidHorMin, sigmoidHorMax, sigmoidVertMin, sigmoidVertMax,
            sigmoidSteepness, sigmoidMidpoint
    );
    private final AccelerationAngleSmooth accelerationSmooth = new AccelerationAngleSmooth(
            accelYawMin, accelYawMax, accelPitchMin, accelPitchMax,
            accelDynamicEnabled, accelCoefDistance,
            accelYawCrossMin, accelYawCrossMax, accelPitchCrossMin, accelPitchCrossMax,
            accelErrorEnabled, accelYawError, accelPitchError,
            constantErrorEnabled, constantYawError, constantPitchError,
            sigmoidDecelEnabled, sigmoidDecelSteepness, sigmoidDecelMidpoint
    );
    private ScaffoldPlacementTarget currentTarget;
    private int placementY;
    private int delayLeft;
    private boolean wasPlaced;
    private int placedBlocksSinceEagleReset;
    private int towerAirTicks;
    private double towerJumpBaseY = Double.NaN;
    private boolean rawForward;
    private boolean rawBackward;
    private boolean rawLeft;
    private boolean rawRight;
    private int forceSneakTicks;
    private int normalAngleSmoothLedgeHoldTicks;
    private int strafeMoveTicks;
    private int ticksUntilTellyJump;
    private int tellyJumpTicks;
    private ItemStack indicatorStack = ItemStack.EMPTY;
    private boolean indicatorVisible;
    private float indicatorX;
    private float indicatorY;
    private float indicatorSize;
    private float indicatorScaleFactor;
    private String indicatorCountText = "";
    private float indicatorCountX;
    private float indicatorCountY;
    private float indicatorTextScale;
    private int indicatorCountValue;
    private boolean indicatorRenderItem;
    private BlockPos lastPlacedRenderPos;
    private long lastPlacedRenderMillis;
    private long debugAttemptCounter;
    private BlockPos debugLastTargetPos;
    private BlockPos debugPendingPlacedPos;
    private int debugPendingPlacedTicks;
    private long debugPendingPlacedAttempt;
    private boolean debugPendingPlacedSeenSolid;
    private ScaffoldLine currentOptimalLine;

    {
        declareSettings();
    }

    private static List<BlockPos> commonOffsets(int... xzOffsets) {
        List<BlockPos> list = new ArrayList<>(xzOffsets.length * xzOffsets.length * 2);
        for (int x : xzOffsets) {
            for (int z : xzOffsets) {
                list.add(new BlockPos(x, 0, z));
                list.add(new BlockPos(x, -1, z));
            }
        }
        return List.copyOf(list);
    }

    private static int withAlphaScale(int argb, float scale) {
        int alpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = Mth.clamp((int) (alpha * scale), 0, 255);
        return (argb & 0x00FFFFFF) | (scaledAlpha << 24);
    }

    private static int getPillCountColor(int count) {
        int low = 0xFFFF5C5C;
        int mid = 0xFFDCE61E;
        int high = 0xFF63F27D;

        if (count <= 64) {
            float t = Mth.clamp(count / 64.0f, 0.0f, 1.0f);
            return lerpColor(low, mid, t);
        }

        float t = Mth.clamp((count - 64) / 64.0f, 0.0f, 1.0f);
        return lerpColor(mid, high, t);
    }

    private static int lerpColor(int from, int to, float t) {
        int a1 = (from >>> 24) & 0xFF;
        int r1 = (from >>> 16) & 0xFF;
        int g1 = (from >>> 8) & 0xFF;
        int b1 = from & 0xFF;

        int a2 = (to >>> 24) & 0xFF;
        int r2 = (to >>> 16) & 0xFF;
        int g2 = (to >>> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = Mth.lerpInt(t, a1, a2);
        int r = Mth.lerpInt(t, r1, r2);
        int g = Mth.lerpInt(t, g1, g2);
        int b = Mth.lerpInt(t, b1, b2);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int colorWithAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
    }

    private static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void debugLog(String pattern, Object... args) {
        if (!DEBUG_LOGS) {
        }
        // DebugLog.info("[ScaffoldDebug] " + pattern, args);
    }

    private static String describeTarget(ScaffoldPlacementTarget target) {
        if (target == null) return "null";
        return "placed=" + target.getPlacedBlockPos()
                + " interact=" + target.getInteractedBlockPos()
                + " side=" + target.getDirection()
                + " rot=" + describeRotation(target.getRotation());
    }

    private static String describeHit(BlockHitResult hit) {
        if (hit == null) return "null";
        return "block=" + hit.getBlockPos()
                + " side=" + hit.getDirection()
                + " pos=" + formatVec(hit.getLocation());
    }

    private static String describeRotation(Rotation rotation) {
        if (rotation == null) return "null";
        return String.format(java.util.Locale.ROOT, "(yaw=%.2f,pitch=%.2f)", rotation.yaw(), rotation.pitch());
    }

    private static String formatVec(Vec3 vec) {
        if (vec == null) return "null";
        return String.format(java.util.Locale.ROOT, "(%.3f,%.3f,%.3f)", vec.x, vec.y, vec.z);
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.LAST;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    private void declareSettings() {
        List<SettingDef> defs = new ArrayList<>();
        defs.add(SettingDef.mode(rotationTiming));
        defs.add(SettingDef.mode(technique));
        defs.add(SettingDef.mode(sameY));
        defs.add(SettingDef.mode(movementCorrection).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL));
        defs.add(SettingDef.mode(angleSmoothMode).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL));
        defs.add(SettingDef.number(linearHorMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.LINEAR));
        defs.add(SettingDef.number(linearHorMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.LINEAR));
        defs.add(SettingDef.number(linearVertMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.LINEAR));
        defs.add(SettingDef.number(linearVertMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.LINEAR));
        defs.add(SettingDef.number(sigmoidHorMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.SIGMOID));
        defs.add(SettingDef.number(sigmoidHorMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.SIGMOID));
        defs.add(SettingDef.number(sigmoidVertMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.SIGMOID));
        defs.add(SettingDef.number(sigmoidVertMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.SIGMOID));
        defs.add(SettingDef.number(sigmoidSteepness).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.SIGMOID));
        defs.add(SettingDef.number(sigmoidMidpoint).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.SIGMOID));
        defs.add(SettingDef.number(interpHorMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.INTERPOLATION));
        defs.add(SettingDef.number(interpHorMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.INTERPOLATION));
        defs.add(SettingDef.number(interpVertMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.INTERPOLATION));
        defs.add(SettingDef.number(interpVertMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.INTERPOLATION));
        defs.add(SettingDef.number(interpDirMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.INTERPOLATION));
        defs.add(SettingDef.number(interpDirMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.INTERPOLATION));
        defs.add(SettingDef.number(interpMidpoint).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.INTERPOLATION));
        defs.add(SettingDef.number(accelYawMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION));
        defs.add(SettingDef.number(accelYawMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION));
        defs.add(SettingDef.number(accelPitchMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION));
        defs.add(SettingDef.number(accelPitchMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION));
        defs.add(SettingDef.bool(accelDynamicEnabled).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION));
        defs.add(SettingDef.number(accelCoefDistance).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && accelDynamicEnabled.get()));
        defs.add(SettingDef.number(accelYawCrossMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && accelDynamicEnabled.get()));
        defs.add(SettingDef.number(accelYawCrossMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && accelDynamicEnabled.get()));
        defs.add(SettingDef.number(accelPitchCrossMin).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && accelDynamicEnabled.get()));
        defs.add(SettingDef.number(accelPitchCrossMax).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && accelDynamicEnabled.get()));
        defs.add(SettingDef.bool(accelErrorEnabled).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION));
        defs.add(SettingDef.number(accelYawError).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && accelErrorEnabled.get()));
        defs.add(SettingDef.number(accelPitchError).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && accelErrorEnabled.get()));
        defs.add(SettingDef.bool(constantErrorEnabled).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION));
        defs.add(SettingDef.number(constantYawError).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && constantErrorEnabled.get()));
        defs.add(SettingDef.number(constantPitchError).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && constantErrorEnabled.get()));
        defs.add(SettingDef.bool(sigmoidDecelEnabled).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION));
        defs.add(SettingDef.number(sigmoidDecelSteepness).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && sigmoidDecelEnabled.get()));
        defs.add(SettingDef.number(sigmoidDecelMidpoint).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL && angleSmoothMode.get() == AngleSmoothMode.ACCELERATION && sigmoidDecelEnabled.get()));
        defs.add(SettingDef.bool(autoBlock));
        defs.add(SettingDef.bool(renderPlacement));
        defs.add(SettingDef.color(renderFillColor).visibleWhen(renderPlacement::get));
        defs.add(SettingDef.color(renderLineColor).visibleWhen(renderPlacement::get));
        defs.add(SettingDef.number(renderLineWidth).visibleWhen(renderPlacement::get));
        defs.add(SettingDef.number(renderBobAmplitude).visibleWhen(renderPlacement::get));
        defs.add(SettingDef.number(renderBobSpeed).visibleWhen(renderPlacement::get));
        defs.add(SettingDef.number(renderPlaceAnimTime).visibleWhen(renderPlacement::get));
        defs.add(SettingDef.bool(placementIndicator));
        defs.add(SettingDef.mode(placementIndicatorStyle).visibleWhen(placementIndicator::get));
        defs.add(SettingDef.mode(placementIndicatorSide).visibleWhen(placementIndicator::get));
        defs.add(SettingDef.mode(placementIndicatorCountMode).visibleWhen(placementIndicator::get));
        defs.add(SettingDef.number(placementIndicatorScale).visibleWhen(placementIndicator::get));
        defs.add(SettingDef.number(placementIndicatorOffsetX).visibleWhen(placementIndicator::get));
        defs.add(SettingDef.number(placementIndicatorOffsetY).visibleWhen(placementIndicator::get));
        defs.add(SettingDef.bool(placementIndicatorBackground).visibleWhen(placementIndicator::get));
        defs.add(SettingDef.bool(autoBlockAlways).visibleWhen(autoBlock::get));
        defs.add(SettingDef.number(autoBlockSlotResetDelay).visibleWhen(autoBlock::get));
        defs.add(SettingDef.number(autoBlockDoNotUseBelowCount).visibleWhen(autoBlock::get));
        defs.add(SettingDef.bool(prediction));
        defs.add(SettingDef.bool(considerInventory).visibleWhen(() -> rotationTiming.get() == RotationTiming.NORMAL));
        defs.add(SettingDef.number(delay));
        defs.add(SettingDef.bool(down));
        defs.add(SettingDef.bool(eagle));
        defs.add(SettingDef.number(blocksToEagle).visibleWhen(eagle::get));
        defs.add(SettingDef.number(eagleEdgeDistance).visibleWhen(eagle::get));
        defs.add(SettingDef.bool(eagleOnlyOnGround).visibleWhen(eagle::get));
        defs.add(SettingDef.number(expandLength).visibleWhen(() -> technique.get() == Technique.EXPAND));
        defs.add(SettingDef.bool(acceleration));
        defs.add(SettingDef.number(accelerationMultiplier).visibleWhen(acceleration::get));
        defs.add(SettingDef.bool(accelerationOnlyOnGround).visibleWhen(acceleration::get));
        defs.add(SettingDef.bool(strafe));
        defs.add(SettingDef.number(strafeSpeed).visibleWhen(strafe::get));
        defs.add(SettingDef.bool(strafeHypixel).visibleWhen(strafe::get));
        defs.add(SettingDef.bool(strafeOnlyOnGround).visibleWhen(strafe::get));
        defs.add(SettingDef.bool(jumpStrafe));
        defs.add(SettingDef.number(jumpStrafeStraightMin).visibleWhen(jumpStrafe::get));
        defs.add(SettingDef.number(jumpStrafeStraightMax).visibleWhen(jumpStrafe::get));
        defs.add(SettingDef.number(jumpStrafeDiagonalMin).visibleWhen(jumpStrafe::get));
        defs.add(SettingDef.number(jumpStrafeDiagonalMax).visibleWhen(jumpStrafe::get));
        defs.add(SettingDef.bool(speedLimiter));
        defs.add(SettingDef.number(speedLimit).visibleWhen(speedLimiter::get));
        defs.add(SettingDef.bool(stabilizeMovement).visibleWhen(() -> technique.get() == Technique.NORMAL));
        defs.add(SettingDef.bool(telly).visibleWhen(() -> technique.get() == Technique.NORMAL));
        defs.add(SettingDef.mode(aimMode).visibleWhen(() -> technique.get() == Technique.NORMAL));
        defs.add(SettingDef.mode(tellyResetMode).visibleWhen(() -> technique.get() == Technique.NORMAL && telly.get()));
        defs.add(SettingDef.number(tellyStraightTicks).visibleWhen(() -> technique.get() == Technique.NORMAL && telly.get()));
        defs.add(SettingDef.number(tellyJumpTicksMin).visibleWhen(() -> technique.get() == Technique.NORMAL && telly.get()));
        defs.add(SettingDef.number(tellyJumpTicksMax).visibleWhen(() -> technique.get() == Technique.NORMAL && telly.get()));
        defs.add(SettingDef.bool(tellyAimOnTower).visibleWhen(() -> technique.get() == Technique.NORMAL && telly.get()));
        defs.add(SettingDef.bool(ceiling).visibleWhen(() -> technique.get() == Technique.NORMAL));
        defs.add(SettingDef.bool(headHitter).visibleWhen(() -> technique.get() == Technique.NORMAL));
        defs.add(SettingDef.mode(towerMode));
        defs.add(SettingDef.number(towerMotion).visibleWhen(() -> towerMode.get() == TowerMode.MOTION));
        defs.add(SettingDef.number(towerTriggerHeight).visibleWhen(() -> towerMode.get() == TowerMode.MOTION));
        defs.add(SettingDef.number(towerSlow).visibleWhen(() -> towerMode.get() == TowerMode.MOTION));
        defs.add(SettingDef.mode(clientSprintMode));
        defs.add(SettingDef.mode(serverSprintMode));
        defs.forEach(this::setting);
    }

    @Override
    public void onEnable() {
        LocalPlayer player = player();
        if (player == null) return;
        placementY = player.getBlockY() - 1;
        delayLeft = 0;
        wasPlaced = false;
        currentTarget = null;
        placedBlocksSinceEagleReset = 0;
        towerAirTicks = 0;
        towerJumpBaseY = Double.NaN;
        forceSneakTicks = 0;
        normalAngleSmoothLedgeHoldTicks = 0;
        strafeMoveTicks = 0;
        ticksUntilTellyJump = 0;
        tellyJumpTicks = randomTellyJumpTicks();
        currentOptimalLine = null;
        indicatorStack = ItemStack.EMPTY;
        indicatorVisible = false;
        indicatorCountText = "";
        indicatorCountValue = 0;
        indicatorRenderItem = false;
        lastPlacedRenderPos = null;
        lastPlacedRenderMillis = 0L;
        debugPendingPlacedPos = null;
        debugPendingPlacedTicks = 0;
        debugPendingPlacedAttempt = 0L;
        debugPendingPlacedSeenSolid = false;
        movementPlanner.reset();
        movementPrediction.reset();
    }

    @Override
    public void onDisable() {
        InventorySwap.INSTANCE.releaseHotbar(this);
        currentTarget = null;
        wasPlaced = false;
        delayLeft = 0;
        placedBlocksSinceEagleReset = 0;
        towerAirTicks = 0;
        towerJumpBaseY = Double.NaN;
        forceSneakTicks = 0;
        normalAngleSmoothLedgeHoldTicks = 0;
        strafeMoveTicks = 0;
        ticksUntilTellyJump = 0;
        tellyJumpTicks = 0;
        currentOptimalLine = null;
        indicatorStack = ItemStack.EMPTY;
        indicatorVisible = false;
        indicatorCountText = "";
        indicatorCountValue = 0;
        indicatorRenderItem = false;
        lastPlacedRenderPos = null;
        lastPlacedRenderMillis = 0L;
        debugPendingPlacedPos = null;
        debugPendingPlacedTicks = 0;
        debugPendingPlacedAttempt = 0L;
        debugPendingPlacedSeenSolid = false;
        movementPlanner.reset();
        movementPrediction.reset();
        RotationManager.INSTANCE.release(this);
    }

    public boolean shouldSuppressOmniDirectionalSprint() {
        return isEnabled() && (currentTarget != null || wasPlaced);
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!renderPlacement.get()) return;

        LocalPlayer player = player();
        Minecraft mc = Minecraft.getInstance();
        if (player == null || mc.level == null) return;

        RenderPreview preview = getRenderPreview(tickDelta);
        if (preview == null) return;

        AABB box = animatedRenderBox(preview.pos, preview.progress, tickDelta);
        int fill = withAlphaScale(renderFillColor.getArgb(), preview.progress);
        int line = withAlphaScale(renderLineColor.getArgb(), preview.progress);

        float prevWidth = RenderState.lineWidth;
        RenderState.lineWidth = Math.max(1.0f, renderLineWidth.get());
        try {
            addFilledBox(renderer, box, fill);
            addOutlineBox(renderer, box, line);
        } finally {
            RenderState.lineWidth = prevWidth;
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = player();
        if (player == null) return;

        if (debugPendingPlacedPos != null) {
            BlockState state = player.level().getBlockState(debugPendingPlacedPos);
            boolean solid = !state.isAir();
            if (solid) {
                debugPendingPlacedSeenSolid = true;
            } else if (debugPendingPlacedSeenSolid) {
                debugLog(
                        "post-place-rollback attempt#%d pos=%s playerPos=%s vel=%s current=%s server=%s",
                        debugPendingPlacedAttempt,
                        debugPendingPlacedPos,
                        formatVec(player.position()),
                        formatVec(player.getDeltaMovement()),
                        describeRotation(RotationManager.INSTANCE.getCurrentRotation()),
                        describeRotation(RotationManager.INSTANCE.getServerRotation())
                );
                debugPendingPlacedPos = null;
                debugPendingPlacedTicks = 0;
                debugPendingPlacedAttempt = 0L;
                debugPendingPlacedSeenSolid = false;
            } else if (debugPendingPlacedTicks == 2) {
                debugLog(
                        "post-place-missing attempt#%d pos=%s playerPos=%s vel=%s current=%s server=%s",
                        debugPendingPlacedAttempt,
                        debugPendingPlacedPos,
                        formatVec(player.position()),
                        formatVec(player.getDeltaMovement()),
                        describeRotation(RotationManager.INSTANCE.getCurrentRotation()),
                        describeRotation(RotationManager.INSTANCE.getServerRotation())
                );
            }

            if (debugPendingPlacedPos != null) {
                debugPendingPlacedTicks--;
                if (debugPendingPlacedTicks <= 0) {
                    debugPendingPlacedPos = null;
                    debugPendingPlacedAttempt = 0L;
                    debugPendingPlacedSeenSolid = false;
                }
            }
        }

        if (delayLeft > 0) delayLeft--;
        InventorySwap.INSTANCE.tick();
        if (player.onGround()) {
            placementY = player.getBlockY() - 1;
            towerAirTicks = 0;
            ticksUntilTellyJump++;
        } else {
            towerAirTicks++;
        }
        if (wasPlaced) {
            wasPlaced = false;
        }

        handleTower(player);
        handleHeadHitter(player);
        handleAcceleration(player);
        handleStrafe(player);
        updateStrafeMoveTicks(player);
    }

    @Override
    public void onRenderHudEngine(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        indicatorVisible = false;
        indicatorCountText = "";
        indicatorCountValue = 0;
        indicatorRenderItem = false;
        if (!placementIndicator.get() || ctx == null || !isEnabled()) return;

        LocalPlayer player = player();
        if (player == null) return;

        PlacementIndicatorData data = getPlacementIndicatorData(player);
        if (data == null || data.stack().isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        int fbw = client.getWindow().getWidth();
        int fbh = client.getWindow().getHeight();

        ViewportContext.beginUnscaledLogical(ctx);
        try {
            float centerX = HudScale.virtualWidth(fbw, fbh) * 0.5f;
            float centerY = HudScale.virtualHeight(fbw, fbh) * 0.5f;
            BetterTooltips tooltips = BetterTooltips.get();
            float userScale = placementIndicatorScale.get().floatValue();
            float renderScale = userScale * 3.0f;
            float slotSize = (tooltips != null ? tooltips.shulkerPreviewSlotSize() : 18.0f) * renderScale;
            float slotRadius = (tooltips != null ? tooltips.shulkerPreviewSlotRadius() : 5.0f) * renderScale;
            float slotSoftness = (tooltips != null ? tooltips.shulkerPreviewSlotSoftness() : 1.0f) * renderScale;
            float stroke = 0.35f * renderScale;
            float offsetX = placementIndicatorOffsetX.get().floatValue();
            float offsetY = placementIndicatorOffsetY.get().floatValue();
            boolean pill = placementIndicatorStyle.get() == IndicatorStyle.PILL;
            float textScale = pill ? 1.08f * renderScale : 0.86f * renderScale;
            TextRenderer countRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, textRenderer);
            float countWidth = data.countText().isEmpty() ? 0.0f : (float) countRenderer.getWidth(data.countText(), false) * textScale;
            float textHeight = (float) countRenderer.getHeight(false) * textScale;
            float pillPadX = 3.0f * renderScale;
            float pillPadY = renderScale;
            float bodyWidth = pill
                    ? Math.max(10.0f * renderScale, countWidth + pillPadX * 2.0f)
                    : slotSize;
            float bodyHeight = pill
                    ? textHeight + pillPadY * 2.0f
                    : slotSize;
            float bodyRadius = placementIndicatorStyle.get() == IndicatorStyle.PILL
                    ? bodyHeight * 0.5f
                    : slotRadius;

            float x;
            float y;
            switch (placementIndicatorSide.get()) {
                case RIGHT -> {
                    x = centerX + offsetX;
                    y = centerY - bodyHeight * 0.5f + offsetY;
                }
                case TOP -> {
                    x = centerX - bodyWidth * 0.5f + offsetX;
                    y = centerY - bodyHeight + offsetY;
                }
                case BOTTOM -> {
                    x = centerX - bodyWidth * 0.5f + offsetX;
                    y = centerY + offsetY;
                }
                case LEFT -> {
                    x = centerX - bodyWidth - offsetX;
                    y = centerY - bodyHeight * 0.5f + offsetY;
                }
                default -> {
                    x = centerX - bodyWidth - offsetX;
                    y = centerY - bodyHeight * 0.5f + offsetY;
                }
            }

            if (placementIndicatorBackground.get()) {
                if (pill) {
                    renderer.roundedRect(x, y, bodyWidth, bodyHeight, bodyRadius, 0.0f, 0xD0101010);
                } else {
                    int slotRgb = tooltips != null ? tooltips.shulkerPreviewSlotBgRgb() : 0x202020;
                    int borderRgb = tooltips != null ? tooltips.shulkerPreviewSlotBorderRgb() : 0x2F2F2F;
                    int borderAlpha = tooltips != null ? tooltips.shulkerPreviewSlotBorderAlpha() : 160;
                    renderer.roundedRect(x, y, bodyWidth, bodyHeight, bodyRadius, slotSoftness, colorWithAlpha(slotRgb, 0xFF));
                    renderer.roundedRectStroke(x, y, bodyWidth, bodyHeight, bodyRadius, slotSoftness, stroke, colorWithAlpha(borderRgb, borderAlpha));
                }
            }

            indicatorStack = data.stack().copy();
            indicatorVisible = true;
            indicatorRenderItem = !pill;
            indicatorX = x;
            indicatorY = y;
            indicatorSize = bodyHeight;
            indicatorScaleFactor = renderScale;
            indicatorCountText = data.countText();
            indicatorCountValue = data.count();
            indicatorTextScale = textScale;
            if (pill && !indicatorCountText.isEmpty()) {
                indicatorCountX = x + (bodyWidth - countWidth) * 0.5f;
                indicatorCountY = y + (bodyHeight - textHeight) * 0.5f;
                countRenderer.begin(indicatorTextScale, false, false);
                countRenderer.render(indicatorCountText, indicatorCountX, indicatorCountY, new RenderColor(getPillCountColor(indicatorCountValue)), false);
                countRenderer.end();
            } else {
                indicatorCountX = x + bodyWidth - 3.0f * renderScale;
                indicatorCountY = y + bodyHeight - 2.0f * renderScale;
            }
        } finally {
            ViewportContext.beginUnscaled(ctx);
        }
    }

    @Override
    public void onRenderHudEngineForeground(Renderer2D renderer, TextRenderer textRenderer, GuiGraphicsExtractor ctx, float tickDelta) {
        if (!indicatorVisible || ctx == null) return;

        ViewportContext.beginUnscaledLogical(ctx);
        try {
            if (indicatorRenderItem && !indicatorStack.isEmpty()) {
                float iconSize = 16.0f * indicatorScaleFactor;
                float offset = (indicatorSize - iconSize) * 0.5f;
                renderer.item(
                        indicatorStack,
                        indicatorX + offset,
                        indicatorY + offset,
                        indicatorScaleFactor,
                        0,
                        Renderer2D.ITEM_OVERLAY_ALL,
                        null
                );
            }

            if (!indicatorCountText.isEmpty() && indicatorRenderItem) {
                float ratio = Renderer2D.getUnscaledItemRatio();
                TextRenderer countRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, textRenderer);
                countRenderer.begin(indicatorTextScale * ratio);
                double textX = indicatorCountX * ratio;
                double textY = indicatorCountY * ratio;
                double width = countRenderer.getWidth(indicatorCountText, false);
                textX -= width;
                textY -= countRenderer.getHeight(false);
                countRenderer.render(indicatorCountText, textX, textY, new RenderColor(0xFFFFFFFF), false);
                countRenderer.end();
            }
        } finally {
            ViewportContext.beginUnscaled(ctx);
        }
    }

    @EventHandler
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) {
            return;
        }

        LocalPlayer player = player();
        boolean hasTarget = refreshCurrentTarget(player);
        if (rotationTiming.get() != RotationTiming.NORMAL) return;

        if (!hasTarget) {
            requestNormalStrictMoonwalkRotation(player);
            return;
        }

        List<RotationProcessor> processors = buildRotationProcessors();
        ScaffoldPlacementTarget targetSnapshot = currentTarget;
        RotationTarget target = new RotationTarget(
                targetSnapshot.getRotation(),
                null,
                processors,
                2,
                4.0f,
                considerInventory.get(),
                movementCorrection.get(),
                new RestrictedSingleUseAction(() -> executeScheduledNormalPlacement(
                        targetSnapshot,
                        RotationManager.INSTANCE.getCurrentRotation() != null
                                ? RotationManager.INSTANCE.getCurrentRotation()
                                : targetSnapshot.getRotation(),
                        preparePlacementHand(player()),
                        resolveNormalScheduledHit(player(), targetSnapshot)
                ))
        );
        RotationManager.INSTANCE.setRotationTarget(target, 100, this);
    }

    private void requestNormalStrictMoonwalkRotation(LocalPlayer player) {
        if (player == null || technique.get() != Technique.NORMAL) return;
        if (movementCorrection.get() != MovementCorrection.STRICT) return;
        if (!hasRawInput() || blockCount(player) <= 0) return;

        float movementYaw = getRawMovementDirectionYaw(player, player.getYRot());
        Rotation moonwalkRotation = new Rotation(movementYaw + 180.0f, 75.0f, false).normalize();
        RotationTarget target = new RotationTarget(
                moonwalkRotation,
                null,
                buildRotationProcessors(),
                2,
                4.0f,
                considerInventory.get(),
                movementCorrection.get(),
                null
        );
        RotationManager.INSTANCE.setRotationTarget(target, 100, this);
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        LocalPlayer player = player();
        if (!canOperate(player)) return;

        rawForward = event.isForward();
        rawBackward = event.isBackward();
        rawLeft = event.isLeft();
        rawRight = event.isRight();
        currentOptimalLine = hasRawInput()
                ? movementPlanner.getOptimalMovementLine(player, getRawMovementDirectionYaw(player, player.getYRot()))
                : null;

        refreshCurrentTarget(player);

        if (handleNormalOnTickPlacement(player, event)) {
            return;
        }

        if (speedLimiter.get() && horizontalSpeed(player.getDeltaMovement()) > speedLimit.get()) {
            event.setForward(false);
            event.setBackward(false);
            event.setLeft(false);
            event.setRight(false);
            return;
        }

        handleStabilizeMovement(player, event);
        handleTelly(player, event);

        if (shouldGoDown(player)) {
            event.setSneak(false);
            return;
        }

        handleNormalStrictMoonwalkInput(player, event);
        handleNormalAngleSmoothLedge(player, event);

        if (forceSneakTicks > 0) {
            event.setSneak(true);
            forceSneakTicks--;
        }

        if (shouldEagle(player, event) && !event.isSneak()) {
            event.setSneak(true);
        }
    }

    @EventHandler
    private void onSync(EventSync event) {
    }

    @EventHandler
    private void onPostPlayerUpdate(PostPlayerUpdateEvent event) {
        LocalPlayer player = player();
        if (!canOperate(player) || rotationTiming.get() != RotationTiming.ON_TICK) return;
        executeOnTickPlacement(player);
    }

    private boolean handleNormalOnTickPlacement(LocalPlayer player, MovementInputEvent event) {
        if (player == null || event == null) return false;
        if (technique.get() != Technique.NORMAL || rotationTiming.get() != RotationTiming.ON_TICK) return false;
        if (shouldGoDown(player) || player.getAbilities().flying) return false;
        if (!normalOnTickPlacementRequired(player)) return false;

        boolean placed = executeOnTickPlacement(player);
        if (placed) {
            return false;
        }

        stopHorizontalInput(event);
        return true;
    }

    private boolean normalOnTickPlacementRequired(LocalPlayer player) {
        if (player == null || player.level() == null) return false;
        if (!hasRawInput()) return false;

        BlockPos targetPos = getTargetedPosition(player);
        if (targetPos == null || !player.level().isInWorldBounds(targetPos)) {
            return false;
        }

        return player.level().getBlockState(targetPos).canBeReplaced();
    }

    private void stopHorizontalInput(MovementInputEvent event) {
        if (event == null) return;
        event.setForward(false);
        event.setBackward(false);
        event.setLeft(false);
        event.setRight(false);
        event.setSprint(false);
    }

    private boolean executeOnTickPlacement(LocalPlayer player) {
        if (!canOperate(player) || currentTarget == null || delayLeft > 0) return false;
        if (isTargetStaleForPlayer(player, currentTarget)) {
            debugLog(
                    "target-cleared reason=stale-window target=%s playerPos=%s vel=%s onGround=%s",
                    describeTarget(currentTarget),
                    formatVec(player.position()),
                    formatVec(player.getDeltaMovement()),
                    player.onGround()
            );
            currentTarget = null;
            return false;
        }

        InteractionHand hand = preparePlacementHand(player);
        if (hand == null) return false;

        debugLog(
                "attempt#%d timing=%s tech=%s target=%s server=%s current=%s hand=%s delay=%d pos=%s vel=%s",
                ++debugAttemptCounter,
                EnumIds.defaultId(rotationTiming.get()),
                EnumIds.defaultId(technique.get()),
                describeTarget(currentTarget),
                describeRotation(RotationManager.INSTANCE.getServerRotation()),
                describeRotation(RotationManager.INSTANCE.getCurrentRotation()),
                hand,
                delayLeft,
                formatVec(player.position()),
                formatVec(player.getDeltaMovement())
        );

        Rotation currentRotation = currentTarget.getRotation();
        if (currentRotation == null) {
            return false;
        }

        Rotation serverRotation = RotationManager.INSTANCE.getServerRotation();
        if (serverRotation == null || !serverRotation.approximatelyEquals(currentRotation, 0.01f)) {
            debugLog(
                    "attempt#%d on-tick-pre-look server=%s targetRot=%s",
                    debugAttemptCounter,
                    describeRotation(serverRotation),
                    describeRotation(currentRotation)
            );
            sendLookPacket(player, currentRotation);
        }

        BlockHitResult hitResult = resolvePlacementHit(player, currentTarget, currentRotation);
        if (!currentTarget.matches(hitResult) || !isValidCrosshairTarget(player, hitResult)) {
            return false;
        }

        boolean placed = tryPlace(player, hand, hitResult);
        if (!placed) {
            return false;
        }

        restoreOnTickPlayerRotation(player, currentRotation);
        return true;
    }

    private void restoreOnTickPlayerRotation(LocalPlayer player, Rotation scaffoldRotation) {
        if (player == null || scaffoldRotation == null) return;

        Rotation playerRotation = new Rotation(player.getYRot(), player.getXRot(), true);
        Rotation updatedServerRotation = RotationManager.INSTANCE.getServerRotation();
        if (updatedServerRotation == null || !updatedServerRotation.approximatelyEquals(playerRotation, 0.01f)) {
            debugLog(
                    "attempt#%d on-tick-post-restore server=%s player=%s",
                    debugAttemptCounter,
                    describeRotation(updatedServerRotation),
                    describeRotation(playerRotation)
            );
            sendLookPacket(player, new Rotation(
                    RotationUtil.withFixedYaw(player.getYRot(), scaffoldRotation.yaw()),
                    player.getXRot(),
                    true
            ));
        }
    }

    private void executeScheduledNormalPlacement(
            ScaffoldPlacementTarget target,
            Rotation rotation,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        LocalPlayer player = player();
        if (!isEnabled() || player == null || target == null || hitResult == null || hand == null || delayLeft > 0) {
            return;
        }
        if (currentTarget != target) {
            if (!samePlacementTarget(currentTarget, target)) {
                currentTarget = target;
            }
        }
        if (isTargetStaleForPlayer(player, target)) {
            currentTarget = null;
            return;
        }

        long attemptId = ++debugAttemptCounter;
        debugLog(
                "attempt#%d timing=%s tech=%s target=%s server=%s current=%s hand=%s delay=%d pos=%s vel=%s",
                attemptId,
                EnumIds.defaultId(rotationTiming.get()),
                EnumIds.defaultId(technique.get()),
                describeTarget(target),
                describeRotation(RotationManager.INSTANCE.getServerRotation()),
                describeRotation(rotation),
                hand,
                delayLeft,
                formatVec(player.position()),
                formatVec(player.getDeltaMovement())
        );

        tryPlace(player, hand, hitResult);
    }

    private boolean samePlacementTarget(ScaffoldPlacementTarget a, ScaffoldPlacementTarget b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getPlacedBlockPos().equals(b.getPlacedBlockPos())
                && a.getInteractedBlockPos().equals(b.getInteractedBlockPos())
                && a.getDirection() == b.getDirection();
    }

    private BlockHitResult resolveNormalScheduledHit(LocalPlayer player, ScaffoldPlacementTarget target) {
        if (player == null || target == null) {
            return null;
        }

        Rotation currentRotation = RotationManager.INSTANCE.getCurrentRotation();
        if (currentRotation == null) {
            currentRotation = target.getRotation();
        }
        if (currentRotation == null) {
            currentRotation = new Rotation(player.getYRot(), player.getXRot(), true);
        }

        return switch (technique.get()) {
            case NORMAL -> getNormalCrosshairTarget(player, target, currentRotation);
            default -> getTechniqueCrosshairTarget(player, target, currentRotation);
        };
    }

    private BlockHitResult resolvePlacementHit(
            LocalPlayer player,
            ScaffoldPlacementTarget target,
            Rotation currentRotation
    ) {
        if (player == null || target == null || currentRotation == null) {
            return null;
        }

        BlockHitResult hitResult = switch (technique.get()) {
            case NORMAL -> getNormalCrosshairTarget(player, target, currentRotation);
            default -> getTechniqueCrosshairTarget(player, target, currentRotation);
        };

        if (hitResult != null) {
            return hitResult;
        }

        if (technique.get() != Technique.NORMAL) {
            return null;
        }

        RotationTiming timing = rotationTiming.get();
        if (timing != RotationTiming.ON_TICK) {
            return null;
        }

        return getOnTickHorizontalFallbackHit(player, target);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        LocalPlayer player = player();
        if (!isEnabled() || player == null || towerMode.get() != TowerMode.VULCAN || !isTowering(player)) return;
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;
        if (isMoving(player)) return;

        ServerboundMovePlayerPacketAccessor accessor = (ServerboundMovePlayerPacketAccessor) packet;
        if (player.tickCount % 2 == 0) {
            accessor.combatant$setX(packet.getX(player.getX()) + 0.1);
            accessor.combatant$setZ(packet.getZ(player.getZ()) + 0.1);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        LocalPlayer player = player();
        if (!isEnabled() || player == null) return;
        if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket packet)) return;

        debugLog(
                "server-correct packetRot=%s playerPos=%s vel=%s target=%s current=%s server=%s",
                describeRotation(new Rotation(packet.change().yRot(), packet.change().xRot(), true)),
                formatVec(player.position()),
                formatVec(player.getDeltaMovement()),
                describeTarget(currentTarget),
                describeRotation(RotationManager.INSTANCE.getCurrentRotation()),
                describeRotation(RotationManager.INSTANCE.getServerRotation())
        );
    }

    @EventHandler
    private void onSprintControl(SprintControlEvent event) {
        if (!isEnabled()) return;

        SprintMode mode = switch (event.getSource()) {
            case INPUT, MOVEMENT_TICK -> clientSprintMode.get();
            case NETWORK -> serverSprintMode.get();
            default -> SprintMode.DO_NOT_CHANGE;
        };

        if (mode == SprintMode.DO_NOT_CHANGE) return;

        switch (mode) {
            case FORCE_SPRINT -> {
                if (event.isMoving()) {
                    event.setSprint(true);
                }
            }
            case FORCE_NO_SPRINT -> event.setSprint(false);
            case NO_SPRINT_ON_PLACE -> {
                if (wasPlaced) {
                    event.setSprint(false);
                }
            }
            case NO_SPRINT_ON_GROUND -> {
                LocalPlayer player = player();
                if (player != null) {
                    event.setSprint(!player.onGround());
                }
            }
            default -> {
            }
        }
    }

    private boolean tryPlace(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult) {
        if (player == null || currentTarget == null || hitResult == null) return false;

        Vec3 previousFallOffPos = currentOptimalLine != null
                ? movementPrediction.getFallOffPositionOnLine(player, currentOptimalLine)
                : null;

        InteractionResult result = Minecraft.getInstance().gameMode.useItemOn(
                player,
                hand,
                hitResult
        );
        if (result == null || !result.consumesAction()) {
            debugLog(
                    "attempt#%d place-fail result=%s hit=%s target=%s playerPos=%s vel=%s onGround=%s current=%s server=%s",
                    debugAttemptCounter,
                    result,
                    describeHit(hitResult),
                    describeTarget(currentTarget),
                    formatVec(player.position()),
                    formatVec(player.getDeltaMovement()),
                    player.onGround(),
                    describeRotation(RotationManager.INSTANCE.getCurrentRotation()),
                    describeRotation(RotationManager.INSTANCE.getServerRotation())
            );
            return false;
        }

        player.swing(hand);
        debugLog(
                "attempt#%d place-ok hit=%s target=%s",
                debugAttemptCounter,
                describeHit(hitResult),
                describeTarget(currentTarget)
        );
        wasPlaced = true;
        placedBlocksSinceEagleReset++;
        if (placedBlocksSinceEagleReset > blocksToEagle.get()) {
            placedBlocksSinceEagleReset = 0;
        }
        debugPendingPlacedPos = currentTarget.getPlacedBlockPos();
        debugPendingPlacedTicks = 3;
        debugPendingPlacedAttempt = debugAttemptCounter;
        debugPendingPlacedSeenSolid = false;
        movementPlanner.trackPlacedBlock(currentTarget.getPlacedBlockPos());
        movementPrediction.onPlace(currentOptimalLine, previousFallOffPos, player.position());
        normalAngleSmoothLedgeHoldTicks = 0;
        lastPlacedRenderPos = currentTarget.getPlacedBlockPos();
        lastPlacedRenderMillis = System.currentTimeMillis();
        delayLeft = delay.get();
        currentTarget = null;
        return true;
    }

    private BlockHitResult getNormalCrosshairTarget(
            LocalPlayer player,
            ScaffoldPlacementTarget target,
            Rotation currentRotation
    ) {
        if (player == null || target == null || currentRotation == null) return null;

        BlockHitResult crosshairTarget = traceScaffoldTarget(player, currentRotation);
        if (target.matches(crosshairTarget)) {
            return crosshairTarget;
        }

        return shouldGoDown(player) ? target.toHitResult() : null;
    }

    private BlockHitResult getTechniqueCrosshairTarget(
            LocalPlayer player,
            ScaffoldPlacementTarget target,
            Rotation currentRotation
    ) {
        if (player == null || target == null || currentRotation == null) return null;

        BlockHitResult crosshairTarget = traceScaffoldTarget(player, currentRotation);
        if (target.matches(crosshairTarget)) {
            return crosshairTarget;
        }

        return switch (technique.get()) {
            case EXPAND -> target.toHitResult();
            case NORMAL -> shouldGoDown(player) ? target.toHitResult() : null;
            default -> null;
        };
    }

    private boolean isValidCrosshairTarget(LocalPlayer player, BlockHitResult hitResult) {
        return player != null && isValidCrosshairTarget(player.getEyePosition(), hitResult);
    }

    private boolean isValidCrosshairTarget(Vec3 eyePos, BlockHitResult hitResult) {
        if (eyePos == null || hitResult == null) return false;

        Vec3 diff = hitResult.getLocation().subtract(eyePos);
        Direction side = hitResult.getDirection();

        if (side.getAxis() != Direction.Axis.Y) {
            double dist = (side == Direction.NORTH || side == Direction.SOUTH) ? diff.z : diff.x;
            return !(Math.abs(dist) < minDist.get());
        }

        return true;
    }

    private boolean isTargetStaleForPlayer(LocalPlayer player, ScaffoldPlacementTarget target) {
        if (player == null || target == null) return false;
        if (player.onGround()) return false;
        if (player.getDeltaMovement().y >= 0.0) return false;

        Direction side = target.getDirection();
        if (side.getAxis() == Direction.Axis.Y) {
            return false;
        }

        double staleY = target.getInteractedBlockPos().getY() + 1.0;
        return player.getY() < staleY;
    }

    private BlockHitResult traceScaffoldTarget(LocalPlayer player, Rotation rotation) {
        if (player == null || rotation == null) return null;
        return traceScaffoldTarget(player, player.getEyePosition(), rotation);
    }

    private BlockHitResult getOnTickHorizontalFallbackHit(
            LocalPlayer player,
            ScaffoldPlacementTarget target
    ) {
        if (player == null || target == null) {
            return null;
        }
        if (target.getDirection().getAxis() == Direction.Axis.Y) {
            return null;
        }

        BlockHitResult fallbackHit = target.toHitResult();
        return isWithinBlockInteractionRange(player, fallbackHit.getLocation()) ? fallbackHit : null;
    }

    private boolean isWithinBlockInteractionRange(LocalPlayer player, Vec3 point) {
        if (player == null || point == null) {
            return false;
        }
        double range = player.blockInteractionRange();
        return player.getEyePosition().distanceToSqr(point) <= range * range;
    }

    private BlockHitResult traceScaffoldTarget(LocalPlayer player, Vec3 eyes, Rotation rotation) {
        if (player == null || eyes == null || rotation == null || player.level() == null) return null;

        double range = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
        Vec3 end = eyes.add(rotation.directionVector().scale(range));
        HitResult hitResult = player.level().clip(new ClipContext(
                eyes,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        return hitResult instanceof BlockHitResult blockHitResult ? blockHitResult : null;
    }

    @EventHandler
    private void onPlayerJump(PlayerJumpEvent event) {
        LocalPlayer player = player();
        if (!isEnabled() || player == null) return;

        ticksUntilTellyJump = 0;
        tellyJumpTicks = randomTellyJumpTicks();
        if (!jumpStrafe.get()) return;

        float direction = getRawMovementDirectionYaw(player, player.getYRot());
        boolean straight = Math.round(direction / 45.0f) * 45 % 90 == 0;
        double min = straight ? jumpStrafeStraightMin.get() : jumpStrafeDiagonalMin.get();
        double max = straight ? jumpStrafeStraightMax.get() : jumpStrafeDiagonalMax.get();
        double speed = randomRange(min, max);
        Vec3 strafeVelocity = MovementUtil.withStrafe(player.getDeltaMovement(), new Vec3(0.0, 0.0, 1.0), direction, speed, 1.0);
        player.setDeltaMovement(strafeVelocity.x, player.getDeltaMovement().y, strafeVelocity.z);
    }

    private InteractionHand preparePlacementHand(LocalPlayer player) {
        if (player == null) return null;
        if (ScaffoldBlockItemSelection.isValidBlock(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (ScaffoldBlockItemSelection.isValidBlock(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        if (!autoBlock.get()) return null;

        int bestSlot = ScaffoldBlockItemSelection.findBestHotbarSlot(
                player,
                autoBlockDoNotUseBelowCount.get(),
                InventorySwap.INSTANCE.serverSelectedSlot()
        );
        if (bestSlot < 0) return null;
        InventorySwap.INSTANCE.leaseHotbar(this, bestSlot, autoBlockSlotResetDelay.get());

        return ScaffoldBlockItemSelection.isValidBlock(player.getMainHandItem()) ? InteractionHand.MAIN_HAND : null;
    }

    private void restoreSelectedSlot() {
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    private ItemStack getPreferredPlacementStack(LocalPlayer player) {
        if (player == null) return ItemStack.EMPTY;
        if (ScaffoldBlockItemSelection.isValidBlock(player.getMainHandItem())) return player.getMainHandItem();
        if (ScaffoldBlockItemSelection.isValidBlock(player.getOffhandItem())) return player.getOffhandItem();
        if (!autoBlock.get()) return ItemStack.EMPTY;

        int bestSlot = ScaffoldBlockItemSelection.findBestHotbarSlot(
                player,
                autoBlockDoNotUseBelowCount.get(),
                InventorySwap.INSTANCE.serverSelectedSlot()
        );
        if (bestSlot < 0) return ItemStack.EMPTY;

        if (autoBlockAlways.get()) {
            InventorySwap.INSTANCE.leaseHotbar(this, bestSlot, autoBlockSlotResetDelay.get());
            if (ScaffoldBlockItemSelection.isValidBlock(player.getMainHandItem())) {
                return player.getMainHandItem();
            }
        }

        return player.getInventory().getItem(bestSlot);
    }

    private ItemStack peekPlacementIndicatorStack(LocalPlayer player) {
        if (player == null) return ItemStack.EMPTY;
        if (ScaffoldBlockItemSelection.isValidBlock(player.getMainHandItem())) return player.getMainHandItem();
        if (ScaffoldBlockItemSelection.isValidBlock(player.getOffhandItem())) return player.getOffhandItem();
        if (!autoBlock.get()) return ItemStack.EMPTY;

        int silentSlot = InventorySwap.INSTANCE.isHotbarLeasedBy(this) ? InventorySwap.INSTANCE.serverSelectedSlot() : -1;
        if (silentSlot >= 0 && silentSlot <= 8) {
            ItemStack stack = player.getInventory().getItem(silentSlot);
            if (ScaffoldBlockItemSelection.isValidBlock(stack)) {
                return stack;
            }
        }

        int bestSlot = ScaffoldBlockItemSelection.findBestHotbarSlot(
                player,
                autoBlockDoNotUseBelowCount.get(),
                InventorySwap.INSTANCE.serverSelectedSlot()
        );
        return bestSlot >= 0 ? player.getInventory().getItem(bestSlot) : ItemStack.EMPTY;
    }

    private PlacementIndicatorData getPlacementIndicatorData(LocalPlayer player) {
        ItemStack stack = peekPlacementIndicatorStack(player);
        if (stack.isEmpty()) return null;

        int count = getPlacementIndicatorCount(player, stack);
        return new PlacementIndicatorData(stack, Math.max(count, 0), String.valueOf(Math.max(count, 0)));
    }

    private int getPlacementIndicatorCount(LocalPlayer player, ItemStack stack) {
        if (player == null || stack.isEmpty()) return 0;

        return switch (placementIndicatorCountMode.get()) {
            case CURRENT_STACK -> stack.getCount();
            case SAME_ITEM -> countAvailableScaffoldBlocks(player, candidate ->
                    ItemStack.isSameItemSameComponents(candidate, stack));
            case ALL_PLACEABLE -> countAvailableScaffoldBlocks(player, ScaffoldBlockItemSelection::isValidBlock);
        };
    }

    private int countAvailableScaffoldBlocks(LocalPlayer player, java.util.function.Predicate<ItemStack> predicate) {
        int count = 0;

        ItemStack offhand = player.getOffhandItem();
        if (predicate.test(offhand)) {
            count += offhand.getCount();
        }

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (predicate.test(stack)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private RenderPreview getRenderPreview(float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        if (currentTarget != null) {
            BlockPos pos = currentTarget.getPlacedBlockPos();
            if (mc.level.getBlockState(pos).canBeReplaced()) {
                return new RenderPreview(pos, 1.0f);
            }
        }

        if (lastPlacedRenderPos == null) return null;

        double elapsed = (System.currentTimeMillis() - lastPlacedRenderMillis) / 1000.0;
        double duration = renderPlaceAnimTime.get();
        if (elapsed >= duration) {
            lastPlacedRenderPos = null;
            return null;
        }

        float progress = (float) (1.0 - (elapsed / duration));
        return new RenderPreview(lastPlacedRenderPos, Mth.clamp(progress, 0.0f, 1.0f));
    }

    private AABB animatedRenderBox(BlockPos pos, float progress, float tickDelta) {
        double time = (System.currentTimeMillis() / 1000.0) * renderBobSpeed.get();
        double bob = Math.sin((time + tickDelta) * Math.PI) * renderBobAmplitude.get() * progress;
        double inset = 0.06 * (1.0 - progress);

        return new AABB(
                pos.getX() + inset,
                pos.getY() + inset + bob,
                pos.getZ() + inset,
                pos.getX() + 1.0 - inset,
                pos.getY() + 1.0 - inset + bob,
                pos.getZ() + 1.0 - inset
        );
    }

    private BlockPos getTargetedPosition(LocalPlayer player) {
        Vec3 predictedPos = getPlacementBasePosition(player);
        BlockPos basePos = BlockPos.containing(predictedPos);
        if (shouldGoDown(player)) {
            return basePos.below(2);
        }
        if (canConstructCeiling(player)) {
            return basePos.above(3);
        }
        if (Minecraft.getInstance().options.keyJump.isDown()
                && (!isMoving(player) || player.horizontalCollision)) {
            return basePos.below();
        }

        return switch (sameY.get()) {
            case ON -> new BlockPos(basePos.getX(), placementY, basePos.getZ());
            case FALLING -> player.getDeltaMovement().y < 0.2
                    ? new BlockPos(basePos.getX(), placementY, basePos.getZ())
                    : basePos.below();
            case OFF -> basePos.below();
        };
    }

    private ScaffoldPlacementTarget findPlacementTarget(LocalPlayer player, ItemStack stack) {
        return switch (technique.get()) {
            case EXPAND -> findExpandPlacementTarget(player, stack);
            case NORMAL -> findNormalPlacementTarget(player, stack);
        };
    }

    private boolean refreshCurrentTarget(LocalPlayer player) {
        if (!canOperate(player)) {
            currentTarget = null;
            if (debugLastTargetPos != null) {
                debugLog("target-cleared reason=canOperate-false");
                debugLastTargetPos = null;
            }
            return false;
        }

        ItemStack stack = getPreferredPlacementStack(player);
        if (!ScaffoldBlockItemSelection.isValidBlock(stack)) {
            currentTarget = null;
            if (debugLastTargetPos != null) {
                debugLog("target-cleared reason=no-valid-stack");
                debugLastTargetPos = null;
            }
            return false;
        }

        currentTarget = findPlacementTarget(player, stack);
        BlockPos currentPos = currentTarget != null ? currentTarget.getPlacedBlockPos() : null;
        if (currentPos == null) {
            if (debugLastTargetPos != null) {
                debugLog("target-cleared reason=findPlacementTarget-null");
                debugLastTargetPos = null;
            }
        } else if (!currentPos.equals(debugLastTargetPos)) {
            debugLog(
                    "target-acquired tech=%s timing=%s target=%s playerPos=%s vel=%s",
                    EnumIds.defaultId(technique.get()),
                    EnumIds.defaultId(rotationTiming.get()),
                    describeTarget(currentTarget),
                    formatVec(player.position()),
                    formatVec(player.getDeltaMovement())
            );
            debugLastTargetPos = currentPos;
        }
        return currentTarget != null;
    }

    private boolean shouldKeepPreviousTarget(
            LocalPlayer player,
            ScaffoldPlacementTarget previousTarget,
            ScaffoldPlacementTarget nextTarget
    ) {
        if (player == null || previousTarget == null || nextTarget == null) {
            return false;
        }
        if (isTargetStaleForPlayer(player, previousTarget)) {
            return false;
        }
        if (!isNearbyPlacementTransition(previousTarget, nextTarget)) {
            return false;
        }
        if (!isPreviousTargetStillActionable(player, previousTarget)) {
            return false;
        }

        Rotation previousRotation = previousTarget.getRotation();
        Rotation nextRotation = nextTarget.getRotation();
        if (previousRotation == null || nextRotation == null) {
            return false;
        }

        return previousRotation.angleTo(nextRotation) > 90.0f;
    }

    private boolean isNearbyPlacementTransition(ScaffoldPlacementTarget previousTarget, ScaffoldPlacementTarget nextTarget) {
        if (previousTarget == null || nextTarget == null) {
            return false;
        }

        BlockPos previousPlaced = previousTarget.getPlacedBlockPos();
        BlockPos nextPlaced = nextTarget.getPlacedBlockPos();
        if (previousPlaced.equals(nextPlaced)) {
            return true;
        }

        int dx = Math.abs(previousPlaced.getX() - nextPlaced.getX());
        int dy = Math.abs(previousPlaced.getY() - nextPlaced.getY());
        int dz = Math.abs(previousPlaced.getZ() - nextPlaced.getZ());
        return dy == 0 && dx + dz <= 1;
    }

    private boolean isPreviousTargetStillActionable(LocalPlayer player, ScaffoldPlacementTarget target) {
        if (player == null || target == null || player.level() == null) {
            return false;
        }

        BlockState supportState = player.level().getBlockState(target.getInteractedBlockPos());
        if (supportState == null || supportState.canBeReplaced()) {
            return false;
        }

        BlockState placedState = player.level().getBlockState(target.getPlacedBlockPos());
        if (placedState == null || !placedState.canBeReplaced()) {
            return false;
        }

        return isWithinBlockInteractionRange(player, target.getHitVec());
    }

    private ScaffoldPlacementTarget findNormalPlacementTarget(LocalPlayer player, ItemStack stack) {
        Vec3 placementEyePos = getPlacementEyePos(player);
        Comparator<BlockPos> comparator = getNormalTargetComparator(player, placementEyePos);
        List<BlockPos> offsets = shouldGoDown(player) ? DOWN_OFFSETS : NORMAL_OFFSETS;
        ScaffoldFacePositionFactory facePositionFactory = createPlacementFacePositionFactory(player, aimMode.get(), placementEyePos);
        BlockPos targetedPos = getTargetedPosition(player);
        List<BlockPos> movementOffsets = movementAlignedNormalOffsets(player, targetedPos, offsets);
        if (movementOffsets.isEmpty()) {
            return null;
        }

        return findNormalPlacementTargetAt(
                player,
                stack,
                targetedPos,
                movementOffsets,
                comparator,
                facePositionFactory,
                placementEyePos
        );
    }

    private List<BlockPos> movementAlignedNormalOffsets(LocalPlayer player, BlockPos targetedPos, List<BlockPos> offsets) {
        if (player == null || targetedPos == null || offsets == null || offsets.isEmpty()) {
            return offsets;
        }
        if (currentOptimalLine == null || !hasRawInput() || shouldGoDown(player)) {
            return offsets;
        }

        Vec3 reference = currentOptimalLine.getNearestPointTo(player.position());
        Vec3 direction = currentOptimalLine.direction();
        List<BlockPos> aligned = new ArrayList<>(offsets.size());

        for (BlockPos offset : offsets) {
            BlockPos candidate = targetedPos.offset(offset);
            double progress = Vec3.atCenterOf(candidate).subtract(reference).dot(direction);
            if (progress >= -0.15) {
                aligned.add(offset);
            }
        }

        return aligned;
    }

    private ScaffoldPlacementTarget findNormalPlacementTargetAt(
            LocalPlayer player,
            ItemStack stack,
            BlockPos targetedPos,
            List<BlockPos> offsets,
            Comparator<BlockPos> comparator,
            ScaffoldFacePositionFactory facePositionFactory,
            Vec3 placementEyePos
    ) {
        return ScaffoldTargetFinder.findTarget(
                player,
                targetedPos,
                stack,
                offsets,
                comparator,
                shouldGoDown(player),
                facePositionFactory,
                placementEyePos
        );
    }

    private ScaffoldPlacementTarget findExpandPlacementTarget(LocalPlayer player, ItemStack stack) {
        Vec3 placementEyePos = getPlacementEyePos(player);
        for (int i = 0; i <= expandLength.get(); i++) {
            BlockPos position = getExpandTargetedPosition(player, i);
            ScaffoldPlacementTarget target = ScaffoldTargetFinder.findTarget(
                    player,
                    position,
                    stack,
                    List.of(BlockPos.ZERO),
                    Comparator.comparingDouble((BlockPos pos) -> Vec3.atCenterOf(pos).distanceToSqr(placementEyePos)).reversed(),
                    true,
                    new ScaffoldFacePositionFactory(
                            player,
                            placementEyePos,
                            ScaffoldFacePositionFactory.Mode.CENTER,
                            currentOptimalLine,
                            randomRange(-1.0, 1.0)
                    ),
                    placementEyePos
            );
            if (target == null) continue;
            Vec3 blockCenter = Vec3.atCenterOf(target.getPlacedBlockPos());
            Rotation rotation = Rotation.lookingAt(blockCenter, placementEyePos);
            return target.withRotation(rotation);
        }
        return null;
    }

    private ScaffoldPlacementTarget findCenterPlacementTarget(LocalPlayer player, ItemStack stack) {
        Vec3 placementEyePos = getPlacementEyePos(player);
        return ScaffoldTargetFinder.findTarget(
                player,
                getTargetedPosition(player),
                stack,
                NORMAL_OFFSETS,
                Comparator.comparingDouble((BlockPos pos) -> Vec3.atCenterOf(pos).distanceToSqr(placementEyePos)).reversed(),
                false,
                new ScaffoldFacePositionFactory(
                        player,
                        placementEyePos,
                        ScaffoldFacePositionFactory.Mode.CENTER,
                        currentOptimalLine,
                        randomRange(-1.0, 1.0)
                ),
                placementEyePos
        );
    }

    private BlockPos getExpandTargetedPosition(LocalPlayer player, int expand) {
        Vec3 position = player.position();
        float yaw = player.getYRot();
        int x = (int) (-Math.sin(Math.toRadians(yaw)) * expand);
        int z = (int) (Math.cos(Math.toRadians(yaw)) * expand);
        BlockPos expanded = BlockPos.containing(position).offset(x, 0, z);
        return getTargetedPositionFromBase(player, expanded);
    }

    private BlockPos getTargetedPositionFromBase(LocalPlayer player, BlockPos basePos) {
        if (shouldGoDown(player)) {
            return basePos.below(2);
        }
        if (Minecraft.getInstance().options.keyJump.isDown()
                && (!isMoving(player) || player.horizontalCollision)) {
            return basePos.below();
        }
        return switch (sameY.get()) {
            case ON -> new BlockPos(basePos.getX(), placementY, basePos.getZ());
            case FALLING -> player.getDeltaMovement().y < 0.2
                    ? new BlockPos(basePos.getX(), placementY, basePos.getZ())
                    : basePos.below();
            case OFF -> basePos.below();
        };
    }

    private void sendLookPacket(LocalPlayer player, Rotation rotation) {
        if (player == null || player.connection == null || rotation == null) return;
        Rotation normalized = rotation.normalize();
        debugLog(
                "send-look pos=%s rot=%s onGround=%s horiz=%s",
                formatVec(player.position()),
                describeRotation(normalized),
                player.onGround(),
                player.horizontalCollision
        );
        player.connection.send(new ServerboundMovePlayerPacket.PosRot(
                player.getX(),
                player.getY(),
                player.getZ(),
                normalized.yaw(),
                normalized.pitch(),
                player.onGround(),
                player.horizontalCollision
        ));
    }

    private boolean canOperate(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        return player != null
                && mc.level != null
                && mc.gameMode != null
                && ClientScreen.current() == null;
    }

    private boolean isMoving(LocalPlayer player) {
        if (player == null || player.input == null) return false;
        return Math.abs(player.input.getMoveVector().x) > 1.0E-5f
                || Math.abs(player.input.getMoveVector().y) > 1.0E-5f;
    }

    private boolean shouldGoDown(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        return down.get()
                && player != null
                && mc.options.keyShift.isDown()
                && canStandOn(player.blockPosition().below(2));
    }

    private boolean shouldEagle(LocalPlayer player, MovementInputEvent event) {
        if (!eagle.get() || player == null) return false;
        if (shouldGoDown(player)) return false;
        if (eagleOnlyOnGround.get() && !player.onGround()) return false;
        if (player.getAbilities().flying) return false;

        boolean normalAngleSmooth = isNormalAngleSmoothLedgeControl();
        if (normalAngleSmooth && isCurrentScaffoldTargetReady(player, currentTarget)) {
            return false;
        }

        double distance = normalAngleSmooth
                ? Math.min(eagleEdgeDistance.get(), 0.24)
                : eagleEdgeDistance.get();
        EagleUtil.EdgeCheck edgeCheck = EagleUtil.checkEdge(player, event, distance);
        if (edgeCheck.diagonalRescue()) {
            return true;
        }

        return placedBlocksSinceEagleReset == 0 && edgeCheck.closeToEdge();
    }

    private void handleNormalAngleSmoothLedge(LocalPlayer player, MovementInputEvent event) {
        if (player == null || event == null) return;
        if (technique.get() != Technique.NORMAL || rotationTiming.get() != RotationTiming.NORMAL) return;
        if (angleSmoothMode.get() == AngleSmoothMode.NONE) return;
        if (shouldGoDown(player) || player.getAbilities().flying) return;

        ScaffoldPlacementTarget target = currentTarget;
        if (target == null) {
            if (normalAngleSmoothLedgeHoldTicks > 0) {
                event.setSneak(true);
                event.setSprint(false);
                normalAngleSmoothLedgeHoldTicks--;
            }
            return;
        }

        EagleUtil.EdgeCheck edgeCheck = EagleUtil.checkEdge(
                player,
                event,
                Math.max(Math.min(eagleEdgeDistance.get(), 0.24), 0.18),
                Math.min(1.0, TickDelta.get() + 0.85)
        );

        Rotation targetRotation = target.getRotation();
        Rotation currentRotation = RotationManager.INSTANCE.getCurrentRotation();
        if (currentRotation == null) {
            currentRotation = RotationManager.INSTANCE.getServerRotation();
        }
        if (targetRotation == null || currentRotation == null) {
            return;
        }

        boolean ready = isCurrentScaffoldTargetReady(player, target, currentRotation);

        int ticks = estimateTicksUntilRotationReady(currentRotation, targetRotation);
        boolean falloffImminent = isNormalAngleSmoothFalloffImminent(player, ticks);
        if ((falloffImminent || edgeCheck.closeToEdge()) && !ready) {
            normalAngleSmoothLedgeHoldTicks = Math.max(
                    normalAngleSmoothLedgeHoldTicks,
                    Math.max(2, ticks + 1)
            );
        }

        if (normalAngleSmoothLedgeHoldTicks <= 0) {
            return;
        }

        event.setSneak(true);
        event.setSprint(false);
        normalAngleSmoothLedgeHoldTicks--;
    }

    private boolean isNormalAngleSmoothFalloffImminent(LocalPlayer player, int ticksUntilReady) {
        if (player == null || currentOptimalLine == null) return false;

        Vec3 fallOff = movementPrediction.getFallOffPositionOnLine(player, currentOptimalLine);
        if (fallOff == null) return false;

        Vec3 nearest = currentOptimalLine.getNearestPointTo(player.position());
        Vec3 direction = currentOptimalLine.direction().normalize();
        double distance = fallOff.subtract(nearest).dot(direction);
        if (distance < -0.05) return true;

        double speed = Math.max(horizontalSpeed(player.getDeltaMovement()), 0.05);
        double leadTicks = Math.max(1.5, ticksUntilReady + 1.0 + TickDelta.get());
        double threshold = Math.max(0.18, Math.min(0.85, speed * leadTicks + 0.06));
        return distance <= threshold;
    }

    private boolean isNormalAngleSmoothLedgeControl() {
        return technique.get() == Technique.NORMAL
                && rotationTiming.get() == RotationTiming.NORMAL
                && angleSmoothMode.get() != AngleSmoothMode.NONE;
    }

    private boolean isCurrentScaffoldTargetReady(LocalPlayer player, ScaffoldPlacementTarget target) {
        Rotation currentRotation = RotationManager.INSTANCE.getCurrentRotation();
        if (currentRotation == null) {
            currentRotation = RotationManager.INSTANCE.getServerRotation();
        }
        return isCurrentScaffoldTargetReady(player, target, currentRotation);
    }

    private boolean isCurrentScaffoldTargetReady(
            LocalPlayer player,
            ScaffoldPlacementTarget target,
            Rotation currentRotation
    ) {
        if (player == null || target == null || currentRotation == null) return false;

        BlockHitResult currentHit = traceScaffoldTarget(player, currentRotation);
        return target.matches(currentHit)
                && isValidCrosshairTarget(player, currentHit);
    }

    private void handleTower(LocalPlayer player) {
        if (!isEnabled() || player == null) return;
        if (!isTowering(player)) {
            towerJumpBaseY = Double.NaN;
            return;
        }

        switch (towerMode.get()) {
            case MOTION -> handleMotionTower(player);
            case VULCAN -> handleVulcanTower(player);
            case HYPIXEL -> handleHypixelTower(player);
            default -> {
            }
        }
    }

    private void handleAcceleration(LocalPlayer player) {
        if (!acceleration.get() || player == null) return;
        if (accelerationOnlyOnGround.get() && !player.onGround()) return;

        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(
                velocity.x * accelerationMultiplier.get(),
                velocity.y,
                velocity.z * accelerationMultiplier.get()
        );
    }

    private void updateStrafeMoveTicks(LocalPlayer player) {
        if (player == null) return;
        if (isMoving(player)) {
            strafeMoveTicks++;
        } else {
            strafeMoveTicks = 0;
        }
    }

    private void handleHeadHitter(LocalPlayer player) {
        if (!headHitter.get() || technique.get() != Technique.NORMAL || player == null) return;
        if (!player.onGround() || !isMoving(player)) return;
        BlockPos above = player.blockPosition().above(2);
        if (player.level().getBlockState(above).isAir()) return;
        player.jumpFromGround();
    }

    private void handleStabilizeMovement(LocalPlayer player, MovementInputEvent event) {
        if (!stabilizeMovement.get() || technique.get() != Technique.NORMAL || player == null) return;
        if (rotationTiming.get() != RotationTiming.NORMAL) return;
        if (event.isJump() && player.onGround()) return;

        ScaffoldLine optimalLine = currentOptimalLine;
        if (optimalLine == null) return;

        Vec3 nearestPoint = optimalLine.getNearestPointTo(player.position());
        Vec3 toLine = nearestPoint.subtract(player.position());
        Vec3 lineDirection = new Vec3(optimalLine.direction().x, 0.0, optimalLine.direction().z);
        if (lineDirection.lengthSqr() < 1.0E-7) return;

        Vec3 desiredWorld = lineDirection.normalize();
        double correctionLength = Math.hypot(toLine.x, toLine.z);
        if (correctionLength > 1.0E-7) {
            Vec3 correction = new Vec3(toLine.x / correctionLength, 0.0, toLine.z / correctionLength);
            double correctionStrength = Mth.clamp(0.35 + correctionLength * 3.0, 0.35, 1.0);
            desiredWorld = desiredWorld.add(correction.scale(correctionStrength)).normalize();
        }

        applyLineHoldInput(event, desiredWorld, player.getYRot());
    }

    private void applyLineHoldInput(MovementInputEvent event, Vec3 desiredWorld, float yaw) {
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double localForward = desiredWorld.z * cos - desiredWorld.x * sin;
        double localSideways = desiredWorld.x * cos + desiredWorld.z * sin;

        event.setForward(localForward > 1.0E-4);
        event.setBackward(localForward < -1.0E-4);
        event.setLeft(localSideways < -1.0E-4);
        event.setRight(localSideways > 1.0E-4);
    }

    private void handleNormalStrictMoonwalkInput(LocalPlayer player, MovementInputEvent event) {
        if (player == null || event == null) return;
        if (technique.get() != Technique.NORMAL) return;
        if (rotationTiming.get() != RotationTiming.NORMAL) return;
        if (movementCorrection.get() != MovementCorrection.STRICT) return;
        if (!hasRawInput() || blockCount(player) <= 0) return;

        Rotation movementRotation = RotationManager.INSTANCE.getCurrentRotation();
        if (movementRotation == null && currentTarget != null) {
            movementRotation = currentTarget.getRotation();
        }
        if (movementRotation == null) {
            movementRotation = new Rotation(getRawMovementDirectionYaw(player, player.getYRot()) + 180.0f, 75.0f, false).normalize();
        }

        Input corrected = RotationUtil.correctMovementInput(
                event.toPlayerInput(),
                player.getYRot(),
                movementRotation.yaw()
        );
        if (corrected == null) return;

        event.setForward(corrected.forward());
        event.setBackward(corrected.backward());
        event.setLeft(corrected.left());
        event.setRight(corrected.right());
        event.setJump(corrected.jump());
        event.setSneak(corrected.shift());
        event.setSprint(corrected.sprint() && corrected.forward() && !corrected.backward());
    }

    private void handleTelly(LocalPlayer player, MovementInputEvent event) {
        if (!telly.get() || technique.get() != Technique.NORMAL || player == null) return;
        if (!isMoving(player) || blockCount(player) <= 0 || !player.onGround()) return;

        switch (tellyResetMode.get()) {
            case REVERSE -> event.setJump(true);
            case RESET -> {
                if (shouldTellyJumpNow(player)) {
                    event.setJump(true);
                }
            }
        }
    }

    private void handleStrafe(LocalPlayer player) {
        if (!strafe.get() || player == null) return;
        if (strafeOnlyOnGround.get() && !player.onGround()) return;

        if (strafeHypixel.get()) {
            double speedValue = player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED) ? 0.295 : 0.207;
            if (player.tickCount % 20 == 0 || strafeMoveTicks <= 7) {
                speedValue = 0.09800000190734863;
            }
            Vec3 velocity = MovementUtil.withStrafe(player.getDeltaMovement(), movementInputVector(player), player.getYRot(), speedValue, 1.0);
            player.setDeltaMovement(velocity);
            return;
        }

        Vec3 velocity = MovementUtil.withStrafe(player.getDeltaMovement(), movementInputVector(player), player.getYRot(), strafeSpeed.get(), 1.0);
        player.setDeltaMovement(velocity);
    }

    private void handleMotionTower(LocalPlayer player) {
        if (player.onGround()) {
            player.jumpFromGround();
            towerJumpBaseY = player.getY();
            return;
        }

        if (Double.isNaN(towerJumpBaseY)) return;
        if (player.getY() <= towerJumpBaseY + towerTriggerHeight.get()) return;

        player.absSnapTo(player.getX(), Math.floor(player.getY()), player.getZ());
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(
                velocity.x * towerSlow.get(),
                towerMotion.get(),
                velocity.z * towerSlow.get()
        );
        towerJumpBaseY = player.getY();
    }

    private void handleVulcanTower(LocalPlayer player) {
        if (player.tickCount % 2 == 0) {
            player.setDeltaMovement(player.getDeltaMovement().x, 0.7, player.getDeltaMovement().z);
        } else {
            player.setDeltaMovement(player.getDeltaMovement().x, isMoving(player) ? 0.42 : 0.6, player.getDeltaMovement().z);
        }
    }

    private void handleHypixelTower(LocalPlayer player) {
        Vec3 velocity = player.getDeltaMovement();

        if (Math.abs(player.getX() % 1.0) > 1.0E-4 && !isMoving(player)) {
            double adjust = Math.round(player.getX()) - player.getX();
            player.setDeltaMovement(Math.copySign(Math.min(Math.abs(adjust), 0.281), adjust), velocity.y, velocity.z);
            velocity = player.getDeltaMovement();
        }

        if (towerAirTicks > 14) {
            player.setDeltaMovement(velocity.x * 0.6, velocity.y - 0.09, velocity.z * 0.6);
            return;
        }

        int mod = Math.floorMod(towerAirTicks, 3);
        if (mod == 0) {
            Vec3 strafe = MovementUtil.withStrafe(player.getDeltaMovement(), new Vec3(0.0, 0.0, 1.0), player.getYRot(), 0.247, 1.0);
            player.setDeltaMovement(strafe.x, 0.42, strafe.z);
        } else if (mod == 2) {
            player.setDeltaMovement(velocity.x, 1.0 - (player.getY() % 1.0), velocity.z);
        }
    }

    private boolean isTowering(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        return towerMode.get() != TowerMode.NONE
                && player != null
                && mc.options.keyJump.isDown()
                && !shouldGoDown(player)
                && blockCount(player) > 0
                && isBlockBelow(player);
    }


    private float getRawMovementDirectionYaw(LocalPlayer player, float fallbackYaw) {
        if (player == null) return fallbackYaw;
        boolean forwards = rawForward && !rawBackward;
        boolean backwards = rawBackward && !rawForward;
        boolean left = rawLeft && !rawRight;
        boolean right = rawRight && !rawLeft;

        if (!forwards && !backwards && !left && !right) return fallbackYaw;

        float actualYaw = fallbackYaw;
        float forward = 1.0f;

        if (backwards) {
            actualYaw += 180.0f;
            forward = -0.5f;
        } else if (forwards) {
            forward = 0.5f;
        }

        if (left) {
            actualYaw -= 90.0f * forward;
        }
        if (right) {
            actualYaw += 90.0f * forward;
        }

        return actualYaw;
    }

    private boolean hasRawInput() {
        return rawForward || rawBackward || rawLeft || rawRight;
    }

    private boolean isRawDiagonal() {
        return rawForward != rawBackward && rawLeft != rawRight;
    }

    private Vec3 movementInputVector(LocalPlayer player) {
        if (player == null || player.input == null) return Vec3.ZERO;
        return new Vec3(player.input.getMoveVector().x, 0.0, player.input.getMoveVector().y);
    }

    private double horizontalSpeed(Vec3 velocity) {
        return velocity == null ? 0.0 : Math.hypot(velocity.x, velocity.z);
    }

    private double randomRange(double min, double max) {
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        return low + Math.random() * (high - low);
    }


    private Comparator<BlockPos> getNormalTargetComparator(LocalPlayer player, Vec3 placementEyePos) {
        if (currentOptimalLine != null) {
            return Comparator.comparingDouble((BlockPos pos) -> currentOptimalLine.squaredDistanceTo(Vec3.atCenterOf(pos))).reversed();
        }
        Vec3 referenceEyePos = placementEyePos != null
                ? placementEyePos
                : (player != null ? player.getEyePosition() : Vec3.ZERO);
        return Comparator.comparingDouble((BlockPos pos) -> Vec3.atCenterOf(pos).distanceToSqr(referenceEyePos)).reversed();
    }

    private ScaffoldFacePositionFactory createPlacementFacePositionFactory(
            LocalPlayer player,
            ScaffoldFacePositionFactory.Mode mode,
            Vec3 placementEyePos
    ) {
        return new ScaffoldFacePositionFactory(
                player,
                placementEyePos,
                mode,
                currentOptimalLine,
                randomRange(-1.0, 1.0)
        );
    }

    private Vec3 getPlacementEyePos(LocalPlayer player) {
        if (player == null) {
            return Vec3.ZERO;
        }

        Vec3 placementPos = getPlacementBasePosition(player);
        return placementPos.add(0.0, player.getEyeHeight(player.getPose()), 0.0);
    }

    private boolean canConstructCeiling(LocalPlayer player) {
        return ceiling.get()
                && technique.get() == Technique.NORMAL
                && player != null
                && !player.level().getBlockState(player.blockPosition().below()).isAir();
    }

    private boolean shouldTellyJumpNow(LocalPlayer player) {
        if (ticksUntilTellyJump < tellyJumpTicks) return false;
        if (isTowering(player) && !tellyAimOnTower.get()) return false;
        return RotationManager.INSTANCE.getCurrentRotation() == null || tellyStraightTicks.get() == 0;
    }

    private int randomTellyJumpTicks() {
        int min = Math.min(tellyJumpTicksMin.get(), tellyJumpTicksMax.get());
        int max = Math.max(tellyJumpTicksMin.get(), tellyJumpTicksMax.get());
        return min + (int) Math.floor(Math.random() * (max - min + 1));
    }


    private Vec3 getPlacementBasePosition(LocalPlayer player) {
        if (player == null) return Vec3.ZERO;
        if (prediction.get()) {
            Vec3 predicted = movementPrediction.getPredictedPlacementPos(player, currentOptimalLine);
            if (predicted != null) {
                return predicted;
            }
        }
        if (technique.get() == Technique.NORMAL && isRawDiagonal() && currentOptimalLine != null) {
            return getTickDeltaDiagonalPlacementPos(player);
        }
        return player.position();
    }

    private Vec3 getTickDeltaDiagonalPlacementPos(LocalPlayer player) {
        Vec3 velocity = new Vec3(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        double tickDelta = TickDelta.get();
        double lead = Math.max(0.35, Math.min(1.35, tickDelta + 0.45));
        Vec3 projected = player.position().add(velocity.scale(lead));

        if (velocity.horizontalDistanceSqr() < 0.02 * 0.02) {
            Vec3 direction = currentOptimalLine.direction().normalize();
            projected = projected.add(direction.scale(0.18 + tickDelta * 0.12));
        }

        return currentOptimalLine.getNearestPointTo(projected);
    }

    private boolean isBlockBelow(LocalPlayer player) {
        return player != null && player.level().getBlockCollisions(
                player,
                player.getBoundingBox().inflate(0.5, 0.0, 0.5).move(0.0, -1.05, 0.0)
        ).iterator().hasNext();
    }

    private boolean canStandOn(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null
                && mc.level.getBlockState(pos).isFaceSturdy(mc.level, pos, net.minecraft.core.Direction.UP);
    }

    private int blockCount(LocalPlayer player) {
        if (player == null) return 0;
        int count = ScaffoldBlockItemSelection.isValidBlock(player.getOffhandItem()) ? player.getOffhandItem().getCount() : 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ScaffoldBlockItemSelection.isValidBlock(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private LocalPlayer player() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null ? mc.player : null;
    }

    private List<RotationProcessor> buildRotationProcessors() {
        List<RotationProcessor> processors = new ArrayList<>();
        AngleSmooth smooth = selectAngleSmooth();
        if (smooth != null) {
            processors.add(smooth);
        }
        return processors;
    }

    private AngleSmooth selectAngleSmooth() {
        return switch (angleSmoothMode.get()) {
            case LINEAR -> linearSmooth;
            case SIGMOID -> sigmoidSmooth;
            case INTERPOLATION -> interpolationSmooth;
            case ACCELERATION -> accelerationSmooth;
            case NONE -> null;
        };
    }

    private int estimateTicksUntilRotationReady(Rotation currentRotation, Rotation targetRotation) {
        AngleSmooth smooth = selectAngleSmooth();
        if (smooth == null || currentRotation == null || targetRotation == null) {
            return 1;
        }

        return Math.max(1, Math.min(6, smooth.calculateTicks(currentRotation, targetRotation)));
    }

    public enum RotationTiming {
        NORMAL,
        ON_TICK
    }

    public enum AngleSmoothMode {
        LINEAR,
        SIGMOID,
        INTERPOLATION,
        ACCELERATION,
        NONE
    }

    public enum SameYMode {
        OFF,
        ON,
        FALLING
    }

    public enum SprintMode {
        DO_NOT_CHANGE,
        FORCE_SPRINT,
        FORCE_NO_SPRINT,
        NO_SPRINT_ON_PLACE,
        NO_SPRINT_ON_GROUND
    }

    public enum TowerMode {
        NONE,
        MOTION,
        VULCAN,
        HYPIXEL
    }

    public enum Technique {
        NORMAL,
        EXPAND
    }

    public enum TellyResetMode {
        REVERSE,
        RESET
    }

    public enum IndicatorSide {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    public enum IndicatorStyle {
        SLOT,
        PILL
    }

    public enum IndicatorCountMode {
        CURRENT_STACK,
        SAME_ITEM,
        ALL_PLACEABLE
    }

    private record RenderPreview(BlockPos pos, float progress) {
    }

    private record PlacementIndicatorData(ItemStack stack, int count, String countText) {
    }
}
