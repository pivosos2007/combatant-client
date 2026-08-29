/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

/**
 * Longitudinal axis used by compile-time rig deformation coordinates.
 */
public enum RigAxis {
    X,
    Y,
    Z;

    float coordinate(float x, float y, float z) {
        return switch (this) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    float min(RigCuboid cuboid) {
        return switch (this) {
            case X -> cuboid.minX();
            case Y -> cuboid.minY();
            case Z -> cuboid.minZ();
        };
    }

    float max(RigCuboid cuboid) {
        return switch (this) {
            case X -> cuboid.maxX();
            case Y -> cuboid.maxY();
            case Z -> cuboid.maxZ();
        };
    }

    float normalized(RigCuboid cuboid, float x, float y, float z) {
        float min = min(cuboid);
        return (coordinate(x, y, z) - min) / (max(cuboid) - min);
    }

    float lateral(RigCuboid cuboid, float x, float y, float z) {
        return switch (this) {
            case X -> y - (cuboid.minY() + cuboid.maxY()) * 0.5f;
            case Y -> x - (cuboid.minX() + cuboid.maxX()) * 0.5f;
            case Z -> x - (cuboid.minX() + cuboid.maxX()) * 0.5f;
        };
    }

    float depth(RigCuboid cuboid, float x, float y, float z) {
        return switch (this) {
            case X -> z - (cuboid.minZ() + cuboid.maxZ()) * 0.5f;
            case Y -> z - (cuboid.minZ() + cuboid.maxZ()) * 0.5f;
            case Z -> y - (cuboid.minY() + cuboid.maxY()) * 0.5f;
        };
    }
}
