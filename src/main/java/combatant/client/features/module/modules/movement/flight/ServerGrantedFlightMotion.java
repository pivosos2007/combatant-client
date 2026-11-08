/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.flight;

import net.minecraft.world.phys.Vec3;

/** Resolves an input-directed velocity without touching capability or packet state. */
public final class ServerGrantedFlightMotion {
    private static final double HORIZONTAL_INERTIA = 0.75;
    private static final double VERTICAL_INERTIA = 0.60;

    private ServerGrantedFlightMotion() { }

    public static Vec3 resolve(Vec3 current, Vec3 desired, boolean noInertia) {
        if (current == null || desired == null) return current;
        if (noInertia) return desired;
        return new Vec3(
                blend(current.x, desired.x, HORIZONTAL_INERTIA),
                blend(current.y, desired.y, VERTICAL_INERTIA),
                blend(current.z, desired.z, HORIZONTAL_INERTIA));
    }

    public static double blend(double previous, double target, double retainedFraction) {
        return previous * retainedFraction + target * (1.0 - retainedFraction);
    }
}
