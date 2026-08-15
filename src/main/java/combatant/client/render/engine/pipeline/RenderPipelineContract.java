/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.pipeline;

/** Explicit uniform/transform cost contract for a render pipeline. */
public enum RenderPipelineContract {
    EXTENDED(true, false),
    UI_FAST(false, true),
    UI_WARPED(false, true),
    UI_EXTENDED(true, true);

    private final boolean meshDataRequired;
    private final boolean uiBatchRequired;

    RenderPipelineContract(boolean meshDataRequired, boolean uiBatchRequired) {
        this.meshDataRequired = meshDataRequired;
        this.uiBatchRequired = uiBatchRequired;
    }

    public boolean meshDataRequired() {
        return meshDataRequired;
    }

    public boolean uiBatchRequired() {
        return uiBatchRequired;
    }
}
