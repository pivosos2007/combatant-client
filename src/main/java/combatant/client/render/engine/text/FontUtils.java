/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

public enum FontUtils {
    ;
    private static final String FONT_ROOT = "font";
    private static final String MSDF_ROOT = "font/msdf";

    private static final Map<String, BuiltinEntry[]> BUILTIN;

    static {
        Map<String, BuiltinEntry[]> map = new LinkedHashMap<>();
        map.put("Comfortaa", new BuiltinEntry[]{
                new BuiltinEntry("comfortaa.ttf", FontInfo.Type.Regular)
        });
        map.put("Monsterrat", new BuiltinEntry[]{
                new BuiltinEntry("monsterrat.ttf", FontInfo.Type.Regular)
        });
        map.put("ProFont", new BuiltinEntry[]{
                new BuiltinEntry("profont.ttf", FontInfo.Type.Regular)
        });
        map.put("MainMenuIcons", new BuiltinEntry[]{
                new BuiltinEntry("mainmenuicons.ttf", FontInfo.Type.Regular, true)
        });
        map.put("GuiIcons", new BuiltinEntry[]{
                new BuiltinEntry("guiicons.ttf", FontInfo.Type.Regular, true)
        });
        map.put("RichIcons", new BuiltinEntry[]{
                new BuiltinEntry("richicons.ttf", FontInfo.Type.Regular, true)
        });
        map.put("Icons", new BuiltinEntry[]{
                new BuiltinEntry("icons.ttf", FontInfo.Type.Regular)
        });
        map.put("IconsNur", new BuiltinEntry[]{
                new BuiltinEntry("iconsnur.ttf", FontInfo.Type.Regular)
        });
        map.put("WeatherIcons", new BuiltinEntry[]{
                new BuiltinEntry("weather_icons.ttf", FontInfo.Type.Regular)
        });
        map.put("MediaPlayer", new BuiltinEntry[]{
                new BuiltinEntry("mediaplayer.ttf", FontInfo.Type.Regular)
        });
        map.put("VanillaSymbols", new BuiltinEntry[]{
                new BuiltinEntry("vanilla_symbols.ttf", FontInfo.Type.Regular)
        });
        map.put("Inter", new BuiltinEntry[]{
                new BuiltinEntry("inter_regular.ttf", FontInfo.Type.Regular),
                new BuiltinEntry("inter_bold.ttf", FontInfo.Type.Bold)
        });
        map.put("InterMedium", new BuiltinEntry[]{
                new BuiltinEntry("inter_medium.ttf", FontInfo.Type.Regular)
        });
        map.put("Onest", new BuiltinEntry[]{
                new BuiltinEntry("onest_regular.ttf", FontInfo.Type.Regular),
                new BuiltinEntry("onest_bold.ttf", FontInfo.Type.Bold)
        });
        map.put("OnestMedium", new BuiltinEntry[]{
                new BuiltinEntry("onest_medium.ttf", FontInfo.Type.Regular)
        });
        map.put("OnestBold", new BuiltinEntry[]{
                new BuiltinEntry("onest_bold.ttf", FontInfo.Type.Regular)
        });
        map.put("OnestLight", new BuiltinEntry[]{
                new BuiltinEntry("onest_light.ttf", FontInfo.Type.Regular)
        });
        map.put("Iosevka", new BuiltinEntry[]{
                new BuiltinEntry("iosevka-medium.ttf", FontInfo.Type.Regular),
                new BuiltinEntry("iosevka-mediumitalic.ttf", FontInfo.Type.Italic),
                new BuiltinEntry("iosevka-bold.ttf", FontInfo.Type.Bold),
                new BuiltinEntry("iosevka-bolditalic.ttf", FontInfo.Type.BoldItalic)
        });
        BUILTIN = Collections.unmodifiableMap(map);
    }

