/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.layout;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.core.UiState;

/** Advances retained scroll targets independently from cached JS execution. */
public final class UiScrollRuntime {
    private UiScrollRuntime() {
    }

    public static boolean tick(UiNode root, long nowNanos) {
        if (root == null) return false;
        boolean changed = tickOne(root, nowNanos);
        for (UiNode child : root.children()) changed |= tick(child, nowNanos);
        return changed;
    }

    private static boolean tickOne(UiNode node, long nowNanos) {
        if (node.type() != UiNodeType.SCROLL) return false;
        UiState state = node.state();
        float maxX = Math.max(0.0f, state.contentWidth() - node.bounds().width());
        float maxY = Math.max(0.0f, state.contentHeight() - node.bounds().height());
        float targetX = clamp(state.targetScrollX(), 0.0f, maxX);
        float targetY = clamp(state.targetScrollY(), 0.0f, maxY);
        state.setTargetScroll(targetX, targetY);

        long last = state.lastScrollTickNanos();
        state.setLastScrollTickNanos(nowNanos);
        if (last == 0L) return false;

        float currentX = clamp(state.scrollX(), 0.0f, maxX);
        float currentY = clamp(state.scrollY(), 0.0f, maxY);
        boolean smooth = node.props().bool("smoothScroll", true);
        float nextX;
        float nextY;
        if (!smooth) {
            nextX = targetX;
            nextY = targetY;
        } else {
            float dtMs = Math.min(100.0f, (nowNanos - last) / 1_000_000.0f);
            float sourceRate = Math.max(0.0f, node.props().number("scrollSmoothingRate", 0.02f));
            float factor = Math.min(1.0f, dtMs * sourceRate);
            nextX = currentX + (targetX - currentX) * factor;
            nextY = currentY + (targetY - currentY) * factor;
            float snap = Math.max(0.0f, node.props().number("scrollSnapEpsilon", 0.05f));
            if (Math.abs(targetX - nextX) < snap) nextX = targetX;
            if (Math.abs(targetY - nextY) < snap) nextY = targetY;
        }

        if (Math.abs(nextX - state.scrollX()) < 0.0001f && Math.abs(nextY - state.scrollY()) < 0.0001f) return false;
        state.setCurrentScroll(nextX, nextY);
        return true;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
