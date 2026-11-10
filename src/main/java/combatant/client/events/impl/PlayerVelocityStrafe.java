/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.phys.Vec3;
import combatant.client.events.Event;

/**
 * Allows overriding the local player's input-to-velocity conversion in
 * {@code Entity.moveRelative}. The supplied velocity is the computed default;
 * movement input, speed and yaw remain unchanged.
 */
@Getter
public class PlayerVelocityStrafe extends Event {

    private final Vec3 movementInput;
    private final float speed;
    private final float yaw;
    @Setter
    private Vec3 velocity;

    public PlayerVelocityStrafe(Vec3 movementInput, float speed, float yaw, Vec3 velocity) {
        this.movementInput = movementInput;
        this.speed = speed;
        this.yaw = yaw;
        this.velocity = velocity;
    }

}
