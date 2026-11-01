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

public record MorphTarget(float[] positions, float[] normals, float[] tangents) {
    public MorphTarget {
        positions = positions == null ? null : positions.clone();
        normals = normals == null ? null : normals.clone();
        tangents = tangents == null ? null : tangents.clone();
        if (positions == null && normals == null && tangents == null) {
            throw new IllegalArgumentException("A morph target must contain at least one attribute");
        }
        requireFinite(positions, "position");
        requireFinite(normals, "normal");
        requireFinite(tangents, "tangent");
    }

    @Override public float[] positions() { return positions == null ? null : positions.clone(); }
    @Override public float[] normals() { return normals == null ? null : normals.clone(); }
    @Override public float[] tangents() { return tangents == null ? null : tangents.clone(); }

    float[] positionsView() { return positions; }
    float[] normalsView() { return normals; }
    float[] tangentsView() { return tangents; }

    private static void requireFinite(float[] values, String label) {
        if (values == null) return;
        for (float value : values) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Morph " + label + " is not finite");
        }
    }
}
