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

    /**
     * Texture coordinate of each corner in units of one block: per direction and corner, the (u, v) pair as 0/1.
     * u runs left to right and v top to bottom as seen from outside the block, matching the vanilla sprite layout
     * (up faces have north at the top).
     */
    private static final byte[] TEXTURE_UV = {
            0, 0, 1, 0, 1, 1, 0, 1,
            0, 1, 0, 0, 1, 0, 1, 1,
            1, 0, 0, 0, 0, 1, 1, 1,
            1, 0, 0, 0, 0, 1, 1, 1,
            1, 0, 0, 0, 0, 1, 1, 1,
            1, 0, 0, 0, 0, 1, 1, 1
    };

    private float[] box = new float[6 * 256];
    private byte[] dir = new byte[256];
    private byte[] cls = new byte[256];
    private int[] color = new int[256];
    private int[] light = new int[256];
    private int[] voxel = new int[256];
    /** Per face: slot in the model-quad arrays below, or -1 for a box side. */
    private int[] quadSlot = new int[256];
    private float[] quadPos = new float[12 * 64];
    private float[] quadUv = new float[8 * 64];
    private float[] quadNormal = new float[3 * 64];
    private float[] quadCentre = new float[3 * 64];
    private int[] quadTint = new int[64];
    private int quadCount;
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
        quadCount = 0;
        lodStep = step;
        truncated = false;
    }

    void markTruncated() {
        truncated = true;
    }

    void add(int direction, byte faceClass, int argb, int lightArgb, int voxelIndex,
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
        light[count] = lightArgb;
        voxel[count] = voxelIndex;
        quadSlot[count] = -1;
        count++;
    }

    /** Adds quad {@code q} of a block model translated to {@code (ox, oy, oz)}. */
    void addQuad(BlockShape shape, int q, byte faceClass, int argb, int lightArgb, int voxelIndex,
                 float ox, float oy, float oz) {
        if (count == dir.length) grow();
        if (quadCount == quadTint.length) growQuads();
        int s = quadCount++;
        float cx = 0f, cy = 0f, cz = 0f;
        for (int c = 0; c < 4; c++) {
            float x = shape.pos[q * 12 + c * 3] + ox, y = shape.pos[q * 12 + c * 3 + 1] + oy, z = shape.pos[q * 12 + c * 3 + 2] + oz;
            quadPos[s * 12 + c * 3] = x;
            quadPos[s * 12 + c * 3 + 1] = y;
            quadPos[s * 12 + c * 3 + 2] = z;
            cx += x;
            cy += y;
            cz += z;
        }
        quadCentre[s * 3] = cx * 0.25f;
        quadCentre[s * 3 + 1] = cy * 0.25f;
        quadCentre[s * 3 + 2] = cz * 0.25f;
        System.arraycopy(shape.uv, q * 8, quadUv, s * 8, 8);
        System.arraycopy(shape.normal, q * 3, quadNormal, s * 3, 3);
        quadTint[s] = shape.tint[q];
        float nx = shape.normal[q * 3], ny = shape.normal[q * 3 + 1], nz = shape.normal[q * 3 + 2];
        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        int d = ay >= ax && ay >= az ? (ny >= 0f ? UP : DOWN) : ax >= az ? (nx >= 0f ? EAST : WEST) : (nz >= 0f ? SOUTH : NORTH);
        dir[count] = (byte) d;
        cls[count] = faceClass;
        color[count] = argb;
        light[count] = lightArgb;
        voxel[count] = voxelIndex;
        quadSlot[count] = s;
        count++;
    }

    private void grow() {
        int next = dir.length * 2;
        box = Arrays.copyOf(box, next * 6);
        dir = Arrays.copyOf(dir, next);
        cls = Arrays.copyOf(cls, next);
        color = Arrays.copyOf(color, next);
        light = Arrays.copyOf(light, next);
        voxel = Arrays.copyOf(voxel, next);
        quadSlot = Arrays.copyOf(quadSlot, next);
    }

    private void growQuads() {
        int next = quadTint.length * 2;
        quadPos = Arrays.copyOf(quadPos, next * 12);
        quadUv = Arrays.copyOf(quadUv, next * 8);
        quadNormal = Arrays.copyOf(quadNormal, next * 3);
        quadCentre = Arrays.copyOf(quadCentre, next * 3);
        quadTint = Arrays.copyOf(quadTint, next);
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

    /** The same shading as {@link #color} computed from white: the multiply colour for a textured face. */
    public int light(int face) {
        return light[face];
    }

    /** Texture u (axis 0) or v (axis 1) of a corner, as 0 or 1 within one block. */
    public static int textureUv(int direction, int corner, int axis) {
        return TEXTURE_UV[direction * 8 + corner * 2 + axis];
    }

    /** Blocks the face spans along texture u (axis 0) or v (axis 1); more than one only for coarse level-of-detail blocks. */
    public float textureSpan(int face, int axis) {
        int d = dir[face];
        int b = face * 6;
        float dx = box[b + 3] - box[b], dy = box[b + 4] - box[b + 1], dz = box[b + 5] - box[b + 2];
        if (d <= DOWN) return axis == 0 ? dx : dz;
        if (d <= WEST) return axis == 0 ? dz : dy;
        return axis == 0 ? dx : dy;
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

    /** One coordinate of a face corner; {@code corner} is 0..3 in the loop order of {@link #CORNERS} (model quads: vertex order). */
    public float corner(int face, int corner, int axis) {
        int slot = quadSlot[face];
        if (slot >= 0) return quadPos[slot * 12 + corner * 3 + axis];
        int pick = CORNERS[dir[face] * 12 + corner * 3 + axis];
        return box[face * 6 + pick * 3 + axis];
    }

    public float centre(int face, int axis) {
        int slot = quadSlot[face];
        if (slot >= 0) return quadCentre[slot * 3 + axis];
        int d = dir[face];
        int n = NORMALS[d * 3 + axis];
        if (n > 0) return box[face * 6 + 3 + axis];
        if (n < 0) return box[face * 6 + axis];
        return (box[face * 6 + axis] + box[face * 6 + 3 + axis]) * 0.5f;
    }

    /** True for a face cut from a block model (any orientation) rather than a box side. */
    public boolean isQuad(int face) {
        return quadSlot[face] >= 0;
    }

    /** Outward normal component of a face; unit length for model quads, an axis for box sides. */
    public float normalOf(int face, int axis) {
        int slot = quadSlot[face];
        return slot >= 0 ? quadNormal[slot * 3 + axis] : NORMALS[dir[face] * 3 + axis];
    }

    /** Atlas u (axis 0) or v (axis 1) of a model quad's vertex. */
    public float quadUv(int face, int corner, int axis) {
        return quadUv[quadSlot[face] * 8 + corner * 2 + axis];
    }

    /** Multiply tint ({@code 0xAARRGGBB}) of a model quad. */
    public int quadTint(int face) {
        return quadTint[quadSlot[face]];
    }

    public static int normal(int direction, int axis) {
        return NORMALS[direction * 3 + axis];
    }
}
