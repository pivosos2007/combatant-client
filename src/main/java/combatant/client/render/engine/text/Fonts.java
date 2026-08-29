/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.text;

import java.io.File;
import java.util.*;

public enum Fonts {
    ;
    public static final String[] BUILTIN_FONTS = {
            "Comfortaa",
            "Inter",
            "InterMedium",
            "Onest",
            "OnestMedium",
            "OnestBold",
            "OnestLight",
            "Iosevka",
            "Monsterrat",
            "ProFont",
            "MainMenuIcons",
            "GuiIcons",
            "RichIcons",
            "IconsNur",
            "Icons",
            "WeatherIcons",
            "MediaPlayer",
            "VanillaSymbols",
    };
    public static final List<FontFamily> FONT_FAMILIES = new ArrayList<>();
    private static final Map<FontInfo, TextRenderer> RENDERER_CACHE = new HashMap<>();
    private static final Map<String, FontFamily> FAMILY_BY_NAME = new HashMap<>();
    private static final Map<String, FontFace> FACE_CACHE = new HashMap<>();
    private static final Map<String, String> NORMALIZED_CACHE = new HashMap<>();
    private static final Map<String, String> FAMILY_ALIASES = new HashMap<>();
    public static String DEFAULT_FONT_FAMILY;
    public static FontFace DEFAULT_FONT;
    public static CustomTextRenderer RENDERER;
    private static TextRenderer DEFAULT_RENDERER;

    public static void refresh() {
        destroyCachedRenderers();

        FONT_FAMILIES.clear();
        RENDERER_CACHE.clear();
        FAMILY_BY_NAME.clear();
        FACE_CACHE.clear();
        NORMALIZED_CACHE.clear();
        ensureAliases();

        for (String builtin : BUILTIN_FONTS) {
            FontUtils.loadBuiltin(FONT_FAMILIES, builtin);
        }

        for (String fontPath : FontUtils.getSearchPaths()) {
            FontUtils.loadSystem(FONT_FAMILIES, new File(fontPath));
        }

        FONT_FAMILIES.sort(Comparator.comparing(FontFamily::getName));
        for (FontFamily family : FONT_FAMILIES) {
            if (family == null || family.getName() == null) continue;
            FAMILY_BY_NAME.put(family.getName().toLowerCase(Locale.ROOT), family);
        }

        DEFAULT_FONT_FAMILY = BUILTIN_FONTS[0];
        DEFAULT_FONT = getFamily(DEFAULT_FONT_FAMILY).get(FontInfo.Type.Regular);

        load(DEFAULT_FONT);
    }

    public static void load(FontFace fontFace) {
        if (fontFace == null) return;

        if (RENDERER != null) {
            if (RENDERER.fontFace.equals(fontFace)) return;
            RENDERER.destroy();
        }

        try {
            RENDERER = new CustomTextRenderer(fontFace);
            DEFAULT_RENDERER = new LanguageFallbackTextRenderer(RENDERER);
        } catch (Exception e) {
            if (fontFace.equals(DEFAULT_FONT)) {
                throw new RuntimeException("Failed to load default font: " + fontFace, e);
            }
            load(DEFAULT_FONT);
        }
    }

    public static int prewarmRenderers(Collection<FontInfo> fontInfos) {
        if (fontInfos == null || fontInfos.isEmpty()) return 0;
        int warmed = 0;
        TextRenderer fallback = TextRenderer.get();
        for (FontInfo info : fontInfos) {
            try {
                TextRenderer renderer = renderer(info, fallback);
                if (renderer != null) {
                    warmed++;
                }
            } catch (Throwable ignored) {
            }
        }
        return warmed;
    }

