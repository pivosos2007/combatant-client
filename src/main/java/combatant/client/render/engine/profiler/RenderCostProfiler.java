/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import java.util.List;

/**
 * Named render-cost zones backed by Mojang's profiler/Tracy integration.
 *
 * <p>This class deliberately records durations only. Fine-grained UI-node
 * instrumentation is disabled because it creates excessive Tracy zone volume
 * and can become measurable profiling overhead by itself.</p>
 */
public enum RenderCostProfiler {
    ;

    public static void beginFrame(long frameId) {
        // RenderFrameProfiler owns the frame-wide zone.
    }

    public static void endFrame() {
        // RenderFrameProfiler owns the frame-wide zone.
    }

    public static Snapshot snapshot() {
        return Snapshot.EMPTY;
    }

    public static boolean isEnabled() {
        return ProfilerPhase.isActive();
    }

    public static boolean isConfigured() {
        return ProfilerPhase.isActive();
    }

    public static Scope scope(String domain, String name) {
        if (!ProfilerPhase.isActive()) return Scope.NOOP;
        return open("render_cost:" + safe(domain) + ":" + safe(name));
    }

    public static Scope phase(String label) {
        if (!ProfilerPhase.isActive()) return Scope.NOOP;
        return open("render_phase:" + safe(label));
    }

    public static Scope uiNode(Object label) {
        // Intentionally disabled: one zone per retained UI node creates too much trace noise.
        return Scope.NOOP;
    }

    public static Scope uiRuntime(String label) {
        if (!ProfilerPhase.isActive()) return Scope.NOOP;
        return open("ui_runtime:" + safe(label));
    }

    public static Scope uiEffect(String label) {
        if (!ProfilerPhase.isActive()) return Scope.NOOP;
        return open("ui_effect:" + safe(label));
    }

    public static Scope postPass(String label) {
        if (!ProfilerPhase.isActive()) return Scope.NOOP;
        return open("postprocess:" + safe(label));
    }

    public static Scope rhiDraw(String label) {
        if (!ProfilerPhase.isActive()) return Scope.NOOP;
        return open("rhi:" + safe(label));
    }

    public static Scope itemRender(String label) {
        if (!ProfilerPhase.isActive()) return Scope.NOOP;
        return open("item_render:" + safe(label));
    }

    public static Scope worldEffect(String label) {
        if (!ProfilerPhase.isActive()) return Scope.NOOP;
        return open("world_effect:" + safe(label));
    }

    public static List<String> debugLines(String title, String domainPrefix, int limit, double minMs) {
        return List.of();
    }

    private static Scope open(String label) {
        return new Scope(ProfilerPhase.scope(label));
    }

    private static String safe(Object value) {
        if (value == null) return "unknown";
        String text = String.valueOf(value);
        return text.isBlank() ? "unknown" : text;
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(null);
        private ProfilerPhase.Scope delegate;

        private Scope(ProfilerPhase.Scope delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            ProfilerPhase.Scope current = delegate;
            if (current == null) return;
            delegate = null;
            current.close();
        }
    }

    public record Snapshot(long frameId, List<Entry> entries) {
        public static final Snapshot EMPTY = new Snapshot(Long.MIN_VALUE, List.of());
    }

    public record Entry(String domain,
                        String owner,
                        String name,
                        long calls,
                        long totalNs,
                        long maxNs,
                        long drawCalls,
                        long meshUploads,
                        long fullscreenPasses,
                        long fastCopies,
                        long shaderCopies,
                        long uploadedBytes,
                        long uniformWrites,
                        long uniformBytes) {
    }
}
