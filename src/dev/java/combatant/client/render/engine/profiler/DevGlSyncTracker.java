package combatant.client.render.engine.profiler;

import java.util.*;

public enum DevGlSyncTracker {
    ;

    private static final int MAX_FENCES = 512;
    private static final int TOP_PHASES = 4;
    private static final int TOP_STACKS = 3;
    private static final int MAX_STACK_FRAMES = 8;
    private static final int MAX_SLOW_WAITS = 3;
    private static final long SLOW_WAIT_THRESHOLD_NS = 1_000_000L;

    private static final Object LOCK = new Object();
    private static final LinkedHashMap<Long, FenceInfo> FENCE_INFOS = new LinkedHashMap<>();
    private static final Map<String, Integer> WAIT_BY_FENCE_PHASE = new HashMap<>();
    private static final Map<String, Integer> WAIT_BY_WAIT_PHASE = new HashMap<>();
    private static final Map<String, Long> WAIT_TIME_BY_FENCE_PHASE = new HashMap<>();
    private static final Map<String, Long> WAIT_TIME_BY_WAIT_PHASE = new HashMap<>();
    private static final Map<String, StackAggregate> WAIT_BY_CREATE_STACK = new HashMap<>();
    private static final List<SlowWait> SLOW_WAITS = new ArrayList<>();
    private static final ThreadLocal<WaitState> WAIT_STATE =
            ThreadLocal.withInitial(WaitState::new);
    private static int waitTotal;
    private static long waitTimeTotalNs;
    private static int tracyWaitCallsFrame;
    private static long tracyWaitTimeFrameNs;

    public static void onFence(long sync) {
        if (!shouldTrackWaits()) return;
        if (sync == 0L) return;
        String phase = DevProfilerPhase.current();
        String createStack = shouldCaptureStacks() ? captureStackTrace() : null;
        synchronized (LOCK) {
            FENCE_INFOS.put(sync, new FenceInfo(phase, createStack));
            trimFences();
        }
    }

    public static void onWaitStart(long sync) {
        if (!shouldTrackWaits()) return;
        WaitState state = WAIT_STATE.get();
        state.startNs = System.nanoTime();
        state.waitPhase = DevProfilerPhase.current();
        state.waitStack = null;
        FenceInfo fenceInfo;
        synchronized (LOCK) {
            fenceInfo = FENCE_INFOS.remove(sync);
        }
        state.fencePhase = fenceInfo == null || fenceInfo.phase == null ? "unknown" : fenceInfo.phase;
        state.createStack = fenceInfo == null ? null : fenceInfo.createStack;
        state.active = true;
    }

    public static void onWaitEnd() {
        WaitState state = WAIT_STATE.get();
        if (state == null || !state.active) return;
        state.active = false;
        long durationNs = System.nanoTime() - state.startNs;
        if (durationNs < 0L) durationNs = 0L;
        if (!shouldTrackWaits()) return;
        String fencePhase = state.fencePhase == null ? "unknown" : state.fencePhase;
        String waitPhase = state.waitPhase == null ? "unknown" : state.waitPhase;
        boolean captureStacks = shouldCaptureStacks();
        String createStack = captureStacks ? normalizeStack(state.createStack) : null;
        String waitStack = captureStacks && durationNs >= SLOW_WAIT_THRESHOLD_NS ? captureStackTrace() : null;
        synchronized (LOCK) {
            WAIT_BY_FENCE_PHASE.merge(fencePhase, 1, Integer::sum);
            WAIT_BY_WAIT_PHASE.merge(waitPhase, 1, Integer::sum);
            WAIT_TIME_BY_FENCE_PHASE.merge(fencePhase, durationNs, Long::sum);
            WAIT_TIME_BY_WAIT_PHASE.merge(waitPhase, durationNs, Long::sum);
            if (captureStacks) {
                StackAggregate aggregate = WAIT_BY_CREATE_STACK.computeIfAbsent(
                        createStack,
                        key -> new StackAggregate(fencePhase, key)
                );
                aggregate.count += 1;
                aggregate.timeNs += durationNs;
            }
            if (durationNs >= SLOW_WAIT_THRESHOLD_NS) {
                recordSlowWait(new SlowWait(
                        durationNs,
                        fencePhase,
                        waitPhase,
                        createStack,
                        captureStacks ? normalizeStack(waitStack) : null
                ));
            }
            waitTotal += 1;
            waitTimeTotalNs += durationNs;
            tracyWaitCallsFrame += 1;
            tracyWaitTimeFrameNs += durationNs;
        }
    }

