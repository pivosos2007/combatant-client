/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static combatant.client.util.schematic.walk.WalkTerrain.pack;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Moves, costs, hazards and budgets of the walk search on small hand-built worlds. */
class WalkPathfinderGridTest {

    private static final int NODES = 50_000;

    private static long[] route(WalkTerrain world, long start, long goal, int maxDrop) {
        WalkPathfinder.Search search = new WalkPathfinder.Search(world);
        search.startPath(start, goal, maxDrop, NODES, null);
        search.run();
        return search.path();
    }

    private static double cost(WalkTerrain world, long start, long goal, int maxDrop) {
        return WalkPathfinder.flood(world, start, maxDrop, 200.0, NODES).cost(goal);
    }

    private static boolean contains(long[] path, long cell) {
        for (long c : path) if (c == cell) return true;
        return false;
    }

    @Test
    void openFieldCostIsTheOctileDistance() {
        GridWorld world = new GridWorld();
        long[] path = route(world, pack(0, 0, 0), pack(7, 0, 3), 1);
        assertNotNull(path);
        assertEquals(8, path.length, "three diagonals and four straight steps");
        assertEquals(4.0 + 3.0 * Math.sqrt(2.0), cost(world, pack(0, 0, 0), pack(7, 0, 3), 1), 1e-9);
    }

    @Test
    void wallWithAGapIsCrossedAtTheGap() {
        GridWorld world = new GridWorld().fill(3, 0, -6, 3, 1, 6, WalkTerrain.SOLID)
                .set(3, 0, 4, WalkTerrain.OPEN).set(3, 1, 4, WalkTerrain.OPEN)
                .fill(-3, -1, -7, 9, 3, -7, WalkTerrain.UNKNOWN).fill(-3, -1, 7, 9, 3, 7, WalkTerrain.UNKNOWN);
        long[] path = route(world, pack(0, 0, 0), pack(6, 0, 0), 1);
        assertNotNull(path);
        assertTrue(contains(path, pack(3, 0, 4)), Arrays.toString(path));
        // Squeezing diagonally past the gap's jambs is not allowed: the cells before and after it are straight.
        assertTrue(contains(path, pack(2, 0, 4)) && contains(path, pack(4, 0, 4)));
    }

    @Test
    void halfBlockStairsNeedNoJump() {
        // Slab at x = 1, full block at x = 2, a slab on a block at x = 3: three steps of half a block each.
        GridWorld slabs = new GridWorld().set(1, 0, 0, WalkTerrain.STEP).block(2, 0, 0).block(3, 0, 0)
                .set(3, 1, 0, WalkTerrain.STEP);
        // A drop of two is allowed so the column's sides are no pit rim and the sum is the moves alone.
        double walked = cost(slabs, pack(0, 0, 0), pack(3, 2, 0), 2);
        assertEquals(1.0 + WalkPathfinder.STEP_UP_COST + 1.0 + 1.0 + WalkPathfinder.STEP_UP_COST, walked, 1e-9);
        // The same climb on full blocks takes two jumps.
        GridWorld blocks = new GridWorld().block(1, 0, 0).block(2, 0, 0).block(2, 1, 0);
        double jumped = cost(blocks, pack(0, 0, 0), pack(2, 2, 0), 2);
        assertEquals(2.0 * (1.0 + WalkPathfinder.JUMP_COST), jumped, 1e-9);
        assertEquals(0.5, slabs.floorOffset(1, 1, 0) + 1.0, 1e-9, "feet half way into the cell above a slab");
    }

    @Test
    void aJumpFromASlabCannotReachOneAndAHalfBlocks() {
        // A slab in a one-wide corridor, then a two-block step: 1.5 blocks up from the slab top, more than a jump.
        GridWorld world = new GridWorld().fill(-2, 0, 1, 6, 3, 1, WalkTerrain.SOLID).fill(-2, 0, -1, 6, 3, -1, WalkTerrain.SOLID)
                .fill(-3, -1, -1, -3, 4, 1, WalkTerrain.UNKNOWN)
                .set(1, 0, 0, WalkTerrain.STEP).fill(2, 0, 0, 6, 1, 0, WalkTerrain.SOLID);
        assertNull(route(world, pack(0, 0, 0), pack(3, 2, 0), 1));
    }

    @Test
    void laddersAreClimbedAndOnlyThere() {
        // A four-high wall with a ladder on its west face at x = 1.
        GridWorld world = new GridWorld().fill(2, 0, -4, 2, 3, 4, WalkTerrain.SOLID)
                .fill(1, 0, 0, 1, 3, 0, WalkTerrain.CLIMB)
                .fill(-4, -1, -5, 6, 6, -5, WalkTerrain.UNKNOWN).fill(-4, -1, 5, 6, 6, 5, WalkTerrain.UNKNOWN);
        long[] path = route(world, pack(-2, 0, 0), pack(2, 4, 0), 1);
        assertNotNull(path);
        for (int y = 0; y <= 3; y++) assertTrue(contains(path, pack(1, y, 0)), "climbs through y=" + y);
        long[] down = route(world, pack(2, 4, 0), pack(-2, 0, 0), 1);
        assertNotNull(down, "and comes back down the same way");
    }

