/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.input;

import combatant.client.render.engine.renderer.ui.runtime.action.UiActionContext;
import combatant.client.render.engine.renderer.ui.runtime.action.UiActionRegistry;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiPerfCounters;

public final class UiInputDispatcher {
    private final UiHitTester hitTester = new UiHitTester();
    private final UiFocusManager focusManager = new UiFocusManager();
    private final UiCursorManager cursorManager = new UiCursorManager();
    private final UiInputState state = new UiInputState();
    private final UiActionRegistry actions;
    private final UiPerfCounters counters;

    public UiInputDispatcher(UiActionRegistry actions) {
        this(actions, null);
    }

    public UiInputDispatcher(UiActionRegistry actions, UiPerfCounters counters) {
        this.actions = actions != null ? actions : new UiActionRegistry();
        this.counters = counters;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public UiInputState state() {
        return state;
    }

    public UiHitResult updateHover(UiNode root, float x, float y) {
        long start = System.nanoTime();
        UiHitResult hit = hitTester.hitTest(root, x, y);
        if (counters != null) {
            counters.setHitTestNanos(System.nanoTime() - start);
        }
        if (state.hoveredNode() != hit.node()) {
            if (state.hoveredNode() != null) state.hoveredNode().state().setHovered(false);
            state.setHoveredNode(hit.node());
            if (hit.node() != null) hit.node().state().setHovered(true);
        }
        if (hit.node() != null) {
            cursorManager.apply(hit.node().style().cursor());
        }
        return hit;
    }

    public boolean pointerDown(UiNode root, UiPointerEvent event) {
        UiHitResult hit = updateHover(root, event.x(), event.y());
        state.setPressedNode(hit.node());
        if (hit.node() != null) {
            hit.node().state().setActive(true);
            focusManager.focus(hit.node());
            state.setFocusedNode(hit.node());
        }
        return hit.hit();
    }

    public boolean pointerUp(UiNode root, UiPointerEvent event) {
        UiHitResult hit = updateHover(root, event.x(), event.y());
        UiNode pressed = state.pressedNode();
        if (pressed != null) {
            pressed.state().setActive(false);
        }
        state.setPressedNode(null);
        if (pressed != null && pressed == hit.node()) {
            String action = pressed.events().get("click");
            if (action != null && !action.isBlank()) {
                return actions.dispatch(action, new UiActionContext(pressed, null, event));
            }
            return true;
        }
        return false;
    }

    public boolean scroll(UiNode root, UiScrollEvent event) {
        UiHitResult hit = updateHover(root, event.x(), event.y());
        UiNode node = hit.node();
        while (node != null && node.type() != UiNodeType.SCROLL) {
            node = node.parent();
        }
        if (node == null) return false;
        float nextX = node.state().scrollX() + (float) event.amountX();
        float nextY = node.state().scrollY() + (float) event.amountY();
        float maxX = Math.max(0.0f, node.state().contentWidth() - node.bounds().width());
        float maxY = Math.max(0.0f, node.state().contentHeight() - node.bounds().height());
        node.state().setScroll(clamp(nextX, 0.0f, maxX), clamp(nextY, 0.0f, maxY));
        state.setScrollOwner(node);
        return true;
    }
}
