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
package combatant.client.render.engine.asset.model.animation;

import java.util.Objects;

public record AnimationChannel(int node, AnimationPath path, AnimationSampler sampler) {
    public AnimationChannel {
        if (node < 0) throw new IllegalArgumentException("Animation channel node index must be non-negative");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(sampler, "sampler");
        int expected = path.components();
        if (expected > 0 && sampler.components() != expected) {
            throw new IllegalArgumentException(path + " animation channels require " + expected + " components");
        }
    }
}
