/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud;


import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;

/**
 * Shared helpers for HUD widget visuals.
 */
public enum HudRenderUtil {
    ;


    public static final int GLASS_BG = 0x660C0C0C;
    public static final float GLASS_PANEL_FILL_ALPHA = 0.165f;
    public static final float GLASS_SMALL_FILL_ALPHA = 0.212f;

    public static int glassBackground() {
        return GLASS_BG;
    }

    public static int glassBackground(float alphaFactor) {
        return scaleAlpha(GLASS_BG, alphaFactor);
    }

    public static int glassBackgroundRgb() {
        return GLASS_BG & 0x00FFFFFF;
    }

    public static int glassPanelBackground(float alphaFactor) {
        return scaleAlpha(GLASS_BG, alphaFactor * GLASS_PANEL_FILL_ALPHA);
    }

    public static int glassSmallBackground(float alphaFactor) {
        return scaleAlpha(GLASS_BG, alphaFactor * GLASS_SMALL_FILL_ALPHA);
    }

    public static void drawLiquidGlass(float x,
                                       float y,
                                       float w,
                                       float h,
                                       float radius,
                                       float scale,
                                       boolean large,
                                       float alpha) {
        drawLiquidGlass(x, y, w, h, radius, scale, large, alpha, alpha);
    }

    public static void drawLiquidGlass(float x,
                                       float y,
                                       float w,
                                       float h,
                                       float radius,
                                       float scale,
                                       boolean large,
                                       float blurAlpha,
                                       float glassAlpha) {
        if (blurAlpha <= 0.001f && glassAlpha <= 0.001f) return;

        float blurStrength = AnimationUtility.clamp(blurAlpha, 0.0f, 1.0f);
        float materialAlpha = AnimationUtility.clamp(glassAlpha, 0.0f, 1.0f);
        float safeScale = Math.max(0.001f, scale);

        float fresnelPower = large ? -22.0f : -18.0f;
        float thickness = (large ? 13.0f : 11.0f) * safeScale;
        float baseAlpha = large ? 0.74f : 0.82f;
        float fresnelMix = large ? 0.34f : 0.38f;
        float distort = large ? 0.155f : 0.135f;

        Renderer2D.COLOR.liquidGlassRect(
                x,
                y,
                w,
                h,
                radius,
                thickness,
                0xFFFFFFFF,
                materialAlpha,
                blurStrength,
                fresnelPower,
                1.0f,
                baseAlpha,
                fresnelMix,
                distort * materialAlpha,
                0.0f
        );
    }


    public static void drawLiquidGlassCorners(float x,
                                              float y,
                                              float w,
                                              float h,
                                              float radiusTL,
                                              float radiusTR,
                                              float radiusBR,
                                              float radiusBL,
                                              float scale,
                                              boolean large,
                                              float alpha) {
        drawLiquidGlassCorners(
                x,
                y,
                w,
                h,
                radiusTL,
                radiusTR,
                radiusBR,
                radiusBL,
                scale,
                large,
                alpha,
                0xFFFFFFFF
        );
    }

    public static void drawLiquidGlassCorners(float x,
                                              float y,
                                              float w,
                                              float h,
                                              float radiusTL,
                                              float radiusTR,
                                              float radiusBR,
                                              float radiusBL,
                                              float scale,
                                              boolean large,
                                              float alpha,
                                              int tintArgb) {
        drawLiquidGlassCorners(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL, scale, large, alpha, alpha, tintArgb);
    }

    public static void drawLiquidGlassCorners(float x,
                                              float y,
                                              float w,
                                              float h,
                                              float radiusTL,
                                              float radiusTR,
                                              float radiusBR,
                                              float radiusBL,
                                              float scale,
                                              boolean large,
                                              float blurAlpha,
                                              float glassAlpha,
                                              int tintArgb) {
        if (blurAlpha <= 0.001f && glassAlpha <= 0.001f) return;

        float blurStrength = AnimationUtility.clamp(blurAlpha, 0.0f, 1.0f);
        float materialAlpha = AnimationUtility.clamp(glassAlpha, 0.0f, 1.0f);
        float safeScale = Math.max(0.001f, scale);

        float fresnelPower = large ? -22.0f : -18.0f;
        float thickness = (large ? 13.0f : 11.0f) * safeScale;
        float baseAlpha = large ? 0.74f : 0.82f;
        float fresnelMix = large ? 0.34f : 0.38f;
        float distort = large ? 0.155f : 0.135f;

        Renderer2D.COLOR.liquidGlassRectCorners(
                x,
                y,
                w,
                h,
                radiusTL,
                radiusTR,
                radiusBR,
                radiusBL,
                thickness,
                tintArgb,
                materialAlpha,
                blurStrength,
                fresnelPower,
                1.0f,
                baseAlpha,
                fresnelMix,
                distort * materialAlpha,
                0.0f
        );
    }

