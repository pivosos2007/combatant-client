/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.emitter;

import net.minecraft.world.phys.Vec3;

/** Area-weighted AABB surface sampler used as the current fallback for entities without a mesh source. */
public final class BoxSurfaceEmitter {
    private BoxSurfaceEmitter() {
    }

    public static SurfaceEmitterSample sample(Vec3 min, Vec3 max, long seed, int sampleIndex) {
        Vec3 lo = min == null ? Vec3.ZERO : min;
        Vec3 hi = max == null ? lo : max;
        double dx = Math.max(0.0, hi.x - lo.x);
        double dy = Math.max(0.0, hi.y - lo.y);
        double dz = Math.max(0.0, hi.z - lo.z);

        double areaX = dy * dz;
        double areaY = dx * dz;
        double areaZ = dx * dy;
        double total = 2.0 * (areaX + areaY + areaZ);
        if (!(total > 1.0e-12)) {
            return new SurfaceEmitterSample(lo, Vec3.ZERO, -1, -1);
        }

        double pick = unit(seed, sampleIndex, 0) * total;
        double u = unit(seed, sampleIndex, 1);
        double v = unit(seed, sampleIndex, 2);
        int face;
        Vec3 position;
        Vec3 normal;

        if ((pick -= areaX) < 0.0) {
            face = 0;
            position = new Vec3(lo.x, lo.y + dy * u, lo.z + dz * v);
            normal = new Vec3(-1, 0, 0);
        } else if ((pick -= areaX) < 0.0) {
            face = 1;
            position = new Vec3(hi.x, lo.y + dy * u, lo.z + dz * v);
            normal = new Vec3(1, 0, 0);
        } else if ((pick -= areaY) < 0.0) {
            face = 2;
            position = new Vec3(lo.x + dx * u, lo.y, lo.z + dz * v);
            normal = new Vec3(0, -1, 0);
        } else if ((pick -= areaY) < 0.0) {
            face = 3;
            position = new Vec3(lo.x + dx * u, hi.y, lo.z + dz * v);
            normal = new Vec3(0, 1, 0);
        } else if ((pick -= areaZ) < 0.0) {
            face = 4;
            position = new Vec3(lo.x + dx * u, lo.y + dy * v, lo.z);
            normal = new Vec3(0, 0, -1);
        } else {
            face = 5;
            position = new Vec3(lo.x + dx * u, lo.y + dy * v, hi.z);
            normal = new Vec3(0, 0, 1);
        }
        return new SurfaceEmitterSample(position, normal, face, -1);
    }

    private static double unit(long seed, int index, int lane) {
        long z = seed + 0x9E3779B97F4A7C15L * (index + 1L) + 0xD1B54A32D192ED03L * (lane + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
