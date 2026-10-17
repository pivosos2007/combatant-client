/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitCameraTest {

    private static OrbitCamera camera() {
        OrbitCamera camera = new OrbitCamera();
        camera.frame(4f, 2f, 3f, 5f);
        camera.reset();
        camera.snap();
        camera.computeView(100f, 50f, 800f, 600f);
        return camera;
    }

    @Test
    void centreProjectsToViewportCentre() {
        OrbitCamera camera = camera();
        float[] out = new float[2];
        assertTrue(camera.project(4f, 2f, 3f, out, 0));
        assertEquals(500f, out[0], 1.0e-3f);
        assertEquals(350f, out[1], 1.0e-3f);
    }

    @Test
    void rayThroughProjectedPointPassesThroughIt() {
        OrbitCamera camera = camera();
        float[] screen = new float[2];
        float[] ray = new float[6];
        float[][] points = {{0f, 0f, 0f}, {8f, 4f, 6f}, {1.5f, 3.25f, 5.5f}, {7f, 0f, 1f}};
        for (float[] p : points) {
            assertTrue(camera.project(p[0], p[1], p[2], screen, 0));
            camera.ray(screen[0], screen[1], ray);
            float vx = p[0] - ray[0], vy = p[1] - ray[1], vz = p[2] - ray[2];
            float along = vx * ray[3] + vy * ray[4] + vz * ray[5];
            float cx = vx - ray[3] * along, cy = vy - ray[4] * along, cz = vz - ray[5] * along;
            assertTrue(along > 0f);
            assertEquals(0f, (float) Math.sqrt(cx * cx + cy * cy + cz * cz), 1.0e-3f);
            assertEquals(1f, ray[3] * ray[3] + ray[4] * ray[4] + ray[5] * ray[5], 1.0e-4f);
        }
    }

    @Test
    void viewMatrixMatchesProjection() {
        OrbitCamera camera = camera();
        float[] m = new float[16];
        camera.viewMatrix(m);
        float[] eye = {camera.eyeX(), camera.eyeY(), camera.eyeZ()};
        // The eye maps to the view-space origin and the target sits straight ahead.
        assertEquals(0f, m[0] * eye[0] + m[4] * eye[1] + m[8] * eye[2] + m[12], 1.0e-3f);
        assertEquals(0f, m[2] * eye[0] + m[6] * eye[1] + m[10] * eye[2] + m[14], 1.0e-3f);
        float depth = m[2] * 4f + m[6] * 2f + m[10] * 3f + m[14];
        assertEquals(camera.distance(), depth, 1.0e-3f);
        assertEquals(0f, m[0] * 4f + m[4] * 2f + m[8] * 3f + m[12], 1.0e-3f);
    }

    @Test
    void pointsBehindTheEyeDoNotProject() {
        OrbitCamera camera = camera();
        float bx = camera.eyeX() + (camera.eyeX() - 4f), by = camera.eyeY() + (camera.eyeY() - 2f), bz = camera.eyeZ() + (camera.eyeZ() - 3f);
        assertFalse(camera.project(bx, by, bz, new float[2], 0));
    }

    @Test
    void easingConvergesAndZoomIsClamped() {
        OrbitCamera camera = camera();
        float start = camera.distance();
        camera.zoom(200f);
        for (int i = 0; i < 200; i++) camera.update(0.016f, false);
        assertTrue(camera.settled());
        assertTrue(camera.distance() < start);
        assertEquals(5f * 0.35f, camera.distance(), 1.0e-3f);
        camera.orbit(0f, 10_000f);
        for (int i = 0; i < 200; i++) camera.update(0.016f, false);
        assertTrue(camera.pitch() <= 1.5f + 1.0e-4f);
    }
}
