/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.CombatantRhi;

/** Dev-source-set owner for the experimental deferred renderer and its frame graph. */
public enum DevDeferredRuntime {
    ;
    private static final DeferredWorldPipeline WORLD = new DeferredWorldPipeline();
    private static final DeferredPassGraph GRAPH = new DeferredPassGraph();

    public static DeferredWorldPipeline world() { return WORLD; }
    public static DeferredPassGraph graph() { return GRAPH; }

    public static void serviceFrameBoundary() { WORLD.serviceRuntimeLifecycle(); }

    public static void backendChanged(CombatantRhi previous) {
        WORLD.releasePhysicalResources();
        GRAPH.releaseBackendResources(previous);
        WORLD.onBackendChanged();
    }

    public static void shutdown() {
        WORLD.shutdownRuntime();
        GRAPH.releaseBackendResources(CombatantRenderSystem.rhi());
    }
}
