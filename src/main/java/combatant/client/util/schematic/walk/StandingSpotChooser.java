/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.Arrays;
import java.util.function.LongPredicate;

/**
 * Picks the feet cell to walk to so that as many of the wanted cells as possible can be clicked from it.
 *
 * <p>Spots compete on placement rate: targets reached divided by the ticks spent walking there (along the real
 * route, as the flood priced it) and placing them, so a nearby spot reaching most of the layer beats a far one
 * reaching slightly more. Among the spots close to the best rate, the one whose best follow-up spot is also cheap
 * wins: a stop that leaves the rest of the layer one step away beats one that strands it on the far side. The
 * previous goal is kept while its rate stays close to the best one, so two near-equal spots do not make the player
 * walk back and forth.</p>
 *
 * <p>Exact coverage (a line-of-sight check per target) is the expensive part. A cheap upper bound, the targets
 * within reach of the eyes at all, orders the candidates, and the exact pass stops as soon as no remaining
 * candidate could still come within the lookahead band of the best one found.</p>
 */
public final class StandingSpotChooser {

    /** Pillar columns covering at least this share of the best coverage compete on cost. */
    static final double COVERAGE_SHARE = 0.75;
    /** Ticks to walk one unit of route cost (walking speed is about 0.22 blocks a tick). */
    static final double WALK_TICKS = 4.6;
    /** Ticks a placement takes once the player stands (turn, click, delay). */
    static final double PLACE_TICKS = 3.0;
    /** Fixed price of a stop: planning, settling, the first turn. */
    static final double STOP_TICKS = 25.0;
    /** The previous goal survives while its rate is at least this share of the best one. */
    static final double HYSTERESIS_SHARE = 0.85;
    /** Spots within this share of the best rate compete on the two-stop lookahead. */
    static final double LOOKAHEAD_BAND = 0.8;
    /** A target's support face can be this much nearer to the eyes than its centre (half a cell diagonal). */
    private static final double FACE_SLACK = 0.87;

    public enum Mode {
        PLACE,
        BREAK
    }

    /**
     * Extra condition on a target seen from given eyes: a piston or observer only comes out facing right when the
     * look from there runs along the right axis, so only spots with that look count for it.
     */
    @FunctionalInterface
    public interface LookFilter {
        boolean accepts(long target, double eyeX, double eyeY, double eyeZ);
    }

    /** A feet cell, how many targets it reaches and what walking there costs. */
    public record Spot(int x, int y, int z, int coverage, double cost) {
        public long key() {
            return WalkTerrain.pack(x, y, z);
        }
    }

    private StandingSpotChooser() {
    }

    /**
     * @param flood   cells reachable from the player, see {@link WalkPathfinder#flood}
     * @param targets wanted cells, packed with {@link WalkTerrain#pack}
     * @param reach   click reach, measured from the eyes of a player centred in the feet cell
     * @param current the goal chosen last time, or {@code null}
     * @param reject  spots not to use (ones that were tried and turned out useless), or {@code null}
     * @return the spot to walk to, or {@code null} when no reachable spot covers a single target
     */
    public static Spot choose(WalkTerrain terrain, WalkPathfinder.Flood flood, long[] targets, int targetCount,
                              Mode mode, double reach, Spot current, LongPredicate reject) {
        return choose(terrain, flood, targets, targetCount, mode, reach, current, reject, null);
    }

    /** As above; {@code look} (may be null) drops targets the spot's look cannot place right. */
    public static Spot choose(WalkTerrain terrain, WalkPathfinder.Flood flood, long[] targets, int targetCount,
                              Mode mode, double reach, Spot current, LongPredicate reject, LookFilter look) {
        Scorer scorer = new Scorer(terrain, flood, targets, targetCount, mode, reach, reject, look);
        scorer.step(Long.MAX_VALUE);
        return scorer.result(current);
    }

    /** Targets placed per tick spent walking {@code cost} route units and placing them. */
    public static double rate(int coverage, double cost) {
        return coverage / (cost * WALK_TICKS + coverage * PLACE_TICKS + STOP_TICKS);
    }

    /** How many targets a player standing centred in {@code (fx, fy, fz)} can click. */
    public static int coverage(WalkTerrain terrain, int fx, int fy, int fz, long[] targets, int targetCount,
                               Mode mode, double reach) {
        return coverage(terrain, fx, fy, fz, targets, targetCount, mode, reach, null);
    }

