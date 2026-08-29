/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.text;

import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgInlineText;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.ScissorFunction;

public interface TextRenderer {
    static TextRenderer get() {
        return Fonts.defaultRenderer();
    }

    private static double fadeAlphaAt(double x,
                                      double clipLeft,
                                      double clipRight,
                                      double fadeLeft,
                                      double fadeRight) {
        if (clipRight <= clipLeft) return 1.0;
        if (x <= clipLeft || x >= clipRight) return 0.0;

        double alpha = 1.0;
        double leftFade = Math.max(0.0, fadeLeft);
        double rightFade = Math.max(0.0, fadeRight);
        if (leftFade > 0.0 && x < clipLeft + leftFade) {
            alpha = Math.min(alpha, (x - clipLeft) / leftFade);
        }
        if (rightFade > 0.0 && x > clipRight - rightFade) {
            alpha = Math.min(alpha, (clipRight - x) / rightFade);
        }
        return Math.max(0.0, Math.min(1.0, alpha));
    }

    private static int scaleArgbAlpha(int argb, double factor) {
        int a = (argb >>> 24) & 0xFF;
        int scaled = (int) Math.round(a * Math.max(0.0, Math.min(1.0, factor)));
        return (argb & 0x00FFFFFF) | ((scaled & 0xFF) << 24);
    }

    private static void applyArgb(RenderColor color, int argb) {
        color.a = (argb >>> 24) & 0xFF;
        color.r = (argb >>> 16) & 0xFF;
        color.g = (argb >>> 8) & 0xFF;
        color.b = argb & 0xFF;
    }

    void setAlpha(double a);

    void begin(double scale, boolean scaleOnly, boolean big);

    default void begin(double scale) {
        begin(scale, false, false);
    }

    default void begin() {
        begin(1, false, false);
    }

    default void beginBig() {
        begin(1, false, true);
    }

    double getWidth(String text, int length, boolean shadow);

    default double getWidth(String text, boolean shadow) {
        return getWidth(text, text.length(), shadow);
    }

    default double getWidth(String text) {
        return getWidth(text, text.length(), false);
    }

    default double getWidthInlineSvg(String text, boolean shadow) {
        return SvgInlineText.width(this, text, shadow);
    }

    default double getWidthInlineSvg(String text, boolean shadow, double iconScale) {
        return SvgInlineText.width(this, text, shadow, iconScale);
    }

    double getHeight(boolean shadow);

    default double getHeight() {
        return getHeight(false);
    }

    default boolean hasGlyph(int codePoint) {
        return true;
    }

    double render(String text, double x, double y, RenderColor color, boolean shadow);

    default double render(String text, double x, double y, RenderColor color) {
        return render(text, x, y, color, false);
    }

    default double renderGradient(String text, double x, double y, Font.GlyphGradient gradient, boolean shadow) {
        if (text == null || text.isEmpty() || gradient == null) return x;
        boolean wasBuilding = isBuilding();
        if (!wasBuilding) begin();

        double cursorX = x;
        int glyphIndex = 0;
        int[] colors = new int[2];
        RenderColor color = new RenderColor(0xFFFFFFFF);
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                String glyph = new String(Character.toChars(cp));
                gradient.colors(glyphIndex, cp, cursorX, colors);
                applyArgb(color, colors[0]);
                cursorX = render(glyph, cursorX, y, color, shadow);
                i += Character.charCount(cp);
                glyphIndex++;
            }
            return cursorX;
        } finally {
            if (!wasBuilding) end();
        }
    }

    default double renderQuadGradient(String text, double x, double y, Font.GlyphQuadGradient gradient, boolean shadow) {
        if (text == null || text.isEmpty() || gradient == null) return x;
        boolean wasBuilding = isBuilding();
        if (!wasBuilding) begin();

        double cursorX = x;
        int glyphIndex = 0;
        int[] colors = new int[4];
        RenderColor color = new RenderColor(0xFFFFFFFF);
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                String glyph = new String(Character.toChars(cp));
                double glyphW = getWidth(glyph, shadow);
                gradient.colors(glyphIndex, cp, cursorX, y, cursorX + glyphW, y + getHeight(shadow), colors);
                applyArgb(color, colors[0]);
                cursorX = render(glyph, cursorX, y, color, shadow);
                i += Character.charCount(cp);
                glyphIndex++;
            }
            return cursorX;
        } finally {
            if (!wasBuilding) end();
        }
    }

    default double renderHorizontalFadeClipped(String text,
                                               double x,
                                               double y,
                                               RenderColor color,
                                               double clipLeft,
                                               double clipRight,
                                               double fadeLeft,
                                               double fadeRight,
                                               boolean shadow) {
        if (text == null || text.isEmpty() || color == null) return x;
        boolean wasBuilding = isBuilding();
        if (!wasBuilding) begin();

        boolean clipped = false;
        try {
            double clipWidth = Math.max(0.0, clipRight - clipLeft);
            double clipHeight = Math.max(0.0, getHeight(shadow));
            clipped = ScissorFunction.pushRaw(
                    (float) clipLeft,
                    (float) y,
                    (float) clipWidth,
                    (float) clipHeight
            );
            int argb = ((color.a & 0xFF) << 24)
                    | ((color.r & 0xFF) << 16)
                    | ((color.g & 0xFF) << 8)
                    | (color.b & 0xFF);
            return renderGradient(text, x, y, (idx, cp, glyphX, out) -> {
                double glyphAdvance = getWidth(new String(Character.toChars(cp)), false);
                double leftAlpha = fadeAlphaAt(glyphX, clipLeft, clipRight, fadeLeft, fadeRight);
                double rightAlpha = fadeAlphaAt(glyphX + glyphAdvance, clipLeft, clipRight, fadeLeft, fadeRight);
                out[0] = scaleArgbAlpha(argb, leftAlpha);
                out[1] = scaleArgbAlpha(argb, rightAlpha);
            }, shadow);
        } finally {
            if (clipped) ScissorFunction.pop();
            if (!wasBuilding) end();
        }
    }

    default double renderInlineSvg(Renderer2D renderer,
                                   String text,
                                   double x,
                                   double y,
                                   RenderColor color,
                                   boolean shadow) {
        return SvgInlineText.render(renderer, this, text, x, y, color, shadow);
    }

    default double renderInlineSvg(Renderer2D renderer,
                                   String text,
                                   double x,
                                   double y,
                                   RenderColor color,
                                   boolean shadow,
                                   SvgRenderOptions svgOptions,
                                   double iconScale,
                                   double iconYOffsetPx) {
        return SvgInlineText.render(renderer, this, text, x, y, color, shadow, svgOptions, iconScale, iconYOffsetPx);
    }

    boolean isBuilding();

    void end();
}
