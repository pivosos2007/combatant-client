/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.rhi.CombatantRhi;

/** Production UI execution point. Command compilation and legacy lowering both terminate here. */
public final class UiPassExecutor {
    private UiBatchPlan lastPlan = UiBatchPlan.EMPTY;

    public UiBatchPlan execute(UiBatchPlan plan, RenderFrameContext context, CombatantRhi rhi) {
        UiBatchPlan compiled = plan != null ? plan : UiBatchPlan.EMPTY;
        if (rhi == null || compiled.passes().isEmpty()) {
            lastPlan = compiled;
            return compiled;
        }

        long drawsBefore = rhi.stats().drawCalls();
        long fullscreenBefore = rhi.stats().fullscreenPasses();
        try {
            for (UiBatchPlan.Pass pass : compiled.passes()) {
                if (pass == null) continue;
                if (!pass.drawCommands().isEmpty()) {
                    rhi.drawMeshes(pass.drawCommands());
                }
                pass.executeWork(context, rhi);
            }
        } finally {
            long backendDraws = Math.max(0L, rhi.stats().drawCalls() - drawsBefore);
            long fullscreenDraws = Math.max(0L, rhi.stats().fullscreenPasses() - fullscreenBefore);
            long meshDrawCommands = Math.max(0L, backendDraws - fullscreenDraws);
            lastPlan = compiled.withExecutionStats(saturatingInt(meshDrawCommands), saturatingInt(backendDraws));
        }
        return lastPlan;
    }

    public UiBatchPlan lastPlan() {
        return lastPlan;
    }

    private static int saturatingInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }
}
