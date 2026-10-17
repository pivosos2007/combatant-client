/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.scaffold;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ScaffoldMovementPredictionTest {
    /** Collision box of the stone block at z..z+1 grown by the player's half width, as the prediction builds them. */
    private static AABB inflatedBlock(int z) {
        return new AABB(0.0 - 0.3, -52.0, z - 0.3, 1.0 + 0.3, -49.45, z + 1.0 + 0.3);
    }

    @Test
    void theFallOffPointIsWhereTheChainOfSupportBlocksEndsNotWhereItStarts() {
        // two blocks (z=0 and z=1) under a player walking +z from z=0.5: the hitbox leaves the last one at z=2.3
        List<AABB> boxes = new ArrayList<>(List.of(inflatedBlock(0), inflatedBlock(1)));
        Vec3 from = new Vec3(0.5, -50.1, 0.5);

        Vec3 edge = ScaffoldMovementPrediction.findEdgeExit(boxes, from, from.add(0.0, 0.0, 3.0));

        assertNotNull(edge);
        assertEquals(2.3, edge.z, 1.0E-9);
        assertEquals(0.5, edge.x, 1.0E-9);
    }

    @Test
    void aLongerChainMovesTheFallOffPointWithIt() {
        List<AABB> boxes = new ArrayList<>(List.of(inflatedBlock(0), inflatedBlock(1), inflatedBlock(2), inflatedBlock(3)));
        Vec3 from = new Vec3(0.5, -50.1, 2.2);

        Vec3 edge = ScaffoldMovementPrediction.findEdgeExit(boxes, from, from.add(0.0, 0.0, 3.0));

        assertNotNull(edge);
        assertEquals(4.3, edge.z, 1.0E-9);
    }

    @Test
    void supportThatReachesTheEndOfTheLineMeansThereIsNoEdgeToPredict() {
        List<AABB> boxes = new ArrayList<>(List.of(inflatedBlock(0), inflatedBlock(1), inflatedBlock(2), inflatedBlock(3)));
        Vec3 from = new Vec3(0.5, -50.1, 0.5);

        assertNull(ScaffoldMovementPrediction.findEdgeExit(boxes, from, from.add(0.0, 0.0, 3.0)));
    }
}
