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

package combatant.client.util.projectile;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public enum ProjectileTargetPointFinder {
    ;

    public static Vec3 findHittablePosition(
            Vec3 playerHeadPosition,
            Vec3 directionOnImpact,
            Vec3 entityPositionOnImpact,
            AABB targetEntityBox
    ) {
        Vec3 virtualEyes = playerHeadPosition.add(
                0.0,
                directionOnImpact.y * -(playerHeadPosition.distanceTo(entityPositionOnImpact)),
                0.0
        );
        return PointFinding.findVisiblePointFromVirtualEye(virtualEyes, targetEntityBox, 5.0);
    }
}
