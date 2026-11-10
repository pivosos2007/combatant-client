/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;

/**
 * Marks the boundaries of a rotation-manager update: {@link Type#PRE} before
 * pending requests are resolved and {@link Type#POST} afterwards.
 * A null phase passed to the constructor is treated as {@code PRE}.
 */
@Getter
public final class RotationUpdateEvent extends Event {
    private final Type type;

    public RotationUpdateEvent(Type type) {
        this.type = type == null ? Type.PRE : type;
    }

    public enum Type {
        PRE,
        POST
    }
}
