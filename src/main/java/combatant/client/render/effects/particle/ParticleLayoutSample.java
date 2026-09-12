/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.particle;

import net.minecraft.world.phys.Vec3;

/** A backend-neutral sample produced by a procedural particle layout. */
public record ParticleLayoutSample(
        Vec3 offset,
        float alpha,
        float scale,
        float rotationDegrees,
        int colorPhase
) {
    public ParticleLayoutSample {
        if (offset == null) {
            offset = Vec3.ZERO;
        }
        alpha = clamp01(alpha);
        scale = Math.max(0.0f, scale);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
