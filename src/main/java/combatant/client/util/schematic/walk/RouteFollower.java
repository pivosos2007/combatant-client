/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

/**
 * Turns a route into a steering decision once per tick: where to aim, when to jump, whether sprinting or coasting
 * is safe, and when the route no longer fits the player's real position and has to be planned again.
 *
 * <p>Game free: it reads the player's position and velocity as numbers and the world through a
 * {@link WalkTerrain}, so the decisions can be tested on grid worlds. The caller turns the aim point into movement
 * keys.</p>
 *
 * <p>Steering looks ahead along the smoothed route for the furthest waypoint on the current level that the hitbox
 * can reach in a straight line, so corners are cut and turns start before the corner instead of at it. Near the
 * goal it lets go of the keys as soon as the player's momentum alone carries it there. Progress is measured as the
 * route length still ahead; when it stops shrinking, or the feet keep returning to the same few cells, the
 * follower tries escapes (re-centre, jump, back off, side-step) before asking for a new route, and gives up after
 * a few of those.</p>
 */
public final class RouteFollower {

    public enum Action {
        /** Keep steering at {@link #aimX()}, {@link #aimZ()}. */
        FOLLOW,
        /** The last waypoint is reached. */
        ARRIVED,
        /** The route no longer fits: plan again from where the player actually is. */
        REPLAN,
        /** Stuck for good. */
        ABORT
    }

    /** Body half width used for line-of-sight steering: exactly the player's, plus a hair for rounding. */
    static final double STEER_HALF_WIDTH = 0.31;
    static final double ARRIVE_DISTANCE = 0.3;
    static final double PASS_DISTANCE = 0.45;
    /** Aim at most this far ahead; further points make the steering cut corners too eagerly. */
    static final double LOOKAHEAD_DISTANCE = 6.0;
    static final int LOOKAHEAD_WAYPOINTS = 6;
    /** Start turning toward the waypoint after the aim point when this close to it. */
    static final double TURN_DISTANCE = 1.0;
    /** Off the route by more than this (on the ground) means the player was pushed or slipped: replan. */
    static final double OFF_ROUTE_DISTANCE = 1.6;
    /** Gap between the body's front and the face of the block to jump onto when the jump goes off. */
    static final double JUMP_LEAD = 0.55;
    /** For a jump over a gap: how close the body centre gets to the edge before taking off. */
    static final double GAP_TAKE_OFF = 0.2;
    /** Only sprint when the route ahead is level and free of turns for this long. */
    static final double SPRINT_DISTANCE = 3.5;
    /** Ground friction factor per tick on normal blocks (slipperiness 0.6 times 0.91) and on ice (0.98 times 0.91). */
    static final double GROUND_FRICTION = 0.546;
    static final double ICE_FRICTION = 0.8918;
    /** Progress counts once the route ahead got this much shorter. */
    static final double PROGRESS_STEP = 0.2;
    static final int RECENTER_TICKS = 8;
    static final int JUMP_ESCAPE_TICKS = 16;
    static final int BACK_OFF_TICKS = 24;
    static final int SIDESTEP_TICKS = 32;
    static final int REPLAN_TICKS = 40;
    static final int MAX_STUCK_REPLANS = 3;
    /** Feet cell samples kept to spot a loop, one every {@link #LOOP_INTERVAL} ticks. */
    static final int LOOP_SAMPLES = 16;
    static final int LOOP_INTERVAL = 5;
    static final int LOOP_REPEATS = 4;

    public static final int ESCAPE_NONE = 0;
    public static final int ESCAPE_RECENTER = 1;
    public static final int ESCAPE_JUMP = 2;
    public static final int ESCAPE_BACK_OFF = 3;
    public static final int ESCAPE_SIDESTEP = 4;

    private long[] raw;
    private long[] waypoints;
    /** Index into {@link #raw} of each waypoint. */
    private int[] rawIndex;
    private int index;
    private int maxDrop;
    private boolean active;

    private double aimX;
    private double aimZ;
    private boolean jump;
    private boolean climb;
    private boolean coast;
    private boolean sprint;
    private boolean descending;
    private boolean gapJump;
    private int escape;

