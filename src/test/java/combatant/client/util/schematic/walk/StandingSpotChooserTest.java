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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingSpotChooserTest {

    private static final double REACH = 4.2;

    /** Feet-level cells of a row x0..x1 at z, on the floor. */
    private static long[] row(int x0, int x1, int y, int z) {
        long[] out = new long[x1 - x0 + 1];
        for (int x = x0; x <= x1; x++) out[x - x0] = pack(x, y, z);
        return out;
    }

    @Test
    void reachModelSeesTopFacesOnlyFromAbove() {
        GridWorld world = new GridWorld().block(0, 0, 0).block(0, 1, 0).block(0, 2, 0);
        // Target on top of a 3-high pole: its only support face is the pole's top at y = 3.
        assertFalse(ReachModel.placeable(world, 1.5, ReachModel.eyeY(0), 0.5, 0, 3, 0, REACH));
        assertTrue(ReachModel.placeable(world, 1.5, ReachModel.eyeY(3), 0.5, 0, 3, 0, REACH));
        // Out of reach, even with a clear view.
        assertFalse(ReachModel.placeable(world, 6.5, ReachModel.eyeY(3), 0.5, 0, 3, 0, REACH));
        // A target with nothing to lean on cannot be placed at all.
        assertFalse(ReachModel.placeable(world, 1.5, ReachModel.eyeY(5), 0.5, 3, 5, 3, REACH));
    }

    @Test
    void reachModelNeedsALineOfSight() {
        // Floor-level target at x = 4 behind a 2-high wall at x = 2: the floor top face is hidden from x = 0.
        GridWorld world = new GridWorld().block(2, 0, 0).block(2, 1, 0);
        assertFalse(ReachModel.placeable(world, 0.5, ReachModel.eyeY(0), 0.5, 3, 0, 0, REACH));
        assertTrue(ReachModel.placeable(world, 0.5, ReachModel.eyeY(0), 0.5, 1, 0, 0, REACH));
    }

    @Test
    void breakingTheBlockUnderFootIsInReach() {
        GridWorld world = new GridWorld().block(0, 0, 0).block(0, 1, 0);
        assertTrue(ReachModel.breakable(world, 0.5, ReachModel.eyeY(2), 0.5, 0, 1, 0, REACH));
        // The lower one is covered on top by the upper block and on the sides only reachable from the ground.
        assertTrue(ReachModel.breakable(world, 1.5, ReachModel.eyeY(0), 0.5, 0, 0, 0, REACH));
        assertFalse(ReachModel.breakable(world, 0.5, ReachModel.eyeY(9), 0.5, 0, 0, 0, REACH));
    }

    @Test
    void walksNextToAFarRowInsteadOfStayingPut() {
        GridWorld world = new GridWorld();
        long[] targets = row(12, 20, 0, 0);
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(0, 0, 0), 1, 40.0, 20_000);
        StandingSpotChooser.Spot spot = StandingSpotChooser.choose(world, flood, targets, targets.length,
                StandingSpotChooser.Mode.PLACE, REACH, null, null);
        assertNotNull(spot);
        assertTrue(spot.coverage() > 0);
        assertTrue(spot.x() >= 8 && spot.x() <= 20, "spot " + spot);
        // Never inside a target cell.
        for (long target : targets) assertFalse(target == spot.key());
    }

    @Test
    void picksTheSpotWithTheBestPlacementRate() {
        GridWorld world = new GridWorld();
        long[] targets = row(4, 10, 0, 0);
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(0, 0, 3), 1, 40.0, 20_000);
        StandingSpotChooser.Spot spot = StandingSpotChooser.choose(world, flood, targets, targets.length,
                StandingSpotChooser.Mode.PLACE, REACH, null, null);
        assertNotNull(spot);
        double chosen = StandingSpotChooser.rate(spot.coverage(), spot.cost());
        for (long cell : flood.cells()) {
            int coverage = StandingSpotChooser.coverage(world, WalkTerrain.unpackX(cell), WalkTerrain.unpackY(cell),
                    WalkTerrain.unpackZ(cell), targets, targets.length, StandingSpotChooser.Mode.PLACE, REACH);
            assertTrue(StandingSpotChooser.rate(coverage, flood.cost(cell)) <= chosen + 1e-12, "better spot " + cell);
        }
        // A nearer spot that reaches nearly as much beats walking to the one that reaches everything.
        assertTrue(spot.cost() < 8.0, "spot " + spot);
    }

    @Test
    void keepsThePreviousGoalWhileItStillCoversAboutAsMuch() {
        GridWorld world = new GridWorld();
        long[] targets = row(0, 8, 0, 0);
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(4, 0, 6), 1, 40.0, 20_000);
        StandingSpotChooser.Spot fresh = StandingSpotChooser.choose(world, flood, targets, targets.length,
                StandingSpotChooser.Mode.PLACE, REACH, null, null);
        assertNotNull(fresh);
        StandingSpotChooser.Spot previous = new StandingSpotChooser.Spot(fresh.x() + 1, fresh.y(), fresh.z(), 0, 0);
        StandingSpotChooser.Spot kept = StandingSpotChooser.choose(world, flood, targets, targets.length,
                StandingSpotChooser.Mode.PLACE, REACH, previous, null);
        assertEquals(previous.key(), kept.key());

        // A rejected goal is dropped even though it would still qualify.
        StandingSpotChooser.Spot replaced = StandingSpotChooser.choose(world, flood, targets, targets.length,
                StandingSpotChooser.Mode.PLACE, REACH, previous, key -> key == previous.key());
        assertFalse(replaced.key() == previous.key());
    }

    @Test
    void returnsNothingWhenEveryTargetIsAboveReach() {
        GridWorld world = new GridWorld().block(0, 0, 0).block(0, 1, 0).block(0, 2, 0).block(0, 3, 0)
                .block(0, 4, 0).block(0, 5, 0);
        long[] targets = {pack(0, 6, 0)};
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(3, 0, 0), 1, 40.0, 20_000);
        assertNull(StandingSpotChooser.choose(world, flood, targets, 1, StandingSpotChooser.Mode.PLACE, REACH, null, null));
    }

    @Test
    void climbsOntoLowerLayersToReachTheMiddleOfAWideLayer() {
        // A 9x9 slab two blocks thick: the middle of the third layer is out of reach from the ground but in reach
        // from the slab top, which is one jump onto a one-block step at its edge.
        GridWorld world = new GridWorld().fill(0, 0, 0, 8, 1, 8, WalkTerrain.SOLID).block(-1, 0, 4);
        long[] targets = {pack(4, 2, 4)};
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(-6, 0, 4), 1, 60.0, 20_000);
        StandingSpotChooser.Spot spot = StandingSpotChooser.choose(world, flood, targets, 1,
                StandingSpotChooser.Mode.PLACE, REACH, null, null);
        assertNotNull(spot);
        assertEquals(2, spot.y());
        assertNotNull(flood.pathTo(spot.key()));
    }

    @Test
    void staysOnTheBuildInsteadOfSteppingDownToItsEdge() {
        // A finished 5x5 layer at y = 0 with the next layer's edge cell (4, 1, 2) missing: the ground beside it
        // reaches the cell too, but stepping down would strand the player under the rest of the build.
        GridWorld world = new GridWorld().fill(0, 0, 0, 4, 0, 4, WalkTerrain.SOLID);
        long[] targets = {pack(4, 1, 2)};
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(2, 1, 2), 1, 40.0, 20_000);
        StandingSpotChooser.Spot spot = StandingSpotChooser.choose(world, flood, targets, 1,
                StandingSpotChooser.Mode.PLACE, REACH, null, null);
        assertNotNull(spot);
        assertEquals(1, spot.y(), "spot " + spot);
    }

    @Test
    void scoringInSlicesGivesTheSameSpotAndPrunesMostCells() {
        GridWorld world = new GridWorld();
        long[] targets = new long[22];
        for (int x = 4; x <= 14; x++) {
            targets[x - 4] = pack(x, 0, 0);
            targets[x - 4 + 11] = pack(x, 0, 1);
        }
        WalkPathfinder.Flood flood = WalkPathfinder.flood(world, pack(0, 0, 5), 1, 40.0, 20_000);
        StandingSpotChooser.Spot whole = StandingSpotChooser.choose(world, flood, targets, targets.length,
                StandingSpotChooser.Mode.PLACE, REACH, null, null);
        assertNotNull(whole);
        StandingSpotChooser.Scorer scorer = new StandingSpotChooser.Scorer(world, flood, targets, targets.length,
                StandingSpotChooser.Mode.PLACE, REACH, null);
        int slices = 0;
        while (!scorer.step(System.nanoTime() - 1)) slices++;
        assertTrue(slices > 0, "a deadline in the past still lets every call make progress");
        assertEquals(whole, scorer.result(null));
        // The cheap bound rules most cells out before any line of sight is traced.
        assertTrue(scorer.evaluated() * 4 < flood.size(), scorer.evaluated() + " of " + flood.size());
        double best = 0.0;
        for (int i = 0; i < flood.size(); i++) {
            long cell = flood.cell(i);
            int coverage = StandingSpotChooser.coverage(world, WalkTerrain.unpackX(cell), WalkTerrain.unpackY(cell),
                    WalkTerrain.unpackZ(cell), targets, targets.length, StandingSpotChooser.Mode.PLACE, REACH);
            best = Math.max(best, StandingSpotChooser.rate(coverage, flood.cost(cell)));
        }
        assertTrue(StandingSpotChooser.rate(whole.coverage(), whole.cost()) >= best * StandingSpotChooser.LOOKAHEAD_BAND - 1e-12);
    }
}
