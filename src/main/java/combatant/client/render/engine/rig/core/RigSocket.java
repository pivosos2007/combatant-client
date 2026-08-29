/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.core;

/**
 * Immutable attachment point resolved against a solved bone model matrix.
 */
public record RigSocket(String name, int boneIndex, RigTransform local) {
    public RigSocket {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Socket name must not be blank");
        if (boneIndex < 0) throw new IllegalArgumentException("Socket bone index must be >= 0");
        local = local != null ? local : RigTransform.identity();
    }
}
