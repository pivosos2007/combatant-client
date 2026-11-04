/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playermodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in, low-frequency ground-truth counters. Times are CPU submission/emission costs,
 * NOT GPU timings: wall-clock timing around GL dispatch does not measure GPU execution.
 * Only touched on the render thread and emits at most one report per five seconds.
 */
public final class PlayerModelRenderDiagnostics {
    private static final Logger LOG = LoggerFactory.getLogger("Combatant/PlayerModelPerf");
    private static final long WINDOW_NS = 5_000_000_000L;
    private static boolean enabled;
    private static long start;
    private static long gpuQueued, cpuFallback, glDraws, irisDraws, irisComputeVertices;
    private static long cpuEmittedVertices, cpuEmissionNs, glSubmissionNs, irisSubmissionNs;
    private static String fallbackReason = "none";
    private static String targetFormat = "unknown";
    private static PlayerModels.Ready lastAsset;
    private static int vertices, triangles;

    private PlayerModelRenderDiagnostics() {}

    public static void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        clear();
        if (value) LOG.info("[PlayerModelPerf] enabled: reporting real submission paths, counts and CPU time (not GPU time)");
    }

    public static boolean enabled() { return enabled; }

    private static void clear() {
        start = System.nanoTime();
        gpuQueued = cpuFallback = glDraws = irisDraws = irisComputeVertices = 0;
        cpuEmittedVertices = cpuEmissionNs = glSubmissionNs = irisSubmissionNs = 0;
        fallbackReason = "none";
        targetFormat = "unknown";
        lastAsset = null;
        vertices = triangles = 0;
    }

    private static void asset(PlayerModels.Ready ready) {
        if (lastAsset == ready || ready == null) return;
        lastAsset = ready;
        vertices = triangles = 0;
        for (var surface : ready.surfaces()) {
            vertices += surface.mesh().vertexCount();
            triangles += surface.mesh().indexCount() / 3;
        }
    }

    public static void targetFormat(String value) {
        if (enabled && value != null) targetFormat = value;
    }

    public static void queued(PlayerModels.Ready ready) {
        if (!enabled) return;
        asset(ready);
        gpuQueued++;
        reportIfDue();
    }

    public static void fallback(PlayerModels.Ready ready, String reason) {
        if (!enabled) return;
        asset(ready);
        cpuFallback++;
        fallbackReason = reason;
        reportIfDue();
    }

    public static void cpuGeometry(long elapsedNs, int emittedVertices) {
        if (!enabled) return;
        cpuEmissionNs += elapsedNs;
        cpuEmittedVertices += emittedVertices;
    }

    public static void glIssued(int count, long elapsedNs) {
        if (!enabled) return;
        glDraws += count;
        glSubmissionNs += elapsedNs;
    }

    public static void irisIssued(int count, int vertexCount, long elapsedNs) {
        if (!enabled) return;
        irisDraws += count;
        irisComputeVertices += vertexCount;
        irisSubmissionNs += elapsedNs;
    }

    private static void reportIfDue() {
        long now = System.nanoTime();
        long duration = now - start;
        if (duration < WINDOW_NS) return;
        LOG.info("[PlayerModelPerf] {}s mesh={} vertices/{} triangles | target={} GPU queued={} GL indexed draws={} Iris indexed draws={} Iris compute vertices={} | CPU fallbacks={} reason={} CPU VertexConsumer vertices={} | CPU emission={}ms GL submission={}ms Iris dispatch+submission={}ms (CPU timings only)",
                String.format(java.util.Locale.ROOT, "%.1f", duration / 1_000_000_000.0), vertices, triangles,
                targetFormat, gpuQueued, glDraws, irisDraws, irisComputeVertices,
                cpuFallback, fallbackReason, cpuEmittedVertices,
                String.format(java.util.Locale.ROOT, "%.3f", cpuEmissionNs / 1_000_000.0),
                String.format(java.util.Locale.ROOT, "%.3f", glSubmissionNs / 1_000_000.0),
                String.format(java.util.Locale.ROOT, "%.3f", irisSubmissionNs / 1_000_000.0));
        start = now;
        gpuQueued = cpuFallback = glDraws = irisDraws = irisComputeVertices = 0;
        cpuEmittedVertices = cpuEmissionNs = glSubmissionNs = irisSubmissionNs = 0;
        fallbackReason = "none";
        targetFormat = "unknown";
    }
}
