/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads vanilla structure-block {@code .nbt} files: a {@code size} list, a {@code palette} of states and a
 * {@code blocks} list of {@code {pos, state}}. Cells the file does not list stay air; structure voids are air too.
 */
public final class StructureNbtReader {

    private static final String STRUCTURE_VOID = "minecraft:structure_void";

    private StructureNbtReader() {
    }

    public static boolean matches(CompoundTag root) {
        return root.contains("size") && root.contains("blocks") && (root.contains("palette") || root.contains("palettes"));
    }

    public static Schematic parse(CompoundTag root, String fallbackName, LitematicReader.PaletteResolver resolver) throws IOException {
        ListTag size = root.getListOrEmpty("size");
        int sx = size.getIntOr(0, 0);
        int sy = size.getIntOr(1, 0);
        int sz = size.getIntOr(2, 0);
        if (sx <= 0 || sy <= 0 || sz <= 0) throw new IOException("Structure has no size");
        long volume = (long) sx * sy * sz;
        if (volume > LitematicReader.MAX_VOLUME) throw new IOException("Schematic too large: " + sx + "x" + sy + "x" + sz);

        ListTag paletteTag = root.contains("palette") ? root.getListOrEmpty("palette")
                : root.getListOrEmpty("palettes").getListOrEmpty(0);
        if (paletteTag.isEmpty()) throw new IOException("Structure has no palette");

        List<String> warnings = new ArrayList<>();
        LitematicReader.PaletteBuilder palette = new LitematicReader.PaletteBuilder(resolver, warnings);
        int[] globalIds = new int[paletteTag.size()];
        for (int i = 0; i < globalIds.length; i++) {
            CompoundTag entry = paletteTag.getCompoundOrEmpty(i);
            globalIds[i] = STRUCTURE_VOID.equals(entry.getStringOr("Name", "")) ? 0 : palette.indexOf(entry);
        }

        short[] cells = new short[(int) volume];
        int total = 0;
        int outside = 0;
        ListTag blocks = root.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompoundOrEmpty(i);
            ListTag pos = block.getListOrEmpty("pos");
            int x = pos.getIntOr(0, -1);
            int y = pos.getIntOr(1, -1);
            int z = pos.getIntOr(2, -1);
            int state = block.getIntOr("state", -1);
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz || state < 0 || state >= globalIds.length) {
                outside++;
                continue;
            }
            int global = globalIds[state];
            if (global == 0) continue;
            int index = (y * sz + z) * sx + x;
            if (cells[index] == 0) total++;
            cells[index] = (short) global;
        }
        if (outside > 0) LitematicReader.addWarning(warnings, outside + " block entries are outside the structure or the palette");
        return new Schematic(fallbackName, fallbackName, root.getStringOr("author", ""), "",
                sx, sy, sz, palette.states(), palette.ids(), cells, total, 1, List.copyOf(warnings));
    }
}
