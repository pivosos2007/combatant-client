/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import java.awt.Color;

/** Area-to-baseline layer rendered from the same resolved path as its strokes. */
public record UiPathAreaFill(double baseline,
                             int topStartArgb,
                             int topEndArgb,
                             int bottomStartArgb,
                             int bottomEndArgb) {
    public static UiPathAreaFill solid(double baseline, int topArgb, int bottomArgb) {
        return new UiPathAreaFill(baseline, topArgb, topArgb, bottomArgb, bottomArgb);
    }

    public static UiPathAreaFill solid(double baseline, Color top, Color bottom) {
        return solid(baseline, argb(top), argb(bottom));
    }

    public static UiPathAreaFill gradient(double baseline,
                                          Color topStart,
                                          Color topEnd,
                                          Color bottomStart,
                                          Color bottomEnd) {
        return new UiPathAreaFill(baseline, argb(topStart), argb(topEnd), argb(bottomStart), argb(bottomEnd));
    }

    public boolean enabled() {
        return ((topStartArgb >>> 24) & 0xFF) > 0
                || ((topEndArgb >>> 24) & 0xFF) > 0
                || ((bottomStartArgb >>> 24) & 0xFF) > 0
                || ((bottomEndArgb >>> 24) & 0xFF) > 0;
    }

    private static int argb(Color color) {
        return color != null ? color.getRGB() : 0;
    }
}
