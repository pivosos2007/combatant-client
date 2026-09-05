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
import combatant.client.render.helpers.ScissorFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

final class TabRichTextRenderer {
    private static final RenderColor TMP = new RenderColor(255, 255, 255, 255);
    private static final int CACHE_LIMIT = 4096;
    private static final float MARQUEE_SPEED = 22.0f;
    private static final float MARQUEE_GAP = 14.0f;
    private static final float MARQUEE_PAUSE_SEC = 0.8f;
    private static final float MARQUEE_FADE = 5.5f;
    private static final WeakHashMap<Component, List<Segment>> SEGMENT_CACHE = new WeakHashMap<>();
    private static final Map<TextWidthKey, Float> WIDTH_CACHE = new HashMap<>();
    private static final Map<TextHeightKey, Float> HEIGHT_CACHE = new HashMap<>();
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
        float fullWidth = width(component, size);
        if (fullWidth <= maxWidth * 1.01f) {
            drawComponentAt(renderer, component, x, y, size, fallbackColor, alpha);
            return;
        }
        drawComponentMarquee(renderer, component, x, y, maxWidth, fullWidth, size, fallbackColor, alpha);
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
        float fullWidth = width(font, text, size);
        if (fullWidth <= maxWidth * 1.01f) {
            drawString(renderer, font, text, x, y, size, color, true);
            return;
        }
        drawPlainMarquee(renderer, font, text, x, y, maxWidth, fullWidth, size, color);
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

    private static void drawComponentAt(Renderer2D renderer,
                                        Component component,
                                        float x,
                                        float y,
                                        float size,
                                        int fallbackColor,
                                        float alpha) {
        float cursor = x;
        for (Segment segment : flatten(component)) {
            if (segment.text() == null || segment.text().isEmpty()) continue;
            TextRenderer font = font(segment.style());
            int color = color(segment.style(), fallbackColor, alpha);
            drawString(renderer, font, segment.text(), cursor, y, size, color, true);
            cursor += width(font, segment.text(), size);
        }
    }

    private static void drawComponentMarquee(Renderer2D renderer,
                                             Component component,
                                             float x,
                                             float y,
                                             float viewWidth,
                                             float fullWidth,
                                             float size,
                                             int fallbackColor,
                                             float alpha) {
        boolean clipped = ScissorFunction.pushRaw(x, y, viewWidth, Math.max(1.0f, height(size)));
        if (!clipped) return;
        try {
            float offset = marqueeOffset(fullWidth, size);
            float gap = MARQUEE_GAP * marqueeScale(size);
            float cycleDistance = fullWidth + gap;
            float fade = marqueeFade(viewWidth, size);
            drawComponentAtFaded(renderer, component, x + offset, y, size, fallbackColor, alpha,
                    x, x + viewWidth, fade);
            if (cycleDistance > viewWidth * 0.5f) {
                drawComponentAtFaded(renderer, component, x + offset + cycleDistance, y, size, fallbackColor, alpha,
                        x, x + viewWidth, fade);
            }
        } finally {
            ScissorFunction.pop();
        }
    }

    private static void drawPlainMarquee(Renderer2D renderer,
                                         TextRenderer font,
                                         String text,
                                         float x,
                                         float y,
                                         float viewWidth,
                                         float fullWidth,
                                         float size,
                                         int color) {
        boolean clipped = ScissorFunction.pushRaw(x, y, viewWidth, Math.max(1.0f, height(font, size)));
        if (!clipped) return;
        try {
            float offset = marqueeOffset(fullWidth, size);
            float gap = MARQUEE_GAP * marqueeScale(size);
            float cycleDistance = fullWidth + gap;
            float fade = marqueeFade(viewWidth, size);
            drawStringFadeClipped(renderer, font, text, x + offset, y, size, color, true,
                    x, x + viewWidth, fade);
            if (cycleDistance > viewWidth * 0.5f) {
                drawStringFadeClipped(renderer, font, text, x + offset + cycleDistance, y, size, color, true,
                        x, x + viewWidth, fade);
            }
        } finally {
            ScissorFunction.pop();
        }
    }

    private static float marqueeFade(float viewWidth, float size) {
        return Math.min(viewWidth * 0.24f, MARQUEE_FADE * marqueeScale(size));
    }

