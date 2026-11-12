/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;

/** Shared theme color blending for native HUD, ClickGui and scripted glass. */
public final class LiquidGlassTheme {
    private LiquidGlassTheme() { }

    /** Mix only the glass RGB; opacity and the already captured scene stay unchanged. */
    public static int tint(int argb, float blend) {
        if (blend <= 0f) return argb;
        Themes.Theme theme = Theme.theme();
        if (theme == null) return argb;
        float t = Math.max(0f, Math.min(1f, blend));
        int surface = theme.surface();
        int accent = theme.accentSoft();
        int target = mixRgb(surface, accent, 0.24f);
        return (argb & 0xFF000000) | (mixRgb(argb, target, t) & 0x00FFFFFF);
    }

    private static int mixRgb(int a, int b, float t) {
        int r = Math.round(((a >>> 16) & 255) * (1f - t) + ((b >>> 16) & 255) * t);
        int g = Math.round(((a >>> 8) & 255) * (1f - t) + ((b >>> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1f - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
