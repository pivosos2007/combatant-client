/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

/**
 * Cuboid face with a stable outward winding. Parameters s/t address the UV quad corners
 * A(0,0), B(1,0), C(1,1), D(0,1).
 */
public enum RigFace {
    NEG_X(-1f, 0f, 0f),
    POS_X(1f, 0f, 0f),
    NEG_Y(0f, -1f, 0f),
    POS_Y(0f, 1f, 0f),
    NEG_Z(0f, 0f, -1f),
    POS_Z(0f, 0f, 1f);

    private final float normalX;
    private final float normalY;
    private final float normalZ;

    RigFace(float normalX, float normalY, float normalZ) {
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
    }

    public float normalX() {
        return normalX;
    }

    public float normalY() {
        return normalY;
    }

    public float normalZ() {
        return normalZ;
    }

    float x(RigCuboid c, float s, float t) {
        return switch (this) {
            case NEG_X -> c.minX();
            case POS_X -> c.maxX();
            case NEG_Y -> lerp(c.minX(), c.maxX(), s);
            case POS_Y -> lerp(c.minX(), c.maxX(), s);
            case NEG_Z -> lerp(c.maxX(), c.minX(), s);
            case POS_Z -> lerp(c.minX(), c.maxX(), s);
        };
    }

    float y(RigCuboid c, float s, float t) {
        return switch (this) {
            case NEG_X, POS_X, NEG_Z, POS_Z -> lerp(c.minY(), c.maxY(), t);
            case NEG_Y -> c.minY();
            case POS_Y -> c.maxY();
        };
    }

    float z(RigCuboid c, float s, float t) {
        return switch (this) {
            case NEG_X -> lerp(c.minZ(), c.maxZ(), s);
            case POS_X -> lerp(c.maxZ(), c.minZ(), s);
            case NEG_Y -> lerp(c.minZ(), c.maxZ(), t);
            case POS_Y -> lerp(c.maxZ(), c.minZ(), t);
            case NEG_Z -> c.minZ();
            case POS_Z -> c.maxZ();
        };
    }

    boolean spansOnS(RigCuboid cuboid, RigAxis axis) {
        float a = axis.coordinate(x(cuboid, 0f, 0f), y(cuboid, 0f, 0f), z(cuboid, 0f, 0f));
        float b = axis.coordinate(x(cuboid, 1f, 0f), y(cuboid, 1f, 0f), z(cuboid, 1f, 0f));
        return Float.compare(a, b) != 0;
    }

    boolean spansOnT(RigCuboid cuboid, RigAxis axis) {
        float a = axis.coordinate(x(cuboid, 0f, 0f), y(cuboid, 0f, 0f), z(cuboid, 0f, 0f));
        float d = axis.coordinate(x(cuboid, 0f, 1f), y(cuboid, 0f, 1f), z(cuboid, 0f, 1f));
        return Float.compare(a, d) != 0;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
