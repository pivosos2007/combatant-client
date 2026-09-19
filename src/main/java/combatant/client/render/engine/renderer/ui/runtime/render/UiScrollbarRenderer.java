/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.runtime.animation.UiEasing;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.input.UiScrollbarMetrics;
import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;

/** Paints the runtime-owned scrollbar overlay for scroll nodes. */
final class UiScrollbarRenderer {
    void render(UiNode node, UiRenderContext context) {
        UiScrollbarMetrics metrics = UiScrollbarMetrics.resolve(node);
        if (metrics == null) return;
        long now = System.nanoTime();
        long timeoutMs = Math.max(0L, Math.round(node.props().number("scrollbarVisibilityMs", 1100.0f)));
        boolean recent = node.state().lastScrollInteractionNanos() > 0L
                && now - node.state().lastScrollInteractionNanos() <= timeoutMs * 1_000_000L;
        float visibleTarget = (recent || node.state().scrollbarHovered() || node.state().scrollbarDragging()) ? 1.0f : 0.0f;
        float visible = node.state().motion("scrollbar:visible", visibleTarget, 220L, UiEasing.EASE_OUT_CUBIC, now);
        if (visible <= 0.001f) return;
        float hover = node.state().motion("scrollbar:hover", node.state().scrollbarHovered() ? 1.0f : 0.0f, 160L, UiEasing.EASE_OUT_CUBIC, now);
        float drag = node.state().motion("scrollbar:drag", node.state().scrollbarDragging() ? 1.0f : 0.0f, 160L, UiEasing.EASE_OUT_CUBIC, now);
        float alpha = node.props().number("scrollbarBaseAlpha", 0.28f)
                + node.props().number("scrollbarHoverAlpha", 0.24f) * hover
                + node.props().number("scrollbarDragAlpha", 0.28f) * drag;
        alpha = Math.max(0.0f, Math.min(1.0f, alpha * visible * context.alpha()));
        int base = UiReactiveVisual.rawColor(node.props().get("scrollbarColor"), 0xFFFFFFFF);
        int color = UiColor.withAlpha(base, alpha);
        float radius = context.renderLength(Math.max(0.0f, node.props().number("scrollbarRadius", 1.0f)));
        context.renderer().roundedRect(
                context.renderX(metrics.x()),
                context.renderY(metrics.thumbY()),
                context.renderLength(metrics.width()),
                context.renderLength(metrics.thumbHeight()),
                radius,
                color
        );
    }
}