    private static void destroyCachedRenderers() {
        for (TextRenderer renderer : RENDERER_CACHE.values()) {
            CustomTextRenderer custom = LanguageFallbackTextRenderer.customPrimary(renderer);
            if (custom != null && custom != RENDERER) {
                try {
                    custom.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static FontFamily getFamily(String name) {
        if (name == null || name.isBlank()) return null;
        return FAMILY_BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    public static TextRenderer renderer(String family) {
        return renderer(family, FontInfo.Type.Regular, TextRenderer.get());
    }

    public static TextRenderer renderer(String family, FontInfo.Type type) {
        return renderer(family, type, TextRenderer.get());
    }

    public static TextRenderer renderer(String family, FontInfo.Type type, TextRenderer fallback) {
        FontFace face = resolveFace(family, type);
        return renderer(face, fallback);
    }

    public static TextRenderer renderer(FontInfo info, TextRenderer fallback) {
        if (info == null) return fallback != null ? fallback : TextRenderer.get();
        FontFace face = resolveFace(info.family(), info.type());
        return renderer(face, fallback);
    }

    public static TextRenderer renderer(FontFace face, TextRenderer fallback) {
        if (face == null) return fallback != null ? fallback : TextRenderer.get();
        if (RENDERER != null && RENDERER.fontFace != null && RENDERER.fontFace.info.equals(face.info)) {
            return defaultRenderer();
        }
        TextRenderer cached = RENDERER_CACHE.get(face.info);
        if (cached != null) return cached;
        try {
            TextRenderer created = new LanguageFallbackTextRenderer(new CustomTextRenderer(face));
            RENDERER_CACHE.put(face.info, created);
            return created;
        } catch (Exception e) {
            return fallback != null ? fallback : TextRenderer.get();
        }
    }

    static TextRenderer defaultRenderer() {
        if (DEFAULT_RENDERER != null) return DEFAULT_RENDERER;
        return RENDERER != null ? RENDERER : VanillaTextRenderer.INSTANCE;
    }

    private static FontFace resolveFace(String family, FontInfo.Type type) {
        if (family == null || family.isBlank()) return null;
        String name = normalizeFamily(family);
        if (name == null || name.isBlank()) return null;
        FontFamily fontFamily = getFamily(name);
        if (fontFamily == null) return null;

        FontInfo.Type wanted = type != null ? type : FontInfo.Type.Regular;
        String cacheKey = name.toLowerCase(Locale.ROOT) + "|" + wanted.name();
        FontFace cached = FACE_CACHE.get(cacheKey);
        if (cached != null) return cached;
        FontFace face = fontFamily.get(wanted);
        if (face != null) {
            FACE_CACHE.put(cacheKey, face);
            return face;
        }
        if (wanted != FontInfo.Type.Regular) {
            face = fontFamily.get(FontInfo.Type.Regular);
            if (face != null) {
                FACE_CACHE.put(cacheKey, face);
                return face;
            }
        }
        face = fontFamily.get(FontInfo.Type.Bold);
        if (face != null) {
            FACE_CACHE.put(cacheKey, face);
        }
        return face;
    }

    private static String normalizeFamily(String family) {
        ensureAliases();
        String cached = NORMALIZED_CACHE.get(family);
        if (cached != null) return cached;
        String key = family.trim();
        String alias = FAMILY_ALIASES.get(key.toLowerCase(Locale.ROOT));
        String out = alias != null ? alias : key;
        NORMALIZED_CACHE.put(family, out);
        return out;
    }

    private static void ensureAliases() {
        if (!FAMILY_ALIASES.isEmpty()) return;
        FAMILY_ALIASES.put("montserrat", "Monsterrat");
        FAMILY_ALIASES.put("intermedium", "InterMedium");
        FAMILY_ALIASES.put("inter medium", "InterMedium");
        FAMILY_ALIASES.put("inter_medium", "InterMedium");
        FAMILY_ALIASES.put("inter-medium", "InterMedium");
        FAMILY_ALIASES.put("onestmedium", "OnestMedium");
        FAMILY_ALIASES.put("onest medium", "OnestMedium");
        FAMILY_ALIASES.put("onest_medium", "OnestMedium");
        FAMILY_ALIASES.put("onest-medium", "OnestMedium");
        FAMILY_ALIASES.put("iconsnur", "IconsNur");
        FAMILY_ALIASES.put("vanillasymbols", "VanillaSymbols");
        FAMILY_ALIASES.put("vanilla symbols", "VanillaSymbols");
        FAMILY_ALIASES.put("vanilla_symbols", "VanillaSymbols");
        FAMILY_ALIASES.put("vanilla-symbols", "VanillaSymbols");
        FAMILY_ALIASES.put("mainmenuicons", "MainMenuIcons");
        FAMILY_ALIASES.put("menuicons", "MainMenuIcons");
        FAMILY_ALIASES.put("guiicons", "GuiIcons");
        FAMILY_ALIASES.put("gui_icons", "GuiIcons");
        FAMILY_ALIASES.put("richicons", "RichIcons");
        FAMILY_ALIASES.put("rich_icons", "RichIcons");
        FAMILY_ALIASES.put("onestbold", "OnestBold");
        FAMILY_ALIASES.put("onest bold", "OnestBold");
        FAMILY_ALIASES.put("onest_bold", "OnestBold");
        FAMILY_ALIASES.put("onest-bold", "OnestBold");
        FAMILY_ALIASES.put("onestlight", "OnestLight");
        FAMILY_ALIASES.put("onest light", "OnestLight");
        FAMILY_ALIASES.put("onest_light", "OnestLight");
        FAMILY_ALIASES.put("onest-light", "OnestLight");
    }
}
