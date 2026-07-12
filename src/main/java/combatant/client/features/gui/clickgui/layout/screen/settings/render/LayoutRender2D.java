/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.render;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
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
