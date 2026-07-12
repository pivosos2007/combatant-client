/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core;

/**
 * AutoCloseable phase guard. Every render hook should use this instead of raw enterPhase().
 * It restores the previous RenderFrameContext phase and closes profiler scopes even on exceptions.
 */
public final class RenderPhaseScope implements AutoCloseable {
    private final RenderPhase phase;
    private final RenderFrameContext previous;
    private final AutoCloseable profilerScope;
    private boolean closed;

    RenderPhaseScope(RenderPhase phase, RenderFrameContext previous, AutoCloseable profilerScope) {
        this.phase = phase == null ? RenderPhase.NONE : phase;
        this.previous = previous;
        this.profilerScope = profilerScope;
    }

    public RenderPhase phase() {
        return phase;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (profilerScope != null) {
                profilerScope.close();
            }
        } catch (Exception ignored) {
            // Profiling must never break render lifecycle recovery.
        } finally {
            CombatantRenderSystem.restorePhase(previous);
        }
    }
}
