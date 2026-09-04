/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D.Deferred2DLayer;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiScissorSnapshot;

public interface Deferred2DSubmit {
    Deferred2DLayer layer();

    ViewportContext viewport();

    UiScissorSnapshot scissorSnapshot();

    UiClipSnapshot clipSnapshot();

    default int[] framebufferScissor() {
        return scissorSnapshot().framebufferRect();
    }

    void submit();

    default void release() {
    }
}
