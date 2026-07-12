/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;


import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.renderer.Renderer2D;


public enum MatteHudStyle {
    ;

    private static final int FALLBACK_SURFACE = 0xEE0D0F12;
    private static final int FALLBACK_STROKE = 0x663A4148;
    private static final int FALLBACK_ACCENT = 0xFF5CC8E7;

    public static void drawPlate(Renderer2D renderer,
                                 double x,
                                 double y,
                                 double width,
                                 double height,
                                 float radius,
                                 float alpha) {
        if (renderer == null || width <= 0.0 || height <= 0.0 || alpha <= 0.001f) return;
        float a = clamp01(alpha);
        float maxRadius = (float) Math.min(width, height) * 0.5f;
        float roundedFloor = Math.min(4.0f, (float) height * 0.36f);
        float r = Math.min(Math.max(radius, roundedFloor), maxRadius);

        int surface = surfaceColor(a);
        int top = withAlpha(mixRgb(surface, 0xFFFFFF, 0.018f), alphaOf(surface));
        int bottom = withAlpha(mixRgb(surface, 0x000000, 0.070f), alphaOf(surface));
        int stroke = strokeColor(a);

        renderer.roundedRectSoftShadow(x, y, width, height, r, 3.0f, 0.08f, withAlpha(0x000000, Math.round(112.0f * a)));
        renderer.roundedRectGradient(x, y, width, height, r, 0.0f, top, bottom, 90.0f);
        renderer.roundedRectStroke(x, y, width, height, r, 0.0f, 0.6f, stroke);
    }

    public static void drawLabelPlate(Renderer2D renderer,
                                      double textX,
                                      double textY,
                                      double textWidth,
                                      double textHeight,
                                      float alpha) {
        drawPlate(renderer, textX - 3.0, textY - 1.5, textWidth + 6.0, textHeight + 3.0, 3.5f, alpha);
    }

    public static void drawFrame(Renderer2D renderer,
                                 double x,
                                 double y,
                                 double width,
                                 double height,
                                 int baseColor,
                                 float alpha) {
        if (renderer == null || width <= 1.0 || height <= 1.0 || alpha <= 0.001f) return;
        float a = clamp01(alpha);
        Themes.Theme theme = Theme.theme();
        int neutralStroke = theme != null ? theme.strokeSoft() : FALLBACK_STROKE;
        int outline = withAlpha(mixRgb(baseColor, neutralStroke & 0x00FFFFFF, 0.58f), Math.round(166.0f * a));
        int dark = withAlpha(0x000000, Math.round(104.0f * a));
        renderer.roundedRectStroke(x - 1.0, y - 1.0, width + 2.0, height + 2.0, 2.75f, 0.0f, 0.9f, dark);
        renderer.roundedRectStroke(x, y, width, height, 2.25f, 0.0f, 1.0f, outline);
    }

    public static int surfaceColor(float alpha) {
        Themes.Theme theme = Theme.theme();
        int accent = theme != null ? theme.accent() : FALLBACK_ACCENT;
        int surface = theme != null ? theme.windowBg() : FALLBACK_SURFACE;
        int rgb = mixRgb(mixRgb(surface, 0x000000, 0.24f), accent & 0x00FFFFFF, 0.10f);
        return withAlpha(rgb, Math.round(198.0f * clamp01(alpha)));
    }

    public static int strokeColor(float alpha) {
        Themes.Theme theme = Theme.theme();
        int stroke = theme != null ? theme.strokeSoft() : FALLBACK_STROKE;
        int surface = theme != null ? theme.windowBg() : FALLBACK_SURFACE;
        int rgb = mixRgb(stroke, surface & 0x00FFFFFF, 0.22f);
        return withAlpha(rgb, Math.round(56.0f * clamp01(alpha)));
    }

    public static int accentSoft(float alpha) {
        Themes.Theme theme = Theme.theme();
        int accent = theme != null ? theme.accent() : FALLBACK_ACCENT;
        return withAlpha(accent, Math.round(78.0f * clamp01(alpha)));
    }

    public static int textMuted(float alpha) {
        Themes.Theme theme = Theme.theme();
        int muted = theme != null ? theme.textMuted() : 0x88C6D2CD;
        return withAlpha(muted, Math.round(alphaOf(muted) * clamp01(alpha)));
    }

    public static int withAlpha(int argb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int scaleAlpha(int argb, float alpha) {
        return withAlpha(argb, Math.round(alphaOf(argb) * clamp01(alpha)));
    }

    public static int mixRgb(int baseColor, int targetRgb, float t) {
        float k = clamp01(t);
        int a = alphaOf(baseColor);
        int r = (baseColor >>> 16) & 0xFF;
        int g = (baseColor >>> 8) & 0xFF;
        int b = baseColor & 0xFF;
        int tr = (targetRgb >>> 16) & 0xFF;
        int tg = (targetRgb >>> 8) & 0xFF;
        int tb = targetRgb & 0xFF;
        int nr = Math.round(r * (1.0f - k) + tr * k);
        int ng = Math.round(g * (1.0f - k) + tg * k);
        int nb = Math.round(b * (1.0f - k) + tb * k);
        return (a << 24) | (nr << 16) | (ng << 8) | nb;
    }

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }
}
