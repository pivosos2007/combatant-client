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
 */
public final class ScaffoldPlacementGate {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private long lastSuccessfulPlacementNanos;
    private int placementsSinceEaglePulse;
    private boolean eaglePulseArmed;

    public ScaffoldPlacementGate() {
        reset();
    }

    public void reset() {
        lastSuccessfulPlacementNanos = 0L;
        placementsSinceEaglePulse = 0;
        eaglePulseArmed = true;
    }

    public boolean isPlacementReady(long nowNanos, double blocksPerSecond, int extraDelayTicksLeft) {
        if (extraDelayTicksLeft > 0) return false;
        if (lastSuccessfulPlacementNanos == 0L) return true;

        return nowNanos - lastSuccessfulPlacementNanos >= placementIntervalNanos(blocksPerSecond);
    }

    public void recordSuccessfulPlacement(long nowNanos, int blocksToEagle) {
        lastSuccessfulPlacementNanos = nowNanos;
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

    static long placementIntervalNanos(double blocksPerSecond) {
        double safeBlocksPerSecond = Math.max(0.1, blocksPerSecond);
        return (long) Math.ceil(NANOS_PER_SECOND / safeBlocksPerSecond);
    }
}
