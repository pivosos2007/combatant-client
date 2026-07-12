/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text.backend;

import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.text.TextBackendPreference;
import combatant.client.render.engine.text.TextCommandStats;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.VanillaTextRenderer;

import java.util.List;

/**
 * Route ordinary UI text through the vanilla/Sodium text path where it is explicitly requested and effect-free.
 */
public final class VanillaSodiumTextBackend implements TextBackend {
    private final TextCommandStats stats;

    public VanillaSodiumTextBackend() {
        this(null);
    }

    public VanillaSodiumTextBackend(TextCommandStats stats) {
        this.stats = stats;
    }

    @Override
    public String id() {
        return "vanilla_sodium_text";
    }

    @Override
    public boolean supports(TextDrawCommand command) {
        if (command.customEffects()) return false;
        if (command.preference() == TextBackendPreference.VANILLA_SODIUM) return true;
        return command.auto() && command.renderer() instanceof VanillaTextRenderer;
    }

    @Override
    public boolean supports(TextDrawCommand command, TextPlacementTransform placement) {
        if (placement != null && placement.world()) return false;
        return supports(command);
    }

    @Override
    public void draw(TextDrawCommand command, TextPlacementTransform placement, RenderFrameContext context, CombatantRhi rhi) {
        TextRenderer renderer = command.renderer() != null ? command.renderer() : VanillaTextRenderer.INSTANCE;
        boolean wasBuilding = renderer.isBuilding();
        if (!wasBuilding) renderer.begin(command.size(), false, command.big());
        try {
            renderer.render(command.text(), command.x(), command.y(), command.color(), command.shadow());
        } finally {
            if (!wasBuilding) renderer.end();
        }
        if (stats != null) stats.backend(TextBackendPreference.VANILLA_SODIUM);
    }

    @Override
    public void drawBatch(List<TextDrawCommand> commands, int start, int end, RenderFrameContext context, CombatantRhi rhi) {
        if (commands == null || start < 0 || end <= start || start >= commands.size()) return;
        TextDrawCommand first = commands.get(start);
        TextRenderer renderer = first.renderer() != null ? first.renderer() : VanillaTextRenderer.INSTANCE;
        boolean wasBuilding = renderer.isBuilding();
        if (!wasBuilding) renderer.begin(first.size(), false, first.big());
        try {
            for (int i = start; i < end && i < commands.size(); i++) {
                TextDrawCommand command = commands.get(i);
                renderer.render(command.text(), command.x(), command.y(), command.color(), command.shadow());
                if (stats != null) stats.backend(TextBackendPreference.VANILLA_SODIUM);
            }
        } finally {
            if (!wasBuilding) renderer.end();
        }
    }
}
