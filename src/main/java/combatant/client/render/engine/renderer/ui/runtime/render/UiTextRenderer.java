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
import combatant.client.render.engine.text.TextEffectSpec;
import combatant.client.render.engine.text.TextRenderer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class UiTextRenderer {
    private static final int WIDTH_CACHE_LIMIT = 1024;
    private static final int HEIGHT_CACHE_LIMIT = 128;

    private final LinkedHashMap<WidthKey, Float> widthCache = new LinkedHashMap<>(WIDTH_CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<WidthKey, Float> eldest) {
            return size() > WIDTH_CACHE_LIMIT;
        }
    };
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
        if (text == null || text.isEmpty()) return 0.0f;
        TextRenderer renderer = resolve(fallback, style);
        float scale = Math.max(0.01f, style.textScale());
        boolean shadow = style.textShadow();
        float maxWidth = style.maxTextWidth();
        WidthKey key = new WidthKey(System.identityHashCode(renderer), text, scale, shadow, maxWidth);
        Float cached = widthCache.get(key);
        if (cached != null) return cached;

        renderer.begin(scale, true, false);
        try {
            float width = (float) renderer.getWidth(text, shadow);
            if (maxWidth > 0.0f) {
                width = Math.min(width, maxWidth);
            }
            widthCache.put(key, width);
            return width;
        } finally {
            renderer.end();
        }
    }

    public float measureHeight(TextRenderer fallback, UiStyle style) {
        TextRenderer renderer = resolve(fallback, style);
        float scale = Math.max(0.01f, style.textScale());
        boolean shadow = style.textShadow();
        HeightKey key = new HeightKey(System.identityHashCode(renderer), scale, shadow);
        Float cached = heightCache.get(key);
        if (cached != null) return cached;

        renderer.begin(scale, true, false);
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
        if (text == null || text.isEmpty()) return;
        if ((color >>> 24) == 0) return;
        TextRenderer renderer = resolve(fallback, style, backend);
        float scale = Math.max(0.01f, style.textScale());
        renderer.begin(scale, false, false);
        try {
            String renderText = style.ellipsis() && style.maxTextWidth() > 0.0f
                    ? ellipsize(renderer, text, style.maxTextWidth(), style.textShadow())
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

    public void renderLinearGradient(TextRenderer fallback,
                                     String text,
                                     float x,
                                     float y,
                                     UiStyle style,
                                     int startColor,
                                     int endColor,
                                     float angleDeg,
                                     String backend) {
        if (text == null || text.isEmpty()) return;
        if (((startColor | endColor) >>> 24) == 0) return;
        TextRenderer renderer = resolve(fallback, style, backend);
        float scale = Math.max(0.01f, style.textScale());
        renderer.begin(scale, false, false);
        try {
            String renderText = style.ellipsis() && style.maxTextWidth() > 0.0f
                    ? ellipsize(renderer, text, style.maxTextWidth(), style.textShadow())
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
        if (text == null || text.isEmpty()) return;
        if ((color >>> 24) == 0) return;
        TextRenderer renderer = resolve(fallback, style);
        float scale = Math.max(0.01f, style.textScale());
        RenderColor renderColor = new RenderColor(color);
        renderer.begin(scale, false, false);
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

    private record WidthKey(int rendererId, String text, float scale, boolean shadow, float maxWidth) {
    }

    private record HeightKey(int rendererId, float scale, boolean shadow) {
    }
}
