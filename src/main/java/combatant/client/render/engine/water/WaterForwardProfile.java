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
        float refractionIntensity
) {
    public static final WaterForwardProfile FOUNDATION = new WaterForwardProfile(
            // Small geometry displacement keeps the native tessellation path meaningful without
            // turning every water block into a high-amplification patch.
            0.018f, 0.30f, 0.37f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f,
            // Near-neutral base extinction. Biome metadata changes the spectral coefficients in
            // the shader; it is never multiplied directly into the final scene color.
            0.055f, 0.045f, 0.040f,
            0.0f, 0.0f, 0.0f,
            1.0f
    );
}
