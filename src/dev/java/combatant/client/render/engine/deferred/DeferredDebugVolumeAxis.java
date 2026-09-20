/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/** Axis used by native 3D-resource debug slicing. */
public enum DeferredDebugVolumeAxis {
    X(0), Y(1), Z(2);

    private final int shaderId;

    DeferredDebugVolumeAxis(int shaderId) {
        this.shaderId = shaderId;
    }

    int shaderId() {
        return shaderId;
    }
}
