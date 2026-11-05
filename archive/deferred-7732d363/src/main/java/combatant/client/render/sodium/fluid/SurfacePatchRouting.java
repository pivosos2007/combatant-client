/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

import java.util.concurrent.atomic.AtomicBoolean;

/** Shared render-thread routing state for native patch replacements produced by Sodium meshing. */
public final class SurfacePatchRouting {
    private static volatile boolean waterReplacementActive;
    private static volatile boolean heightReplacementActive;
    private static final AtomicBoolean RELOAD_REQUESTED = new AtomicBoolean();

    private SurfacePatchRouting() { }

    public static boolean waterReplacementActive() { return waterReplacementActive; }
    public static boolean heightReplacementActive() { return heightReplacementActive; }

    public static boolean setWaterReplacementActive(boolean active) {
        if (waterReplacementActive == active) return false;
        waterReplacementActive = active;
        RELOAD_REQUESTED.set(true);
        return true;
    }

    public static boolean setHeightReplacementActive(boolean active) {
        if (heightReplacementActive == active) return false;
        heightReplacementActive = active;
        RELOAD_REQUESTED.set(true);
        return true;
    }

    /** Consumed before primary terrain submission to schedule non-destructive loaded-section re-meshing. */
    public static boolean consumeReloadRequested() {
        return RELOAD_REQUESTED.getAndSet(false);
    }

    public static void reset() {
        setWaterReplacementActive(false);
        setHeightReplacementActive(false);
    }
}
