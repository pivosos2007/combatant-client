/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.modules.movement.holesnap.Steering;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.schematic.walk.CellSet;
import combatant.client.util.schematic.walk.PillarPlanner;
import combatant.client.util.schematic.walk.ReachModel;
import combatant.client.util.schematic.walk.RouteFollower;
import combatant.client.util.schematic.walk.StandingSpotChooser;
import combatant.client.util.schematic.walk.TerrainCache;
import combatant.client.util.schematic.walk.WalkPathfinder;
import combatant.client.util.schematic.walk.WalkTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Moves the player for {@link SchematicBuilder} with nothing but the movement keys: walks to a spot from which
 * the next missing cells can be clicked, jump-places a support column when no spot reaches them (or when that is
 * much cheaper than the walk), and breaks those support blocks again once the build is done.
 *
 * <p>Everything the server sees is ordinary input: keys pressed through {@link MovementInputEvent}, one jump at a
 * time from the ground, sprint only through the sprint key on long level stretches, never while a click is due, no
 * velocity writes. Placement and mining clicks go out from the builder's pre-tick hook, before the movement packet,
 * the same order vanilla uses.</p>
 *
 * <p>Planning reads the world through a {@link TerrainCache} and runs in slices: a flood, the spot scoring and a
 * pillar search each stop when the tick's budget ({@link #PLAN_BUDGET_NANOS}) is spent and carry on next tick, so
 * a plan never costs a frame spike.</p>
 */
final class BuildMover {

    enum Mode {
        IDLE,
        WALKING,
        PILLARING,
        CLEARING
    }

    /** What the builder lends the mover. */
    interface Host {
        /** Missing cells of the working layers that the inventory has material for, packed; returns the count. */
        int walkTargets(LocalPlayer player, long[] out);

        double reach();

        boolean inFootprint(long cell);

        void aim(Rotation rotation);

        int stuckTimeout();

        boolean pillarEnabled();

        Set<String> helperIds();

        /** More of this block in the inventory than the unfinished build still needs: pillaring with it is free. */
        boolean canSpare(Block block);

        /** Ends the builder's silent hotbar lease so a tool switch is what the server sees. */
        void releaseHotbar();

        /** Whether a target is worth walking to these eyes for; pistons and observers need a particular look. */
        boolean lookAllows(long target, double eyeX, double eyeY, double eyeZ);
    }

    /** A support block click the builder should send this tick. */
    record HelperClick(BuildPlanner.Plan plan, int hotbarSlot, BlockState expected) {
    }

    private enum Stage {
        NONE,
        FLOOD,
        SPOTS,
        PILLAR
    }

    private static final int MAX_TARGETS = 1024;
    private static final double FLOOD_COST = 72.0;
    /** A first flood only reaches this far past the targets; the full one runs when it finds nothing. */
    private static final double FLOOD_SLACK = 20.0;
    private static final int FLOOD_NODES = 8000;
    private static final int PATH_NODES = 8000;
    private static final int MAX_DROP = 1;
    /** Getting off a finished build to break the support blocks may need a slightly bigger, still harmless drop. */
    private static final int CLEAR_MAX_DROP = 2;
    /** Falls of up to three blocks cost no health. */
    private static final int SAFE_DROP = 3;
    private static final int MAX_PILLAR = 8;
    /** Walk planning works with a slightly shorter reach than clicks do: the player never stops dead centre. */
    private static final double PLAN_MARGIN = 0.35;
    private static final double CENTER_DISTANCE = 0.2;
    private static final int REPLAN_WAIT_TICKS = 40;
    private static final int EMPTY_WAIT_TICKS = 10;
    private static final int ABORT_COOLDOWN_TICKS = 100;
    /** After arriving the builder gets this long to find a click before the spot counts as useless. */
    private static final int ARRIVAL_GRACE_TICKS = 1;
    private static final int MAX_PILLAR_RETRIES = 4;
    private static final int MAX_GOAL_REPLANS = 6;
    private static final float PILLAR_PITCH_READY = 80.0f;
    /** Planning work allowed per tick; the rest waits for the next tick. */
    static final long PLAN_BUDGET_NANOS = 1_000_000L;
    /** The cache is rebuilt from the world this often, catching block changes nobody reported. */
    private static final int CACHE_REFRESH_TICKS = 600;
    private static final int CACHE_MAX_SECTIONS = 4096;
    /** Sections read while their chunk was missing are read again this often. */
    private static final int CACHE_REFILL_TICKS = 20;
    /** Spots further than this (route cost) are weighed against a pillar near the targets. */
    private static final double PILLAR_COMPARE_COST = 24.0;
    /** A pillar has to beat the walk by this factor: its blocks still need breaking again afterwards. */
    private static final double PILLAR_ADVANTAGE = 1.5;
    private static final int PILLAR_STALL_TICKS = 200;

    private final Minecraft mc;
    private final Host host;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private final long[] targets = new long[MAX_TARGETS];
    private int targetCount;
    /** Targets of the previous plan: those missing from the next one got their block and must be read again. */
    private long[] previousTargets = new long[0];
    private final CellSet reserved = new CellSet();
    private final CellSet rejected = new CellSet();
    private final Map<Long, Block> helpers = new LinkedHashMap<>();
    /** The world the tracked support blocks stand in; blocks of another world are never broken. */
    private WeakReference<ClientLevel> helperLevel = new WeakReference<>(null);
    /** The world as the planner sees it: one lookup per cell, shared by every search until refreshed. */
    private final TerrainCache cache = new TerrainCache(new WorldSections());
    /** Classes of block states whose collision shape never depends on the position: most of them. */
    private final Map<BlockState, Byte> stateClasses = new IdentityHashMap<>();
    /** The world right now, for the few cells checked every tick. */
    private final WalkTerrain live = this::classifyLive;
    private final WalkPathfinder.Search floodSearch = new WalkPathfinder.Search(cache);
    private final WalkPathfinder.Search routeSearch = new WalkPathfinder.Search(cache);
    private final RouteFollower follower = new RouteFollower();
    private ClientLevel cacheLevel;
    private int cacheAge;

