/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.svg;

import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;

public enum SvgInlineText {
    ;
    private static final String OPEN = "[[svg:";
    private static final String CLOSE = "]]";

    public static double width(TextRenderer textRenderer, String text, boolean shadow) {
        return width(textRenderer, text, shadow, 0.92);
    }

    public static double width(TextRenderer textRenderer, String text, boolean shadow, double iconScale) {
        if (textRenderer == null || text == null || text.isEmpty()) return 0.0;

        double lineHeight = Math.max(1.0, textRenderer.getHeight(shadow));
        double iconSize = lineHeight * Math.max(0.1, iconScale);

        double cursor = 0.0;
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf(OPEN, i);
            if (open < 0) {
                String tail = text.substring(i);
                if (!tail.isEmpty()) {
                    cursor += textRenderer.getWidth(tail, shadow);
                }
                break;
            }

            if (open > i) {
                String chunk = text.substring(i, open);
                cursor += textRenderer.getWidth(chunk, shadow);
            }

            int startName = open + OPEN.length();
            int close = text.indexOf(CLOSE, startName);
            if (close < 0) {
                String tail = text.substring(open);
                cursor += textRenderer.getWidth(tail, shadow);
                break;
            }

            String name = text.substring(startName, close).trim();
            if (name.isEmpty()) {
                String raw = text.substring(open, close + CLOSE.length());
                cursor += textRenderer.getWidth(raw, shadow);
            } else {
                cursor += iconSize;
            }

            i = close + CLOSE.length();
        }
        return cursor;
    }

    public static double render(Renderer2D renderer,
                                TextRenderer textRenderer,
                                String text,
                                double x,
                                double y,
                                RenderColor color,
                                boolean shadow) {
        return render(renderer, textRenderer, text, x, y, color, shadow, SvgRenderOptions.fromFile(), 0.92, 0.0);
    }

    public static double render(Renderer2D renderer,
                                TextRenderer textRenderer,
                                String text,
                                double x,
                                double y,
                                RenderColor color,
                                boolean shadow,
                                SvgRenderOptions svgOptions,
                                double iconScale,
                                double iconYOffsetPx) {
        if (renderer == null || textRenderer == null || text == null || text.isEmpty()) return 0.0;

        RenderColor c = color != null ? color : new RenderColor(0xFFFFFFFF);
        SvgRenderOptions baseOptions = svgOptions != null ? svgOptions : SvgRenderOptions.fromFile();
        float alphaMul = (c.a & 0xFF) / 255.0f;
        SvgRenderOptions drawOptions = baseOptions.withAlpha(baseOptions.alpha() * alphaMul);

        double lineHeight = Math.max(1.0, textRenderer.getHeight(shadow));
        double iconSize = lineHeight * Math.max(0.1, iconScale);
        double iconY = y + (lineHeight - iconSize) * 0.5 + iconYOffsetPx;

        double cursor = x;
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf(OPEN, i);
            if (open < 0) {
                String tail = text.substring(i);
                if (!tail.isEmpty()) {
                    cursor += textRenderer.render(tail, cursor, y, c, shadow);
                }
                break;
            }

            if (open > i) {
                String chunk = text.substring(i, open);
                cursor += textRenderer.render(chunk, cursor, y, c, shadow);
            }

            int startName = open + OPEN.length();
            int close = text.indexOf(CLOSE, startName);
            if (close < 0) {
                String tail = text.substring(open);
                cursor += textRenderer.render(tail, cursor, y, c, shadow);
                break;
            }

            String name = text.substring(startName, close).trim();
            if (name.isEmpty()) {
                String raw = text.substring(open, close + CLOSE.length());
                cursor += textRenderer.render(raw, cursor, y, c, shadow);
            } else {
                renderer.svg(name, cursor, iconY, iconSize, iconSize, drawOptions);
                cursor += iconSize;
            }

            i = close + CLOSE.length();
        }

        return cursor - x;
    }
}
