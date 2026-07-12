/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public enum AimUtil {
    ;

    public static float[] getYawPitch(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double dx = d.x;
        double dy = d.y;
        double dz = d.z;

        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / Math.PI) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, distXZ) * 180.0F / Math.PI);

        return new float[]{yaw, pitch};
    }

    /**
     * Backward-compatible alias.
     */
    public static float approach(float cur, float target, float maxDelta) {
        return approachAngle(cur, target, maxDelta);
    }

    public static float approachAngle(float cur, float target, float maxDelta) {
        float delta = Mth.wrapDegrees(target - cur);
        if (delta > maxDelta) delta = maxDelta;
        if (delta < -maxDelta) delta = -maxDelta;
        return cur + delta;
    }

    /**
     * Closest point on (or in) AABB to a point.
     */
    public static Vec3 closestPointToBox(Vec3 p, AABB b) {
        double x = Mth.clamp(p.x, b.minX, b.maxX);
        double y = Mth.clamp(p.y, b.minY, b.maxY);
        double z = Mth.clamp(p.z, b.minZ, b.maxZ);
        return new Vec3(x, y, z);
    }

    // Slab method. Returns t (distance multiplier along dir) to first intersection, or NaN if no hit.
    public static double rayAabbT(Vec3 origin, Vec3 dir, AABB box, double maxT) {
        double tMin = 0.0;
        double tMax = maxT;

        // X
        if (Math.abs(dir.x) < 1e-9) {
            if (origin.x < box.minX || origin.x > box.maxX) return Double.NaN;
        } else {
            double inv = 1.0 / dir.x;
            double t1 = (box.minX - origin.x) * inv;
            double t2 = (box.maxX - origin.x) * inv;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return Double.NaN;
        }

        // Y
        if (Math.abs(dir.y) < 1e-9) {
            if (origin.y < box.minY || origin.y > box.maxY) return Double.NaN;
        } else {
            double inv = 1.0 / dir.y;
            double t1 = (box.minY - origin.y) * inv;
            double t2 = (box.maxY - origin.y) * inv;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return Double.NaN;
        }

        // Z
        if (Math.abs(dir.z) < 1e-9) {
            if (origin.z < box.minZ || origin.z > box.maxZ) return Double.NaN;
        } else {
            double inv = 1.0 / dir.z;
            double t1 = (box.minZ - origin.z) * inv;
            double t2 = (box.maxZ - origin.z) * inv;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return Double.NaN;
        }

        return (tMin >= 0.0 && tMin <= maxT) ? tMin : Double.NaN;
    }
}
