/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

/**
 * What to reach, independent of who walks there. Positions are block coordinates; a request is immutable and
 * shareable.
 *
 * <ul>
 *   <li>{@link GoalKind#BLOCK}: stand in the feet cell {@code (x, y, z)}.</li>
 *   <li>{@link GoalKind#NEAR}: get within {@code range} blocks of the centre of cell {@code (x, y, z)}, measured
 *       from the feet.</li>
 *   <li>{@link GoalKind#REACH}: stand somewhere the eyes are within {@code range} blocks of the centre of block
 *       {@code (x, y, z)}, so it can be clicked. The block itself may be solid.</li>
 * </ul>
 */
public record NavigationRequest(GoalKind kind, int x, int y, int z, double range, Options options) {

    public enum GoalKind {
        BLOCK,
        NEAR,
        REACH
    }

    /**
     * @param allowSprint  the provider may sprint on level, straight stretches
     * @param allowJump    the provider may jump; routes that need a jump are not used when false
     * @param maxDrop      deepest fall the route may contain (the walk pathfinder caps it at 3)
     * @param timeoutTicks fail with {@link NavigationFailure#TIMEOUT} after this many ticks; 0 or less for never
     */
    public record Options(boolean allowSprint, boolean allowJump, int maxDrop, int timeoutTicks) {

        public static final Options DEFAULT = new Options(true, true, 3, 0);

        public Options {
            maxDrop = Math.max(0, maxDrop);
            timeoutTicks = Math.max(0, timeoutTicks);
        }

        public Options withSprint(boolean allowSprint) {
            return new Options(allowSprint, allowJump, maxDrop, timeoutTicks);
        }

        public Options withJump(boolean allowJump) {
            return new Options(allowSprint, allowJump, maxDrop, timeoutTicks);
        }

        public Options withMaxDrop(int maxDrop) {
            return new Options(allowSprint, allowJump, maxDrop, timeoutTicks);
        }

        public Options withTimeout(int timeoutTicks) {
            return new Options(allowSprint, allowJump, maxDrop, timeoutTicks);
        }
    }

    public NavigationRequest {
        if (kind == null) throw new IllegalArgumentException("kind");
        if (kind != GoalKind.BLOCK && !(range >= 0.0)) throw new IllegalArgumentException("range must be >= 0: " + range);
        options = options != null ? options : Options.DEFAULT;
    }

    public static NavigationRequest block(int x, int y, int z) {
        return new NavigationRequest(GoalKind.BLOCK, x, y, z, 0.0, Options.DEFAULT);
    }

    public static NavigationRequest near(int x, int y, int z, double radius) {
        return new NavigationRequest(GoalKind.NEAR, x, y, z, radius, Options.DEFAULT);
    }

    public static NavigationRequest reach(int x, int y, int z, double reach) {
        return new NavigationRequest(GoalKind.REACH, x, y, z, reach, Options.DEFAULT);
    }

    public NavigationRequest withOptions(Options options) {
        return new NavigationRequest(kind, x, y, z, range, options);
    }

    /** Squared distance from the point to the goal cell centre. */
    public double distanceSqr(double px, double py, double pz) {
        double dx = px - (x + 0.5), dy = py - (y + 0.5), dz = pz - (z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Whether a player with feet at {@code (px, py, pz)} and eyes {@code eyeHeight} higher satisfies the goal.
     * {@link GoalKind#BLOCK} compares the feet cell (feet y is rounded the way slab floors need).
     */
    public boolean satisfiedBy(double px, double py, double pz, double eyeHeight) {
        return switch (kind) {
            case BLOCK -> (int) Math.floor(px) == x && (int) Math.floor(py + 0.5) == y && (int) Math.floor(pz) == z;
            case NEAR -> distanceSqr(px, py, pz) <= range * range;
            case REACH -> distanceSqr(px, py + eyeHeight, pz) <= range * range;
        };
    }
}
