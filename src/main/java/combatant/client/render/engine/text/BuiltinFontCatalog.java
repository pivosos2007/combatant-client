/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import combatant.client.util.resources.asset.FontAsset;
import combatant.client.util.resources.asset.FontCatalog;

@FontCatalog(namespace = "combatant", root = "font")
public enum BuiltinFontCatalog {
    @FontAsset(value = "comfortaa.ttf", family = "Comfortaa", primary = true, prewarm = true, order = 0)
    COMFORTAA,
    @FontAsset(value = "comfortaa_semibold.ttf", family = "ComfortaaSemibold", prewarm = true, order = 1)
    COMFORTAA_SEMIBOLD,
    @FontAsset(value = "inter_regular.ttf", family = "Inter", type = FontInfo.Type.Regular, prewarm = true, order = 10)
    INTER_REGULAR,
    @FontAsset(value = "inter_bold.ttf", family = "Inter", type = FontInfo.Type.Bold, prewarm = true, order = 11)
    INTER_BOLD,
    @FontAsset(value = "inter_medium.ttf", family = "InterMedium", prewarm = true, order = 20)
    INTER_MEDIUM,
    @FontAsset(value = "onest_regular.ttf", family = "Onest", type = FontInfo.Type.Regular, prewarm = true, order = 30)
    ONEST_REGULAR,
    @FontAsset(value = "onest_bold.ttf", family = "Onest", type = FontInfo.Type.Bold, prewarm = true, order = 31)
    ONEST_BOLD_FACE,
    @FontAsset(value = "onest_medium.ttf", family = "OnestMedium", prewarm = true, order = 40)
    ONEST_MEDIUM,
    @FontAsset(value = "onest_bold.ttf", family = "OnestBold", prewarm = true, order = 50)
    ONEST_BOLD,
    @FontAsset(value = "onest_light.ttf", family = "OnestLight", prewarm = true, order = 60)
    ONEST_LIGHT,
    @FontAsset(value = "matrix_sans_print.ttf", family = "MatrixSansPrint", prewarm = true, order = 65)
    MATRIX_SANS_PRINT,
    @FontAsset(value = "iosevka-medium.ttf", family = "Iosevka", type = FontInfo.Type.Regular, order = 70)
    IOSEVKA_REGULAR,
    @FontAsset(value = "iosevka-mediumitalic.ttf", family = "Iosevka", type = FontInfo.Type.Italic, order = 71)
    IOSEVKA_ITALIC,
    @FontAsset(value = "iosevka-bold.ttf", family = "Iosevka", type = FontInfo.Type.Bold, order = 72)
    IOSEVKA_BOLD,
    @FontAsset(value = "iosevka-bolditalic.ttf", family = "Iosevka", type = FontInfo.Type.BoldItalic, order = 73)
    IOSEVKA_BOLD_ITALIC,
    @FontAsset(value = "monsterrat.ttf", family = "Monsterrat", prewarm = true, order = 80)
    MONSTERRAT,
    @FontAsset(value = "profont.ttf", family = "ProFont", order = 90)
    PROFONT,
    @FontAsset(value = "mainmenuicons.ttf", family = "MainMenuIcons", prewarm = true, order = 100)
    MAIN_MENU_ICONS,
    @FontAsset(value = "guiicons.ttf", family = "GuiIcons", prewarm = true, order = 110)
    GUI_ICONS,
    @FontAsset(value = "richicons.ttf", family = "RichIcons", prewarm = true, order = 120)
    RICH_ICONS,
    @FontAsset(value = "iconsnur.ttf", family = "IconsNur", prewarm = true, order = 130)
    ICONS_NUR,
    @FontAsset(value = "icons.ttf", family = "Icons", prewarm = true, order = 140)
    ICONS,
    @FontAsset(value = "weather_icons.ttf", family = "WeatherIcons", prewarm = true, order = 150)
    WEATHER_ICONS,
    @FontAsset(value = "mediaplayer.ttf", family = "MediaPlayer", prewarm = true, order = 160)
    MEDIA_PLAYER,
    @FontAsset(value = "vanilla_symbols.ttf", family = "VanillaSymbols", prewarm = true, order = 170)
    VANILLA_SYMBOLS;

    private volatile FontInfo cachedInfo;

    private FontAsset metadata() {
        try {
            FontAsset asset = BuiltinFontCatalog.class.getField(name()).getAnnotation(FontAsset.class);
            if (asset == null) {
                throw new IllegalStateException("Missing @FontAsset metadata for " + name());
            }
            return asset;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing enum field for builtin font " + name(), e);
        }
    }

    /** Typed runtime descriptor for this builtin face. */
    public FontInfo info() {
        FontInfo cached = cachedInfo;
        if (cached != null) return cached;
        FontAsset asset = metadata();
        cached = new FontInfo(asset.family(), asset.type());
        cachedInfo = cached;
        return cached;
    }

    /**
     * Resolves the renderer for this exact builtin face. Callers should prefer this over string
     * family names so renames/aliases cannot silently select the wrong font.
     */
    public TextRenderer renderer() {
        return Fonts.renderer(info(), TextRenderer.get());
    }

    /** Resolves this builtin face, using {@code fallback} only if its resource cannot be loaded. */
    public TextRenderer renderer(TextRenderer fallback) {
        return Fonts.renderer(info(), fallback);
    }

    /** Typed style selector for the complete Iosevka family used by rich-text renderers. */
    public static BuiltinFontCatalog iosevka(FontInfo.Type type) {
        FontInfo.Type resolved = type != null ? type : FontInfo.Type.Regular;
        return switch (resolved) {
            case Bold -> IOSEVKA_BOLD;
            case Italic -> IOSEVKA_ITALIC;
            case BoldItalic -> IOSEVKA_BOLD_ITALIC;
            default -> IOSEVKA_REGULAR;
        };
    }
}
