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
 * Rotation-update phases: {@link Type#PRE} accepts normal requests; {@link Type#RESOLVED} exposes a
 * provisional movement direction for emergency requests; {@link Type#POST} follows request resolution.
 * The manager publishes the movement direction with {@link Type#RESOLVED}. A null phase is treated as {@code PRE}.
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
