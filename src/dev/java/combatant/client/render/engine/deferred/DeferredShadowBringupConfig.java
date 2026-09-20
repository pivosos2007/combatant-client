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
 * <p>Near-only is retained as an explicit diagnostic mode, but production defaults to the full
 * cascade set now that near-field geometry, acne and camera/celestial stabilization have passed
 * bring-up. Enable isolation explicitly with {@code -Dcombatant.render.deferred.shadowNearOnly=true}.</p>
 */
final class DeferredShadowBringupConfig {
    private static final String NEAR_ONLY_PROPERTY = "combatant.render.deferred.shadowNearOnly";
    private static final String NEAR_DISTANCE_PROPERTY = "combatant.render.deferred.shadowNearDistance";
    private static final String NEAR_BIAS_PROPERTY = "combatant.render.deferred.shadowNearBiasTexels";
    private static final String NEAR_SLOPE_BIAS_PROPERTY = "combatant.render.deferred.shadowNearSlopeBiasTexels";
    private static final String NEAR_MAX_BIAS_PROPERTY = "combatant.render.deferred.shadowNearMaxBiasTexels";
    private static final String FAR_FADE_FRACTION_PROPERTY = "combatant.render.deferred.shadowFarFadeFraction";
    private static final String RECEIVER_DISTANCE_SCALE_PROPERTY =
            "combatant.render.deferred.shadowReceiverDistanceScale";
    private static final String MAX_ADAPTIVE_FILTER_RADIUS_PROPERTY = "combatant.render.deferred.shadowMaxAdaptiveFilterRadiusTexels";
    private static final String DISTANT_SKYLIGHT_FALLBACK_START_PROPERTY =
            "combatant.render.deferred.shadowDistantSkylightFallbackStart";
    private static final String DISTANT_SKYLIGHT_FALLBACK_END_PROPERTY =
            "combatant.render.deferred.shadowDistantSkylightFallbackEnd";
    private static final String LOW_SKYLIGHT_LEAK_END_PROPERTY =
            "combatant.render.deferred.shadowLowSkylightLeakEnd";
    private static final String DISTANT_SSRT_ENABLED_PROPERTY =
            "combatant.render.deferred.shadowDistantSsrt";
    private static final String DISTANT_SSRT_STEPS_PROPERTY =
            "combatant.render.deferred.shadowDistantSsrtSteps";
    private static final String DISTANT_SSRT_THICKNESS_PROPERTY =
            "combatant.render.deferred.shadowDistantSsrtThickness";

    private DeferredShadowBringupConfig() {
    }

    static boolean nearOnly() {
        return Boolean.parseBoolean(System.getProperty(NEAR_ONLY_PROPERTY, "false"));
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

    static float farFadeFraction() {
        // Keep only a short terminal hand-off. A 12% fade on the very wide final cascade made
        // production shadows appear to die substantially before the actual receiver limit.
        return floatProperty(FAR_FADE_FRACTION_PROPERTY, 0.06f, 0.0f, 0.40f);
    }

    static float receiverDistanceScale() {
        // Chunk visibility is square in X/Z, so a camera looking along a diagonal sees farther than
        // the axial N*16 distance. The base range already includes one guard chunk, so 1.30 covers
        // the practical 12-chunk diagonal without paying the full sqrt(2) density penalty again.
        return floatProperty(RECEIVER_DISTANCE_SCALE_PROPERTY, 1.30f, 1.0f, 1.60f);
    }

    static float maxAdaptiveFilterRadiusTexels() {
        return floatProperty(MAX_ADAPTIVE_FILTER_RADIUS_PROPERTY, 3.0f, 0.0f, 6.0f);
    }

    /**
     * Conservative distant-shadow fallback from Minecraft skylight. Only pixels very close to
     * fully sky-exposed are allowed to become fully directionally lit outside reliable CSM
     * coverage. This prevents long caves/tunnels from becoming sunlit merely because the shadow
     * map ran out of receiver range.
     */
    static float distantSkylightFallbackStart() {
        return floatProperty(DISTANT_SKYLIGHT_FALLBACK_START_PROPERTY, 0.90f, 0.0f, 0.99f);
    }

    static float distantSkylightFallbackEnd() {
        return floatProperty(DISTANT_SKYLIGHT_FALLBACK_END_PROPERTY, 0.985f, 0.01f, 1.0f);
    }

    /**
     * Photon-style low-skylight guard for distant SSRT only. Valid near-map PCF remains purely
     * geometric so Minecraft's discrete skylight levels cannot appear as bands in the shadow.
     */
    static float lowSkylightLeakEnd() {
        return floatProperty(LOW_SKYLIGHT_LEAK_END_PROPERTY, 2.0f / 15.0f, 0.0f, 0.5f);
    }


    static boolean distantSsrtEnabled() {
        return Boolean.parseBoolean(System.getProperty(DISTANT_SSRT_ENABLED_PROPERTY, "true"));
    }

    static int distantSsrtSteps() {
        return intProperty(DISTANT_SSRT_STEPS_PROPERTY, 10, 4, 24);
    }

    static float distantSsrtThickness() {
        // Photon uses z_tolerance=10; its hit predicate accepts roughly 0..2*tolerance.
        return floatProperty(DISTANT_SSRT_THICKNESS_PROPERTY, 10.0f, 0.25f, 32.0f);
    }

    private static int intProperty(String key, int fallback, int min, int max) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
