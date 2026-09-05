/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.component.solid;

import combatant.client.render.engine.renderer.ui.runtime.style.UiTheme;
import combatant.client.render.engine.renderer.ui.runtime.style.UiThemeRegistry;

/** Rockstar-derived metrics, motion timings and an accent-relative palette. */
public final class SolidStyleTokens {
    public static final float ROOT_RADIUS = 12.0f;
    public static final float ROOT_SQUIRCLE = 2.0f;
    public static final float NAV_ITEM_SIZE = 17.0f;
    public static final float NAV_ICON_SIZE = 9.0f;
    public static final float NAV_RADIUS = 4.0f;
    public static final float COLLECTION_ROW_HEIGHT = 15.0f;
    public static final float COLLECTION_ROW_RADIUS = 3.0f;
    public static final float SEARCH_WIDTH = 81.0f;
    public static final float SEARCH_HEIGHT = 12.0f;
    public static final float SEARCH_RADIUS = 3.0f;
    public static final float CARD_RADIUS = 7.0f;
    public static final float SHORTCUT_RADIUS = 4.0f;
    public static final float WHEEL_STEP = 22.0f;
    public static final long STATE_MOTION_MS = 160L;
    public static final long VISIBILITY_MOTION_MS = 220L;
    public static final long HOVER_MOTION_MS = 300L;
    public static final long APPEAR_MOTION_MS = 240L;
    public static final long SCROLLBAR_TIMEOUT_MS = 1100L;
    public static final float MARQUEE_SPEED = 35.0f;
    public static final long MARQUEE_HOLD_MS = 600L;

    private static final int BASELINE_ACCENT = 0xFF906BFF;

    private final int accent;
    private final int surface;
    private final int surfaceWeak;
    private final int surfaceStrong;
    private final int separator;
    private final int deepBackground;
    private final int foreground;

    public SolidStyleTokens(int accentArgb) {
        accent = forceOpaque(accentArgb);
        surface = shiftFromBaseline(0xE618151D, accent);
        surfaceWeak = shiftFromBaseline(0x6618151D, accent);
        surfaceStrong = shiftFromBaseline(0xFF1A171F, accent);
        separator = shiftFromBaseline(0x593D3647, accent);
        deepBackground = shiftFromBaseline(0xFF050407, accent);
        foreground = 0xFFFFFFFF;
    }

    public static SolidStyleTokens defaults() {
        return new SolidStyleTokens(BASELINE_ACCENT);
    }

    /** Resolves the component accent through Combatant's current theme registry. */
    public static SolidStyleTokens currentTheme() {
        UiTheme theme = UiThemeRegistry.current();
        int accent = theme.color("solid.accent", theme.color("accent", theme.color("primary", BASELINE_ACCENT)));
        return new SolidStyleTokens(accent);
    }

    public int accent() { return accent; }
    public int surface() { return surface; }
    public int surfaceWeak() { return surfaceWeak; }
    public int surfaceStrong() { return surfaceStrong; }
    public int separator() { return separator; }
    public int deepBackground() { return deepBackground; }
    public int foreground() { return foreground; }

    public int foreground(float alpha) {
        return withAlpha(foreground, alpha);
    }

    public int rowBackground(float emphasized, float selected, float hover) {
        float mix = 0.08f * clamp01(emphasized) + 0.018f * clamp01(selected) + 0.025f * clamp01(hover);
        return mix(surfaceWeak, withAlpha(foreground, alpha(surfaceWeak)), mix);
    }

    public int rowText(float emphasized, float selected) {
        int color = mix(foreground, accent, 0.5f * clamp01(emphasized));
        return withAlpha(color, 0.60f + 0.30f * clamp01(emphasized) + 0.10f * clamp01(selected));
    }

    public int subtleInteractive(float hover) {
        return mix(withAlpha(surface, 0.40f), withAlpha(foreground, 0.40f), 0.025f * clamp01(hover));
    }

    public int searchBackground(float hover) {
        int base = withAlpha(surface, 173.4f / 255.0f);
        return mix(base, withAlpha(foreground, alpha(base)), 0.035f + 0.035f * clamp01(hover));
    }

    public static int mix(int from, int to, float amount) {
        float t = clamp01(amount);
        int a = Math.round(channel(from, 24) + (channel(to, 24) - channel(from, 24)) * t);
        int r = Math.round(channel(from, 16) + (channel(to, 16) - channel(from, 16)) * t);
        int g = Math.round(channel(from, 8) + (channel(to, 8) - channel(from, 8)) * t);
        int b = Math.round(channel(from, 0) + (channel(to, 0) - channel(from, 0)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int withAlpha(int argb, float alpha) {
        return (argb & 0x00FFFFFF) | Math.round(255.0f * clamp01(alpha)) << 24;
    }

    private static int shiftFromBaseline(int source, int accent) {
        float[] baseline = rgbToHsb(BASELINE_ACCENT);
        float[] target = rgbToHsb(accent);
        float[] value = rgbToHsb(source);
        if (value[1] == 0.0f) return source;
        float hue = value[0] + target[0] - baseline[0];
        hue -= (float) Math.floor(hue);
        float saturationRatio = baseline[1] == 0.0f ? 1.0f : target[1] / baseline[1];
        int rgb = hsbToRgb(hue, clamp01(value[1] * saturationRatio), value[2]);
        return source & 0xFF000000 | rgb;
    }

    private static float[] rgbToHsb(int argb) {
        float r = channel(argb, 16) / 255.0f;
        float g = channel(argb, 8) / 255.0f;
        float b = channel(argb, 0) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0.0f) h = 0.0f;
        else if (max == r) h = ((g - b) / d) % 6.0f;
        else if (max == g) h = (b - r) / d + 2.0f;
        else h = (r - g) / d + 4.0f;
        h /= 6.0f;
        if (h < 0.0f) h += 1.0f;
        return new float[]{h, max == 0.0f ? 0.0f : d / max, max};
    }

    private static int hsbToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1.0f - Math.abs((h * 6.0f) % 2.0f - 1.0f));
        float m = v - c;
        float r = 0, g = 0, b = 0;
        int sector = (int) Math.floor(h * 6.0f) % 6;
        switch (sector) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        return Math.round((r + m) * 255.0f) << 16
                | Math.round((g + m) * 255.0f) << 8
                | Math.round((b + m) * 255.0f);
    }

    private static int forceOpaque(int color) { return color | 0xFF000000; }
    private static int channel(int color, int shift) { return color >>> shift & 0xFF; }
    private static float alpha(int color) { return channel(color, 24) / 255.0f; }
    private static float clamp01(float value) { return Math.max(0.0f, Math.min(1.0f, value)); }
}
