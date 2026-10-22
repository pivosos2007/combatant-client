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
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.util.anticheat.AntiCheatPresetDefaults;
import combatant.client.automation.placement.PlacementConstraints;
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.automation.placement.PlacementSlot;
import combatant.client.util.block.placer.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ModuleInfo(
        id = "selftrap",
        displayName = "SelfTrap",
        aliases = {"HeadTrap", "TopTrap"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.PROTECT,
        description = "module.selftrap.description")
public final class SelfTrap extends Module {

    /** Gap between two "out of blocks" warnings, so a fight does not flood the HUD. */
    private static final long NO_BLOCKS_WARN_INTERVAL_MS = 3000L;

    private final Minecraft mc = Minecraft.getInstance();
    // grim preset: place range <= 4.5, wall range 0, rotation NORMAL, 1 block/tick;
    // vanilla preset: 5.5 range/wall range, NO_ROTATION, max blocks/tick. Picked once, then every setting stays as the player sets it.
    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.VANILLA);
    private final EnumValue<Mode> mode = enumSetting("selfTrapMode", "mode", Mode.HEAD, Mode.values());
    private final EnumValue<BlockPriority> blockPriority = enumCommon(
            "selfTrapBlockPriority", "block_priority",
            CommonSettingSchemas.SHELL_BLOCK_PRIORITY, BlockPriority.ANY, BlockPriority.values());
    private final NumberValue<Integer> blocksPerTick = numCommon(
            "selfTrapBlocksPerTick", "blocks_per_tick", CommonSettingSchemas.SHELL_BLOCKS_PER_TICK, 4, 1, 8);
    private final BooleanValue dynamicHitbox = boolCommon(
            "selfTrapDynamicHitbox", "dynamic_hitbox", CommonSettingSchemas.SHELL_DYNAMIC_HITBOX, true);
    private final BooleanValue support = boolCommon("selfTrapSupport", "support", CommonSettingSchemas.SHELL_SUPPORT, true);
    private final BooleanValue attackCrystals = boolCommon(
            "selfTrapAttackCrystals", "attack_crystals", CommonSettingSchemas.SHELL_ATTACK_CRYSTALS, true);
    private final NumberValue<Integer> crystalDelay = visibleWhen(
            numCommon("selfTrapCrystalDelay", "crystal_delay",
                    CommonSettingSchemas.SHELL_CRYSTAL_DELAY, 0, 0, 10),
            attackCrystals::get);
    private final BooleanValue placeWhileUsing = boolCommon(
            "selfTrapPlaceWhileUsing", "place_while_using", CommonSettingSchemas.SHELL_PLACE_WHILE_USING, false);
    private final NumberValue<Integer> swapBackDelay = numCommon(
            "selfTrapSwapBackDelay", "swap_back_delay", CommonSettingSchemas.SHELL_SWAP_BACK_DELAY, 0, 0, 10);
    private final BooleanValue disableOnJump = bool("selfTrapDisableOnJump", "disable_on_jump", true);
    private final BooleanValue disableInAir = boolCommon(
            "selfTrapDisableInAir", "disable_in_air", CommonSettingSchemas.SHELL_DISABLE_IN_AIR, false);
    private final BooleanValue autoDisable = bool("selfTrapAutoDisable", "auto_disable", false);
    private final BooleanValue warnNoBlocks = boolCommon(
            "selfTrapWarnNoBlocks", "warn_no_blocks", CommonSettingSchemas.SHELL_WARN_NO_BLOCKS, true);
    private final EnumValue<PlacementRotationMode> rotation = enumSetting(
            "selfTrapRotation", "rotation", PlacementRotationMode.NORMAL, PlacementRotationMode.values());
    private final EnumValue<MovementCorrection> movementCorrection = enumSetting(
            "selfTrapMovementCorrection", "movement_correction", MovementCorrection.SILENT, MovementCorrection.values());
    private final NumberValue<Double> range = numCommon(
            "selfTrapRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> wallRange = numCommon(
            "selfTrapWallRange", "wall_range", CommonSettingSchemas.COMBAT_WALL_RANGE, 3.0D, 0.0D, 6.0D);

    {
        AntiCheatPresetDefaults.bind(anticheat)
                .cap(range, AntiCheatPreset.GRIM_BLOCK_RANGE, AntiCheatPreset.VANILLA_BLOCK_RANGE)
                .cap(wallRange, 0.0D, AntiCheatPreset.VANILLA_BLOCK_RANGE)
                .set(rotation, PlacementRotationMode.NORMAL, PlacementRotationMode.NO_ROTATION)
                .cap(blocksPerTick, 1, blocksPerTick.getMax());
    }

    private final NumberValue<Integer> delay = num("selfTrapDelay", "delay", 0, 0, 10);
    private final BooleanValue render = bool("selfTrapRender", "render", true);
    private final RGBAColorValue fillColor = color("selfTrapFillColor", "fill_color", "#285A9C44");
    private final RGBAColorValue lineColor = color("selfTrapLineColor", "line_color", "#55AAFFFF");
    private final NumberValue<Float> lineWidth = numCommon(
            "selfTrapLineWidth", "line_width", CommonSettingSchemas.LINE_WIDTH, 2.0F, 1.0F, 6.0F);

    private final BlockPlacer blockPlacer = new BlockPlacer(
            this, 10,
            this::findPlacementSlot,
            this::placementConstraints,
            delay::get,
            delay::get,
            blocksPerTick::get,
            () -> true,
            placeWhileUsing::get
    );
    private final CrystalClearer crystalClearer = new CrystalClearer();
    private final List<BlockPos> currentTargets = new ArrayList<>();
    private final PlacementPreviewRenderer placementPreview = new PlacementPreviewRenderer();
    private long lastNoBlocksWarningMs;

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        crystalClearer.reset();
        lastNoBlocksWarningMs = 0L;
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

        updateTargets();

        if (attackCrystals.get()) {
            // Crystals are entities: under GRIM, attack them only within Grim's entity reach, not the block range.
            double crystalRange = anticheat.get() == AntiCheatPreset.GRIM
                    ? Math.min(range.get(), AntiCheatPreset.GRIM_ENTITY_RANGE) : range.get();
            crystalClearer.clear(mc, player, currentTargets, crystalRange, crystalDelay.get());
        }

        warnIfOutOfBlocks(player);
        if (render.get()) placementPreview.tick(mc.level, currentTargets, previewBlockState(), lineColor.getArgb());
        blockPlacer.tick();
        if (render.get()) placementPreview.tick(mc.level, currentTargets, previewBlockState(), lineColor.getArgb());

        if (autoDisable.get() && currentTargets.isEmpty()) setEnabled(false);
    }

    private void warnIfOutOfBlocks(LocalPlayer player) {
        if (!warnNoBlocks.get() || currentTargets.isEmpty()) return;
        if (ShellBlocks.find(player, blockPriority.get()) != null) return;
        long now = Util.getMillis();
        if (now - lastNoBlocksWarningMs < NO_BLOCKS_WARN_INTERVAL_MS) return;
        lastNoBlocksWarningMs = now;
        Notifier.warning(I18n.get("notification.selftrap.no_blocks"));
    }

    private void updateTargets() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            currentTargets.clear();
            blockPlacer.clear();
            return;
        }

        Mode currentMode = mode.get();
        Set<Pos> footprint = ShellBlocks.footprint(player, dynamicHitbox.get());
        List<BlockPos> needed = ShellBlocks.toBlockPos(SurroundPlanner.order(
                SurroundPlanner.trap(footprint, currentMode != Mode.HEAD, currentMode == Mode.FULL),
                ShellBlocks.replaceable(mc.level),
                SurroundPlanner.bodyCells(footprint),
                support.get()));

        currentTargets.clear();
        currentTargets.addAll(needed);
        blockPlacer.update(needed);
    }

    private PlacementSlot findPlacementSlot(BlockPos ignored) {
        LocalPlayer player = mc.player;
        return player == null ? null : ShellBlocks.find(player, blockPriority.get());
    }

    private boolean effConstructFailResult() {
        return rotation.get() == PlacementRotationMode.NO_ROTATION;
    }

    private PlacementConstraints placementConstraints() {
        return new PlacementConstraints(
                range.get(), wallRange.get(), rotation.get(), movementCorrection.get(),
                effConstructFailResult(), false, 1, swapBackDelay.get(), swapBackDelay.get(), true, 0);
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
        PlacementSlot slot = mc.player == null ? null : ShellBlocks.find(mc.player, blockPriority.get());
        return slot != null && slot.stack().getItem() instanceof BlockItem item
                ? item.getBlock().defaultBlockState() : null;
    }


    public enum Mode {
        /** Just the roof over your head. */
        HEAD,
        /** Roof plus the head-level cells beside you, which stops crystals going off at face height. */
        FACE,
        /** Face, plus the roof-level cells beside the roof. */
        FULL
    }
}
