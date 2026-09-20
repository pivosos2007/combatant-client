/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/**
 * Semantic contract for reducing raster/MSAA motion into the canonical single-sample temporal
 * signals. Coverage, motion validity and velocity are intentionally independent.
 *
 * <p>The important distinction is covered-with-invalid-motion: a forward fragment owns the final
 * pixel even when it cannot provide a trustworthy previous position. In that case the resolve
 * must invalidate motion instead of leaking valid opaque velocity from geometry underneath.</p>
 */
public final class DeferredMotionResolveContract {
    /** Increment when resolve semantics, not merely storage packing, change. */
    public static final int VERSION = 1;

    private DeferredMotionResolveContract() {
    }

    /**
     * {@code coverage == 0}: the raster producer does not own this sample; preserve the base
     * pre-translucency signal. {@code coverage != 0}: the raster producer owns this sample and its
     * validity replaces the base validity even when that validity is zero.
     */
    public static boolean rasterOwnsSample(float coverage) {
        return coverage > 0.5f;
    }

    /** Zero velocity represents stationary motion only when validity is explicitly true. */
    public static boolean trustworthyMotion(float validity) {
        return validity > 0.5f;
    }
}
