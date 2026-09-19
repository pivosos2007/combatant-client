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
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderTransform;

public final class UiInputDispatcher {
    private final UiHitTester hitTester = new UiHitTester();
    private final UiFocusManager focusManager = new UiFocusManager();
    private final UiCursorManager cursorManager = new UiCursorManager();
    private final UiInputState state = new UiInputState();
    private final UiActionRegistry actions;
    private final UiPerfCounters counters;
    private UiRenderTransform coordinateTransform = UiRenderTransform.IDENTITY;

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

    /** Maps render-space pointer coordinates back into the retained tree's logical units. */
    public void setCoordinateTransform(UiRenderTransform transform) {
        coordinateTransform = transform != null ? transform : UiRenderTransform.IDENTITY;
    }

    private float logicalX(float renderX) {
        return coordinateTransform.logicalX(renderX);
    }

    private float logicalY(float renderY) {
        return coordinateTransform.logicalY(renderY);
    }

    public UiHitResult updateHover(UiNode root, float x, float y) {
        return updateHoverLogical(root, logicalX(x), logicalY(y));
    }

    private UiHitResult updateHoverLogical(UiNode root, float x, float y) {
        updateScrollbarDrag(x, y);
        updateScrollbarHover(root, x, y);

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
        UiPointerEvent logicalEvent = new UiPointerEvent(logicalX(event.x()), logicalY(event.y()), event.button());
        UiNode scrollbar = UiScrollSupport.scrollbarAt(root, logicalEvent.x(), logicalEvent.y());
        if (scrollbar != null && logicalEvent.button() == 0) {
            UiScrollbarMetrics metrics = UiScrollbarMetrics.resolve(scrollbar);
            if (metrics != null) {
                long now = System.nanoTime();
                scrollbar.state().markScrollInteraction(now);
                state.setScrollOwner(scrollbar);
                if (metrics.containsThumb(logicalEvent.x(), logicalEvent.y())) {
                    scrollbar.state().setScrollbarDragging(true);
                    scrollbar.state().setScrollbarDragGrab(logicalEvent.y() - metrics.thumbY());
                } else {
                    float thumbY = logicalEvent.y() - metrics.thumbHeight() * 0.5f;
                    float target = metrics.scrollForThumbY(thumbY);
                    scrollbar.state().setTargetScroll(scrollbar.state().targetScrollX(), target);
                }
                return true;
            }
        }

        UiHitResult hit = updateHoverLogical(root, logicalEvent.x(), logicalEvent.y());
        state.setPressedNode(hit.node());
        if (hit.node() != null) {
            hit.node().state().setActive(true);
            focusManager.focus(hit.node());
            state.setFocusedNode(hit.node());
        }
        return hit.hit();
    }

    public boolean pointerUp(UiNode root, UiPointerEvent event) {
        UiPointerEvent logicalEvent = new UiPointerEvent(logicalX(event.x()), logicalY(event.y()), event.button());
        UiNode scrollOwner = state.scrollOwner();
        if (scrollOwner != null && scrollOwner.state().scrollbarDragging() && logicalEvent.button() == 0) {
            scrollOwner.state().setScrollbarDragging(false);
            scrollOwner.state().markScrollInteraction(System.nanoTime());
            state.setScrollOwner(null);
            updateScrollbarHover(root, logicalEvent.x(), logicalEvent.y());
            return true;
        }

        UiHitResult hit = updateHoverLogical(root, logicalEvent.x(), logicalEvent.y());
        UiNode pressed = state.pressedNode();
        if (pressed != null) pressed.state().setActive(false);
        state.setPressedNode(null);
        if (pressed != null && pressed == hit.node()) {
            String eventName = switch (logicalEvent.button()) {
                case 1 -> "secondaryClick";
                case 2 -> "auxiliaryClick";
                default -> "click";
            };
            String action = pressed.events().get(eventName);
            if (action != null && !action.isBlank()) {
                return actions.dispatch(action, new UiActionContext(pressed, null, logicalEvent));
            }
            return true;
        }
        return false;
    }

    public boolean scroll(UiNode root, UiScrollEvent event) {
        float x = logicalX(event.x());
        float y = logicalY(event.y());
        UiHitResult hit = updateHoverLogical(root, x, y);
        UiNode node = hit.node();
        while (node != null && node.type() != UiNodeType.SCROLL) node = node.parent();
        if (node == null) return false;

        float step = Math.max(0.0f, node.props().number("wheelStep", 1.0f));
        float nextX = node.state().targetScrollX() + (float) event.amountX() * step;
        float nextY = node.state().targetScrollY() + (float) event.amountY() * step;
        float maxX = Math.max(0.0f, node.state().contentWidth() - node.bounds().width());
        float maxY = Math.max(0.0f, node.state().contentHeight() - node.bounds().height());
        node.state().setTargetScroll(clamp(nextX, 0.0f, maxX), clamp(nextY, 0.0f, maxY));
        node.state().markScrollInteraction(System.nanoTime());
        state.setScrollOwner(node);
        return true;
    }

    private void updateScrollbarHover(UiNode root, float x, float y) {
        UiNode next = UiScrollSupport.scrollbarAt(root, x, y);
        UiNode previous = state.scrollbarHoverNode();
        if (previous != next) {
            if (previous != null) previous.state().setScrollbarHovered(false);
            if (next != null) {
                next.state().setScrollbarHovered(true);
                next.state().markScrollInteraction(System.nanoTime());
            }
            state.setScrollbarHoverNode(next);
        }
    }

    private void updateScrollbarDrag(float x, float y) {
        UiNode owner = state.scrollOwner();
        if (owner == null || !owner.state().scrollbarDragging()) return;
        UiScrollbarMetrics metrics = UiScrollbarMetrics.resolve(owner);
        if (metrics == null) return;
        float thumbY = y - owner.state().scrollbarDragGrab();
        float target = metrics.scrollForThumbY(thumbY);
        owner.state().setTargetScroll(owner.state().targetScrollX(), target);
        owner.state().markScrollInteraction(System.nanoTime());
    }
}
