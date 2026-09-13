/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.framegraph;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;

import java.util.List;
import java.util.function.Consumer;

/**
 * Combatant frame graph front-end.
 *
 * <p>Legacy phase handlers remain valid and are represented as side-effect passes. New renderer
 * passes should use {@link #add(FrameGraphPassContract, Consumer)} so hazards are explicit before
 * execution. Physical allocation/barrier lowering is intentionally a backend compiler concern.</p>
 */
public final class CombatantFrameGraph {
    private final PassScheduler scheduler = new PassScheduler();

    public void add(RenderPhase phase, Consumer<RenderFrameContext> handler) {
        add(phase, phase == null ? "unnamed" : phase.name().toLowerCase(), handler);
    }

    public void add(RenderPhase phase, String label, Consumer<RenderFrameContext> handler) {
        if (handler == null) return;
        scheduler.add(new RenderPassNode(phase, label, handler));
    }

    public void add(FrameGraphPassContract contract, Consumer<RenderFrameContext> handler) {
        if (contract == null || handler == null) return;
        scheduler.add(new RenderPassNode(contract, handler));
    }

    public CompiledFrameGraph compile(RenderPhase phase) {
        return scheduler.compile(phase);
    }

    public void execute(RenderPhase phase, RenderFrameContext baseContext) {
        List<RenderPassNode> nodes = scheduler.nodes(phase);
        if (nodes.isEmpty()) return;

        // Compile first so contract violations fail before any GPU-visible side effect of this phase.
        scheduler.compile(phase);

        String label = "framegraph:" + (phase == null ? "none" : phase.name().toLowerCase());
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(phase, label)) {
            RenderFrameContext ctx = CombatantRenderSystem.currentContext();
            if (ctx == null && baseContext != null) ctx = baseContext.withPhase(phase);
            if (ctx == null) return;
            for (RenderPassNode node : nodes) {
                try (RenderPhaseScope nodeScope = CombatantRenderSystem.phase(phase, "pass:" + node.label())) {
                    node.execute(CombatantRenderSystem.currentContext());
                }
            }
        }
    }

    public void clear() {
        scheduler.clear();
    }
}