    public static final String PANEL_STYLE_DEFAULT = "Default";
    public static final String PANEL_STYLE_ACCENT = "Accent";
    public static final String PANEL_STYLE_GRADIENT = "Gradient";
    public static final String SHADOW_MODE_BLACK = "Black";
    public static final String SHADOW_MODE_THEME = "Theme";

    public record ThemeGradient(int start, int end, float angleDeg) {
    }

    /**
     * Returns a theme-driven accent gradient suitable for HUD outlines/glows.
     * Theme stroke-gradient colors are preferred when defined; otherwise the regular
     * accent/accent-soft pair is used. The returned gradient is normalized top -> bottom.
     */
    public static ThemeGradient themeAccentGradient(int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        Themes.ThemeEntry entry = Theme.currentEntry();
        Themes.GradientSpec strokeGradient = entry != null ? entry.strokeGradient() : null;
        if (strokeGradient != null && strokeGradient.enabled()) {
            return new ThemeGradient(
                    setAlpha(strokeGradient.start(), a),
                    setAlpha(strokeGradient.end(), a),
                    90.0f
            );
        }

        Themes.Theme current = Theme.theme();
        int start = current != null ? current.accent() : 0xFF5CC8E7;
        int end = current != null ? current.accentSoft() : 0x805CC8E7;
        return new ThemeGradient(setAlpha(start, a), setAlpha(end, a), 90.0f);
    }

    /** Returns the actual theme surface gradient, falling back to its accent gradient. */
    public static ThemeGradient themePanelGradient(int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        Themes.ThemeEntry entry = Theme.currentEntry();
        Themes.GradientSpec windowGradient = entry != null ? entry.windowGradient() : null;
        if (windowGradient != null && windowGradient.enabled()) {
            return new ThemeGradient(
                    setAlpha(windowGradient.start(), a),
                    setAlpha(windowGradient.end(), a),
                    windowGradient.angleDeg()
            );
        }
        return themeAccentGradient(a);
    }

    /** Mixes a neutral HUD surface with one endpoint of the theme's real gradient. */
    public static int gradientSurface(int surface, int themeColor, float strength) {
        float amount = AnimationUtility.clamp(strength, 0.0f, 1.0f);
        int alpha = (surface >>> 24) & 0xFF;
        return setAlpha(mixColor(surface, setAlpha(themeColor, alpha), amount), alpha);
    }

    /** Mixes a panel surface towards the active theme accent without changing its alpha. */
    public static int accentSurface(int color, float strength) {
        Themes.Theme current = Theme.theme();
        if (current == null) return color;
        int alpha = (color >>> 24) & 0xFF;
        return setAlpha(mixColor(color, current.accent(), strength), alpha);
    }

    /**
     * Draws an optional HUD stroke. Theme-gradient mode is explicitly vertical: top -> bottom.
     */
    public static void drawHudStroke(Renderer2D renderer,
                                     float x, float y, float w, float h,
                                     float radius, float softness, float thickness,
                                     int solidColor, boolean themeGradient,
                                     int alpha, float alphaFactor) {
        if (renderer == null || w <= 0.0f || h <= 0.0f || thickness <= 0.0f) return;
        int resolvedAlpha = Math.round(Math.max(0, Math.min(255, alpha))
                * AnimationUtility.clamp(alphaFactor, 0.0f, 1.0f));
        if (resolvedAlpha <= 0) return;

        if (themeGradient) {
            ThemeGradient gradient = themeAccentGradient(resolvedAlpha);
            renderer.roundedRectStrokeGradient(
                    x, y, w, h, radius, softness, thickness,
                    gradient.start(), gradient.end(), gradient.angleDeg()
            );
            return;
        }

        renderer.roundedRectStroke(
                x, y, w, h, radius, softness, thickness,
                setAlpha(solidColor, resolvedAlpha)
        );
    }

    /**
     * Draws an optional HUD soft shadow. Theme mode inherits the theme's accent/stroke
     * gradient exactly (including its angle) when that gradient is enabled. Themes without
     * an accent gradient fall back to a solid accent-colored shadow.
     */
    public static void drawHudShadow(Renderer2D renderer,
                                     float x, float y, float w, float h,
                                     float radius, float scale,
                                     boolean themeColored, int alpha, float alphaFactor) {
        drawHudShadow(renderer, x, y, w, h, radius, scale,
                themeColored, alpha, alphaFactor, 1.0f);
    }

