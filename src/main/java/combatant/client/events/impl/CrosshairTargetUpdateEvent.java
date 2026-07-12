/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import combatant.client.events.Event;

@Getter
public final class CrosshairTargetUpdateEvent extends Event {
    private final float tickDelta;
    @Setter
    private HitResult hitResult;
    @Setter
    private Entity targetedEntity;

    public CrosshairTargetUpdateEvent(float tickDelta, HitResult hitResult, Entity targetedEntity) {
        this.tickDelta = tickDelta;
        this.hitResult = hitResult;
        this.targetedEntity = targetedEntity;
    }

}
