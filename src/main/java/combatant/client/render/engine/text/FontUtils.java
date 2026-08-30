/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import combatant.client.util.resources.asset.AssetAutoLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public enum FontUtils {
    ;
    private static final String FONT_ROOT = "font";
    private static final String MSDF_ROOT = "font/msdf";

    public static InputStream streamBuiltin(String name) {
        return streamBuiltin(Identifier.fromNamespaceAndPath("combatant", FONT_ROOT + "/" + name));
    }

    public static InputStream streamBuiltin(Identifier id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || id == null) return null;
        ResourceManager rm = mc.getResourceManager();
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
        AssetAutoLoader.FontDefinition definition = builtinDefinition(info);
        if (definition == null) return null;
        Identifier resource = definition.resource();
        String path = resource.getPath();
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = file.lastIndexOf('.');
        String base = dot > 0 ? file.substring(0, dot) : file;
        return Identifier.fromNamespaceAndPath(resource.getNamespace(), MSDF_ROOT + "/" + base + "." + extension);
    }

    public static String getBuiltinFileName(FontInfo info) {
        if (info == null) return null;
        AssetAutoLoader.FontDefinition definition = builtinDefinition(info);
        if (definition == null || definition.resource() == null) return null;
        String path = definition.resource().getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public static List<String> getBuiltinFamilies() {
        Set<String> families = new LinkedHashSet<>();
        for (AssetAutoLoader.FontDefinition definition : AssetAutoLoader.fontAssets()) {
            if (definition.info() != null && definition.info().family() != null) {
                families.add(definition.info().family());
            }
        }
        return List.copyOf(families);
    }

    public static String primaryBuiltinFamily() {
        List<AssetAutoLoader.FontDefinition> definitions = AssetAutoLoader.fontAssets();
        for (AssetAutoLoader.FontDefinition definition : definitions) {
            if (definition.primary()) return definition.info().family();
        }
        return definitions.isEmpty() ? null : definitions.getFirst().info().family();
    }

    public static void loadBuiltin(List<FontFamily> families, String familyName) {
        if (familyName == null || familyName.isBlank()) return;
        FontFamily family = null;
        for (AssetAutoLoader.FontDefinition definition : AssetAutoLoader.fontAssets()) {
            FontInfo info = definition.info();
            if (info == null || !familyName.equalsIgnoreCase(info.family())) continue;
            if (family == null) family = getOrCreate(families, info.family());
            if (family.hasType(info.type())) continue;
            family.addFont(new BuiltinFontFace(info, definition.resource(), definition.atlasOnly()));
        }
    }

    public static FontInfo getBuiltinFontInfo(String familyName) {
        if (familyName == null || familyName.isBlank()) return new FontInfo(familyName, FontInfo.Type.Regular);
        for (AssetAutoLoader.FontDefinition definition : AssetAutoLoader.fontAssets()) {
            if (familyName.equalsIgnoreCase(definition.info().family())) return definition.info();
        }
        return new FontInfo(familyName, FontInfo.Type.Regular);
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

    private static AssetAutoLoader.FontDefinition builtinDefinition(FontInfo info) {
        AssetAutoLoader.FontDefinition familyFallback = null;
        for (AssetAutoLoader.FontDefinition definition : AssetAutoLoader.fontAssets()) {
            FontInfo candidate = definition.info();
            if (candidate == null || !candidate.family().equals(info.family())) continue;
            if (familyFallback == null) familyFallback = definition;
            if (candidate.equals(info)) return definition;
        }
        return familyFallback;
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
}
