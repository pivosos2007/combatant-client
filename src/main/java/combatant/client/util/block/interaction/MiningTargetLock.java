/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.block.interaction;

import java.util.Objects;

/** Sticky transaction state. A candidate is admitted once and cannot preempt it on later ticks. */
public final class MiningTargetLock<T> {
    public enum Phase { IDLE, ALIGNING, BREAKING }

    private T target;
    private Phase phase = Phase.IDLE;

    public T target() { return target; }
    public Phase phase() { return phase; }
    public boolean isLocked() { return target != null; }

    public boolean acquire(T candidate) {
        if (candidate == null || target != null) return false;
        target = candidate;
        phase = Phase.ALIGNING;
        return true;
    }

    public void markBreaking(T candidate) {
        if (target != null && Objects.equals(target, candidate)) phase = Phase.BREAKING;
    }

    public void release() {
        target = null;
        phase = Phase.IDLE;
    }
}
