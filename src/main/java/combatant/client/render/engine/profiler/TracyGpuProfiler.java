/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

public enum TracyGpuProfiler {
    ;
    private static final boolean DEV = DevProfilerBridge.available("TracyGpuProfiler");

    public static boolean isEnabled() {
        if (!DEV) return false;
        return DevProfilerBridge.bool("TracyGpuProfiler", "isEnabled", false, new Class<?>[0]);
    }

    public static Scope beginZone(String name) {
        if (!DEV) return Scope.NOOP;
        return new Scope(DevProfilerBridge.closeable("TracyGpuProfiler", "beginZone", new Class<?>[]{String.class}, name));
    }

    public static void onFrameEnd() {
        if (!DEV) return;
        DevProfilerBridge.invoke("TracyGpuProfiler", "onFrameEnd", new Class<?>[0]);
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
