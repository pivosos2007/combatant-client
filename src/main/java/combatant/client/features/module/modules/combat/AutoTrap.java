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
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.helpers.PlacementPreviewRenderer;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.util.anticheat.AntiCheatPresetDefaults;
import combatant.client.automation.placement.PlacementConstraints;
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.automation.placement.PlacementSlot;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.target.TargetingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@ModuleInfo(
        id = "autotrap",
        displayName = "AutoTrap",
        aliases = {"Trap", "BoxEnemy"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.autotrap.description")
public final class AutoTrap extends Module {

    private static final Set<Block> BLOCKS = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.NETHERITE_BLOCK,
            Blocks.ENDER_CHEST,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL
    );

    private final Minecraft mc = Minecraft.getInstance();
    // grim preset: place range <= 4.5, wall range 0, rotation NORMAL;
    // vanilla preset: 5.5 range/wall range, NO_ROTATION. Picked once, then every setting stays as the player sets it.
    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.VANILLA);
    private final NumberValue<Double> range = numCommon(
            "autoTrapRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> wallRange = numCommon(
            "autoTrapWallRange", "wall_range", CommonSettingSchemas.COMBAT_WALL_RANGE, 3.0D, 0.0D, 6.0D);
    private final NumberValue<Double> enemyRange = num("autoTrapEnemyRange", "enemy_range", 5.0D, 2.0D, 10.0D);
    private final BooleanMapValue targetFilters = groupCommon(
            "autoTrapTargets", "targets", "target", targetDefaults());
    private final EnumValue<TrapMode> mode = enumSetting("autoTrapMode", "mode", TrapMode.FULL, TrapMode.values());
    private final NumberValue<Double> predictTicks = num("autoTrapPredictTicks", "predict_ticks", 0.5D, 0.0D, 3.0D);
    private final BooleanValue autoDisable = bool("autoTrapAutoDisable", "auto_disable", false);
    private final EnumValue<PlacementRotationMode> rotation = enumSetting(
            "autoTrapRotation", "rotation", PlacementRotationMode.NORMAL, PlacementRotationMode.values());

    {
        AntiCheatPresetDefaults.bind(anticheat)
                .cap(range, AntiCheatPreset.GRIM_BLOCK_RANGE, AntiCheatPreset.VANILLA_BLOCK_RANGE)
                .cap(wallRange, 0.0D, AntiCheatPreset.VANILLA_BLOCK_RANGE)
                .set(rotation, PlacementRotationMode.NORMAL, PlacementRotationMode.NO_ROTATION);
    }

    private final EnumValue<MovementCorrection> movementCorrection = enumSetting(
            "autoTrapMovementCorrection", "movement_correction", MovementCorrection.SILENT, MovementCorrection.values());
    private final NumberValue<Integer> delay = num("autoTrapDelay", "delay", 0, 0, 10);
    private final BooleanValue render = bool("autoTrapRender", "render", true);
    private final RGBAColorValue fillColor = color("autoTrapFillColor", "fill_color", "#285A9C44");
    private final RGBAColorValue lineColor = color("autoTrapLineColor", "line_color", "#55AAFFFF");
    private final NumberValue<Float> lineWidth = numCommon(
            "autoTrapLineWidth", "line_width", CommonSettingSchemas.LINE_WIDTH, 2.0F, 1.0F, 6.0F);

    private final BlockPlacer blockPlacer = new BlockPlacer(
            this, 10,
            this::findPlacementSlot,
            this::placementConstraints,
            delay::get,
            delay::get,
            () -> 1,
            () -> true,
            () -> false
    );
    private final List<BlockPos> currentTargets = new ArrayList<>();
    private final PlacementPreviewRenderer placementPreview = new PlacementPreviewRenderer();

    @Override
    public void onEnable() {
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
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        blockPlacer.tick();
        updateTargets();
        if (render.get()) placementPreview.tick(mc.level, currentTargets, previewBlockState(), lineColor.getArgb());
        if (autoDisable.get() && currentTargets.isEmpty()) setEnabled(false);
    }

    private void updateTargets() {
        Player target = findTarget();
        if (target == null || mc.level == null) {
            currentTargets.clear();
            blockPlacer.clear();
            return;
        }

        Vec3 velocity = target.getDeltaMovement();
        double ticks = predictTicks.get();
        BlockPos feet = BlockPos.containing(
                target.getX() + velocity.x * ticks,
                target.getY() + velocity.y * ticks,
                target.getZ() + velocity.z * ticks);

        List<BlockPos> candidates = new ArrayList<>();
        if (mode.get() == TrapMode.FULL) {
            for (Direction direction : Direction.Plane.HORIZONTAL) candidates.add(feet.relative(direction));
        }
        if (mode.get() != TrapMode.TOP) {
            for (Direction direction : Direction.Plane.HORIZONTAL) candidates.add(feet.above().relative(direction));
        }
        if (mode.get() != TrapMode.SIDES) candidates.add(feet.above(2));

        List<BlockPos> needed = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (mc.level.getBlockState(pos).canBeReplaced()) needed.add(pos);
        }
        currentTargets.clear();
        currentTargets.addAll(needed);
        blockPlacer.update(needed);
    }

    private Player findTarget() {
        LivingEntity target = TargetingUtil.findBestTarget(mc, new TargetingUtil.TargetingSettings(
                enemyRange.get(),
                180.0F,
                true,
                targetFilters.get(TargetFilters.IGNORE_FRIENDS),
                targetFilters.get(TargetFilters.IGNORE_STAFF),
                targetFilters.get(TargetFilters.IGNORE_ENEMIES),
                targetFilters.get(TargetFilters.IGNORE_NAKED),
                true,
                targetFilters.get(TargetFilters.VISIBLE_ONLY),
                TargetingUtil.TargetPriority.DISTANCE));
        return target instanceof Player player ? player : null;
    }

    private PlacementSlot findPlacementSlot(BlockPos ignored) {
        return PlacementSlot.find(mc.player, AutoTrap::isTargetBlock);
    }

    private boolean effConstructFailResult() {
        return rotation.get() == PlacementRotationMode.NO_ROTATION;
    }

    private PlacementConstraints placementConstraints() {
        return new PlacementConstraints(
                range.get(), wallRange.get(), rotation.get(), movementCorrection.get(),
                effConstructFailResult(), false, 1, 0, 0, true, 0);
    }

    private static boolean isTargetBlock(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && BLOCKS.contains(blockItem.getBlock());
    }

    private static LinkedHashMap<String, Boolean> targetDefaults() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(TargetFilters.IGNORE_FRIENDS, true);
        defaults.put(TargetFilters.IGNORE_STAFF, true);
        defaults.put(TargetFilters.IGNORE_ENEMIES, false);
        defaults.put(TargetFilters.IGNORE_NAKED, false);
        defaults.put(TargetFilters.VISIBLE_ONLY, false);
        return defaults;
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
        PlacementSlot slot = findPlacementSlot(null);
        return slot != null && slot.stack().getItem() instanceof BlockItem item
                ? item.getBlock().defaultBlockState() : null;
    }


    public enum TrapMode {
        FULL,
        TOP,
        SIDES
    }
}
