/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import combatant.client.render.engine.rhi.RhiStatsSnapshot;
import combatant.client.render.engine.rhi.resource.RenderResourceStatsSnapshot;

public enum TracyProfiler {
    ;
    private static final boolean DEV = DevProfilerBridge.available("TracyProfiler");

    public static boolean isEnabled() {
        if (!DEV) return false;
        return DevProfilerBridge.bool("TracyProfiler", "isEnabled", false, new Class<?>[0]);
    }

    public static boolean isAvailable() {
        if (!DEV) return false;
        return DevProfilerBridge.bool("TracyProfiler", "isAvailable", false, new Class<?>[0]);
    }

    public static synchronized boolean setEnabled(boolean enabled) {
        if (!DEV) return false;
        return DevProfilerBridge.bool("TracyProfiler", "setEnabled", false, new Class<?>[]{boolean.class}, enabled);
    }

    public static Scope beginZone(String name) {
        if (!DEV) return Scope.NOOP;
        return new Scope(DevProfilerBridge.closeable("TracyProfiler", "beginZone", new Class<?>[]{String.class}, name));
    }

    public static void markFrame() {
        if (!DEV) return;
        DevProfilerBridge.invoke("TracyProfiler", "markFrame", new Class<?>[0]);
    }

    public static boolean shouldTraceCurrentThread() {
        if (!DEV) return false;
        return DevProfilerBridge.bool("TracyProfiler", "shouldTraceCurrentThread", false, new Class<?>[0]);
    }

    public static void plotUiFrame(double ms, int nodes) {
        if (!DEV) return;
        DevProfilerBridge.invoke("TracyProfiler", "plotUiFrame", new Class<?>[]{double.class, int.class}, ms, nodes);
    }

    public static void plotWorldFrame(double ms, int nodes) {
        if (!DEV) return;
        DevProfilerBridge.invoke("TracyProfiler", "plotWorldFrame", new Class<?>[]{double.class, int.class}, ms, nodes);
    }

    public static void plotGlWait(double ms, int calls) {
        if (!DEV) return;
        DevProfilerBridge.invoke("TracyProfiler", "plotGlWait", new Class<?>[]{double.class, int.class}, ms, calls);
    }

    public static void plotUiBatch(int draws, int vertices) {
        if (!DEV) return;
        DevProfilerBridge.invoke("TracyProfiler", "plotUiBatch", new Class<?>[]{int.class, int.class}, draws, vertices);
    }

    /** Sends frame counters directly to Tracy; independent from the optional 2D profiler tree. */
    public static void plotUiPipeline(UiPipelineStatsSnapshot snapshot) {
        if (!DEV || snapshot == null) return;
        DevProfilerBridge.invoke(
                "TracyProfiler",
                "plotUiPipeline",
                new Class<?>[]{UiPipelineStatsSnapshot.class},
                snapshot
        );
    }

    /** Sends RHI binding/pass/upload counters without depending on a tree profiler. */
    public static void plotRhiPipeline(RhiStatsSnapshot snapshot) {
        if (!DEV || snapshot == null) return;
        DevProfilerBridge.invoke(
                "TracyProfiler",
                "plotRhiPipeline",
                new Class<?>[]{RhiStatsSnapshot.class},
                snapshot
        );
    }

    public static void plotRenderResources(RenderResourceStatsSnapshot snapshot) {
        if (!DEV || snapshot == null) return;
        DevProfilerBridge.invoke(
                "TracyProfiler",
                "plotRenderResources",
                new Class<?>[]{RenderResourceStatsSnapshot.class},
                snapshot
        );
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(null);
        private final AutoCloseable delegate;

        private Scope(AutoCloseable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            DevProfilerBridge.close(delegate);
        }
    }
}
