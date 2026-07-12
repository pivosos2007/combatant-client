/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.color;

public enum ColorEffects {
    ;

    public static int argb(int rgb, float alpha) {
        int a = clamp255(Math.round(clamp01(alpha) * 255.0f));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    public static int argbWithBaseAlpha(int baseArgb, int rgb, float alphaMultiplier) {
        int baseAlpha = (baseArgb >>> 24) & 0xFF;
        int alpha = clamp255(Math.round(baseAlpha * clamp01(alphaMultiplier)));
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    public static int multiplyAlpha(int argb, float alphaMultiplier) {
        int alpha = (argb >>> 24) & 0xFF;
        int outAlpha = clamp255(Math.round(alpha * clamp01(alphaMultiplier)));
        return (outAlpha << 24) | (argb & 0x00FFFFFF);
    }

    public static int tintRgb(int sourceRgb, int tintRgb, float strength, ColorBlendMode mode, float phase) {
        float t = clamp01(strength);
        if (t <= 0.0f || mode == null || mode == ColorBlendMode.ORIGINAL) {
            return sourceRgb & 0x00FFFFFF;
        }

        int source = sourceRgb & 0x00FFFFFF;
        int tint = tintRgb & 0x00FFFFFF;
        return switch (mode) {
            case ORIGINAL -> source;
            case SOFT -> mixRgb(source, tint, t * 0.72f);
            case ATMOSPHERIC -> mixRgb(source, screenRgb(source, tint), t * 0.64f);
            case CHROMATIC -> chromaticTint(source, tint, t, phase);
            case ESSENCE -> essenceTint(source, tint, t, phase);
        };
    }

    public static int mixRgb(int a, int b, float amount) {
        float t = clamp01(amount);
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(bl);
    }

    public static int screenRgb(int a, int b) {
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = 255 - ((255 - ar) * (255 - br)) / 255;
        int g = 255 - ((255 - ag) * (255 - bg)) / 255;
        int bl = 255 - ((255 - ab) * (255 - bb)) / 255;
        return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(bl);
    }

    public static int overlayRgb(int a, int b) {
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = overlayChannel(ar, br);
        int g = overlayChannel(ag, bg);
        int bl = overlayChannel(ab, bb);
        return (r << 16) | (g << 8) | bl;
    }

    public static float hash01(float seed) {
        float value = (float) (Math.sin(seed * 12.9898f + 78.233f) * 43758.5453f);
        return value - (float) Math.floor(value);
    }

    private static int chromaticTint(int source, int tint, float strength, float phase) {
        int shifted = rotateRgb(tint, hash01(phase) < 0.5f ? 1 : 2);
        int sourceScreen = screenRgb(source, shifted);
        int colorized = mixRgb(shifted, sourceScreen, 0.42f);
        return mixRgb(source, colorized, strength * 0.72f);
    }

    private static int essenceTint(int source, int tint, float strength, float phase) {
        int warm = mixRgb(tint, rotateRgb(tint, 1), 0.22f + hash01(phase) * 0.28f);
        int overlay = overlayRgb(source, warm);
        int glow = screenRgb(source, warm);
        int enhanced = mixRgb(overlay, glow, 0.36f);
        return mixRgb(source, enhanced, strength * 0.82f);
    }

    private static int rotateRgb(int rgb, int steps) {
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;
        int s = Math.floorMod(steps, 3);
        if (s == 1) {
            return (g << 16) | (b << 8) | r;
        }
        if (s == 2) {
            return (b << 16) | (r << 8) | g;
        }
        return rgb & 0x00FFFFFF;
    }

    private static int overlayChannel(int a, int b) {
        if (a < 128) {
            return clamp255((2 * a * b) / 255);
        }
        return clamp255(255 - (2 * (255 - a) * (255 - b)) / 255);
    }

    private static float clamp01(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    private static int clamp255(int value) {
        if (value < 0) return 0;
        if (value > 255) return 255;
        return value;
    }
}
