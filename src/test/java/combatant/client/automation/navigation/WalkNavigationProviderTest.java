/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

import combatant.client.util.schematic.walk.WalkTerrain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The walk provider on a flat synthetic world: floor at y = -1, walls where the test puts them. */
class WalkNavigationProviderTest {

    private static final int HALF = 12;

    /** A bounded flat world; everything outside +-HALF is unknown so failed searches end fast. */
    private static final class Flat implements WalkTerrain {
        private final java.util.Map<Long, Byte> overrides = new java.util.HashMap<>();

        Flat set(int x, int y, int z, byte cell) {
            overrides.put(WalkTerrain.pack(x, y, z), cell);
            return this;
        }

        @Override
        public byte cell(int x, int y, int z) {
            Byte o = overrides.get(WalkTerrain.pack(x, y, z));
            if (o != null) return o;
            if (Math.abs(x) > HALF || Math.abs(z) > HALF) return UNKNOWN;
            return y < 0 ? SOLID : OPEN;
        }
    }

    /** Simulated player: walks at 0.2 blocks a tick toward the aim point while steering. */
    private static final class Walker {
        final PlayerSample sample = new PlayerSample().set(0.5, 0.0, 0.5, 0, 0, true, false);
        int jumps;

        void move(NavigationIntent intent) {
            double vx = 0, vz = 0;
            if (intent.steer) {
                double dx = intent.aimX - sample.x, dz = intent.aimZ - sample.z;
                double len = Math.hypot(dx, dz);
                if (len > 1e-6) {
                    double speed = Math.min(0.2, len);
                    vx = dx / len * speed;
                    vz = dz / len * speed;
                }
            }
            if (intent.jump) jumps++;
            sample.set(sample.x + vx, sample.y, sample.z + vz, vx, vz, true, false);
        }
    }

    private static NavigationHandle start(WalkTerrain world, NavigationRequest request) {
        return new WalkNavigationProvider(() -> world, () -> true).navigate(request);
    }

    private static int run(NavigationHandle handle, Walker walker, int maxTicks) {
        int ticks = 0;
        while (!handle.isDone() && ticks < maxTicks) {
            handle.tick(walker.sample);
            if (!handle.isDone()) walker.move(handle.intent());
            ticks++;
        }
        return ticks;
    }

    @Test
    void walksToABlockAcrossOpenGround() {
        NavigationHandle handle = start(new Flat(), NavigationRequest.block(8, 0, 5));
        Walker walker = new Walker();
        run(handle, walker, 600);
        assertEquals(NavigationStatus.SUCCEEDED, handle.status(), String.valueOf(handle.result()));
        assertEquals(8, (int) Math.floor(walker.sample.x));
        assertEquals(5, (int) Math.floor(walker.sample.z));
        assertTrue(handle.expandedNodes() > 0);
    }

    @Test
    void routesAroundAWallThroughItsGap() {
        Flat world = new Flat();
        for (int z = -HALF; z <= HALF; z++) {
            if (z == 6) continue;
            world.set(4, 0, z, WalkTerrain.SOLID).set(4, 1, z, WalkTerrain.SOLID);
        }
        NavigationHandle handle = start(world, NavigationRequest.block(8, 0, 0));
        Walker walker = new Walker();
        run(handle, walker, 1500);
        assertEquals(NavigationStatus.SUCCEEDED, handle.status(), String.valueOf(handle.result()));
        assertEquals(8, (int) Math.floor(walker.sample.x));
    }

    @Test
    void sealedGoalFailsWithNoPath() {
        Flat world = new Flat();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                world.set(8 + dx, 0, 8 + dz, WalkTerrain.SOLID).set(8 + dx, 1, 8 + dz, WalkTerrain.SOLID);
            }
        }
        NavigationHandle handle = start(world, NavigationRequest.block(8, 0, 8));
        run(handle, new Walker(), 3000);
        assertEquals(NavigationStatus.FAILED, handle.status());
        assertEquals(NavigationFailure.NO_PATH, handle.result().failure());
    }

    @Test
    void alreadyInsideTheGoalSucceedsWithoutPlanning() {
        NavigationHandle handle = start(new Flat(), NavigationRequest.near(1, 0, 0, 3));
        handle.tick(new Walker().sample);
        assertEquals(NavigationStatus.SUCCEEDED, handle.status());
        assertEquals(0, handle.expandedNodes());
    }

    @Test
    void reachGoalStopsAtTheBlocksReachNotInsideIt() {
        Flat world = new Flat().set(8, 0, 0, WalkTerrain.SOLID).set(8, 1, 0, WalkTerrain.SOLID);
        NavigationHandle handle = start(world, NavigationRequest.reach(8, 1, 0, 4.5));
        Walker walker = new Walker();
        run(handle, walker, 800);
        assertEquals(NavigationStatus.SUCCEEDED, handle.status(), String.valueOf(handle.result()));
        assertTrue(NavigationRequest.reach(8, 1, 0, 4.5).satisfiedBy(walker.sample.x, walker.sample.y, walker.sample.z, 1.62));
        assertTrue(walker.sample.x < 8.0, "stopped short of the wall");
    }

    @Test
    void unreachableNearGoalFailsInsteadOfWalkingForever() {
        Flat world = new Flat();
        for (int x = 5; x <= 11; x++) {
            for (int z = -HALF; z <= HALF; z++) world.set(x, 0, z, WalkTerrain.SOLID).set(x, 1, z, WalkTerrain.SOLID);
        }
        NavigationHandle handle = start(world, NavigationRequest.near(9, 0, 0, 1.5));
        run(handle, new Walker(), 3000);
        assertEquals(NavigationStatus.FAILED, handle.status());
        assertEquals(NavigationFailure.NO_PATH, handle.result().failure());
    }

    @Test
    void forbiddenJumpRejectsARouteThatNeedsOne() {
        // A full-block step up at x = 3 spanning the whole world width: the only way on is a jump.
        Flat world = new Flat();
        for (int z = -HALF; z <= HALF; z++) {
            for (int x = 3; x <= HALF; x++) world.set(x, 0, z, WalkTerrain.SOLID);
        }
        NavigationRequest.Options noJump = NavigationRequest.Options.DEFAULT.withJump(false);
        NavigationHandle blocked = start(world, NavigationRequest.block(6, 1, 0).withOptions(noJump));
        run(blocked, new Walker(), 3000);
        assertEquals(NavigationFailure.NO_PATH, blocked.result().failure());
        assertTrue(blocked.result().detail().contains("jump"));

        NavigationHandle allowed = start(world, NavigationRequest.block(6, 1, 0));
        Walker walker = new Walker();
        for (int i = 0; i < 200 && allowed.intent().aimX == 0.0 && !allowed.isDone(); i++) allowed.tick(walker.sample);
        assertEquals(NavigationStatus.RUNNING, allowed.status(), "a route with a jump exists when jumping is allowed");
        assertTrue(allowed.intent().steer);
    }

    @Test
    void cancelStopsMidRouteAndReleasesIntent() {
        NavigationHandle handle = start(new Flat(), NavigationRequest.block(10, 0, 10));
        Walker walker = new Walker();
        run(handle, walker, 10);
        handle.cancel();
        assertEquals(NavigationStatus.CANCELLED, handle.status());
        handle.tick(walker.sample);
        assertFalse(handle.intent().steer);
    }
}
