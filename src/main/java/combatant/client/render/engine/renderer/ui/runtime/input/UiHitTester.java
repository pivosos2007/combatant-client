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
        UiNode hit = hitNode(root, x, y);
        if (hit == null) return UiHitResult.MISS;
        UiBounds bounds = hit.bounds();
        return new UiHitResult(hit, x - bounds.x(), y - bounds.y());
    }

    private UiNode hitNode(UiNode node, float x, float y) {
        if (node == null || !node.state().visible() || node.state().disabled()) return null;
        UiBounds bounds = node.bounds();
        if (!contains(bounds, x, y)) return null;

        List<UiNode> children = node.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            UiNode childHit = hitNode(children.get(i), x, y);
            if (childHit != null) return childHit;
        }
        if (!containsVisualShape(node, bounds, x, y)) return null;
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
