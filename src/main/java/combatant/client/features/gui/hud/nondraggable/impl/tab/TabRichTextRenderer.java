/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl.tab;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

final class TabRichTextRenderer {
    private static final RenderColor TMP = new RenderColor(255, 255, 255, 255);
    private static final int CACHE_LIMIT = 4096;
    private static final WeakHashMap<Component, List<Segment>> SEGMENT_CACHE = new WeakHashMap<>();
    private static final Map<TextWidthKey, Float> WIDTH_CACHE = new HashMap<>();
    private static final Map<TextHeightKey, Float> HEIGHT_CACHE = new HashMap<>();
    private static final Map<FitKey, String> FIT_CACHE = new HashMap<>();
    private TabRichTextRenderer() {
    }

    static void draw(Renderer2D renderer,
                     Component component,
                     float x,
                     float y,
                     float maxWidth,
                     float size,
                     int fallbackColor,
                     float alpha) {
        if (component == null || maxWidth <= 0f || alpha <= 0f) return;
        float cursor = x;
        float remaining = maxWidth;
        for (Segment segment : flatten(component)) {
            if (remaining <= 1f) break;
            TextRenderer font = font(segment.style());
            int color = color(segment.style(), fallbackColor, alpha);
            String text = fit(font, segment.text(), size, remaining);
            drawString(renderer, font, text, cursor, y, size, color, true);
            float width = width(font, text, size);
            cursor += width;
            remaining -= width;
            if (text.length() < segment.text().length()) break;
        }
    }

    static float width(Component component, float size) {
        float out = 0f;
        for (Segment segment : flatten(component)) {
            out += width(font(segment.style()), segment.text(), size);
        }
        return out;
    }

    static float widthPlain(String text, float size) {
        return width(font(Style.EMPTY), text, size);
    }

    static void drawPlain(Renderer2D renderer,
                          String text,
                          float x,
                          float y,
                          float maxWidth,
                          float size,
                          int fallbackColor,
                          float alpha) {
        if (text == null || text.isEmpty() || maxWidth <= 0f || alpha <= 0f) return;
        TextRenderer font = font(Style.EMPTY);
        int color = color(Style.EMPTY, fallbackColor, alpha);
        String fitted = fit(font, text, size, maxWidth);
        drawString(renderer, font, fitted, x, y, size, color, true);
    }

    static float height(float size) {
        return height(font(Style.EMPTY), size);
    }

