/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import org.junit.jupiter.api.Test;

import static combatant.client.util.schematic.walk.WalkTerrain.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathSmootherTest {

    private static long[] raw(WalkTerrain world, long start, long goal) {
        WalkPathfinder.Search search = new WalkPathfinder.Search(world);
        search.startPath(start, goal, 1, 50_000, null);
        search.run();
        return search.path();
    }

    /** Every waypoint is a raw cell, in the raw order, first and last kept. */
    private static void assertSubsequence(long[] raw, long[] smooth) {
        assertEquals(raw[0], smooth[0]);
        assertEquals(raw[raw.length - 1], smooth[smooth.length - 1]);
        int r = 0;
        for (long w : smooth) {
            while (r < raw.length && raw[r] != w) r++;
            assertTrue(r < raw.length, "waypoint not on the raw route in order");
        }
    }

    /** Each smoothed leg is either one raw move or a straight line the hitbox can sweep. */
    private static void assertCollisionFree(WalkTerrain world, long[] raw, long[] smooth) {
        for (int i = 1; i < smooth.length; i++) {
            long a = smooth[i - 1], b = smooth[i];
            int ia = indexOf(raw, a), ib = indexOf(raw, b);
            if (ib == ia + 1) continue;
            int y = WalkTerrain.unpackY(a);
            assertEquals(y, WalkTerrain.unpackY(b), "shortcuts stay on one level");
            assertTrue(PathSmoother.straightWalkable(world, WalkTerrain.unpackX(a) + 0.5, WalkTerrain.unpackZ(a) + 0.5,
                    WalkTerrain.unpackX(b) + 0.5, WalkTerrain.unpackZ(b) + 0.5, y,
                    world.floorOffset(WalkTerrain.unpackX(a), y, WalkTerrain.unpackZ(a)), 0.3, 1));
        }
    }

    private static int indexOf(long[] path, long cell) {
        for (int i = 0; i < path.length; i++) if (path[i] == cell) return i;
        return -1;
    }

    @Test
    void openFieldBecomesOneStraightLine() {
        GridWorld world = new GridWorld();
        long[] raw = raw(world, pack(0, 0, 0), pack(9, 0, 4));
        long[] smooth = PathSmoother.smooth(world, raw, 1);
        assertEquals(2, smooth.length);
        assertTrue(PathSmoother.length(smooth) <= PathSmoother.length(raw) + 1e-9);
    }

    @Test
    void cornersAroundAWallStayClearOfIt() {
        GridWorld world = new GridWorld().fill(4, 0, -6, 4, 1, 3, WalkTerrain.SOLID).fill(8, 0, 0, 8, 1, 9, WalkTerrain.SOLID)
                .block(2, 0, 5).block(2, 1, 5);
        long[] raw = raw(world, pack(0, 0, 0), pack(12, 0, 2));
        assertNotNull(raw);
        long[] smooth = PathSmoother.smooth(world, raw, 1);
        assertTrue(smooth.length < raw.length, "something was straightened");
        assertSubsequence(raw, smooth);
        assertCollisionFree(world, raw, smooth);
        assertTrue(PathSmoother.length(smooth) <= PathSmoother.length(raw) + 1e-9);
    }

    @Test
    void levelChangesAreKept() {
        GridWorld world = new GridWorld().fill(5, 0, -5, 12, 0, 5, WalkTerrain.SOLID);
        long[] raw = raw(world, pack(0, 0, 0), pack(10, 1, 3));
        long[] smooth = PathSmoother.smooth(world, raw, 1);
        assertSubsequence(raw, smooth);
        assertCollisionFree(world, raw, smooth);
        // The jump's take-off and landing cells both remain.
        int jump = -1;
        for (int i = 1; i < raw.length; i++) if (WalkTerrain.unpackY(raw[i]) != WalkTerrain.unpackY(raw[i - 1])) jump = i;
        assertTrue(indexOf(smooth, raw[jump - 1]) >= 0 && indexOf(smooth, raw[jump]) >= 0);
    }

    @Test
    void neverShortcutsAlongAPitRimOrThroughWater() {
        GridWorld world = new GridWorld().fill(3, -3, 1, 6, -1, 2, WalkTerrain.OPEN).fill(3, 0, -2, 6, 0, -1, WalkTerrain.WATER);
        long[] raw = raw(world, pack(0, 0, 0), pack(9, 0, 0));
        long[] smooth = PathSmoother.smooth(world, raw, 1);
        assertSubsequence(raw, smooth);
        assertCollisionFree(world, raw, smooth);
        assertTrue(PathSmoother.length(smooth) <= PathSmoother.length(raw) + 1e-9);
    }
}
