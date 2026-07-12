package combatant.client.render.engine.profiler;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import combatant.client.render.engine.rhi.RhiStatsSnapshot;
import combatant.client.render.engine.rhi.resource.RenderResourceStatsSnapshot;
import combatant.client.render.engine.rhi.uniform.UniformAllocatorStatsSnapshot;
import combatant.client.runtime.RuntimeGate;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.*;

public enum DevFrameStutterProfiler {
    ;
    private static final long DEFAULT_THRESHOLD_NS = 35_000_000L;
    private static final long DEFAULT_SAMPLE_INTERVAL_NS = 2_000_000L;
    private static final long GC_CORRELATION_WINDOW_NS = 75_000_000L;
    private static final int MAX_STACK_FRAMES = 6;
    private static final int MAX_SAMPLES = 2048;
    private static final int MAX_GC_EVENTS = 64;
    private static final int TOP_STACKS = 8;

    private static final Object LOCK = new Object();
    private static final Sample[] SAMPLES = new Sample[MAX_SAMPLES];
    private static final GcEvent[] GC_EVENTS = new GcEvent[MAX_GC_EVENTS];

    private static volatile ProfilerSettings.OutputMode output = ProfilerSettings.OutputMode.OFF;
    private static volatile long thresholdNs = DEFAULT_THRESHOLD_NS;
    private static volatile long sampleIntervalNs = DEFAULT_SAMPLE_INTERVAL_NS;
    private static volatile Thread renderThread;
    private static volatile Thread samplerThread;
    private static volatile boolean running;
    private static volatile boolean gcListenerInstalled;

    private static int sampleCursor;
    private static int sampleCount;
    private static int gcCursor;
    private static int gcCount;
    private static long lastPresentedNs;
    private static long lastLoggedNs;

    public static void configure(ProfilerSettings.OutputMode mode, double thresholdMs, double sampleMs) {
        output = mode == null ? ProfilerSettings.OutputMode.OFF : mode;
        thresholdNs = clampMs(thresholdMs, 1.0, 5000.0, DEFAULT_THRESHOLD_NS);
        sampleIntervalNs = clampMs(sampleMs, 1.0, 100.0, DEFAULT_SAMPLE_INTERVAL_NS);
        if (output == ProfilerSettings.OutputMode.OFF) {
            stopSampler();
            synchronized (LOCK) {
                sampleCursor = 0;
                sampleCount = 0;
                lastPresentedNs = 0L;
                lastLoggedNs = 0L;
            }
            return;
        }
        installGcListener();
        ensureSampler();
    }

    public static String statusLine() {
        if (output == ProfilerSettings.OutputMode.OFF) {
            return "Profiler stutter: off";
        }
        return String.format(Locale.ROOT,
                "Profiler stutter: %s (threshold %.1f ms, sample %.1f ms)",
                output.name().toLowerCase(Locale.ROOT),
                thresholdNs / 1_000_000.0,
                sampleIntervalNs / 1_000_000.0);
    }

    public static boolean isEnabled() {
        return output != ProfilerSettings.OutputMode.OFF;
    }

    public static void onFramePresented(RhiStatsSnapshot rhi,
                                        UniformAllocatorStatsSnapshot uniforms,
                                        RenderResourceStatsSnapshot resources) {
        try {
            if (!isEnabled()) return;
            if (RuntimeGate.isPanic()) return;
            renderThread = Thread.currentThread();
            ensureSampler();

            long now = System.nanoTime();
            long previous;
            synchronized (LOCK) {
                previous = lastPresentedNs;
                lastPresentedNs = now;
            }
            if (previous == 0L) return;

            long elapsedNs = now - previous;
            if (elapsedNs < thresholdNs) return;
            if (now - lastLoggedNs < thresholdNs) return;
            lastLoggedNs = now;

            List<String> lines = buildReport(previous, now, elapsedNs, rhi, uniforms, resources);
            emit(lines);
        } catch (Throwable t) {
            ProfilerLog.warn("[FrameStutter] disabled after profiler failure: %s",
                    t.getClass().getSimpleName() + ": " + t.getMessage());
            configure(ProfilerSettings.OutputMode.OFF, 0.0, 0.0);
        }
    }

