/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A parsed schematic as one dense box. Cells are stored y-major ({@code (y * sizeZ + z) * sizeX + x}) so a
 * layer is one contiguous slice, which is what the layer view, projection and builder all iterate.
 */
public final class Schematic {

    private final String name;
    private final String fileName;
    private final String author;
    private final String description;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BlockState[] palette;
    private final String[] paletteIds;
    private final short[] blocks;
    private final int totalBlocks;
    private final int regionCount;
    private final List<String> warnings;

    public Schematic(String name, String fileName, String author, String description,
                     int sizeX, int sizeY, int sizeZ,
                     BlockState[] palette, String[] paletteIds, short[] blocks,
                     int totalBlocks, int regionCount, List<String> warnings) {
        this.name = name == null || name.isBlank() ? fileName : name;
        this.fileName = fileName;
        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.paletteIds = paletteIds;
        this.blocks = blocks;
        this.totalBlocks = totalBlocks;
        this.regionCount = regionCount;
        this.warnings = warnings == null ? List.of() : warnings;
    }

    public String name() {
        return name;
    }

    public String fileName() {
        return fileName;
    }

    public String author() {
        return author;
    }

    public String description() {
        return description;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int layerArea() {
        return sizeX * sizeZ;
    }

    public int volume() {
        return blocks.length;
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public int regionCount() {
        return regionCount;
    }

    public List<String> warnings() {
        return warnings;
    }

    public int paletteSize() {
        return palette.length;
    }

    public BlockState paletteState(int index) {
        return palette[index];
    }

    public String paletteId(int index) {
        return paletteIds[index];
    }

    public boolean contains(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    public int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    public int xOf(int index) {
        return index % sizeX;
    }

    public int zOf(int index) {
        return (index / sizeX) % sizeZ;
    }

    public int yOf(int index) {
        return index / (sizeX * sizeZ);
    }

    public int paletteIndexAt(int index) {
        return blocks[index] & 0xFFFF;
    }

    public int paletteIndex(int x, int y, int z) {
        return blocks[index(x, y, z)] & 0xFFFF;
    }

    public BlockState state(int x, int y, int z) {
        return palette[paletteIndex(x, y, z)];
    }
}
