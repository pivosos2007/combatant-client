/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

public enum ProfilerPhase {
    ;
    private static final boolean DEV = DevProfilerBridge.available("ProfilerPhase");

    public static Scope scope(String label) {
        if (!DEV) return Scope.NOOP;
        return new Scope(DevProfilerBridge.closeable("ProfilerPhase", "scope", new Class<?>[]{String.class}, label));
    }

    public static void begin(String label) {
        if (!DEV) return;
        DevProfilerBridge.invoke("ProfilerPhase", "begin", new Class<?>[]{String.class}, label);
    }

    public static void end(String label) {
        if (!DEV) return;
        DevProfilerBridge.invoke("ProfilerPhase", "end", new Class<?>[]{String.class}, label);
    }

    public static String current() {
        if (!DEV) return "unknown";
        return DevProfilerBridge.string("ProfilerPhase", "current", "unknown", new Class<?>[0]);
    }

    public static void clearCurrent() {
        if (!DEV) return;
        DevProfilerBridge.invoke("ProfilerPhase", "clearCurrent", new Class<?>[0]);
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
