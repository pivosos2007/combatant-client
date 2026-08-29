/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

import java.util.Arrays;

/**
 * Compile-time axis-aligned cuboid. Face UVs are optional so callers can preserve vanilla visible-face masks.
 */
public final class RigCuboid {
    private final float minX;
    private final float minY;
    private final float minZ;
    private final float maxX;
    private final float maxY;
    private final float maxZ;
    private final RigFaceUv[] faceUvs;

    private RigCuboid(float minX, float minY, float minZ,
                      float maxX, float maxY, float maxZ,
                      RigFaceUv[] faceUvs) {
        if (!(maxX > minX) || !(maxY > minY) || !(maxZ > minZ)) {
            throw new IllegalArgumentException("Rig cuboid must have positive size on every axis");
        }
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.faceUvs = faceUvs.clone();
    }

    public static Builder builder(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return new Builder(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public float minX() { return minX; }
    public float minY() { return minY; }
    public float minZ() { return minZ; }
    public float maxX() { return maxX; }
    public float maxY() { return maxY; }
    public float maxZ() { return maxZ; }

    public RigFaceUv faceUv(RigFace face) {
        if (face == null) throw new IllegalArgumentException("Rig face must not be null");
        return faceUvs[face.ordinal()];
    }

    public boolean hasFace(RigFace face) {
        return faceUv(face) != null;
    }

    public static final class Builder {
        private final float minX;
        private final float minY;
        private final float minZ;
        private final float maxX;
        private final float maxY;
        private final float maxZ;
        private final RigFaceUv[] faceUvs = new RigFaceUv[RigFace.values().length];

        private Builder(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public Builder face(RigFace face, RigFaceUv uv) {
            if (face == null) throw new IllegalArgumentException("Rig face must not be null");
            faceUvs[face.ordinal()] = uv;
            return this;
        }

        public Builder allFaces(RigFaceUv uv) {
            if (uv == null) throw new IllegalArgumentException("Rig face UV must not be null");
            Arrays.fill(faceUvs, uv);
            return this;
        }

        public RigCuboid build() {
            boolean hasFace = false;
            for (RigFaceUv uv : faceUvs) {
                if (uv != null) {
                    hasFace = true;
                    break;
                }
            }
            if (!hasFace) throw new IllegalArgumentException("Rig cuboid must contain at least one visible face");
            return new RigCuboid(minX, minY, minZ, maxX, maxY, maxZ, faceUvs);
        }
    }
}
