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
    @FontAsset(value = "mainmenuicons.ttf", family = "MainMenuIcons", atlasOnly = true, order = 100)
    MAIN_MENU_ICONS,
    @FontAsset(value = "guiicons.ttf", family = "GuiIcons", atlasOnly = true, prewarm = true, order = 110)
    GUI_ICONS,
    @FontAsset(value = "richicons.ttf", family = "RichIcons", atlasOnly = true, prewarm = true, order = 120)
    RICH_ICONS,
    @FontAsset(value = "iconsnur.ttf", family = "IconsNur", prewarm = true, order = 130)
    ICONS_NUR,
    @FontAsset(value = "icons.ttf", family = "Icons", prewarm = true, order = 140)
    ICONS,
    @FontAsset(value = "weather_icons.ttf", family = "WeatherIcons", order = 150)
    WEATHER_ICONS,
    @FontAsset(value = "mediaplayer.ttf", family = "MediaPlayer", order = 160)
    MEDIA_PLAYER,
    @FontAsset(value = "vanilla_symbols.ttf", family = "VanillaSymbols", order = 170)
    VANILLA_SYMBOLS
}
