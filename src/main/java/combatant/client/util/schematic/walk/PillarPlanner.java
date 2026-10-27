/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.function.LongPredicate;

/**
 * Where to jump-place a column of support blocks when no walkable spot reaches the wanted cells.
 *
 * <p>A pillar starts on a reachable feet cell and rises one block per jump. Each candidate column is judged by
 * the coverage its top would have (with the column itself counted as solid), then by the walk to its foot plus
 * a fixed price per block, so a short pillar next to the player beats a tall one or a long walk. Columns too far
 * sideways from every target to reach one from any height are skipped before any coverage is computed.</p>
 */
public final class PillarPlanner {

    /** Price of one pillar block in walk-cost units: a jump, a placement and later a block to break again. */
    static final double BLOCK_COST = 3.0;

    /** Column at {@code (x, z)} from feet cell {@code y} up {@code height} blocks; the player ends at {@code y + height}. */
    public record Pillar(int x, int y, int z, int height, int coverage, double cost) {
        public int topY() {
            return y + height;
        }
    }

    private PillarPlanner() {
    }

    /**
     * @param forbidden cells no support block may occupy (the build footprint), or {@code null}
     * @param reject    feet cells not to start a pillar from, or {@code null}
     * @return the cheapest pillar reaching a share of the best coverage, or {@code null} if none reaches anything
     */
    public static Pillar plan(WalkTerrain terrain, WalkPathfinder.Flood flood, long[] targets, int targetCount,
                              double reach, int maxHeight, LongPredicate forbidden, LongPredicate reject) {
        Planner planner = new Planner(terrain, flood, targets, targetCount, reach, maxHeight, forbidden, reject);
        planner.step(Long.MAX_VALUE);
        return planner.result();
    }

    /** One pillar choice, resumable across ticks like {@link StandingSpotChooser.Scorer}. */
    public static final class Planner {
        private final WalkTerrain terrain;
        private final WalkPathfinder.Flood flood;
        private final long[] targets;
        private final int targetCount;
        private final double reach;
        private final int maxHeight;
        private final LongPredicate forbidden;
        private final LongPredicate reject;
        private final Pillar[] options;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private int cursor;
        private int bestCoverage;

        public Planner(WalkTerrain terrain, WalkPathfinder.Flood flood, long[] targets, int targetCount, double reach,
                       int maxHeight, LongPredicate forbidden, LongPredicate reject) {
            this.terrain = terrain;
            this.flood = flood;
            this.targets = targets;
            this.targetCount = targetCount;
            this.reach = reach;
            this.maxHeight = maxHeight;
            this.forbidden = forbidden;
            this.reject = reject;
            this.options = new Pillar[targetCount == 0 || maxHeight <= 0 ? 0 : flood.size()];
            for (int i = 0; i < targetCount; i++) {
                long t = targets[i];
                minY = Math.min(minY, WalkTerrain.unpackY(t));
                maxY = Math.max(maxY, WalkTerrain.unpackY(t));
                minX = Math.min(minX, WalkTerrain.unpackX(t));
                maxX = Math.max(maxX, WalkTerrain.unpackX(t));
                minZ = Math.min(minZ, WalkTerrain.unpackZ(t));
                maxZ = Math.max(maxZ, WalkTerrain.unpackZ(t));
            }
        }

        /** @return {@code true} once every reachable column has been judged */
        public boolean step(long deadlineNanos) {
            // Every call judges at least one column, so a late start still moves on.
            while (cursor < options.length) {
                int i = cursor++;
                long cell = flood.cell(i);
                if (reject != null && reject.test(cell) || !nearTargets(cell)) continue;
                options[i] = bestColumn(cell, flood.cost(cell));
                if (options[i] != null) bestCoverage = Math.max(bestCoverage, options[i].coverage());
                if (System.nanoTime() - deadlineNanos > 0) return cursor == options.length;
            }
            return true;
        }

        /** The cheapest column reaching a share of the best coverage, once {@link #step} returned {@code true}. */
        public Pillar result() {
            if (bestCoverage == 0) return null;
            int needed = Math.max(1, (int) Math.ceil(bestCoverage * StandingSpotChooser.COVERAGE_SHARE));
            Pillar best = null;
            for (Pillar option : options) {
                if (option == null || option.coverage() < needed) continue;
                if (best == null || option.cost() < best.cost()) best = option;
            }
            return best;
        }

        /** The column is within reach of the targets' box sideways; height alone never makes up for distance. */
        private boolean nearTargets(long cell) {
            double cx = WalkTerrain.unpackX(cell) + 0.5, cz = WalkTerrain.unpackZ(cell) + 0.5;
            double dx = cx < minX ? minX - cx : cx > maxX + 1 ? cx - maxX - 1 : 0.0;
            double dz = cz < minZ ? minZ - cz : cz > maxZ + 1 ? cz - maxZ - 1 : 0.0;
            return dx * dx + dz * dz <= (reach + 0.5) * (reach + 0.5);
        }

        /** The best height for a column at one feet cell, or {@code null} when no height reaches a target. */
        private Pillar bestColumn(long cell, double walkCost) {
            int x = WalkTerrain.unpackX(cell), y = WalkTerrain.unpackY(cell), z = WalkTerrain.unpackZ(cell);
            Pillar best = null;
            for (int h = 1; h <= maxHeight; h++) {
                int block = y + h - 1;
                // The new block's cell must be free and allowed, and the jump onto it needs head room above the top.
                if (!terrain.open(x, block, z) || forbidden != null && forbidden.test(WalkTerrain.pack(x, block, z))) break;
                if (!terrain.open(x, block + 1, z) || !terrain.open(x, block + 2, z) || !terrain.open(x, block + 3, z)) break;
                int feet = y + h;
                // Standing far above every target never helps; neither does a top still below the lowest reachable band.
                if (ReachModel.eyeY(feet) - reach > maxY + 1) break;
                if (ReachModel.eyeY(feet) + reach < minY) continue;
                WalkTerrain withColumn = column(terrain, x, y, z, h);
                int coverage = StandingSpotChooser.coverage(withColumn, x, feet, z, targets, targetCount,
                        StandingSpotChooser.Mode.PLACE, reach);
                if (coverage == 0) continue;
                Pillar option = new Pillar(x, y, z, h, coverage, walkCost + h * BLOCK_COST);
                if (best == null || coverage > best.coverage()) best = option;
            }
            return best;
        }
    }

    /** The terrain with support blocks at {@code (x, y .. y + height - 1, z)}. */
    static WalkTerrain column(WalkTerrain terrain, int x, int y, int z, int height) {
        return (cx, cy, cz) -> cx == x && cz == z && cy >= y && cy < y + height ? WalkTerrain.SOLID : terrain.cell(cx, cy, cz);
    }

    /** Placement rate of a pillar, comparable with a standing spot's: its blocks are priced as extra walking. */
    public static double rate(Pillar pillar) {
        return StandingSpotChooser.rate(pillar.coverage(), pillar.cost());
    }
}