    private static List<String> buildReport(long frameStartNs,
                                            long frameEndNs,
                                            long elapsedNs,
                                            RhiStatsSnapshot rhi,
                                            UniformAllocatorStatsSnapshot uniforms,
                                            RenderResourceStatsSnapshot resources) {
        List<Sample> samples = samplesInWindow(frameStartNs, frameEndNs);
        List<GcEvent> gcEvents = gcNearWindow(frameStartNs, frameEndNs);

        Minecraft mc = Minecraft.getInstance();
        String screen = mc == null || ClientScreen.current() == null ? "none" : ClientScreen.current().getClass().getSimpleName();
        String world = mc == null || mc.level == null ? "none" : mc.level.dimension().identifier().toString();

        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT,
                "[FrameStutter] frame %.2f ms over %.2f ms, samples %d, screen=%s, world=%s",
                elapsedNs / 1_000_000.0,
                thresholdNs / 1_000_000.0,
                samples.size(),
                screen,
                world));

        if (rhi != null) {
            long uploaded = rhi.uploadedVertexBytes() + rhi.uploadedIndexBytes();
            lines.add(String.format(Locale.ROOT,
                    "[FrameStutter] rhi draw=%d fs=%d meshUpload=%d/%s copies=%d/%d ringWrap=%d stall=%d fallback=%d backlog=%d",
                    rhi.drawCalls(),
                    rhi.fullscreenPasses(),
                    rhi.meshUploads(),
                    formatBytes(uploaded),
                    rhi.textureFastCopies(),
                    rhi.textureShaderCopies(),
                    rhi.ringWraps(),
                    rhi.ringStalls(),
                    rhi.immediateFallbackUploads(),
                    rhi.dynamicArenaBacklogEvents()));
        }
        if (uniforms != null || resources != null) {
            long uniformWrites = uniforms == null ? 0L : uniforms.writes();
            long uniformBytes = uniforms == null ? 0L : uniforms.uploadedBytes();
            int retireBacklog = resources == null ? 0 : resources.retirementBacklog();
            int leaks = resources == null ? 0 : resources.leakedResources();
            lines.add(String.format(Locale.ROOT,
                    "[FrameStutter] uniforms writes=%d/%s resourceBacklog=%d leaks=%d",
                    uniformWrites,
                    formatBytes(uniformBytes),
                    retireBacklog,
                    leaks));
        }

        if (gcEvents.isEmpty()) {
            lines.add("[FrameStutter] gc nearby: none");
        } else {
            for (GcEvent gc : gcEvents) {
                double relativeEndMs = (gc.endNs - frameStartNs) / 1_000_000.0;
                lines.add(String.format(Locale.ROOT,
                        "[FrameStutter] gc nearby: %s %s %.2f ms at %+,.2f ms, freed %s",
                        gc.name,
                        gc.cause,
                        (double) gc.durationMs,
                        relativeEndMs,
                        formatBytes(gc.freedBytes)));
            }
        }

        appendSampleSummary(lines, samples);
        return lines;
    }

    private static void appendSampleSummary(List<String> lines, List<Sample> samples) {
        if (samples.isEmpty()) {
            lines.add("[FrameStutter] sample top: none");
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        int glWait = 0;
        int glUpload = 0;
        int glDraw = 0;
        int glState = 0;
        for (Sample sample : samples) {
            counts.merge(sample.stack, 1, Integer::sum);
            if (sample.stack.startsWith("GL WAIT:")) {
                glWait++;
            } else if (sample.stack.startsWith("GL UPLOAD:")) {
                glUpload++;
            } else if (sample.stack.startsWith("GL DRAW:")) {
                glDraw++;
            } else if (sample.stack.startsWith("GL STATE:")) {
                glState++;
            }
        }
        lines.add(String.format(Locale.ROOT,
                "[FrameStutter] gl samples wait/upload/draw/state=%d/%d/%d/%d",
                glWait,
                glUpload,
                glDraw,
                glState));

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        lines.add("[FrameStutter] sample top:");
        int top = Math.min(TOP_STACKS, entries.size());
        for (int i = 0; i < top; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            double pct = entry.getValue() * 100.0 / samples.size();
            lines.add(String.format(Locale.ROOT,
                    "[FrameStutter]   %d) %s (%d, %.1f%%)",
                    i + 1,
                    entry.getKey(),
                    entry.getValue(),
                    pct));
        }
    }

    private static List<Sample> samplesInWindow(long startNs, long endNs) {
        List<Sample> out = new ArrayList<>();
        synchronized (LOCK) {
            for (int i = 0; i < sampleCount; i++) {
                Sample sample = SAMPLES[i];
                if (sample != null && sample.timeNs >= startNs && sample.timeNs <= endNs) {
                    out.add(sample);
                }
            }
        }
        out.sort((a, b) -> Long.compare(a.timeNs, b.timeNs));
        return out;
    }

    private static List<GcEvent> gcNearWindow(long startNs, long endNs) {
        long from = startNs - GC_CORRELATION_WINDOW_NS;
        long to = endNs + GC_CORRELATION_WINDOW_NS;
        List<GcEvent> out = new ArrayList<>();
        synchronized (LOCK) {
            for (int i = 0; i < gcCount; i++) {
                GcEvent event = GC_EVENTS[i];
                if (event != null && event.endNs >= from && event.endNs <= to) {
                    out.add(event);
                }
            }
        }
        out.sort((a, b) -> Long.compare(a.endNs, b.endNs));
        return out;
    }

    private static void ensureSampler() {
        if (running || output == ProfilerSettings.OutputMode.OFF) return;
        running = true;
        samplerThread = new Thread(DevFrameStutterProfiler::sampleLoop, "Combatant-DevFrameStutterProfiler");
        samplerThread.setDaemon(true);
        samplerThread.start();
    }

    private static void stopSampler() {
        running = false;
        Thread thread = samplerThread;
        samplerThread = null;
        if (thread != null) {
            thread.interrupt();
        }
        renderThread = null;
    }

    private static void sampleLoop() {
        long lastSampleNs = 0L;
        while (running) {
            if (output == ProfilerSettings.OutputMode.OFF || RuntimeGate.isPanic()) {
                sleepMs(20L);
                continue;
            }
            Thread target = renderThread;
            if (target == null) {
                sleepMs(10L);
                continue;
            }
            long now = System.nanoTime();
            if (now - lastSampleNs >= sampleIntervalNs) {
                recordSample(now, target);
                lastSampleNs = now;
            }
            sleepMs(1L);
        }
    }

    private static void recordSample(long now, Thread target) {
        StackTraceElement[] stack = target.getStackTrace();
        if (stack == null || stack.length == 0) return;
        String key = buildKey(stack);
        if (key == null || key.isBlank()) return;
        synchronized (LOCK) {
            SAMPLES[sampleCursor] = new Sample(now, key);
            sampleCursor = (sampleCursor + 1) % MAX_SAMPLES;
            if (sampleCount < MAX_SAMPLES) {
                sampleCount++;
            }
        }
    }

    private static String buildKey(StackTraceElement[] stack) {
        StringBuilder sb = new StringBuilder(320);
        String glCat = glCategory(stack);
        if (glCat != null) {
            sb.append("GL ").append(glCat).append(": ");
        }
        int added = 0;
        for (StackTraceElement e : stack) {
            if (added >= MAX_STACK_FRAMES) break;
            if (isNoise(e)) continue;
            if (added > 0) sb.append(" <= ");
            sb.append(formatElement(e));
            added++;
        }
        return added == 0 ? null : sb.toString();
    }

    private static String glCategory(StackTraceElement[] stack) {
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            String method = e.getMethodName();
            if (!isGlFrame(cls)) continue;
            if (method == null) continue;
            if (method.equals("glfwSwapBuffers")
                    || method.equals("glClientWaitSync")
                    || method.equals("nglClientWaitSync")
                    || method.equals("glWaitSync")
                    || method.equals("glFinish")) {
                return "WAIT";
            }
            if (method.startsWith("glDraw") || method.startsWith("glMultiDraw")) {
                return "DRAW";
            }
            if (method.contains("BufferSubData")
                    || method.contains("BufferData")
                    || method.contains("TexImage")
                    || method.contains("TexSubImage")
                    || method.contains("FlushMapped")
                    || method.contains("MapBuffer")
                    || method.contains("MapBufferRange")
                    || method.contains("UnmapBuffer")) {
                return "UPLOAD";
            }
            if (method.startsWith("glUseProgram")
                    || method.startsWith("glBind")
                    || method.startsWith("glTexParameter")
                    || method.startsWith("glBlend")
                    || method.startsWith("glEnable")
                    || method.startsWith("glDisable")
                    || method.startsWith("glDepth")
                    || method.startsWith("glColorMask")
                    || method.startsWith("glViewport")
                    || method.startsWith("glScissor")
                    || method.startsWith("glActiveTexture")
                    || method.startsWith("glUniform")
                    || method.startsWith("glVertexAttrib")) {
                return "STATE";
            }
        }
        return null;
    }

    private static boolean isGlFrame(String cls) {
        return cls != null
                && (cls.startsWith("org.lwjgl.opengl.")
                || cls.startsWith("org.lwjgl.glfw.")
                || cls.startsWith("com.mojang.blaze3d.platform.")
                || cls.startsWith("com.mojang.blaze3d.systems."));
    }

    private static boolean isNoise(StackTraceElement e) {
        String cls = e.getClassName();
        return cls.startsWith("java.lang.Thread")
                || cls.startsWith("jdk.internal")
                || cls.startsWith("sun.")
                || cls.startsWith("combatant.client.render.engine.profiler.DevFrameStutterProfiler");
    }

    private static String formatElement(StackTraceElement e) {
        String file = e.getFileName() == null ? "Unknown" : e.getFileName();
        return e.getClassName() + "." + e.getMethodName() + "(" + file + ":" + e.getLineNumber() + ")";
    }

    private static void installGcListener() {
        if (gcListenerInstalled) return;
        synchronized (LOCK) {
            if (gcListenerInstalled) return;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (bean instanceof NotificationEmitter emitter) {
                    emitter.addNotificationListener(DevFrameStutterProfiler::onGcNotification, null, null);
                }
            }
            gcListenerInstalled = true;
        }
    }

    private static void onGcNotification(Notification notification, Object handback) {
        if (output == ProfilerSettings.OutputMode.OFF) return;
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;
        Object data = notification.getUserData();
        if (!(data instanceof CompositeData compositeData)) return;
        try {
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(compositeData);
            GcInfo gcInfo = info.getGcInfo();
            long freed = usedBefore(gcInfo) - usedAfter(gcInfo);
            GcEvent event = new GcEvent(
                    System.nanoTime(),
                    info.getGcName(),
                    info.getGcCause(),
                    Math.max(0L, gcInfo.getDuration()),
                    Math.max(0L, freed));
            synchronized (LOCK) {
                GC_EVENTS[gcCursor] = event;
                gcCursor = (gcCursor + 1) % MAX_GC_EVENTS;
                if (gcCount < MAX_GC_EVENTS) {
                    gcCount++;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static long usedBefore(GcInfo info) {
        return used(info == null ? null : info.getMemoryUsageBeforeGc());
    }

    private static long usedAfter(GcInfo info) {
        return used(info == null ? null : info.getMemoryUsageAfterGc());
    }

    private static long used(Map<String, MemoryUsage> usage) {
        if (usage == null || usage.isEmpty()) return 0L;
        long total = 0L;
        for (MemoryUsage value : usage.values()) {
            if (value != null && value.getUsed() > 0L) {
                total += value.getUsed();
            }
        }
        return total;
    }

    private static void emit(List<String> lines) {
        ProfilerSettings.OutputMode mode = output;
        if (mode == ProfilerSettings.OutputMode.OFF || lines == null || lines.isEmpty()) return;
        if (mode == ProfilerSettings.OutputMode.LOG) {
            for (String line : lines) {
                ProfilerLog.info("%s", line);
            }
            return;
        }
        if (mode == ProfilerSettings.OutputMode.CHAT) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gui == null) return;
            for (String line : lines) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal(line));
            }
        }
    }

    private static long clampMs(double valueMs, double minMs, double maxMs, long fallbackNs) {
        if (!Double.isFinite(valueMs) || valueMs <= 0.0) return fallbackNs;
        double clamped = Math.max(minMs, Math.min(maxMs, valueMs));
        return (long) (clamped * 1_000_000.0);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KiB", kb);
        return String.format(Locale.ROOT, "%.2f MiB", kb / 1024.0);
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private record Sample(long timeNs, String stack) {
    }

    private record GcEvent(long endNs, String name, String cause, long durationMs, long freedBytes) {
    }
}
