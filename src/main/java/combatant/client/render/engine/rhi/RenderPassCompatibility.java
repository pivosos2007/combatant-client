/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import java.util.List;

/** Defines hard attachment/clear barriers for an already-open ordered render pass. */
public enum RenderPassCompatibility {
    ;

    /**
     * Verify the RenderPearl 26.3 render-pass ABI before a draw is handed to the backend.
     * FrontendRenderPass.setPipeline() requires the exact color attachment count (including
     * unused slots), and a format match for each bound color target. A pipeline with no
     * declared target is NOT an implicit one-target pipeline.
     */
    public static void requirePipelineColorTargets(RenderPipeline pipeline,
                                                    List<RhiColorAttachment> passColors) {
        if (pipeline == null || passColors == null) {
            throw new IllegalArgumentException("Missing RHI pipeline or color attachments");
        }
        List<ColorTargetState> states = pipeline.getColorTargetStates();
        if (states.size() != passColors.size()) {
            throw new IllegalStateException("RHI color attachment contract for "
                    + pipeline.getLocation() + ": pipeline declares " + states.size()
                    + " color targets but render pass uses " + passColors.size());
        }
        for (int slot = 0; slot < states.size(); slot++) {
            RhiColorAttachment attachment = passColors.get(slot);
            if (attachment.isUnused()) continue; // RenderPearl explicitly supports unused slots.
            ColorTargetState state = states.get(slot);
            if (state == null || state.format() != attachment.view().texture().getFormat()) {
                throw new IllegalStateException("RHI color target mismatch for "
                        + pipeline.getLocation() + " at slot " + slot
                        + ": pipeline=" + (state == null ? "unused" : state.format())
                        + ", pass=" + attachment.view().texture().getFormat());
            }
        }
    }

    public static boolean canContinue(List<RhiColorAttachment> passColors,
                                      Object passDepthAttachment,
                                      List<RhiColorAttachment> nextColors,
                                      Object nextDepthAttachment,
                                      boolean nextClearsDepth) {
        if (passDepthAttachment != nextDepthAttachment || nextClearsDepth) return false;
        if (passColors == null || nextColors == null || passColors.size() != nextColors.size()) return false;
        for (int i = 0; i < passColors.size(); i++) {
            RhiColorAttachment pass = passColors.get(i);
            RhiColorAttachment next = nextColors.get(i);
            if (pass.view() != next.view() || next.clearColor().isPresent()) return false;
        }
        return true;
    }

}
