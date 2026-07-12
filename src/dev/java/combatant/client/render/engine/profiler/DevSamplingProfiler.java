package combatant.client.render.engine.profiler;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import combatant.client.runtime.RuntimeGate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum DevSamplingProfiler {
    ;

    private static final long SAMPLE_INTERVAL_NS = 10_000_000L; // 10 ms
    private static final long EMIT_INTERVAL_NS = 1_000_000_000L; // 1 s
    private static final int TOP_LIMIT = 8;
    private static final int TOP_LIMIT_NON_GL = 4;
    private static final int TOP_LIMIT_GL_WAIT = 3;
    private static final int MAX_STACK_FRAMES = 4;
    private static final int MAX_ENTRIES = 256;

    private static final Object LOCK = new Object();
    private static final Map<String, Integer> COUNTS = new HashMap<>();
    private static int idleSamples;

    private static volatile Thread targetThread;
    private static volatile Thread samplerThread;
    private static volatile boolean running;
    private static volatile long lastEmitNs;
    private static volatile List<String> pendingChatLines;
    private static volatile Target lastSource = Target.WORLD;

    public static void onClientFrame(Target source) {
        if (!isEnabled()) {
            DevProfilerPhase.clearCurrent();
            stopIfRunning();
            return;
        }
        if (RuntimeGate.isPanic()) return;
        lastSource = source;
        flushPendingChat();
        if (!matchesTarget(source)) return;
        targetThread = Thread.currentThread();
        ensureRunning();
    }

    private static boolean isEnabled() {
        return getOutputModeRaw() != ProfilerSettings.OutputMode.OFF;
    }

    public static boolean isActive() {
        return getOutputModeRaw() != ProfilerSettings.OutputMode.OFF;
    }

    private static void ensureRunning() {
        if (running) return;
        running = true;
        samplerThread = new Thread(DevSamplingProfiler::runLoop, "Combatant-DevSamplingProfiler");
        samplerThread.setDaemon(true);
        samplerThread.start();
    }

    private static void stopIfRunning() {
        if (!running) return;
        running = false;
        Thread t = samplerThread;
        samplerThread = null;
        if (t != null) t.interrupt();
        synchronized (LOCK) {
            COUNTS.clear();
            idleSamples = 0;
        }
        DevGlSyncTracker.reset();
        pendingChatLines = null;
    }

    private static void runLoop() {
        long lastSampleNs = 0L;
        while (running) {
            if (!isEnabled()) {
                running = false;
                synchronized (LOCK) {
                    COUNTS.clear();
                    idleSamples = 0;
                }
                DevGlSyncTracker.reset();
                pendingChatLines = null;
                break;
            }
            if (RuntimeGate.isPanic()) {
                sleepMs(20);
                continue;
            }
            Thread t = targetThread;
            if (t == null) {
                sleepMs(10);
                continue;
            }
            long now = System.nanoTime();
            if (now - lastSampleNs >= SAMPLE_INTERVAL_NS) {
                if (matchesTarget(lastSource)) {
                    sample(t);
                }
                lastSampleNs = now;
            }
            if (now - lastEmitNs >= EMIT_INTERVAL_NS) {
                emit(now);
            }
            sleepMs(2);
        }
    }

    private static void sample(Thread t) {
        StackTraceElement[] stack = t.getStackTrace();
        if (stack == null || stack.length == 0) return;
        if (isIdleStack(stack)) {
            synchronized (LOCK) {
                idleSamples += 1;
            }
            return;
        }
        String key = buildKey(stack);
        if (key == null || key.isEmpty()) return;
        synchronized (LOCK) {
            COUNTS.merge(key, 1, Integer::sum);
            if (COUNTS.size() > MAX_ENTRIES) {
                trimToLimit();
            }
        }
    }

    private static void emit(long now) {
        ProfilerSettings.OutputMode mode = getOutputModeRaw();
        if (mode == ProfilerSettings.OutputMode.OFF) return;
        List<String> lines = buildOutputLines();
        if (lines.isEmpty()) return;
        lastEmitNs = now;

        if (mode == ProfilerSettings.OutputMode.LOG) {
            for (String line : lines) {
                ProfilerLog.info("[SampleProfiler] %s", line);
            }
        } else if (mode == ProfilerSettings.OutputMode.CHAT) {
            pendingChatLines = lines;
        }

        synchronized (LOCK) {
            COUNTS.clear();
            idleSamples = 0;
        }
    }

    private static List<String> buildOutputLines() {
        List<Map.Entry<String, Integer>> entries;
        int total;
        int idle;
        synchronized (LOCK) {
            if (COUNTS.isEmpty() && idleSamples == 0) return List.of();
            entries = new ArrayList<>(COUNTS.entrySet());
            int sum = 0;
            for (Integer count : COUNTS.values()) {
                if (count != null) {
                    sum += count;
                }
            }
            total = sum;
            idle = idleSamples;
        }
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<String> lines = new ArrayList<>();
        int all = total + idle;
        double idlePct = all == 0 ? 0.0 : (idle * 100.0) / all;
        lines.add(String.format("sample prof: %d samples, idle %d (%.1f%%), entries %d", all, idle, idlePct, entries.size()));

        int glWait = 0;
        int glUpload = 0;
        int glDraw = 0;
        int glState = 0;
        int glWaitClient = 0;
        int glWaitSwap = 0;
        int glWaitFinish = 0;
        int glWaitSync = 0;
        int glWaitOther = 0;

        List<Map.Entry<String, Integer>> nonGl = new ArrayList<>();
        List<Map.Entry<String, Integer>> glWaitList = new ArrayList<>();
        for (Map.Entry<String, Integer> e : entries) {
            String k = e.getKey();
            int c = e.getValue();
            if (k.startsWith("GL WAIT:")) {
                glWait += c;
                glWaitList.add(e);
                if (containsAny(k, "glClientWaitSync", "nglClientWaitSync")) {
                    glWaitClient += c;
                } else if (containsAny(k, "glfwSwapBuffers")) {
                    glWaitSwap += c;
                } else if (containsAny(k, "glFinish", "nglFinish")) {
                    glWaitFinish += c;
                } else if (containsAny(k, "glWaitSync", "nglWaitSync")) {
                    glWaitSync += c;
                } else {
                    glWaitOther += c;
                }
            } else if (k.startsWith("GL UPLOAD:")) {
                glUpload += c;
            } else if (k.startsWith("GL DRAW:")) {
                glDraw += c;
            } else if (k.startsWith("GL STATE:")) {
                glState += c;
            } else {
                nonGl.add(e);
            }
        }
        int glTotal = glWait + glUpload + glDraw + glState;
        if (glTotal > 0) {
            double waitPct = all == 0 ? 0.0 : (glWait * 100.0) / all;
            double uploadPct = all == 0 ? 0.0 : (glUpload * 100.0) / all;
            double drawPct = all == 0 ? 0.0 : (glDraw * 100.0) / all;
            double statePct = all == 0 ? 0.0 : (glState * 100.0) / all;
            lines.add(String.format(
                    "gl samples: wait %d (%.1f%%), upload %d (%.1f%%), draw %d (%.1f%%), state %d (%.1f%%)",
                    glWait, waitPct, glUpload, uploadPct, glDraw, drawPct, glState, statePct
            ));
            if (glWait > 0) {
                lines.add(String.format(
                        "gl wait breakdown: client %d, swap %d, finish %d, waitsync %d, other %d",
                        glWaitClient, glWaitSwap, glWaitFinish, glWaitSync, glWaitOther
                ));
            }
        }

        List<String> waitPhaseLines = DevGlSyncTracker.drainLines();
        if (!waitPhaseLines.isEmpty()) {
            lines.addAll(waitPhaseLines);
        }
        lines.add("sample prof top:");

        int top = Math.min(TOP_LIMIT, entries.size());
        for (int i = 0; i < top; i++) {
            Map.Entry<String, Integer> e = entries.get(i);
            double pct = all == 0 ? 0.0 : (e.getValue() * 100.0) / all;
            lines.add(String.format("  %d) %s (%d, %.1f%%)", i + 1, e.getKey(), e.getValue(), pct));
        }

        if (!nonGl.isEmpty()) {
            int topNon = Math.min(TOP_LIMIT_NON_GL, nonGl.size());
            lines.add("sample prof top non-gl:");
            for (int i = 0; i < topNon; i++) {
                Map.Entry<String, Integer> e = nonGl.get(i);
                double pct = all == 0 ? 0.0 : (e.getValue() * 100.0) / all;
                lines.add(String.format("  %d) %s (%d, %.1f%%)", i + 1, e.getKey(), e.getValue(), pct));
            }
        }

        if (!glWaitList.isEmpty()) {
            int topWait = Math.min(TOP_LIMIT_GL_WAIT, glWaitList.size());
            lines.add("sample prof top gl wait:");
            for (int i = 0; i < topWait; i++) {
                Map.Entry<String, Integer> e = glWaitList.get(i);
                double pct = all == 0 ? 0.0 : (e.getValue() * 100.0) / all;
                lines.add(String.format("  %d) %s (%d, %.1f%%)", i + 1, e.getKey(), e.getValue(), pct));
            }
        }
        return lines;
    }

    private static void flushPendingChat() {
        List<String> lines = pendingChatLines;
        if (lines == null || lines.isEmpty()) return;
        pendingChatLines = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) return;
        for (String line : lines) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[SampleProfiler] " + line));
        }
    }

    private static String buildKey(StackTraceElement[] stack) {
        StringBuilder sb = new StringBuilder();
        String glCat = glCategory(stack);
        boolean hasPrefix = false;
        if (glCat != null) {
            sb.append("GL ").append(glCat).append(": ");
            hasPrefix = true;
        }
        int added = 0;
        for (StackTraceElement e : stack) {
            if (added >= MAX_STACK_FRAMES) break;
            if (isNoise(e)) continue;
            if (added > 0 || (sb.length() > 0 && !hasPrefix)) sb.append(" <= ");
            sb.append(formatElement(e));
            added++;
        }
        return added == 0 ? null : sb.toString();
    }

    private static boolean isNoise(StackTraceElement e) {
        String cls = e.getClassName();
        return cls.startsWith("java.lang.Thread")
                || cls.startsWith("java.util.concurrent")
                || cls.startsWith("jdk.internal")
                || cls.startsWith("sun.")
                || cls.startsWith("combatant.client.render.engine.profiler.DevSamplingProfiler");
    }

    private static String glCategory(StackTraceElement[] stack) {
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            String method = e.getMethodName();
            if (!isGlFrame(cls)) continue;
            String cat = glCategoryForMethod(method);
            if (cat != null) return cat;
        }
        return null;
    }

    private static boolean isGlFrame(String cls) {
        if (cls == null) return false;
        return cls.startsWith("org.lwjgl.opengl.")
                || cls.startsWith("org.lwjgl.glfw.")
                || cls.startsWith("com.mojang.blaze3d.platform.")
                || cls.startsWith("com.mojang.blaze3d.systems.");
    }

    private static String glCategoryForMethod(String method) {
        if (method == null) return null;
        if (method.equals("glfwSwapBuffers")
                || method.equals("glClientWaitSync")
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
                || method.contains("CompressedTex")
                || method.contains("FlushMapped")
                || method.contains("MapBuffer")
                || method.contains("MapBufferRange")
                || method.contains("UnmapBuffer")) {
            return "UPLOAD";
        }
        if (method.startsWith("glUseProgram")
                || method.startsWith("glBind")
                || method.startsWith("glTexParameter")
                || method.startsWith("glBlendFunc")
                || method.startsWith("glBlendEquation")
                || method.startsWith("glEnable")
                || method.startsWith("glDisable")
                || method.startsWith("glDepthFunc")
                || method.startsWith("glDepthMask")
                || method.startsWith("glColorMask")
                || method.startsWith("glCullFace")
                || method.startsWith("glFrontFace")
                || method.startsWith("glViewport")
                || method.startsWith("glScissor")
                || method.startsWith("glPolygonMode")
                || method.startsWith("glActiveTexture")
                || method.startsWith("glUniform")
                || method.startsWith("glVertexAttrib")) {
            return "STATE";
        }
        return null;
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String n : needles) {
            if (n != null && !n.isEmpty() && text.contains(n)) return true;
        }
        return false;
    }

    private static boolean isIdleStack(StackTraceElement[] stack) {
        boolean hasWait = false;
        boolean hasLimit = false;
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            String method = e.getMethodName();
            if ("org.lwjgl.glfw.GLFW".equals(cls) && "glfwWaitEventsTimeout".equals(method)) {
                hasWait = true;
            }
            if ("com.mojang.blaze3d.systems.RenderSystem".equals(cls) && "limitDisplayFPS".equals(method)) {
                hasLimit = true;
            }
            if (hasWait || hasLimit) {
                return true;
            }
        }
        return false;
    }

    private static String formatElement(StackTraceElement e) {
        String file = e.getFileName() == null ? "Unknown" : e.getFileName();
        int line = e.getLineNumber();
        return e.getClassName() + "." + e.getMethodName() + "(" + file + ":" + line + ")";
    }

    private static boolean matchesTarget(Target source) {
        if (source == null) return true;
        Target target = getTargetRaw();
        return target == Target.ALL || target == source;
    }

    private static Target getTargetRaw() {
        ProfilerSettings.SampleTarget target = ProfilerSettings.getSampleTarget();
        if (target == null) return Target.ALL;
        return switch (target) {
            case D2 -> Target.UI;
            case D3 -> Target.WORLD;
            case ALL -> Target.ALL;
        };
    }

    private static void trimToLimit() {
        if (COUNTS.size() <= MAX_ENTRIES) return;
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(COUNTS.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (int i = MAX_ENTRIES; i < entries.size(); i++) {
            COUNTS.remove(entries.get(i).getKey());
        }
    }

    private static ProfilerSettings.OutputMode getOutputModeRaw() {
        return ProfilerSettings.getSampleOutput();
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // ignore
        }
    }

    public enum Target {
        ALL,
        UI,
        WORLD
    }
}
