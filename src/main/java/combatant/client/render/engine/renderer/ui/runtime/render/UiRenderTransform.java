/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;

/**
 * Maps retained UI logical units into render-space coordinates.
 *
 * <p>Layout, script props and measurements remain in logical units. The transform is applied only
 * at the paint boundary, so widget/HUD scaling never changes the meaning of authored numbers.</p>
 */
public record UiRenderTransform(float translateX, float translateY, float scale) {
    public static final UiRenderTransform IDENTITY = new UiRenderTransform(0.0f, 0.0f, 1.0f);

    public UiRenderTransform {
        if (!Float.isFinite(translateX)) translateX = 0.0f;
        if (!Float.isFinite(translateY)) translateY = 0.0f;
        if (!Float.isFinite(scale) || scale <= 0.0f) scale = 1.0f;
    }

    public static UiRenderTransform scaleAt(float translateX, float translateY, float scale) {
        if (Math.abs(translateX) <= 0.0001f && Math.abs(translateY) <= 0.0001f && Math.abs(scale - 1.0f) <= 0.0001f) {
            return IDENTITY;
        }
        return new UiRenderTransform(translateX, translateY, scale);
    }

    public boolean identity() {
        return this == IDENTITY
                || (Math.abs(translateX) <= 0.0001f
                && Math.abs(translateY) <= 0.0001f
                && Math.abs(scale - 1.0f) <= 0.0001f);
    }

    public float x(float logicalX) {
        return translateX + logicalX * scale;
    }

    public double x(double logicalX) {
        return translateX + logicalX * scale;
    }

    public float y(float logicalY) {
        return translateY + logicalY * scale;
    }

    public double y(double logicalY) {
        return translateY + logicalY * scale;
    }

    public float logicalX(float renderX) {
        return (renderX - translateX) / scale;
    }

    public float logicalY(float renderY) {
        return (renderY - translateY) / scale;
    }

    public float length(float logicalLength) {
        return logicalLength * scale;
    }

    public double length(double logicalLength) {
        return logicalLength * scale;
    }

    public UiBounds bounds(UiBounds logical) {
        if (logical == null) return UiBounds.ZERO;
        if (identity()) return logical;
        return new UiBounds(
                x(logical.x()),
                y(logical.y()),
                Math.max(0.0f, length(logical.width())),
                Math.max(0.0f, length(logical.height()))
        );
    }
}
