/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.function.LongPredicate;

/**
 * Which way to extend a temporary bridge when no standing spot, walkable or pillared, reaches the wanted cells.
 *
 * <p>A bridge grows one block at a time from the floor the player stands on, level with it, towards the nearest
 * target. Only targets within vertical reach of the bridge's height count (taller gaps are the pillar's job), and
 * a step has to bring the player clearly closer sideways, so a bridge never wanders. Free of game types like the
 * rest of the walker so it can be tested on small hand-drawn worlds.</p>
 */
public final class BridgePlanner {

    /** Horizontal step of direction {@code i}: east, west, south, north. */
    public static final int[] DX = {1, -1, 0, 0};
    public static final int[] DZ = {0, 0, 1, -1};

    /** Sideways progress a step must make; a diagonal approach gains less than a full block per step. */
    static final double MIN_GAIN = 0.2;
    /** A target this close sideways is straight above or below: no bridge helps. */
    static final double ARRIVED = 1.0;

    private BridgePlanner() {
    }

    /**
     * @param x         feet cell the player stands in
     * @param forbidden cells no support block may occupy (the build footprint), or {@code null}
     * @param reject    floor cells that were tried and failed, or {@code null}
     * @return the direction index into {@link #DX}/{@link #DZ} of the next bridge block, or {@code -1}
     */
    public static int choose(WalkTerrain terrain, int x, int y, int z, long[] targets, int targetCount, double reach,
                             LongPredicate forbidden, LongPredicate reject) {
        if (!terrain.solid(x, y - 1, z)) return -1;
        double vertical = reach + 1.0;
        double current = Double.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < targetCount; i++) {
            long t = targets[i];
            if (Math.abs(WalkTerrain.unpackY(t) - y) > vertical) continue;
            any = true;
            current = Math.min(current, sideways(t, x, z));
        }
        if (!any || current <= ARRIVED) return -1;
        int best = -1;
        double bestDistance = current - MIN_GAIN;
        for (int d = 0; d < DX.length; d++) {
            int cx = x + DX[d], cz = z + DZ[d];
            long floor = WalkTerrain.pack(cx, y - 1, cz);
            if (!terrain.open(cx, y - 1, cz) || !terrain.passable(cx, y, cz) || !terrain.passable(cx, y + 1, cz)) continue;
            if (forbidden != null && forbidden.test(floor) || reject != null && reject.test(floor)) continue;
            double nearest = Double.MAX_VALUE;
            for (int i = 0; i < targetCount; i++) {
                long t = targets[i];
                if (Math.abs(WalkTerrain.unpackY(t) - y) > vertical) continue;
                nearest = Math.min(nearest, sideways(t, cx, cz));
            }
            if (nearest < bestDistance) {
                bestDistance = nearest;
                best = d;
            }
        }
        return best;
    }

    private static double sideways(long target, int x, int z) {
        return Math.hypot(WalkTerrain.unpackX(target) - x, WalkTerrain.unpackZ(target) - z);
    }
}
