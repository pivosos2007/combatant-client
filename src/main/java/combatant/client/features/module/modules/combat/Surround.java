/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.module.modules.combat.surround.BlockPriority;
import combatant.client.features.module.modules.combat.surround.CrystalClearer;
import combatant.client.features.module.modules.combat.surround.ShellBlocks;
import combatant.client.features.module.modules.combat.surround.SurroundPlanner;
import combatant.client.features.module.modules.combat.surround.SurroundPlanner.Pos;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.helpers.PlacementPreviewRenderer;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.placer.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ModuleInfo(
        id = "surround",
        displayName = "Surround",
        aliases = {"FeetTrap", "ObsidianFeet", "SelfSurround"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.PROTECT,
        description = "module.surround.description")
public final class Surround extends Module {

    /** Gap between two "out of blocks" warnings, so a fight does not flood the HUD. */
    private static final long NO_BLOCKS_WARN_INTERVAL_MS = 3000L;

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode = enumSetting("surroundMode", "mode", Mode.FEET, Mode.values());
    private final EnumValue<CenterMode> center = enumSetting("surroundCenter", "center", CenterMode.NONE, CenterMode.values());
    private final EnumValue<BlockPriority> blockPriority = enumSetting(
            "surroundBlockPriority", "block_priority", BlockPriority.ANY, BlockPriority.values());
    private final NumberValue<Integer> blocksPerTick = num(
            "surroundBlocksPerTick", "blocks_per_tick", 4, 1, 8);
    private final BooleanValue dynamicHitbox = bool(
            "surroundDynamicHitbox", "dynamic_hitbox", true);
    private final BooleanValue support = bool(
            "surroundSupport", "support", true);
    private final BooleanValue attackCrystals = bool(
            "surroundAttackCrystals", "attack_crystals", true);
    private final NumberValue<Integer> crystalDelay = visibleWhen(
            num("surroundCrystalDelay", "crystal_delay", 0, 0, 10), attackCrystals::get);
    private final BooleanValue placeWhileUsing = bool(
            "surroundPlaceWhileUsing", "place_while_using", false);
    private final NumberValue<Integer> swapBackDelay = num(
            "surroundSwapBackDelay", "swap_back_delay", 0, 0, 10);
    private final BooleanValue disableOnJump = boolCommon(
            "surroundDisableOnJump", "disable_on_jump", CommonSettingSchemas.PLAYER_FALL_CHECK, true);
    private final BooleanValue disableInAir = bool("surroundDisableInAir", "disable_in_air", false);
    private final BooleanValue disableOnMove = bool("surroundDisableOnMove", "disable_on_move", false);
    private final BooleanValue autoDisable = bool("surroundAutoDisable", "auto_disable", false);
    private final BooleanValue warnNoBlocks = bool("surroundWarnNoBlocks", "warn_no_blocks", true);
    private final EnumValue<BlockPlacer.RotationMode> rotation = enumSetting(
            "surroundRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());
    private final EnumValue<MovementCorrection> movementCorrection = enumSetting(
            "surroundMovementCorrection", "movement_correction", MovementCorrection.SILENT, MovementCorrection.values());
    private final NumberValue<Double> range = numCommon(
            "surroundRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> wallRange = numCommon(
            "surroundWallRange", "wall_range", CommonSettingSchemas.COMBAT_WALL_RANGE, 3.0D, 0.0D, 6.0D);
    private final NumberValue<Integer> delay = num("surroundDelay", "delay", 0, 0, 10);
    private final BooleanValue render = bool("surroundRender", "render", true);
    private final RGBAColorValue fillColor = color("surroundFillColor", "fill_color", "#285A9C44");
    private final RGBAColorValue lineColor = color("surroundLineColor", "line_color", "#55AAFFFF");
    private final NumberValue<Float> lineWidth = numCommon(
            "surroundLineWidth", "line_width", CommonSettingSchemas.LINE_WIDTH, 2.0F, 1.0F, 6.0F);

    private final BlockPlacer blockPlacer = new BlockPlacer(
            this, this, 10,
            this::findPlacementSlot,
            range::get,
            wallRange::get,
            delay::get,
            delay::get,
            swapBackDelay::get,
            swapBackDelay::get,
            () -> 1,
            () -> rotation.get() == BlockPlacer.RotationMode.NO_ROTATION,
            () -> true,
            placeWhileUsing::get,
            () -> false,
            blocksPerTick::get,
            rotation::get,
            movementCorrection::get
    );
    private final CrystalClearer crystalClearer = new CrystalClearer();
    private final List<BlockPos> currentTargets = new ArrayList<>();
    private final PlacementPreviewRenderer placementPreview = new PlacementPreviewRenderer();
    /** Columns (x, z) the player stood over when the module came on; "disable on move" watches these. */
    private final Set<Long> homeColumns = new HashSet<>();
    private long lastNoBlocksWarningMs;

    @Override
    public void onEnable() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        applyCentering(player);
        crystalClearer.reset();
        lastNoBlocksWarningMs = 0L;
        rememberHome(player);
        placementPreview.reset();
        blockPlacer.enable();
        updateTargets();
        if (render.get()) placementPreview.tick(mc.level, currentTargets, previewBlockState(), lineColor.getArgb());
    }

    @Override
    public void onDisable() {
        blockPlacer.disable();
        placementPreview.reset();
        currentTargets.clear();
        homeColumns.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        if (disableOnJump.get() && (mc.options.keyJump.isDown() || player.getDeltaMovement().y > 0.15D)) {
            setEnabled(false);
            return;
        }
        if (disableInAir.get() && !player.onGround()) {
            setEnabled(false);
            return;
        }
        if (disableOnMove.get() && hasLeftHome(player)) {
            setEnabled(false);
            return;
        }
        if (center.get() == CenterMode.MOTION) {
            moveTowardCenter(player, 0.4D);
        }

        updateTargets();

        if (attackCrystals.get()) {
            crystalClearer.clear(mc, player, currentTargets, range.get(), crystalDelay.get());
        }

        warnIfOutOfBlocks(player);
        if (render.get()) placementPreview.tick(mc.level, currentTargets, previewBlockState(), lineColor.getArgb());
        blockPlacer.tick();
        if (render.get()) placementPreview.tick(mc.level, currentTargets, previewBlockState(), lineColor.getArgb());

        if (autoDisable.get() && currentTargets.isEmpty()) {
            setEnabled(false);
        }
    }

    private void applyCentering(LocalPlayer player) {
        CenterMode cm = center.get();
        if (cm == CenterMode.NONE) return;

        double x = Math.floor(player.getX()) + 0.5D;
        double z = Math.floor(player.getZ()) + 0.5D;

        if (cm == CenterMode.MOTION) {
            moveTowardCenter(player, 0.5D);
            return;
        }

        player.setPos(x, player.getY(), z);
        if (cm == CenterMode.STRICT_TELEPORT) {
            player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
        }
        if (player.connection != null) {
            player.connection.send(new ServerboundMovePlayerPacket.Pos(
                    x, player.getY(), z, player.onGround(), player.horizontalCollision));
        }
    }

    private static void moveTowardCenter(LocalPlayer player, double strength) {
        double x = Math.floor(player.getX()) + 0.5D;
        double z = Math.floor(player.getZ()) + 0.5D;
        double dx = x - player.getX();
        double dz = z - player.getZ();
        if (Math.abs(dx) <= 0.05D && Math.abs(dz) <= 0.05D) return;
        player.setDeltaMovement(dx * strength, player.getDeltaMovement().y, dz * strength);
    }

    private void rememberHome(LocalPlayer player) {
        homeColumns.clear();
        for (Pos cell : ShellBlocks.footprint(player, dynamicHitbox.get())) {
            homeColumns.add(columnKey(cell.x(), cell.z()));
        }
        homeColumns.add(columnKey((int) Math.floor(player.getX()), (int) Math.floor(player.getZ())));
    }

    /** True once the player is over a column that was not under them when the module came on. */
    private boolean hasLeftHome(LocalPlayer player) {
        return !homeColumns.contains(columnKey((int) Math.floor(player.getX()), (int) Math.floor(player.getZ())));
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private void warnIfOutOfBlocks(LocalPlayer player) {
        if (!warnNoBlocks.get() || currentTargets.isEmpty()) return;
        if (ShellBlocks.find(player, blockPriority.get()) != null) return;
        long now = Util.getMillis();
        if (now - lastNoBlocksWarningMs < NO_BLOCKS_WARN_INTERVAL_MS) return;
        lastNoBlocksWarningMs = now;
        Notifier.warning(I18n.get("notification.surround.no_blocks"));
    }

    private void updateTargets() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            currentTargets.clear();
            blockPlacer.clear();
            return;
        }

        Mode currentMode = mode.get();
        boolean floor = currentMode == Mode.FULL || currentMode == Mode.FULL_BOX;
        boolean head = currentMode == Mode.ANTI_FACE_PLACE || currentMode == Mode.FULL_BOX;
        boolean roof = currentMode == Mode.COVER || currentMode == Mode.FULL_BOX;

        Set<Pos> footprint = ShellBlocks.footprint(player, dynamicHitbox.get());
        List<BlockPos> needed = ShellBlocks.toBlockPos(SurroundPlanner.order(
                SurroundPlanner.shell(footprint, floor, head, roof),
                ShellBlocks.replaceable(mc.level),
                SurroundPlanner.bodyCells(footprint),
                support.get()));

        currentTargets.clear();
        currentTargets.addAll(needed);
        blockPlacer.update(needed);
    }

    private BlockPlacer.PlacementSlot findPlacementSlot(BlockPos ignored) {
        LocalPlayer player = mc.player;
        return player == null ? null : ShellBlocks.find(player, blockPriority.get());
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null) return;
        placementPreview.render(renderer, mc.level, currentTargets, previewBlockState(),
                fillColor.getArgb(), lineColor.getArgb(), lineWidth.get());
    }

    private BlockState previewBlockState() {
        BlockPlacer.PlacementSlot slot = mc.player == null ? null : ShellBlocks.find(mc.player, blockPriority.get());
        return slot != null && slot.stack().getItem() instanceof BlockItem item
                ? item.getBlock().defaultBlockState() : null;
    }


    public enum Mode {
        FEET,
        FULL,
        ANTI_FACE_PLACE,
        COVER,
        FULL_BOX
    }

    public enum CenterMode {
        NONE,
        TELEPORT,
        STRICT_TELEPORT,
        MOTION
    }

}
