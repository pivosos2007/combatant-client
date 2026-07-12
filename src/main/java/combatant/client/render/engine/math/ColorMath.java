/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.math;

import net.minecraft.util.Mth;

public enum ColorMath {
    ;

    public static int colorWithAlpha(int rgb, int alpha) {
        int a = clamp255(alpha);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    public static int mixRgb(int rgbA, int rgbB, float t) {
        float clamped = Mth.clamp(t, 0f, 1f);
        int ar = (rgbA >>> 16) & 0xFF;
        int ag = (rgbA >>> 8) & 0xFF;
        int ab = rgbA & 0xFF;
        int br = (rgbB >>> 16) & 0xFF;
        int bg = (rgbB >>> 8) & 0xFF;
        int bb = rgbB & 0xFF;

        int r = Math.round(ar + (br - ar) * clamped);
        int g = Math.round(ag + (bg - ag) * clamped);
        int b = Math.round(ab + (bb - ab) * clamped);
        return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    public static int scaleAlpha(int argb, float alpha) {
        int a = (argb >>> 24) & 0xFF;
        int na = Math.round(a * Mth.clamp(alpha, 0f, 1f));
        return (argb & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    public static int premultiplyAlpha(int rgb, int alpha) {
        int a = clamp255(alpha);
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (r * a + 127) / 255;
        g = (g * a + 127) / 255;
        b = (b * a + 127) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int premultiplyArgb(int argb) {
        return premultiplyAlpha(argb & 0x00FFFFFF, (argb >>> 24) & 0xFF);
    }

    private static int clamp255(int v) {
        return Math.min(255, Math.max(0, v));
    }
}
