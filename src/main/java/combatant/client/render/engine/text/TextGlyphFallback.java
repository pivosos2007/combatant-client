/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import java.util.Set;

/**
 * Shared codepoint fallback order for procedural text.
 * <p>
 * Preferred custom renderer stays first. VanillaSymbols is a Combatant atlas/MSDF renderer,
 * so covered vanilla icons still go through our text mesh pipeline. VanillaTextRenderer is
 * only the hard fallback for glyphs that are not present in our assets yet.
 */
public enum TextGlyphFallback {
    ;
    public static final String VANILLA_SYMBOLS_KEY = "vanilla_symbols";
    public static final String VANILLA_KEY = "vanilla";
    public static final String SVG_KEY_PREFIX = "svg:";
    private static final Set<Integer> VANILLA_SVG_CODEPOINTS = Set.of(
            0x274C,
            0x1F30A,
            0x1F356,
            0x1F3A3,
            0x1F3F9,
            0x1F514,
            0x1F525,
            0x1F531,
            0x1F9EA
    );

    public static TextRenderer vanillaSymbols(TextRenderer fallback) {
        return Fonts.renderer("VanillaSymbols", FontInfo.Type.Regular, fallback != null ? fallback : TextRenderer.get());
    }

    public static TextRenderer rendererForGlyph(TextRenderer preferred, int codePoint) {
        if (preferred != null && preferred.hasGlyph(codePoint)) {
            return preferred;
        }
        TextRenderer symbols = vanillaSymbols(preferred);
        if (symbols != null && symbols.hasGlyph(codePoint)) {
            return symbols;
        }
        return VanillaTextRenderer.INSTANCE;
    }

    public static boolean shouldUseVanillaSvg(TextRenderer preferred, int codePoint) {
        if (!VANILLA_SVG_CODEPOINTS.contains(codePoint)) {
            return false;
        }
        return preferred == null || !preferred.hasGlyph(codePoint);
    }

    public static String vanillaSvgName(int codePoint) {
        if (!VANILLA_SVG_CODEPOINTS.contains(codePoint)) {
            return null;
        }
        return "vanilla/u" + Integer.toHexString(codePoint);
    }

    public static String vanillaSvgFontKey(int codePoint) {
        String name = vanillaSvgName(codePoint);
        return name != null ? SVG_KEY_PREFIX + name : null;
    }

    public static boolean isSvgFontKey(String key) {
        return key != null && key.startsWith(SVG_KEY_PREFIX) && key.length() > SVG_KEY_PREFIX.length();
    }

    public static String svgNameFromFontKey(String key) {
        return isSvgFontKey(key) ? key.substring(SVG_KEY_PREFIX.length()) : null;
    }

    public static String fontKeyForGlyph(String preferredKey,
                                         TextRenderer preferred,
                                         int codePoint,
                                         String defaultPreferredKey) {
        if (preferred != null && preferred.hasGlyph(codePoint)) {
            return preferredKey != null ? preferredKey : defaultPreferredKey;
        }
        String svgKey = vanillaSvgFontKey(codePoint);
        if (svgKey != null) {
            return svgKey;
        }
        TextRenderer symbols = vanillaSymbols(preferred);
        if (symbols != null && symbols.hasGlyph(codePoint)) {
            return VANILLA_SYMBOLS_KEY;
        }
        return VANILLA_KEY;
    }
}
