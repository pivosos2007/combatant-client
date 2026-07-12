/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import java.util.List;

public enum RenderProfiler3D {
    ;
    private static final boolean DEV = DevProfilerBridge.available("RenderProfiler3D");

    public static boolean isEnabled() {
        if (!DEV) return false;
        return DevProfilerBridge.bool("RenderProfiler3D", "isEnabled", false, new Class<?>[0]);
    }

    public static void beginFrame(String name) {
        if (!DEV) return;
        DevProfilerBridge.invoke("RenderProfiler3D", "beginFrame", new Class<?>[]{String.class}, name);
    }

    public static boolean beginFrameIfNeeded(String name) {
        if (!DEV) return false;
        return DevProfilerBridge.bool("RenderProfiler3D", "beginFrameIfNeeded", false, new Class<?>[]{String.class}, name);
    }

    public static void endFrame() {
        if (!DEV) return;
        DevProfilerBridge.invoke("RenderProfiler3D", "endFrame", new Class<?>[0]);
    }

    public static Section section(String name) {
        if (!DEV) return Section.NOOP;
        return new Section(DevProfilerBridge.closeable("RenderProfiler3D", "section", new Class<?>[]{String.class}, name));
    }

    public static List<String> getDebugLines() {
        if (!DEV) return List.of();
        return DevProfilerBridge.lines("RenderProfiler3D", "getDebugLines", new Class<?>[0]);
    }

    public static List<String> getDebugLines(int topLimit, int depthLimit, double minMs) {
        if (!DEV) return List.of();
        return DevProfilerBridge.lines("RenderProfiler3D", "getDebugLines",
                new Class<?>[]{int.class, int.class, double.class}, topLimit, depthLimit, minMs);
    }

    public static final class Section implements AutoCloseable {
        private static final Section NOOP = new Section(null);
        private final AutoCloseable delegate;

        private Section(AutoCloseable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            DevProfilerBridge.close(delegate);
        }
    }
}
