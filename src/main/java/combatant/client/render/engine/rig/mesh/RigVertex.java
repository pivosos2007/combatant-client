/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

/**
 * CPU-side rig vertex with primitive fields matching the packed rig VertexFormat to avoid JOML object churn.
 */
public final class RigVertex {
    public static final int UNUSED_BONE = -1;

    private final float x;
    private final float y;
    private final float z;
    private final float u;
    private final float v;
    private final float normalX;
    private final float normalY;
    private final float normalZ;
    private final int colorArgb;

    private final int bone0;
    private final int bone1;
    private final int bone2;
    private final int bone3;
    private final float weight0;
    private final float weight1;
    private final float weight2;
    private final float weight3;

    private final float deformU;
    private final float deformLateral;
    private final float deformDepth;
    private final float deformAux;
    private final int deformId;
    private final int deformFlags;

    public RigVertex(float x, float y, float z,
                     float u, float v,
                     float normalX, float normalY, float normalZ,
                     int bone0, int bone1, int bone2, int bone3,
                     float weight0, float weight1, float weight2, float weight3,
                     float deformU, float deformLateral, float deformDepth, float deformAux,
                     int deformId, int deformFlags) {
        this(x, y, z, u, v, normalX, normalY, normalZ, 0xFFFFFFFF,
                bone0, bone1, bone2, bone3,
                weight0, weight1, weight2, weight3,
                deformU, deformLateral, deformDepth, deformAux, deformId, deformFlags);
    }

    public RigVertex(float x, float y, float z,
                     float u, float v,
                     float normalX, float normalY, float normalZ, int colorArgb,
                     int bone0, int bone1, int bone2, int bone3,
                     float weight0, float weight1, float weight2, float weight3,
                     float deformU, float deformLateral, float deformDepth, float deformAux,
                     int deformId, int deformFlags) {
        if (bone0 < 0) throw new IllegalArgumentException("Rig vertex must have a primary bone");
        if (bone1 < -1 || bone2 < -1 || bone3 < -1) throw new IllegalArgumentException("Unused rig bone slots must be -1");
        validateWeight(weight0, "weight0");
        validateWeight(weight1, "weight1");
        validateWeight(weight2, "weight2");
        validateWeight(weight3, "weight3");
        if (bone1 == UNUSED_BONE && weight1 > 0f
                || bone2 == UNUSED_BONE && weight2 > 0f
                || bone3 == UNUSED_BONE && weight3 > 0f) {
            throw new IllegalArgumentException("Unused rig bone slots cannot carry non-zero weight");
        }
        float sum = weight0 + weight1 + weight2 + weight3;
        if (!(sum > 0f) || !Float.isFinite(sum)) throw new IllegalArgumentException("Rig vertex weights must have a finite positive sum");
        float inv = 1f / sum;

        this.x = x;
        this.y = y;
        this.z = z;
        this.u = u;
        this.v = v;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
        this.colorArgb = colorArgb;
        this.bone0 = bone0;
        this.bone1 = bone1;
        this.bone2 = bone2;
        this.bone3 = bone3;
        this.weight0 = weight0 * inv;
        this.weight1 = weight1 * inv;
        this.weight2 = weight2 * inv;
        this.weight3 = weight3 * inv;
        this.deformU = deformU;
        this.deformLateral = deformLateral;
        this.deformDepth = deformDepth;
        this.deformAux = deformAux;
        this.deformId = deformId;
        this.deformFlags = deformFlags;
    }

    private static void validateWeight(float weight, String name) {
        if (!Float.isFinite(weight) || weight < 0f) {
            throw new IllegalArgumentException("Rig vertex " + name + " must be finite and >= 0: " + weight);
        }
    }

    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
    public float u() { return u; }
    public float v() { return v; }
    public float normalX() { return normalX; }
    public float normalY() { return normalY; }
    public float normalZ() { return normalZ; }
    public int colorArgb() { return colorArgb; }
    public int bone0() { return bone0; }
    public int bone1() { return bone1; }
    public int bone2() { return bone2; }
    public int bone3() { return bone3; }
    public float weight0() { return weight0; }
    public float weight1() { return weight1; }
    public float weight2() { return weight2; }
    public float weight3() { return weight3; }
    public float deformU() { return deformU; }
    public float deformLateral() { return deformLateral; }
    public float deformDepth() { return deformDepth; }
    public float deformAux() { return deformAux; }
    public int deformId() { return deformId; }
    public int deformFlags() { return deformFlags; }
}
