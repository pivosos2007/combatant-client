/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import java.util.concurrent.TimeUnit;

/**
 * Timing gate for server-validated block destruction. The clock starts only after the
 * outgoing START_DESTROY_BLOCK packet, never from the client's visual crack progress.
 */
final class Vulcan297FastBreakTiming {
    private static final long NANOS_PER_MILLI = TimeUnit.MILLISECONDS.toNanos(1);
    private static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final long MAX_BREAK_NANOS = TimeUnit.SECONDS.toNanos(120);

    private Object target;
    private long startNanos;
    private long requiredNanos;
    private boolean active;

    synchronized void start(Object block, long nowNanos) {
        target = block;
        startNanos = nowNanos;
        requiredNanos = 0L;
        active = block != null;
    }

    synchronized void stop(Object block) {
        if (active && target.equals(block)) reset();
    }

    synchronized void reset() {
        target = null;
        startNanos = 0L;
        requiredNanos = 0L;
        active = false;
    }

    /**
     * Client damage is progress per vanilla tick. The server-side FastBreak A check
     * calculates a real-time deadline from block hardness, tool speed and effects.
     * Convert back to milliseconds and keep the *largest* observed deadline: a tool
     * swap, airborne penalty or changing mining effects must not shorten an active dig.
     * A full tick of slack covers timing/packet jitter, not an assumed anti-cheat buffer.
     */
    synchronized boolean ready(Object block, float damagePerTick, float currentProgress,
                               float minimumProgress, long nowNanos) {
        if (!active || block == null || !target.equals(block)) return false;
        if (!Float.isFinite(damagePerTick) || damagePerTick <= 0.0f || damagePerTick >= 1.0f) return false;
        if (nowNanos < startNanos) return false;

        double expected = TICK_NANOS / (double) damagePerTick;
        if (!Double.isFinite(expected) || expected >= MAX_BREAK_NANOS) return false;
        long required = Math.min(MAX_BREAK_NANOS, (long) Math.ceil(expected) + TICK_NANOS);
        requiredNanos = Math.max(requiredNanos, required);
        return Float.isFinite(currentProgress) && currentProgress >= minimumProgress
                && nowNanos - startNanos >= requiredNanos;
    }
}
