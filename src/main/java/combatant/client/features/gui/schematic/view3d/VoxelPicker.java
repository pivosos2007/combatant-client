/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

/**
 * Ray against a voxel grid with a grid walk (Amanatides and Woo), so a hover test costs one step per crossed cell
 * instead of one test per block. Exploded layers are walked slab by slab in the order the ray meets them.
 */
public final class VoxelPicker {

    @FunctionalInterface
    public interface Solid {
        boolean test(int x, int y, int z);
    }

    private static final float EPSILON = 1.0e-4f;

    private VoxelPicker() {
    }

    /**
     * First solid cell hit by the ray, as a y-major index ({@code (y * sizeZ + z) * sizeX + x}), or -1. The
     * direction does not need to be normalised. {@code layerPitch} is the world height of one layer plus its gap.
     */
    public static int pick(int sizeX, int sizeY, int sizeZ, float layerPitch,
                           float ox, float oy, float oz, float dx, float dy, float dz, Solid solid) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) return -1;
        if (layerPitch <= 1f + EPSILON) return walk(sizeX, sizeZ, 0, sizeY, ox, oy, oz, dx, dy, dz, solid);
        int first = dy < 0f ? sizeY - 1 : 0;
        int stepLayer = dy < 0f ? -1 : 1;
        for (int y = first; y >= 0 && y < sizeY; y += stepLayer) {
            // Shift the ray so this slab sits at its unexploded height and reuse the plain walk inside it.
            float shiftedY = oy - y * (layerPitch - 1f);
            int hit = walk(sizeX, sizeZ, y, y + 1, ox, shiftedY, oz, dx, dy, dz, solid);
            if (hit >= 0) return hit;
        }
        return -1;
    }

    private static int walk(int sizeX, int sizeZ, int yLow, int yHigh,
                            float ox, float oy, float oz, float dx, float dy, float dz, Solid solid) {
        float enter = Math.max(Math.max(entry(ox, dx, 0f, sizeX), entry(oy, dy, yLow, yHigh)),
                Math.max(0f, entry(oz, dz, 0f, sizeZ)));
        float exit = Math.min(exit(ox, dx, 0f, sizeX), Math.min(exit(oy, dy, yLow, yHigh), exit(oz, dz, 0f, sizeZ)));
        if (enter > exit) return -1;
        float t = enter + EPSILON;
        int x = clampCell(ox + dx * t, 0, sizeX - 1);
        int y = clampCell(oy + dy * t, yLow, yHigh - 1);
        int z = clampCell(oz + dz * t, 0, sizeZ - 1);
        int stepX = dx > 0f ? 1 : -1, stepY = dy > 0f ? 1 : -1, stepZ = dz > 0f ? 1 : -1;
        float deltaX = dx == 0f ? Float.MAX_VALUE : Math.abs(1f / dx);
        float deltaY = dy == 0f ? Float.MAX_VALUE : Math.abs(1f / dy);
        float deltaZ = dz == 0f ? Float.MAX_VALUE : Math.abs(1f / dz);
        float maxX = boundary(ox, dx, x, t), maxY = boundary(oy, dy, y, t), maxZ = boundary(oz, dz, z, t);
        int guard = sizeX + (yHigh - yLow) + sizeZ + 3;
        while (guard-- > 0) {
            if (solid.test(x, y, z)) return (y * sizeZ + z) * sizeX + x;
            if (maxX <= maxY && maxX <= maxZ) {
                x += stepX;
                maxX += deltaX;
                if (x < 0 || x >= sizeX) return -1;
            } else if (maxY <= maxZ) {
                y += stepY;
                maxY += deltaY;
                if (y < yLow || y >= yHigh) return -1;
            } else {
                z += stepZ;
                maxZ += deltaZ;
                if (z < 0 || z >= sizeZ) return -1;
            }
        }
        return -1;
    }

    /** Ray parameter at which the walk next crosses a cell boundary on one axis. */
    private static float boundary(float origin, float dir, int cell, float t) {
        if (dir == 0f) return Float.MAX_VALUE;
        float edge = dir > 0f ? cell + 1 : cell;
        return Math.max(t, (edge - origin) / dir);
    }

    /** Ray parameter where the ray enters the slab [min, max] on one axis; +inf when it never does. */
    private static float entry(float origin, float dir, float min, float max) {
        if (dir == 0f) return origin >= min && origin <= max ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        return Math.min((min - origin) / dir, (max - origin) / dir);
    }

    private static float exit(float origin, float dir, float min, float max) {
        if (dir == 0f) return origin >= min && origin <= max ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        return Math.max((min - origin) / dir, (max - origin) / dir);
    }

    private static int clampCell(float value, int min, int max) {
        return Math.max(min, Math.min(max, (int) Math.floor(value)));
    }
}