    private Mode mode = Mode.IDLE;
    private Stage stage = Stage.NONE;
    private StandingSpotChooser.Mode planMode;
    private double planReach;
    private double floodBound;
    private long planStart;
    private WalkPathfinder.Flood flood;
    private StandingSpotChooser.Scorer scorer;
    private StandingSpotChooser.Spot found;
    private PillarPlanner.Planner pillarPlanner;
    private boolean replanPending;
    private int routeDrop = MAX_DROP;
    /** The drop the current plan's flood allowed; routes and replans for it must allow the same. */
    private int floodDrop = MAX_DROP;

    private long goal;
    private boolean hasGoal;
    private StandingSpotChooser.Spot spot;
    private PillarPlanner.Pillar pillar;
    private int pillarBaseY = Integer.MIN_VALUE;
    private int pillarRetries;
    private int pillarProgressBlocks;
    private int pillarIdleTicks;
    private boolean jumpThisTick;
    private boolean controlling;
    private boolean holdStill;
    /** A click is due this tick (the builder holds the rotation for it). */
    private boolean clicking;
    private int replanWait;
    private int abortCooldown;
    private int arrivalGrace;
    private boolean warnedUnreachable;
    private BlockPos mining;
    private Direction miningFace;
    private Vec3 lastPosition;

    private int routes;
    private int routeCells;
    private double walked;
    private int pillarBlocks;
    private int helpersRemoved;
    private int aborts;
    private int pillarJumps;
    private int replans;
    /** Replans for the current goal; past {@link #MAX_GOAL_REPLANS} the goal is dropped. */
    private int goalReplans;
    /** Ticks that spent part of their budget on a plan; more than one per plan means it was sliced. */
    private int planTicks;
    private int emptyWaits;
    private int unreachableWaits;
    /** Longest single tick spent in the mover's planning and route following, for the smoke report. */
    private long cpuMaxNanos;
    /** The layer (world y) for which no standing spot level with it reaches anything: build it from below. */
    private int lowGroundLayer = Integer.MIN_VALUE;

    BuildMover(Minecraft mc, Host host) {
        this.mc = mc;
        this.host = host;
    }

    Mode mode() {
        return mode;
    }

    boolean hasHelpers() {
        return !helpers.isEmpty();
    }

    int routes() {
        return routes;
    }

    int routeCells() {
        return routeCells;
    }

    double walked() {
        return walked;
    }

    int pillarBlocks() {
        return pillarBlocks;
    }

    int helpersRemoved() {
        return helpersRemoved;
    }

    int aborts() {
        return aborts;
    }

    int pillarJumps() {
        return pillarJumps;
    }

    /**
     * Whether to hold back clicks on the layer at world height {@code layerY} while the feet are below it. From
     * the ground the outer ring of a solid layer is in reach first, and placing it walls off the step that leads
     * onto the layer below, stranding the player under the rest. Climbing up first keeps that step. Once the
     * walker found no spot level with the layer, building from below is all that is left.
     */
    boolean climbBeforePlacing(LocalPlayer player, int layerY) {
        return feetY(player) < layerY && lowGroundLayer != layerY;
    }

    /**
     * A pillar jump is in the air: its support click is due within a few ticks and nothing else may take the
     * click or the rotation, or the block is missed and the jump wasted.
     */
    boolean midPillarJump() {
        return mode == Mode.PILLARING && pillar != null && pillarBaseY != Integer.MIN_VALUE;
    }

    /** Forget the route and stop driving the keys; support blocks already placed stay tracked. */
    void reset() {
        stopMining();
        mode = Mode.IDLE;
        follower.stop();
        cancelPlan();
        hasGoal = false;
        spot = null;
        pillar = null;
        pillarBaseY = Integer.MIN_VALUE;
        controlling = false;
        holdStill = false;
        jumpThisTick = false;
        replanWait = 0;
        abortCooldown = 0;
        rejected.clear();
        warnedUnreachable = false;
        lowGroundLayer = Integer.MIN_VALUE;
        cache.clear();
        cacheAge = 0;
        previousTargets = new long[0];
    }

    /**
     * Module switched off (or the world changed): the counters go, and so does the route. Support blocks stay
     * tracked while the world is the same, so switching the module off and on again can still break them; blocks
     * of another world are forgotten.
     */
    void clearAll() {
        reset();
        ClientLevel level = helperLevel.get();
        if (level == null || level != mc.level) helpers.clear();
        routes = 0;
        routeCells = 0;
        walked = 0.0;
        pillarBlocks = 0;
        helpersRemoved = 0;
        aborts = 0;
        pillarJumps = 0;
        replans = 0;
        planTicks = 0;
        emptyWaits = 0;
        unreachableWaits = 0;
        cpuMaxNanos = 0L;
        lastPosition = null;
    }

    /** A pause condition holds (screen, item use, low health, no material): let go of the keys. */
    void pause() {
        clicking = false;
        stopMining();
        controlling = false;
        holdStill = false;
        jumpThisTick = false;
    }

