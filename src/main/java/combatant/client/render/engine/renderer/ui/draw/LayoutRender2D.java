/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.Font;
import combatant.client.render.engine.text.TextSizing;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.helpers.ScissorFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

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

    public static void roundedStrokeAngular(float x,
                                            float y,
                                            float w,
                                            float h,
                                            float radius,
                                            float thickness,
                                            int startColor,
                                            int endColor,
                                            float offset) {
        if (Renderer2D.COLOR == null) return;
        int start = applyGuiAlpha(startColor);
        int end = applyGuiAlpha(endColor);
        if (((start >>> 24) & 0xFF) <= 0 && ((end >>> 24) & 0xFF) <= 0) return;
        Renderer2D.COLOR.roundedRectStrokeAngularGradient(
                x, y, w, h, radius, SOFTNESS, thickness, start, end, offset);
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
        return color;
    }

    private static int clamp(int c) {
        if (c < 0) return 0;
        return Math.min(c, 255);
    }

    private static final java.util.ArrayList<TextBatchItem> TEXT_BATCH = new java.util.ArrayList<>();
    private static final int TEXT_WIDTH_CACHE_LIMIT = 4096;
    private static final int TEXT_HEIGHT_CACHE_LIMIT = 256;
    private static final Map<TextMeasureKey, Float> TEXT_WIDTH_CACHE = new LinkedHashMap<>(512, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TextMeasureKey, Float> eldest) {
            return size() > TEXT_WIDTH_CACHE_LIMIT;
        }
    };
    private static final Map<TextHeightKey, Float> TEXT_HEIGHT_CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TextHeightKey, Float> eldest) {
            return size() > TEXT_HEIGHT_CACHE_LIMIT;
        }
    };

    private static float renderAlphaMultiplier = 1.0f;
    private static boolean verticalAlphaFadeEnabled;
    private static float verticalAlphaFadeStart;
    private static float verticalAlphaFadeEnd;
    private static boolean textBatchActive;
    private static long textFontGeneration = Long.MIN_VALUE;

    public static int mixColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
        int r = (int) (((from >>> 16) & 0xFF) * (1 - t) + ((to >>> 16) & 0xFF) * t);
        int g = (int) (((from >>> 8) & 0xFF) * (1 - t) + ((to >>> 8) & 0xFF) * t);
        int b = (int) (((from) & 0xFF) * (1 - t) + ((to) & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int scaleAlpha(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int na = (int) (a * Math.max(0f, Math.min(1f, factor)));
        return (color & 0x00FFFFFF) | (na << 24);
    }

    public static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static float setRenderAlphaMultiplier(float alpha) {
        float previous = renderAlphaMultiplier;
        renderAlphaMultiplier = Math.max(0.0f, Math.min(1.0f, alpha));
        return previous;
    }

    public static float getRenderAlphaMultiplier() {
        return renderAlphaMultiplier;
    }

    public static void restoreRenderAlphaMultiplier(float previous) {
        renderAlphaMultiplier = Math.max(0.0f, Math.min(1.0f, previous));
    }

    public static VerticalAlphaFadeScope pushBottomAlphaFade(float startY, float endY) {
        VerticalAlphaFadeScope scope = new VerticalAlphaFadeScope(
                verticalAlphaFadeEnabled,
                verticalAlphaFadeStart,
                verticalAlphaFadeEnd
        );
        verticalAlphaFadeEnabled = endY > startY;
        verticalAlphaFadeStart = startY;
        verticalAlphaFadeEnd = Math.max(startY, endY);
        return scope;
    }

    private static int applyAlpha(int argb) {
        if (renderAlphaMultiplier >= 0.999f) return argb;
        int a = (argb >>> 24) & 0xFF;
        int na = Math.round(a * renderAlphaMultiplier);
        return (argb & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    private static float verticalAlphaAt(float y) {
        return verticalAlphaAt(y, verticalAlphaFadeEnabled, verticalAlphaFadeStart, verticalAlphaFadeEnd);
    }

    private static float verticalAlphaAt(float y, boolean enabled, float start, float end) {
        if (!enabled) return 1.0f;
        if (y <= start) return 1.0f;
        if (y >= end) return 0.0f;
        return 1.0f - (y - start) / Math.max(0.0001f, end - start);
    }

    private static int multiplyAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int na = Math.round(a * Math.max(0.0f, Math.min(1.0f, factor)));
        return (argb & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    public static float scaleForSize(float size) {
        return TextSizing.scaleForSize(size);
    }

    public static float textWidth(TextRenderer tr, String text, float size) {
        if (tr == null || text == null || text.isEmpty()) return 0f;
        checkFontGeneration();
        TextMeasureKey key = new TextMeasureKey(tr, Float.floatToIntBits(size), text);
        Float cached = TEXT_WIDTH_CACHE.get(key);
        if (cached != null) return cached;
        float scale = scaleForSize(size);
        tr.begin(scale, true, false);
        try {
            float width = (float) tr.getWidth(text, false);
            TEXT_WIDTH_CACHE.put(key, width);
            return width;
        } finally {
            tr.end();
        }
    }

    public static float textHeight(TextRenderer tr, float size) {
        if (tr == null) return 0f;
        checkFontGeneration();
        TextHeightKey key = new TextHeightKey(tr, Float.floatToIntBits(size));
        Float cached = TEXT_HEIGHT_CACHE.get(key);
        if (cached != null) return cached;
        float scale = scaleForSize(size);
        tr.begin(scale, true, false);
        try {
            float height = (float) tr.getHeight(false);
            TEXT_HEIGHT_CACHE.put(key, height);
            return height;
        } finally {
            tr.end();
        }
    }

    public static void beginTextBatch() {
        if (textBatchActive) return;
        textBatchActive = true;
        TEXT_BATCH.clear();
    }

    public static void endTextBatch() {
        if (!textBatchActive) return;
        textBatchActive = false;
        if (TEXT_BATCH.isEmpty()) return;

        java.util.LinkedHashMap<TextBatchKey, java.util.ArrayList<TextBatchItem>> groups = new java.util.LinkedHashMap<>();
        for (TextBatchItem item : TEXT_BATCH) {
            TextBatchKey key = new TextBatchKey(item.renderer, Float.floatToIntBits(item.size), item.shadow);
            java.util.ArrayList<TextBatchItem> list = groups.get(key);
            if (list == null) {
                list = new java.util.ArrayList<>();
                groups.put(key, list);
            }
            list.add(item);
        }

        for (var entry : groups.entrySet()) {
            TextBatchKey key = entry.getKey();
            TextRenderer tr = key.renderer;
            if (tr == null) continue;
            float size = Float.intBitsToFloat(key.sizeBits);
            float scale = scaleForSize(size);
            tr.begin(scale, false, false);
            for (TextBatchItem item : entry.getValue()) {
                renderTextItem(tr, item);
            }
            tr.end();
        }

        TEXT_BATCH.clear();
    }

    public static void drawText(TextRenderer tr, String text, float x, float y, float size, int argb, boolean shadow) {
        if (tr == null || text == null || text.isEmpty()) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        if (textBatchActive) {
            TEXT_BATCH.add(new TextBatchItem(
                    tr, text, x, y, size, argb, shadow,
                    verticalAlphaFadeEnabled, verticalAlphaFadeStart, verticalAlphaFadeEnd
            ));
            return;
        }
        float scale = scaleForSize(size);
        tr.begin(scale, false, false);
        renderTextItem(tr, new TextBatchItem(
                tr, text, x, y, size, argb, shadow,
                verticalAlphaFadeEnabled, verticalAlphaFadeStart, verticalAlphaFadeEnd
        ));
        tr.end();
    }

    public static void drawMarqueeText(TextRenderer tr, String text, float x, float y,
                                       float size, float available, int argb,
                                       float hoverSeconds, float speed, float gap, float fadeWidth) {
        if (tr == null || text == null || text.isEmpty() || available <= 0f) return;
        float fullWidth = textWidth(tr, text, size);
        if (fullWidth <= available + 0.5f) {
            drawText(tr, text, x, y, size, argb, false);
            return;
        }
        if (hoverSeconds < 0f) {
            drawText(tr, fitText(tr, text, size, available), x, y, size, argb, false);
            return;
        }
        int color = applyAlpha(argb);
        if ((color >>> 24) == 0) return;
        float safeSpeed = Math.max(1f, speed);
        float cycleDistance = fullWidth + Math.max(0f, gap);
        float travelSeconds = cycleDistance / safeSpeed;
        float pauseSeconds = 0.65f;
        float cycle = pauseSeconds + travelSeconds;
        float phase = Math.max(0f, hoverSeconds) % cycle;
        float offset = phase <= pauseSeconds ? 0f : -(phase - pauseSeconds) * safeSpeed;
        float fade = Math.min(Math.max(0f, fadeWidth), available * 0.24f);
        float leftFade = offset < -0.5f ? fade : 0f;
        float right = x + available;
        boolean clip = ScissorFunction.pushRaw(x, y - size * 0.18f,
                available, textHeight(tr, size) + size * 0.38f);
        if (!clip) return;
        try {
            Font.GlyphQuadGradient gradient = (index, codePoint, x0, y0, x1, y1, out) -> {
                float left = marqueeEdgeAlpha((float)x0, x, right, leftFade, fade);
                float rightAlpha = marqueeEdgeAlpha((float)x1, x, right, leftFade, fade);
                out[0] = alpha(color, left * verticalAlphaAt((float)y0));
                out[1] = alpha(color, left * verticalAlphaAt((float)y1));
                out[2] = alpha(color, rightAlpha * verticalAlphaAt((float)y1));
                out[3] = alpha(color, rightAlpha * verticalAlphaAt((float)y0));
            };
            tr.begin(scaleForSize(size), false, false);
            try {
                float firstX = x + offset;
                tr.renderQuadGradient(text, firstX, y, gradient, false);
                if (firstX + cycleDistance < right) {
                    tr.renderQuadGradient(text, firstX + cycleDistance, y, gradient, false);
                }
            } finally {
                tr.end();
            }
        } finally {
            ScissorFunction.pop();
        }
    }

    private static float marqueeEdgeAlpha(float position, float left, float right,
                                          float leftFade, float rightFade) {
        float l = leftFade > 0f ? Math.max(0f, Math.min(1f, (position - left) / leftFade)) : 1f;
        float r = rightFade > 0f ? Math.max(0f, Math.min(1f, (right - position) / rightFade)) : 1f;
        return Math.min(l, r);
    }

    private static void renderTextItem(TextRenderer tr, TextBatchItem item) {
        if (!item.fadeEnabled) {
            tr.render(item.text, item.x, item.y, new RenderColor(item.argb), item.shadow);
            return;
        }
        tr.renderQuadGradient(item.text, item.x, item.y, (index, codePoint, x0, y0, x1, y1, out) -> {
            int top = multiplyAlpha(item.argb, verticalAlphaAt((float) y0, true, item.fadeStart, item.fadeEnd));
            int bottom = multiplyAlpha(item.argb, verticalAlphaAt((float) y1, true, item.fadeStart, item.fadeEnd));
            out[0] = top;
            out[1] = bottom;
            out[2] = bottom;
            out[3] = top;
        }, item.shadow);
    }

    public static String fitText(TextRenderer tr, String text, float size, float maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0f) return text;
        String trimmed = text;
        float w = textWidth(tr, trimmed, size);
        if (w <= maxWidth) return trimmed;
        while (trimmed.length() > 1 && textWidth(tr, trimmed + "...", size) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    public static List<String> wrapText(TextRenderer tr, String text, float size, float maxWidth, int maxLines) {
        ArrayList<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("");
            return lines;
        }
        if (maxLines <= 1 || maxWidth <= 0f) {
            lines.add(fitText(tr, text, size, maxWidth));
            return lines;
        }

        String remaining = normalizeSpaces(text);
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            if (lines.size() == maxLines - 1) {
                lines.add(fitText(tr, remaining, size, maxWidth));
                break;
            }
            if (textWidth(tr, remaining, size) <= maxWidth) {
                lines.add(remaining);
                break;
            }

            int split = bestWrapIndex(tr, remaining, size, maxWidth);
            String line = remaining.substring(0, split).trim();
            if (line.isEmpty()) {
                lines.add(fitText(tr, remaining, size, maxWidth));
                break;
            }
            lines.add(line);
            remaining = remaining.substring(split).trim();
        }

        if (lines.isEmpty()) {
            lines.add(fitText(tr, text, size, maxWidth));
        }
        return lines;
    }

    private static int bestWrapIndex(TextRenderer tr, String text, float size, float maxWidth) {
        int bestSpace = -1;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) continue;
            String candidate = text.substring(0, i).trim();
            if (!candidate.isEmpty() && textWidth(tr, candidate, size) <= maxWidth) {
                bestSpace = i;
                continue;
            }
            if (bestSpace >= 0) break;
        }
        if (bestSpace >= 0) return bestSpace;

        String fitted = fitText(tr, text, size, maxWidth);
        if (fitted.endsWith("...")) {
            fitted = fitted.substring(0, Math.max(1, fitted.length() - 3));
        }
        return Math.max(1, Math.min(text.length(), fitted.length()));
    }

    private static String normalizeSpaces(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(trimmed.length());
        boolean lastSpace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastSpace) {
                    sb.append(' ');
                    lastSpace = true;
                }
            } else {
                sb.append(c);
                lastSpace = false;
            }
        }
        return sb.toString();
    }

    public static void drawRoundedRect(float x, float y, float w, float h, float radius, int argb) {
        if (Renderer2D.COLOR == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        if (verticalAlphaFadeEnabled) {
            int top = multiplyAlpha(argb, verticalAlphaAt(y));
            int bottom = multiplyAlpha(argb, verticalAlphaAt(y + h));
            Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, radius, 1.1f, top, top, bottom, bottom);
        } else {
            Renderer2D.COLOR.roundedRect(x, y, w, h, radius, 1.1f, argb);
        }
    }

    public static void drawRoundedRectStroke(float x, float y, float w, float h, float radius, float thickness, int argb) {
        if (Renderer2D.COLOR == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        if (verticalAlphaFadeEnabled) {
            int top = multiplyAlpha(argb, verticalAlphaAt(y));
            int bottom = multiplyAlpha(argb, verticalAlphaAt(y + h));
            Renderer2D.COLOR.roundedRectStrokeGradientQuad(x, y, w, h, radius, 1.1f, thickness,
                    top, top, bottom, bottom);
        } else {
            Renderer2D.COLOR.roundedRectStroke(x, y, w, h, radius, 1.1f, thickness, argb);
        }
    }

    public static void drawRoundedRectGlow(float x, float y, float w, float h,
                                           float radius, float glow, int argb) {
        if (Renderer2D.COLOR == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        Renderer2D.COLOR.roundedRectGlow(x, y, w, h, radius, 1.1f, glow, argb);
    }

    public static void drawRoundedRectShadow(float x, float y, float w, float h,
                                             float radius, float softness, float spread, int argb) {
        if (Renderer2D.COLOR == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        Renderer2D.COLOR.roundedRectShadow(x, y, w, h, radius, softness, spread, argb);
    }

    public static void drawRoundedRectGradient(float x, float y, float w, float h, float radius,
                                               int startArgb, int endArgb, float angleDeg) {
        if (Renderer2D.COLOR == null) return;
        startArgb = applyAlpha(startArgb);
        endArgb = applyAlpha(endArgb);
        if (((startArgb >>> 24) & 0xFF) <= 0 && ((endArgb >>> 24) & 0xFF) <= 0) return;
        if (verticalAlphaFadeEnabled) {
            int[] colors = new int[4];
            UiMeshGeometry.computeLinearGradientColors(w, h, startArgb, endArgb, angleDeg, 0.0f, colors);
            float topAlpha = verticalAlphaAt(y);
            float bottomAlpha = verticalAlphaAt(y + h);
            Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, radius, 1.1f,
                    multiplyAlpha(colors[0], topAlpha), multiplyAlpha(colors[1], topAlpha),
                    multiplyAlpha(colors[2], bottomAlpha), multiplyAlpha(colors[3], bottomAlpha));
        } else {
            Renderer2D.COLOR.roundedRectGradient(x, y, w, h, radius, 1.1f, startArgb, endArgb, angleDeg);
        }
    }

    public static void drawRoundedRectStrokeGradient(float x, float y, float w, float h, float radius, float thickness,
                                                     int startArgb, int endArgb, float angleDeg) {
        if (Renderer2D.COLOR == null) return;
        startArgb = applyAlpha(startArgb);
        endArgb = applyAlpha(endArgb);
        if (((startArgb >>> 24) & 0xFF) <= 0 && ((endArgb >>> 24) & 0xFF) <= 0) return;
        if (verticalAlphaFadeEnabled) {
            int[] colors = new int[4];
            UiMeshGeometry.computeLinearGradientColors(w, h, startArgb, endArgb, angleDeg, 0.0f, colors);
            float topAlpha = verticalAlphaAt(y);
            float bottomAlpha = verticalAlphaAt(y + h);
            Renderer2D.COLOR.roundedRectStrokeGradientQuad(x, y, w, h, radius, 1.1f, thickness,
                    multiplyAlpha(colors[0], topAlpha), multiplyAlpha(colors[1], topAlpha),
                    multiplyAlpha(colors[2], bottomAlpha), multiplyAlpha(colors[3], bottomAlpha));
        } else {
            Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, h, radius, 1.1f, thickness, startArgb, endArgb, angleDeg);
        }
    }

    public static void drawRoundedRectCorners(float x, float y, float w, float h,
                                              float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                              int argb) {
        if (Renderer2D.COLOR == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        if (verticalAlphaFadeEnabled) {
            int top = multiplyAlpha(argb, verticalAlphaAt(y));
            int bottom = multiplyAlpha(argb, verticalAlphaAt(y + h));
            Renderer2D.COLOR.roundedRectCornersQuad(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL, 1.1f,
                    top, top, bottom, bottom);
        } else {
            Renderer2D.COLOR.roundedRectCorners(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL, 1.1f, argb);
        }
    }

    public static void drawRect(float x, float y, float w, float h, int argb) {
        if (Renderer2D.COLOR == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        if (verticalAlphaFadeEnabled) {
            int top = multiplyAlpha(argb, verticalAlphaAt(y));
            int bottom = multiplyAlpha(argb, verticalAlphaAt(y + h));
            Renderer2D.COLOR.quadGradient(x, y, w, h, top, top, bottom, bottom);
        } else {
            Renderer2D.COLOR.quad(x, y, w, h, argb);
        }
    }

    public static void flushRenderer() {
        if (Renderer2D.COLOR == null) return;
        if (Renderer2D.isBatching()) {
            Renderer2D.flushBatch();
        } else {
            Renderer2D.COLOR.begin();
        }
    }

    public static void drawGradientRect(float x, float y, float w, float h,
                                        int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        if (Renderer2D.COLOR == null) return;
        cTopLeft = multiplyAlpha(applyAlpha(cTopLeft), verticalAlphaAt(y));
        cTopRight = multiplyAlpha(applyAlpha(cTopRight), verticalAlphaAt(y));
        cBottomRight = multiplyAlpha(applyAlpha(cBottomRight), verticalAlphaAt(y + h));
        cBottomLeft = multiplyAlpha(applyAlpha(cBottomLeft), verticalAlphaAt(y + h));
        Renderer2D.COLOR.quadGradient(x, y, w, h, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public static void drawCircle(float cx, float cy, float r, int argb) {
        if (Renderer2D.COLOR == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        if (verticalAlphaFadeEnabled) {
            int top = multiplyAlpha(argb, verticalAlphaAt(cy - r));
            int bottom = multiplyAlpha(argb, verticalAlphaAt(cy + r));
            Renderer2D.COLOR.roundedRectGradientQuad(cx - r, cy - r, r * 2f, r * 2f, r, 1.1f,
                    top, top, bottom, bottom);
        } else {
            Renderer2D.COLOR.circle(cx, cy, r, argb);
        }
    }

    public static void drawLine(float x1, float y1, float x2, float y2, int argb) {
        if (Renderer2D.COLOR == null) return;
        argb = applyAlpha(argb);
        if (((argb >>> 24) & 0xFF) <= 0) return;
        Renderer2D.COLOR.line(x1, y1, x2, y2, argb);
    }

    public static void drawBlur(float x, float y, float w, float h, float radius, int argb, float alpha) {
        alpha *= renderAlphaMultiplier;
        alpha = AnimationUtility.clamp(alpha * clickGuiBlurDensityScale(), 0.0f, 1.0f);
        if (alpha <= 0.01f) return;
        float quality = clickGuiBlurQuality();
        float brightness = 1.0f;
        Renderer2D.COLOR.blurRect(x, y, w, h, radius, quality, brightness, alpha, 0xFFFFFF);
    }

    public static void drawGlass(float x, float y, float w, float h, float radius, float alpha) {
        if (Renderer2D.COLOR == null) return;
        alpha *= renderAlphaMultiplier;
        if (alpha <= 0.01f) return;

        float pad = 5f;
        float gx = x - pad;
        float gy = y - pad;
        float gw = w + pad * 2f;
        float gh = h + pad * 2f;
        float gr = Math.max(0f, radius + pad);
        float scale = Math.max(0.75f, gr / 10.0f);
        float glassOpacity = alpha;

        Renderer2D.COLOR.withLiquidGlassBlurProfile(
                Renderer2D.BlurQuality.LIQUID_GLASS,
                1.38f,
                () -> Renderer2D.COLOR.liquidGlassRect(
                        gx, gy, gw, gh, gr,
                        0xFFFFFFFF,
                        glassOpacity,
                        1.0f,
                        Renderer2D.LiquidGlassPreset.HUD_LARGE,
                        scale,
                        0.0f
                )
        );
    }

    public static float clickGuiBlurQuality() {
        return 27.35f;
    }

    public static float clickGuiBlurDensityScale() {
        return 1.16f;
    }

    public static float clickGuiGlassBlurScale() {
        return 1.28f;
    }



    private static void checkFontGeneration() {
        long generation = Fonts.generation();
        if (textFontGeneration == generation) return;
        invalidateTextCaches();
        textFontGeneration = generation;
    }

    public static void invalidateTextCaches() {
        textFontGeneration = Long.MIN_VALUE;
        TEXT_WIDTH_CACHE.clear();
        TEXT_HEIGHT_CACHE.clear();
        TEXT_BATCH.clear();
        textBatchActive = false;
    }

    public static final class VerticalAlphaFadeScope implements AutoCloseable {
        private final boolean previousEnabled;
        private final float previousStart;
        private final float previousEnd;
        private boolean closed;

        private VerticalAlphaFadeScope(boolean previousEnabled, float previousStart, float previousEnd) {
            this.previousEnabled = previousEnabled;
            this.previousStart = previousStart;
            this.previousEnd = previousEnd;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            verticalAlphaFadeEnabled = previousEnabled;
            verticalAlphaFadeStart = previousStart;
            verticalAlphaFadeEnd = previousEnd;
        }
    }

    private record TextBatchItem(TextRenderer renderer, String text, float x, float y, float size, int argb,
                                 boolean shadow, boolean fadeEnabled, float fadeStart, float fadeEnd) {
    }

    private record TextBatchKey(TextRenderer renderer, int sizeBits, boolean shadow) {
    }

    private record TextMeasureKey(TextRenderer renderer, int sizeBits, String text) {
    }

    private record TextHeightKey(TextRenderer renderer, int sizeBits) {
    }

}
