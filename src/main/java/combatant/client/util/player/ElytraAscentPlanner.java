/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Finds a collision-clear horizontal departure when the ascent column is obstructed. */
public final class ElytraAscentPlanner {
    private static final double[] DEPARTURE_DISTANCES = {2.0, 3.5, 5.0, 7.0};
    private static final double[] DEPARTURE_ANGLES = {0, 45, -45, 90, -90, 135, -135, 180};

    private ElytraAscentPlanner() {
    }

    public record Ascent(boolean verticalClear, Vec3 departure) {
    }

    /** Departure is a horizontal displacement in world coordinates, not a steering position. */
    public static Ascent plan(ElytraEscapePlanner.CollisionWorld world, AABB bounds,
                              Vec3 preference, double rise, Vec3 intendedTravel) {
        double height = Math.max(2.0, Math.min(9.0, rise));
        Vec3 ascent = new Vec3(0.0, height, 0.0);
        if (clear(world, bounds, ascent) && clear(world, bounds, intendedTravel)) {
            return new Ascent(true, null);
        }
        Vec3 forward = new Vec3(preference.x, 0.0, preference.z);
        if (forward.lengthSqr() < 1.0E-5) forward = new Vec3(0.0, 0.0, 1.0);
        forward = forward.normalize();
        for (double distance : DEPARTURE_DISTANCES) {
            for (double degrees : DEPARTURE_ANGLES) {
                double radians = Math.toRadians(degrees);
                double sin = Math.sin(radians);
                double cos = Math.cos(radians);
                Vec3 departure = new Vec3(
                        (forward.x * cos - forward.z * sin) * distance, 0.0,
                        (forward.x * sin + forward.z * cos) * distance);
                if (clear(world, bounds, departure)
                        && clear(world, bounds.move(departure), ascent)) {
                    return new Ascent(false, departure);
                }
            }
        }
        return new Ascent(false, null);
    }

    public static boolean clear(ElytraEscapePlanner.CollisionWorld world, AABB bounds, Vec3 movement) {
        if (!world.loaded(bounds, movement)) return false;
        Vec3 clipped = world.move(bounds, movement);
        return clipped != null && clipped.distanceToSqr(movement) < 0.01;
    }
}