    static int coverage(WalkTerrain terrain, int fx, int fy, int fz, long[] targets, int targetCount,
                        Mode mode, double reach, LookFilter look) {
        double ex = fx + 0.5, ey = ReachModel.eyeY(fy), ez = fz + 0.5;
        double prefilter = (reach + 1.0) * (reach + 1.0);
        int count = 0;
        for (int i = 0; i < targetCount; i++) {
            if (clickable(terrain, targets[i], fx, fy, fz, ex, ey, ez, mode, reach, prefilter, look)) count++;
        }
        return count;
    }

    private static boolean clickable(WalkTerrain terrain, long target, int fx, int fy, int fz,
                                     double ex, double ey, double ez, Mode mode, double reach, double prefilter,
                                     LookFilter look) {
        int tx = WalkTerrain.unpackX(target), ty = WalkTerrain.unpackY(target), tz = WalkTerrain.unpackZ(target);
        // A block cannot go where the body stands.
        if (mode == Mode.PLACE && tx == fx && tz == fz && (ty == fy || ty == fy + 1)) return false;
        double dx = tx + 0.5 - ex, dy = ty + 0.5 - ey, dz = tz + 0.5 - ez;
        if (dx * dx + dy * dy + dz * dz > prefilter) return false;
        if (look != null && !look.accepts(target, ex, ey, ez)) return false;
        return mode == Mode.PLACE
                ? ReachModel.placeable(terrain, ex, ey, ez, tx, ty, tz, reach)
                : ReachModel.breakable(terrain, ex, ey, ez, tx, ty, tz, reach);
    }

    /**
     * The scoring of one choice, resumable: {@link #step} works until it is done or a deadline passes, then
     * {@link #result} picks the spot. The flood must stay unchanged until then.
     */
    public static final class Scorer {
        private final WalkTerrain terrain;
        private final WalkPathfinder.Flood flood;
        /** Targets sorted by x (packed keys sort by x first), so a spot only looks at the slice within reach. */
        private final long[] sorted;
        private final Mode mode;
        private final double reach;
        private final LongPredicate reject;
        private final LookFilter look;
        private final int lowest;
        private final int[] bounds;
        private final int words;

        private int stage;
        private int cursor;
        /** Candidates as {@code tier << 62 | upper-bound rate bits << 24 | flood index}, sorted after the bound pass. */
        private long[] candidates = new long[64];
        private int candidateCount;

        private int[] poolCell = new int[16];
        private int[] poolCoverage = new int[16];
        private long[][] poolMask = new long[16][];
        private int poolSize;
        private double bestRate;
        private boolean highTierHit;
        private int evaluated;

        public Scorer(WalkTerrain terrain, WalkPathfinder.Flood flood, long[] targets, int targetCount, Mode mode,
                      double reach, LongPredicate reject) {
            this(terrain, flood, targets, targetCount, mode, reach, reject, null);
        }

        public Scorer(WalkTerrain terrain, WalkPathfinder.Flood flood, long[] targets, int targetCount, Mode mode,
                      double reach, LongPredicate reject, LookFilter look) {
            this.terrain = terrain;
            this.flood = flood;
            this.sorted = Arrays.copyOf(targets, Math.max(0, targetCount));
            Arrays.sort(sorted);
            this.mode = mode;
            this.reach = reach;
            this.reject = reject;
            this.look = look;
            this.words = (sorted.length + 63) >> 6;
            int low = Integer.MAX_VALUE;
            for (long target : sorted) low = Math.min(low, WalkTerrain.unpackY(target));
            this.lowest = low;
            this.bounds = sorted.length == 0 ? null : bounds(sorted, reach);
            this.stage = sorted.length == 0 ? 2 : 0;
        }

        /** Number of spots whose exact coverage was computed, for tests and diagnostics. */
        public int evaluated() {
            return evaluated;
        }

        /**
         * Scores until done or past the deadline; every call makes some progress, even one that starts late.
         *
         * @return {@code true} once scoring is complete
         */
        public boolean step(long deadlineNanos) {
            while (stage == 0) {
                if (cursor == flood.size()) {
                    Arrays.sort(candidates, 0, candidateCount);
                    cursor = candidateCount - 1;
                    stage = 1;
                    break;
                }
                boundCell(cursor++);
                if ((cursor & 63) == 0 && System.nanoTime() - deadlineNanos > 0) return false;
            }
            while (stage == 1) {
                if (cursor < 0) {
                    stage = 2;
                    break;
                }
                if (!exactCandidate(candidates[cursor--])) {
                    stage = 2;
                    break;
                }
                if (System.nanoTime() - deadlineNanos > 0) return cursor < 0 && finish();
            }
            return true;
        }

