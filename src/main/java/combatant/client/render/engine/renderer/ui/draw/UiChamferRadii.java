/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

public record UiChamferRadii(float topLeftX, float topLeftY,
                             float topRightX, float topRightY,
                             float bottomRightX, float bottomRightY,
                             float bottomLeftX, float bottomLeftY) {
    public static final UiChamferRadii ZERO = new UiChamferRadii(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);

    public static UiChamferRadii uniform(double value) {
        float v = (float) Math.max(0.0, value);
        return new UiChamferRadii(v, v, v, v, v, v, v, v);
    }

    public static UiChamferRadii of(double tl, double tr, double br, double bl) {
        return new UiChamferRadii((float) Math.max(0.0, tl), (float) Math.max(0.0, tl),
                (float) Math.max(0.0, tr), (float) Math.max(0.0, tr),
                (float) Math.max(0.0, br), (float) Math.max(0.0, br),
                (float) Math.max(0.0, bl), (float) Math.max(0.0, bl));
    }

    public static UiChamferRadii axes(double tlX, double tlY, double trX, double trY,
                                      double brX, double brY, double blX, double blY) {
        return new UiChamferRadii((float) Math.max(0.0, tlX), (float) Math.max(0.0, tlY),
                (float) Math.max(0.0, trX), (float) Math.max(0.0, trY),
                (float) Math.max(0.0, brX), (float) Math.max(0.0, brY),
                (float) Math.max(0.0, blX), (float) Math.max(0.0, blY));
    }
}
