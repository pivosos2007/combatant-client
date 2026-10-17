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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads Sponge {@code .schem} files (WorldEdit, FAWE, Axiom; versions 2 and 3) into the same dense box the
 * litematic reader produces. Cells are {@code (y * length + z) * width + x}; the block data is one var-int per cell.
 */
public final class SpongeSchemReader {

    private SpongeSchemReader() {
    }

    /** Whether the root compound looks like a Sponge schematic (v3 nests everything under {@code Schematic}). */
    public static boolean matches(CompoundTag root) {
        CompoundTag body = body(root);
        return body.contains("Width") && body.contains("Height") && body.contains("Length");
    }

    public static Schematic parse(CompoundTag root, String fallbackName, LitematicReader.PaletteResolver resolver) throws IOException {
        CompoundTag body = body(root);
        int width = body.getShortOr("Width", (short) 0) & 0xFFFF;
        int height = body.getShortOr("Height", (short) 0) & 0xFFFF;
        int length = body.getShortOr("Length", (short) 0) & 0xFFFF;
        if (width <= 0 || height <= 0 || length <= 0) throw new IOException("Sponge schematic has no size");
        long volume = (long) width * height * length;
        if (volume > LitematicReader.MAX_VOLUME) throw new IOException("Schematic too large: " + width + "x" + height + "x" + length);

        CompoundTag blocks = body.getCompoundOrEmpty("Blocks");
        CompoundTag paletteTag = blocks.contains("Palette") ? blocks.getCompoundOrEmpty("Palette") : body.getCompoundOrEmpty("Palette");
        byte[] data = blocks.contains("Data") ? blocks.getByteArray("Data").orElse(new byte[0])
                : body.getByteArray("BlockData").orElse(new byte[0]);
        if (paletteTag.keySet().isEmpty() || data.length == 0) throw new IOException("Sponge schematic has no block data");

        List<String> warnings = new ArrayList<>();
        LitematicReader.PaletteBuilder palette = new LitematicReader.PaletteBuilder(resolver, warnings);
        int maxIndex = -1;
        for (String key : paletteTag.keySet()) maxIndex = Math.max(maxIndex, paletteTag.getIntOr(key, -1));
        if (maxIndex >= Short.MAX_VALUE) throw new IOException("Sponge palette is too large: " + (maxIndex + 1) + " entries");
        int[] globalIds = new int[maxIndex + 1];
        for (String key : paletteTag.keySet()) {
            int index = paletteTag.getIntOr(key, -1);
            if (index < 0) continue;
            ParsedState parsed = parseState(key);
            globalIds[index] = palette.indexOf(parsed.name, parsed.properties);
        }

        short[] cells = new short[(int) volume];
        int total = 0;
        int outOfRange = 0;
        int position = 0;
        for (int cell = 0; cell < cells.length; cell++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (position >= data.length) throw new IOException("Sponge block data ends after " + cell + " of " + cells.length + " cells");
                int b = data[position++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 28) throw new IOException("Malformed var-int in block data");
            }
            if (value < 0) throw new IOException("Malformed var-int in block data: negative palette index at cell " + cell);
            if (value >= globalIds.length) {
                outOfRange++;
                continue;
            }
            int global = globalIds[value];
            if (global != 0) {
                cells[cell] = (short) global;
                total++;
            }
        }
        if (outOfRange > 0) LitematicReader.addWarning(warnings, outOfRange + " cells use a palette index that does not exist");

        CompoundTag meta = body.getCompoundOrEmpty("Metadata");
        return new Schematic(meta.getStringOr("Name", fallbackName), fallbackName, meta.getStringOr("Author", ""), "",
                width, height, length, palette.states(), palette.ids(), cells, total, 1, List.copyOf(warnings));
    }

    private static CompoundTag body(CompoundTag root) {
        return root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;
    }

    /** Splits {@code minecraft:oak_stairs[facing=north,half=bottom]} into the block id and a sorted property map. */
    static ParsedState parseState(String key) {
        int open = key.indexOf('[');
        if (open < 0 || !key.endsWith("]")) return new ParsedState(key, Collections.emptyMap());
        Map<String, String> props = new TreeMap<>();
        for (String pair : key.substring(open + 1, key.length() - 1).split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return new ParsedState(key.substring(0, open), props);
    }

    record ParsedState(String name, Map<String, String> properties) {
    }
}
