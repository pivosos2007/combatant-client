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
 * Overrides the local player's maximum upward step height returned by
 * {@code LivingEntity.maxUpStep} for the current query.
 */
@Setter
@Getter
public final class PlayerStepEvent extends Event {
    private float height;

    public PlayerStepEvent(float height) {
        this.height = height;
    }

}
