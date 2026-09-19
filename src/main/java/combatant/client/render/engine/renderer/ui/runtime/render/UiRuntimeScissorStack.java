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

/**
 * Retained-tree rectangular scissor stack used while {@link UiRenderer} walks runtime nodes.
 * This is intentionally separate from renderer-side analytic/MSAA {@code UiClipStack}.
 */
public final class UiRuntimeScissorStack {
    private final Deque<Boolean> pushed = new ArrayDeque<>();

    public boolean push(UiBounds bounds, UiRenderContext context) {
        bounds = context != null ? context.renderBounds(bounds) : bounds;
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
