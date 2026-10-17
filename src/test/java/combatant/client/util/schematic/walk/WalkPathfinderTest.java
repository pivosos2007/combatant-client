/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static combatant.client.util.schematic.walk.WalkTerrain.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkPathfinderTest {

    private static final int NODES = 20_000;

    @Test
    void packRoundTripsNegativeCoordinates() {
        long key = pack(-30_000_000 + 7, -64, 29_999_999);
        assertEquals(-30_000_000 + 7, WalkTerrain.unpackX(key));
        assertEquals(-64, WalkTerrain.unpackY(key));
        assertEquals(29_999_999, WalkTerrain.unpackZ(key));
    }

    @Test
    void walksStraightAcrossFlatGround() {
        List<Long> path = WalkPathfinder.find(new GridWorld(), pack(0, 0, 0), pack(5, 0, 0), 1, NODES);
        assertNotNull(path);
        assertEquals(6, path.size());
        assertEquals(pack(5, 0, 0), path.get(path.size() - 1));
    }

    @Test
    void takesDiagonalsOnlyWhenBothCornersAreWalkable() {
        GridWorld open = new GridWorld();
        assertEquals(4, WalkPathfinder.find(open, pack(0, 0, 0), pack(3, 0, 3), 1, NODES).size());

        // A wall block on one corner forbids cutting it: the route has to go round.
        GridWorld corner = new GridWorld().block(1, 0, 0).block(1, 1, 0);
        List<Long> path = WalkPathfinder.find(corner, pack(0, 0, 0), pack(1, 0, 1), 1, NODES);
        assertNotNull(path);
        assertEquals(3, path.size());
        assertEquals(pack(0, 0, 1), path.get(1));
    }

    @Test
    void stepsUpOneBlockButNotTwo() {
        GridWorld step = new GridWorld().block(2, 0, 0);
        List<Long> up = WalkPathfinder.find(step, pack(0, 0, 0), pack(2, 1, 0), 1, NODES);
        assertNotNull(up);
        assertTrue(up.contains(pack(1, 0, 0)));

        // A two-block wall around a one-wide corridor: nothing to climb, no way through.
        GridWorld wall = new GridWorld().fill(-5, -1, 1, 5, 1, 1, WalkTerrain.SOLID).fill(-5, -1, -1, 5, 1, -1, WalkTerrain.SOLID)
                .block(2, 0, 0).block(2, 1, 0)
                .fill(-6, -1, -1, -6, 3, 1, WalkTerrain.UNKNOWN).fill(6, -1, -1, 6, 3, 1, WalkTerrain.UNKNOWN);
        assertNull(WalkPathfinder.find(wall, pack(0, 0, 0), pack(4, 0, 0), 1, NODES));
    }

    @Test
    void jumpNeedsHeadRoomAtTheTakeOff() {
        // A ceiling at y = 2 everywhere except above the step itself: no cell to jump from has head room.
        GridWorld lowCeiling = new GridWorld().fill(-5, 2, -5, 5, 2, 5, WalkTerrain.SOLID).block(1, 0, 0)
                .set(1, 2, 0, WalkTerrain.OPEN);
        assertNull(WalkPathfinder.find(lowCeiling, pack(0, 0, 0), pack(1, 1, 0), 1, 2000));
        GridWorld clear = new GridWorld().block(1, 0, 0);
        assertNotNull(WalkPathfinder.find(clear, pack(0, 0, 0), pack(1, 1, 0), 1, 2000));
    }

    @Test
    void dropsAreLimitedByMaxDrop() {
        // Raised platform x 0..2 at feet y = 3, ground beyond it at feet y = 0.
        GridWorld world = new GridWorld().fill(0, 0, 0, 2, 2, 0, WalkTerrain.SOLID)
                .fill(-3, -1, -3, 8, 5, -1, WalkTerrain.UNKNOWN).fill(-3, -1, 1, 8, 5, 1, WalkTerrain.UNKNOWN);
        assertNull(WalkPathfinder.find(world, pack(2, 3, 0), pack(4, 0, 0), 1, NODES));
        List<Long> path = WalkPathfinder.find(world, pack(2, 3, 0), pack(4, 0, 0), 3, NODES);
        assertNotNull(path);
        assertEquals(pack(3, 0, 0), path.get(1));
    }

    @Test
    void neverStepsOnOrIntoDanger() {
        // A lava strip across the corridor with a one-block gap at z = 2.
        GridWorld world = new GridWorld().layer(-1,
                "#######",
                "###~###",
                "###~###",
                "#######",
                "###~###");
        world.set(3, -1, 3, WalkTerrain.SOLID);
        List<Long> path = WalkPathfinder.find(world, pack(0, 0, 1), pack(6, 0, 1), 1, NODES);
        assertNotNull(path);
        for (long cell : path) {
            assertTrue(world.standable(WalkTerrain.unpackX(cell), WalkTerrain.unpackY(cell), WalkTerrain.unpackZ(cell)));
            assertFalse(world.cell(WalkTerrain.unpackX(cell), WalkTerrain.unpackY(cell) - 1, WalkTerrain.unpackZ(cell)) == WalkTerrain.DANGER);
        }
    }

    @Test
    void floodReportsCostsAndRoutes() {
        WalkPathfinder.Flood flood = WalkPathfinder.flood(new GridWorld(), pack(0, 0, 0), 1, 3.0, NODES);
        assertTrue(flood.reached(pack(3, 0, 0)));
        assertFalse(flood.reached(pack(4, 0, 0)));
        assertEquals(3.0, flood.cost(pack(3, 0, 0)), 1e-9);
        List<Long> route = flood.pathTo(pack(2, 0, 2));
        assertNotNull(route);
        assertEquals(3, route.size());
        assertNull(flood.pathTo(pack(9, 0, 9)));
    }

    @Test
    void unloadedTerrainIsAWall() {
        GridWorld world = new GridWorld().fill(3, -1, -3, 3, 3, 3, WalkTerrain.UNKNOWN)
                .fill(-3, -1, -3, 8, 3, -3, WalkTerrain.UNKNOWN).fill(-3, -1, 3, 8, 3, 3, WalkTerrain.UNKNOWN)
                .fill(-3, -1, -3, -3, 3, 3, WalkTerrain.UNKNOWN);
        assertNull(WalkPathfinder.find(world, pack(0, 0, 0), pack(5, 0, 0), 1, 500));
    }
}
