/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import java.awt.Color;

/** One ordered stroke layer rendered from an already-resolved UI path. */
public record UiPathStrokeLayer(double width,
                                int startArgb,
                                int endArgb,
                                UiPathCap cap,
                                UiPathJoin join) {
    public UiPathStrokeLayer {
        width = Math.max(0.0, width);
        cap = cap != null ? cap : UiPathCap.ROUND;
        join = join != null ? join : UiPathJoin.ROUND;
    }

    public static UiPathStrokeLayer solid(double width, int argb) {
        return new UiPathStrokeLayer(width, argb, argb, UiPathCap.ROUND, UiPathJoin.ROUND);
    }

    public static UiPathStrokeLayer solid(double width, Color color) {
        return solid(width, color != null ? color.getRGB() : 0);
    }

    public static UiPathStrokeLayer gradient(double width, int startArgb, int endArgb) {
        return new UiPathStrokeLayer(width, startArgb, endArgb, UiPathCap.ROUND, UiPathJoin.ROUND);
    }

    public static UiPathStrokeLayer gradient(double width, Color start, Color end) {
        return gradient(width, start != null ? start.getRGB() : 0, end != null ? end.getRGB() : 0);
    }

    public boolean enabled() {
        return width > 0.0 && (((startArgb >>> 24) & 0xFF) > 0 || ((endArgb >>> 24) & 0xFF) > 0);
    }
}
