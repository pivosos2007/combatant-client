/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.pipeline;

public enum DepthPolicy {
    NONE,
    MAIN_FRAMEBUFFER,
    PRE_TRANSLUCENT,
    CUSTOM;

    public boolean needsDepthAttachment() {
        return this == MAIN_FRAMEBUFFER || this == PRE_TRANSLUCENT || this == CUSTOM;
    }
}
