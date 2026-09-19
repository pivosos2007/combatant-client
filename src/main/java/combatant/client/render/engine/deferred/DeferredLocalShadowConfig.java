/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/** Numerical/quality policy for analytic local-light shadow allocation and filtering. */
record DeferredLocalShadowConfig(
        boolean enabled,
        int atlasViewBudget,
        int faceResolution,
        float nearPlane,
        float retentionBoost,
        float normalOffsetTexels,
        float receiverBiasTexels,
        float filterRadiusTexels,
        float pointFaceFovDegrees
) {
    private static final DeferredLocalShadowConfig DEFAULT = new DeferredLocalShadowConfig(
            true, 6, 384, 0.0025f, 1.35f, 1.0f, 1.0f, 1.0f, 96.0f
    );

    DeferredLocalShadowConfig {
        atlasViewBudget = clamp(atlasViewBudget, 1, 64);
        faceResolution = clamp(faceResolution, 64, 2048);
        nearPlane = finiteClamp(nearPlane, 0.001f, 1.0f, 0.01f);
        retentionBoost = finiteClamp(retentionBoost, 1.0f, 4.0f, 1.20f);
        normalOffsetTexels = finiteClamp(normalOffsetTexels, 0.0f, 8.0f, 1.0f);
        receiverBiasTexels = finiteClamp(receiverBiasTexels, 0.0f, 8.0f, 1.0f);
        filterRadiusTexels = finiteClamp(filterRadiusTexels, 0.0f, 8.0f, 1.25f);
        pointFaceFovDegrees = finiteClamp(pointFaceFovDegrees, 90.0f, 110.0f, 96.0f);
    }

    static DeferredLocalShadowConfig current() {
        return DEFAULT;
    }

    int atlasColumns() {
        return Math.max(1, (int) Math.ceil(Math.sqrt(atlasViewBudget)));
    }

    int atlasRows() {
        return (atlasViewBudget + atlasColumns() - 1) / atlasColumns();
    }

    int atlasWidth() {
        return Math.multiplyExact(faceResolution, atlasColumns());
    }

    int atlasHeight() {
        return Math.multiplyExact(faceResolution, atlasRows());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
