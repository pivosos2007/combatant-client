/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;

/**
 * Semantic UI command counters used for normalized telemetry. Executable rendering work is owned
 * separately by UiPassCompiler, so only the per-kind counts of the recorded draws are kept.
 */
public final class UiSemanticCommandBuffer {
    private final int[] counts = new int[UiCommandKind.values().length];
    private int size;
    private final UiCommandStats stats = new UiCommandStats();

    public void beginFrame(RenderFrameContext context) {
        if (context != null) {
            stats.beginFrame(context.frameId());
        }
    }

    public void record(UiCommandKind kind) {
        if (kind == null) return;
        RenderFrameContext ctx = CombatantRenderSystem.currentContext();
        if (ctx != null) stats.beginFrame(ctx.frameId());
        counts[kind.ordinal()]++;
        size++;
        stats.record(kind);
    }

    public int count(UiCommandKind kind) {
        return counts[kind.ordinal()];
    }

    public UiCommandStats stats() {
        return stats;
    }

    public UiStatsSnapshot statsSnapshot() {
        return stats.snapshot();
    }

    public void clear() {
        java.util.Arrays.fill(counts, 0);
        size = 0;
    }

    public int size() {
        return size;
    }

}
