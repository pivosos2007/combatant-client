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

/** Reads vanilla structure-block {@code .nbt} files: a {@code size} list, a {@code palette} and a sparse {@code blocks} list. */
public final class StructureNbtReader {

    private static final String STRUCTURE_VOID = "minecraft:structure_void";

    private StructureNbtReader() {
    }

    public static Schematic parse(CompoundTag root, String fallbackName, LitematicReader.PaletteResolver resolver)
            throws IOException {
        ListTag size = root.getListOrEmpty("size");
        int sizeX = size.getIntOr(0, 0);
        int sizeY = size.getIntOr(1, 0);
        int sizeZ = size.getIntOr(2, 0);
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) throw new IOException("Not a structure file: missing size");
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > LitematicReader.MAX_VOLUME) throw new IOException("Schematic too large: " + sizeX + "x" + sizeY + "x" + sizeZ);

        ListTag paletteList = root.getList("palette").orElseGet(() -> root.getListOrEmpty("palettes").getListOrEmpty(0));
        if (paletteList.isEmpty()) throw new IOException("Structure file has an empty palette");

        List<String> warnings = new ArrayList<>();
        LitematicReader.PaletteBuilder palette = new LitematicReader.PaletteBuilder(resolver, warnings);
        int[] globalIds = new int[paletteList.size()];
        for (int i = 0; i < globalIds.length; i++) {
            CompoundTag entry = paletteList.getCompoundOrEmpty(i);
            globalIds[i] = STRUCTURE_VOID.equals(entry.getStringOr("Name", "")) ? 0 : palette.indexOf(entry);
        }

        short[] blocks = new short[(int) volume];
        int total = 0;
        int skipped = 0;
        ListTag entries = root.getListOrEmpty("blocks");
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag block = entries.getCompoundOrEmpty(i);
            ListTag pos = block.getListOrEmpty("pos");
            int x = pos.getIntOr(0, -1);
            int y = pos.getIntOr(1, -1);
            int z = pos.getIntOr(2, -1);
            int state = block.getIntOr("state", -1);
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ || state < 0 || state >= globalIds.length) {
                skipped++;
                continue;
            }
            int global = globalIds[state];
            if (global != 0) {
                blocks[(y * sizeZ + z) * sizeX + x] = (short) global;
                total++;
            }
        }
        if (skipped > 0) LitematicReader.addWarning(warnings, skipped + " blocks outside the structure box or palette");

        return new Schematic(fallbackName, fallbackName, root.getStringOr("author", ""), "",
                sizeX, sizeY, sizeZ, palette.states(), palette.ids(), blocks, total, 1, List.copyOf(warnings));
    }
}
