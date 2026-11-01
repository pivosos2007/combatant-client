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

public enum AnimationPath {
    TRANSLATION(3), ROTATION(4), SCALE(3), WEIGHTS(-1);

    private final int components;
    AnimationPath(int components) { this.components = components; }
    public int components() { return components; }

    public static AnimationPath fromGltf(String path) {
        return switch (path) {
            case "translation" -> TRANSLATION;
            case "rotation" -> ROTATION;
            case "scale" -> SCALE;
            case "weights" -> WEIGHTS;
            default -> throw new IllegalArgumentException("Unsupported animation path: " + path);
        };
    }
}
