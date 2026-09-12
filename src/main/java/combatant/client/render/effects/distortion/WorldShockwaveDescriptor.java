/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.distortion;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Bridge contract only. The current renderer may approximate it, while the advanced renderer can
 * later route the same descriptor into the shared distortion/emission resources.
 */
public record WorldShockwaveDescriptor(
        Vec3 center,
        long spawnTimeMs,
        long lifetimeMs,
        float initialRadius,
        float maxRadius,
        float thickness,
        float refractionStrength,
        float emission,
        int tintColor,
        long seed
) {
    public WorldShockwaveDescriptor {
        center = center == null ? Vec3.ZERO : center;
        lifetimeMs = Math.max(1L, lifetimeMs);
        initialRadius = Math.max(0.0f, initialRadius);
        maxRadius = Math.max(initialRadius, maxRadius);
        thickness = Math.max(0.001f, thickness);
        refractionStrength = Math.max(0.0f, refractionStrength);
        emission = Math.max(0.0f, emission);
    }

    public float progress(long nowMs) {
        return Mth.clamp((nowMs - spawnTimeMs) / (float) lifetimeMs, 0.0f, 1.0f);
    }

    public float radius(long nowMs) {
        float t = progress(nowMs);
        float eased = t * t * (3.0f - 2.0f * t);
        return initialRadius + (maxRadius - initialRadius) * eased;
    }

    public boolean isExpired(long nowMs) {
        return nowMs - spawnTimeMs >= lifetimeMs;
    }
}
