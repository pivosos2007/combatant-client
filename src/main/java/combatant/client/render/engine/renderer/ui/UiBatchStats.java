/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.profiler.DevProfilerBridge;
import combatant.client.render.engine.renderer.Renderer2D;

import java.util.EnumMap;
import java.util.Map;

/** Runtime diagnostics for the ordered UI batcher. */
public class UiBatchStats {
    private static final boolean METRICS_ENABLED = DevProfilerBridge.available("RenderProfiler2D");
    private final EnumMap<Renderer2D.FlushReason, Integer> frameFlushReasons =
            METRICS_ENABLED ? new EnumMap<>(Renderer2D.FlushReason.class) : null;
    private volatile boolean active;
    private volatile int lastOrder;
    private volatile int lastDraws;
    private volatile int lastVertices;
    private volatile int lastIndices;
    private volatile int poolTotal;
    private volatile int frameBatches;
    private volatile int frameDraws;
    private volatile int frameVertices;
    private volatile int frameIndices;
    private volatile String lastError = "";
    private volatile Renderer2D.FlushReason lastFlushReason = Renderer2D.FlushReason.UNKNOWN;

    public void update(boolean active, int order, int draws, int vertices, int indices, int poolTotal) {
        if (!METRICS_ENABLED) return;
        this.active = active;
        this.lastOrder = order;
        this.lastDraws = draws;
        this.lastVertices = vertices;
        this.lastIndices = indices;
        this.poolTotal = poolTotal;
        this.lastError = "";
    }

    public void noteEmpty(boolean active, int poolTotal) {
        if (!METRICS_ENABLED) return;
        this.active = active;
        this.poolTotal = poolTotal;
    }

    public void noteFailure(String reason) {
        if (!METRICS_ENABLED) return;
        this.lastError = reason != null ? reason : "";
    }

    public void noteFlushReason(Renderer2D.FlushReason reason) {
        if (!METRICS_ENABLED) return;
        Renderer2D.FlushReason safeReason = reason != null ? reason : Renderer2D.FlushReason.UNKNOWN;
        this.lastFlushReason = safeReason;
        frameFlushReasons.merge(safeReason, 1, Integer::sum);
    }

    public void addFrame(int order, int draws, int vertices, int indices) {
        if (!METRICS_ENABLED) return;
        frameBatches += order;
        frameDraws += draws;
        frameVertices += vertices;
        frameIndices += indices;
    }

    public void onFrameStart() {
        if (!METRICS_ENABLED) return;
        frameBatches = 0;
        frameDraws = 0;
        frameVertices = 0;
        frameIndices = 0;
        lastFlushReason = Renderer2D.FlushReason.UNKNOWN;
        frameFlushReasons.clear();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getLastOrder() {
        return lastOrder;
    }

    public int getLastDraws() {
        return lastDraws;
    }

    public int getLastVertices() {
        return lastVertices;
    }

    public int getLastIndices() {
        return lastIndices;
    }

    public int getPoolTotal() {
        return poolTotal;
    }

    public int getFrameBatches() {
        return frameBatches;
    }

    public int getFrameDraws() {
        return frameDraws;
    }

    public int getFrameVertices() {
        return frameVertices;
    }

    public int getFrameIndices() {
        return frameIndices;
    }

    public String getLastError() {
        return lastError;
    }

    public Renderer2D.FlushReason getLastFlushReason() {
        return lastFlushReason;
    }

    public Map<Renderer2D.FlushReason, Integer> getFrameFlushReasons() {
        if (!METRICS_ENABLED || frameFlushReasons == null || frameFlushReasons.isEmpty()) return Map.of();
        return new EnumMap<>(frameFlushReasons);
    }
}
