/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.geometry;

import combatant.client.render.engine.asset.model.data.ModelPrimitive;

import java.util.Objects;

/** CPU preparation only. GPU upload is deliberately delegated to Combatant's GL/Vulkan RHI. */
public final class ModelGeometryCompiler {
    private static final float EPSILON = 1.0e-12f;

    private ModelGeometryCompiler() {}

    public static ModelCompiledPrimitive compile(ModelPrimitive primitive, int normalTexCoordSet) {
        Objects.requireNonNull(primitive, "primitive");
        int[] indices = ModelTriangleIndices.build(primitive);
        float[] positions = primitive.positions();
        float[] normals = primitive.normals();
        boolean generatedNormals = normals == null;
        if (generatedNormals) normals = generateNormals(positions, indices, primitive.vertexCount());

        float[] tangents = primitive.tangents();
        boolean generatedTangents = false;
        float[] selectedUv = normalTexCoordSet == 1 ? primitive.texCoords1() : primitive.texCoords0();
        if (tangents == null && selectedUv != null) {
            tangents = generateTangents(positions, normals, selectedUv, indices, primitive.vertexCount());
            generatedTangents = tangents != null;
        }

        return new ModelCompiledPrimitive(
                primitive.material(),
                primitive.vertexCount(),
                indices,
                positions,
                normals,
                tangents,
                primitive.texCoords0(),
                primitive.texCoords1(),
                primitive.colors(),
                primitive.joints(),
                primitive.weights(),
                primitive.bounds(),
                generatedNormals,
                generatedTangents
        );
    }

    private static float[] generateNormals(float[] positions, int[] indices, int vertexCount) {
        float[] normals = new float[vertexCount * 3];
        for (int i = 0; i + 2 < indices.length; i += 3) {
            int a = indices[i], b = indices[i + 1], c = indices[i + 2];
            int ao = a * 3, bo = b * 3, co = c * 3;
            float abx = positions[bo] - positions[ao];
            float aby = positions[bo + 1] - positions[ao + 1];
            float abz = positions[bo + 2] - positions[ao + 2];
            float acx = positions[co] - positions[ao];
            float acy = positions[co + 1] - positions[ao + 1];
            float acz = positions[co + 2] - positions[ao + 2];
            float nx = aby * acz - abz * acy;
            float ny = abz * acx - abx * acz;
            float nz = abx * acy - aby * acx;
            accumulate3(normals, ao, nx, ny, nz);
            accumulate3(normals, bo, nx, ny, nz);
            accumulate3(normals, co, nx, ny, nz);
        }
        normalize3(normals, vertexCount, 0.0f, 1.0f, 0.0f);
        return normals;
    }

    /** Standard MikkTSpace-compatible tangent basis construction shape; handedness is stored in W. */
    private static float[] generateTangents(float[] positions, float[] normals, float[] uv,
                                            int[] indices, int vertexCount) {
        float[] tan1 = new float[vertexCount * 3];
        float[] tan2 = new float[vertexCount * 3];
        boolean contributed = false;
        for (int i = 0; i + 2 < indices.length; i += 3) {
            int a = indices[i], b = indices[i + 1], c = indices[i + 2];
            int ap = a * 3, bp = b * 3, cp = c * 3;
            int au = a * 2, bu = b * 2, cu = c * 2;
            float x1 = positions[bp] - positions[ap];
            float y1 = positions[bp + 1] - positions[ap + 1];
            float z1 = positions[bp + 2] - positions[ap + 2];
            float x2 = positions[cp] - positions[ap];
            float y2 = positions[cp + 1] - positions[ap + 1];
            float z2 = positions[cp + 2] - positions[ap + 2];
            float s1 = uv[bu] - uv[au];
            float t1 = uv[bu + 1] - uv[au + 1];
            float s2 = uv[cu] - uv[au];
            float t2 = uv[cu + 1] - uv[au + 1];
            float determinant = s1 * t2 - s2 * t1;
            if (!Float.isFinite(determinant) || Math.abs(determinant) <= EPSILON) continue;
            float r = 1.0f / determinant;
            float sx = (t2 * x1 - t1 * x2) * r;
            float sy = (t2 * y1 - t1 * y2) * r;
            float sz = (t2 * z1 - t1 * z2) * r;
            float tx = (s1 * x2 - s2 * x1) * r;
            float ty = (s1 * y2 - s2 * y1) * r;
            float tz = (s1 * z2 - s2 * z1) * r;
            accumulate3(tan1, ap, sx, sy, sz); accumulate3(tan1, bp, sx, sy, sz); accumulate3(tan1, cp, sx, sy, sz);
            accumulate3(tan2, ap, tx, ty, tz); accumulate3(tan2, bp, tx, ty, tz); accumulate3(tan2, cp, tx, ty, tz);
            contributed = true;
        }
        if (!contributed) return null;

        float[] tangents = new float[vertexCount * 4];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int n = vertex * 3;
            int t = vertex * 4;
            float nx = normals[n], ny = normals[n + 1], nz = normals[n + 2];
            float tx = tan1[n], ty = tan1[n + 1], tz = tan1[n + 2];
            float dot = nx * tx + ny * ty + nz * tz;
            tx -= nx * dot; ty -= ny * dot; tz -= nz * dot;
            float len2 = tx * tx + ty * ty + tz * tz;
            if (!Float.isFinite(len2) || len2 <= EPSILON) {
                float ax = Math.abs(nx) < 0.9f ? 1.0f : 0.0f;
                float ay = Math.abs(nx) < 0.9f ? 0.0f : 1.0f;
                tx = ay * nz;
                ty = -ax * nz;
                tz = ax * ny - ay * nx;
                len2 = tx * tx + ty * ty + tz * tz;
            }
            float inv = len2 > EPSILON ? (float) (1.0 / Math.sqrt(len2)) : 1.0f;
            tx *= inv; ty *= inv; tz *= inv;
            float bx = ny * tz - nz * ty;
            float by = nz * tx - nx * tz;
            float bz = nx * ty - ny * tx;
            float handed = (bx * tan2[n] + by * tan2[n + 1] + bz * tan2[n + 2]) < 0.0f ? -1.0f : 1.0f;
            tangents[t] = tx; tangents[t + 1] = ty; tangents[t + 2] = tz; tangents[t + 3] = handed;
        }
        return tangents;
    }

    private static void accumulate3(float[] values, int offset, float x, float y, float z) {
        values[offset] += x; values[offset + 1] += y; values[offset + 2] += z;
    }

    private static void normalize3(float[] values, int count, float fallbackX, float fallbackY, float fallbackZ) {
        for (int i = 0; i < count; i++) {
            int o = i * 3;
            float x = values[o], y = values[o + 1], z = values[o + 2];
            float len2 = x * x + y * y + z * z;
            if (!Float.isFinite(len2) || len2 <= EPSILON) {
                values[o] = fallbackX; values[o + 1] = fallbackY; values[o + 2] = fallbackZ;
                continue;
            }
            float inv = (float) (1.0 / Math.sqrt(len2));
            values[o] = x * inv; values[o + 1] = y * inv; values[o + 2] = z * inv;
        }
    }
}
