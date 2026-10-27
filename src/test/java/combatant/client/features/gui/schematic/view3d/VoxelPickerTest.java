/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoxelPickerTest {

    private static final int SIZE = 4;

    private static int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    private static VoxelPicker.Solid only(int sx, int sy, int sz) {
        return (x, y, z) -> x == sx && y == sy && z == sz;
    }

    @Test
    void straightDownHitsTheVoxel() {
        int hit = VoxelPicker.pick(SIZE, SIZE, SIZE, 1f, 2.5f, 10f, 3.5f, 0f, -1f, 0f, only(2, 1, 3));
        assertEquals(index(2, 1, 3), hit);
    }

    @Test
    void diagonalRayReturnsNearestOfTwo() {
        VoxelPicker.Solid two = (x, y, z) -> (x == 0 && y == 0 && z == 0) || (x == 3 && y == 3 && z == 3);
        int fromFar = VoxelPicker.pick(SIZE, SIZE, SIZE, 1f, 9f, 9f, 9f, -1f, -1f, -1f, two);
        assertEquals(index(3, 3, 3), fromFar);
        int fromOrigin = VoxelPicker.pick(SIZE, SIZE, SIZE, 1f, -5f, -5f, -5f, 1f, 1f, 1f, two);
        assertEquals(index(0, 0, 0), fromOrigin);
    }

    @Test
    void rayStartingInsideTheGridWalksFromItsOrigin() {
        VoxelPicker.Solid row = (x, y, z) -> y == 1 && z == 1 && x >= 1;
        assertEquals(index(1, 1, 1), VoxelPicker.pick(SIZE, SIZE, SIZE, 1f, 0.5f, 1.5f, 1.5f, 1f, 0f, 0f, row));
        assertEquals(index(3, 1, 1), VoxelPicker.pick(SIZE, SIZE, SIZE, 1f, 3.5f, 1.5f, 1.5f, 1f, 0f, 0f, row));
    }

    @Test
    void missesReturnMinusOne() {
        assertEquals(-1, VoxelPicker.pick(SIZE, SIZE, SIZE, 1f, 2.5f, 10f, 3.5f, 0f, 1f, 0f, only(2, 1, 3)));
        assertEquals(-1, VoxelPicker.pick(SIZE, SIZE, SIZE, 1f, 9f, 9f, 9f, 1f, 0f, 0f, only(2, 1, 3)));
        assertEquals(-1, VoxelPicker.pick(SIZE, SIZE, SIZE, 1f, 1.5f, 10f, 3.5f, 0f, -1f, 0f, only(2, 1, 3)));
    }

    @Test
    void explodedLayersAreHitAtTheirShiftedHeight() {
        // Gap of one block: layer 1 occupies world y [2, 3].
        assertEquals(index(2, 1, 3), VoxelPicker.pick(SIZE, SIZE, SIZE, 2f, 2.5f, 2.5f, -5f, 0f, 0f, 1f, only(2, 1, 3)));
        assertEquals(-1, VoxelPicker.pick(SIZE, SIZE, SIZE, 2f, 2.5f, 1.5f, -5f, 0f, 0f, 1f, only(2, 1, 3)));
        VoxelPicker.Solid column = (x, y, z) -> x == 1 && z == 1;
        assertEquals(index(1, 3, 1), VoxelPicker.pick(SIZE, SIZE, SIZE, 2f, 1.5f, 20f, 1.5f, 0f, -1f, 0f, column));
        assertEquals(index(1, 0, 1), VoxelPicker.pick(SIZE, SIZE, SIZE, 2f, 1.5f, -20f, 1.5f, 0f, 1f, 0f, column));
    }
}
