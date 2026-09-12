/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.input;

import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;

/** Vertical scrollbar geometry shared by input and renderer. */
public record UiScrollbarMetrics(float x,
                                 float y,
                                 float width,
                                 float height,
                                 float thumbY,
                                 float thumbHeight,
                                 float maxScroll,
                                 float travel) {
    public static UiScrollbarMetrics resolve(UiNode node) {
        if (node == null || node.type() != UiNodeType.SCROLL || !node.props().bool("scrollbar", false)) return null;
        UiBounds b = node.bounds();
        float content = node.state().contentHeight();
        float viewport = b.height();
        float maxScroll = Math.max(0.0f, content - viewport);
        if (maxScroll <= 0.5f || viewport <= 0.0f) return null;

        float width = Math.max(0.5f, node.props().number("scrollbarWidth", 2.0f));
        float offset = node.props().number("scrollbarOffset", -3.0f);
        float leading = Math.max(0.0f, node.props().number("scrollbarLeadingInset", 2.0f));
        float trailing = Math.max(0.0f, node.props().number("scrollbarTrailingInset", 2.0f));
        float trackY = b.y() + leading;
        float trackH = Math.max(0.0f, b.height() - leading - trailing);
        float minThumb = Math.max(1.0f, node.props().number("scrollbarMinThumb", 18.0f));
        float thumbH = Math.min(trackH, Math.max(minThumb, trackH * (viewport / Math.max(viewport, content))));
        float travel = Math.max(0.0f, trackH - thumbH);
        float ratio = maxScroll <= 0.0f ? 0.0f : clamp(node.state().scrollY() / maxScroll, 0.0f, 1.0f);
        float thumbY = trackY + travel * ratio;
        float x = b.x() + b.width() + offset;
        return new UiScrollbarMetrics(x, trackY, width, trackH, thumbY, thumbH, maxScroll, travel);
    }

    public boolean containsTrack(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    public boolean containsThumb(float px, float py) {
        return px >= x && px <= x + width && py >= thumbY && py <= thumbY + thumbHeight;
    }

    public float scrollForThumbY(float proposedThumbY) {
        if (travel <= 0.0f || maxScroll <= 0.0f) return 0.0f;
        float ratio = clamp((proposedThumbY - y) / travel, 0.0f, 1.0f);
        return maxScroll * ratio;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
