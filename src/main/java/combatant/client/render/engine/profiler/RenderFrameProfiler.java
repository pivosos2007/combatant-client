/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.rhi.RhiStatsSnapshot;
import combatant.client.render.engine.rhi.uniform.UniformAllocatorStatsSnapshot;

/**
 * Frame/phase timing only. Counters such as draw count, vertex count and
 * allocation totals intentionally do not live here: Tracy should show where
 * frame time is spent, not duplicate renderer statistics.
 */
public enum RenderFrameProfiler {
    ;

    public static void beginFrame(long frameId) {
        // Vanilla already owns the frame-wide profiler scope. Combatant adds only
        // lexical child phases so profiler stack nesting cannot be corrupted.
    }

    public static PhaseScope phase(RenderPhase phase, String label) {
        if (!ProfilerPhase.isActive()) return PhaseScope.NOOP;

        String phaseName = phase == null ? "none" : phase.name().toLowerCase(java.util.Locale.ROOT);
        String zoneName = label == null || label.isBlank()
                ? "combatant:render_phase:" + phaseName
                : "combatant:render_phase:" + phaseName + ":" + label;
        return new PhaseScope(ProfilerPhase.scope(zoneName));
    }

    public static void endFrame(RhiStatsSnapshot rhi, UniformAllocatorStatsSnapshot uniforms) {
        // Frame boundary is owned by Minecraft/Tracy.
    }

    public static final class PhaseScope implements AutoCloseable {
        private static final PhaseScope NOOP = new PhaseScope(null);
        private ProfilerPhase.Scope delegate;

        private PhaseScope(ProfilerPhase.Scope delegate) {
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
}
