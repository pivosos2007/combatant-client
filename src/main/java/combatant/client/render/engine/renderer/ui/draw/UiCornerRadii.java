/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

public record UiCornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
    public static final UiCornerRadii ZERO = new UiCornerRadii(0f, 0f, 0f, 0f);

    public static UiCornerRadii uniform(double value) {
        float v = (float) Math.max(0.0, value);
        return new UiCornerRadii(v, v, v, v);
    }

    public static UiCornerRadii of(double topLeft, double topRight, double bottomRight, double bottomLeft) {
        return new UiCornerRadii(
                (float) Math.max(0.0, topLeft),
                (float) Math.max(0.0, topRight),
                (float) Math.max(0.0, bottomRight),
                (float) Math.max(0.0, bottomLeft)
        );
    }

    public boolean isZero() {
        return topLeft == 0f && topRight == 0f && bottomRight == 0f && bottomLeft == 0f;
    }
}