        private boolean finish() {
            stage = 2;
            return true;
        }

        private void boundCell(int index) {
            long cell = flood.cell(index);
            int x = WalkTerrain.unpackX(cell), y = WalkTerrain.unpackY(cell), z = WalkTerrain.unpackZ(cell);
            if (!inside(bounds, x, y, z) || reject != null && reject.test(cell)) return;
            int bound = upperBound(x, y, z);
            if (bound == 0) return;
            float rate = (float) rate(bound, flood.cost(cell));
            long tier = y >= lowest ? 1L : 0L;
            if (candidateCount == candidates.length) candidates = Arrays.copyOf(candidates, candidateCount * 2);
            candidates[candidateCount++] = tier << 62 | (long) Float.floatToIntBits(rate) << 24 | index;
        }

        /** Targets near enough to the eyes that a face of them could be in reach. */
        private int upperBound(int fx, int fy, int fz) {
            double ex = fx + 0.5, ey = ReachModel.eyeY(fy), ez = fz + 0.5;
            double limit = reach + FACE_SLACK;
            double limitSq = limit * limit;
            int count = 0;
            for (int i = first(fx - (int) Math.ceil(limit) - 1); i < sorted.length; i++) {
                long target = sorted[i];
                int tx = WalkTerrain.unpackX(target);
                if (tx > fx + limit + 1) break;
                int ty = WalkTerrain.unpackY(target), tz = WalkTerrain.unpackZ(target);
                if (mode == Mode.PLACE && tx == fx && tz == fz && (ty == fy || ty == fy + 1)) continue;
                double dx = tx + 0.5 - ex, dy = ty + 0.5 - ey, dz = tz + 0.5 - ez;
                if (dx * dx + dy * dy + dz * dz <= limitSq) count++;
            }
            return count;
        }

        /** @return {@code false} when no remaining candidate can matter any more */
        private boolean exactCandidate(long candidate) {
            int tier = (int) (candidate >>> 62);
            double bound = Float.intBitsToFloat((int) (candidate >>> 24 & 0x7FFFFFFFL));
            int index = (int) (candidate & 0xFFFFFF);
            // Spots level with the lowest target stand on the layer below it. While any of them reaches something,
            // lower spots are out: stepping down off a solid build to finish its edge from the ground strands the
            // player under the next layer, which nothing but a pillar reaches again.
            if (tier == 0 && highTierHit) return false;
            // Candidates come best bound first: once one cannot reach the band, none after it in its tier can.
            // A positive best rate within the upper tier means that tier had a hit, so the lower one is out too.
            if (bestRate > 0.0 && bound < bestRate * LOOKAHEAD_BAND) return false;
            long cell = flood.cell(index);
            long[] mask = new long[words];
            int coverage = exactCoverage(WalkTerrain.unpackX(cell), WalkTerrain.unpackY(cell), WalkTerrain.unpackZ(cell), mask);
            evaluated++;
            if (coverage == 0) return true;
            if (tier == 1) highTierHit = true;
            bestRate = Math.max(bestRate, rate(coverage, flood.cost(cell)));
            if (poolSize == poolCell.length) {
                poolCell = Arrays.copyOf(poolCell, poolSize * 2);
                poolCoverage = Arrays.copyOf(poolCoverage, poolSize * 2);
                poolMask = Arrays.copyOf(poolMask, poolSize * 2);
            }
            poolCell[poolSize] = index;
            poolCoverage[poolSize] = coverage;
            poolMask[poolSize] = mask;
            poolSize++;
            return true;
        }

        private int exactCoverage(int fx, int fy, int fz, long[] mask) {
            double ex = fx + 0.5, ey = ReachModel.eyeY(fy), ez = fz + 0.5;
            double prefilter = (reach + 1.0) * (reach + 1.0);
            int count = 0;
            for (int i = first(fx - (int) Math.ceil(reach) - 2); i < sorted.length; i++) {
                long target = sorted[i];
                if (WalkTerrain.unpackX(target) > fx + reach + 2) break;
                if (clickable(terrain, target, fx, fy, fz, ex, ey, ez, mode, reach, prefilter, look)) {
                    count++;
                    if (mask != null) mask[i >> 6] |= 1L << i;
                }
            }
            return count;
        }

        /** Index of the first sorted target with x at least {@code x}. */
        private int first(int x) {
            int lo = 0, hi = sorted.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (WalkTerrain.unpackX(sorted[mid]) < x) lo = mid + 1;
                else hi = mid;
            }
            return lo;
        }

