/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D.Deferred2DLayer;

public interface Deferred2DSubmit {
    Deferred2DLayer layer();

    ViewportContext viewport();

    int[] framebufferScissor();

    void submit();

    default void release() {
    }
}
