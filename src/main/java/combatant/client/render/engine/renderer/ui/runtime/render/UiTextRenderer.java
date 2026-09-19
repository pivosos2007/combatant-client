/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;
import combatant.client.render.engine.text.VanillaTextRenderer;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.RuntimeTextLayout;
import combatant.client.render.engine.text.TextEffectSpec;
import combatant.client.render.engine.text.TextRenderer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UiTextRenderer {
    private static final int HEIGHT_CACHE_LIMIT = 128;

    private final LinkedHashMap<HeightKey, Float> heightCache = new LinkedHashMap<>(HEIGHT_CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<HeightKey, Float> eldest) {
            return size() > HEIGHT_CACHE_LIMIT;
        }
    };

    private static String ellipsize(TextRenderer renderer, String text, float maxWidth, boolean shadow) {
        if (renderer.getWidth(text, shadow) <= maxWidth) return text;
        String suffix = "...";
        float suffixWidth = (float) renderer.getWidth(suffix, shadow);
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (renderer.getWidth(text, mid, shadow) + suffixWidth <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low <= 0 ? suffix : text.substring(0, low) + suffix;
    }

    public float measureWidth(TextRenderer fallback, String text, UiStyle style) {
        return layout(fallback, text, style).width();
    }

    public float measureHeight(TextRenderer fallback, String text, UiStyle style) {
        return layout(fallback, text, style).height();
    }

    public float measureHeight(TextRenderer fallback, UiStyle style) {
        return lineHeight(fallback, style);
    }

    public TextLayout layout(TextRenderer fallback, String text, UiStyle style) {
        boolean preserveWhitespace = "pre-wrap".equals(style.whiteSpace());
        String safe = preserveWhitespace
                ? RuntimeTextLayout.multiLine(text)
                : RuntimeTextLayout.singleLine(text);
        if (safe.isEmpty()) return new TextLayout(List.of(), 0.0f, 0.0f, lineHeight(fallback, style));

        TextRenderer renderer = resolve(fallback, style);
        float fontSize = style.fontSize();
        boolean shadow = style.textShadow();
        float constraint = textConstraint(style);
        float lineHeight = lineHeight(fallback, style);
        List<String> lines = new ArrayList<>();

        renderer.beginSize(fontSize, true, false);
        try {
            if (!style.wrapsText()) {
                String line = RuntimeTextLayout.singleLine(safe);
                if (style.ellipsizesText() && constraint > 0.0f) {
                    line = ellipsize(renderer, line, constraint, shadow);
                }
                lines.add(line);
            } else {
                String[] paragraphs = preserveWhitespace ? safe.split("\n", -1) : new String[]{safe};
                for (String paragraph : paragraphs) {
                    if (constraint <= 0.0f) {
                        lines.add(paragraph);
                    } else if (preserveWhitespace) {
                        wrapPreformattedParagraph(renderer, paragraph, constraint, shadow, style, lines);
                    } else {
                        wrapParagraph(renderer, paragraph, constraint, shadow, style, lines);
                    }
                }
            }

            boolean truncated = false;
            int maxLines = style.maxLines();
            if (maxLines > 0 && lines.size() > maxLines) {
                truncated = true;
                while (lines.size() > maxLines) lines.remove(lines.size() - 1);
            }
            if (truncated && style.ellipsizesText() && !lines.isEmpty()) {
                int last = lines.size() - 1;
                String value = lines.get(last);
                lines.set(last, constraint > 0.0f
                        ? ellipsizeWithSuffix(renderer, value, constraint, shadow)
                        : value + "...");
            }

            float width = 0.0f;
            for (String line : lines) width = Math.max(width, (float) renderer.getWidth(line, shadow));
            if (constraint > 0.0f && (!style.wrapsText()
                    || "break-word".equals(style.overflowWrap())
                    || "anywhere".equals(style.overflowWrap()))) {
                width = Math.min(width, constraint);
            }
            return new TextLayout(List.copyOf(lines), width, lineHeight * lines.size(), lineHeight);
        } finally {
            renderer.end();
        }
    }

    private void wrapParagraph(TextRenderer renderer,
                               String paragraph,
                               float maxWidth,
                               boolean shadow,
                               UiStyle style,
                               List<String> out) {
        int before = out.size();
        if (paragraph.isEmpty()) {
            out.add("");
            return;
        }
        String[] words = paragraph.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (renderer.getWidth(candidate, shadow) <= maxWidth || line.isEmpty()) {
                if (line.isEmpty() && renderer.getWidth(word, shadow) > maxWidth
                        && !"normal".equals(style.overflowWrap())) {
                    appendBrokenWord(renderer, word, maxWidth, shadow, out, line);
                } else {
                    line.setLength(0);
                    line.append(candidate);
                }
                continue;
            }
            out.add(line.toString());
            line.setLength(0);
            if (renderer.getWidth(word, shadow) > maxWidth && !"normal".equals(style.overflowWrap())) {
                appendBrokenWord(renderer, word, maxWidth, shadow, out, line);
            } else {
                line.append(word);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        if (out.size() == before) out.add("");
    }

    /** CSS-like pre-wrap: preserve authored whitespace/newlines, wrap at whitespace when possible. */
    private void wrapPreformattedParagraph(TextRenderer renderer,
                                           String paragraph,
                                           float maxWidth,
                                           boolean shadow,
                                           UiStyle style,
                                           List<String> out) {
        if (paragraph.isEmpty()) {
            out.add("");
            return;
        }

        StringBuilder line = new StringBuilder();
        int lastBreak = -1;
        for (int offset = 0; offset < paragraph.length(); ) {
            int cp = paragraph.codePointAt(offset);
            int count = Character.charCount(cp);
            line.appendCodePoint(cp);
            if (Character.isWhitespace(cp)) lastBreak = line.length();

            if (renderer.getWidth(line.toString(), shadow) > maxWidth) {
                if (lastBreak > 0 && lastBreak < line.length()) {
                    out.add(line.substring(0, lastBreak));
                    String remainder = line.substring(lastBreak);
                    line.setLength(0);
                    line.append(remainder);
                    lastBreak = lastWhitespaceBreak(line);
                } else if (lastBreak == line.length()) {
                    out.add(line.toString());
                    line.setLength(0);
                    lastBreak = -1;
                } else if (!"normal".equals(style.overflowWrap()) && line.codePointCount(0, line.length()) > 1) {
                    int cpStart = line.offsetByCodePoints(line.length(), -1);
                    String tail = line.substring(cpStart);
                    line.delete(cpStart, line.length());
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(tail);
                    lastBreak = -1;
                }
            }
            offset += count;
        }
        if (!line.isEmpty()) out.add(line.toString());
    }

    private static int lastWhitespaceBreak(CharSequence value) {
        int last = -1;
        for (int offset = 0; offset < value.length(); ) {
            int cp = Character.codePointAt(value, offset);
            int count = Character.charCount(cp);
            if (Character.isWhitespace(cp)) last = offset + count;
            offset += count;
        }
        return last;
    }

    private static void appendBrokenWord(TextRenderer renderer,
                                         String word,
                                         float maxWidth,
                                         boolean shadow,
                                         List<String> out,
                                         StringBuilder remainder) {
        StringBuilder part = new StringBuilder();
        for (int offset = 0; offset < word.length(); ) {
            int cp = word.codePointAt(offset);
            String next = part.toString() + new String(Character.toChars(cp));
            if (!part.isEmpty() && renderer.getWidth(next, shadow) > maxWidth) {
                out.add(part.toString());
                part.setLength(0);
            }
            part.appendCodePoint(cp);
            offset += Character.charCount(cp);
        }
        remainder.append(part);
    }

    private static String ellipsizeWithSuffix(TextRenderer renderer, String text, float maxWidth, boolean shadow) {
        String suffix = "...";
        float suffixWidth = (float) renderer.getWidth(suffix, shadow);
        if (suffixWidth >= maxWidth) return suffix;
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (renderer.getWidth(text, mid, shadow) + suffixWidth <= maxWidth) low = mid;
            else high = mid - 1;
        }
        return text.substring(0, Math.max(0, low)) + suffix;
    }

    private static float textConstraint(UiStyle style) {
        if (style.maxTextWidth() > 0.0f) return style.maxTextWidth();
        if (style.width() != null) return Math.max(0.0f, style.width() - style.paddingX());
        if (style.maxWidth() != null) return Math.max(0.0f, style.maxWidth() - style.paddingX());
        return 0.0f;
    }

    private float lineHeight(TextRenderer fallback, UiStyle style) {
        float glyphHeight = measureGlyphHeight(fallback, style);
        Float lineHeight = style.lineHeight();
        return lineHeight != null ? lineHeight : glyphHeight;
    }

    /** Leading offset for an explicitly authored CSS-like line-height. */
    public float lineOffsetY(TextRenderer fallback, UiStyle style) {
        Float lineHeight = style.lineHeight();
        if (lineHeight == null) return 0.0f;
        return (lineHeight - measureGlyphHeight(fallback, style)) * 0.5f;
    }

    private float measureGlyphHeight(TextRenderer fallback, UiStyle style) {
        TextRenderer renderer = resolve(fallback, style);
        float fontSize = style.fontSize();
        boolean shadow = style.textShadow();
        HeightKey key = new HeightKey(System.identityHashCode(renderer), fontSize, shadow);
        Float cached = heightCache.get(key);
        if (cached != null) return cached;

        renderer.beginSize(fontSize, true, false);
        try {
            float height = (float) renderer.getHeight(shadow);
            heightCache.put(key, height);
            return height;
        } finally {
            renderer.end();
        }
    }

    public void render(TextRenderer fallback, String text, float x, float y, UiStyle style) {
        render(fallback, text, x, y, style, style.textColor() != null ? style.textColor() : 0xFFFFFFFF);
    }

    public void render(TextRenderer fallback, String text, float x, float y, UiStyle style, int color) {
        render(fallback, text, x, y, style, color, style.textEffect(), style.textEffectSpeed());
    }

    public void render(TextRenderer fallback, String text, float x, float y, UiStyle style, int color, String effectName, int effectSpeed) {
        TextEffectSpec effect = TextEffectSpec.of(effectName, Math.max(0.1f, effectSpeed * 0.08f));
        render(fallback, text, x, y, style, color, effect);
    }

    public void render(TextRenderer fallback, String text, float x, float y, UiStyle style, int color, TextEffectSpec effect) {
        render(fallback, text, x, y, style, color, effect, style.textBackend());
    }

    public void render(TextRenderer fallback, String text, float x, float y, UiStyle style, int color, TextEffectSpec effect, String backend) {
        render(fallback, text, x, y, style, color, effect, backend, 1.0f);
    }

    public void render(TextRenderer fallback, String text, float x, float y, UiStyle style, int color,
                       TextEffectSpec effect, String backend, float renderScale) {
        text = RuntimeTextLayout.singleLine(text);
        if (text.isEmpty()) return;
        if ((color >>> 24) == 0) return;
        TextRenderer renderer = resolve(fallback, style, backend);
        float scale = Math.max(0.0001f, renderScale);
        float fontSize = style.fontSize() * scale;
        renderer.beginSize(fontSize, false, false);
        try {
            String renderText = style.ellipsizesText() && style.maxTextWidth() > 0.0f
                    ? ellipsize(renderer, text, style.maxTextWidth() * scale, style.textShadow())
                    : text;
            float timeSec = (System.currentTimeMillis() % 3_600_000L) / 1000.0f;
            if (!UiTextEffectRenderer.render(
                    renderer,
                    renderText,
                    x,
                    y,
                    color,
                    effect,
                    timeSec,
                    style.textShadow()
            )) {
                renderer.render(renderText, x, y, new RenderColor(color), style.textShadow());
            }
        } finally {
            renderer.end();
        }
    }

    /**
     * Draws a compact two-ring phosphor bloom behind the sharp text pass.
     * The spread intentionally stays small so display text remains legible at HUD scale.
     */
    public void renderGlow(TextRenderer fallback,
                           String text,
                           float x,
                           float y,
                           UiStyle style,
                           int color,
                           float width,
                           float strength,
                           String backend) {
        renderGlow(fallback, text, x, y, style, color, width, strength, backend, 1.0f);
    }

    public void renderGlow(TextRenderer fallback,
                           String text,
                           float x,
                           float y,
                           UiStyle style,
                           int color,
                           float width,
                           float strength,
                           String backend,
                           float renderScale) {
        text = RuntimeTextLayout.singleLine(text);
        float scale = Math.max(0.0001f, renderScale);
        float spread = Math.max(0.0f, Math.min(4.0f * scale, width * scale));
        float power = Math.max(0.0f, Math.min(1.0f, strength));
        if (text.isEmpty() || spread <= 0.01f || power <= 0.001f || (color >>> 24) == 0) return;

        TextRenderer renderer = resolve(fallback, style, backend);
        float fontSize = style.fontSize() * scale;
        String renderText;
        renderer.beginSize(fontSize, false, false);
        try {
            renderText = style.ellipsizesText() && style.maxTextWidth() > 0.0f
                    ? ellipsize(renderer, text, style.maxTextWidth() * scale, false)
                    : text;
            float inner = Math.max(0.45f, spread * 0.48f);
            int outerColor = multiplyAlpha(color, power * 0.22f);
            int innerColor = multiplyAlpha(color, power * 0.46f);
            renderRing(renderer, renderText, x, y, spread, outerColor);
            renderRing(renderer, renderText, x, y, inner, innerColor);
            renderer.render(renderText, x, y, new RenderColor(multiplyAlpha(color, power * 0.20f)), false);
        } finally {
            renderer.end();
        }
    }

    private static void renderRing(TextRenderer renderer, String text, float x, float y, float radius, int color) {
        if ((color >>> 24) == 0) return;
        RenderColor glow = new RenderColor(color);
        float diagonal = radius * 0.70710677f;
        renderer.render(text, x - radius, y, glow, false);
        renderer.render(text, x + radius, y, glow, false);
        renderer.render(text, x, y - radius, glow, false);
        renderer.render(text, x, y + radius, glow, false);
        renderer.render(text, x - diagonal, y - diagonal, glow, false);
        renderer.render(text, x + diagonal, y - diagonal, glow, false);
        renderer.render(text, x - diagonal, y + diagonal, glow, false);
        renderer.render(text, x + diagonal, y + diagonal, glow, false);
    }

    private static int multiplyAlpha(int color, float amount) {
        int alpha = (color >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, Math.round(alpha * Math.max(0.0f, Math.min(1.0f, amount)))));
        return (color & 0x00FFFFFF) | (scaled << 24);
    }

    public void renderLinearGradient(TextRenderer fallback,
                                     String text,
                                     float x,
                                     float y,
                                     UiStyle style,
                                     int startColor,
                                     int endColor,
                                     float angleDeg,
                                     String backend) {
        renderLinearGradient(fallback, text, x, y, style, startColor, endColor, angleDeg, backend, 1.0f);
    }

    public void renderLinearGradient(TextRenderer fallback,
                                     String text,
                                     float x,
                                     float y,
                                     UiStyle style,
                                     int startColor,
                                     int endColor,
                                     float angleDeg,
                                     String backend,
                                     float renderScale) {
        text = RuntimeTextLayout.singleLine(text);
        if (text.isEmpty()) return;
        if (((startColor | endColor) >>> 24) == 0) return;
        TextRenderer renderer = resolve(fallback, style, backend);
        float scale = Math.max(0.0001f, renderScale);
        float fontSize = style.fontSize() * scale;
        renderer.beginSize(fontSize, false, false);
        try {
            String renderText = style.ellipsizesText() && style.maxTextWidth() > 0.0f
                    ? ellipsize(renderer, text, style.maxTextWidth() * scale, style.textShadow())
                    : text;
            renderer.renderQuadGradient(renderText, x, y, (idx, cp, x0, y0, x1, y1, out) ->
                    linearGradientColors(x0, y0, x1, y1, startColor, endColor, angleDeg, out),
                    style.textShadow());
        } finally {
            renderer.end();
        }
    }

    private static void linearGradientColors(double x0,
                                             double y0,
                                             double x1,
                                             double y1,
                                             int startColor,
                                             int endColor,
                                             float angleDeg,
                                             int[] out) {
        double width = Math.max(0.0001, Math.abs(x1 - x0));
        double height = Math.max(0.0001, Math.abs(y1 - y0));
        double angle = Math.toRadians(angleDeg);
        double dirX = Math.cos(angle);
        double dirY = Math.sin(angle);

        double p0 = 0.0;
        double p1 = width * dirX;
        double p2 = height * dirY;
        double p3 = p1 + p2;
        double min = Math.min(Math.min(p0, p1), Math.min(p2, p3));
        double max = Math.max(Math.max(p0, p1), Math.max(p2, p3));
        double range = Math.max(0.0001, max - min);

        out[0] = mixArgb(startColor, endColor, (p0 - min) / range);
        out[1] = mixArgb(startColor, endColor, (p2 - min) / range);
        out[2] = mixArgb(startColor, endColor, (p3 - min) / range);
        out[3] = mixArgb(startColor, endColor, (p1 - min) / range);
    }

    private static int mixArgb(int start, int end, double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        int sa = (start >>> 24) & 0xFF;
        int sr = (start >>> 16) & 0xFF;
        int sg = (start >>> 8) & 0xFF;
        int sb = start & 0xFF;
        int ea = (end >>> 24) & 0xFF;
        int er = (end >>> 16) & 0xFF;
        int eg = (end >>> 8) & 0xFF;
        int eb = end & 0xFF;
        int a = (int) Math.round(sa + (ea - sa) * k);
        int r = (int) Math.round(sr + (er - sr) * k);
        int g = (int) Math.round(sg + (eg - sg) * k);
        int b = (int) Math.round(sb + (eb - sb) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void renderHorizontalFadeClipped(TextRenderer fallback,
                                            String text,
                                            float x,
                                            float y,
                                            UiStyle style,
                                            float clipLeft,
                                            float clipRight,
                                            float fadeLeft,
                                            float fadeRight) {
        renderHorizontalFadeClipped(fallback, text, x, y, style, clipLeft, clipRight, fadeLeft, fadeRight,
                style.textColor() != null ? style.textColor() : 0xFFFFFFFF);
    }

    public void renderHorizontalFadeClipped(TextRenderer fallback,
                                            String text,
                                            float x,
                                            float y,
                                            UiStyle style,
                                            float clipLeft,
                                            float clipRight,
                                            float fadeLeft,
                                            float fadeRight,
                                            int color) {
        renderHorizontalFadeClipped(fallback, text, x, y, style, clipLeft, clipRight, fadeLeft, fadeRight, color, 1.0f);
    }

    public void renderHorizontalFadeClipped(TextRenderer fallback,
                                            String text,
                                            float x,
                                            float y,
                                            UiStyle style,
                                            float clipLeft,
                                            float clipRight,
                                            float fadeLeft,
                                            float fadeRight,
                                            int color,
                                            float renderScale) {
        text = RuntimeTextLayout.singleLine(text);
        if (text.isEmpty()) return;
        if ((color >>> 24) == 0) return;
        TextRenderer renderer = resolve(fallback, style);
        float scale = Math.max(0.0001f, renderScale);
        float fontSize = style.fontSize() * scale;
        RenderColor renderColor = new RenderColor(color);
        renderer.beginSize(fontSize, false, false);
        try {
            renderer.renderHorizontalFadeClipped(text, x, y, renderColor, clipLeft, clipRight, fadeLeft, fadeRight, style.textShadow());
        } finally {
            renderer.end();
        }
    }

    private TextRenderer resolve(TextRenderer fallback, UiStyle style) {
        return resolve(fallback, style, "auto");
    }

    private TextRenderer resolve(TextRenderer fallback, UiStyle style, String backend) {
        if ("vanilla".equalsIgnoreCase(backend) || "vanilla_sodium".equalsIgnoreCase(backend)) {
            return VanillaTextRenderer.INSTANCE;
        }
        TextRenderer base = fallback != null ? fallback : TextRenderer.get();
        if (style.fontFamily() == null || style.fontFamily().isBlank()) {
            return base;
        }
        return Fonts.renderer(style.fontFamily(), style.fontType(), base);
    }

    public record TextLayout(List<String> lines, float width, float height, float lineHeight) {
        public boolean multiline() {
            return lines.size() > 1;
        }
    }

    private record HeightKey(int rendererId, float fontSize, boolean shadow) {
    }
}
