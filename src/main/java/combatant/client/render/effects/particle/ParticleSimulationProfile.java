/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.particle;

/**
 * Backend-neutral particle motion policy. Current CPU consumers only use the
 * parameters they already support; a future GPU backend can evaluate the same
 * descriptor without changing module-facing configuration.
 */
public record ParticleSimulationProfile(
        Policy policy,
        float linearDrag,
        float angularDrag,
        float gravity
) {
    public ParticleSimulationProfile {
        policy = policy == null ? Policy.STATIC : policy;
        linearDrag = sanitizeDrag(linearDrag);
        angularDrag = sanitizeDrag(angularDrag);
        gravity = Float.isFinite(gravity) ? gravity : 0.0f;
    }

    public static ParticleSimulationProfile drag(float linearDrag, float angularDrag) {
        return new ParticleSimulationProfile(Policy.DRAG, linearDrag, angularDrag, 0.0f);
    }

    public static ParticleSimulationProfile dragGravity(float linearDrag,
                                                        float angularDrag,
                                                        float gravity) {
        return new ParticleSimulationProfile(Policy.DRAG_GRAVITY, linearDrag, angularDrag, gravity);
    }

    public static ParticleSimulationProfile staticParticle() {
        return new ParticleSimulationProfile(Policy.STATIC, 1.0f, 1.0f, 0.0f);
    }

    private static float sanitizeDrag(float drag) {
        if (!Float.isFinite(drag)) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, drag));
    }

    public enum Policy {
        STATIC,
        BALLISTIC,
        DRAG,
        DRAG_GRAVITY,
        ORBIT,
        ATTRACTOR_FIELD,
        NOISE_FIELD,
        SPLINE_FOLLOW,
        SURFACE_FOLLOW
    }
}
