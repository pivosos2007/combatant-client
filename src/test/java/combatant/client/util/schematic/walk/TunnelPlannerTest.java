/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import combatant.client.util.schematic.walk.TunnelPlanner.Hazard;
import combatant.client.util.schematic.walk.TunnelPlanner.Step;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelPlannerTest {

    /** Stone everywhere from y = -1 up to 5, x in -5..10, z in -5..5, so the tunnel is dug through rock. */
    private static GridWorld rock() {
        return new GridWorld().fill(-5, 0, -5, 10, 5, 5, WalkTerrain.SOLID);
    }

    private static Step east(GridWorld w) {
        return TunnelPlanner.plan(w, 0, 0, 0, 1, 0, 1, 4);
    }

    @Test
    void solidRockPlansTwoDigCellsFeetFirst() {
        GridWorld w = rock().set(0, 0, 0, WalkTerrain.OPEN).set(0, 1, 0, WalkTerrain.OPEN);
        Step step = east(w);
        assertEquals(Hazard.NONE, step.hazard());
        assertArrayEquals(new long[]{WalkTerrain.pack(1, 0, 0), WalkTerrain.pack(1, 1, 0)}, step.dig());
        assertEquals(1, step.x());
        assertEquals(0, step.y());
    }

    @Test
    void alreadyOpenColumnNeedsNoDigging() {
        GridWorld w = new GridWorld();
        Step step = east(w);
        assertEquals(Hazard.NONE, step.hazard());
        assertEquals(0, step.dig().length);
    }

    @Test
    void lavaBesideTheNextColumnPauses() {
        GridWorld w = rock().set(1, 0, 1, WalkTerrain.DANGER);
        assertEquals(Hazard.LAVA, east(w).hazard());
    }

    @Test
    void lavaAboveTheCeilingPauses() {
        GridWorld w = rock().set(1, 2, 0, WalkTerrain.DANGER);
        assertEquals(Hazard.LAVA, east(w).hazard());
    }

    @Test
    void lavaInTheNextCellPauses() {
        GridWorld w = rock().set(1, 0, 0, WalkTerrain.DANGER);
        assertEquals(Hazard.LAVA, east(w).hazard());
    }

    @Test
    void lavaSeenFurtherAheadPauses() {
        GridWorld w = rock().set(3, 1, 0, WalkTerrain.DANGER);
        assertEquals(Hazard.LAVA, east(w).hazard());
    }

    @Test
    void lavaOutsideTheLookAheadIsIgnored() {
        GridWorld w = rock().set(8, 0, 0, WalkTerrain.DANGER);
        assertEquals(Hazard.NONE, east(w).hazard());
    }

    @Test
    void lavaFloorBeatsWater() {
        GridWorld w = rock().set(1, -1, 0, WalkTerrain.DANGER).set(1, 2, 0, WalkTerrain.WATER);
        assertEquals(Hazard.LAVA, east(w).hazard());
    }

    @Test
    void waterBesideTheTunnelPausesAsFluid() {
        GridWorld w = rock().set(1, 1, -1, WalkTerrain.WATER);
        assertEquals(Hazard.FLUID, east(w).hazard());
    }

    @Test
    void missingFloorWithGroundBelowIsADrop() {
        GridWorld w = rock().set(1, -1, 0, WalkTerrain.OPEN);
        assertEquals(Hazard.DROP, TunnelPlanner.plan(w, 0, 0, 0, 1, 0, 2, 4).hazard());
    }

    @Test
    void missingFloorWithoutGroundWithinReachIsVoid() {
        GridWorld w = rock();
        for (int y = -1; y >= -4; y--) w.set(1, y, 0, WalkTerrain.OPEN);
        assertEquals(Hazard.VOID, east(w).hazard());
    }

    @Test
    void unknownCellsBelowTheWorldAreVoid() {
        GridWorld w = rock().set(1, -1, 0, WalkTerrain.UNKNOWN);
        assertEquals(Hazard.VOID, east(w).hazard());
    }

    @Test
    void unloadedTunnelCellIsReported() {
        GridWorld w = rock().set(1, 1, 0, WalkTerrain.UNKNOWN);
        assertEquals(Hazard.UNLOADED, east(w).hazard());
    }

    @Test
    void worstHazardWins() {
        GridWorld w = rock().set(1, 1, 0, WalkTerrain.UNKNOWN).set(2, 0, 0, WalkTerrain.WATER);
        assertEquals(Hazard.FLUID, east(w).hazard());
    }

    @Test
    void directionsFollowTheCardinalIndex() {
        assertEquals(0, TunnelPlanner.cardinalFromYaw(0.0f));
        assertEquals(1, TunnelPlanner.cardinalFromYaw(90.0f));
        assertEquals(2, TunnelPlanner.cardinalFromYaw(180.0f));
        assertEquals(3, TunnelPlanner.cardinalFromYaw(-90.0f));
        assertEquals(1, TunnelPlanner.dirZ(0));
        assertEquals(-1, TunnelPlanner.dirX(1));
        assertEquals(-1, TunnelPlanner.dirZ(2));
        assertEquals(1, TunnelPlanner.dirX(3));
    }

    @Test
    void southTunnelUsesTheZAxis() {
        GridWorld w = rock();
        Step step = TunnelPlanner.plan(w, 0, 0, 0, 0, 1, 1, 4);
        assertTrue(step.safe());
        assertArrayEquals(new long[]{WalkTerrain.pack(0, 0, 1), WalkTerrain.pack(0, 1, 1)}, step.dig());
    }
}
