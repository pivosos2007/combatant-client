/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.scaffold;

/**
 * Owns the two independent pieces of Scaffold timing state.
 * Placement cadence must never extend or re-arm an Eagle sneak pulse.
 *
 * <p>The cadence is counted in client ticks. Placements only ever happen on a tick, and the wall-clock interval
 * between two ticks wanders around 50 ms: a gate on elapsed nanoseconds turned a nominal one block per tick (20
 * blocks per second) into a coin flip between one and two ticks, which is exactly the pace a diagonal bridge needs.
 */
public final class ScaffoldPlacementGate {
    private static final double TICKS_PER_SECOND = 20.0;

    private long lastSuccessfulPlacementTick;
    private boolean hasPlaced;
    private int placementsSinceEaglePulse;
    private boolean eaglePulseArmed;

    public ScaffoldPlacementGate() {
        reset();
    }

    public void reset() {
        lastSuccessfulPlacementTick = 0L;
        hasPlaced = false;
        placementsSinceEaglePulse = 0;
        eaglePulseArmed = true;
    }

    public boolean isPlacementReady(long nowTick, double blocksPerSecond, int extraDelayTicksLeft) {
        if (extraDelayTicksLeft > 0) return false;
        if (!hasPlaced) return true;

        return nowTick - lastSuccessfulPlacementTick >= placementIntervalTicks(blocksPerSecond);
    }

    public void recordSuccessfulPlacement(long nowTick, int blocksToEagle) {
        lastSuccessfulPlacementTick = nowTick;
        hasPlaced = true;
        placementsSinceEaglePulse++;

        if (placementsSinceEaglePulse > Math.max(0, blocksToEagle)) {
            placementsSinceEaglePulse = 0;
            eaglePulseArmed = true;
        }
    }

    public boolean consumeEaglePulse(
            boolean eagleEnabled,
            boolean closeToEdge,
            boolean onlyOnGround,
            boolean onGround,
            boolean flying,
            boolean goingDown
    ) {
        boolean eligible = eagleEnabled
                && closeToEdge
                && (!onlyOnGround || onGround)
                && !flying
                && !goingDown;
        if (!eligible || !eaglePulseArmed) return false;

        eaglePulseArmed = false;
        return true;
    }

    public static boolean shouldSafeWalk(
            boolean scaffoldEnabled,
            boolean eagleEnabled,
            boolean canOperate,
            boolean onGround,
            boolean flying,
            boolean goingDown,
            boolean hasBlocks
    ) {
        return scaffoldEnabled
                && eagleEnabled
                && canOperate
                && onGround
                && !flying
                && !goingDown
                && hasBlocks;
    }

    /** Fewest whole ticks between two placements that keeps the rate at or under {@code blocksPerSecond}. */
    static long placementIntervalTicks(double blocksPerSecond) {
        double safeBlocksPerSecond = Math.max(0.1, blocksPerSecond);
        return Math.max(1L, (long) Math.ceil(TICKS_PER_SECOND / safeBlocksPerSecond - 1.0E-9));
    }
}