    /**
     * The builder is about to click. Usually the player stands still for it. With {@code walkOn} (a plain block
     * whose state does not depend on the look or the click point) a walk already under way goes on: the block goes
     * in as the player passes, instead of a stop, a click and a fresh start for every block picked up on the way.
     */
    void holdForClick(LocalPlayer player, boolean walkOn) {
        controlling = true;
        jumpThisTick = false;
        clicking = true;
        if (walkOn && mode == Mode.WALKING && (follower.active() || replanPending)) {
            long started = System.nanoTime();
            holdStill = false;
            if (!followRoute(player, started + PLAN_BUDGET_NANOS)) {
                if (mode == Mode.WALKING) mode = Mode.IDLE;
                arrivalGrace = ARRIVAL_GRACE_TICKS;
                holdStill = true;
            }
            cpuMaxNanos = Math.max(cpuMaxNanos, System.nanoTime() - started);
            return;
        }
        holdStill = true;
    }

    /** A block went in: spots that looked useless may not be any more. */
    void onPlaced() {
        rejected.clear();
        warnedUnreachable = false;
        // A plan half way through was scored against the targets before this block: start it again when needed.
        if (mode == Mode.IDLE && stage != Stage.NONE) cancelPlan();
    }

    void onHelperPlaced(BlockPos pos, Block block) {
        helpers.put(pos.asLong(), block);
        helperLevel = new WeakReference<>(mc.level);
        pillarBlocks++;
        cache.invalidateAround(pos.getX(), pos.getY(), pos.getZ());
    }

    void trackDistance(LocalPlayer player) {
        Vec3 position = player.position();
        if (lastPosition != null && mode != Mode.IDLE) {
            walked += Math.hypot(position.x - lastPosition.x, position.z - lastPosition.z)
                    + Math.abs(position.y - lastPosition.y);
        }
        lastPosition = position;
    }

    // ------------------------------------------------------------------ placing phase

    /**
     * The builder found nothing to click from here. Walk somewhere better or climb; returns a support block
     * click when one is due this tick.
     */
    HelperClick tickBuild(LocalPlayer player) {
        long started = System.nanoTime();
        HelperClick click = tickBuildTimed(player, started + PLAN_BUDGET_NANOS);
        cpuMaxNanos = Math.max(cpuMaxNanos, System.nanoTime() - started);
        return click;
    }

    private HelperClick tickBuildTimed(LocalPlayer player, long deadline) {
        clicking = false;
        controlling = true;
        holdStill = false;
        jumpThisTick = false;
        refreshCache();
        if (mode == Mode.CLEARING) {
            mode = Mode.IDLE;
            follower.stop();
            cancelPlan();
        }
        if (abortCooldown > 0) {
            abortCooldown--;
            holdStill = true;
            return null;
        }
        if (mode == Mode.PILLARING) return tickPillar(player, deadline);
        if (mode == Mode.WALKING) {
            if (followRoute(player, deadline)) return null;
            if (mode == Mode.WALKING) mode = Mode.IDLE;
            arrivalGrace = ARRIVAL_GRACE_TICKS;
        }
        holdStill = true;
        if (arrivalGrace > 0) {
            arrivalGrace--;
            return null;
        }
        if (stage == Stage.NONE) {
            if (hasGoal && feetKey(player) == goal) {
                // Arrived and still nothing to place: this spot only looked good on the grid.
                rejected.add(goal);
                hasGoal = false;
            }
            if (replanWait > 0) {
                replanWait--;
                return null;
            }
            if (!grounded(player) || !startPlan(player, StandingSpotChooser.Mode.PLACE, host.reach() - PLAN_MARGIN)) return null;
        }
        advancePlan(player, deadline);
        return null;
    }

    /** Collects the targets and starts a flood from the feet; {@code false} when there is nothing to plan for. */
    private boolean startPlan(LocalPlayer player, StandingSpotChooser.Mode planFor, double reach) {
        if (planFor == StandingSpotChooser.Mode.PLACE) {
            targetCount = host.walkTargets(player, targets);
            reserved.clear();
            for (int i = 0; i < targetCount; i++) reserved.add(targets[i]);
            forgetPlacedTargets();
        } else {
            targetCount = 0;
            for (long key : helpers.keySet()) {
                if (targetCount == MAX_TARGETS) break;
                targets[targetCount++] = key;
            }
            reserved.clear();
        }
        if (targetCount <= 0) {
            // Nothing to walk to right now (the rest waits for a retry or for power): look again soon.
            emptyWaits++;
            replanWait = EMPTY_WAIT_TICKS;
            return false;
        }
        planMode = planFor;
        planReach = reach;
        planStart = feetKey(player);
        floodBound = Math.min(FLOOD_COST, distanceToTargets(planStart) + FLOOD_SLACK);
        startFlood();
        return true;
    }

    private void startFlood() {
        int drop = planMode == StandingSpotChooser.Mode.PLACE
                ? (helpers.isEmpty() ? MAX_DROP : SAFE_DROP)
                : CLEAR_MAX_DROP;
        floodDrop = drop;
        floodSearch.startFlood(planStart, drop, floodBound, FLOOD_NODES, reserved.isEmpty() ? null : reserved);
        stage = Stage.FLOOD;
        flood = null;
        scorer = null;
        pillarPlanner = null;
        found = null;
    }

    /** Cells that were targets last time and are not any more got a block: the cache must read them again. */
    private void forgetPlacedTargets() {
        for (long key : previousTargets) {
            if (!reserved.contains(key)) cache.invalidateAround(WalkTerrain.unpackX(key), WalkTerrain.unpackY(key), WalkTerrain.unpackZ(key));
        }
        previousTargets = Arrays.copyOf(targets, targetCount);
    }

