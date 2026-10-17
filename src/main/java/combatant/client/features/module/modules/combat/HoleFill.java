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
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.target.TargetingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@ModuleInfo(
        id = "holefill",
        displayName = "HoleFill",
        aliases = {"FillHoles", "AntiHole"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.holefill.description")
public final class HoleFill extends Module {

    private static final Set<Block> SAFE_BLOCKS = Set.of(
            Blocks.BEDROCK,
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
    // custom = the settings below as set; grim = place range <= 4.5, wall range 0, rotation NORMAL, 1 block/tick;
    // vanilla = 5.5 range/wall range, NO_ROTATION.
    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.CUSTOM);
    private final NumberValue<Double> range = numCommon(
            "holeFillRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> wallRange = numCommon(
            "holeFillWallRange", "wall_range", CommonSettingSchemas.COMBAT_WALL_RANGE, 3.0D, 0.0D, 6.0D);
    private final NumberValue<Double> enemyRange = num("holeFillEnemyRange", "enemy_range", 6.0D, 2.0D, 12.0D);
    private final BooleanMapValue targetFilters = groupCommon(
            "holeFillTargets", "targets", "target", targetDefaults());
    private final EnumValue<BlocksMode> blocksMode = enumSetting(
            "holeFillBlocksMode", "blocks_mode", BlocksMode.OBSIDIAN, BlocksMode.values());
    private final NumberValue<Integer> maxHoles = num("holeFillMaxHoles", "max_holes", 8, 1, 32);
    private final NumberValue<Integer> scanInterval = num("holeFillScanInterval", "scan_interval_ticks", 2, 1, 10);
    private final BooleanValue autoDisable = bool("holeFillAutoDisable", "auto_disable", false);
    private final EnumValue<BlockPlacer.RotationMode> rotation = enumSetting(
            "holeFillRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());
    private final NumberValue<Integer> delay = num("holeFillDelay", "delay", 0, 0, 10);
    private final BooleanValue render = bool("holeFillRender", "render", true);
    private final RGBAColorValue fillColor = color("holeFillFillColor", "fill_color", "#285A9C44");
    private final RGBAColorValue lineColor = color("holeFillLineColor", "line_color", "#55AAFFFF");
    private final NumberValue<Float> lineWidth = numCommon(
            "holeFillLineWidth", "line_width", CommonSettingSchemas.LINE_WIDTH, 2.0F, 1.0F, 6.0F);

    private final BlockPlacer blockPlacer = new BlockPlacer(
            this, this, 10,
            this::findPlacementSlot,
            this::effPlaceRange,
            this::effWallRange,
            delay::get,
            delay::get,
            () -> 0,
            () -> 0,
            () -> 1,
            this::effConstructFailResult,
            () -> true,
            () -> false,
            this::effRotation,
            () -> MovementCorrection.SILENT
    );
    private final List<BlockPos> currentTargets = new ArrayList<>();
    private int scanTicker;

    @Override
    public void onEnable() {
        scanTicker = 0;
        blockPlacer.enable();
        updateTargets();
    }

    @Override
    public void onDisable() {
        blockPlacer.disable();
        currentTargets.clear();
        scanTicker = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        blockPlacer.tick();
        if (++scanTicker >= scanInterval.get()) {
            scanTicker = 0;
            updateTargets();
        }
        if (autoDisable.get() && currentTargets.isEmpty()) setEnabled(false);
    }

    private void updateTargets() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            currentTargets.clear();
            blockPlacer.clear();
            return;
        }

        List<Player> opponents = findOpponents();
        if (opponents.isEmpty()) {
            currentTargets.clear();
            blockPlacer.clear();
            return;
        }

        BlockPos origin = player.blockPosition();
        int radius = (int) Math.ceil(effPlaceRange());
        double placementRangeSq = effPlaceRange() * effPlaceRange();
        double enemyRangeSq = enemyRange.get() * enemyRange.get();
        List<HoleCandidate> candidates = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    double px = pos.getX() + 0.5D;
                    double py = pos.getY();
                    double pz = pos.getZ() + 0.5D;
                    if (player.distanceToSqr(px, py, pz) > placementRangeSq || !isHole(level, pos)) continue;

                    double nearestEnemySq = Double.MAX_VALUE;
                    for (Player opponent : opponents) {
                        if (opponent.blockPosition().equals(pos)) continue;
                        nearestEnemySq = Math.min(nearestEnemySq, opponent.distanceToSqr(px, py, pz));
                    }
                    if (nearestEnemySq <= enemyRangeSq) candidates.add(new HoleCandidate(pos.immutable(), nearestEnemySq));
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(HoleCandidate::enemyDistanceSq)
                .thenComparingDouble(candidate -> player.distanceToSqr(
                        candidate.pos().getX() + 0.5D, candidate.pos().getY(), candidate.pos().getZ() + 0.5D)));

        List<BlockPos> selected = candidates.stream().limit(maxHoles.get()).map(HoleCandidate::pos).toList();
        currentTargets.clear();
        currentTargets.addAll(selected);
        blockPlacer.update(selected);
    }

    private List<Player> findOpponents() {
        double searchRange = enemyRange.get() + effPlaceRange();
        List<LivingEntity> targets = TargetingUtil.findTargets(mc, new TargetingUtil.TargetingSettings(
                searchRange,
                180.0F,
                true,
                targetFilters.get(TargetFilters.IGNORE_FRIENDS),
                targetFilters.get(TargetFilters.IGNORE_STAFF),
                targetFilters.get(TargetFilters.IGNORE_ENEMIES),
                targetFilters.get(TargetFilters.IGNORE_NAKED),
                true,
                targetFilters.get(TargetFilters.VISIBLE_ONLY),
                TargetingUtil.TargetPriority.DISTANCE));
        List<Player> players = new ArrayList<>();
        for (LivingEntity target : targets) {
            if (target instanceof Player player) players.add(player);
        }
        return players;
    }

    private static boolean isHole(ClientLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).canBeReplaced()
                || !level.getBlockState(pos.above()).canBeReplaced()
                || !level.getBlockState(pos.above(2)).canBeReplaced()
                || !isSafe(level, pos.below())) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!isSafe(level, pos.relative(direction))) return false;
        }
        return true;
    }

    private static boolean isSafe(ClientLevel level, BlockPos pos) {
        return SAFE_BLOCKS.contains(level.getBlockState(pos).getBlock());
    }

    private BlockPlacer.PlacementSlot findPlacementSlot(BlockPos ignored) {
        LocalPlayer player = mc.player;
        if (player == null) return null;
        ItemStack offhand = player.getOffhandItem();
        if (isTargetItem(offhand)) return new BlockPlacer.PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isTargetItem(stack)) return new BlockPlacer.PlacementSlot(slot, InteractionHand.MAIN_HAND, stack);
        }
        return null;
    }

    private double effPlaceRange() {
        return anticheat.get().limit(range.get(), AntiCheatPreset.GRIM_BLOCK_RANGE, AntiCheatPreset.VANILLA_BLOCK_RANGE);
    }

    private double effWallRange() {
        return anticheat.get().limit(wallRange.get(), 0.0D, AntiCheatPreset.VANILLA_BLOCK_RANGE);
    }

    private BlockPlacer.RotationMode effRotation() {
        return anticheat.get().pick(rotation.get(), BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.NO_ROTATION);
    }

    private boolean effConstructFailResult() {
        return effRotation() == BlockPlacer.RotationMode.NO_ROTATION;
    }

    private boolean isTargetItem(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        return switch (blocksMode.get()) {
            case OBSIDIAN -> block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN;
            case WEBS -> block == Blocks.COBWEB;
            case ANY_SAFE -> SAFE_BLOCKS.contains(block) || block == Blocks.COBWEB;
        };
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
        if (!isEnabled() || !render.get() || currentTargets.isEmpty()) return;
        for (BlockPos pos : currentTargets) {
            AABB box = new AABB(pos);
            renderer.filledBox(box, fillColor.getArgb());
            renderer.outlineBox(box, lineColor.getArgb(), lineWidth.get());
        }
    }

    private record HoleCandidate(BlockPos pos, double enemyDistanceSq) {
    }

    public enum BlocksMode {
        OBSIDIAN,
        WEBS,
        ANY_SAFE
    }
}
