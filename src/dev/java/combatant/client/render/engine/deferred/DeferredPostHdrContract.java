/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/**
 * Canonical hand-off from final temporal resolve into HDR post processing.
 *
 * <p>The input is always output-resolution. TAA 1:1 and TAAU therefore share the same exposure and
 * bloom algorithms; post stages must not route through render-resolution {@code SCENE_COLOR}.</p>
 */
public final class DeferredPostHdrContract {
    public static final int VERSION = 1;
    public static final DeferredResource HDR_INPUT = DeferredResource.TAA_RESOLVED_COLOR;

    private DeferredPostHdrContract() {
    }
}
