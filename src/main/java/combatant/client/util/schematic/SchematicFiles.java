/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** The schematic file formats the menu can load, picked by extension and confirmed by the NBT layout. */
public final class SchematicFiles {

    public static final List<String> EXTENSIONS = List.of(LitematicReader.EXTENSION, ".schem", ".nbt");

    private SchematicFiles() {
    }

    public static boolean isSupported(Path path) {
        return extensionOf(path.getFileName().toString()) != null;
    }

    /** The matching entry of {@link #EXTENSIONS} (lower case), or null when the name is not a schematic file. */
    public static String extensionOf(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (lower.endsWith(extension)) return extension;
        }
        return null;
    }

    /** File name without its schematic extension; other names come back unchanged. */
    public static String baseName(Path path) {
        return baseName(path.getFileName().toString());
    }

    public static String baseName(String fileName) {
        String extension = extensionOf(fileName);
        return extension == null ? fileName : fileName.substring(0, fileName.length() - extension.length());
    }

    /** Short label for the file type chip ("litematic", "schem", "nbt"). */
    public static String formatLabel(String fileName) {
        String extension = extensionOf(fileName);
        return extension == null ? "" : extension.substring(1);
    }

    public static Schematic read(Path path) throws IOException {
        return parse(readRoot(path), baseName(path), LitematicReader::resolveState);
    }

    public static SchematicHeader readHeader(Path path, long lastModified) throws IOException {
        if (LitematicReader.EXTENSION.equals(extensionOf(path.getFileName().toString()))) {
            return LitematicReader.readHeader(path, lastModified);
        }
        Schematic schematic = read(path);
        return new SchematicHeader(path, path.getFileName().toString(), schematic.name(), schematic.author(),
                schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), schematic.totalBlocks(), schematic.regionCount(),
                lastModified, "");
    }

    public static Schematic parse(CompoundTag root, String fallbackName, LitematicReader.PaletteResolver resolver) throws IOException {
        if (root.contains("Regions")) return LitematicReader.parse(root, fallbackName, resolver);
        if (SpongeSchemReader.matches(root)) return SpongeSchemReader.parse(root, fallbackName, resolver);
        if (StructureNbtReader.matches(root)) return StructureNbtReader.parse(root, fallbackName, resolver);
        throw new IOException("Not a litematic, Sponge schem or structure nbt file");
    }

    private static CompoundTag readRoot(Path path) throws IOException {
        return LitematicReader.readBounded(path);
    }
}
