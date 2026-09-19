/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.water;

/**
 * Temporary full-replacement water profile. This keeps the current reference-style surface
 * parameters centralized while the later dimension/material water policy remains free to replace it.
 */
public record WaterForwardProfile(
        float displacementAmplitudeFactor,
        float displacementSpatialFrequency,
        float displacementTemporalFrequency,
        float windCoupling,
        float flowCoupling,
        float rainRippleContribution,
        float windX,
        float windZ,
        float absorptionR,
        float absorptionG,
        float absorptionB,
        float scatteringR,
        float scatteringG,
        float scatteringB,
        float refractionProbeDistance
) {
    public static final WaterForwardProfile FOUNDATION = new WaterForwardProfile(
            0.025f, 0.30f, 0.37f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f,
            0.39f, 0.14f, 0.07f,
            0.01f, 0.01f, 0.01f,
            8.0f
    );
}
