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

import java.util.function.Consumer;


public final class CombatantFrameGraph {
    private final PassScheduler scheduler = new PassScheduler();

    public void add(RenderPhase phase, Consumer<RenderFrameContext> handler) {
        add(phase, phase == null ? "unnamed" : phase.name().toLowerCase(), handler);
    }

    public void add(RenderPhase phase, String label, Consumer<RenderFrameContext> handler) {
        if (handler == null) return;
        scheduler.add(new RenderPassNode(phase, label, handler));
    }

    public void execute(RenderPhase phase, RenderFrameContext baseContext) {
        String label = "framegraph:" + (phase == null ? "none" : phase.name().toLowerCase());
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(phase, label)) {
            RenderFrameContext ctx = CombatantRenderSystem.currentContext();
            if (ctx == null && baseContext != null) ctx = baseContext.withPhase(phase);
            if (ctx == null) return;
            for (RenderPassNode node : scheduler.nodes(phase)) {
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
