/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.rhi.CombatantRhi;

/**
 * Executes compiled UI passes. Renderer2D ordered batches remain the production executor;
 * the normalized command stream is the source that future specialized passes lower from.
 */
public final class UiPassExecutor {
    private UiBatchPlan lastPlan = UiBatchPlan.EMPTY;

    public void execute(UiBatchPlan plan, RenderFrameContext context, CombatantRhi rhi) {
        lastPlan = plan != null ? plan : UiBatchPlan.EMPTY;
    }

    public UiBatchPlan lastPlan() {
        return lastPlan;
    }
}