    private double bestRemaining;
    private int stallTicks;
    private int noProgressTicks;
    private int stuckReplans;
    private final long[] loop = new long[LOOP_SAMPLES];
    private int loopCount;
    private int tick;
    private long blockedCell = Long.MIN_VALUE;

    /** Starts following {@code path} (feet cells from the player's cell to the goal). */
    public void start(WalkTerrain terrain, long[] path, int maxDrop) {
        this.raw = path;
        this.maxDrop = maxDrop;
        this.waypoints = PathSmoother.smooth(terrain, path, maxDrop);
        this.rawIndex = new int[waypoints.length];
        for (int i = 0, r = 0; i < waypoints.length; i++) {
            while (raw[r] != waypoints[i]) r++;
            rawIndex[i] = r;
        }
        this.index = waypoints.length > 1 ? 1 : 0;
        this.active = true;
        this.bestRemaining = Double.MAX_VALUE;
        this.stallTicks = 0;
        this.noProgressTicks = 0;
        this.loopCount = 0;
        this.escape = ESCAPE_NONE;
    }

    /** A fresh route for the same goal after a replan; keeps the count of stuck replans. */
    public void restart(WalkTerrain terrain, long[] path) {
        int replans = stuckReplans;
        start(terrain, path, maxDrop);
        stuckReplans = replans;
    }

    public void stop() {
        active = false;
        raw = null;
        waypoints = null;
        stuckReplans = 0;
    }

    public boolean active() {
        return active;
    }

    public long goal() {
        return raw[raw.length - 1];
    }

    public long[] waypoints() {
        return waypoints;
    }

    public int index() {
        return index;
    }

    public double aimX() {
        return aimX;
    }

    public double aimZ() {
        return aimZ;
    }

    /** Press jump this tick (a step up, a gap, an escape). */
    public boolean jump() {
        return jump;
    }

    /** Hold jump to climb a ladder or swim up out of water. */
    public boolean climb() {
        return climb;
    }

    /** Let go of the keys: momentum carries the player onto the goal. */
    public boolean coast() {
        return coast;
    }

    /** The route ahead is long and level enough to sprint. */
    public boolean sprint() {
        return sprint;
    }

    /** Heading for a lower cell or over a gap: do not sneak at the edge. */
    public boolean descending() {
        return descending || gapJump;
    }

    public int escape() {
        return escape;
    }

    /**
     * One tick of following.
     *
     * @param terrain            the world as it is now (route cells are re-checked against it)
     * @param horizontalCollision the player ran into a wall last tick
     * @param stuckTimeout       ticks without progress after which the route is given up
     */
    public Action tick(WalkTerrain terrain, double x, double y, double z, double vx, double vz, boolean onGround,
                       boolean horizontalCollision, int stuckTimeout) {
        if (!active) return Action.ARRIVED;
        tick++;
        jump = false;
        climb = false;
        coast = false;
        sprint = false;
        descending = false;
        gapJump = false;
        advance(x, y, z, onGround);
        if (index >= waypoints.length) {
            stop();
            return Action.ARRIVED;
        }
        blockedCell = Long.MIN_VALUE;
        if (onGround && (offRoute(x, y, z) || blocked(terrain, x, z))) return Action.REPLAN;
        Action stuck = trackProgress(x, y, z, onGround, stuckTimeout);
        if (stuck != Action.FOLLOW) return stuck;
        steer(terrain, x, y, z, vx, vz, onGround, horizontalCollision);
        return Action.FOLLOW;
    }

    private void advance(double x, double y, double z, boolean onGround) {
        // Passing any of the next few waypoints counts: a cut corner skips the ones before it.
        for (int k = Math.min(waypoints.length - 1, index + LOOKAHEAD_WAYPOINTS); k >= index; k--) {
            long cell = waypoints[k];
            boolean last = k == waypoints.length - 1;
            if (Math.abs(y - WalkTerrain.unpackY(cell)) >= 0.6) continue;
            if (horizontal(x, z, cell) > (last ? ARRIVE_DISTANCE : PASS_DISTANCE) || last && !onGround) continue;
            index = k + 1;
            return;
        }
    }

