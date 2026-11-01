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
package combatant.client.render.engine.asset.model.geometry;

import combatant.client.render.engine.asset.model.data.ModelPrimitive;
import combatant.client.render.engine.asset.model.data.ModelPrimitiveAccess;
import combatant.client.render.engine.asset.model.data.MorphTarget;
import combatant.client.render.engine.asset.model.data.MorphTargetAccess;

/** Backend-neutral CPU morph and skin transforms for imported geometry. */
public final class ModelVertexTransforms {
    private static final float EPSILON = 1.0e-8f;

    private ModelVertexTransforms() {}

    public static PreparedGeometry prepare(ModelPrimitive primitive, float[] morphWeights) {
        float[] positions = ModelPrimitiveAccess.positions(primitive).clone();
        float[] sourceNormals = ModelPrimitiveAccess.normals(primitive);
        float[] sourceTangents = ModelPrimitiveAccess.tangents(primitive);
        float[] normals = sourceNormals == null ? null : sourceNormals.clone();
        float[] tangents = sourceTangents == null ? null : sourceTangents.clone();
        if (hasActiveMorph(primitive, morphWeights)) {
            applyMorphs(primitive, morphWeights, positions, normals, tangents);
        }
        return new PreparedGeometry(positions, normals, tangents);
    }

    public static boolean hasActiveMorph(ModelPrimitive primitive, float[] weights) {
        if (primitive.morphTargets().isEmpty() || weights == null) return false;
        int count = Math.min(weights.length, primitive.morphTargets().size());
        for (int i = 0; i < count; i++) if (weights[i] != 0.0f) return true;
        return false;
    }

    public static void applyMorphs(ModelPrimitive primitive, float[] weights,
                                   float[] positions, float[] normals, float[] tangents) {
        for (int targetIndex = 0; targetIndex < primitive.morphTargets().size(); targetIndex++) {
            float weight = weights != null && targetIndex < weights.length ? weights[targetIndex] : 0.0f;
            if (weight == 0.0f) continue;
            MorphTarget target = primitive.morphTargets().get(targetIndex);
            addWeighted(positions, MorphTargetAccess.positions(target), weight);
            addWeighted(normals, MorphTargetAccess.normals(target), weight);
            addWeightedTangent(tangents, MorphTargetAccess.tangents(target), weight);
        }
        normalizeTriples(normals, 3);
        normalizeTriples(tangents, 4);
    }

    public static float[] faceNormal(float[] positions, int a, int b, int c) {
        float[] result = new float[3];
        faceNormal(positions, a, b, c, result);
        return result;
    }

    public static void faceNormal(float[] positions, int a, int b, int c, float[] output) {
        if (output == null || output.length < 3) throw new IllegalArgumentException("Normal output requires three values");
        int first = a * 3;
        int second = b * 3;
        int third = c * 3;
        float abX = positions[second] - positions[first];
        float abY = positions[second + 1] - positions[first + 1];
        float abZ = positions[second + 2] - positions[first + 2];
        float acX = positions[third] - positions[first];
        float acY = positions[third + 1] - positions[first + 1];
        float acZ = positions[third + 2] - positions[first + 2];
        float x = abY * acZ - abZ * acY;
        float y = abZ * acX - abX * acZ;
        float z = abX * acY - abY * acX;
        double lengthSquared = (double) x * x + (double) y * y + (double) z * z;
        if (!Double.isFinite(lengthSquared) || lengthSquared <= 1.0e-16) {
            output[0] = 0.0f;
            output[1] = 1.0f;
            output[2] = 0.0f;
            return;
        }
        float inverseLength = (float) (1.0 / Math.sqrt(lengthSquared));
        output[0] = x * inverseLength;
        output[1] = y * inverseLength;
        output[2] = z * inverseLength;
    }

    public static void skinVertex(int vertex, float x, float y, float z,
                                  float nx, float ny, float nz, int[] joints,
                                  float[] weights, float[] palette, int jointCount,
                                  float[] output) {
        if (output.length < 6) throw new IllegalArgumentException("Skin output requires six values");
        if (jointCount <= 0) {
            put(output, x, y, z, nx, ny, nz);
            return;
        }
        float px = 0.0f, py = 0.0f, pz = 0.0f, totalWeight = 0.0f;
        float tx = 0.0f, ty = 0.0f, tz = 0.0f;
        int offset = vertex * 4;
        for (int i = 0; i < 4; i++) {
            float weight = Math.max(0.0f, weights[offset + i]);
            int joint = Math.max(0, Math.min(jointCount - 1, joints[offset + i]));
            int matrix = joint * 16;
            px += (palette[matrix] * x + palette[matrix + 4] * y + palette[matrix + 8] * z + palette[matrix + 12]) * weight;
            py += (palette[matrix + 1] * x + palette[matrix + 5] * y + palette[matrix + 9] * z + palette[matrix + 13]) * weight;
            pz += (palette[matrix + 2] * x + palette[matrix + 6] * y + palette[matrix + 10] * z + palette[matrix + 14]) * weight;
            totalWeight += weight;
            tx += (palette[matrix] * nx + palette[matrix + 4] * ny + palette[matrix + 8] * nz) * weight;
            ty += (palette[matrix + 1] * nx + palette[matrix + 5] * ny + palette[matrix + 9] * nz) * weight;
            tz += (palette[matrix + 2] * nx + palette[matrix + 6] * ny + palette[matrix + 10] * nz) * weight;
        }
        if (totalWeight <= EPSILON) {
            put(output, x, y, z, nx, ny, nz);
            return;
        }
        float inverseWeight = 1.0f / totalWeight;
        float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (length > EPSILON) {
            tx /= length;
            ty /= length;
            tz /= length;
        } else {
            tx = nx;
            ty = ny;
            tz = nz;
        }
        put(output, px * inverseWeight, py * inverseWeight, pz * inverseWeight, tx, ty, tz);
    }

    private static void put(float[] output, float x, float y, float z, float nx, float ny, float nz) {
        output[0] = x; output[1] = y; output[2] = z;
        output[3] = nx; output[4] = ny; output[5] = nz;
    }

    private static void addWeighted(float[] base, float[] delta, float weight) {
        if (base == null || delta == null) return;
        for (int i = 0; i < Math.min(base.length, delta.length); i++) base[i] += delta[i] * weight;
    }

    private static void addWeightedTangent(float[] base, float[] delta, float weight) {
        if (base == null || delta == null) return;
        int vertices = Math.min(base.length / 4, delta.length / 3);
        for (int vertex = 0; vertex < vertices; vertex++) {
            int baseOffset = vertex * 4;
            int deltaOffset = vertex * 3;
            base[baseOffset] += delta[deltaOffset] * weight;
            base[baseOffset + 1] += delta[deltaOffset + 1] * weight;
            base[baseOffset + 2] += delta[deltaOffset + 2] * weight;
        }
    }

    private static void normalizeTriples(float[] values, int stride) {
        if (values == null) return;
        for (int i = 0; i + 2 < values.length; i += stride) {
            float length = (float) Math.sqrt(values[i] * values[i] + values[i + 1] * values[i + 1] + values[i + 2] * values[i + 2]);
            if (length > EPSILON) {
                values[i] /= length;
                values[i + 1] /= length;
                values[i + 2] /= length;
            }
        }
    }

    public record PreparedGeometry(float[] positions, float[] normals, float[] tangents) {}
}
