/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat;

import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextGlyphFallback;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.VanillaTextRenderer;
import net.minecraft.network.chat.Style;

/** Font selection, grapheme boundaries and measurement shared by BetterChat layout surfaces. */
final class BetterChatTextSupport {
    private BetterChatTextSupport() { }

    static TextRenderer interRegular() {
        return Fonts.renderer("Inter", FontInfo.Type.Regular, TextRenderer.get());
    }

    static TextRenderer iosevkaRegular() {
        return Fonts.renderer("Iosevka", FontInfo.Type.Regular, TextRenderer.get());
    }

    static TextRenderer iosevkaItalic() {
        return Fonts.renderer("Iosevka", FontInfo.Type.Italic, iosevkaRegular());
    }

    static TextRenderer iosevkaBold() {
        return Fonts.renderer("Iosevka", FontInfo.Type.Bold, iosevkaRegular());
    }

    static TextRenderer iosevkaBoldItalic() {
        return Fonts.renderer("Iosevka", FontInfo.Type.BoldItalic, iosevkaBold());
    }

    static TextRenderer renderer(String key) {
        if (key == null) return iosevkaRegular();
        return switch (key) {
            case "iosevka_bold" -> iosevkaBold();
            case "iosevka_bold_italic" -> iosevkaBoldItalic();
            case "iosevka_medium_italic" -> iosevkaItalic();
            case "vanilla_symbols" -> TextGlyphFallback.vanillaSymbols(iosevkaRegular());
            case "vanilla" -> VanillaTextRenderer.INSTANCE;
            default -> iosevkaRegular();
        };
    }

    static String fontForStyle(Style style) {
        Style safe = style == null ? Style.EMPTY : style;
        if (safe.isBold() && safe.isItalic()) return "iosevka_bold_italic";
        if (safe.isBold()) return "iosevka_bold";
        if (safe.isItalic()) return "iosevka_medium_italic";
        return "iosevka_medium";
    }

    static String fontForGlyph(String baseFont, int codePoint) {
        return TextGlyphFallback.fontKeyForGlyph(
                baseFont, renderer(baseFont), codePoint, "iosevka_medium");
    }

    static String fontForCluster(String baseFont, String cluster) {
        if (cluster == null || cluster.isEmpty()) return baseFont;
        int first = cluster.codePointAt(0);
        if (Character.charCount(first) == cluster.length()) return fontForGlyph(baseFont, first);

        TextRenderer preferred = renderer(baseFont);
        if (preferred != null) {
            boolean complete = true;
            for (int offset = 0; offset < cluster.length(); ) {
                int codePoint = cluster.codePointAt(offset);
                if (!preferred.hasGlyph(codePoint)) {
                    complete = false;
                    break;
                }
                offset += Character.charCount(codePoint);
            }
            if (complete) return baseFont != null ? baseFont : "iosevka_medium";
        }
        return TextGlyphFallback.VANILLA_KEY;
    }

    static int nextClusterEnd(String text, int start) {
        int offset = start + Character.charCount(text.codePointAt(start));
        boolean afterJoiner = false;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            if (afterJoiner) {
                offset += Character.charCount(codePoint);
                afterJoiner = false;
                continue;
            }
            if (codePoint == 0x200D) {
                offset += Character.charCount(codePoint);
                afterJoiner = true;
                continue;
            }
            int type = Character.getType(codePoint);
            boolean combining = type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
            boolean variationSelector = codePoint >= 0xFE00 && codePoint <= 0xFE0F
                    || codePoint >= 0xE0100 && codePoint <= 0xE01EF;
            boolean emojiModifier = codePoint >= 0x1F3FB && codePoint <= 0x1F3FF;
            if (!combining && !variationSelector && !emojiModifier) break;
            offset += Character.charCount(codePoint);
        }
        return offset;
    }

    static TextRenderer rendererForGlyph(TextRenderer preferred, int codePoint) {
        return TextGlyphFallback.rendererForGlyph(preferred, codePoint);
    }

    static float scale(float size) {
        return size / 18.0f;
    }

    static float width(TextRenderer renderer, String text, float size) {
        if (renderer == null || text == null || text.isEmpty()) return 0f;
        float scale = scale(size);
        float svgAdvance = svgGlyphSize(renderer, size, false);
        TextRenderer current = null;
        float width = 0f;
        try {
            for (int i = 0; i < text.length(); ) {
                int codePoint = text.codePointAt(i);
                if (TextGlyphFallback.shouldUseVanillaSvg(renderer, codePoint)) {
                    if (current != null) {
                        current.end();
                        current = null;
                    }
                    width += svgAdvance;
                    i += Character.charCount(codePoint);
                    continue;
                }
                String glyph = new String(Character.toChars(codePoint));
                TextRenderer next = rendererForGlyph(renderer, codePoint);
                if (next != current) {
                    if (current != null) current.end();
                    current = next;
                    current.begin(scale, true, false);
                }
                width += (float) current.getWidth(glyph, false);
                i += Character.charCount(codePoint);
            }
            return width;
        } finally {
            if (current != null) current.end();
        }
    }

    static float height(TextRenderer renderer, float size) {
        if (renderer == null) return 0f;
        renderer.begin(scale(size), true, false);
        try {
            return (float) renderer.getHeight(false);
        } finally {
            renderer.end();
        }
    }

    static float svgGlyphSize(TextRenderer renderer, float size, boolean shadow) {
        return Math.max(1.0f, height(renderer, size)) * 0.92f;
    }
}
