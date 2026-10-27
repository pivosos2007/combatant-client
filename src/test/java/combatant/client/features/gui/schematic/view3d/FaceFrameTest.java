/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceFrameTest {

    private static OrbitCamera cameraFor(float cx, float cy, float cz, float radius) {
        OrbitCamera camera = new OrbitCamera();
        camera.frame(cx, cy, cz, radius);
        camera.reset();
        camera.snap();
        camera.computeView(0f, 0f, 800f, 600f);
        return camera;
    }

    private static float distanceSq(FaceList faces, OrbitCamera camera, int face) {
        float dx = faces.centre(face, 0) - camera.eyeX();
        float dy = faces.centre(face, 1) - camera.eyeY();
        float dz = faces.centre(face, 2) - camera.eyeZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @Test
    void facesAreOrderedFarToNear() {
        int[] cells = new int[6 * 4 * 5];
        for (int i = 0; i < cells.length; i++) cells[i] = (i * 7 + i / 3) % 3 == 0 ? 0 : 1;
        FaceList faces = FaceMesher.build(VoxelVolume.of(6, 4, 5, cells), new int[]{0, 0xFF808080}, ViewMode.ALL, 1, 0f, 10_000);
        OrbitCamera camera = cameraFor(3f, 2f, 2.5f, 4.5f);
        FaceFrame frame = new FaceFrame();
        int visible = frame.build(faces, camera, 0f, 0f, 800f, 600f);
        assertTrue(visible > 0);
        assertTrue(visible < faces.count(), "back faces are dropped");
        // Quantisation to 16 bits allows near-ties to swap; anything larger is a sorting bug.
        float tolerance = 1.0e-3f * distanceSq(faces, camera, frame.face(0));
        for (int i = 1; i < visible; i++) {
            float previous = distanceSq(faces, camera, frame.face(i - 1));
            float current = distanceSq(faces, camera, frame.face(i));
            assertTrue(current <= previous + tolerance, "face " + i + " is farther than the one before it");
        }
    }

    @Test
    void cubeShowsAtMostThreeSidesAndTopIsLast() {
        FaceList faces = FaceMesher.build(VoxelVolume.of(1, 1, 1, new int[]{1}), new int[]{0, 0xFF808080}, ViewMode.ALL, 0, 0f, 100);
        OrbitCamera camera = cameraFor(0.5f, 0.5f, 0.5f, 0.87f);
        FaceFrame frame = new FaceFrame();
        assertEquals(3, frame.build(faces, camera, 0f, 0f, 800f, 600f));
        boolean sawTop = false;
        for (int i = 0; i < 3; i++) sawTop |= faces.direction(frame.face(i)) == FaceList.UP;
        assertTrue(sawTop);
    }

    @Test
    void facesOutsideTheClipRectAreSkipped() {
        FaceList faces = FaceMesher.build(VoxelVolume.of(1, 1, 1, new int[]{1}), new int[]{0, 0xFF808080}, ViewMode.ALL, 0, 0f, 100);
        OrbitCamera camera = cameraFor(0.5f, 0.5f, 0.5f, 0.87f);
        assertEquals(0, new FaceFrame().build(faces, camera, 700f, 0f, 50f, 50f));
    }
}
