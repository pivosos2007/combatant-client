/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import lombok.Getter;
import net.minecraft.world.phys.Vec3;
import combatant.client.events.Event;

@Getter
public final class PlayerStepSuccessEvent extends Event {
    private final Vec3 adjustedVec;

    public PlayerStepSuccessEvent(Vec3 adjustedVec) {
        this.adjustedVec = adjustedVec;
    }

}
