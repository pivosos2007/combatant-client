/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

/**
 * Per-frame work for the painter's algorithm: drop faces turned away from the camera or outside the viewport,
 * project the rest and order them far to near. Buffers grow with the face list and are reused every frame, and
 * the order comes from a two-pass radix sort over packed {@code depth << 32 | face} keys, so a frame allocates
 * nothing.
 */
public final class FaceFrame {

    private static final int DEPTH_LEVELS = 0xFFFF;

    private float[] screen = new float[0];
    private float[] depth = new float[0];
    private long[] keys = new long[0];
    private long[] scratch = new long[0];
    private final int[] counts = new int[256];
    private int visible;

    /** Number of faces to draw this frame. */
    public int visible() {
        return visible;
    }

    /** Face index of the {@code i}-th face to draw, farthest first. */
    public int face(int i) {
        return (int) keys[i];
    }

    /** Projected corner {@code corner} (0..3) of a face from the last {@link #build}: x at axis 0, y at axis 1. */
    public float screen(int face, int corner, int axis) {
        return screen[face * 8 + corner * 2 + axis];
    }

    public int build(FaceList faces, OrbitCamera camera, float clipX, float clipY, float clipW, float clipH) {
        ensureCapacity(faces.count());
        visible = 0;
        float near = Float.MAX_VALUE, far = 0f;
        for (int f = 0, n = faces.count(); f < n; f++) {
            if (!projectFace(faces, camera, f, clipX, clipY, clipX + clipW, clipY + clipH)) continue;
            float d = depth[f];
            if (d < near) near = d;
            if (d > far) far = d;
            keys[visible++] = f;
        }
        if (visible == 0) return 0;
        float scale = far > near ? DEPTH_LEVELS / (far - near) : 0f;
        for (int i = 0; i < visible; i++) {
            int f = (int) keys[i];
            long q = DEPTH_LEVELS - Math.min(DEPTH_LEVELS, (int) ((depth[f] - near) * scale));
            keys[i] = (q << 32) | f;
        }
        radixSort(visible);
        return visible;
    }

    private boolean projectFace(FaceList faces, OrbitCamera camera, int f, float minX, float minY, float maxX, float maxY) {
        int d = faces.direction(f);
        float cx = faces.centre(f, 0), cy = faces.centre(f, 1), cz = faces.centre(f, 2);
        float ex = camera.eyeX() - cx, ey = camera.eyeY() - cy, ez = camera.eyeZ() - cz;
        if (ex * FaceList.normal(d, 0) + ey * FaceList.normal(d, 1) + ez * FaceList.normal(d, 2) <= 0f) return false;
        int base = f * 8;
        float lowX = Float.MAX_VALUE, lowY = Float.MAX_VALUE, highX = -Float.MAX_VALUE, highY = -Float.MAX_VALUE;
        for (int c = 0; c < 4; c++) {
            if (!camera.project(faces.corner(f, c, 0), faces.corner(f, c, 1), faces.corner(f, c, 2), screen, base + c * 2)) {
                return false;
            }
            float sx = screen[base + c * 2], sy = screen[base + c * 2 + 1];
            lowX = Math.min(lowX, sx);
            lowY = Math.min(lowY, sy);
            highX = Math.max(highX, sx);
            highY = Math.max(highY, sy);
        }
        if (highX < minX || highY < minY || lowX > maxX || lowY > maxY) return false;
        depth[f] = ex * ex + ey * ey + ez * ez;
        return true;
    }

    /** Stable LSD radix sort of the first {@code n} keys on their 16 depth bits. */
    private void radixSort(int n) {
        long[] from = keys, to = scratch;
        for (int shift = 32; shift < 48; shift += 8) {
            java.util.Arrays.fill(counts, 0);
            for (int i = 0; i < n; i++) counts[(int) (from[i] >>> shift) & 0xFF]++;
            int sum = 0;
            for (int b = 0; b < 256; b++) {
                int c = counts[b];
                counts[b] = sum;
                sum += c;
            }
            for (int i = 0; i < n; i++) to[counts[(int) (from[i] >>> shift) & 0xFF]++] = from[i];
            long[] swap = from;
            from = to;
            to = swap;
        }
        // Two passes leave the result back in keys.
    }

    private void ensureCapacity(int faces) {
        if (depth.length >= faces) return;
        int size = Math.max(faces, depth.length * 3 / 2);
        screen = new float[size * 8];
        depth = new float[size];
        keys = new long[size];
        scratch = new long[size];
    }
}
