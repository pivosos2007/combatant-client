/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static combatant.client.util.schematic.walk.WalkTerrain.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteFollowerTest {

    private static final int NO_TIMEOUT = 100_000;

    private static long[] raw(WalkTerrain world, long start, long goal) {
        WalkPathfinder.Search search = new WalkPathfinder.Search(world);
        search.startPath(start, goal, 1, 50_000, null);
        search.run();
        return search.path();
    }

    private static RouteFollower follow(WalkTerrain world, long start, long goal) {
        RouteFollower follower = new RouteFollower();
        follower.start(world, raw(world, start, goal), 1);
        return follower;
    }

    @Test
    void aimsFarAheadOnAStraightRoute() {
        GridWorld world = new GridWorld();
        RouteFollower follower = follow(world, pack(0, 0, 0), pack(12, 0, 0));
        assertEquals(RouteFollower.Action.FOLLOW, follower.tick(world, 0.5, 0, 0.5, 0, 0, true, false, NO_TIMEOUT));
        assertTrue(follower.aimX() >= 5.5, "aim " + follower.aimX());
        assertTrue(follower.sprint(), "a long level stretch is sprintable");
    }

    @Test
    void replansFromWhereThePlayerWasPushedTo() {
        GridWorld world = new GridWorld();
        long goal = pack(10, 0, 0);
        RouteFollower follower = follow(world, pack(0, 0, 0), goal);
        assertEquals(RouteFollower.Action.FOLLOW, follower.tick(world, 3.5, 0, 0.5, 0.2, 0, true, false, NO_TIMEOUT));
        // Knocked four blocks sideways: the old line is useless.
        assertEquals(RouteFollower.Action.REPLAN, follower.tick(world, 3.5, 0, 4.5, 0, 0, true, false, NO_TIMEOUT));
        long[] fresh = raw(world, pack(3, 0, 4), goal);
        assertNotNull(fresh);
        follower.restart(world, fresh);
        assertEquals(RouteFollower.Action.FOLLOW, follower.tick(world, 3.5, 0, 4.5, 0, 0, true, false, NO_TIMEOUT));
        assertTrue(follower.aimX() > 3.5 && follower.aimZ() < 4.5, "heads back toward the goal");

        // Fallen two blocks into a hole: also a replan.
        GridWorld hole = new GridWorld().fill(4, -3, -1, 6, -1, 1, WalkTerrain.OPEN).set(5, -4, 0, WalkTerrain.SOLID);
        RouteFollower other = follow(hole, pack(0, 0, 3), pack(10, 0, 3));
        other.tick(hole, 0.5, 0, 3.5, 0, 0, true, false, NO_TIMEOUT);
        assertEquals(RouteFollower.Action.REPLAN, other.tick(hole, 5.5, -3, 0.5, 0, 0, true, false, NO_TIMEOUT));
    }

    @Test
    void replansWhenABlockAppearsOnTheRoute() {
        GridWorld world = new GridWorld();
        RouteFollower follower = follow(world, pack(0, 0, 0), pack(8, 0, 0));
        assertEquals(RouteFollower.Action.FOLLOW, follower.tick(world, 0.5, 0, 0.5, 0, 0, true, false, NO_TIMEOUT));
        world.block(1, 0, 0).block(1, 1, 0);
        assertEquals(RouteFollower.Action.REPLAN, follower.tick(world, 0.5, 0, 0.5, 0, 0, true, false, NO_TIMEOUT));
    }

    @Test
    void escapesInOrderThenReplansThenGivesUp() {
        GridWorld world = new GridWorld();
        long[] path = raw(world, pack(0, 0, 0), pack(8, 0, 0));
        RouteFollower follower = new RouteFollower();
        follower.start(world, path, 1);
        Set<Integer> escapes = new HashSet<>();
        boolean jumped = false;
        int replans = 0;
        RouteFollower.Action action = RouteFollower.Action.FOLLOW;
        for (int t = 0; t < 1000 && action != RouteFollower.Action.ABORT; t++) {
            // Pinned in place: the position never changes.
            action = follower.tick(world, 0.5, 0, 0.5, 0, 0, true, false, NO_TIMEOUT);
            if (action == RouteFollower.Action.REPLAN) {
                if (replans == 0) {
                    assertTrue(escapes.contains(RouteFollower.ESCAPE_RECENTER) || escapes.contains(RouteFollower.ESCAPE_JUMP));
                    assertTrue(escapes.contains(RouteFollower.ESCAPE_BACK_OFF));
                    assertTrue(escapes.contains(RouteFollower.ESCAPE_SIDESTEP));
                    assertTrue(jumped, "tried a jump before giving the route up");
                }
                replans++;
                follower.restart(world, path);
            }
            escapes.add(follower.escape());
            jumped |= follower.jump();
        }
        assertEquals(RouteFollower.Action.ABORT, action);
        assertEquals(RouteFollower.MAX_STUCK_REPLANS, replans);
    }

    @Test
    void stuckTimeoutAbortsEvenWhileEscaping() {
        GridWorld world = new GridWorld();
        RouteFollower follower = follow(world, pack(0, 0, 0), pack(8, 0, 0));
        RouteFollower.Action action = RouteFollower.Action.FOLLOW;
        int t = 0;
        while (action == RouteFollower.Action.FOLLOW && t < 100) {
            action = follower.tick(world, 0.5, 0, 0.5, 0, 0, true, false, 12);
            t++;
        }
        assertEquals(RouteFollower.Action.ABORT, action);
        assertTrue(t <= 15, "gave up after " + t);
    }

    @Test
    void jumpsOnlyCloseToTheStep() {
        GridWorld world = new GridWorld().fill(3, 0, -3, 6, 0, 3, WalkTerrain.SOLID);
        RouteFollower follower = follow(world, pack(0, 0, 0), pack(5, 1, 0));
        follower.tick(world, 0.5, 0, 0.5, 0.2, 0, true, false, NO_TIMEOUT);
        assertFalse(follower.jump());
        follower.tick(world, 1.6, 0, 0.5, 0.2, 0, true, false, NO_TIMEOUT);
        assertFalse(follower.jump(), "too early");
        follower.tick(world, 2.3, 0, 0.5, 0.2, 0, true, false, NO_TIMEOUT);
        assertTrue(follower.jump(), "in front of the step");
    }

    @Test
    void halfBlockStepsAreWalkedUpWithoutJumping() {
        GridWorld world = new GridWorld().set(3, 0, 0, WalkTerrain.STEP).fill(4, 0, -2, 8, 0, 2, WalkTerrain.SOLID);
        RouteFollower follower = follow(world, pack(0, 0, 0), pack(6, 1, 0));
        follower.tick(world, 0.5, 0, 0.5, 0.2, 0, true, false, NO_TIMEOUT);
        follower.tick(world, 2.4, 0, 0.5, 0.2, 0, true, false, NO_TIMEOUT);
        assertFalse(follower.jump());
    }

    @Test
    void coastsOntoTheGoalWhenMomentumIsEnough() {
        GridWorld world = new GridWorld();
        RouteFollower follower = follow(world, pack(0, 0, 0), pack(4, 0, 0));
        follower.tick(world, 1.5, 0, 0.5, 0.28, 0, true, false, NO_TIMEOUT);
        assertFalse(follower.coast());
        follower.tick(world, 4.15, 0, 0.5, 0.28, 0, true, false, NO_TIMEOUT);
        assertTrue(follower.coast(), "sprint-speed momentum carries the last third of a block");
        assertFalse(follower.sprint(), "no sprint right before the stop");

        // On ice the slide is long: let go much earlier.
        GridWorld ice = new GridWorld().fill(-2, -1, -2, 10, -1, 2, WalkTerrain.ICE);
        RouteFollower slide = follow(ice, pack(0, 0, 0), pack(6, 0, 0));
        slide.tick(ice, 5.6, 0, 0.5, 0.25, 0, true, false, NO_TIMEOUT);
        assertTrue(slide.coast(), "nearly a block before the goal");
    }

    @Test
    void arrivesOnlyOnTheGround() {
        GridWorld world = new GridWorld();
        RouteFollower follower = follow(world, pack(0, 0, 0), pack(3, 0, 0));
        assertEquals(RouteFollower.Action.FOLLOW, follower.tick(world, 3.5, 0.2, 0.5, 0, 0, false, false, NO_TIMEOUT));
        assertEquals(RouteFollower.Action.ARRIVED, follower.tick(world, 3.5, 0, 0.5, 0, 0, true, false, NO_TIMEOUT));
        assertFalse(follower.active());
    }

    @Test
    void climbsLaddersByHoldingJump() {
        GridWorld world = new GridWorld().fill(2, 0, -4, 2, 3, 4, WalkTerrain.SOLID).fill(1, 0, 0, 1, 3, 0, WalkTerrain.CLIMB)
                .fill(-4, -1, -5, 6, 6, -5, WalkTerrain.UNKNOWN).fill(-4, -1, 5, 6, 6, 5, WalkTerrain.UNKNOWN);
        RouteFollower follower = follow(world, pack(-2, 0, 0), pack(2, 4, 0));
        follower.tick(world, -1.5, 0, 0.5, 0, 0, true, false, NO_TIMEOUT);
        assertFalse(follower.climb());
        follower.tick(world, 1.5, 0.4, 0.5, 0, 0, false, false, NO_TIMEOUT);
        assertTrue(follower.climb(), "on the ladder with the route going up");
    }
}
