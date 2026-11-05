/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

import java.util.Arrays;

/**
 * Cacheable screen-space face stream, sorted back-to-front. Coarse groups are rejected against the view frustum
 * before individual faces are projected. Faces intersecting the near plane are clipped rather than disappearing.
 * All buffers are reused while orbiting or panning.
 */
public final class FaceFrame {

    private static final int DEPTH_LEVELS = 0xFFFF;
    private static final int GROUP_SIZE = 64;
    private static final int VERTICES = 6;

    private float[] screen = new float[0];
    /** Model quads: atlas UV; cube faces: normalized tile UV in [0, 1]. */
    private float[] uv = new float[0];
    /** Perspective divisor of every projected vertex; needed when subdividing a merged rectangle. */
    private float[] viewDepth = new float[0];
    private byte[] vertexCounts = new byte[0];
    private boolean[] nearClipped = new boolean[0];
    private float[] screenArea = new float[0];
    private float[] depth = new float[0];
    private long[] keys = new long[0];
    private long[] scratch = new long[0];
    private final int[] counts = new int[256];
    private final float[] input = new float[4 * 5];
    private final float[] clipped = new float[VERTICES * 5];
    private float[] groupBounds = new float[0];
    private FaceList groupFaces;
    private float sceneMinX, sceneMinY, sceneMinZ, sceneMaxX, sceneMaxY, sceneMaxZ;
    private boolean eyeInsideScene;
    private int visible;
    private FaceList builtFaces;
    private int builtRevision = -1;
    private float builtX, builtY, builtW, builtH;

    public int visible() {
        return visible;
    }

    public int face(int i) {
        return (int) keys[i];
    }

    public int vertices(int face) {
        return vertexCounts[face];
    }

    public boolean clipped(int face) {
        return nearClipped[face];
    }

    public float screen(int face, int corner, int axis) {
        return screen[(face * VERTICES + corner) * 2 + axis];
    }

    public float uv(int face, int corner, int axis) {
        return uv[(face * VERTICES + corner) * 2 + axis];
    }

    /** Approximate on-screen polygon area in pixels, for adaptive texture submission. */
    public float screenArea(int face) {
        return screenArea[face];
    }

    public float viewDepth(int face, int corner) {
        return viewDepth[face * VERTICES + corner];
    }

