/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.finder.ChunkBlocks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Flags chunks with blocks worldgen never produces on its own: signs that a player lived there.
 *
 * <p>Signals and thresholds follow WaterClient's OGChunkFinder, plus full-honey hives from
 * 67Client and "many chests below y=0" from WaterClient's ChunkFinder. Checks run strongest
 * first and stop at the first hit, so a chunk costs one pass in the common case.</p>
 */
@ModuleInfo(
        id = "ogchunkfinder",
        displayName = "OGChunkFinder",
        aliases = {"BaseFinder", "OldChunkFinder"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.ogchunkfinder.description")
public final class OGChunkFinder extends ChunkFinderModule<OGChunkFinder.Found> {

    private static final String REPEATERS = "powered_repeaters";
    private static final String LIGHTS = "light_near_zero";
    private static final String ROTATED_DEEPSLATE = "rotated_deepslate";
    private static final String COBBLED_DEEPSLATE = "cobbled_deepslate";
    private static final String HIVES = "bee_hives";
    private static final String CHESTS = "chests_below_zero";
    private static final String SEAGRASS = "grown_seagrass";
    private static final String FLOWERS = "flowers_underground";
    private static final String VINES = "long_vines";

    // Thresholds from the reference finders; lower values flag natural terrain.
    private static final int POWERED_REPEATER_MIN = 3;
    private static final int DEEPSLATE_MIN = 3;
    private static final int TALL_SEAGRASS_MIN = 75;
    private static final int SEAGRASS_MIN = 70;
    private static final int FLOWER_MIN = 6;
    private static final int VINE_MIN = 150;
    private static final int CHEST_MIN = 10;
    private static final int HONEY_FULL = 5;

    private final BooleanMapValue signals = group("signals", defaultSignals());

    public OGChunkFinder() {
        super(24, "#4050FF00", "#FF50FF00", 4, 3000L);
    }

    private static Map<String, Boolean> defaultSignals() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(REPEATERS, true);
        defaults.put(LIGHTS, true);
        defaults.put(ROTATED_DEEPSLATE, true);
        defaults.put(COBBLED_DEEPSLATE, true);
        defaults.put(HIVES, true);
        defaults.put(CHESTS, true);
        defaults.put(SEAGRASS, true);
        defaults.put(FLOWERS, false);
        defaults.put(VINES, true);
        return defaults;
    }

    @Override
    protected String analysisSignature() {
        StringBuilder signature = new StringBuilder();
        for (String key : defaultSignals().keySet()) {
            signature.append(signals.get(key) ? '1' : '0');
        }
        return signature.toString();
    }

    @Override
    protected Found analyzeChunk(ClientLevel level, LevelChunk chunk) {
        Found hit;
        if (signals.get(REPEATERS) && (hit = countAtLeast(chunk, -64, 320, OGChunkFinder::isPoweredRepeater,
                POWERED_REPEATER_MIN, "Powered repeaters")) != null) return hit;
        if (signals.get(LIGHTS) && (hit = firstMatch(chunk, -1, 1, s -> isPlacedLight(s.getBlock()),
                "Light source at y=0")) != null) return hit;
        if (signals.get(ROTATED_DEEPSLATE) && (hit = countAtLeast(chunk, 0, 63, OGChunkFinder::isRotatedDeepslate,
                DEEPSLATE_MIN, "Rotated deepslate")) != null) return hit;
        if (signals.get(COBBLED_DEEPSLATE) && (hit = countAtLeast(chunk, 0, 50, s -> s.is(Blocks.COBBLED_DEEPSLATE),
                DEEPSLATE_MIN, "Cobbled deepslate")) != null) return hit;
        if (signals.get(HIVES) && (hit = findWorkedHive(chunk)) != null) return hit;
        if (signals.get(CHESTS) && (hit = countAtLeast(chunk, -64, -1,
                s -> s.is(Blocks.CHEST) || s.is(Blocks.TRAPPED_CHEST), CHEST_MIN, "Chests below y=0")) != null) return hit;
        if (signals.get(SEAGRASS)) {
            if ((hit = countAtLeast(chunk, -64, 320, s -> s.is(Blocks.TALL_SEAGRASS), TALL_SEAGRASS_MIN,
                    "Grown tall seagrass")) != null) return hit;
            if ((hit = countAtLeast(chunk, -64, 16, s -> s.is(Blocks.SEAGRASS), SEAGRASS_MIN,
                    "Grown seagrass")) != null) return hit;
        }
        if (signals.get(FLOWERS) && (hit = countAtLeast(chunk, -64, 16, s -> s.is(BlockTags.FLOWERS), FLOWER_MIN,
                "Flowers underground")) != null) return hit;
        if (signals.get(VINES) && (hit = countAtLeast(chunk, -64, 320, s -> s.is(Blocks.VINE), VINE_MIN,
                "Overgrown vines")) != null) return hit;
        return null;
    }

    private static Found countAtLeast(LevelChunk chunk, int minY, int maxY, Predicate<BlockState> match,
                                      int threshold, String reason) {
        BlockPos[] last = {null};
        int[] count = {0};
        ChunkBlocks.forEach(chunk, minY, maxY, match, (x, y, z, state) -> {
            last[0] = new BlockPos(x, y, z);
            return ++count[0] < threshold;
        });
        return count[0] >= threshold ? new Found(reason, last[0]) : null;
    }

    private static Found firstMatch(LevelChunk chunk, int minY, int maxY, Predicate<BlockState> match, String reason) {
        BlockPos pos = ChunkBlocks.first(chunk, minY, maxY, match);
        return pos != null ? new Found(reason, pos) : null;
    }

    // Hives fill to honey level 5 over time and only hold bees if something kept them alive;
    // either state in a natural nest is rare, both together almost never happen by chance.
    private static Found findWorkedHive(LevelChunk chunk) {
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            if (!(entry.getValue() instanceof BeehiveBlockEntity hive)) continue;
            BlockState state = chunk.getBlockState(entry.getKey());
            boolean fullHoney = state.hasProperty(BeehiveBlock.HONEY_LEVEL)
                    && state.getValue(BeehiveBlock.HONEY_LEVEL) >= HONEY_FULL;
            if (fullHoney || !hive.isEmpty()) {
                return new Found(fullHoney ? "Full honey hive" : "Occupied bee hive", entry.getKey());
            }
        }
        return null;
    }

    private static boolean isPoweredRepeater(BlockState state) {
        return state.is(Blocks.REPEATER) && state.getValue(RepeaterBlock.POWERED);
    }

    // Natural deepslate is always vertical; sideways logs of it only come from placement.
    private static boolean isRotatedDeepslate(BlockState state) {
        return state.is(Blocks.DEEPSLATE)
                && state.hasProperty(RotatedPillarBlock.AXIS)
                && state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y;
    }

    private static boolean isPlacedLight(Block block) {
        return block == Blocks.TORCH
                || block == Blocks.WALL_TORCH
                || block == Blocks.SOUL_TORCH
                || block == Blocks.SOUL_WALL_TORCH
                || block == Blocks.LANTERN
                || block == Blocks.SOUL_LANTERN
                || block == Blocks.GLOWSTONE
                || block == Blocks.SHROOMLIGHT
                || block == Blocks.SEA_LANTERN
                || block == Blocks.JACK_O_LANTERN
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.REDSTONE_LAMP
                || block == Blocks.END_ROD
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.OCHRE_FROGLIGHT
                || block == Blocks.VERDANT_FROGLIGHT
                || block == Blocks.PEARLESCENT_FROGLIGHT;
    }

    public record Found(String reason, BlockPos evidence) implements ChunkFinderModule.Hit {
    }
}
