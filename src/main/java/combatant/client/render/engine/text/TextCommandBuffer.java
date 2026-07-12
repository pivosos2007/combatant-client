/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.text.backend.TextBackendRouter;
import combatant.client.render.engine.text.backend.TextDrawCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Frame-local text command buffer. It is intentionally independent from Renderer2D.
 */
public final class TextCommandBuffer {
    private final List<TextDrawCommand> commands = new ArrayList<>();
    private final TextCommandStats stats;

    public TextCommandBuffer(TextCommandStats stats) {
        this.stats = stats;
    }

    public void record(TextDrawCommand command) {
        if (command == null || command.text() == null || command.text().isEmpty()) return;
        commands.add(command);
        stats.recorded();
        if (command.effect() != null && command.effect().enabled()) stats.effect();
        if (command.clip() != null && command.clip().enabled()) stats.clipped();
    }

    public int size() {
        return commands.size();
    }

    public void flush(TextBackendRouter router) {
        if (commands.isEmpty()) return;
        RenderFrameContext context = CombatantRenderSystem.ensureFrameContext();
        CombatantRhi rhi = CombatantRenderSystem.rhi();
        for (int i = 0; i < commands.size(); ) {
            int consumed = Math.max(1, router.drawAdjacent(commands, i, context, rhi));
            for (int j = 0; j < consumed && i + j < commands.size(); j++) {
                stats.submitted();
            }
            i += consumed;
        }
        commands.clear();
    }

    public void clear() {
        commands.clear();
    }
}
