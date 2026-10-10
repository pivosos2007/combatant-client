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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.finder.ChunkBlocks;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scores chunks by growth that only happens while a player keeps them loaded, plus amethyst
 * that anti-xray hides but block light still reveals. Port of 67Client's SusChunkScanner.
 *
 * <p>Plants only random-tick inside simulation distance, so max-age kelp, tall bamboo, ripe
 * berries and long vines mean someone spent hours nearby. Each signal has a weight and a
 * per-chunk cap so one freak kelp forest cannot flag a chunk alone.</p>
 */
@ModuleInfo(
        id = "suschunkfinder",
        displayName = "SusChunkFinder",
        aliases = {"GrowthFinder", "OPChunkFinder"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.suschunkfinder.description")
public final class SusChunkFinder extends ChunkFinderModule<SusChunkFinder.Scored> {

    // Growth below/above this band is either bedrock-level noise or open sky farms.
    private static final int SCAN_MIN_Y = -12;
    private static final int SCAN_MAX_Y = 80;
    private static final int POINTS_PER_SENSITIVITY = 5;
    // Amethyst clusters emit exactly this block light; the cell keeps it even when hidden.
    private static final int AMETHYST_CLUSTER_LIGHT = 5;
    private static final int MAX_COLUMN = 40;

    private final NumberValue<Integer> sensitivity = num("sensitivity", 5, 1, 10);
    private final BooleanMapValue signals = group("signals", defaultSignals());

    public SusChunkFinder() {
        super(4, "#40CB40FF", "#FFCB40FF", 3, 3000L);
    }

    private static Map<String, Boolean> defaultSignals() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        for (Signal signal : Signal.values()) {
            defaults.put(signal.id, true);
        }
        return defaults;
    }

    @Override
    protected String analysisSignature() {
        StringBuilder signature = new StringBuilder().append(sensitivity.get()).append('|');
        for (Signal signal : Signal.values()) {
            signature.append(signals.get(signal.id) ? '1' : '0');
        }
        return signature.toString();
    }

    @Override
    protected Scored analyzeChunk(ClientLevel level, LevelChunk chunk) {
        Tally tally = new Tally();
        if (signals.get(Signal.AMETHYST.id)) countHiddenAmethyst(level, chunk, tally);
        countGrowth(level, chunk, tally);

        double score = tally.score();
        if (score < sensitivity.get() * POINTS_PER_SENSITIVITY || tally.evidence == null) return null;
        return new Scored(tally.describe(score), tally.evidence, score);
    }

    // A cluster cell next to budding amethyst that still carries light 5: either a visible
    // cluster, or air where anti-xray removed one. The server-sent light gives it away.
    private void countHiddenAmethyst(ClientLevel level, LevelChunk chunk, Tally tally) {
        ChunkBlocks.forEach(chunk, SCAN_MIN_Y, SCAN_MAX_Y, s -> s.is(Blocks.BUDDING_AMETHYST), (x, y, z, state) -> {
            BlockPos budding = new BlockPos(x, y, z);
            for (Direction face : Direction.values()) {
                BlockPos cell = budding.relative(face);
                BlockState cellState = level.getBlockState(cell);
                if ((cellState.isAir() || cellState.is(Blocks.AMETHYST_CLUSTER))
                        && level.getBrightness(LightLayer.BLOCK, cell) == AMETHYST_CLUSTER_LIGHT) {
                    tally.add(Signal.AMETHYST, cell);
                }
            }
            return !tally.capped(Signal.AMETHYST);
        });
    }

    private void countGrowth(ClientLevel level, LevelChunk chunk, Tally tally) {
        ChunkBlocks.forEach(chunk, SCAN_MIN_Y, SCAN_MAX_Y, SusChunkFinder::isPlantTarget, (x, y, z, state) -> {
            inspectPlant(level, new BlockPos(x, y, z), state, tally);
            return true;
        });
    }

    private void inspectPlant(ClientLevel level, BlockPos pos, BlockState state, Tally tally) {
        Block block = state.getBlock();
        if ((block == Blocks.KELP || block == Blocks.KELP_PLANT) && signals.get(Signal.KELP.id)) {
            // Measure from the top of each column only, so a column counts once.
            if (isAny(level.getBlockState(pos.above()), Blocks.KELP, Blocks.KELP_PLANT)) return;
            int height = column(level, pos, Direction.DOWN, Blocks.KELP, Blocks.KELP_PLANT);
            boolean maxAge = block == Blocks.KELP
                    && state.hasProperty(BlockStateProperties.AGE_25)
                    && state.getValue(BlockStateProperties.AGE_25) == 25;
            if ((maxAge && height >= 8) || height >= 14) tally.add(Signal.KELP, pos);
        } else if (block == Blocks.BAMBOO && signals.get(Signal.BAMBOO.id)) {
            if (level.getBlockState(pos.below()).is(Blocks.BAMBOO)) return;
            if (column(level, pos, Direction.UP, Blocks.BAMBOO) >= 12) tally.add(Signal.BAMBOO, pos);
        } else if (block == Blocks.SWEET_BERRY_BUSH && signals.get(Signal.BERRIES.id)) {
            if (state.hasProperty(BlockStateProperties.AGE_3) && state.getValue(BlockStateProperties.AGE_3) == 3) {
                tally.add(Signal.BERRIES, pos);
            }
        } else if (block == Blocks.VINE && signals.get(Signal.VINES.id)) {
            if (level.getBlockState(pos.above()).is(Blocks.VINE)) return;
            if (column(level, pos, Direction.DOWN, Blocks.VINE) >= 7) tally.add(Signal.VINES, pos);
        } else if (block == Blocks.POINTED_DRIPSTONE && signals.get(Signal.DRIPSTONE.id)) {
            if (level.getBlockState(pos.above()).is(Blocks.POINTED_DRIPSTONE)
                    || !level.getBlockState(pos.below()).is(Blocks.POINTED_DRIPSTONE)) return;
            if (column(level, pos, Direction.DOWN, Blocks.POINTED_DRIPSTONE) >= 5) tally.add(Signal.DRIPSTONE, pos);
        }
    }

    private static int column(ClientLevel level, BlockPos start, Direction direction, Block... blocks) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        int length = 1;
        while (length < MAX_COLUMN) {
            cursor.move(direction);
            if (!isAny(level.getBlockState(cursor), blocks)) break;
            length++;
        }
        return length;
    }

    private static boolean isAny(BlockState state, Block... blocks) {
        for (Block block : blocks) {
            if (state.is(block)) return true;
        }
        return false;
    }

    private static boolean isPlantTarget(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.KELP
                || block == Blocks.KELP_PLANT
                || block == Blocks.BAMBOO
                || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.VINE
                || block == Blocks.POINTED_DRIPSTONE;
    }

    private static final class Tally {
        private final EnumMap<Signal, Integer> hits = new EnumMap<>(Signal.class);
        private BlockPos evidence;
        private double evidenceWeight;

        void add(Signal signal, BlockPos pos) {
            if (capped(signal)) return;
            hits.merge(signal, 1, Integer::sum);
            // Keep the heaviest signal's position as the marker's "proof" block.
            if (signal.weight >= evidenceWeight) {
                evidence = pos.immutable();
                evidenceWeight = signal.weight;
            }
        }

        boolean capped(Signal signal) {
            return hits.getOrDefault(signal, 0) >= signal.cap;
        }

        double score() {
            double total = 0.0;
            for (Map.Entry<Signal, Integer> entry : hits.entrySet()) {
                total += entry.getKey().weight * entry.getValue();
            }
            return total;
        }

        String describe(double score) {
            StringBuilder out = new StringBuilder("Score ").append((int) score).append(" (");
            boolean first = true;
            for (Map.Entry<Signal, Integer> entry : hits.entrySet()) {
                if (!first) out.append(", ");
                out.append(entry.getKey().label).append(" x").append(entry.getValue());
                first = false;
            }
            return out.append(')').toString();
        }
    }

    private enum Signal {
        AMETHYST("amethyst", "amethyst", 6.0, 16),
        KELP("kelp", "kelp", 2.0, 6),
        BAMBOO("bamboo", "bamboo", 2.0, 6),
        BERRIES("berries", "berries", 2.0, 5),
        VINES("vines", "vines", 2.0, 6),
        DRIPSTONE("dripstone", "dripstone", 2.0, 5);

        private final String id;
        private final String label;
        private final double weight;
        private final int cap;

        Signal(String id, String label, double weight, int cap) {
            this.id = id;
            this.label = label;
            this.weight = weight;
            this.cap = cap;
        }
    }

    public record Scored(String reason, BlockPos evidence, double score) implements ChunkFinderModule.Hit {
    }
}
