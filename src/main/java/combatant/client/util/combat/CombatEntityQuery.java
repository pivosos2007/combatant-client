/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Shared spatial entity queries for placement planners. */
public final class CombatEntityQuery {
    private CombatEntityQuery() {
    }

    public static List<Entity> blockingEntities(ClientLevel level, AABB box) {
        if (level == null || box == null) return List.of();
        return level.getEntities((Entity) null, box, CombatEntityQuery::blocksPlacement);
    }

    public static boolean isBlocked(ClientLevel level, AABB box) {
        return !blockingEntities(level, box).isEmpty();
    }

    public static boolean blocksPlacement(Entity entity) {
        return entity != null
                && entity.isAlive()
                && !entity.isRemoved()
                && !(entity instanceof ExperienceOrb);
    }
}
