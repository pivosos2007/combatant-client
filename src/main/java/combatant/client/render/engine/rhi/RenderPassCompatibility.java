/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

/**
 * Defines the hard attachment/clear barriers for an already open ordered render pass.
 * Pipeline, uniforms, samplers and mesh ranges are deliberately absent: they can change
 * between draws without ending the pass.
 */
public enum RenderPassCompatibility {
    ;

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
