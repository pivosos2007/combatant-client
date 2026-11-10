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
 * Requests cancellation of the local player's push-out-of-blocks operation.
 * The coordinates identify the query position; they cannot be changed by listeners.
 */
@Getter
public class EventPushOutOfBlocks extends Event {
    private final double x;
    private final double z;

    public EventPushOutOfBlocks(double x, double z) {
        this.x = x;
        this.z = z;
    }

}
