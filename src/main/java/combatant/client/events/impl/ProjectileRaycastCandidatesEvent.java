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

import java.util.List;

/** Candidate entities returned by the world query before projectile hit testing. */
@Getter
public final class ProjectileRaycastCandidatesEvent extends Event {
    @Setter
    private List<Entity> entities;

    public ProjectileRaycastCandidatesEvent(List<Entity> entities) {
        this.entities = entities;
    }
}
