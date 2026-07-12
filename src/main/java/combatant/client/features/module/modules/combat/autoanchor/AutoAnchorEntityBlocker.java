/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoanchor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;

public final class AutoAnchorEntityBlocker {
    public boolean isBlocked(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null) return true;

        AABB box = new AABB(pos);
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