    public static void emitTracyFrame() {
        if (!DevTracyProfiler.isEnabled()) return;
        int calls;
        long totalNs;
        synchronized (LOCK) {
            calls = tracyWaitCallsFrame;
            totalNs = tracyWaitTimeFrameNs;
            tracyWaitCallsFrame = 0;
            tracyWaitTimeFrameNs = 0L;
        }
        DevTracyProfiler.plotGlWait(totalNs / 1_000_000.0, calls);
    }

    public static List<String> drainLines() {
        if (!DevSamplingProfiler.isActive()) return List.of();
        List<String> lines = new ArrayList<>();
        Map<String, Integer> byFence;
        Map<String, Integer> byWait;
        Map<String, Long> timeByFence;
        Map<String, Long> timeByWait;
        List<StackAggregate> createStacks;
        List<SlowWait> slowWaits;
        int total;
        long totalNs;
        synchronized (LOCK) {
            if (waitTotal == 0) return List.of();
            total = waitTotal;
            totalNs = waitTimeTotalNs;
            byFence = new HashMap<>(WAIT_BY_FENCE_PHASE);
            byWait = new HashMap<>(WAIT_BY_WAIT_PHASE);
            timeByFence = new HashMap<>(WAIT_TIME_BY_FENCE_PHASE);
            timeByWait = new HashMap<>(WAIT_TIME_BY_WAIT_PHASE);
            createStacks = new ArrayList<>(WAIT_BY_CREATE_STACK.values());
            slowWaits = new ArrayList<>(SLOW_WAITS);
            WAIT_BY_FENCE_PHASE.clear();
            WAIT_BY_WAIT_PHASE.clear();
            WAIT_TIME_BY_FENCE_PHASE.clear();
            WAIT_TIME_BY_WAIT_PHASE.clear();
            WAIT_BY_CREATE_STACK.clear();
            SLOW_WAITS.clear();
            waitTotal = 0;
            waitTimeTotalNs = 0L;
        }
        lines.add(String.format("gl wait calls: %d", total));
        lines.add("gl wait phase (fence): " + formatTop(byFence, total));
        lines.add("gl wait phase (wait): " + formatTop(byWait, total));
        if (totalNs > 0L) {
            lines.add(String.format("gl wait time total: %s", formatDuration(totalNs)));
            lines.add("gl wait time (fence): " + formatTopTime(timeByFence, totalNs));
            lines.add("gl wait time (wait): " + formatTopTime(timeByWait, totalNs));
        }
        String createStackSummary = formatTopCreateStacks(createStacks, totalNs);
        if (createStackSummary != null) {
            lines.add("gl wait source (create): " + createStackSummary);
        }
        if (!slowWaits.isEmpty()) {
            lines.add("gl wait slow traces:");
            for (int i = 0; i < slowWaits.size(); i++) {
                SlowWait slow = slowWaits.get(i);
                lines.add(String.format(
                        "  %d) %s fence=%s wait=%s",
                        i + 1,
                        formatDuration(slow.durationNs),
                        slow.fencePhase,
                        slow.waitPhase
                ));
                if (slow.createStack != null) {
                    lines.add("     create: " + slow.createStack);
                }
                if (slow.waitStack != null) {
                    lines.add("     wait: " + slow.waitStack);
                }
            }
        }
        return lines;
    }

    private static String formatTop(Map<String, Integer> src, int total) {
        if (src.isEmpty()) return "n/a";
        List<Map.Entry<String, Integer>> list = new ArrayList<>(src.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int top = Math.min(TOP_PHASES, list.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < top; i++) {
            Map.Entry<String, Integer> e = list.get(i);
            double pct = total == 0 ? 0.0 : (e.getValue() * 100.0) / total;
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey()).append('=').append(e.getValue())
                    .append(String.format(" (%.1f%%)", pct));
        }
        return sb.toString();
    }

    private static String formatTopTime(Map<String, Long> src, long totalNs) {
        if (src.isEmpty()) return "n/a";
        List<Map.Entry<String, Long>> list = new ArrayList<>(src.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        int top = Math.min(TOP_PHASES, list.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < top; i++) {
            Map.Entry<String, Long> e = list.get(i);
            double pct = totalNs == 0L ? 0.0 : (e.getValue() * 100.0) / totalNs;
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey()).append('=').append(formatDuration(e.getValue()))
                    .append(String.format(" (%.1f%%)", pct));
        }
        return sb.toString();
    }

    private static String formatDuration(long ns) {
        double ms = ns / 1_000_000.0;
        return String.format("%.2fms", ms);
    }

