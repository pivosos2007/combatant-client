/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autobed;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;

public final class AutoBedEntityBlocker {
    public boolean isBlocked(ClientLevel level, BlockPos footPos, BlockPos headPos) {
        if (level == null || footPos == null || headPos == null) return true;

        AABB box = new AABB(footPos).minmax(new AABB(headPos));
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive() || entity.isRemoved() || entity instanceof ExperienceOrb) {
                continue;
            }
            if (entity.getBoundingBox().intersects(box)) {
                return true;
            }
        }
        return false;
    }
}