    @Test
    void neverWalksBesideFireAtBodyHeight() {
        GridWorld world = new GridWorld().set(3, 0, 1, WalkTerrain.DANGER);
        long[] path = route(world, pack(0, 0, 0), pack(6, 0, 0), 1);
        assertNotNull(path);
        for (long cell : path) {
            int x = WalkTerrain.unpackX(cell), z = WalkTerrain.unpackZ(cell);
            assertTrue(Math.abs(x - 3) + Math.abs(z - 1) > 1, "beside the fire at " + x + "," + z);
        }
    }

    @Test
    void keepsAMarginFromLavaOnTheFloor() {
        // Lava pool in the floor at x 3..4, z 0..1; a route along z = -1 brushes it, z = -2 keeps a cell away.
        GridWorld world = new GridWorld().fill(3, -1, 0, 4, -1, 1, WalkTerrain.DANGER);
        double brushing = WalkPathfinder.cellCost(world, 3, 0, -1, 1);
        assertTrue(brushing >= WalkPathfinder.DANGER_NEIGHBOUR_COST, "cost beside lava " + brushing);
        assertEquals(0.0, WalkPathfinder.cellCost(world, 3, 0, -2, 1), 1e-9);
        long[] path = route(world, pack(0, 0, 0), pack(7, 0, 0), 1);
        assertNotNull(path);
        for (long cell : path) assertFalse(world.cell(WalkTerrain.unpackX(cell), -1, WalkTerrain.unpackZ(cell)) == WalkTerrain.DANGER);
    }

    @Test
    void jumpsAOneBlockGapButNeverOneOverLava() {
        // A trench at x = 2, three deep, across the whole corridor.
        GridWorld trench = corridorWithTrench(WalkTerrain.OPEN);
        long[] path = route(trench, pack(0, 0, 0), pack(4, 0, 0), 1);
        assertNotNull(path);
        assertFalse(contains(path, pack(2, 0, 0)) || contains(path, pack(2, -1, 0)));
        assertTrue(contains(path, pack(1, 0, 0)) && contains(path, pack(3, 0, 0)));
        GridWorld lava = corridorWithTrench(WalkTerrain.DANGER);
        assertNull(route(lava, pack(0, 0, 0), pack(4, 0, 0), 1));
    }

    private static GridWorld corridorWithTrench(byte bottom) {
        return new GridWorld().fill(-2, 0, 1, 6, 3, 1, WalkTerrain.SOLID).fill(-2, 0, -1, 6, 3, -1, WalkTerrain.SOLID)
                .fill(-3, -3, -1, -3, 4, 1, WalkTerrain.UNKNOWN).fill(7, -3, -1, 7, 4, 1, WalkTerrain.UNKNOWN)
                .fill(2, -3, 0, 2, -1, 0, WalkTerrain.OPEN).set(2, -3, 0, bottom).set(2, -4, 0, WalkTerrain.SOLID);
    }

    @Test
    void diagonalsMayClipAShallowHoleButNotAPit() {
        GridWorld shallow = new GridWorld().set(1, -1, 0, WalkTerrain.OPEN);
        assertEquals(2, route(shallow, pack(0, 0, 0), pack(1, 0, 1), 1).length);
        GridWorld pit = new GridWorld().fill(1, -3, 0, 1, -1, 0, WalkTerrain.OPEN).set(1, -4, 0, WalkTerrain.UNKNOWN);
        long[] path = route(pit, pack(0, 0, 0), pack(1, 0, 1), 1);
        assertEquals(3, path.length);
        assertEquals(pack(0, 0, 1), path[1]);
    }

    @Test
    void dropsBeyondTheSafeLimitAreNeverTaken() {
        GridWorld cliff = new GridWorld().fill(0, 0, 0, 2, 4, 0, WalkTerrain.SOLID)
                .fill(-3, -1, -3, 8, 8, -1, WalkTerrain.UNKNOWN).fill(-3, -1, 1, 8, 8, 1, WalkTerrain.UNKNOWN);
        // Five blocks down: even a generous maxDrop is clamped to the three that never hurt.
        assertNull(route(cliff, pack(2, 5, 0), pack(4, 0, 0), 10));
    }

