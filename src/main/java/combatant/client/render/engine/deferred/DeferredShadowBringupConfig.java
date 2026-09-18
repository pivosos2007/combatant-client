/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/**
 * Temporary normalization controls for directional-shadow bring-up.
 *
 * <p>The default deliberately isolates one useful near cascade instead of stretching one cascade
 * across the complete camera far plane. This is a diagnostic/correctness mode, not final quality
 * policy. It can be disabled at launch with {@code -Dcombatant.render.deferred.shadowNearOnly=false}
 * once the hard-shadow geometry is known-good.</p>
 */
final class DeferredShadowBringupConfig {
    private static final String NEAR_ONLY_PROPERTY = "combatant.render.deferred.shadowNearOnly";
    private static final String NEAR_DISTANCE_PROPERTY = "combatant.render.deferred.shadowNearDistance";
    private static final String NEAR_BIAS_PROPERTY = "combatant.render.deferred.shadowNearBiasTexels";
    private static final String NEAR_SLOPE_BIAS_PROPERTY = "combatant.render.deferred.shadowNearSlopeBiasTexels";
    private static final String NEAR_MAX_BIAS_PROPERTY = "combatant.render.deferred.shadowNearMaxBiasTexels";

    private DeferredShadowBringupConfig() {
    }

    static boolean nearOnly() {
        return Boolean.parseBoolean(System.getProperty(NEAR_ONLY_PROPERTY, "true"));
    }

    static float nearDistance() {
        return floatProperty(NEAR_DISTANCE_PROPERTY, 96.0f, 16.0f, 256.0f);
    }

    static float nearBiasTexels() {
        return floatProperty(NEAR_BIAS_PROPERTY, 0.15f, 0.0f, 2.0f);
    }

    static float nearSlopeBiasTexels() {
        return floatProperty(NEAR_SLOPE_BIAS_PROPERTY, 0.35f, 0.0f, 4.0f);
    }

    static float nearMaxBiasTexels() {
        return floatProperty(NEAR_MAX_BIAS_PROPERTY, 1.25f, 0.0f, 8.0f);
    }

    private static float floatProperty(String key, float fallback, float min, float max) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            float parsed = Float.parseFloat(value.trim());
            if (!Float.isFinite(parsed)) return fallback;
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
