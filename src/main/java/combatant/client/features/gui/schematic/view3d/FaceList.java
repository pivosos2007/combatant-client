/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

import java.util.Arrays;

/**
 * Exposed box faces of a schematic as parallel arrays. Every face is an axis-aligned rectangle on one side of a
 * box (one voxel, or a coarse block of voxels when level of detail kicked in), with its colour already shaded.
 */
public final class FaceList {

    public static final int UP = 0;
    public static final int DOWN = 1;
    public static final int EAST = 2;
    public static final int WEST = 3;
    public static final int SOUTH = 4;
    public static final int NORTH = 5;

    /** Outward normal per direction as {x, y, z}. */
    static final int[] NORMALS = {
            0, 1, 0,
            0, -1, 0,
            1, 0, 0,
            -1, 0, 0,
            0, 0, 1,
            0, 0, -1
    };

    /**
     * Corner loop per direction: for each corner whether it takes the max (1) or min (0) of x, y and z. Side
     * faces list their two upper corners first so the renderer can light them differently.
     */
    static final byte[] CORNERS = {
            0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1,
            0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0,
            1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0,
            0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1,
            1, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1,
            0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0
    };

    private float[] box = new float[6 * 256];
    private byte[] dir = new byte[256];
    private byte[] cls = new byte[256];
    private int[] color = new int[256];
    private int[] voxel = new int[256];
    private int count;
    private int lodStep = 1;
    private boolean truncated;

    public int count() {
        return count;
    }

    public int lodStep() {
        return lodStep;
    }

    public boolean truncated() {
        return truncated;
    }

    void reset(int step) {
        count = 0;
        lodStep = step;
        truncated = false;
    }

    void markTruncated() {
        truncated = true;
    }

    void add(int direction, byte faceClass, int argb, int voxelIndex,
             float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (count == dir.length) grow();
        int b = count * 6;
        box[b] = minX;
        box[b + 1] = minY;
        box[b + 2] = minZ;
        box[b + 3] = maxX;
        box[b + 4] = maxY;
        box[b + 5] = maxZ;
        dir[count] = (byte) direction;
        cls[count] = faceClass;
        color[count] = argb;
        voxel[count] = voxelIndex;
        count++;
    }

    private void grow() {
        int next = dir.length * 2;
        box = Arrays.copyOf(box, next * 6);
        dir = Arrays.copyOf(dir, next);
        cls = Arrays.copyOf(cls, next);
        color = Arrays.copyOf(color, next);
        voxel = Arrays.copyOf(voxel, next);
    }

    public int direction(int face) {
        return dir[face];
    }

    public byte faceClass(int face) {
        return cls[face];
    }

    public int color(int face) {
        return color[face];
    }

    /** Schematic index of the voxel (or first voxel of the coarse block) the face belongs to. */
    public int voxel(int face) {
        return voxel[face];
    }

    public float min(int face, int axis) {
        return box[face * 6 + axis];
    }

    public float max(int face, int axis) {
        return box[face * 6 + 3 + axis];
    }

    /** One coordinate of a face corner; {@code corner} is 0..3 in the loop order of {@link #CORNERS}. */
    public float corner(int face, int corner, int axis) {
        int pick = CORNERS[dir[face] * 12 + corner * 3 + axis];
        return box[face * 6 + pick * 3 + axis];
    }

    public float centre(int face, int axis) {
        int d = dir[face];
        int n = NORMALS[d * 3 + axis];
        if (n > 0) return box[face * 6 + 3 + axis];
        if (n < 0) return box[face * 6 + axis];
        return (box[face * 6 + axis] + box[face * 6 + 3 + axis]) * 0.5f;
    }

    public static int normal(int direction, int axis) {
        return NORMALS[direction * 3 + axis];
    }
}
