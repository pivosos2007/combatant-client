/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.clip;

/**
 * Backend-neutral render-pass contract for shape clipping.
 *
 * <p>This is intentionally explicit. A pipeline either does not participate in shape clipping, or
 * it declares that the render pass must be created/prepared with the backend's shape-clip
 * attachment when a clip scope is active. GL maps this to a stencil attachment on the current FBO.
 * A future Vulkan backend must map the same contract to a render pass with a stencil attachment or
 * to a declared mask attachment/binding.</p>
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
