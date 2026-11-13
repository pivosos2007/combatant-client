/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Predicts an unglided descent against a translating target box, in blocks and ticks. */
public final class SmashTrajectory {
    private SmashTrajectory() {}

    public static boolean canReach(Vec3 feet, double eyeHeight, double halfWidth, Vec3 velocity,
                                   AABB target, Vec3 targetVelocity, double initialFall, double minimumFall,
                                   double minimumDownSpeed, double minimumApproachSpeed, int ticks,
                                   double maximumHorizontalGap, double reach, double gravity) {
        if (velocity.y > -minimumDownSpeed || feet.y <= target.maxY) return false;
        double dx = Mth.clamp(feet.x, target.minX, target.maxX) - feet.x;
        double dz = Mth.clamp(feet.z, target.minZ, target.maxZ) - feet.z;
        double gap = Math.sqrt(dx * dx + dz * dz);
        boolean overlapping = feet.x + halfWidth >= target.minX && feet.x - halfWidth <= target.maxX
                && feet.z + halfWidth >= target.minZ && feet.z - halfWidth <= target.maxZ;
        if (!overlapping && gap > 0.0
                && ((velocity.x - targetVelocity.x) * dx + (velocity.z - targetVelocity.z) * dz) / gap
                < minimumApproachSpeed) return false;

        Vec3 eye = feet.add(0, eyeHeight, 0);
        double fall = initialFall;
        for (int tick = 0; tick < ticks; tick++) {
            Vec3 next = eye.add(velocity);
            double drop = Math.max(0, -velocity.y);
            if (fall + drop >= minimumFall && velocity.y < targetVelocity.y) {
                double from = drop > 0 ? Mth.clamp((minimumFall - fall) / drop, 0, 1) : 0;
                Vec3 relativeStart = eye.subtract(targetVelocity.scale(tick));
                Vec3 relativeStep = velocity.subtract(targetVelocity);
                double low = from;
                double high = 1;
                // Squared distance to a convex box is convex along each tick's linear segment.
                for (int iteration = 0; iteration < 32; iteration++) {
                    double a = (2 * low + high) / 3;
                    double b = (low + 2 * high) / 3;
                    if (distanceSquared(target, relativeStart.add(relativeStep.scale(a)))
                            <= distanceSquared(target, relativeStart.add(relativeStep.scale(b)))) high = b;
                    else low = a;
                }
                Vec3 contact = relativeStart.add(relativeStep.scale((low + high) / 2));
                double x = contact.x - Mth.clamp(contact.x, target.minX, target.maxX);
                double z = contact.z - Mth.clamp(contact.z, target.minZ, target.maxZ);
                if (x * x + z * z <= maximumHorizontalGap * maximumHorizontalGap
                        && distanceSquared(target, contact) <= reach * reach) return true;
            }
            fall += drop;
            eye = next;
            velocity = new Vec3(velocity.x * 0.91, (velocity.y - gravity) * 0.98, velocity.z * 0.91);
        }
        return false;
    }

    private static double distanceSquared(AABB box, Vec3 point) {
        return point.distanceToSqr(Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY), Mth.clamp(point.z, box.minZ, box.maxZ));
    }
}
