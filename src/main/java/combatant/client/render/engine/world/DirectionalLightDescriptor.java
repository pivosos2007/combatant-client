/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import org.joml.Vector3f;

/**
 * Directional light whose direction points from the receiver toward the source.
 */
public record DirectionalLightDescriptor(
        float directionX,
        float directionY,
        float directionZ,
        float radianceRed,
        float radianceGreen,
        float radianceBlue,
        float angularRadiusRadians,
        boolean castsShadow,
        boolean valid
) {
    public static final DirectionalLightDescriptor NONE = new DirectionalLightDescriptor(
            0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, false, false
    );

    public DirectionalLightDescriptor {
        float lengthSquared = directionX * directionX + directionY * directionY + directionZ * directionZ;
        boolean finiteDirection = Float.isFinite(directionX) && Float.isFinite(directionY)
                && Float.isFinite(directionZ) && Float.isFinite(lengthSquared);
        if (!valid || !finiteDirection || lengthSquared <= 1.0e-12f) {
            directionX = 0.0f;
            directionY = 1.0f;
            directionZ = 0.0f;
            valid = false;
            castsShadow = false;
        } else {
            float inverseLength = 1.0f / (float) Math.sqrt(lengthSquared);
            directionX *= inverseLength;
            directionY *= inverseLength;
            directionZ *= inverseLength;
        }

        radianceRed = finiteNonNegative(radianceRed);
        radianceGreen = finiteNonNegative(radianceGreen);
        radianceBlue = finiteNonNegative(radianceBlue);
        angularRadiusRadians = finiteNonNegative(angularRadiusRadians);
        if (radianceRed <= 0.0f && radianceGreen <= 0.0f && radianceBlue <= 0.0f) {
            valid = false;
            castsShadow = false;
        }
    }

    public static DirectionalLightDescriptor of(float directionX,
                                                float directionY,
                                                float directionZ,
                                                float radianceRed,
                                                float radianceGreen,
                                                float radianceBlue,
                                                float angularRadiusRadians,
                                                boolean castsShadow) {
        return new DirectionalLightDescriptor(
                directionX, directionY, directionZ,
                radianceRed, radianceGreen, radianceBlue,
                angularRadiusRadians, castsShadow, true
        );
    }

    public Vector3f direction(Vector3f destination) {
        Vector3f out = destination == null ? new Vector3f() : destination;
        return out.set(directionX, directionY, directionZ);
    }

    public boolean shadowValid() {
        return valid && castsShadow;
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }
}
