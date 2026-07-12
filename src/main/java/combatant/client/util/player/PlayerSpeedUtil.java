/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Speed helpers for BPS calculations.
 * кулькулятор
 */
public enum PlayerSpeedUtil {
    ;

    /**
     * Returns absolute speed in blocks per second for the provided entity (XYZ magnitude).
     */
    public static float getBps(Entity entity) {
        if (entity == null) return 0f;
        Entity base = entity.getVehicle() != null ? entity.getVehicle() : entity;
        Vec3 vel = base.getDeltaMovement();
        double vy = base.onGround() ? 0.0 : vel.y; // ignore tiny gravity jitter when grounded
        double speed = Math.sqrt(vel.x * vel.x + vy * vy + vel.z * vel.z);
        return (float) (speed * 20.0);
    }
}
