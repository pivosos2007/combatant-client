/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.Arrays;

/**
 * Straightens grid routes: on each level the route keeps only the cells where it has to turn, joined by straight
 * lines that the player's hitbox can sweep without touching a wall, leaving the floor or crossing a costly cell.
 *
 * <p>The straight line between two cell centres is never longer than the grid route between them, so the smoothed
 * route is never longer than the raw one. Level changes (jumps, drops, steps, ladders) stay as they are: both cells
 * of every such move remain waypoints.</p>
 */
public final class PathSmoother {

    /** Half the player's 0.6 width plus a little slack for steering error. */
    public static final double BODY_HALF_WIDTH = 0.35;
    /** Distance between two hitbox samples along a line; smaller than any wall the sweep must notice. */
    private static final double SAMPLE_STEP = 0.1;
    /** A shortcut spans at most this many route cells; longer ones rarely help and cost checks. */
    private static final int MAX_SPAN = 16;

    private PathSmoother() {
    }

    /**
     * @param path   feet cells from start to goal, as from {@link WalkPathfinder}
     * @param maxDrop the drop the route was planned with; a line may not pass the rim of a deeper pit
     * @return the waypoints: a subsequence of {@code path} that keeps its first and last cell
     */
    public static long[] smooth(WalkTerrain terrain, long[] path, int maxDrop) {
        if (path == null || path.length <= 2) return path;
        long[] out = new long[path.length];
        int count = 0;
        out[count++] = path[0];
        int i = 0;
        while (i < path.length - 1) {
            int next = i + 1;
            int y = WalkTerrain.unpackY(path[i]);
            for (int j = Math.min(path.length - 1, i + MAX_SPAN); j > i + 1; j--) {
                if (sameLevel(path, i, j, y) && walkable(terrain, path, i, j, y, maxDrop)) {
                    next = j;
                    break;
                }
            }
            out[count++] = path[next];
            i = next;
        }
        return Arrays.copyOf(out, count);
    }

    private static boolean sameLevel(long[] path, int from, int to, int y) {
        for (int k = from + 1; k <= to; k++) {
            if (WalkTerrain.unpackY(path[k]) != y) return false;
        }
        return true;
    }

    private static boolean walkable(WalkTerrain terrain, long[] path, int from, int to, int y, int maxDrop) {
        double x0 = WalkTerrain.unpackX(path[from]) + 0.5, z0 = WalkTerrain.unpackZ(path[from]) + 0.5;
        double x1 = WalkTerrain.unpackX(path[to]) + 0.5, z1 = WalkTerrain.unpackZ(path[to]) + 0.5;
        double floor = terrain.floorOffset(WalkTerrain.unpackX(path[from]), y, WalkTerrain.unpackZ(path[from]));
        return straightWalkable(terrain, x0, z0, x1, z1, y, floor, BODY_HALF_WIDTH, maxDrop);
    }

    /**
     * Can a body of half width {@code halfWidth} walk straight from {@code (x0, z0)} to {@code (x1, z1)} with its
     * feet in level {@code y}? Every cell the hitbox touches on the way must be standable at that level, on a floor
     * of about the same height, free of any extra cost and away from hazards.
     */
    public static boolean straightWalkable(WalkTerrain terrain, double x0, double z0, double x1, double z1, int y,
                                           double floor, double halfWidth, int maxDrop) {
        double dx = x1 - x0, dz = z1 - z0;
        double length = Math.hypot(dx, dz);
        int steps = Math.max(1, (int) Math.ceil(length / SAMPLE_STEP));
        int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = x0 + dx * t, z = z0 + dz * t;
            int minX = floor(x - halfWidth), maxX = floor(x + halfWidth);
            int minZ = floor(z - halfWidth), maxZ = floor(z + halfWidth);
            // Consecutive samples mostly cover the same cells; only re-check when the box moved into new ones.
            if (minX == lastX && minZ == lastZ && maxX - minX == 1 && maxZ - minZ == 1) continue;
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    if (!flatCell(terrain, cx, y, cz, floor, maxDrop)) return false;
                }
            }
            if (maxX - minX == 1 && maxZ - minZ == 1) {
                lastX = minX;
                lastZ = minZ;
            } else {
                lastX = Integer.MIN_VALUE;
            }
        }
        return true;
    }

    private static boolean flatCell(WalkTerrain terrain, int x, int y, int z, double floor, int maxDrop) {
        byte feet = terrain.cell(x, y, z);
        if (!terrain.standable(x, y, z) || feet == WalkTerrain.CLIMB || feet == WalkTerrain.NARROW
                || terrain.cell(x, y + 1, z) == WalkTerrain.NARROW) return false;
        if (Math.abs(terrain.floorOffset(x, y, z) - floor) > 0.01) return false;
        return WalkPathfinder.cellCost(terrain, x, y, z, maxDrop) == 0.0;
    }

    /** Length of the polyline through the cell centres, vertical distance included. */
    public static double length(long[] path) {
        double total = 0.0;
        for (int i = 1; i < path.length; i++) {
            double dx = WalkTerrain.unpackX(path[i]) - WalkTerrain.unpackX(path[i - 1]);
            double dy = WalkTerrain.unpackY(path[i]) - WalkTerrain.unpackY(path[i - 1]);
            double dz = WalkTerrain.unpackZ(path[i]) - WalkTerrain.unpackZ(path[i - 1]);
            total += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return total;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
