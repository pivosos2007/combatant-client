/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.surface;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Backend-neutral semantic description of a surface-propagating radial wave. */
public record SurfaceWaveDescriptor(
        Vec3 center,
        long spawnTimeMs,
        long lifetimeMs,
        float maxRadius,
        float frontWidth,
        float trailWidth,
        float fillStrength,
        float trailStrength,
        int maxCellsPerFrame,
        float minAlpha,
        boolean depthTest
) {
    public SurfaceWaveDescriptor {
        if (center == null) {
            center = Vec3.ZERO;
        }
        lifetimeMs = Math.max(1L, lifetimeMs);
        maxRadius = Math.max(0.0f, maxRadius);
        frontWidth = Math.max(0.001f, frontWidth);
        trailWidth = Math.max(0.001f, trailWidth);
        fillStrength = Mth.clamp(fillStrength, 0.0f, 1.0f);
        trailStrength = Mth.clamp(trailStrength, 0.0f, 1.0f);
        maxCellsPerFrame = Math.max(1, maxCellsPerFrame);
        minAlpha = Mth.clamp(minAlpha, 0.0f, 1.0f);
    }

    public boolean isExpired(long nowMs) {
        return nowMs - spawnTimeMs >= lifetimeMs;
    }

    public float progress(long nowMs) {
        return Mth.clamp((nowMs - spawnTimeMs) / (float) lifetimeMs, 0.0f, 1.0f);
    }
}
