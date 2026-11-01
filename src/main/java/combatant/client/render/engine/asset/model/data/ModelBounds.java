/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are adapted from GFBS-glTF 1.5.1
 * (commit 8906d4083c23b5d5bbf5191a0555d149a5272900).
 * Copyright (c) 2026 LytharaLab. Original portions are licensed under the MIT License.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 *
 * Combatant modifications are distributed under GNU GPL v3.0 as part of Combatant.
 */
package combatant.client.render.engine.asset.model.data;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Immutable axis-aligned bounds used by rendering and collision.
 */
public record ModelBounds(float minX, float minY, float minZ,
                         float maxX, float maxY, float maxZ) {
    public static final ModelBounds EMPTY = new ModelBounds(
        Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
    );

    public ModelBounds {
        float[] values = {minX, minY, minZ, maxX, maxY, maxZ};
        for (float value : values) {
            if (Float.isNaN(value)) throw new IllegalArgumentException("Bounds contain NaN");
        }
    }

    public static ModelBounds ofPositions(float[] positions) {
        if (positions == null || positions.length == 0) return EMPTY;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + 2 < positions.length; i += 3) {
            float x = positions[i];
            float y = positions[i + 1];
            float z = positions[i + 2];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        return new ModelBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean valid() {
        return minX <= maxX && minY <= maxY && minZ <= maxZ;
    }

    public float centerX() { return (minX + maxX) * 0.5f; }
    public float centerY() { return (minY + maxY) * 0.5f; }
    public float centerZ() { return (minZ + maxZ) * 0.5f; }

    public float radius() {
        if (!valid()) return 0.0f;
        float x = (maxX - minX) * 0.5f;
        float y = (maxY - minY) * 0.5f;
        float z = (maxZ - minZ) * 0.5f;
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public ModelBounds union(ModelBounds other) {
        if (other == null || !other.valid()) return this;
        if (!valid()) return other;
        return new ModelBounds(
            Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
            Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ)
        );
    }

    public ModelBounds transform(Matrix4f matrix) {
        if (!valid()) return EMPTY;
        Vector3f point = new Vector3f();
        float outMinX = Float.POSITIVE_INFINITY;
        float outMinY = Float.POSITIVE_INFINITY;
        float outMinZ = Float.POSITIVE_INFINITY;
        float outMaxX = Float.NEGATIVE_INFINITY;
        float outMaxY = Float.NEGATIVE_INFINITY;
        float outMaxZ = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            point.set(
                (corner & 1) == 0 ? minX : maxX,
                (corner & 2) == 0 ? minY : maxY,
                (corner & 4) == 0 ? minZ : maxZ
            );
            matrix.transformPosition(point);
            outMinX = Math.min(outMinX, point.x);
            outMinY = Math.min(outMinY, point.y);
            outMinZ = Math.min(outMinZ, point.z);
            outMaxX = Math.max(outMaxX, point.x);
            outMaxY = Math.max(outMaxY, point.y);
            outMaxZ = Math.max(outMaxZ, point.z);
        }
        return new ModelBounds(outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ);
    }

    public net.minecraft.world.phys.AABB minecraft(double offsetX, double offsetY, double offsetZ) {
        return new net.minecraft.world.phys.AABB(
            minX + offsetX, minY + offsetY, minZ + offsetZ,
            maxX + offsetX, maxY + offsetY, maxZ + offsetZ
        );
    }
}
