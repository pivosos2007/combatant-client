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

/**
 * Reports a successful upward step after entity movement has completed.
 * {@code adjustedVec} is the displacement between the captured positions;
 * the single-argument constructor uses zero vectors for absent positions.
 */
@Getter
public final class PlayerStepSuccessEvent extends Event {
    private final Vec3 adjustedVec;
    private final Vec3 beforePos;
    private final Vec3 afterPos;

    public PlayerStepSuccessEvent(Vec3 adjustedVec) {
        this(adjustedVec, Vec3.ZERO, Vec3.ZERO);
    }

    public PlayerStepSuccessEvent(Vec3 adjustedVec, Vec3 beforePos, Vec3 afterPos) {
        this.adjustedVec = adjustedVec;
        this.beforePos = beforePos;
        this.afterPos = afterPos;
    }

}
