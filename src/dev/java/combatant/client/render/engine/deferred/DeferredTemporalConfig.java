/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** User-facing final temporal policy. Filtering details stay inside the temporal consumer. */
public final class DeferredTemporalConfig {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicReference<Snapshot> CURRENT = new AtomicReference<>(new Snapshot(false));

    private DeferredTemporalConfig() {
    }

    public static Snapshot current() {
        return CURRENT.get();
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static long setTaaEnabled(boolean enabled) {
        Snapshot next = new Snapshot(enabled);
        Snapshot previous = CURRENT.getAndSet(next);
        return previous.equals(next) ? GENERATION.get() : GENERATION.incrementAndGet();
    }

    public record Snapshot(boolean taaEnabled) {
    }
}
