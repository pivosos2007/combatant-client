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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounding box used for one entity in a projectile hit test. {@code maxDistanceSq} is squared. */
@Getter
public final class ProjectileRaycastBoxEvent extends Event {
    private final Entity target;
    private final Vec3 start;
    private final double maxDistanceSq;
    @Setter
    private AABB box;

    public ProjectileRaycastBoxEvent(Entity target, Vec3 start, double maxDistanceSq, AABB box) {
        this.target = target;
        this.start = start;
        this.maxDistanceSq = maxDistanceSq;
        this.box = box;
    }
}
