/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import java.util.List;

/** Defines hard attachment/clear barriers for an already-open ordered render pass. */
public enum RenderPassCompatibility {
    ;

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

    /** Compatibility overload retained for single-target callers. */
    public static boolean canContinue(Object passColorAttachment,
                                      Object passDepthAttachment,
                                      Object nextColorAttachment,
                                      Object nextDepthAttachment,
                                      boolean nextClearsColor,
                                      boolean nextClearsDepth) {
        return passColorAttachment == nextColorAttachment
                && passDepthAttachment == nextDepthAttachment
                && !nextClearsColor
                && !nextClearsDepth;
    }
}
