/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceMesherTest {

    private static final int[] COLORS = {0, 0xFF808080, 0xFF40A040};

    private static VoxelVolume solid(int sx, int sy, int sz) {
        int[] cells = new int[sx * sy * sz];
        Arrays.fill(cells, 1);
        return VoxelVolume.of(sx, sy, sz, cells);
    }

    private static int countDirection(FaceList faces, int direction) {
        int n = 0;
        for (int f = 0; f < faces.count(); f++) if (faces.direction(f) == direction) n++;
        return n;
    }

    @Test
    void singleVoxelHasSixFaces() {
        FaceList faces = FaceMesher.build(solid(1, 1, 1), COLORS, ViewMode.ALL, 0, 0f, 1000);
        assertEquals(6, faces.count());
        for (int d = 0; d < 6; d++) assertEquals(1, countDirection(faces, d));
    }

    @Test
    void cubeOfOneClassKeepsOnlyItsShell() {
        // Selected layer outside the box: every voxel shares the OTHER class, so only the 24 outer faces remain.
        FaceList faces = FaceMesher.build(solid(2, 2, 2), COLORS, ViewMode.ALL, -1, 0f, 1000);
        assertEquals(24, faces.count());
    }

    @Test
    void classBoundaryExposesFacesBetweenLayers() {
        FaceList faces = FaceMesher.build(solid(2, 2, 2), COLORS, ViewMode.ALL, 0, 0f, 1000);
        assertEquals(32, faces.count());
        assertEquals(8, countDirection(faces, FaceList.UP));
        assertEquals(8, countDirection(faces, FaceList.DOWN));
    }

    @Test
    void layerOnlyDrawsTheSelectedSlab() {
        FaceList faces = FaceMesher.build(solid(2, 3, 2), COLORS, ViewMode.LAYER_ONLY, 1, 0f, 1000);
        assertEquals(16, faces.count());
        for (int f = 0; f < faces.count(); f++) {
            assertEquals(ViewMode.SELECTED, faces.faceClass(f));
            assertEquals(1f, faces.min(f, 1));
            assertEquals(2f, faces.max(f, 1));
        }
    }

    @Test
    void explodedLayersAreSeparatedByTheGap() {
        FaceList faces = FaceMesher.build(solid(1, 3, 1), COLORS, ViewMode.EXPLODED, 2, 0.5f, 1000);
        assertEquals(18, faces.count());
        float topOfTop = 0f;
        for (int f = 0; f < faces.count(); f++) topOfTop = Math.max(topOfTop, faces.max(f, 1));
        assertEquals(2f * 1.5f + 1f, topOfTop, 1.0e-6f);
    }

    @Test
    void airGapsAndCavitiesAreCulledCorrectly() {
        // 3x1x1 row with the middle cell empty: two separate voxels, 12 faces.
        FaceList faces = FaceMesher.build(VoxelVolume.of(3, 1, 1, new int[]{1, 0, 2}), COLORS, ViewMode.ALL, 0, 0f, 1000);
        assertEquals(12, faces.count());
        assertEquals(0, faces.voxel(0));
    }

    @Test
    void levelOfDetailCoarsensLayersAwayFromTheSelection() {
        FaceList full = FaceMesher.build(solid(16, 16, 16), COLORS, ViewMode.ALL, -1, 0f, 5000);
        assertEquals(1536, full.count());
        assertEquals(1, full.lodStep());
        // Band [0, 2) stays at full size (640 faces); layers 2..15 become 2x2x2 blocks (352 faces).
        FaceList coarse = FaceMesher.build(solid(16, 16, 16), COLORS, ViewMode.ALL, -1, 0f, 1000);
        assertEquals(2, coarse.lodStep());
        assertEquals(992, coarse.count());
        assertFalse(coarse.truncated());
    }

    @Test
    void hopelessBudgetTruncatesAtTheCap() {
        FaceList faces = FaceMesher.build(solid(16, 16, 16), COLORS, ViewMode.ALL, 8, 0f, 10);
        assertTrue(faces.truncated());
        assertEquals(10, faces.count());
    }
}