    /** The player is nowhere near the segment it is supposed to walk, or on another level entirely. */
    private boolean offRoute(double x, double y, double z) {
        long from = waypoints[Math.max(0, index - 1)];
        long to = waypoints[index];
        int feet = (int) Math.floor(y + 0.5);
        int low = Math.min(WalkTerrain.unpackY(from), WalkTerrain.unpackY(to));
        int high = Math.max(WalkTerrain.unpackY(from), WalkTerrain.unpackY(to));
        if (feet < low - 1 || feet > high + 1) return true;
        return segmentDistance(x, z, from, to) > OFF_ROUTE_DISTANCE;
    }

    /** A route cell just behind or ahead of the player stopped being walkable (a block went in, a door shut). */
    private boolean blocked(WalkTerrain terrain, double x, double z) {
        // Only the cells ahead count: a block the builder put into a cell already passed is no obstacle.
        int from = rawIndex[Math.max(0, index - 1)];
        int nearest = from;
        double best = Double.MAX_VALUE;
        for (int i = from; i <= rawIndex[index]; i++) {
            double d = horizontal(x, z, raw[i]);
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        int to = Math.min(raw.length - 1, nearest + 3);
        for (int i = nearest + 1; i <= to; i++) {
            long cell = raw[i];
            if (!terrain.standable(WalkTerrain.unpackX(cell), WalkTerrain.unpackY(cell), WalkTerrain.unpackZ(cell))) {
                blockedCell = cell;
                return true;
            }
        }
        return false;
    }

    /** The route cell found blocked by the last {@link Action#REPLAN}, or {@link Long#MIN_VALUE}. */
    public long blockedCell() {
        return blockedCell;
    }

    private Action trackProgress(double x, double y, double z, boolean onGround, int stuckTimeout) {
        double remaining = remaining(x, z);
        if (remaining < bestRemaining - PROGRESS_STEP) {
            bestRemaining = remaining;
            stallTicks = 0;
            noProgressTicks = 0;
            escape = ESCAPE_NONE;
        } else {
            stallTicks++;
            noProgressTicks++;
        }
        if (tick % LOOP_INTERVAL == 0) {
            long feet = WalkTerrain.pack((int) Math.floor(x), (int) Math.floor(y + 0.5), (int) Math.floor(z));
            int repeats = 0;
            for (int i = 0; i < Math.min(loopCount, LOOP_SAMPLES); i++) if (loop[i] == feet) repeats++;
            loop[loopCount++ % LOOP_SAMPLES] = feet;
            // Back in the same cell again and again while the route ahead does not shrink: going in circles.
            if (repeats >= LOOP_REPEATS && stallTicks > 0 && stallTicks < BACK_OFF_TICKS) stallTicks = BACK_OFF_TICKS;
        }
        if (noProgressTicks > stuckTimeout) return Action.ABORT;
        if (stallTicks >= REPLAN_TICKS && onGround) {
            stallTicks = 0;
            bestRemaining = Double.MAX_VALUE;
            loopCount = 0;
            escape = ESCAPE_NONE;
            return ++stuckReplans > MAX_STUCK_REPLANS ? Action.ABORT : Action.REPLAN;
        }
        if (stallTicks >= SIDESTEP_TICKS) escape = ESCAPE_SIDESTEP;
        else if (stallTicks >= BACK_OFF_TICKS) escape = ESCAPE_BACK_OFF;
        else if (stallTicks >= JUMP_ESCAPE_TICKS) escape = ESCAPE_JUMP;
        else if (stallTicks >= RECENTER_TICKS) escape = ESCAPE_RECENTER;
        return Action.FOLLOW;
    }

    /** Route length still ahead: to the next waypoint, then along the rest. */
    public double remaining(double x, double z) {
        double total = horizontal(x, z, waypoints[index]);
        for (int i = index + 1; i < waypoints.length; i++) total += distance(waypoints[i - 1], waypoints[i]);
        return total;
    }

    private void steer(WalkTerrain terrain, double x, double y, double z, double vx, double vz, boolean onGround,
                       boolean horizontalCollision) {
        long next = waypoints[index];
        int level = WalkTerrain.unpackY(waypoints[Math.max(0, index - 1)]);
        int nextY = WalkTerrain.unpackY(next);
        aimX = WalkTerrain.unpackX(next) + 0.5;
        aimZ = WalkTerrain.unpackZ(next) + 0.5;
        if (nextY == level && Math.abs(y - level - terrain.floorOffset(cellX(x), level, cellZ(z))) < 0.6) {
            lookAhead(terrain, x, z, level);
        }
        int feetX = cellX(x), feetY = (int) Math.floor(y + 0.5), feetZ = cellZ(z);
        boolean sameColumn = WalkTerrain.unpackX(next) == feetX && WalkTerrain.unpackZ(next) == feetZ;
        byte feet = terrain.cell(feetX, feetY, feetZ);
        if (nextY > feetY) {
            if (sameColumn || feet == WalkTerrain.CLIMB || feet == WalkTerrain.WATER) {
                // Ladders climb while jump is held; water lifts the body the same way.
                climb = true;
            } else if (onGround && (horizontalCollision || frontGap(x, z, next) <= JUMP_LEAD)) {
                double rise = nextY + terrain.floorOffset(WalkTerrain.unpackX(next), nextY, WalkTerrain.unpackZ(next))
                        - (feetY + terrain.floorOffset(feetX, feetY, feetZ));
                jump = rise > WalkPathfinder.STEP_HEIGHT || horizontalCollision;
            }
        } else if (nextY < feetY) {
            descending = true;
        } else if (onGround && gapAhead(terrain, feetX, feetY, feetZ, next)) {
            gapJump = true;
            jump = edgeDistance(x, z, feetX, feetZ, next) <= GAP_TAKE_OFF;
        }
        applyEscape(x, z, onGround);
        double speed = Math.hypot(vx, vz);
        if (index == waypoints.length - 1) {
            byte floor = terrain.cell(WalkTerrain.unpackX(next), nextY - 1, WalkTerrain.unpackZ(next));
            double friction = floor == WalkTerrain.ICE ? ICE_FRICTION : GROUND_FRICTION;
            double stopping = speed * friction / (1.0 - friction);
            coast = onGround && !jump && !climb && horizontal(x, z, next) <= stopping + 0.05;
        }
        sprint = escape == ESCAPE_NONE && !jump && !gapJump && !climb && !descending && onGround
                && feet != WalkTerrain.WATER && levelAhead(x, z, level) >= SPRINT_DISTANCE;
    }

    /** Aim at the furthest waypoint on this level that the body reaches in a straight line, turning early near it. */
    private void lookAhead(WalkTerrain terrain, double x, double z, int level) {
        double floor = terrain.floorOffset(cellX(x), level, cellZ(z));
        int best = -1;
        int limit = Math.min(waypoints.length - 1, index + LOOKAHEAD_WAYPOINTS);
        for (int k = limit; k >= index; k--) {
            long cell = waypoints[k];
            if (!sameLevel(index, k, level) || horizontal(x, z, cell) > LOOKAHEAD_DISTANCE) continue;
            if (PathSmoother.straightWalkable(terrain, x, z, WalkTerrain.unpackX(cell) + 0.5, WalkTerrain.unpackZ(cell) + 0.5,
                    level, floor, STEER_HALF_WIDTH, maxDrop)) {
                best = k;
                break;
            }
        }
        if (best < 0) return;
        long aim = waypoints[best];
        aimX = WalkTerrain.unpackX(aim) + 0.5;
        aimZ = WalkTerrain.unpackZ(aim) + 0.5;
        if (best + 1 < waypoints.length && WalkTerrain.unpackY(waypoints[best + 1]) == level
                && horizontal(x, z, aim) < TURN_DISTANCE) {
            long after = waypoints[best + 1];
            double mx = (aimX + WalkTerrain.unpackX(after) + 0.5) * 0.5;
            double mz = (aimZ + WalkTerrain.unpackZ(after) + 0.5) * 0.5;
            if (PathSmoother.straightWalkable(terrain, x, z, mx, mz, level, floor, STEER_HALF_WIDTH, maxDrop)) {
                aimX = mx;
                aimZ = mz;
            }
        }
    }

    private void applyEscape(double x, double z, boolean onGround) {
        double cx = Math.floor(x) + 0.5, cz = Math.floor(z) + 0.5;
        double dx = aimX - x, dz = aimZ - z;
        double length = Math.max(1.0e-6, Math.hypot(dx, dz));
        switch (escape) {
            case ESCAPE_RECENTER -> {
                // A body caught on a corner frees itself by lining up with the cell centre first.
                if (Math.hypot(x - cx, z - cz) > 0.12) {
                    aimX = cx;
                    aimZ = cz;
                }
            }
            case ESCAPE_JUMP -> jump = onGround;
            case ESCAPE_BACK_OFF -> {
                aimX = x - dx / length;
                aimZ = z - dz / length;
            }
            case ESCAPE_SIDESTEP -> {
                double side = (tick / 8) % 2 == 0 ? 1.0 : -1.0;
                aimX = x - dz / length * side;
                aimZ = z + dx / length * side;
            }
            default -> {
            }
        }
    }

    /** Level route length ahead of the player before the first level change. */
    private double levelAhead(double x, double z, int level) {
        double total = horizontal(x, z, waypoints[index]);
        if (WalkTerrain.unpackY(waypoints[index]) != level) return 0.0;
        for (int i = index + 1; i < waypoints.length && WalkTerrain.unpackY(waypoints[i]) == level; i++) {
            total += distance(waypoints[i - 1], waypoints[i]);
        }
        // Keep a short stretch before the goal unsprinted, so the stop does not overshoot.
        return index == waypoints.length - 1 ? total - SPRINT_DISTANCE : total;
    }

    private boolean sameLevel(int from, int to, int level) {
        for (int k = from; k <= to; k++) if (WalkTerrain.unpackY(waypoints[k]) != level) return false;
        return true;
    }

    /** The next waypoint is two cells away in a straight line with no floor in between. */
    private static boolean gapAhead(WalkTerrain terrain, int fx, int fy, int fz, long next) {
        int dx = WalkTerrain.unpackX(next) - fx, dz = WalkTerrain.unpackZ(next) - fz;
        if (Math.abs(dx) + Math.abs(dz) != 2 || dx != 0 && dz != 0) return false;
        int mx = fx + dx / 2, mz = fz + dz / 2;
        return !terrain.standable(mx, fy, mz) && !terrain.standable(mx, fy - 1, mz);
    }

    /** Distance from the body centre to the edge of its cell facing {@code next}. */
    private static double edgeDistance(double x, double z, int fx, int fz, long next) {
        int dx = Integer.signum(WalkTerrain.unpackX(next) - fx), dz = Integer.signum(WalkTerrain.unpackZ(next) - fz);
        if (dx > 0) return fx + 1 - x;
        if (dx < 0) return x - fx;
        if (dz > 0) return fz + 1 - z;
        return z - fz;
    }

    /** Gap between the body's side and the nearest face of cell {@code next}, horizontally. */
    private static double frontGap(double x, double z, long next) {
        double minX = WalkTerrain.unpackX(next), minZ = WalkTerrain.unpackZ(next);
        double dx = Math.max(0.0, Math.max(minX - x, x - (minX + 1)));
        double dz = Math.max(0.0, Math.max(minZ - z, z - (minZ + 1)));
        return Math.max(0.0, Math.hypot(dx, dz) - 0.3);
    }

    private static double segmentDistance(double x, double z, long from, long to) {
        double ax = WalkTerrain.unpackX(from) + 0.5, az = WalkTerrain.unpackZ(from) + 0.5;
        double bx = WalkTerrain.unpackX(to) + 0.5, bz = WalkTerrain.unpackZ(to) + 0.5;
        double dx = bx - ax, dz = bz - az;
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq < 1.0e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (z - az) * dz) / lengthSq));
        return Math.hypot(x - (ax + dx * t), z - (az + dz * t));
    }

    private static double horizontal(double x, double z, long cell) {
        return Math.hypot(WalkTerrain.unpackX(cell) + 0.5 - x, WalkTerrain.unpackZ(cell) + 0.5 - z);
    }

    private static double distance(long a, long b) {
        double dx = WalkTerrain.unpackX(a) - WalkTerrain.unpackX(b);
        double dy = WalkTerrain.unpackY(a) - WalkTerrain.unpackY(b);
        double dz = WalkTerrain.unpackZ(a) - WalkTerrain.unpackZ(b);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int cellX(double x) {
        return (int) Math.floor(x);
    }

    private static int cellZ(double z) {
        return (int) Math.floor(z);
    }
}
