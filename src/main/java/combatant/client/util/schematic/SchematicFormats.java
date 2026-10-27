/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** The schematic file types the menu understands, picked by file extension. */
public final class SchematicFormats {

    /** Litematica, Sponge (WorldEdit, FAWE, Axiom) and vanilla structure-block files, in preference order. */
    public static final List<String> EXTENSIONS = List.of(LitematicReader.EXTENSION, ".schem", ".nbt");


    public static final long MAX_SCHEMATIC_NBT_BYTES = 512L * 1024 * 1024; // 512 MiB
    public static final int MAX_SCHEMATIC_NBT_DEPTH = 512;

    public static NbtAccounter nbtAccounter() {
        return new NbtAccounter(MAX_SCHEMATIC_NBT_BYTES, MAX_SCHEMATIC_NBT_DEPTH);
    }
    private SchematicFormats() {
    }

    /** The matching entry of {@link #EXTENSIONS} for {@code path}, or {@code null} for an unsupported file. */
    public static String extensionOf(Path path) {
        String file = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (file.endsWith(extension)) return extension;
        }
        return null;
    }

    public static boolean isSchematic(Path path) {
        return extensionOf(path) != null;
    }

    public static String baseName(Path path) {
        String file = path.getFileName().toString();
        String extension = extensionOf(path);
        return extension == null ? file : file.substring(0, file.length() - extension.length());
    }

    public static Schematic read(Path path) throws IOException {
        String extension = extensionOf(path);
        if (extension == null) throw new IOException("Unsupported file type");
        if (extension.equals(LitematicReader.EXTENSION)) return LitematicReader.read(path);
        CompoundTag root = NbtIo.readCompressed(path, nbtAccounter());
        String name = baseName(path);
        return extension.equals(".schem")
                ? SpongeSchematicReader.parse(root, name, LitematicReader::resolveState)
                : StructureNbtReader.parse(root, name, LitematicReader::resolveState);
    }

    public static SchematicHeader readHeader(Path path, long lastModified) throws IOException {
        if (LitematicReader.EXTENSION.equals(extensionOf(path))) return LitematicReader.readHeader(path, lastModified);
        Schematic schematic = read(path);
        return new SchematicHeader(path, path.getFileName().toString(), schematic.name(), schematic.author(),
                schematic.sizeX(), schematic.sizeY(), schematic.sizeZ(), schematic.totalBlocks(), schematic.regionCount(),
                lastModified, "");
    }
}
