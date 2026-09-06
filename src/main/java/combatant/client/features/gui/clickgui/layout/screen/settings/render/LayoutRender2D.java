/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.render;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;

public enum LayoutRender2D {
    ;
    private static final float SOFTNESS = 0.4f; //do not change

    public static int argb(int a, int r, int g, int b) {
        return ((clamp(a) & 0xFF) << 24)
                | ((clamp(r) & 0xFF) << 16)
                | ((clamp(g) & 0xFF) << 8)
                | (clamp(b) & 0xFF);
    }

    public static int alpha(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int na = Math.round(a * Math.max(0f, Math.min(1f, factor)));
        return (color & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    public static void rect(float x, float y, float w, float h, int color) {
        if (Renderer2D.COLOR == null) return;
        int c = applyGuiAlpha(color);
        if (((c >>> 24) & 0xFF) <= 0) return;
        Renderer2D.COLOR.quad(x, y, w, h, c);
    }

    public static void rectQuad(float x, float y, float w, float h, int cTl, int cTr, int cBr, int cBl) {
        if (Renderer2D.COLOR == null) return;
        int tl = applyGuiAlpha(cTl);
        int tr = applyGuiAlpha(cTr);
        int br = applyGuiAlpha(cBr);
        int bl = applyGuiAlpha(cBl);
        if (((tl >>> 24) & 0xFF) <= 0
                && ((tr >>> 24) & 0xFF) <= 0
                && ((br >>> 24) & 0xFF) <= 0
                && ((bl >>> 24) & 0xFF) <= 0) return;
        Renderer2D.COLOR.quad(x, y, w, h, tl, tr, br, bl);
    }

    /**
     * Draw a horizontally-oriented subpixel divider with deterministic framebuffer coverage.
     * <p>
     * A regular quad thinner than one framebuffer pixel is sampled as ordinary geometry and
     * can therefore alternate between fully visible and fully missed while its Y coordinate
     * moves through fractional pixels (for example during smooth scrolling). This helper
     * resolves the requested logical interval into framebuffer-aligned one-pixel rows and
     * scales each row alpha by its exact vertical coverage. The result keeps the authored
     * optical weight while moving smoothly instead of popping/collapsing.
     */
    public static void horizontalHairline(float x,
                                          float y,
                                          float w,
                                          float thickness,
                                          int leftColor,
                                          int rightColor) {
        if (Renderer2D.COLOR == null || w <= 0f || thickness <= 0f) return;

        float deviceScaleY = currentDeviceScaleY();
        double topPx = y * (double) deviceScaleY;
        double bottomPx = (y + thickness) * (double) deviceScaleY;
        if (!(bottomPx > topPx)) return;

        int firstRow = (int) Math.floor(topPx);
        int lastRow = (int) Math.ceil(bottomPx) - 1;

        // Hairlines are expected to touch only a handful of rows. Avoid exploding geometry
        // if this API is accidentally used for a large filled region.
        if (lastRow - firstRow > 8) {
            rectQuad(x, y, w, thickness, leftColor, rightColor, rightColor, leftColor);
            return;
        }

        float logicalPixelH = 1f / deviceScaleY;
        for (int row = firstRow; row <= lastRow; row++) {
            double overlapPx = Math.min(bottomPx, row + 1.0) - Math.max(topPx, row);
            if (overlapPx <= 1.0e-6) continue;

            float coverage = (float) Math.max(0.0, Math.min(1.0, overlapPx));
            float rowY = row * logicalPixelH;
            int left = alpha(leftColor, coverage);
            int right = alpha(rightColor, coverage);
            rectQuad(x, rowY, w, logicalPixelH, left, right, right, left);
        }
    }

    private static float currentDeviceScaleY() {
        ViewportContext viewport = ViewportContext.current();
        if (viewport == null || viewport.framebufferHeight() <= 0 || viewport.height() <= 0f) {
            return 1f;
        }
        float scale = viewport.framebufferHeight() / viewport.height();
        return Float.isFinite(scale) && scale > 1.0e-4f ? scale : 1f;
    }

    public static void rounded(float x, float y, float w, float h, float radius, int color) {
        roundedQuad(x, y, w, h, radius, color, color, color, color);
    }

    public static void roundedQuad(float x, float y, float w, float h, float radius, int cTl, int cTr, int cBr, int cBl) {
        if (Renderer2D.COLOR == null) return;
        int tl = applyGuiAlpha(cTl);
        int tr = applyGuiAlpha(cTr);
        int br = applyGuiAlpha(cBr);
        int bl = applyGuiAlpha(cBl);
        if (((tl >>> 24) & 0xFF) <= 0
                && ((tr >>> 24) & 0xFF) <= 0
                && ((br >>> 24) & 0xFF) <= 0
                && ((bl >>> 24) & 0xFF) <= 0) return;
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, radius, SOFTNESS, tl, tr, br, bl);
    }

    public static void roundedStroke(float x, float y, float w, float h, float radius, float thickness, int color) {
        roundedStrokeQuad(x, y, w, h, radius, thickness, color, color, color, color);
    }

    public static void roundedStrokeQuad(float x,
                                         float y,
                                         float w,
                                         float h,
                                         float radius,
                                         float thickness,
                                         int cTl,
                                         int cTr,
                                         int cBr,
                                         int cBl) {
        if (Renderer2D.COLOR == null) return;
        int tl = applyGuiAlpha(cTl);
        int tr = applyGuiAlpha(cTr);
        int br = applyGuiAlpha(cBr);
        int bl = applyGuiAlpha(cBl);
        if (((tl >>> 24) & 0xFF) <= 0
                && ((tr >>> 24) & 0xFF) <= 0
                && ((br >>> 24) & 0xFF) <= 0
                && ((bl >>> 24) & 0xFF) <= 0) return;
        Renderer2D.COLOR.roundedRectStrokeGradientQuad(x, y, w, h, radius, SOFTNESS, thickness, tl, tr, br, bl);
    }

    public static void roundedSoftShadow(float x,
                                         float y,
                                         float w,
                                         float h,
                                         float radius,
                                         float blur,
                                         float innerAlpha,
                                         int color) {
        if (Renderer2D.COLOR == null) return;
        int c = applyGuiAlpha(color);
        if (((c >>> 24) & 0xFF) <= 0) return;
        Renderer2D.COLOR.roundedRectSoftShadow(x, y, w, h, radius, blur, innerAlpha, c);
    }

    private static int applyGuiAlpha(int color) {
        float factor = Math.max(0f, Math.min(1f, ClickGuiRenderer.getAnimAlpha()));
        int a = (color >>> 24) & 0xFF;
        int na = Math.round(a * factor);
        return (color & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    private static int clamp(int c) {
        if (c < 0) return 0;
        return Math.min(c, 255);
    }
}
