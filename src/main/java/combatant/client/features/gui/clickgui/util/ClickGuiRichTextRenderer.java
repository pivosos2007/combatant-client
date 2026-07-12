/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.util;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextGlyphFallback;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ClickGuiRichTextRenderer {
    private static final RenderColor TMP = new RenderColor(255, 255, 255, 255);

    private ClickGuiRichTextRenderer() {
    }

    public static void draw(Component component,
                            float x,
                            float y,
                            float maxWidth,
                            float size,
                            int fallbackColor,
                            float alpha,
                            boolean shadow) {
        if (component == null || maxWidth <= 0f || alpha <= 0f) return;
        float cursor = x;
        float remaining = maxWidth;
        for (Segment segment : flatten(component)) {
            if (remaining <= 1f) break;
            TextRenderer font = font(segment.style());
            int color = color(segment.style(), fallbackColor, alpha);
            String text = fit(font, segment.text(), size, remaining);
            drawString(ClickGuiRenderer.currentRenderer(), font, text, cursor, y, size, color, shadow);
            float width = width(font, text, size);
            cursor += width;
            remaining -= width;
            if (text.length() < segment.text().length()) break;
        }
    }

    public static float width(Component component, float size) {
        float out = 0f;
        for (Segment segment : flatten(component)) {
            out += width(font(segment.style()), segment.text(), size);
        }
        return out;
    }

    public static float height(float size) {
        return height(font(Style.EMPTY), size);
    }

    private static List<Segment> flatten(Component text) {
        List<Segment> segments = new ArrayList<>();
        if (text == null) return segments;
        text.visit((style, string) -> {
            if (string != null && !string.isEmpty()) {
                segments.add(new Segment(string, style == null ? Style.EMPTY : style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return segments;
    }

    private static TextRenderer font(Style style) {
        if (style != null && style.isBold()) {
            return Fonts.renderer("InterMedium", FontInfo.Type.Regular, ClickGuiRenderer.getInterMedium());
        }
        if (style != null && style.isItalic()) {
            return Fonts.renderer("Inter", FontInfo.Type.Italic, ClickGuiRenderer.getInterRegular());
        }
        return ClickGuiRenderer.getInterMedium();
    }

    private static int color(Style style, int fallbackColor, float alpha) {
        int rgb = fallbackColor & 0x00FFFFFF;
        TextColor textColor = style != null ? style.getColor() : null;
        if (textColor != null) {
            rgb = textColor.getValue() & 0x00FFFFFF;
        }
        int baseA = (fallbackColor >>> 24) & 0xFF;
        int a = Math.max(0, Math.min(255, Math.round(baseA * alpha)));
        return (a << 24) | rgb;
    }

    private static String fit(TextRenderer font, String text, float size, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (width(font, text, size) <= maxWidth) return text;
        String suffix = "...";
        float suffixW = width(font, suffix, size);
        int end = text.length();
        while (end > 0 && width(font, text.substring(0, end), size) + suffixW > maxWidth) {
            end--;
        }
        return end <= 0 ? suffix : text.substring(0, end) + suffix;
    }

    private static float width(TextRenderer font, String text, float size) {
        if (font == null || text == null || text.isEmpty()) return 0f;
        float scale = ClickGuiRenderer.scaleForSize(size);
        float svgAdvance = svgGlyphSize(font, size);
        TextRenderer current = null;
        float width = 0f;
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                if (TextGlyphFallback.shouldUseVanillaSvg(font, cp)) {
                    if (current != null) {
                        current.end();
                        current = null;
                    }
                    width += svgAdvance;
                    i += Character.charCount(cp);
                    continue;
                }
                String glyph = new String(Character.toChars(cp));
                TextRenderer next = TextGlyphFallback.rendererForGlyph(font, cp);
                if (next != current) {
                    if (current != null) current.end();
                    current = next;
                    current.begin(scale, true, false);
                }
                width += (float) current.getWidth(glyph, false);
                i += Character.charCount(cp);
            }
            return width;
        } finally {
            if (current != null) current.end();
        }
    }

    private static float height(TextRenderer font, float size) {
        if (font == null) return size;
        float scale = ClickGuiRenderer.scaleForSize(size);
        font.begin(scale, true, false);
        try {
            return (float) font.getHeight(false);
        } finally {
            font.end();
        }
    }

    private static void drawString(Renderer2D renderer, TextRenderer font, String text, float x, float y, float size, int argb, boolean shadow) {
        if (font == null || text == null || text.isEmpty() || ((argb >>> 24) & 0xFF) <= 0) return;
        float scale = ClickGuiRenderer.scaleForSize(size);
        float svgSize = svgGlyphSize(font, size);
        float svgY = y + (height(font, size) - svgSize) * 0.5f;
        TMP.a = (argb >>> 24) & 0xFF;
        TMP.r = (argb >>> 16) & 0xFF;
        TMP.g = (argb >>> 8) & 0xFF;
        TMP.b = argb & 0xFF;
        TextRenderer current = null;
        float cursorX = x;
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                if (TextGlyphFallback.shouldUseVanillaSvg(font, cp)) {
                    if (current != null) {
                        current.end();
                        current = null;
                    }
                    String svgName = TextGlyphFallback.vanillaSvgName(cp);
                    if (renderer != null && svgName != null) {
                        renderer.svg(svgName, cursorX, svgY, svgSize, svgSize,
                                SvgRenderOptions.fromFile().withAlpha(((argb >>> 24) & 0xFF) / 255f));
                    }
                    cursorX += svgSize;
                    i += Character.charCount(cp);
                    continue;
                }
                String glyph = new String(Character.toChars(cp));
                TextRenderer next = TextGlyphFallback.rendererForGlyph(font, cp);
                if (next != current) {
                    if (current != null) current.end();
                    current = next;
                    current.begin(scale, false, false);
                }
                cursorX = (float) current.render(glyph, cursorX, y, TMP, shadow);
                i += Character.charCount(cp);
            }
        } finally {
            if (current != null) current.end();
        }
    }

    private static float svgGlyphSize(TextRenderer font, float size) {
        return Math.max(1f, height(font, size)) * 0.92f;
    }

    private record Segment(String text, Style style) {
    }
}
