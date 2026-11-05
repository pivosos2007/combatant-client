/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * User-facing production enablement for environment subsystems.
 *
 * <p>This is deliberately separate from {@link DeferredSmokeTestState}: normal settings select
 * production policy, while smoke overrides remain a temporary diagnostic layer over that policy.</p>
 */
public final class DeferredEnvironmentFeatureConfig {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicReference<Snapshot> CURRENT =
            new AtomicReference<>(new Snapshot(true, true, true));

    private DeferredEnvironmentFeatureConfig() {
    }

    public static Snapshot current() {
        return CURRENT.get();
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static long apply(boolean skyEnabled, boolean cloudsEnabled, boolean weatherEnabled) {
        Snapshot next = new Snapshot(skyEnabled, cloudsEnabled, weatherEnabled);
        Snapshot previous = CURRENT.getAndSet(next);
        return previous.equals(next) ? GENERATION.get() : GENERATION.incrementAndGet();
    }

    public record Snapshot(boolean skyEnabled, boolean cloudsEnabled, boolean weatherEnabled) {
    }
}