    public static InputStream streamBuiltin(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        ResourceManager rm = mc.getResourceManager();
        Identifier id = Identifier.fromNamespaceAndPath("combatant", FONT_ROOT + "/" + name);
        try {
            var res = rm.getResource(id);
            if (res.isEmpty()) return null;
            return res.get().open();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Identifier msdfResource(FontInfo info, String extension) {
        if (info == null || extension == null || extension.isBlank()) return null;
        String file = getBuiltinFileName(info);
        if (file == null) return null;
        int dot = file.lastIndexOf('.');
        String base = dot > 0 ? file.substring(0, dot) : file;
        return Identifier.fromNamespaceAndPath("combatant", MSDF_ROOT + "/" + base + "." + extension);
    }

    public static String getBuiltinFileName(FontInfo info) {
        if (info == null) return null;
        BuiltinEntry[] entries = BUILTIN.get(info.family());
        if (entries == null || entries.length == 0) return null;
        for (BuiltinEntry entry : entries) {
            if (entry.type == info.type()) return entry.file;
        }
        return entries[0].file;
    }

    public static boolean isIconFamily(String family) {
        if (family == null) return false;
        return family.equalsIgnoreCase("Icons")
                || family.equalsIgnoreCase("IconsNur")
                || family.equalsIgnoreCase("WeatherIcons")
                || family.equalsIgnoreCase("MediaPlayer")
                || family.equalsIgnoreCase("VanillaSymbols")
                || family.equalsIgnoreCase("Icons2");
    }

    public static InputStream streamFile(File file) {
        try {
            return new FileInputStream(file);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void loadBuiltin(List<FontFamily> families, String familyName) {
        BuiltinEntry[] entries = BUILTIN.get(familyName);
        if (entries == null || entries.length == 0) return;

        FontFamily family = getOrCreate(families, familyName);
        for (BuiltinEntry entry : entries) {
            if (family.hasType(entry.type)) continue;
            family.addFont(new BuiltinFontFace(new FontInfo(familyName, entry.type), entry.file, entry.atlasOnly));
        }
    }

    public static FontInfo getBuiltinFontInfo(String familyName) {
        BuiltinEntry[] entries = BUILTIN.get(familyName);
        if (entries == null || entries.length == 0) return new FontInfo(familyName, FontInfo.Type.Regular);
        return new FontInfo(familyName, entries[0].type);
    }

    public static List<String> getSearchPaths() {
        List<String> out = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (os.contains("win")) {
            out.add(System.getenv("WINDIR") + "\\Fonts");
        } else if (os.contains("mac")) {
            out.add("/System/Library/Fonts");
            out.add("/Library/Fonts");
            out.add(System.getProperty("user.home") + "/Library/Fonts");
        } else {
            out.add("/usr/share/fonts");
            out.add("/usr/local/share/fonts");
            out.add(System.getProperty("user.home") + "/.fonts");
            out.add(System.getProperty("user.home") + "/.local/share/fonts");
        }

        return out;
    }

    public static void loadSystem(List<FontFamily> families, File root) {
        if (root == null || !root.exists() || !root.isDirectory()) return;

        try (Stream<Path> stream = Files.walk(root.toPath())) {
            stream.filter(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".ttf") || name.endsWith(".otf");
            }).forEach(path -> {
                FontInfo info = readFontInfo(path.toFile());
                if (info == null) return;
                FontFamily family = getOrCreate(families, info.family());
                if (family.hasType(info.type())) return;
                family.addFont(new SystemFontFace(info, path));
            });
        } catch (Exception ignored) {
        }
    }

    public static byte[] readBytes(InputStream in) {
        try {
            return in.readAllBytes();
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    private static FontFamily getOrCreate(List<FontFamily> families, String name) {
        for (FontFamily f : families) {
            if (f.getName().equalsIgnoreCase(name)) return f;
        }
        FontFamily family = new FontFamily(name);
        families.add(family);
        return family;
    }

    private static FontInfo readFontInfo(File file) {
        try (InputStream in = new FileInputStream(file)) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            boolean bold = font.isBold();
            boolean italic = font.isItalic();
            FontInfo.Type type = bold && italic ? FontInfo.Type.BoldItalic
                    : bold ? FontInfo.Type.Bold
                    : italic ? FontInfo.Type.Italic
                    : FontInfo.Type.Regular;
            String family = font.getFamily();
            if (family == null || family.isBlank()) return null;
            return new FontInfo(family, type);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record BuiltinEntry(String file, FontInfo.Type type, boolean atlasOnly) {
        private BuiltinEntry(String file, FontInfo.Type type) {
            this(file, type, false);
        }
    }
}
