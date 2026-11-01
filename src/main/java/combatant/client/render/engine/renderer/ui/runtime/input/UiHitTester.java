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
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.draw.UiSquircleProfile;

import java.util.List;
import java.util.Locale;

public final class UiHitTester {
    private static boolean contains(UiBounds bounds, float x, float y) {
        return x >= bounds.x()
                && x <= bounds.x() + bounds.width()
                && y >= bounds.y()
                && y <= bounds.y() + bounds.height();
    }

    public UiHitResult hitTest(UiNode root, float x, float y) {
        UiNode hit = hitNode(root, x, y, false);
        if (hit == null) return UiHitResult.MISS;
        UiBounds bounds = hit.bounds();
        return new UiHitResult(hit, x - bounds.x(), y - bounds.y());
    }

    /**
     * Visual hit-testing used only for cursor feedback. A disabled control remains a
     * pointer target for NOT_ALLOWED, but is never returned by hitTest() and cannot
     * receive an action, hover animation or focus. The same z-order is respected.
     */
    public UiHitResult cursorHitTest(UiNode root, float x, float y) {
        UiNode hit = hitNode(root, x, y, true);
        if (hit == null) return UiHitResult.MISS;
        UiBounds bounds = hit.bounds();
        return new UiHitResult(hit, x - bounds.x(), y - bounds.y());
    }

    private UiNode hitNode(UiNode node, float x, float y, boolean includeDisabled) {
        if (node == null || !node.state().visible()) return null;
        UiBounds bounds = node.bounds();
        if (!contains(bounds, x, y)) return null;
        // A disabled parent disables its entire subtree. Its full control bounds,
        // including the label, are the cursor target. No child can be activated.
        if (node.state().disabled()) {
            return includeDisabled && !"none".equalsIgnoreCase(node.props().string("pointerEvents", "auto"))
                    && containsVisualShape(node, bounds, x, y) ? node : null;
        }
        List<UiNode> children = node.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode childHit = hitNode(children.get(i), x, y, includeDisabled);
            if (childHit != null) return childHit;
        }
        if (!containsVisualShape(node, bounds, x, y)) return null;
        if (!node.props().bool("interactive", true)
                || "none".equalsIgnoreCase(node.props().string("pointerEvents", "auto"))) return null;
        return node;
    }

    private static boolean containsVisualShape(UiNode node, UiBounds bounds, float x, float y) {
        if (node.type() != UiNodeType.SHAPE) return true;
        UiProps props = node.props();
        String shape = props.string("shape", "").toLowerCase(Locale.ROOT);
        if (!shape.equals("squircle") && !shape.equals("superellipse")) return true;

        double halfWidth = bounds.width() * 0.5;
        double halfHeight = bounds.height() * 0.5;
        if (halfWidth <= 0.0 || halfHeight <= 0.0) return false;
        double nx = Math.abs((x - (bounds.x() + halfWidth)) / halfWidth);
        double ny = Math.abs((y - (bounds.y() + halfHeight)) / halfHeight);
        String profile = props.string("profile", "standard").toLowerCase(Locale.ROOT);
        float fallbackPower = switch (profile) {
            case "soft" -> UiSquircleProfile.SOFT.exponent();
            case "tight" -> UiSquircleProfile.TIGHT.exponent();
            default -> UiSquircleProfile.STANDARD.exponent();
        };
        double exponent = props.number("power", props.number("exponent", fallbackPower));
        exponent = Math.max(2.0, Math.min(16.0, Double.isFinite(exponent) ? exponent : fallbackPower));
        return Math.pow(nx, exponent) + Math.pow(ny, exponent) <= 1.0 + 1.0e-6;
    }
}
