/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.clip;

/**
 * Backend-neutral shape-clip attachment contract. GL maps active clipping to stencil; other
 * backends provide an equivalent declared clip attachment or mask binding.
 */
public enum ShapeClipRenderPassContract {
    /**
     * Pipeline must not rely on shape clipping. Active shape clips will be reported as stale state.
     */
    NONE,

    /**
     * Pipeline requires a shape-clip attachment only while a clip scope/mask write is active.
     */
    WHEN_ACTIVE,

    /**
     * Pipeline always requires the backend shape-clip attachment for its render pass.
     */
    ALWAYS;

    public boolean requiresAttachment(boolean clipActive) {
        return this == ALWAYS || (this == WHEN_ACTIVE && clipActive);
    }

    public boolean participates() {
        return this != NONE;
    }
}
