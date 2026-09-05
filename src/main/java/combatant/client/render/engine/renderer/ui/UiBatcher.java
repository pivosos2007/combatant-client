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

import java.util.List;

/** Builds the concrete ordered plan while retaining normalized-command diagnostics. */
public final class UiBatcher {
    public UiBatchPlan compile(UiCommandBuffer commands, List<UiBatchPlan.Pass> passes) {
        List<UiBatchPlan.Pass> executablePasses = passes == null ? List.of() : passes;

        int shapes = 0;
        int paths = 0;
        int primitives = 0;
        int textures = 0;
        int text = 0;
        int items = 0;
        int effects = 0;
        int commandCount = 0;

        if (commands != null) {
            commandCount = commands.size();
            for (UiCommand command : commands.commands()) {
                UiCommandKind kind = command.kind();
                switch (kind) {
                    case SHAPE -> shapes++;
                    case PATH -> paths++;
                    case PRIMITIVE -> primitives++;
                    case TEXTURE -> textures++;
                    case TEXT -> text++;
                    case ITEM -> items++;
                    case BLUR_REGION, LIQUID_GLASS_REGION, EFFECT_REGION -> effects++;
                }
            }
        }

        int orderedBatches = 0;
        for (UiBatchPlan.Pass pass : executablePasses) {
            if (pass != null) orderedBatches += pass.orderedBatchCount();
        }

        if (commands != null) {
            commands.stats().addCompiledPasses(executablePasses.size());
            commands.stats().addCompiledOrderedBatches(orderedBatches);
        }

        return new UiBatchPlan(
                executablePasses,
                commandCount,
                shapes,
                paths,
                primitives,
                textures,
                text,
                items,
                effects,
                orderedBatches,
                0,
                0
        );
    }
}
