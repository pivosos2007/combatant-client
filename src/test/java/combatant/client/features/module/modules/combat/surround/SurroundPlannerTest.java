/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.surround;

import combatant.client.features.module.modules.combat.surround.SurroundPlanner.Pos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurroundPlannerTest {

    private static final int GROUND_Y = 63;

    /** Flat ground up to y=63 with open air above; {@code solid} adds blocks, {@code holes} removes ground. */
    private static final class World implements Predicate<Pos> {
        private final Set<Pos> solid = new HashSet<>();
        private final Set<Pos> holes = new HashSet<>();
        private boolean voidWorld;

        @Override
        public boolean test(Pos pos) {
            if (solid.contains(pos)) return false;
            return voidWorld || pos.y() > GROUND_Y || holes.contains(pos);
        }

        /** Nothing solid anywhere except what {@link #solid} adds. */
        World emptied() {
            voidWorld = true;
            return this;
        }

        World solid(int x, int y, int z) {
            solid.add(new Pos(x, y, z));
            return this;
        }

        World hole(int x, int y, int z) {
            holes.add(new Pos(x, y, z));
            return this;
        }
    }

    private static Set<Pos> single(int x, int y, int z) {
        Set<Pos> footprint = new LinkedHashSet<>();
        footprint.add(new Pos(x, y, z));
        return footprint;
    }

    private static List<Pos> plan(Set<Pos> footprint, boolean floor, boolean head, boolean roof,
                                  World world, boolean supports) {
        return SurroundPlanner.order(
                SurroundPlanner.shell(footprint, floor, head, roof), world, SurroundPlanner.bodyCells(footprint), supports);
    }

    @Test
    void feetShellOnFlatGroundIsTheFourSides() {
        Set<Pos> footprint = single(0, 64, 0);
        Set<Pos> shell = SurroundPlanner.shell(footprint, false, false, false);
        assertEquals(Set.of(new Pos(0, 64, -1), new Pos(1, 64, 0), new Pos(0, 64, 1), new Pos(-1, 64, 0)), shell);
    }

    @Test
    void shellOfAStraddlingPlayerSkipsItsOwnCells() {
        Set<Pos> footprint = new LinkedHashSet<>(List.of(new Pos(0, 64, 0), new Pos(1, 64, 0)));
        Set<Pos> sides = SurroundPlanner.sides(footprint);
        assertEquals(6, sides.size());
        assertFalse(sides.contains(new Pos(0, 64, 0)));
        assertFalse(sides.contains(new Pos(1, 64, 0)));
        assertTrue(sides.contains(new Pos(-1, 64, 0)));
        assertTrue(sides.contains(new Pos(2, 64, 0)));
    }

    @Test
    void fullBoxAddsFloorHeadSidesAndRoof() {
        Set<Pos> shell = SurroundPlanner.shell(single(0, 64, 0), true, true, true);
        assertTrue(shell.contains(new Pos(0, 63, 0)), "floor under the player");
        assertTrue(shell.contains(new Pos(1, 63, 0)), "floor under a side");
        assertTrue(shell.contains(new Pos(1, 65, 0)), "head-level side");
        assertTrue(shell.contains(new Pos(0, 66, 0)), "roof two above the feet");
        assertEquals(4 + 4 + 4 + 1 + 1, shell.size());
    }

    @Test
    void onlyBlocksThatAreStillMissingAreKept() {
        World world = new World().solid(1, 64, 0).solid(-1, 64, 0);
        List<Pos> plan = plan(single(0, 64, 0), false, false, false, world, true);
        assertEquals(Set.of(new Pos(0, 64, -1), new Pos(0, 64, 1)), new HashSet<>(plan));
    }

    @Test
    void layersComeBottomUp() {
        List<Pos> plan = plan(single(0, 64, 0), true, true, false, new World(), false);
        for (int i = 1; i < plan.size(); i++) {
            assertTrue(plan.get(i - 1).y() <= plan.get(i).y(), "layer order broken at " + i);
        }
    }

    @Test
    void sideOverAGapGetsAPillarFirst() {
        // The ground under the north side is missing, so the side has no solid neighbour except the
        // ground blocks beside that gap (reached through a block placed under it).
        World world = new World().hole(0, GROUND_Y, -1);
        List<Pos> plan = plan(single(0, 64, 0), false, false, false, world, true);
        int pillar = plan.indexOf(new Pos(0, GROUND_Y, -1));
        int side = plan.indexOf(new Pos(0, 64, -1));
        assertTrue(pillar >= 0, "pillar block planned");
        assertTrue(pillar < side, "pillar goes in before the block resting on it");
        assertEquals(5, plan.size());
    }

    @Test
    void gapSupportIsSkippedWhenSupportIsOff() {
        World world = new World().hole(0, GROUND_Y, -1);
        List<Pos> plan = plan(single(0, 64, 0), false, false, false, world, false);
        assertEquals(4, plan.size());
        assertFalse(plan.contains(new Pos(0, GROUND_Y, -1)));
    }

    @Test
    void sidesOnSolidGroundNeedNoSupport() {
        List<Pos> plan = plan(single(0, 64, 0), false, false, false, new World(), true);
        assertEquals(4, plan.size());
    }

    /** Every block must touch something solid, or something planned before it, at the moment it is placed. */
    private static void assertPlaceableInOrder(List<Pos> plan, World world) {
        Set<Pos> before = new HashSet<>();
        for (Pos pos : plan) {
            boolean touches = false;
            for (Pos near : List.of(pos.below(), pos.above(), pos.offset(1, 0), pos.offset(-1, 0),
                    pos.offset(0, 1), pos.offset(0, -1))) {
                if (!world.test(near) || before.contains(near)) touches = true;
            }
            assertTrue(touches, pos + " has nothing to rest on when it is placed");
            before.add(pos);
        }
    }

    @Test
    void roofInOpenAirIsBridgedFromTheHeadLevelWall() {
        // The head-level sides rest on the feet-level wall; the roof at y=66 then gets one bridge block
        // on top of a head-level side, because the cell under the roof is the player's own head.
        World world = new World();
        List<Pos> plan = plan(single(0, 64, 0), false, true, true, world, true);
        assertTrue(plan.contains(new Pos(0, 66, 0)), "roof planned");
        assertPlaceableInOrder(plan, world);
    }

    @Test
    void roofWithoutHeadWallNeedsAPillarAndABridge() {
        List<Pos> plan = plan(single(0, 64, 0), false, false, true, new World(), true);
        int roof = plan.indexOf(new Pos(0, 66, 0));
        assertTrue(roof >= 2, "pillar and bridge come first");
        assertTrue(plan.indexOf(new Pos(1, 65, 0)) >= 0 || plan.indexOf(new Pos(0, 65, 1)) >= 0
                || plan.indexOf(new Pos(-1, 65, 0)) >= 0 || plan.indexOf(new Pos(0, 65, -1)) >= 0);
    }

    @Test
    void trapHeadIsJustTheRoof() {
        assertEquals(Set.of(new Pos(0, 66, 0)), SurroundPlanner.trap(single(0, 64, 0), false, false));
    }

    @Test
    void trapFaceAddsTheHeadLevelSides() {
        Set<Pos> trap = SurroundPlanner.trap(single(0, 64, 0), true, false);
        assertEquals(5, trap.size());
        assertTrue(trap.contains(new Pos(1, 65, 0)));
        assertFalse(trap.contains(new Pos(1, 66, 0)));
    }

    @Test
    void trapFullAddsTheRoofLevelSidesToo() {
        Set<Pos> trap = SurroundPlanner.trap(single(0, 64, 0), true, true);
        assertEquals(9, trap.size());
        assertTrue(trap.contains(new Pos(1, 66, 0)));
    }

    @Test
    void trapRoofsEveryCellOfAStraddlingPlayer() {
        Set<Pos> footprint = new LinkedHashSet<>(List.of(new Pos(0, 64, 0), new Pos(1, 64, 0)));
        Set<Pos> trap = SurroundPlanner.trap(footprint, false, false);
        assertEquals(Set.of(new Pos(0, 66, 0), new Pos(1, 66, 0)), trap);
    }

    @Test
    void trapInOpenGroundBuildsItsOwnWayUp() {
        World world = new World();
        Set<Pos> footprint = single(0, 64, 0);
        List<Pos> plan = SurroundPlanner.order(
                SurroundPlanner.trap(footprint, true, true), world, SurroundPlanner.bodyCells(footprint), true);
        assertTrue(plan.contains(new Pos(0, 66, 0)), "roof planned");
        assertTrue(plan.size() > 9, "supports were added under and beside the trap");
        assertPlaceableInOrder(plan, world);
    }

    @Test
    void roofChainNeverEntersThePlayersBody() {
        Set<Pos> footprint = single(0, 64, 0);
        List<Pos> plan = plan(footprint, false, false, true, new World(), true);
        assertFalse(plan.contains(new Pos(0, 64, 0)));
        assertFalse(plan.contains(new Pos(0, 65, 0)));
    }

    @Test
    void floatingWithNothingSolidNearbyStillListsTheTargets() {
        // Nothing can anchor anything, so the plan is the wanted blocks and no invented supports.
        List<Pos> plan = plan(single(0, 64, 0), false, false, false, new World().emptied(), true);
        assertEquals(4, plan.size());
    }

    @Test
    void aSingleSolidBlockNextToTheGapAnchorsTheWholeShell() {
        // One solid block under the east side in an empty world: every block up to and including the
        // east side must be placeable in order, with bridges built out to the sides that touch nothing.
        World world = new World().emptied().solid(1, 63, 0);
        List<Pos> plan = plan(single(0, 64, 0), false, false, false, world, true);
        assertTrue(plan.contains(new Pos(1, 64, 0)));
        assertPlaceableInOrder(plan.subList(0, plan.indexOf(new Pos(1, 64, 0)) + 1), world);
    }

    @Test
    void duplicatePositionsArePlacedOnce() {
        List<Pos> wanted = List.of(new Pos(1, 64, 0), new Pos(1, 64, 0), new Pos(2, 64, 0));
        List<Pos> plan = SurroundPlanner.order(wanted, new World(), Set.of(), true);
        assertEquals(2, plan.size());
    }
}
