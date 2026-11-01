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

import java.util.List;
import java.util.Objects;

public record ModelMesh(String name, List<ModelPrimitive> primitives, float[] defaultMorphWeights) {
    public ModelMesh {
        name = name == null ? "" : name;
        primitives = List.copyOf(Objects.requireNonNull(primitives, "primitives"));
        if (primitives.isEmpty()) throw new IllegalArgumentException("A model mesh must contain at least one primitive");
        defaultMorphWeights = defaultMorphWeights == null ? null : defaultMorphWeights.clone();
        if (defaultMorphWeights != null) {
            for (float value : defaultMorphWeights) {
                if (!Float.isFinite(value)) throw new IllegalArgumentException("Morph weight is not finite");
            }
        }
    }

    @Override
    public float[] defaultMorphWeights() {
        return defaultMorphWeights == null ? null : defaultMorphWeights.clone();
    }

    /** Package-private zero-copy view used by the renderer hot path. */
    float[] defaultMorphWeightsView() { return defaultMorphWeights; }
}