    private static void trimFences() {
        if (FENCE_INFOS.size() <= MAX_FENCES) return;
        int toRemove = FENCE_INFOS.size() - MAX_FENCES;
        var it = FENCE_INFOS.keySet().iterator();
        while (toRemove > 0 && it.hasNext()) {
            it.next();
            it.remove();
            toRemove--;
        }
    }

    private static String formatTopCreateStacks(List<StackAggregate> stacks, long totalNs) {
        if (stacks == null || stacks.isEmpty()) return null;
        stacks.sort((a, b) -> Long.compare(b.timeNs, a.timeNs));
        int top = Math.min(TOP_STACKS, stacks.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < top; i++) {
            StackAggregate aggregate = stacks.get(i);
            double pct = totalNs == 0L ? 0.0 : (aggregate.timeNs * 100.0) / totalNs;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(aggregate.fencePhase)
                    .append('=')
                    .append(formatDuration(aggregate.timeNs))
                    .append(" x")
                    .append(aggregate.count)
                    .append(String.format(" (%.1f%%)", pct))
                    .append(" :: ")
                    .append(aggregate.createStack);
        }
        return sb.toString();
    }

    private static void recordSlowWait(SlowWait slowWait) {
        SLOW_WAITS.add(slowWait);
        SLOW_WAITS.sort((a, b) -> Long.compare(b.durationNs, a.durationNs));
        while (SLOW_WAITS.size() > MAX_SLOW_WAITS) {
            SLOW_WAITS.remove(SLOW_WAITS.size() - 1);
        }
    }

    private static String captureStackTrace() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder(256);
        int added = 0;
        for (StackTraceElement e : stack) {
            if (added >= MAX_STACK_FRAMES) break;
            if (isStackNoise(e)) continue;
            if (added > 0) sb.append(" <= ");
            sb.append(formatElement(e));
            added++;
        }
        return added == 0 ? "unknown" : sb.toString();
    }

    private static String normalizeStack(String stack) {
        return stack == null || stack.isBlank() ? "unknown" : stack;
    }

    private static boolean shouldCaptureStacks() {
        return ProfilerSettings.isSampleDetailedStacks();
    }

    private static boolean shouldTrackWaits() {
        return DevSamplingProfiler.isActive() || DevTracyProfiler.isEnabled();
    }

    private static boolean isStackNoise(StackTraceElement e) {
        String cls = e.getClassName();
        String method = e.getMethodName();
        return cls.equals(Thread.class.getName())
                || cls.startsWith("java.lang.Thread")
                || cls.startsWith("jdk.internal")
                || cls.startsWith("sun.")
                || cls.startsWith("org.lwjgl.opengl.GL")
                || cls.startsWith("org.lwjgl.system.JNI")
                || cls.equals(DevGlSyncTracker.class.getName())
                || cls.equals("combatant.client.mixins.GlStateManagerMixin")
                || (cls.equals("com.mojang.blaze3d.opengl.GlStateManager")
                && ("_glFenceSync".equals(method) || "_glClientWaitSync".equals(method)));
    }

    private static String formatElement(StackTraceElement e) {
        String cls = e.getClassName();
        int idx = cls.lastIndexOf('.');
        String simple = idx >= 0 ? cls.substring(idx + 1) : cls;
        return simple + "." + e.getMethodName() + "(" + e.getFileName() + ":" + e.getLineNumber() + ")";
    }

    public static void reset() {
        synchronized (LOCK) {
            FENCE_INFOS.clear();
            WAIT_BY_FENCE_PHASE.clear();
            WAIT_BY_WAIT_PHASE.clear();
            WAIT_TIME_BY_FENCE_PHASE.clear();
            WAIT_TIME_BY_WAIT_PHASE.clear();
            WAIT_BY_CREATE_STACK.clear();
            SLOW_WAITS.clear();
            waitTotal = 0;
            waitTimeTotalNs = 0L;
        }
    }

    private record FenceInfo(String phase, String createStack) {
    }

    private static final class StackAggregate {
        final String fencePhase;
        final String createStack;
        int count;
        long timeNs;

        StackAggregate(String fencePhase, String createStack) {
            this.fencePhase = fencePhase;
            this.createStack = createStack;
        }
    }

    private record SlowWait(long durationNs, String fencePhase, String waitPhase, String createStack,
                            String waitStack) {
    }

    private static final class WaitState {
        long startNs;
        String fencePhase;
        String waitPhase;
        String createStack;
        String waitStack;
        boolean active;
    }
}
