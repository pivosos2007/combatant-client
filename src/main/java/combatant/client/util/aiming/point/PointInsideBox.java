/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.aiming.point;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Point constrained to a box.
 * Adapted from LiquidBounce (CCBlueX).
 */
public record PointInsideBox(Vec3 pos, AABB box) {

    public PointInsideBox {
        pos = clampToBox(pos, box);
    }

    private static Vec3 clampToBox(Vec3 pos, AABB box) {
        return new Vec3(
                Mth.clamp(pos.x, box.minX, box.maxX),
                Mth.clamp(pos.y, box.minY, box.maxY),
                Mth.clamp(pos.z, box.minZ, box.maxZ)
        );
    }
}
