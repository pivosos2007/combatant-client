/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.Setter;

/**
 * Requests additional player ticks after the normal position send.
 * Only cancellation activates the extra-tick path; nonpositive {@code iterations}
 * cancels without additional ticks. A reentrancy guard prevents nested requests.
 */
@Setter
@Getter
public class PostPlayerUpdateEvent extends Event {
    private int iterations;

}