    private static void drawComponentAtFaded(Renderer2D renderer,
                                             Component component,
                                             float x,
                                             float y,
                                             float size,
                                             int fallbackColor,
                                             float alpha,
                                             float clipLeft,
                                             float clipRight,
                                             float fade) {
        float cursor = x;
        for (Segment segment : flatten(component)) {
            if (segment.text() == null || segment.text().isEmpty()) continue;
            TextRenderer font = font(segment.style());
            int color = color(segment.style(), fallbackColor, alpha);
            drawStringFadeClipped(renderer, font, segment.text(), cursor, y, size, color, true,
                    clipLeft, clipRight, fade);
            cursor += width(font, segment.text(), size);
        }
    }

    private static float marqueeOffset(float fullWidth, float size) {
        float scale = marqueeScale(size);
        float speed = MARQUEE_SPEED * scale;
        float gap = MARQUEE_GAP * scale;
        if (speed <= 0.0f) return 0.0f;
        float cycleDistance = fullWidth + gap;
        float cycleTime = MARQUEE_PAUSE_SEC + cycleDistance / speed;
        if (cycleTime <= 0.0f) return 0.0f;
        float t = (Util.getMillis() / 1000.0f) % cycleTime;
        return t > MARQUEE_PAUSE_SEC ? -(t - MARQUEE_PAUSE_SEC) * speed : 0.0f;
    }

    private static float marqueeScale(float size) {
        return Math.max(0.35f, size / 20.5f);
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

    private static void drawStringFadeClipped(Renderer2D renderer,
                                              TextRenderer font,
                                              String text,
                                              float x,
                                              float y,
                                              float size,
                                              int argb,
                                              boolean shadow,
                                              float clipLeft,
                                              float clipRight,
                                              float fade) {
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
                cursorX = drawRunFadeClipped(runFont, text, runStart, i, cursorX, y, scale, shadow,
                        clipLeft, clipRight, fade);
                runFont = null;
                runStart = nextIndex;
                String svgName = TextGlyphFallback.vanillaSvgName(cp);
                if (renderer != null && svgName != null) {
                    float fadeAlpha = fadeAlphaAt(cursorX + svgSize * 0.5f, clipLeft, clipRight, fade);
                    float baseAlpha = ((argb >>> 24) & 0xFF) / 255f;
                    if (fadeAlpha > 0.001f) {
                        renderer.svg(svgName, cursorX, svgY, svgSize, svgSize,
                                SvgRenderOptions.fromFile().withAlpha(baseAlpha * fadeAlpha));
                    }
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
                cursorX = drawRunFadeClipped(runFont, text, runStart, i, cursorX, y, scale, shadow,
                        clipLeft, clipRight, fade);
                runFont = nextFont;
                runStart = i;
            }
            i = nextIndex;
        }
        drawRunFadeClipped(runFont, text, runStart, text.length(), cursorX, y, scale, shadow,
                clipLeft, clipRight, fade);
    }

    private static float drawRunFadeClipped(TextRenderer font,
                                            String text,
                                            int start,
                                            int end,
                                            float x,
                                            float y,
                                            float scale,
                                            boolean shadow,
                                            float clipLeft,
                                            float clipRight,
                                            float fade) {
        if (font == null || text == null || end <= start) return x;
        String run = text.substring(start, end);
        font.begin(scale, false, false);
        try {
            return (float) font.renderHorizontalFadeClipped(run, x, y, TMP,
                    clipLeft, clipRight, fade, fade, shadow);
        } finally {
            font.end();
        }
    }

    private static float fadeAlphaAt(float x, float clipLeft, float clipRight, float fade) {
        if (clipRight <= clipLeft) return 1.0f;
        if (x <= clipLeft || x >= clipRight) return 0.0f;
        float f = Math.max(0.0f, fade);
        if (f <= 0.0f) return 1.0f;
        float alpha = 1.0f;
        if (x < clipLeft + f) alpha = Math.min(alpha, (x - clipLeft) / f);
        if (x > clipRight - f) alpha = Math.min(alpha, (clipRight - x) / f);
        return Math.max(0.0f, Math.min(1.0f, alpha));
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


    private record Segment(String text, Style style) {
    }
}