    private void advancePlan(LocalPlayer player, long deadline) {
        if (stage != Stage.NONE) planTicks++;
        while (stage != Stage.NONE) {
            switch (stage) {
                case FLOOD -> {
                    if (floodSearch.step(deadline) == WalkPathfinder.Search.RUNNING) return;
                    flood = floodSearch.flood();
                    scorer = new StandingSpotChooser.Scorer(cache, flood, targets, targetCount, planMode, planReach,
                            rejected, planMode == StandingSpotChooser.Mode.PLACE ? host::lookAllows : null);
                    stage = Stage.SPOTS;
                }
                case SPOTS -> {
                    if (!scorer.step(deadline)) return;
                    found = scorer.result(planMode == StandingSpotChooser.Mode.PLACE ? spot : null);
                    if (found == null && floodBound < FLOOD_COST) {
                        floodBound = FLOOD_COST;
                        startFlood();
                    } else if (planMode == StandingSpotChooser.Mode.PLACE && host.pillarEnabled()
                            && (found == null || found.cost() > PILLAR_COMPARE_COST)) {
                        pillarPlanner = new PillarPlanner.Planner(cache, flood, targets, targetCount, planReach, MAX_PILLAR,
                                host::inFootprint, rejected);
                        stage = Stage.PILLAR;
                    } else {
                        finishPlan(player, null);
                    }
                }
                case PILLAR -> {
                    if (!pillarPlanner.step(deadline)) return;
                    finishPlan(player, pillarPlanner.result());
                }
                default -> stage = Stage.NONE;
            }
            if (System.nanoTime() - deadline > 0) return;
        }
    }

