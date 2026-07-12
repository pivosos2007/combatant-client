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
