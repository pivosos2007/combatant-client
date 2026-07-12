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

package combatant.client.util.block.scaffold;

import net.minecraft.world.phys.Vec3;

public record ScaffoldLine(Vec3 position, Vec3 direction) {

    public ScaffoldLine(Vec3 position, Vec3 direction) {
        if (direction == null || direction.lengthSqr() < 1.0E-7) {
            throw new IllegalArgumentException("Direction must not be zero.");
        }
        this.position = position;
        this.direction = direction.normalize();
    }

    public static ScaffoldLine fromPoints(Vec3 begin, Vec3 end) {
        return new ScaffoldLine(begin, end.subtract(begin));
    }

    public Vec3 getNearestPointTo(Vec3 point) {
        Vec3 delta = point.subtract(position);
        double t = delta.dot(direction);
        return position.add(direction.scale(t));
    }

    public double squaredDistanceTo(Vec3 point) {
        return getNearestPointTo(point).distanceToSqr(point);
    }
}
