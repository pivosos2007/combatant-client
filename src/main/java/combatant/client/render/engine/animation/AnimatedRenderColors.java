/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.animation;


import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.math.ColorMath;

import java.awt.Color;

/**
 * Shared animated color resolver for visual modules and HUD text gradients.
 *
 * <p>Callers provide the semantic mode, speed, phase/index and the configured
 * primary/secondary colors. The resolver keeps the old module color semantics
 * in one place instead of duplicating ColorUtils switch blocks in every module.</p>
 */
public enum AnimatedRenderColors {
    ;

    private static final int FULL_ALPHA = 0xFF000000;
    private static final float MODULE_LIST_ANIMATED_WEIGHT = 0.86f;
    private static final float MODULE_LIST_PALETTE_CYCLES_PER_SECOND = 0.025f;

    public enum Mode {
        STATIC,
        RAINBOW,
        LIGHT_RAINBOW,
        SKY,
        FADE,
        DOUBLE_COLOR,
        ANALOGOUS,
        THEME
    }

    public static boolean usesSecondary(Mode mode) {
        return mode == Mode.DOUBLE_COLOR || mode == Mode.ANALOGOUS;
    }

    public static boolean animated(Mode mode) {
        return mode != null && mode != Mode.STATIC;
    }

    public static int shaderMode(Mode mode) {
        return switch (mode != null ? mode : Mode.STATIC) {
            case STATIC -> 0;
            case RAINBOW -> 1;
            case LIGHT_RAINBOW -> 2;
            case SKY -> 3;
            case FADE -> 4;
            case DOUBLE_COLOR -> 5;
            case ANALOGOUS -> 6;
            case THEME -> 7;
        };
    }

    public static float angularOffset01(Mode mode, int speed) {
        if (!animated(mode)) {
            return 0.0f;
        }
        int safeSpeed = Math.max(1, speed);
        return wrap01(AnimationUtility.time(1.0f / (safeSpeed * 360.0f)));
    }

    public static int angularPrimaryColor(Mode mode, int primaryArgb) {
        if (mode == Mode.THEME) {
            return ColorMath.colorWithAlpha(Theme.theme().accent(), alpha(primaryArgb));
        }
        return primaryArgb;
    }

    public static int angularSecondaryColor(Mode mode, int primaryArgb, int secondaryArgb) {
        return switch (mode != null ? mode : Mode.STATIC) {
            case ANALOGOUS -> analogous(secondaryArgb);
            case THEME -> ColorMath.colorWithAlpha(Theme.theme().accentSoft(), alpha(primaryArgb));
            case FADE, STATIC, RAINBOW, LIGHT_RAINBOW, SKY -> primaryArgb;
            case DOUBLE_COLOR -> secondaryArgb;
        };
    }

    public static int resolve(Mode mode, int speed, int phase, int primaryArgb, int secondaryArgb) {
        return resolve(mode, speed, phase, primaryArgb, secondaryArgb, false);
    }

    public static int resolve(Mode mode,
                              int speed,
                              int phase,
                              int primaryArgb,
                              int secondaryArgb,
                              boolean preservePrimaryAlpha) {
        Mode resolved = mode != null ? mode : Mode.STATIC;
        int safeSpeed = Math.max(1, speed);
        int index = Math.floorMod(phase, 36000);
        int alpha = alpha(primaryArgb);

        return switch (resolved) {
            case SKY -> withAlpha(skyRainbowRgb(safeSpeed, index), preservePrimaryAlpha ? alpha : 255);
            case LIGHT_RAINBOW -> withAlpha(rainbowRgb(safeSpeed, index, 0.6f, 1.0f), preservePrimaryAlpha ? alpha : 255);
            case RAINBOW -> withAlpha(rainbowRgb(safeSpeed, index, 1.0f, 1.0f), preservePrimaryAlpha ? alpha : 255);
            case FADE -> fade(primaryArgb, safeSpeed, index, preservePrimaryAlpha ? alpha : 255);
            case DOUBLE_COLOR -> twoColor(primaryArgb, secondaryArgb, safeSpeed, index);
            case ANALOGOUS -> twoColorHue(primaryArgb, analogous(secondaryArgb), safeSpeed, index);
            case THEME -> themeColor(safeSpeed, index, preservePrimaryAlpha ? alpha : 255);
            case STATIC -> primaryArgb;
        };
    }