    private void finishPlan(LocalPlayer player, PillarPlanner.Pillar column) {
        stage = Stage.NONE;
        StandingSpotChooser.Spot next = found;
        if (planMode == StandingSpotChooser.Mode.BREAK) {
            finishClearPlan(player, next);
            return;
        }
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < targetCount; i++) lowest = Math.min(lowest, WalkTerrain.unpackY(targets[i]));
        if (next == null || next.y() < lowest) lowGroundLayer = lowest;
        boolean pillarWins = column != null
                && (next == null || PillarPlanner.rate(column) > StandingSpotChooser.rate(next.coverage(), next.cost()) * PILLAR_ADVANTAGE);
        if (next != null && !pillarWins) {
            spot = next;
            startRoute(flood.route(next.key()), next.key(), Mode.WALKING, floodDrop);
            return;
        }
        if (column != null) {
            pillar = column;
            pillarRetries = 0;
            pillarIdleTicks = 0;
            pillarProgressBlocks = pillarBlocks;
            pillarBaseY = Integer.MIN_VALUE;
            long base = WalkTerrain.pack(column.x(), column.y(), column.z());
            startRoute(flood.route(base), base, Mode.PILLARING, floodDrop);
            return;
        }
        if (!warnedUnreachable) {
            warnedUnreachable = true;
            Notifier.warning(I18n.get("module.schematicbuilder.notify.unreachable"));
        }
        unreachableWaits++;
        replanWait = REPLAN_WAIT_TICKS;
    }

    private void cancelPlan() {
        stage = Stage.NONE;
        flood = null;
        scorer = null;
        pillarPlanner = null;
        found = null;
        replanPending = false;
    }

    private double distanceToTargets(long from) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < targetCount; i++) best = Math.min(best, octile(from, targets[i]));
        return best == Double.MAX_VALUE ? 0.0 : best;
    }

    private static double octile(long a, long b) {
        double dx = Math.abs(WalkTerrain.unpackX(a) - WalkTerrain.unpackX(b));
        double dz = Math.abs(WalkTerrain.unpackZ(a) - WalkTerrain.unpackZ(b));
        return Math.max(dx, dz) + 0.4142 * Math.min(dx, dz) + Math.abs(WalkTerrain.unpackY(a) - WalkTerrain.unpackY(b));
    }

    private void startRoute(long[] path, long target, Mode next, int drop) {
        if (path == null) {
            rejected.add(target);
            return;
        }
        goalReplans = 0;
        routeDrop = drop;
        follower.start(cache, path, drop);
        goal = target;
        hasGoal = true;
        mode = next;
        routes++;
        routeCells += path.length - 1;
    }

    // ------------------------------------------------------------------ route following

    /** Advances along the route; {@code false} once the last cell is reached (or the route was dropped). */
    private boolean followRoute(LocalPlayer player, long deadline) {
        if (replanPending) return advanceReplan(player, deadline);
        if (!follower.active()) return false;
        Vec3 pos = player.position();
        Vec3 motion = player.getDeltaMovement();
        RouteFollower.Action action = follower.tick(live, pos.x, pos.y, pos.z, motion.x, motion.z, grounded(player),
                player.horizontalCollision, host.stuckTimeout());
        switch (action) {
            case FOLLOW -> {
                return true;
            }
            case REPLAN -> {
                startReplan(player);
                return replanPending ? advanceReplan(player, deadline) : follower.active();
            }
            case ABORT -> {
                abort();
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private void startReplan(LocalPlayer player) {
        if (!grounded(player)) return;
        // Whatever made the route wrong may have changed blocks around the player: read them afresh.
        Vec3 pos = player.position();
        int fx = (int) Math.floor(pos.x), fy = feetY(player), fz = (int) Math.floor(pos.z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) cache.invalidate(fx + dx, fy + dy, fz + dz);
            }
        }
        long blocked = follower.blockedCell();
        if (blocked != Long.MIN_VALUE) {
            // The cache still has the cell as it was when the route was planned; without this the new route
            // would run into the same block.
            int bx = WalkTerrain.unpackX(blocked), by = WalkTerrain.unpackY(blocked), bz = WalkTerrain.unpackZ(blocked);
            for (int dy = -1; dy <= 2; dy++) cache.invalidateAround(bx, by + dy, bz);
        }
        if (++goalReplans > MAX_GOAL_REPLANS) {
            // The way there keeps breaking: pick another spot instead.
            rejected.add(goal);
            follower.stop();
            hasGoal = false;
            mode = Mode.IDLE;
            return;
        }
        routeSearch.startPath(feetKey(player), goal, routeDrop, PATH_NODES, reserved.isEmpty() ? null : reserved);
        replanPending = true;
        replans++;
    }

    private boolean advanceReplan(LocalPlayer player, long deadline) {
        int status = routeSearch.step(deadline);
        if (status == WalkPathfinder.Search.RUNNING) {
            holdStill = true;
            return true;
        }
        replanPending = false;
        long[] path = routeSearch.path();
        if (path == null) {
            rejected.add(goal);
            follower.stop();
            hasGoal = false;
            mode = Mode.IDLE;
            return false;
        }
        follower.restart(cache, path);
        routeCells += path.length - 1;
        return true;
    }

    private void abort() {
        aborts++;
        Notifier.warning(I18n.get("module.schematicbuilder.notify.stuck"));
        if (hasGoal) rejected.add(goal);
        follower.stop();
        cancelPlan();
        hasGoal = false;
        pillar = null;
        mode = Mode.IDLE;
        abortCooldown = ABORT_COOLDOWN_TICKS;
    }

    // ------------------------------------------------------------------ pillar

    private HelperClick tickPillar(LocalPlayer player, long deadline) {
        PillarPlanner.Pillar column = pillar;
        if (column == null) {
            mode = Mode.IDLE;
            return null;
        }
        if ((follower.active() || replanPending) && followRoute(player, deadline)) return null;
        if (follower.active() || mode != Mode.PILLARING) return null;
        if (pillarBlocks != pillarProgressBlocks) {
            pillarProgressBlocks = pillarBlocks;
            pillarIdleTicks = 0;
        } else if (++pillarIdleTicks > PILLAR_STALL_TICKS) {
            // Standing at the column without a block going in for this long: it will not, try somewhere else.
            pillarIdleTicks = 0;
            rejected.add(WalkTerrain.pack(column.x(), column.y(), column.z()));
            abort();
            return null;
        }
        int feetY = feetY(player);
        if (feetY >= column.topY() && player.onGround()) {
            finishPillar();
            return null;
        }
        int slot = helperSlot(player);
        if (slot < 0) {
            Notifier.warning(I18n.get("module.schematicbuilder.notify.no_helper"));
            finishPillar();
            abortCooldown = ABORT_COOLDOWN_TICKS;
            return null;
        }
        Vec3 pos = player.position();
        double off = Math.hypot(pos.x - (column.x() + 0.5), pos.z - (column.z() + 0.5));
        if (player.onGround()) {
            return pillarOnGround(player, column, feetY, off);
        }
        return pillarInAir(player, column, slot);
    }

    private HelperClick pillarOnGround(LocalPlayer player, PillarPlanner.Pillar column, int feetY, double off) {
        if (pillarBaseY != Integer.MIN_VALUE && feetY <= pillarBaseY && ++pillarRetries > MAX_PILLAR_RETRIES) {
            // Landed back where the jump started too often: the placement keeps missing.
            rejected.add(WalkTerrain.pack(column.x(), column.y(), column.z()));
            abort();
            return null;
        }
        pillarBaseY = Integer.MIN_VALUE;
        if (off > 0.5 && live.solid(column.x(), feetY, column.z())) {
            // A support block went in but the body ended up beside the column, not on it: the column cell is no
            // longer free to centre in. Leave it; the next plan sees the block as ground like any other.
            rejected.add(WalkTerrain.pack(column.x(), column.y(), column.z()));
            finishPillar();
            return null;
        }
        Vec3 down = new Vec3(column.x() + 0.5, feetY, column.z() + 0.5);
        Rotation look = Rotation.lookingAt(down, player.getEyePosition());
        host.aim(look);
        if (off > CENTER_DISTANCE) return null;
        Rotation server = RotationManager.INSTANCE.getServerRotation();
        Rotation current = RotationManager.INSTANCE.getCurrentRotation();
        if (server == null || current == null || server.pitch() < PILLAR_PITCH_READY || current.pitch() < PILLAR_PITCH_READY) {
            return null;
        }
        if (Math.abs(player.getDeltaMovement().x) + Math.abs(player.getDeltaMovement().z) > 0.03) return null;
        jumpThisTick = true;
        pillarJumps++;
        pillarBaseY = feetY;
        return null;
    }

    private HelperClick pillarInAir(LocalPlayer player, PillarPlanner.Pillar column, int slot) {
        if (pillarBaseY == Integer.MIN_VALUE) return null;
        BlockPos target = new BlockPos(column.x(), pillarBaseY, column.z());
        Vec3 down = new Vec3(column.x() + 0.5, pillarBaseY, column.z() + 0.5);
        host.aim(Rotation.lookingAt(down, player.getEyePosition()));
        // The click is only legal once the feet have cleared the cell the new block goes into.
        if (player.getY() < pillarBaseY + 1.0 || !mc.level.getBlockState(target).canBeReplaced()) return null;
        ItemStack stack = player.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        BlockPos support = target.below();
        if (mc.level.getBlockState(support).canBeReplaced()) return null;
        Rotation rotation = Rotation.lookingAt(down, player.getEyePosition());
        double distanceSq = player.getEyePosition().distanceToSqr(down);
        BuildPlanner.Plan plan = new BuildPlanner.Plan(target, support, Direction.UP, down, rotation, false,
                distanceSq, false);
        return new HelperClick(plan, slot, blockItem.getBlock().defaultBlockState());
    }

    private void finishPillar() {
        pillar = null;
        pillarBaseY = Integer.MIN_VALUE;
        hasGoal = false;
        spot = null;
        mode = Mode.IDLE;
    }

    /** Hotbar slot of the support block: a listed helper (softest first), else any plain full block the build can spare. */
    int helperSlot(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        Set<String> ids = host.helperIds();
        int best = -1;
        float bestHardness = Float.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof BlockItem blockItem) || !ids.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                    || !host.canSpare(blockItem.getBlock())) continue;
            float hardness = blockItem.getBlock().defaultDestroyTime();
            if (isPlainFullBlock(blockItem.getBlock()) && hardness < bestHardness) {
                bestHardness = hardness;
                best = i;
            }
        }
        if (best >= 0) return best;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof BlockItem blockItem && isPlainFullBlock(blockItem.getBlock())
                    && host.canSpare(blockItem.getBlock())) return i;
        }
        return -1;
    }

    private static boolean isPlainFullBlock(Block block) {
        if (block instanceof FallingBlock || block.defaultDestroyTime() < 0.0f) return false;
        BlockState state = block.defaultBlockState();
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !BuildPlanner.isInteractive(state);
    }

    // ------------------------------------------------------------------ clearing support blocks

    /** The build is done: break the support blocks again. {@code false} once there is nothing left to do. */
    boolean tickClear(LocalPlayer player, double reach) {
        long started = System.nanoTime();
        boolean busy = tickClearTimed(player, reach, started + PLAN_BUDGET_NANOS);
        cpuMaxNanos = Math.max(cpuMaxNanos, System.nanoTime() - started);
        return busy;
    }

    private boolean tickClearTimed(LocalPlayer player, double reach, long deadline) {
        clicking = false;
        controlling = true;
        holdStill = true;
        jumpThisTick = false;
        refreshCache();
        prunePlacedHelpers();
        if (helpers.isEmpty()) {
            stopMining();
            if (mode != Mode.IDLE) Notifier.info(I18n.get("module.schematicbuilder.notify.helpers_removed"));
            mode = Mode.IDLE;
            follower.stop();
            cancelPlan();
            controlling = false;
            return false;
        }
        if (mode != Mode.CLEARING) {
            mode = Mode.CLEARING;
            follower.stop();
            cancelPlan();
            hasGoal = false;
            rejected.clear();
        }
        if (abortCooldown > 0) {
            abortCooldown--;
            return true;
        }
        if (follower.active() || replanPending) {
            stopMining();
            holdStill = false;
            if (followRoute(player, deadline)) return true;
            holdStill = true;
            mode = Mode.CLEARING;
        }
        if (stage != Stage.NONE) {
            advancePlan(player, deadline);
            return true;
        }
        if (mineReachable(player, reach)) return true;
        if (!grounded(player)) return true;
        if (replanWait > 0) {
            replanWait--;
            return true;
        }
        if (startPlan(player, StandingSpotChooser.Mode.BREAK, reach - PLAN_MARGIN)) advancePlan(player, deadline);
        return true;
    }

    private void finishClearPlan(LocalPlayer player, StandingSpotChooser.Spot next) {
        if (next == null) {
            Notifier.warning(I18n.get("module.schematicbuilder.notify.helpers_left", helpers.size()));
            helpers.clear();
            return;
        }
        if (next.key() == feetKey(player)) {
            rejected.add(next.key());
            replanWait = ARRIVAL_GRACE_TICKS;
            return;
        }
        startRoute(flood.route(next.key()), next.key(), Mode.CLEARING, CLEAR_MAX_DROP);
    }

    private void prunePlacedHelpers() {
        Iterator<Map.Entry<Long, Block>> it = helpers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Block> entry = it.next();
            long key = entry.getKey();
            cursor.set(WalkTerrain.unpackX(key), WalkTerrain.unpackY(key), WalkTerrain.unpackZ(key));
            if (!mc.level.isLoaded(cursor)) continue;
            if (mc.level.getBlockState(cursor).getBlock() != entry.getValue()) {
                it.remove();
                cache.invalidateAround(cursor.getX(), cursor.getY(), cursor.getZ());
                if (mode == Mode.CLEARING) helpersRemoved++;
                if (mining != null && mining.asLong() == key) mining = null;
            }
        }
    }

    private boolean mineReachable(LocalPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        long best = 0L;
        int bestFace = -1;
        int bestY = Integer.MIN_VALUE;
        for (long key : helpers.keySet()) {
            int x = WalkTerrain.unpackX(key), y = WalkTerrain.unpackY(key), z = WalkTerrain.unpackZ(key);
            if (y <= bestY) continue;
            int face = ReachModel.breakFace(live, eye.x, eye.y, eye.z, x, y, z, reach);
            if (face < 0) continue;
            best = key;
            bestFace = face;
            bestY = y;
        }
        if (bestFace < 0) {
            stopMining();
            return false;
        }
        mine(player, BlockPos.of(best), bestFace);
        return true;
    }

    private void mine(LocalPlayer player, BlockPos pos, int faceIndex) {
        int[] n = ReachModel.faceNormal(faceIndex);
        if (mining != null && !mining.equals(pos)) stopMining();
        Vec3 eye = player.getEyePosition();
        Vec3 point = new Vec3(
                n[0] != 0 ? pos.getX() + 0.5 + n[0] * 0.5 : clamp(eye.x, pos.getX() + 0.2, pos.getX() + 0.8),
                n[1] != 0 ? pos.getY() + 0.5 + n[1] * 0.5 : clamp(eye.y, pos.getY() + 0.2, pos.getY() + 0.8),
                n[2] != 0 ? pos.getZ() + 0.5 + n[2] * 0.5 : clamp(eye.z, pos.getZ() + 0.2, pos.getZ() + 0.8));
        host.aim(Rotation.lookingAt(point, eye));
        BlockHitResult hit = BuildPlanner.trace(player, RotationManager.INSTANCE.getCurrentRotation(), host.reach() + 0.5);
        if (hit == null || !hit.getBlockPos().equals(pos)) return;
        if (mining == null) selectTool(player, mc.level.getBlockState(pos));
        mining = pos;
        miningFace = hit.getDirection();
        if (mc.gameMode.continueDestroyBlock(pos, miningFace)) player.swing(InteractionHand.MAIN_HAND);
    }

    private void selectTool(LocalPlayer player, BlockState state) {
        host.releaseHotbar();
        Inventory inventory = player.getInventory();
        int best = inventory.getSelectedSlot();
        float bestSpeed = inventory.getItem(best).getDestroySpeed(state);
        for (int i = 0; i < 9; i++) {
            float speed = inventory.getItem(i).getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        if (best != inventory.getSelectedSlot()) InventorySwap.INSTANCE.selectHotbar(best);
    }

    private void stopMining() {
        if (mining != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        mining = null;
        miningFace = null;
    }

    // ------------------------------------------------------------------ input

    /** Drives the keys while the mover is in charge; leaves the player's own input alone otherwise. */
    void applyInput(MovementInputEvent event, LocalPlayer player) {
        if (!controlling) return;
        int keys = 0;
        boolean jump = false;
        boolean descending = false;
        boolean sprint = false;
        if (!holdStill && follower.active() && !replanPending) {
            Vec3 pos = player.position();
            if (!follower.coast()) {
                keys = keysToward(RotationManager.INSTANCE.getMovementRotation().yaw(), follower.aimX() - pos.x,
                        follower.aimZ() - pos.z);
            }
            jump = follower.jump() && player.onGround() || follower.climb();
            descending = follower.descending();
            // Never sprint toward a click: the rule is no sprint while placing.
            sprint = !clicking && follower.sprint() && keys == Steering.FORWARD && !jump && canSprint(player);
        } else if (mode == Mode.PILLARING && pillar != null && !follower.active() && player.onGround()) {
            // Centre on the column before the jump; the pillar is one block wide.
            Vec3 pos = player.position();
            double dx = pillar.x() + 0.5 - pos.x;
            double dz = pillar.z() + 0.5 - pos.z;
            if (Math.hypot(dx, dz) > CENTER_DISTANCE * 0.5) {
                keys = Steering.keysToward(RotationManager.INSTANCE.getMovementRotation().yaw(), dx, dz);
            }
        }
        if (jumpThisTick && player.onGround()) jump = true;
        if (!sprint && player.isSprinting()) player.setSprinting(false);
        event.setForward((keys & Steering.FORWARD) != 0);
        event.setBackward((keys & Steering.BACKWARD) != 0);
        event.setLeft((keys & Steering.LEFT) != 0);
        event.setRight((keys & Steering.RIGHT) != 0);
        event.setJump(jump);
        event.setSprint(sprint);
        event.setSneak(!descending && !sprint && player.onGround() && aboutToFall(player));
        jumpThisTick = false;
    }

    /** Vanilla's own sprint conditions, checked before the sprint key is pressed at all. */
    private static boolean canSprint(LocalPlayer player) {
        return (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly)
                && !player.isInWater() && !player.isUsingItem() && !player.isShiftKeyDown();
    }

    /**
     * Movement keys toward an offset. Far away only a clear sideways offset adds a strafe key; close to the target
     * a small one does too, so the body lines up with a one-wide gap instead of rubbing along its corner.
     */
    static int keysToward(float yawDegrees, double dx, double dz) {
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) return 0;
        double yaw = Math.toRadians(yawDegrees);
        double forward = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
        double left = Math.cos(yaw) * dx + Math.sin(yaw) * dz;
        double threshold = Math.min(0.08 + 0.1 * length, 0.38 * length);
        int keys = 0;
        if (forward > threshold) keys |= Steering.FORWARD;
        else if (forward < -threshold) keys |= Steering.BACKWARD;
        if (left > threshold) keys |= Steering.LEFT;
        else if (left < -threshold) keys |= Steering.RIGHT;
        return keys;
    }

    /**
     * Where the feet will be next tick has no floor under any part of the hitbox: one more step falls. Walking
     * along an edge with only a corner over air is safe and stays at full speed; the sneak only comes when the
     * whole body is about to leave the floor, and vanilla's sneak edge clamp then holds it on.
     */
    private boolean aboutToFall(LocalPlayer player) {
        ClientLevel level = mc.level;
        Vec3 motion = player.getDeltaMovement();
        AABB box = player.getBoundingBox();
        double shiftX = motion.x * 1.5, shiftZ = motion.z * 1.5;
        double minX = box.minX + shiftX + 0.02, maxX = box.maxX + shiftX - 0.02;
        double minZ = box.minZ + shiftZ + 0.02, maxZ = box.maxZ + shiftZ - 0.02;
        int y = (int) Math.floor(box.minY - 0.5);
        for (int i = 0; i < 3; i++) {
            double x = i == 0 ? minX : i == 1 ? (minX + maxX) * 0.5 : maxX;
            for (int j = 0; j < 3; j++) {
                double z = j == 0 ? minZ : j == 1 ? (minZ + maxZ) * 0.5 : maxZ;
                cursor.set((int) Math.floor(x), y, (int) Math.floor(z));
                if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) return false;
            }
        }
        return true;
    }

    /** On the ground, or swimming: the two states in which the walker can plan and steer. */
    private static boolean grounded(LocalPlayer player) {
        return player.onGround() || player.isInWater();
    }

    // ------------------------------------------------------------------ world view

    /** Starts the cache afresh when the world changed or it got old; targets placed since are dropped per cell. */
    private void refreshCache() {
        ClientLevel level = mc.level;
        if (level != cacheLevel || ++cacheAge >= CACHE_REFRESH_TICKS && stage == Stage.NONE && !replanPending
                || cache.sectionCount() > CACHE_MAX_SECTIONS) {
            cacheLevel = level;
            cacheAge = 0;
            cache.clear();
        } else if (cacheAge % CACHE_REFILL_TICKS == 0 && stage == Stage.NONE && !replanPending) {
            // Chunks that were missing when their section was read may have arrived since.
            cache.refillIncomplete();
        }
    }

    private byte classifyLive(int x, int y, int z) {
        ClientLevel level = mc.level;
        probe.set(x, y, z);
        if (y < level.getMinY() || y >= level.getMaxY() || !level.isLoaded(probe)) return WalkTerrain.UNKNOWN;
        return classifyState(level, probe, level.getBlockState(probe));
    }

    private byte classifyState(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock().hasDynamicShape()) return classify(level, pos, state);
        Byte known = stateClasses.get(state);
        if (known != null) return known;
        byte value = classify(level, pos, state);
        stateClasses.put(state, value);
        return value;
    }

    /** Fills the cache a whole chunk section at a time, straight from the section's palette. */
    private final class WorldSections implements TerrainCache.SectionSource {
        private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

        @Override
        public byte cell(int x, int y, int z) {
            return classifyLive(x, y, z);
        }

        @Override
        public boolean fillSection(int sectionX, int sectionY, int sectionZ, byte[] out) {
            ClientLevel level = mc.level;
            int index = level.getSectionIndexFromSectionY(sectionY);
            if (index < 0 || index >= level.getSectionsCount()) {
                java.util.Arrays.fill(out, WalkTerrain.UNKNOWN);
                return true;
            }
            if (!level.hasChunk(sectionX, sectionZ)) {
                java.util.Arrays.fill(out, WalkTerrain.UNKNOWN);
                return false;
            }
            LevelChunk chunk = level.getChunk(sectionX, sectionZ);
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir()) {
                java.util.Arrays.fill(out, WalkTerrain.OPEN);
                return true;
            }
            int baseX = sectionX << 4, baseY = sectionY << 4, baseZ = sectionZ << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        at.set(baseX + x, baseY + y, baseZ + z);
                        out[y << 8 | z << 4 | x] = classifyState(level, at, state);
                    }
                }
            }
            return true;
        }
    }

    static byte classify(ClientLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY() || !level.isLoaded(pos)) return WalkTerrain.UNKNOWN;
        return classify(level, pos, level.getBlockState(pos));
    }

    private static byte classify(ClientLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (isDangerous(block)) return WalkTerrain.DANGER;
        FluidState fluid = state.getFluidState();
        boolean water = !fluid.isEmpty();
        if (water && !fluid.is(FluidTags.WATER)) return WalkTerrain.DANGER;
        if (block == Blocks.COBWEB) return WalkTerrain.WEB;
        if (state.is(BlockTags.CLIMBABLE)) return WalkTerrain.CLIMB;
        if (block instanceof DoorBlock) return state.getValue(DoorBlock.OPEN) ? WalkTerrain.NARROW : WalkTerrain.SOLID;
        if (block instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN)) return WalkTerrain.NARROW;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return water ? WalkTerrain.WATER : WalkTerrain.OPEN;
        double top = shape.max(Direction.Axis.Y);
        if (top > 1.0) return WalkTerrain.UNKNOWN;
        if (top < 0.2) return water ? WalkTerrain.WATER : WalkTerrain.OPEN;
        // The feet cell is floor(y + 0.5): floors under half a block keep the feet in this cell, the rest lift
        // them into the cell above.
        if (top < 0.5 - 1.0e-6) return WalkTerrain.RAISED;
        if (top <= 0.5 + 1.0e-6) return WalkTerrain.STEP;
        if (block.getSpeedFactor() < 1.0f) return WalkTerrain.SLOW;
        if (block.getFriction() > 0.6f) return WalkTerrain.ICE;
        return WalkTerrain.SOLID;
    }

    private static boolean isDangerous(Block block) {
        return block instanceof BaseFireBlock
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CACTUS
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.POWDER_SNOW
                || block == Blocks.WITHER_ROSE
                || block == Blocks.POINTED_DRIPSTONE;
    }

    private static int feetY(LocalPlayer player) {
        // Standing on a slab puts the feet half way into a cell; round so the cell below is the slab.
        return (int) Math.floor(player.getY() + 0.5);
    }

    private static long feetKey(LocalPlayer player) {
        return WalkTerrain.pack((int) Math.floor(player.getX()), feetY(player), (int) Math.floor(player.getZ()));
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /** Debug view for the smoke driver: mode, route progress and the current goal. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        parts.add("mode=" + mode);
        long[] waypoints = follower.active() ? follower.waypoints() : null;
        if (waypoints != null) parts.add("route=" + follower.index() + "/" + waypoints.length);
        if (stage != Stage.NONE) parts.add("plan=" + stage);
        if (hasGoal) parts.add("goal=" + BlockPos.of(goal).toShortString());
        if (pillar != null) parts.add("pillar=" + pillar);
        parts.add("helpers=" + helpers.size());
        if (!rejected.isEmpty()) parts.add("rejected=" + rejected.size());
        if (abortCooldown > 0 || replanWait > 0) parts.add("wait=" + abortCooldown + "/" + replanWait);
        parts.add("replans=" + replans);
        parts.add("planTicks=" + planTicks);
        parts.add("waits=" + emptyWaits + "/" + unreachableWaits);
        parts.add("cpuMaxUs=" + cpuMaxNanos / 1000L);
        parts.add("cacheMiss=" + cache.misses() + "/" + cache.lookups());
        return String.join(" ", parts);
    }
}
