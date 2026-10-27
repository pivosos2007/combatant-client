/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

/** Read-only dense box of palette indices, y-major like {@code Schematic}; 0 is air. */
public interface VoxelVolume {

    int sizeX();

    int sizeY();

    int sizeZ();

    int cell(int index);

    default int index(int x, int y, int z) {
        return (y * sizeZ() + z) * sizeX() + x;
    }

    static VoxelVolume of(int sizeX, int sizeY, int sizeZ, int[] cells) {
        if (cells.length != sizeX * sizeY * sizeZ) throw new IllegalArgumentException("cells do not match the size");
        return new VoxelVolume() {
            @Override
            public int sizeX() {
                return sizeX;
            }

            @Override
            public int sizeY() {
                return sizeY;
            }

            @Override
            public int sizeZ() {
                return sizeZ;
            }

            @Override
            public int cell(int index) {
                return cells[index];
            }
        };
    }
}
