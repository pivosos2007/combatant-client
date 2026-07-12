/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.resources;

import combatant.client.util.logging.DebugLog;

/**
 * Tracks whether Minecraft's shader/resource reload has published shader sources
 * to the render backend. Custom render code must not force pipeline compilation
 * before this becomes ready, otherwise Mojang's pipeline compiler may log
 * transient "Couldn't find source" errors even though the same shaders work a
 * few frames later.
 */
public enum RenderResourceReadiness {
    ;
    private static volatile boolean renderResourcesReady;
    private static volatile long generation;
    private static volatile long reloadStartedAtMs;
    private static volatile String stateReason = "boot";

    public static boolean isReady() {
        return renderResourcesReady;
    }

    public static long generation() {
        return generation;
    }

    public static String stateReason() {
        return stateReason;
    }

    public static void markReloading(String reason) {
        renderResourcesReady = false;
        reloadStartedAtMs = System.currentTimeMillis();
        stateReason = reason != null ? reason : "resource reload";
    }

    public static void markReady(String reason) {
        boolean wasReady = renderResourcesReady;
        renderResourcesReady = true;
        generation++;
        stateReason = reason != null ? reason : "resource reload complete";

        if (!wasReady) {
            long dt = reloadStartedAtMs > 0L ? Math.max(0L, System.currentTimeMillis() - reloadStartedAtMs) : 0L;
            DebugLog.renderThread("[Combatant] Render resources ready gen=" + generation + " after " + dt + "ms");
        }
    }
}
