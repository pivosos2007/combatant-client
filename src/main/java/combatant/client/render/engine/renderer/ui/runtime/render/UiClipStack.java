/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.helpers.ScissorFunction;

import java.util.ArrayDeque;
import java.util.Deque;

public final class UiClipStack {
    private final Deque<Boolean> pushed = new ArrayDeque<>();

    public boolean push(UiBounds bounds, UiRenderContext context) {
        if (bounds == null || bounds.width() <= 0.0f || bounds.height() <= 0.0f) {
            pushed.push(false);
            return false;
        }
        boolean ok = switch (context != null ? context.projectionMode() : UiProjectionMode.CURRENT) {
            case SCALED_SCREEN -> ScissorFunction.pushRaw(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            case CURRENT, RAW_FRAMEBUFFER, UNSCALED_LOGICAL ->
                    ScissorFunction.pushRaw(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        };
        pushed.push(ok);
        return ok;
    }

    public void pop() {
        if (pushed.isEmpty()) return;
        if (pushed.pop()) {
            ScissorFunction.pop();
        }
    }
}
