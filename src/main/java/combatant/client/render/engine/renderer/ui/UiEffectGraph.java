/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.command.UiCommand;
import combatant.client.render.engine.command.UiCommandBuffer;
import combatant.client.render.engine.command.UiCommandKind;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.rhi.CombatantRhi;

/**
 * Owner for UI effects. Blur/liquid draw paths still execute through Renderer2D ordered
 * batches, but effect discovery is command-buffer based and not hidden in primitive flush.
 */
public final class UiEffectGraph {
    private int lastEffectCommands;

    public void execute(UiCommandBuffer commands, RenderFrameContext context, CombatantRhi rhi) {
        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiEffect("ui_effect_graph")) {
            int effects = 0;
            if (commands != null) {
                for (UiCommand command : commands.commands()) {
                    UiCommandKind kind = command.kind();
                    if (kind == UiCommandKind.BLUR_REGION || kind == UiCommandKind.LIQUID_GLASS_REGION || kind == UiCommandKind.EFFECT_REGION) {
                        effects++;
                        try (RenderCostProfiler.Scope ignoredEffect = RenderCostProfiler.uiEffect(kind.name().toLowerCase())) {
                            // Current draw work still runs through Renderer2D ordered batches; this scope attributes
                            // command discovery/compilation to the concrete effect kind.
                        }
                    }
                }
            }
            lastEffectCommands = effects;
        }
    }

    public int lastEffectCommands() {
        return lastEffectCommands;
    }
}
