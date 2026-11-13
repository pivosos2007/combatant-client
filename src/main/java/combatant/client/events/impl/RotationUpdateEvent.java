/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import combatant.client.util.aiming.data.Rotation;
import lombok.Getter;

/**
 * Marks the boundaries of a rotation-manager update: {@link Type#PRE} before
 * pending requests are resolved and {@link Type#POST} afterwards.
 * {@link Type#RESOLVED} exposes the tentative movement heading before
 * the final rotation request is selected in the same update.
 * A null phase passed to the constructor is treated as {@code PRE}.
 */
@Getter
public final class RotationUpdateEvent extends Event {
    private final Type type;
    private final Rotation movementRotation;

    public RotationUpdateEvent(Type type) {
        this(type, null);
    }

    public RotationUpdateEvent(Type type, Rotation movementRotation) {
        this.type = type == null ? Type.PRE : type;
        this.movementRotation = movementRotation;
    }

    public enum Type {
        PRE,
        RESOLVED,
        POST
    }
}
