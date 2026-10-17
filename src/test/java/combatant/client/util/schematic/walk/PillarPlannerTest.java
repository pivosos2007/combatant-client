/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import org.junit.jupiter.api.Test;

import java.util.function.LongPredicate;

import static combatant.client.util.schematic.walk.WalkTerrain.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PillarPlannerTest {

    private static final double REACH = 4.2;

    /** A one-wide pole of {@code height} blocks at the origin. */
    private static GridWorld pole(int height) {
        GridWorld world = new GridWorld();
        for (int y = 0; y < height; y++) world.block(0, y, 0);
        return world;
    }

    @Test
    void buildsAColumnBesideAPoleToReachItsTop() {
        GridWorld world = pole(6);
        long[] targets = {pack(0, 6, 0)};
        LongPredicate footprint = key -> WalkTerrain.unpackX(key) == 0 && WalkTerrain.unpackZ(key) == 0;
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(4, 0, 0), 1, 30.0, 20_000);
        PillarPlanner.Pillar pillar = PillarPlanner.plan(world, flood, targets, 1, REACH, 8, footprint, null);
        assertNotNull(pillar);
        assertEquals(0, pillar.y());
        assertTrue(pillar.x() != 0 || pillar.z() != 0, "never inside the footprint: " + pillar);
        // The top has to see the pole's top face, so the eyes end up above y = 6.
        assertTrue(ReachModel.eyeY(pillar.topY()) > 6.0, "top " + pillar.topY());
        WalkTerrain withColumn = PillarPlanner.column(world, pillar.x(), pillar.y(), pillar.z(), pillar.height());
        assertTrue(ReachModel.placeable(withColumn, pillar.x() + 0.5, ReachModel.eyeY(pillar.topY()), pillar.z() + 0.5,
                0, 6, 0, REACH));
        // Eyes just above the top face are enough from anywhere within reach: no taller than needed.
        assertEquals(5, pillar.height(), "pillar " + pillar);
    }

    @Test
    void picksTheShortestColumnThatWorks() {
        GridWorld world = pole(6);
        long[] targets = {pack(0, 6, 0)};
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(1, 0, 0), 1, 30.0, 20_000);
        PillarPlanner.Pillar pillar = PillarPlanner.plan(world, flood, targets, 1, REACH, 10,
                key -> WalkTerrain.unpackX(key) == 0 && WalkTerrain.unpackZ(key) == 0, null);
        assertNotNull(pillar);
        PillarPlanner.Pillar lower = new PillarPlanner.Pillar(pillar.x(), pillar.y(), pillar.z(), pillar.height() - 1, 0, 0);
        WalkTerrain shorter = PillarPlanner.column(world, lower.x(), lower.y(), lower.z(), lower.height());
        assertEquals(0, StandingSpotChooser.coverage(shorter, lower.x(), lower.topY(), lower.z(), targets, 1,
                StandingSpotChooser.Mode.PLACE, REACH));
    }

    @Test
    void noColumnUnderALowCeiling() {
        GridWorld world = pole(6).fill(-6, 3, -6, 6, 3, 6, WalkTerrain.SOLID);
        world.set(0, 3, 0, WalkTerrain.SOLID);
        long[] targets = {pack(0, 6, 0)};
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(2, 0, 0), 1, 30.0, 20_000);
        assertNull(PillarPlanner.plan(world, flood, targets, 1, REACH, 8, null, null));
    }

    @Test
    void limitsTheHeight() {
        GridWorld world = pole(12);
        long[] targets = {pack(0, 12, 0)};
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(2, 0, 0), 1, 30.0, 20_000);
        assertNull(PillarPlanner.plan(world, flood, targets, 1, REACH, 4, null, null));
        assertNotNull(PillarPlanner.plan(world, flood, targets, 1, REACH, 12, null, null));
    }
}
