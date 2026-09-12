/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Stable descriptor consumed by transient backends. Keep this independent from graph resources,
 * shader filenames, API handles and future HDR/G-buffer layouts.
 */
public record TransientEffectDescriptor(
        long id,
        String type,
        EffectDomain domain,
        Vec3 position,
        long spawnTimeMs,
        long lifetimeMs,
        int sourceEntityId,
        long seed,
        int flags,
        int priority,
        EffectMaterial material,
        EffectParameters parameters
) {
    public static final int NO_SOURCE_ENTITY = -1;

    public TransientEffectDescriptor {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Transient effect type must not be blank");
        }
        domain = domain == null ? EffectDomain.BILLBOARD : domain;
        position = position == null ? Vec3.ZERO : position;
        lifetimeMs = Math.max(1L, lifetimeMs);
        material = material == null
                ? new EffectMaterial(0xFFFFFFFF, 0xFFFFFFFF, 1.0f, 0.0f,
                EffectMaterial.DepthPolicy.MAIN, EffectMaterial.BlendPolicy.ALPHA)
                : material;
        parameters = parameters == null ? EffectParameters.ZERO : parameters;
    }

    public boolean isExpired(long nowMs) {
        return nowMs - spawnTimeMs >= lifetimeMs;
    }

    public float progress(long nowMs) {
        return Mth.clamp((nowMs - spawnTimeMs) / (float) lifetimeMs, 0.0f, 1.0f);
    }
}
