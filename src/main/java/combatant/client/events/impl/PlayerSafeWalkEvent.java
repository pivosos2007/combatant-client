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
 * Requests edge protection for the local player when the ground-surface query
 * would otherwise return false. Setting {@code safeWalk} to true enables the
 * override; leaving it false preserves the original query result.
 */
@Setter
@Getter
public class PlayerSafeWalkEvent extends Event {
    private boolean safeWalk;

}
