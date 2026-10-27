/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.placement;

import java.util.HashMap;

/**
 * Counts failed placements per key. Reaching the failure limit keeps the key out of planning for
 * a while, then it gets a fresh start. Failures older than the stale window are forgotten so one
 * rejected click long ago does not count toward a later backoff. Free of Minecraft types.
 */
public final class PlacementBackoff<K> {

    private static final class Entry {
        int failures;
        long lastFailureTick;
        long blockedUntil = Long.MIN_VALUE;
    }

    private final int failureLimit;
    private final int backoffTicks;
    private final int staleTicks;
    private final HashMap<K, Entry> entries = new HashMap<>();

    public PlacementBackoff(int failureLimit, int backoffTicks, int staleTicks) {
        this.failureLimit = Math.max(1, failureLimit);
        this.backoffTicks = Math.max(1, backoffTicks);
        this.staleTicks = Math.max(1, staleTicks);
    }

    /** @return the failure count for the key after this failure; 0 when it just entered backoff */
    public int recordFailure(K key, long tick) {
        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        if (entry.failures > 0 && tick - entry.lastFailureTick > staleTicks) entry.failures = 0;
        entry.failures++;
        entry.lastFailureTick = tick;
        if (entry.failures >= failureLimit) {
            entry.failures = 0;
            entry.blockedUntil = tick + backoffTicks;
            return 0;
        }
        return entry.failures;
    }

    public void recordSuccess(K key) {
        entries.remove(key);
    }

    public boolean isBackedOff(K key, long tick) {
        Entry entry = entries.get(key);
        if (entry == null) return false;
        if (tick < entry.blockedUntil) return true;
        if (entry.failures == 0 || tick - entry.lastFailureTick > staleTicks) entries.remove(key);
        return false;
    }

    public int failures(K key, long tick) {
        Entry entry = entries.get(key);
        if (entry == null || tick - entry.lastFailureTick > staleTicks) return 0;
        return entry.failures;
    }

    public void clear() {
        entries.clear();
    }
}