        /** The spot to walk to once {@link #step} returned {@code true}. */
        public Spot result(Spot current) {
            if (poolSize == 0) return null;
            int floor = highTierHit ? lowest : Integer.MIN_VALUE;
            double best = 0.0;
            for (int i = 0; i < poolSize; i++) {
                if (y(i) >= floor) best = Math.max(best, rate(poolCoverage[i], cost(i)));
            }
            if (current != null && current.y() >= floor && flood.reached(current.key())
                    && (reject == null || !reject.test(current.key()))) {
                int kept = exactCoverage(current.x(), current.y(), current.z(), null);
                double cost = flood.cost(current.key());
                if (kept > 0 && rate(kept, cost) >= best * HYSTERESIS_SHARE) {
                    return new Spot(current.x(), current.y(), current.z(), kept, cost);
                }
            }
            int chosen = -1;
            double chosenScore = 0.0;
            for (int i = 0; i < poolSize; i++) {
                if (y(i) < floor || rate(poolCoverage[i], cost(i)) < best * LOOKAHEAD_BAND) continue;
                double score = twoStopRate(i, floor);
                if (chosen < 0 || score > chosenScore + 1e-12) {
                    chosen = i;
                    chosenScore = score;
                }
            }
            long cell = flood.cell(poolCell[chosen]);
            return new Spot(WalkTerrain.unpackX(cell), WalkTerrain.unpackY(cell), WalkTerrain.unpackZ(cell),
                    poolCoverage[chosen], cost(chosen));
        }

        /**
         * Placement rate over this stop and the best stop after it: the second stop places what this one cannot,
         * after a walk priced at the octile distance between the two (a lower bound of the real route).
         */
        private double twoStopRate(int i, int floor) {
            int covered = poolCoverage[i];
            double ticks = cost(i) * WALK_TICKS + covered * PLACE_TICKS + STOP_TICKS;
            double best = covered / ticks;
            if (covered == sorted.length) return best;
            long a = flood.cell(poolCell[i]);
            long[] mi = poolMask[i];
            for (int j = 0; j < poolSize; j++) {
                if (j == i || y(j) < floor) continue;
                long[] mj = poolMask[j];
                int extra = 0;
                for (int w = 0; w < words; w++) extra += Long.bitCount(mj[w] & ~mi[w]);
                if (extra == 0) continue;
                double walk = octile(a, flood.cell(poolCell[j]));
                double rate = (covered + extra) / (ticks + walk * WALK_TICKS + extra * PLACE_TICKS + STOP_TICKS);
                if (rate > best) best = rate;
            }
            return best;
        }

        private int y(int poolIndex) {
            return WalkTerrain.unpackY(flood.cell(poolCell[poolIndex]));
        }

        private double cost(int poolIndex) {
            return flood.cost(flood.cell(poolCell[poolIndex]));
        }
    }

    /** Octile distance between two feet cells plus the cheapest price of the levels between them. */
    static double octile(long a, long b) {
        double dx = Math.abs(WalkTerrain.unpackX(a) - WalkTerrain.unpackX(b));
        double dz = Math.abs(WalkTerrain.unpackZ(a) - WalkTerrain.unpackZ(b));
        double dy = Math.abs(WalkTerrain.unpackY(a) - WalkTerrain.unpackY(b));
        return Math.max(dx, dz) + (WalkPathfinder.DIAGONAL_COST - 1.0) * Math.min(dx, dz) + dy * WalkPathfinder.STEP_UP_COST;
    }

    /** Box of feet cells that could reach any target at all: targets grown by the reach plus the eye height. */
    private static int[] bounds(long[] targets, double reach) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long t : targets) {
            minX = Math.min(minX, WalkTerrain.unpackX(t));
            maxX = Math.max(maxX, WalkTerrain.unpackX(t));
            minY = Math.min(minY, WalkTerrain.unpackY(t));
            maxY = Math.max(maxY, WalkTerrain.unpackY(t));
            minZ = Math.min(minZ, WalkTerrain.unpackZ(t));
            maxZ = Math.max(maxZ, WalkTerrain.unpackZ(t));
        }
        int grow = (int) Math.ceil(reach) + 1;
        int eye = (int) Math.ceil(ReachModel.EYE_HEIGHT);
        return new int[]{minX - grow, minY - grow - eye, minZ - grow, maxX + grow, maxY + grow, maxZ + grow};
    }

    private static boolean inside(int[] b, int x, int y, int z) {
        return x >= b[0] && y >= b[1] && z >= b[2] && x <= b[3] && y <= b[4] && z <= b[5];
    }
}
