/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;

@Getter
public class EventPushOutOfBlocks extends Event {
    private final double x;
    private final double z;

    public EventPushOutOfBlocks(double x, double z) {
        this.x = x;
        this.z = z;
    }

}