    @Test
    void waterAndCobwebsAreWalkedAroundWhenCheap() {
        GridWorld world = new GridWorld().layer(0,
                "...........",
                "...wwwww...",
                "...wcccw...",
                "...wwwww...",
                "...........");
        long[] path = route(world, pack(0, 0, 2), pack(10, 0, 2), 1);
        assertNotNull(path);
        for (long cell : path) {
            byte c = world.cell(WalkTerrain.unpackX(cell), 0, WalkTerrain.unpackZ(cell));
            assertTrue(c == WalkTerrain.OPEN, "went through " + c + " at " + cell);
        }
        // Boxed in, wading is still allowed.
        GridWorld pool = new GridWorld().fill(-5, 0, -1, 5, 2, -1, WalkTerrain.SOLID).fill(-5, 0, 1, 5, 2, 1, WalkTerrain.SOLID)
                .fill(-6, -1, -1, -6, 3, 1, WalkTerrain.UNKNOWN).fill(6, -1, -1, 6, 3, 1, WalkTerrain.UNKNOWN)
                .set(2, 0, 0, WalkTerrain.WATER);
        assertTrue(contains(route(pool, pack(0, 0, 0), pack(4, 0, 0), 1), pack(2, 0, 0)));
    }

    @Test
    void reservedCellsAreAvoidedWhenADetourIsCheap() {
        GridWorld world = new GridWorld();
        CellSet reserved = new CellSet();
        for (int z = -1; z <= 1; z++) reserved.add(pack(3, 0, z));
        WalkPathfinder.Search search = new WalkPathfinder.Search(world);
        search.startPath(pack(0, 0, 0), pack(6, 0, 0), 1, NODES, reserved);
        search.run();
        long[] path = search.path();
        assertNotNull(path);
        for (long cell : path) assertFalse(reserved.contains(cell), "walked through a cell the build fills");
    }

    @Test
    void sameWorldSameRoute() {
        GridWorld world = new GridWorld().fill(4, 0, -8, 4, 1, 8, WalkTerrain.SOLID).block(2, 0, 2).block(6, 0, -2);
        long[] first = route(world, pack(0, 0, 0), pack(9, 0, 1), 1);
        WalkPathfinder.Search reused = new WalkPathfinder.Search(world);
        for (int i = 0; i < 3; i++) {
            reused.startPath(pack(0, 0, 0), pack(9, 0, 1), 1, NODES, null);
            reused.run();
            assertArrayEquals(first, reused.path(), "run " + i);
        }
        // Interrupting the search every few nodes changes nothing either.
        reused.startPath(pack(0, 0, 0), pack(9, 0, 1), 1, NODES, null);
        int slices = 0;
        while (reused.step(System.nanoTime() - 1) == WalkPathfinder.Search.RUNNING) slices++;
        assertTrue(slices > 0);
        assertArrayEquals(first, reused.path());
    }

    @Test
    void nodeBudgetEndsTheSearchAndLeavesAPartialRoute() {
        GridWorld world = new GridWorld();
        WalkPathfinder.Search search = new WalkPathfinder.Search(world);
        search.startPath(pack(0, 0, 0), pack(400, 0, 0), 1, 100, null);
        assertEquals(WalkPathfinder.Search.FAILED, search.run());
        assertEquals(100, search.expanded());
        assertNull(search.path());
        long[] partial = search.partialPath();
        assertNotNull(partial);
        assertEquals(pack(0, 0, 0), partial[0]);
        assertTrue(WalkTerrain.unpackX(partial[partial.length - 1]) > 10, "heads for the goal");
    }

    @Test
    void timeBudgetCapsEveryTick() {
        // A big field of scattered posts, read through the cache as in game.
        GridWorld world = new GridWorld();
        for (int x = -20; x < 260; x += 3) {
            for (int z = -20; z < 260; z += 4) world.block(x, 0, z + (x & 1)).block(x, 1, z + (x & 1));
        }
        long budget = 1_000_000L;
        List<Long> slices = new ArrayList<>();
        for (int warm = 0; warm < 3; warm++) {
            // A cold cache each time, like the first plan in a new area.
            WalkPathfinder.Search search = new WalkPathfinder.Search(new TerrainCache(world));
            search.startPath(pack(0, 0, 0), pack(250, 0, 249), 1, 400_000, null);
            slices.clear();
            int status;
            do {
                long start = System.nanoTime();
                status = search.step(start + budget);
                slices.add(System.nanoTime() - start);
            } while (status == WalkPathfinder.Search.RUNNING);
            assertEquals(WalkPathfinder.Search.FOUND, status);
        }
        long[] sorted = slices.stream().mapToLong(Long::longValue).sorted().toArray();
        long p90 = sorted[(int) (sorted.length * 0.9)];
        // The clock is read every few expansions, so a slice overshoots by at most a few of them.
        assertTrue(p90 < budget * 2, "p90 slice " + p90 / 1000 + " us over " + sorted.length + " slices");
        assertTrue(sorted.length > 1, "the search did need several ticks");
    }
}
