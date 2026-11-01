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

import java.util.Objects;

public record ModelSkin(String name, int skeletonRoot, int[] joints, float[] inverseBindMatrices) {
    /** Import-time safety ceiling only. Actual GPU skinning capacity is backend/RHI policy. */
    public static final int MAX_JOINTS = 65_535;
    public ModelSkin {
        name = name == null ? "" : name;
        joints = Objects.requireNonNull(joints, "joints").clone();
        inverseBindMatrices = Objects.requireNonNull(inverseBindMatrices, "inverseBindMatrices").clone();
        if (skeletonRoot < -1) throw new IllegalArgumentException("Invalid skeleton root");
        if (joints.length == 0) throw new IllegalArgumentException("A skin must contain at least one joint");
        java.util.HashSet<Integer> uniqueJoints = new java.util.HashSet<>();
        for (int joint : joints) {
            if (joint < 0) throw new IllegalArgumentException("Joint indices must be non-negative");
            if (!uniqueJoints.add(joint)) throw new IllegalArgumentException("A skin cannot contain duplicate joints");
        }
        if (joints.length > MAX_JOINTS) {
            throw new IllegalArgumentException("Combatant importer accepts at most " + MAX_JOINTS + " joints per skin");
        }
        int expectedMatrices;
        try {
            expectedMatrices = Math.multiplyExact(joints.length, 16);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Skin is too large", overflow);
        }
        if (inverseBindMatrices.length != expectedMatrices) {
            throw new IllegalArgumentException("Inverse bind matrix count does not match joints");
        }
        for (float value : inverseBindMatrices) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Inverse bind matrix contains a non-finite value");
        }
    }

    @Override
    public int[] joints() { return joints.clone(); }

    @Override
    public float[] inverseBindMatrices() { return inverseBindMatrices.clone(); }

    /** Package-private zero-copy views used by the renderer hot path. */
    int[] jointsView() { return joints; }
    float[] inverseBindMatricesView() { return inverseBindMatrices; }
}
