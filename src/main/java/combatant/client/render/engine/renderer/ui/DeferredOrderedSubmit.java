/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer2D.Deferred2DLayer;

public record DeferredOrderedSubmit(Deferred2DLayer layer, ViewportContext viewport, int[] framebufferScissor, OrderedUiBatcher batcher) implements Deferred2DSubmit {
    @Override
    public void submit() {
        if (batcher == null) return;
        batcher.flush(true);
        Renderer2D.flushUiLayer();
    }

    @Override
    public void release() {
        Renderer2D.releaseDeferredBatcher(batcher);
    }
}
