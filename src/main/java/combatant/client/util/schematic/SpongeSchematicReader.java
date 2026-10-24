/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads Sponge {@code .schem} files (WorldEdit, FAWE, Axiom and most web editors), versions 1 to 3.
 *
 * <p>Blocks are one var-int per cell in {@code (y * length + z) * width + x} order, which is the same layout as
 * {@link Schematic}. Version 3 nests everything under a {@code Schematic} compound with the blocks in a
 * {@code Blocks} compound; versions 1 and 2 keep {@code Palette} and {@code BlockData} on the root.</p>
 */
public final class SpongeSchematicReader {

    private SpongeSchematicReader() {
    }

    public static Schematic parse(CompoundTag file, String fallbackName, LitematicReader.PaletteResolver resolver)
            throws IOException {
        CompoundTag root = file.getCompound("Schematic").orElse(file);
        int width = dimension(root, "Width");
        int height = dimension(root, "Height");
        int length = dimension(root, "Length");
        if (width <= 0 || height <= 0 || length <= 0) throw new IOException("Not a Sponge schematic: missing size");
        long volume = (long) width * height * length;
        if (volume > LitematicReader.MAX_VOLUME) {
            throw new IOException("Schematic too large: " + width + "x" + height + "x" + length);
        }

        CompoundTag blocksTag = root.getCompound("Blocks").orElse(root);
        CompoundTag paletteTag = blocksTag.getCompoundOrEmpty("Palette");
        byte[] data = blocksTag.getByteArray("Data").or(() -> blocksTag.getByteArray("BlockData"))
                .orElseThrow(() -> new IOException("Sponge schematic has no block data"));
        if (paletteTag.keySet().isEmpty()) throw new IOException("Sponge schematic has an empty palette");

        List<String> warnings = new ArrayList<>();
        LitematicReader.PaletteBuilder palette = new LitematicReader.PaletteBuilder(resolver, warnings);
        int[] globalIds = new int[1];
        for (String key : paletteTag.keySet()) {
            int id = paletteTag.getIntOr(key, -1);
            if (id < 0) continue;
            if (id >= globalIds.length) globalIds = java.util.Arrays.copyOf(globalIds, Math.max(id + 1, globalIds.length * 2));
            globalIds[id] = indexOfKey(palette, key);
        }

        short[] blocks = new short[(int) volume];
        int total = 0;
        int outOfRange = 0;
        int position = 0;
        for (int cell = 0; cell < blocks.length; cell++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (position >= data.length) throw new IOException("Block data ends after " + cell + " of " + blocks.length + " cells");
                int b = data[position++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 28) throw new IOException("Corrupt var-int in block data");
            }
            if (value >= globalIds.length) {
                outOfRange++;
                continue;
            }
            int global = globalIds[value];
            if (global != 0) {
                blocks[cell] = (short) global;
                total++;
            }
        }
        if (outOfRange > 0) LitematicReader.addWarning(warnings, outOfRange + " entries outside the palette");

        CompoundTag meta = root.getCompoundOrEmpty("Metadata");
        return new Schematic(
                meta.getStringOr("Name", fallbackName),
                fallbackName,
                meta.getStringOr("Author", ""),
                "",
                width, height, length,
                palette.states(),
                palette.ids(),
                blocks,
                total,
                1,
                List.copyOf(warnings));
    }

    /** Splits {@code minecraft:oak_stairs[facing=north,half=bottom]} into a name and sorted properties. */
    private static int indexOfKey(LitematicReader.PaletteBuilder palette, String key) throws IOException {
        int open = key.indexOf('[');
        String name = open < 0 ? key : key.substring(0, open);
        Map<String, String> props = new TreeMap<>();
        if (open >= 0 && key.endsWith("]")) {
            for (String pair : key.substring(open + 1, key.length() - 1).split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return palette.indexOf(name, props);
    }

    private static int dimension(CompoundTag tag, String key) {
        return tag.getShort(key).map(Short::intValue).orElseGet(() -> tag.getIntOr(key, 0)) & 0xFFFF;
    }
}
