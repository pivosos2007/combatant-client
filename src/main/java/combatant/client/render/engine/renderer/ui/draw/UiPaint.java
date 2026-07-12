/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

public record UiPaint(UiPaintKind kind,
                      int topLeft,
                      int topRight,
                      int bottomRight,
                      int bottomLeft,
                      float angleDeg,
                      float offsetPx) {
    public static UiPaint solid(int argb) {
        return new UiPaint(UiPaintKind.SOLID, argb, argb, argb, argb, 0f, 0f);
    }

    public static UiPaint corners(int topLeft, int topRight, int bottomRight, int bottomLeft) {
        boolean solid = topLeft == topRight && topLeft == bottomRight && topLeft == bottomLeft;
        return new UiPaint(solid ? UiPaintKind.SOLID : UiPaintKind.CORNER_GRADIENT,
                topLeft, topRight, bottomRight, bottomLeft, 0f, 0f);
    }

    public static UiPaint linear(int startArgb, int endArgb, float angleDeg, float offsetPx) {
        return new UiPaint(UiPaintKind.LINEAR_GRADIENT, startArgb, endArgb, endArgb, startArgb, angleDeg, offsetPx);
    }

    public boolean isSolid() {
        return kind == UiPaintKind.SOLID || (topLeft == topRight && topLeft == bottomRight && topLeft == bottomLeft);
    }

    public int solidColor() {
        return topLeft;
    }
}
