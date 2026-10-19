/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.actions;

/**
 * Pure no-progress detector for approach movements. Progress means getting closer than the best distance seen so
 * far by at least {@code minProgress}; {@code maxStallTicks} consecutive updates without it report a stall.
 */
public final class ProgressWatchdog {
    private final int maxStallTicks;
    private final double minProgress;
    private double best = Double.POSITIVE_INFINITY;
    private int stalledTicks;

    public ProgressWatchdog(int maxStallTicks, double minProgress) {
        this.maxStallTicks = Math.max(1, maxStallTicks);
        this.minProgress = Math.max(0.0, minProgress);
    }

    /** @return true when the stall limit is reached */
    public boolean update(double distance) {
        if (distance < best - minProgress) {
            best = distance;
            stalledTicks = 0;
        } else {
            stalledTicks++;
        }
        return stalledTicks >= maxStallTicks;
    }

    public int stalledTicks() {
        return stalledTicks;
    }

    public void reset() {
        best = Double.POSITIVE_INFINITY;
        stalledTicks = 0;
    }
}