    public static int mixArgb(int a, int b, float t) {
        float clamped = clamp01(t);
        int aa = Math.round(alpha(a) + (alpha(b) - alpha(a)) * clamped);
        int rr = Math.round(red(a) + (red(b) - red(a)) * clamped);
        int gg = Math.round(green(a) + (green(b) - green(a)) * clamped);
        int bb = Math.round(blue(a) + (blue(b) - blue(a)) * clamped);
        return argb(aa, rr, gg, bb);
    }

    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (clamp255(alpha) << 24);
    }

    public static int scaleAlpha(int argb, float scale) {
        return withAlpha(argb, Math.round(alpha(argb) * clamp01(scale)));
    }

    public static int brighten(int argb, float factor) {
        float f = Math.max(0.0f, factor);
        return argb(alpha(argb), clamp255(Math.round(red(argb) * f)), clamp255(Math.round(green(argb) * f)), clamp255(Math.round(blue(argb) * f)));
    }

    public static int analogousColor(int argb) {
        return analogous(argb);
    }

    public static void moduleListBlockGlyphGradient(int topArgb,
                                                    int bottomArgb,
                                                    Mode mode,
                                                    int speed,
                                                    double glyphY0,
                                                    double glyphY1,
                                                    double blockY0,
                                                    double blockY1,
                                                    int glyphIndex,
                                                    float timeSec,
                                                    boolean topRainbow,
                                                    boolean bottomRainbow,
                                                    boolean shimmer,
                                                    int[] out) {
        if (out == null || out.length < 4) return;

        float topT = blockProgress(glyphY0, blockY0, blockY1);
        float bottomT = blockProgress(glyphY1, blockY0, blockY1);
        int topColor = moduleListBlockColor(topArgb, bottomArgb, mode, speed, topT, glyphIndex, timeSec, topRainbow, bottomRainbow, shimmer);
        int bottomColor = moduleListBlockColor(topArgb, bottomArgb, mode, speed, bottomT, glyphIndex, timeSec, topRainbow, bottomRainbow, shimmer);

        out[0] = topColor;
        out[1] = bottomColor;
        out[2] = bottomColor;
        out[3] = topColor;
    }

    public static int moduleListBlockColor(int topArgb,
                                           int bottomArgb,
                                           Mode mode,
                                           int speed,
                                           float blockProgress,
                                           int glyphIndex,
                                           float timeSec,
                                           boolean topRainbow,
                                           boolean bottomRainbow,
                                           boolean shimmer) {
        int safeSpeed = Math.max(1, speed);
        float t = clamp01(blockProgress);
        Mode resolved = mode != null ? mode : Mode.STATIC;

        int base = mixArgb(topArgb, bottomArgb, t);

        boolean rainbowEndpoints = topRainbow || bottomRainbow;
        if (rainbowEndpoints) {
            int endpointPalette = moduleListPaletteColor(Mode.RAINBOW, safeSpeed, t, timeSec,
                    topRainbow ? topArgb : bottomArgb,
                    bottomRainbow ? bottomArgb : topArgb);
            base = mixArgb(base, endpointPalette, MODULE_LIST_ANIMATED_WEIGHT);
        }

        if (resolved != Mode.STATIC) {
            int animated = moduleListPaletteColor(resolved, safeSpeed, t, timeSec, topArgb, bottomArgb);
            base = mixArgb(base, animated, MODULE_LIST_ANIMATED_WEIGHT);
        }

        if (shimmer) {
            float wave = 0.5f + 0.5f * (float) Math.sin(timeSec * safeSpeed * 0.16f + t * 6.2831855f + glyphIndex * 0.035f);
            int dark = mixArgb(base, withAlpha(0xFF000000, alpha(base)), 0.18f);
            int light = mixArgb(base, withAlpha(0xFFFFFFFF, alpha(base)), 0.28f);
            base = mixArgb(dark, light, wave);
        }

        return base;
    }

    private static int moduleListPaletteColor(Mode mode,
                                              int speed,
                                              float blockProgress,
                                              float timeSec,
                                              int primaryArgb,
                                              int secondaryArgb) {
        int safeSpeed = Math.max(1, speed);
        float t = clamp01(blockProgress);
        float paletteOffset = animated(mode) ? paletteOffset(timeSec, safeSpeed) : 0.0f;
        float paletteT = wrap01(t + paletteOffset);
        int alpha = Math.round(alpha(primaryArgb) + (alpha(secondaryArgb) - alpha(primaryArgb)) * t);

        return switch (mode != null ? mode : Mode.STATIC) {
            case RAINBOW -> withAlpha(Color.HSBtoRGB(paletteT, 1.0f, 1.0f), alpha);
            case LIGHT_RAINBOW -> withAlpha(Color.HSBtoRGB(paletteT, 0.6f, 1.0f), alpha);
            case SKY -> withAlpha(Color.HSBtoRGB(skyHue(paletteT), 0.5f, 1.0f), alpha);
            case FADE -> fadeThroughValue(primaryArgb, cyclicMix(paletteT), alpha);
            case DOUBLE_COLOR -> mixArgb(primaryArgb, secondaryArgb, cyclicMix(paletteT));
            case ANALOGOUS -> mixArgb(primaryArgb, analogous(secondaryArgb), cyclicMix(paletteT));
            case THEME -> themeColor(safeSpeed, Math.round(paletteT * 360.0f), alpha);
            case STATIC -> mixArgb(primaryArgb, secondaryArgb, t);
        };
    }

    private static int themeColor(int speed, int index, int alpha) {
        Themes.Theme theme = Theme.theme();
        int primary = withAlpha(theme.accent(), alpha);
        int secondary = withAlpha(theme.accentSoft(), alpha);
        return twoColor(primary, secondary, speed, index);
    }

    private static float paletteOffset(float timeSec, int speed) {
        return wrap01(timeSec * Math.max(1, speed) * MODULE_LIST_PALETTE_CYCLES_PER_SECOND);
    }

    private static float cyclicMix(float value) {
        float wrapped = wrap01(value);
        return wrapped < 0.5f ? wrapped * 2.0f : (1.0f - wrapped) * 2.0f;
    }

    private static float blockProgress(double y, double blockY0, double blockY1) {
        double height = Math.max(1.0, blockY1 - blockY0);
        return clamp01((float) ((y - blockY0) / height));
    }

    private static int fadeThroughValue(int primaryArgb, float blockProgress, int alpha) {
        float[] hsb = Color.RGBtoHSB(red(primaryArgb), green(primaryArgb), blue(primaryArgb), null);
        float value = 0.38f + 0.62f * clamp01(blockProgress);
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], value) & 0x00FFFFFF;
        return (clamp255(alpha) << 24) | rgb;
    }

    private static float skyHue(float t) {
        float wrapped = wrap01(t);
        return wrapped < 0.5f ? -wrapped : wrapped;
    }

    private static float wrap01(float value) {
        return value - (float) Math.floor(value);
    }

    private static int rainbowRgb(int speed, int index, float saturation, float brightness) {
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360);
        return FULL_ALPHA | (Color.HSBtoRGB(angle / 360.0f, saturation, brightness) & 0x00FFFFFF);
    }

    private static int skyRainbowRgb(int speed, int index) {
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360);
        float hue = ((angle %= 360) / 360.0f) < 0.5f ? -(angle / 360.0f) : (angle / 360.0f);
        return FULL_ALPHA | (Color.HSBtoRGB(hue, 0.5f, 1.0f) & 0x00FFFFFF);
    }

    private static int fade(int primaryArgb, int speed, int index, int alpha) {
        float[] hsb = Color.RGBtoHSB(red(primaryArgb), green(primaryArgb), blue(primaryArgb), null);
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360);
        angle = (angle > 180 ? 360 - angle : angle) + 180;
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], angle / 360.0f) & 0x00FFFFFF;
        return (clamp255(alpha) << 24) | rgb;
    }

    private static int twoColor(int primaryArgb, int secondaryArgb, int speed, int index) {
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360);
        angle = (angle >= 180 ? 360 - angle : angle) * 2;
        return mixArgb(primaryArgb, secondaryArgb, angle / 360.0f);
    }

    private static int twoColorHue(int primaryArgb, int secondaryArgb, int speed, int index) {
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360);
        angle = (angle >= 180 ? 360 - angle : angle) * 2;
        float t = clamp01(angle / 360.0f);
        float[] a = Color.RGBtoHSB(red(primaryArgb), green(primaryArgb), blue(primaryArgb), null);
        float[] b = Color.RGBtoHSB(red(secondaryArgb), green(secondaryArgb), blue(secondaryArgb), null);
        int rgb = Color.HSBtoRGB(lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)) & 0x00FFFFFF;
        int outAlpha = Math.round(alpha(primaryArgb) + (alpha(secondaryArgb) - alpha(primaryArgb)) * t);
        return (clamp255(outAlpha) << 24) | rgb;
    }

    private static int analogous(int argb) {
        float[] hsb = Color.RGBtoHSB(red(argb), green(argb), blue(argb), null);
        int rgb = Color.HSBtoRGB(hsb[0] - 0.84f, hsb[1], hsb[2]) & 0x00FFFFFF;
        return (alpha(argb) << 24) | rgb;
    }

    private static int argb(int a, int r, int g, int b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }
}
