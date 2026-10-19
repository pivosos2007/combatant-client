/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

import combatant.client.util.schematic.walk.RouteFollower;
import combatant.client.util.schematic.walk.TerrainCache;
import combatant.client.util.schematic.walk.WalkPathfinder;
import combatant.client.util.schematic.walk.WalkTerrain;

/**
 * Incremental walk navigation: A* in slices of {@link #PLAN_BUDGET_NANOS} per tick, then {@link RouteFollower}
 * steering. While planning the player stands still. Goals that are not a standable cell (a solid block to reach,
 * a spot within a radius) are planned to the closest reachable cell and judged by the request's own predicate.
 */
final class WalkNavigationHandle extends AbstractNavigationHandle {

    /** Search time per tick; a few hundred expansions. */
    static final long PLAN_BUDGET_NANOS = 1_500_000L;
    static final int MAX_NODES = 60_000;
    /** Ticks without progress after which the route follower gives up its escapes. */
    static final int STUCK_TIMEOUT = 40;
    static final int MAX_REPLANS = 8;

    private final WalkTerrain live;
    private final TerrainCache cache;
    private final WalkPathfinder.Search search;
    private final RouteFollower follower = new RouteFollower();
    private final long goalCell;
    private boolean planning;
    private boolean partialRoute;
    private int replans;
    private double remaining = Double.NaN;

    WalkNavigationHandle(NavigationRequest request, WalkTerrain live) {
        super(request);
        this.live = live;
        this.cache = new TerrainCache(live);
        this.search = new WalkPathfinder.Search(cache);
        this.goalCell = WalkTerrain.pack(request.x(), request.y(), request.z());
    }

    @Override
    protected void step(PlayerSample p) {
        if (request.satisfiedBy(p.x, p.y, p.z, p.eyeHeight)) {
            succeed();
            return;
        }
        remaining = follower.active()
                ? follower.remaining(p.x, p.z)
                : Math.hypot(request.x() + 0.5 - p.x, request.z() + 0.5 - p.z);
        long feet = feetCell(p);
        if (!planning && !follower.active()) beginPlan(feet);
        if (planning) {
            advancePlan();
            if (planning || isDone()) return;
        }
        follow(p, feet);
    }

    private void beginPlan(long feet) {
        search.startPath(feet, goalCell, request.options().maxDrop(), MAX_NODES, null);
        planning = true;
    }

    private void advancePlan() {
        if (search.step(System.nanoTime() + PLAN_BUDGET_NANOS) == WalkPathfinder.Search.RUNNING) return;
        planning = false;
        long[] path;
        if (search.status() == WalkPathfinder.Search.FOUND) {
            path = search.path();
            partialRoute = false;
        } else {
            path = request.kind() == NavigationRequest.GoalKind.BLOCK ? null : search.partialPath();
            partialRoute = true;
        }
        if (path == null || path.length < 2) {
            fail(NavigationFailure.NO_PATH, "no route after " + search.expanded() + " nodes");
            return;
        }
        if (!request.options().allowJump() && requiresJump(path)) {
            fail(NavigationFailure.NO_PATH, "route needs a jump");
            return;
        }
        if (follower.active()) follower.restart(cache, path);
        else follower.start(cache, path, request.options().maxDrop());
    }

    private void follow(PlayerSample p, long feet) {
        RouteFollower.Action action = follower.tick(live, p.x, p.y, p.z, p.vx, p.vz, p.onGround,
                p.horizontalCollision, STUCK_TIMEOUT);
        switch (action) {
            case FOLLOW -> {
                NavigationRequest.Options options = request.options();
                intent.steer = !follower.coast();
                intent.aimX = follower.aimX();
                intent.aimZ = follower.aimZ();
                intent.jump = follower.jump() && p.onGround && options.allowJump() || follower.climb();
                intent.sprint = options.allowSprint() && follower.sprint();
            }
            case ARRIVED -> {
                if (request.satisfiedBy(p.x, p.y, p.z, p.eyeHeight)) succeed();
                else if (partialRoute) fail(NavigationFailure.NO_PATH, "closest reachable point is outside the goal");
                else replan(feet);
            }
            case REPLAN -> replan(feet);
            case ABORT -> fail(NavigationFailure.STUCK, "route follower gave up");
        }
    }

    private void replan(long feet) {
        if (++replans > MAX_REPLANS) {
            fail(NavigationFailure.STUCK, "replanned " + MAX_REPLANS + " times");
            return;
        }
        cache.clear();
        beginPlan(feet);
    }

    /** A step that cannot be walked: a gap to cross, or a rise onto something that is not a half block. */
    private boolean requiresJump(long[] path) {
        for (int i = 1; i < path.length; i++) {
            int ax = WalkTerrain.unpackX(path[i - 1]), ay = WalkTerrain.unpackY(path[i - 1]), az = WalkTerrain.unpackZ(path[i - 1]);
            int bx = WalkTerrain.unpackX(path[i]), by = WalkTerrain.unpackY(path[i]), bz = WalkTerrain.unpackZ(path[i]);
            if (Math.max(Math.abs(bx - ax), Math.abs(bz - az)) > 1) return true;
            if (by <= ay) continue;
            boolean climbing = live.cell(ax, ay, az) == WalkTerrain.CLIMB || live.cell(bx, by, bz) == WalkTerrain.CLIMB;
            boolean halfBlock = live.cell(bx, by - 1, bz) == WalkTerrain.STEP || live.cell(bx, by, bz) == WalkTerrain.RAISED;
            if (!climbing && !halfBlock) return true;
        }
        return false;
    }

    private static long feetCell(PlayerSample p) {
        return WalkTerrain.pack((int) Math.floor(p.x), (int) Math.floor(p.y + 0.5), (int) Math.floor(p.z));
    }

    @Override
    public boolean drivesPlayer() {
        return false;
    }

    @Override
    public double remainingDistance() {
        return remaining;
    }

    @Override
    public int expandedNodes() {
        return search.expanded();
    }

    @Override
    protected void onFinished(NavigationResult result) {
        follower.stop();
    }

}