    public static void drawHudShadow(Renderer2D renderer,
                                     float x, float y, float w, float h,
                                     float radius, float scale,
                                     boolean themeColored, int alpha, float alphaFactor,
                                     float themeStrength) {
        if (renderer == null || w <= 0.0f || h <= 0.0f) return;
        int resolvedAlpha = Math.round(Math.max(0, Math.min(255, alpha))
                * AnimationUtility.clamp(alphaFactor, 0.0f, 1.0f));
        if (resolvedAlpha <= 0) return;

        float safeScale = Math.max(0.001f, scale);
        float blur = 6.0f * safeScale;
        float innerAlpha = 0.055f;
        if (!themeColored) {
            renderer.roundedRectSoftShadow(
                    x, y, w, h, radius, blur, innerAlpha, setAlpha(0xFF000000, resolvedAlpha)
            );
            return;
        }

        Themes.ThemeEntry entry = Theme.currentEntry();
        Themes.GradientSpec gradient = entry != null ? entry.strokeGradient() : null;
        float colorStrength = AnimationUtility.clamp(themeStrength, 0.0f, 1.0f);
        int black = setAlpha(0xFF000000, resolvedAlpha);
        if (gradient != null && gradient.enabled()) {
            renderer.roundedRectSoftShadowGradient(
                    x, y, w, h, radius, blur, innerAlpha,
                    setAlpha(mixColor(black, gradient.start(), colorStrength), resolvedAlpha),
                    setAlpha(mixColor(black, gradient.end(), colorStrength), resolvedAlpha),
                    gradient.angleDeg()
            );
            return;
        }

        Themes.Theme current = Theme.theme();
        int accent = current != null ? current.accent() : 0xFF5CC8E7;
        renderer.roundedRectSoftShadow(
                x, y, w, h, radius, blur, innerAlpha,
                setAlpha(mixColor(black, accent, colorStrength), resolvedAlpha)
        );
    }

    public static void drawHudBackground(Renderer2D renderer,
                                         float x, float y,
                                         float w, float h,
                                         float radius, float softness,
                                         int solidColor,
                                         boolean useGradient) {
        if (renderer == null) return;
        if (useGradient) {
            int alpha = (solidColor >>> 24) & 0xFF;
            Themes.ThemeEntry entry = Theme.currentEntry();
            Themes.GradientSpec gradient = entry != null ? entry.windowGradient() : null;
            if (gradient != null && gradient.enabled()) {
                int start = setAlpha(gradient.start(), alpha);
                int end = setAlpha(gradient.end(), alpha);
                renderer.roundedRectGradient(x, y, w, h, radius, softness, start, end, gradient.angleDeg());
            } else {
                int start = mixRgb(solidColor, 0xFFFFFF, 0.06f);
                int end = mixRgb(solidColor, 0x000000, 0.08f);
                renderer.roundedRectGradient(x, y, w, h, radius, softness, start, end, 90f);
            }
        } else {
            renderer.roundedRect(x, y, w, h, radius, softness, solidColor);
        }
    }

    public static int setAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int scaleAlpha(int color, float factor) {
        factor = Math.max(0f, Math.min(1f, factor));
        int a = (color >>> 24) & 0xFF;
        int na = (int) (a * factor);
        return (color & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    public static int mixColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
        int r = (int) (((from >>> 16) & 0xFF) * (1 - t) + ((to >>> 16) & 0xFF) * t);
        int g = (int) (((from >>> 8) & 0xFF) * (1 - t) + ((to >>> 8) & 0xFF) * t);
        int b = (int) (((from) & 0xFF) * (1 - t) + ((to) & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int mixRgb(int baseColor, int targetRgb, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (baseColor >>> 24) & 0xFF;
        int r = (baseColor >>> 16) & 0xFF;
        int g = (baseColor >>> 8) & 0xFF;
        int b = baseColor & 0xFF;
        int tr = (targetRgb >>> 16) & 0xFF;
        int tg = (targetRgb >>> 8) & 0xFF;
        int tb = targetRgb & 0xFF;
        int nr = (int) (r * (1 - t) + tr * t);
        int ng = (int) (g * (1 - t) + tg * t);
        int nb = (int) (b * (1 - t) + tb * t);
        return (a << 24) | (nr << 16) | (ng << 8) | nb;
    }

    public static float animateVisibility(float current, boolean visible) {
        float target = visible ? 1.0f : 0.0f;
        float duration = visible ? 0.30f : 0.20f;
        float step = duration <= 0.0f ? 1.0f : AnimationUtility.clamp01(AnimationUtility.deltaTime() / duration);
        if (current < target) {
            current = Math.min(target, current + step);
        } else {
            current = Math.max(target, current - step);
        }
        return AnimationUtility.snap(current, target, 0.001f);
    }

    public static float visibilityScale(float progress) {
        return AnimationUtility.easeOutBack(progress, 1.0f);
    }

    public static float animateDimension(float current, float target) {
        if (current < 0.0f) return target;
        float progress = AnimationUtility.clamp01(AnimationUtility.deltaTime() / 0.10f);
        float eased = 1.0f - (float) Math.pow(1.0f - progress, 4.0f);
        float next = AnimationUtility.lerp(current, target, eased);
        return AnimationUtility.snap(next, target, 0.05f);
    }
}
