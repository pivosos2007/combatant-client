package combatant.client.render.engine.profiler;

import com.mojang.jtracy.Plot;
import com.mojang.jtracy.TracyClient;
import com.mojang.jtracy.Zone;

public enum DevTracyProfiler {
    ;

    private static final Scope NOOP_SCOPE = new Scope(null);
    private static final ThreadLocal<Boolean> THREAD_NAMED =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static volatile boolean loadAttempted;
    private static volatile boolean available;
    private static volatile Plot uiFrameMsPlot;
    private static volatile Plot uiNodeCountPlot;
    private static volatile Plot worldFrameMsPlot;
    private static volatile Plot worldNodeCountPlot;
    private static volatile Plot glWaitMsPlot;
    private static volatile Plot glWaitCallsPlot;
    private static volatile Plot uiBatchDrawsPlot;
    private static volatile Plot uiBatchVerticesPlot;

    public static boolean isEnabled() {
        return ProfilerSettings.isTracyEnabled() && isAvailable();
    }

    public static boolean isAvailable() {
        ensureLoaded();
        return available;
    }

    public static synchronized boolean setEnabled(boolean enabled) {
        ProfilerSettings.setTracyEnabled(enabled);
        if (!enabled) {
            ProfilerLog.info("Tracy disabled");
            return false;
        }
        boolean active = isAvailable();
        if (active) {
            ProfilerLog.info("Tracy enabled; data is streamed to the external Tracy viewer, not latest.log");
            TracyClient.message("Combatant Tracy enabled");
        }
        return active;
    }

    public static Scope beginZone(String name) {
        if (!isEnabled() || name == null || name.isBlank()) {
            return NOOP_SCOPE;
        }
        ensureThreadNamed();
        return new Scope(TracyClient.beginZone(name, false));
    }

    public static void markFrame() {
        if (!isEnabled()) return;
        ensureThreadNamed();
        TracyClient.markFrame();
    }

    public static boolean shouldTraceCurrentThread() {
        String threadName = Thread.currentThread().getName();
        if (threadName == null || threadName.isBlank()) {
            return false;
        }
        return "Render thread".equals(threadName)
                || threadName.startsWith("Render thread")
                || "main".equals(threadName)
                || "Main thread".equals(threadName);
    }

    public static void plotUiFrame(double ms, int nodes) {
        if (!isEnabled()) return;
        plotUiFrameMs().setValue(ms);
        plotUiNodeCount().setValue(nodes);
    }

    public static void plotWorldFrame(double ms, int nodes) {
        if (!isEnabled()) return;
        plotWorldFrameMs().setValue(ms);
        plotWorldNodeCount().setValue(nodes);
    }

    public static void plotGlWait(double ms, int calls) {
        if (!isEnabled()) return;
        plotGlWaitMs().setValue(ms);
        plotGlWaitCalls().setValue(calls);
    }

    public static void plotUiBatch(int draws, int vertices) {
        if (!isEnabled()) return;
        plotUiBatchDraws().setValue(draws);
        plotUiBatchVertices().setValue(vertices);
    }

    private static void ensureLoaded() {
        if (loadAttempted) {
            return;
        }
        synchronized (DevTracyProfiler.class) {
            if (loadAttempted) {
                return;
            }
            loadAttempted = true;
            try {
                TracyClient.load();
                TracyClient.reportAppInfo("Combatant render profiler");
                available = TracyClient.isAvailable();
                if (available) {
                    ProfilerLog.info("Tracy client loaded");
                }
            } catch (Throwable t) {
                available = false;
                ProfilerLog.warn("Tracy unavailable: %s", t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private static void ensureThreadNamed() {
        if (Boolean.TRUE.equals(THREAD_NAMED.get())) {
            return;
        }
        TracyClient.setThreadName(Thread.currentThread().getName(), 0);
        THREAD_NAMED.set(Boolean.TRUE);
    }

    private static Plot plotUiFrameMs() {
        Plot plot = uiFrameMsPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (uiFrameMsPlot == null) {
                uiFrameMsPlot = TracyClient.createPlot("render.ui.ms");
            }
            return uiFrameMsPlot;
        }
    }

    private static Plot plotUiNodeCount() {
        Plot plot = uiNodeCountPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (uiNodeCountPlot == null) {
                uiNodeCountPlot = TracyClient.createPlot("render.ui.nodes");
            }
            return uiNodeCountPlot;
        }
    }

    private static Plot plotWorldFrameMs() {
        Plot plot = worldFrameMsPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (worldFrameMsPlot == null) {
                worldFrameMsPlot = TracyClient.createPlot("render.world.ms");
            }
            return worldFrameMsPlot;
        }
    }

    private static Plot plotWorldNodeCount() {
        Plot plot = worldNodeCountPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (worldNodeCountPlot == null) {
                worldNodeCountPlot = TracyClient.createPlot("render.world.nodes");
            }
            return worldNodeCountPlot;
        }
    }

    private static Plot plotGlWaitMs() {
        Plot plot = glWaitMsPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (glWaitMsPlot == null) {
                glWaitMsPlot = TracyClient.createPlot("render.gl_wait.ms");
            }
            return glWaitMsPlot;
        }
    }

    private static Plot plotGlWaitCalls() {
        Plot plot = glWaitCallsPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (glWaitCallsPlot == null) {
                glWaitCallsPlot = TracyClient.createPlot("render.gl_wait.calls");
            }
            return glWaitCallsPlot;
        }
    }

    private static Plot plotUiBatchDraws() {
        Plot plot = uiBatchDrawsPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (uiBatchDrawsPlot == null) {
                uiBatchDrawsPlot = TracyClient.createPlot("render.ui_batch.draws");
            }
            return uiBatchDrawsPlot;
        }
    }

    private static Plot plotUiBatchVertices() {
        Plot plot = uiBatchVerticesPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (uiBatchVerticesPlot == null) {
                uiBatchVerticesPlot = TracyClient.createPlot("render.ui_batch.vertices");
            }
            return uiBatchVerticesPlot;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Zone zone;

        private Scope(Zone zone) {
            this.zone = zone;
        }

        @Override
        public void close() {
            if (zone != null) {
                zone.close();
            }
        }
    }
}
