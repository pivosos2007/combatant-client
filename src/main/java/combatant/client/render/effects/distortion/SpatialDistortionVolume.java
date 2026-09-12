/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.distortion;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Backend-neutral local distortion field descriptor; no scene texture or graph resource ownership. */
public record SpatialDistortionVolume(
        Shape shape,
        Vec3 center,
        Vec3 axis,
        Vec3 halfExtents,
        float radius,
        float length,
        float strength,
        float chromaticDispersion,
        float noiseScale,
        float flowSpeed,
        float falloff,
        float emission,
        long spawnTimeMs,
        long lifetimeMs,
        long seed
) {
    public SpatialDistortionVolume {
        shape = shape == null ? Shape.SPHERE : shape;
        center = center == null ? Vec3.ZERO : center;
        axis = axis == null || axis.lengthSqr() < 1.0e-8 ? new Vec3(0.0, 1.0, 0.0) : axis.normalize();
        halfExtents = halfExtents == null ? Vec3.ZERO : new Vec3(
                Math.max(0.0, halfExtents.x),
                Math.max(0.0, halfExtents.y),
                Math.max(0.0, halfExtents.z)
        );
        radius = Math.max(0.0f, radius);
        length = Math.max(0.0f, length);
        strength = Math.max(0.0f, strength);
        chromaticDispersion = Math.max(0.0f, chromaticDispersion);
        noiseScale = Math.max(0.0001f, noiseScale);
        falloff = Math.max(0.001f, falloff);
        emission = Math.max(0.0f, emission);
        lifetimeMs = Math.max(1L, lifetimeMs);
    }

    public float progress(long nowMs) {
        return Mth.clamp((nowMs - spawnTimeMs) / (float) lifetimeMs, 0.0f, 1.0f);
    }

    public boolean isExpired(long nowMs) {
        return nowMs - spawnTimeMs >= lifetimeMs;
    }

    public enum Shape {
        SPHERE,
        CAPSULE,
        BOX,
        PLANE,
        RING,
        FIELD
    }
}
