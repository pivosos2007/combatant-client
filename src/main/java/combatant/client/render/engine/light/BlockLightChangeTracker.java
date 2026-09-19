/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.light;

import java.util.concurrent.atomic.AtomicLong;

/** Monotonic invalidation generation for renderer-owned colored block-light data. */
public final class BlockLightChangeTracker {
    private static final AtomicLong GENERATION = new AtomicLong(1L);

    private BlockLightChangeTracker() {
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static void markChanged() {
        GENERATION.incrementAndGet();
    }
}
