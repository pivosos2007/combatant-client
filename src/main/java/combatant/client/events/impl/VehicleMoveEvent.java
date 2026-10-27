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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Posted from {@code Entity.move} for the vehicle the local player is steering.
 * {@link #getMovement()} may be replaced; when it aliases the vehicle's delta movement the hook also
 * writes the replacement back so the next tick continues from it.
 */
@Getter
public class VehicleMoveEvent extends Event {
    private final Entity vehicle;
    private final MoverType type;
    private final boolean aliasesVelocity;
    @Setter
    private Vec3 movement;

    public VehicleMoveEvent(Entity vehicle, MoverType type, Vec3 movement) {
        this.vehicle = vehicle;
        this.type = type;
        this.movement = movement;
        this.aliasesVelocity = movement == vehicle.getDeltaMovement();
    }
}