    private static List<Segment> flatten(Component text) {
        if (text == null) return List.of();
        List<Segment> cached = SEGMENT_CACHE.get(text);
        if (cached != null) return cached;

        List<Segment> segments = new ArrayList<>();
        text.visit((style, string) -> {
            if (string != null && !string.isEmpty()) {
                segments.add(new Segment(string, style == null ? Style.EMPTY : style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        List<Segment> immutable = List.copyOf(segments);
        if (SEGMENT_CACHE.size() > CACHE_LIMIT) {
            SEGMENT_CACHE.clear();
        }
        SEGMENT_CACHE.put(text, immutable);
        return immutable;
    }

    private static TextRenderer font(Style style) {
        if (style != null && style.isBold()) {
            return Fonts.renderer("Onest", FontInfo.Type.Bold, regular());
        }
        return Fonts.renderer("OnestMedium", FontInfo.Type.Regular, regular());
    }

    private static TextRenderer regular() {
        return Fonts.renderer("Onest", FontInfo.Type.Regular, TextRenderer.get());
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
        if (maxWidth <= 1f) return "";

        FitKey key = new FitKey(fontId(font), text, sizeKey(size), Math.max(1, Math.round(maxWidth * 2f)));
        String cached = FIT_CACHE.get(key);
        if (cached != null) return cached;

        String out;
        if (width(font, text, size) <= maxWidth) {
            out = text;
        } else {
            String suffix = "...";
            float suffixW = width(font, suffix, size);
            if (suffixW >= maxWidth) {
                out = suffix;
            } else {
                int low = 0;
                int high = text.length();
                while (low < high) {
                    int mid = (low + high + 1) >>> 1;
                    int safeMid = safeCharBoundary(text, mid);
                    if (safeMid <= low && mid > low) safeMid = mid;
                    float w = width(font, text.substring(0, safeMid), size) + suffixW;
                    if (w <= maxWidth) {
                        low = safeMid;
                    } else {
                        high = Math.max(0, safeMid - 1);
                    }
                }
                int end = safeCharBoundary(text, low);
                out = end <= 0 ? suffix : text.substring(0, end) + suffix;
            }
        }

        putBounded(FIT_CACHE, key, out);
        return out;
    }

    private static float width(TextRenderer font, String text, float size) {
        if (font == null || text == null || text.isEmpty()) return 0f;
        TextWidthKey key = new TextWidthKey(fontId(font), text, sizeKey(size));
        Float cached = WIDTH_CACHE.get(key);
        if (cached != null) return cached;

        float scale = size / 18f;
        float svgAdvance = svgGlyphSize(font, size);
        float width = 0f;
        TextRenderer runFont = null;
        int runStart = 0;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int nextIndex = i + Character.charCount(cp);
            if (TextGlyphFallback.shouldUseVanillaSvg(font, cp)) {
                width += measureRun(runFont, text, runStart, i, scale);
                runFont = null;
                runStart = nextIndex;
                width += svgAdvance;
                i = nextIndex;
                continue;
            }

            TextRenderer nextFont = TextGlyphFallback.rendererForGlyph(font, cp);
            if (runFont == null) {
                runFont = nextFont;
                runStart = i;
            } else if (nextFont != runFont) {
                width += measureRun(runFont, text, runStart, i, scale);
                runFont = nextFont;
                runStart = i;
            }
            i = nextIndex;
        }
        width += measureRun(runFont, text, runStart, text.length(), scale);

        putBounded(WIDTH_CACHE, key, width);
        return width;
    }

    private static float measureRun(TextRenderer font, String text, int start, int end, float scale) {
        if (font == null || text == null || end <= start) return 0f;
        font.begin(scale, true, false);
        try {
            return (float) font.getWidth(text.substring(start, end), false);
        } finally {
            font.end();
        }
    }

    private static float height(TextRenderer font, float size) {
        if (font == null) return size;
        TextHeightKey key = new TextHeightKey(fontId(font), sizeKey(size));
        Float cached = HEIGHT_CACHE.get(key);
        if (cached != null) return cached;

        float scale = size / 18f;
        font.begin(scale, true, false);
        float out;
        try {
            out = (float) font.getHeight(false);
        } finally {
            font.end();
        }
        putBounded(HEIGHT_CACHE, key, out);
        return out;
    }

    private static void drawString(Renderer2D renderer, TextRenderer font, String text, float x, float y, float size, int argb, boolean shadow) {
        if (font == null || text == null || text.isEmpty() || ((argb >>> 24) & 0xFF) <= 0) return;
        float scale = size / 18f;
        float svgSize = svgGlyphSize(font, size);
        float svgY = y + (height(font, size) - svgSize) * 0.5f;
        TMP.a = (argb >>> 24) & 0xFF;
        TMP.r = (argb >>> 16) & 0xFF;
        TMP.g = (argb >>> 8) & 0xFF;
        TMP.b = argb & 0xFF;

        TextRenderer runFont = null;
        int runStart = 0;
        float cursorX = x;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int nextIndex = i + Character.charCount(cp);
            if (TextGlyphFallback.shouldUseVanillaSvg(font, cp)) {
                cursorX = drawRun(runFont, text, runStart, i, cursorX, y, scale, shadow);
                runFont = null;
                runStart = nextIndex;
                String svgName = TextGlyphFallback.vanillaSvgName(cp);
                if (renderer != null && svgName != null) {
                    renderer.svg(svgName, cursorX, svgY, svgSize, svgSize,
                            SvgRenderOptions.fromFile().withAlpha(((argb >>> 24) & 0xFF) / 255f));
                }
                cursorX += svgSize;
                i = nextIndex;
                continue;
            }

            TextRenderer nextFont = TextGlyphFallback.rendererForGlyph(font, cp);
            if (runFont == null) {
                runFont = nextFont;
                runStart = i;
            } else if (nextFont != runFont) {
                cursorX = drawRun(runFont, text, runStart, i, cursorX, y, scale, shadow);
                runFont = nextFont;
                runStart = i;
            }
            i = nextIndex;
        }
        drawRun(runFont, text, runStart, text.length(), cursorX, y, scale, shadow);

    }

    private static float drawRun(TextRenderer font, String text, int start, int end, float x, float y, float scale, boolean shadow) {
        if (font == null || text == null || end <= start) return x;
        String run = text.substring(start, end);
        font.begin(scale, false, false);
        try {
            return (float) font.render(run, x, y, TMP, shadow);
        } finally {
            font.end();
        }
    }

    private static float svgGlyphSize(TextRenderer font, float size) {
        return Math.max(1f, height(font, size)) * 0.92f;
    }

    private static int fontId(TextRenderer font) {
        return System.identityHashCode(font);
    }

    private static int sizeKey(float size) {
        return Math.round(size * 100.0f);
    }

    private static int safeCharBoundary(String text, int index) {
        int out = Math.max(0, Math.min(text.length(), index));
        if (out > 0 && out < text.length() && Character.isLowSurrogate(text.charAt(out))) {
            out--;
        }
        return out;
    }

    private static <K, V> void putBounded(Map<K, V> cache, K key, V value) {
        if (cache.size() > CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(key, value);
    }

    private record TextWidthKey(int fontId, String text, int sizeKey) {
    }

    private record TextHeightKey(int fontId, int sizeKey) {
    }

    private record FitKey(int fontId, String text, int sizeKey, int widthKey) {
    }

    private record Segment(String text, Style style) {
    }
}
