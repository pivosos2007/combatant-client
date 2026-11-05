/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playermodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * One-time source-mesh normal validation for OBJ player assets. Some imported
 * triangulated faces have authored vertex normals pointing away from the actual
 * surface, leaving alternating dark/light triangles under entity lighting.
 *
 * Rebuild a reliable area-weighted face baseline, then smooth only neighbouring
 * near-coplanar faces at the same position. Hard edges (>=45 degrees) and seams
 * at unrelated positions are preserved. This runs only during model compilation.
 */
final class PlayerModelNormalRepair {
    private static final float COS_CREASE = 0.70710678f;
    private static final float COS_BAD_NORMAL = 0.82f;

    private record Position(int x, int y, int z) { }

    private PlayerModelNormalRepair() { }

    static float[] repair(float[] positions, float[] input, int[] indices) {
        int count = positions.length / 3;
        if (input == null || input.length != positions.length || indices == null || indices.length < 3)
            return input;
        float[] baseline = new float[input.length];
        Map<Position, ArrayList<Integer>> neighbours = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int o = i * 3;
            Position position = new Position(Float.floatToIntBits(positions[o]),
                    Float.floatToIntBits(positions[o + 1]), Float.floatToIntBits(positions[o + 2]));
            neighbours.computeIfAbsent(position, ignored -> new ArrayList<>(4)).add(i);
        }
        for (int t = 0; t + 2 < indices.length; t += 3) {
            int a = indices[t], b = indices[t + 1], c = indices[t + 2];
            if (a < 0 || b < 0 || c < 0 || a >= count || b >= count || c >= count) continue;
            int ai = a * 3, bi = b * 3, ci = c * 3;
            float abx = positions[bi] - positions[ai], aby = positions[bi + 1] - positions[ai + 1], abz = positions[bi + 2] - positions[ai + 2];
            float acx = positions[ci] - positions[ai], acy = positions[ci + 1] - positions[ai + 1], acz = positions[ci + 2] - positions[ai + 2];
            float nx = aby * acz - abz * acy, ny = abz * acx - abx * acz, nz = abx * acy - aby * acx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!(length > 1.0e-12f) || !Float.isFinite(length)) continue;
            for (int index : new int[]{ai, bi, ci}) {
                baseline[index] += nx;
                baseline[index + 1] += ny;
                baseline[index + 2] += nz;
            }
        }
        for (int i = 0; i < baseline.length; i += 3) normalize(baseline, i);
        float[] output = input.clone();
        for (ArrayList<Integer> nearby : neighbours.values()) {
            // A position shared by very many disconnected surfaces should not be
            // blurred together; leave such exceptional topology unchanged.
            if (nearby.size() > 32) continue;
            for (int vertex : nearby) {
                int o = vertex * 3;
                float rx = baseline[o], ry = baseline[o + 1], rz = baseline[o + 2];
                if (rx == 0 && ry == 0 && rz == 0) continue;
                float x = 0, y = 0, z = 0;
                for (int other : nearby) {
                    int j = other * 3;
                    float dot = rx * baseline[j] + ry * baseline[j + 1] + rz * baseline[j + 2];
                    if (dot < COS_CREASE) continue;
                    x += baseline[j]; y += baseline[j + 1]; z += baseline[j + 2];
                }
                float mag = (float) Math.sqrt(x * x + y * y + z * z);
                if (!(mag > 0.00001f)) continue;
                x /= mag; y /= mag; z /= mag;
                float agreement = input[o] * x + input[o + 1] * y + input[o + 2] * z;
                if (!(agreement >= COS_BAD_NORMAL)) {
                    // The authored normal is strongly inconsistent with the actual
                    // triangle surface. Prefer the local geometric normal instead.
                    output[o] = x; output[o + 1] = y; output[o + 2] = z;
                }
            }
        }
        return output;
    }

    private static void normalize(float[] values, int o) {
        float x = values[o], y = values[o + 1], z = values[o + 2];
        float mag = (float) Math.sqrt(x * x + y * y + z * z);
        if (mag > 1e-10f) {
            values[o] /= mag; values[o + 1] /= mag; values[o + 2] /= mag;
        }
    }
}