    public int build(FaceList faces, OrbitCamera camera, float clipX, float clipY, float clipW, float clipH) {
        if (faces == builtFaces && camera.revision() == builtRevision
                && clipX == builtX && clipY == builtY && clipW == builtW && clipH == builtH) return visible;
        builtFaces = faces;
        builtRevision = camera.revision();
        builtX = clipX;
        builtY = clipY;
        builtW = clipW;
        builtH = clipH;
        ensureCapacity(faces.count());
        if (groupFaces != faces) buildGroups(faces);
        eyeInsideScene = camera.eyeX() >= sceneMinX && camera.eyeX() <= sceneMaxX
                && camera.eyeY() >= sceneMinY && camera.eyeY() <= sceneMaxY
                && camera.eyeZ() >= sceneMinZ && camera.eyeZ() <= sceneMaxZ;
        visible = 0;
        float near = Float.MAX_VALUE, far = 0f;
        for (int start = 0, g = 0, count = faces.count(); start < count; start += GROUP_SIZE, g++) {
            int bound = g * 6;
            // Near geometry needs a generous box test: strict near-plane group culling
            // otherwise makes whole batches of faces pop when the camera crosses them.
            float pad = 0.25f;
            if (!camera.intersectsBox(groupBounds[bound] - pad, groupBounds[bound + 1] - pad, groupBounds[bound + 2] - pad,
                    groupBounds[bound + 3] + pad, groupBounds[bound + 4] + pad, groupBounds[bound + 5] + pad,
                    clipX, clipY, clipW, clipH)) continue;
            int end = Math.min(start + GROUP_SIZE, count);
            for (int f = start; f < end; f++) {
                if (!projectFace(faces, camera, f, clipX, clipY, clipX + clipW, clipY + clipH)) continue;
                float d = depth[f];
                if (d < near) near = d;
                if (d > far) far = d;
                keys[visible++] = f;
            }
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

    private void buildGroups(FaceList faces) {
        groupFaces = faces;
        int groups = (faces.count() + GROUP_SIZE - 1) / GROUP_SIZE;
        if (groupBounds.length < groups * 6) groupBounds = new float[groups * 6];
        sceneMinX = sceneMinY = sceneMinZ = Float.POSITIVE_INFINITY;
        sceneMaxX = sceneMaxY = sceneMaxZ = Float.NEGATIVE_INFINITY;
        for (int g = 0; g < groups; g++) {
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            for (int f = g * GROUP_SIZE, end = Math.min(f + GROUP_SIZE, faces.count()); f < end; f++) {
                for (int c = 0; c < 4; c++) {
                    float x = faces.corner(f, c, 0), y = faces.corner(f, c, 1), z = faces.corner(f, c, 2);
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }
            int b = g * 6;
            groupBounds[b] = minX;
            groupBounds[b + 1] = minY;
            groupBounds[b + 2] = minZ;
            groupBounds[b + 3] = maxX;
            groupBounds[b + 4] = maxY;
            groupBounds[b + 5] = maxZ;
            sceneMinX = Math.min(sceneMinX, minX);
            sceneMinY = Math.min(sceneMinY, minY);
            sceneMinZ = Math.min(sceneMinZ, minZ);
            sceneMaxX = Math.max(sceneMaxX, maxX);
            sceneMaxY = Math.max(sceneMaxY, maxY);
            sceneMaxZ = Math.max(sceneMaxZ, maxZ);
        }
    }

    private boolean projectFace(FaceList faces, OrbitCamera camera, int f, float minX, float minY, float maxX, float maxY) {
        float cx = faces.centre(f, 0), cy = faces.centre(f, 1), cz = faces.centre(f, 2);
        float ex = camera.eyeX() - cx, ey = camera.eyeY() - cy, ez = camera.eyeZ() - cz;
        // When the camera zooms into the schematic, allow interior views of otherwise culled cube backs.
        if (!eyeInsideScene && faces.cullBack(f)
                && ex * faces.normalOf(f, 0) + ey * faces.normalOf(f, 1) + ez * faces.normalOf(f, 2) < -1.0f) return false;
        int front = 0;
        for (int c = 0; c < 4; c++) {
            int p = c * 5;
            float x = faces.corner(f, c, 0), y = faces.corner(f, c, 1), z = faces.corner(f, c, 2);
            input[p] = x;
            input[p + 1] = y;
            input[p + 2] = z;
            input[p + 3] = faces.isQuad(f) ? faces.quadUv(f, c, 0) : FaceList.textureUv(faces.direction(f), c, 0);
            input[p + 4] = faces.isQuad(f) ? faces.quadUv(f, c, 1) : FaceList.textureUv(faces.direction(f), c, 1);
            if (camera.viewDepth(x, y, z) >= OrbitCamera.NEAR) front++;
        }
        if (front == 0) return false;
        int n = 4;
        float[] polygon = input;
        if (front != 4) {
            n = 0;
            int prev = 3;
            float prevDepth = camera.viewDepth(input[15], input[16], input[17]);
            for (int c = 0; c < 4; c++) {
                int p = c * 5, q = prev * 5;
                float d = camera.viewDepth(input[p], input[p + 1], input[p + 2]);
                boolean inside = d >= OrbitCamera.NEAR, wasInside = prevDepth >= OrbitCamera.NEAR;
                if (inside != wasInside) {
                    float t = (OrbitCamera.NEAR - prevDepth) / (d - prevDepth);
                    for (int axis = 0; axis < 5; axis++)
                        clipped[n * 5 + axis] = input[q + axis] + (input[p + axis] - input[q + axis]) * t;
                    n++;
                }
                if (inside) {
                    System.arraycopy(input, p, clipped, n * 5, 5);
                    n++;
                }
                prev = c;
                prevDepth = d;
            }
            polygon = clipped;
        }
        if (n < 3) return false;
        int base = f * VERTICES * 2;
        float lowX = Float.MAX_VALUE, lowY = Float.MAX_VALUE, highX = -Float.MAX_VALUE, highY = -Float.MAX_VALUE;
        for (int c = 0; c < n; c++) {
            int p = c * 5, off = base + c * 2;
            // Near-plane intersection rounding can put depth a few ulps below NEAR.
            if (!camera.projectClipped(polygon[p], polygon[p + 1], polygon[p + 2], screen, off)) return false;
            viewDepth[f * VERTICES + c] = camera.viewDepth(polygon[p], polygon[p + 1], polygon[p + 2]);
            uv[off] = polygon[p + 3];
            uv[off + 1] = polygon[p + 4];
            float sx = screen[off], sy = screen[off + 1];
            lowX = Math.min(lowX, sx);
            lowY = Math.min(lowY, sy);
            highX = Math.max(highX, sx);
            highY = Math.max(highY, sy);
        }
        if (highX < minX || highY < minY || lowX > maxX || lowY > maxY) return false;
        float twiceArea = 0f;
        for (int c = 0; c < n; c++) {
            int a = base + c * 2, b = base + ((c + 1) % n) * 2;
            twiceArea += screen[a] * screen[b + 1] - screen[b] * screen[a + 1];
        }
        screenArea[f] = Math.min(Math.abs(twiceArea) * 0.5f,
                Math.min(highX - lowX, maxX - minX) * Math.min(highY - lowY, maxY - minY));
        vertexCounts[f] = (byte) n;
        nearClipped[f] = front != 4;
        depth[f] = ex * ex + ey * ey + ez * ez;
        return true;
    }

    private void radixSort(int n) {
        long[] from = keys, to = scratch;
        for (int shift = 32; shift < 48; shift += 8) {
            Arrays.fill(counts, 0);
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
    }

    private void ensureCapacity(int faces) {
        if (depth.length >= faces) return;
        int size = Math.max(faces, depth.length * 3 / 2 + 1);
        screen = new float[size * VERTICES * 2];
        uv = new float[size * VERTICES * 2];
        viewDepth = new float[size * VERTICES];
        vertexCounts = new byte[size];
        nearClipped = new boolean[size];
        screenArea = new float[size];
        depth = new float[size];
        keys = new long[size];
        scratch = new long[size];
    }
}
