/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import java.awt.Color;

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

    /** IDE-friendly authored color overload. Internal/hot paths should keep using packed ARGB ints. */
    public static UiPaint solid(Color color) {
        return solid(color != null ? color.getRGB() : 0);
    }

    public static UiPaint corners(int topLeft, int topRight, int bottomRight, int bottomLeft) {
        boolean solid = topLeft == topRight && topLeft == bottomRight && topLeft == bottomLeft;
        return new UiPaint(solid ? UiPaintKind.SOLID : UiPaintKind.CORNER_GRADIENT,
                topLeft, topRight, bottomRight, bottomLeft, 0f, 0f);
    }

    public static UiPaint corners(Color topLeft, Color topRight, Color bottomRight, Color bottomLeft) {
        return corners(argb(topLeft), argb(topRight), argb(bottomRight), argb(bottomLeft));
    }

    public static UiPaint linear(int startArgb, int endArgb, float angleDeg, float offsetPx) {
        return new UiPaint(UiPaintKind.LINEAR_GRADIENT, startArgb, endArgb, endArgb, startArgb, angleDeg, offsetPx);
    }

    public static UiPaint linear(Color start, Color end, float angleDeg, float offsetPx) {
        return linear(argb(start), argb(end), angleDeg, offsetPx);
    }

    private static int argb(Color color) {
        return color != null ? color.getRGB() : 0;
    }

    public boolean isSolid() {
        return kind == UiPaintKind.SOLID || (topLeft == topRight && topLeft == bottomRight && topLeft == bottomLeft);
    }

    public int solidColor() {
        return topLeft;
    }
}
