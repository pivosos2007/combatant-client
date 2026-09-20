/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import java.util.EnumSet;
import java.util.Set;

/**
 * Stable typed identities for independently smoke-testable renderer subsystems.
 *
 * <p>This is deliberately separate from quality/config policy. Production policy still decides the
 * default state; {@link DeferredSmokeTestState} may temporarily override it for diagnosis.</p>
 */
public enum DeferredFeature {
    SHADOWS,
    CONTACT_SHADOWS,
    GTAO,
    INDIRECT_LIGHT,
    COLORED_BLOCK_LIGHT,
    DYNAMIC_LIGHTS,
    REFLECTIONS,
    WATER,
    TAA,
    EXPOSURE,
    BLOOM,
    PARTICIPATING_MEDIA,
    DEPTH_OF_FIELD,
    MOTION_BLUR,

    /** Volumetric cloud occupancy/render/temporal subsystem. */
    CLOUDS,
    /** Sky/atmosphere environment subsystem. */
    SKY,
    /** World-space weather/environment-state subsystem. */
    WEATHER;

    boolean productionEnabled(DeferredRuntimeConfig.Snapshot settings) {
        if (settings == null) settings = DeferredRuntimeConfig.current();
        return switch (this) {
            case SHADOWS -> settings.shadowsEnabled();
            case CONTACT_SHADOWS -> settings.shadowsEnabled() && settings.contactShadowsEnabled();
            case GTAO -> settings.ambientOcclusionEnabled();
            case INDIRECT_LIGHT -> settings.indirectLightEnabled();
            case REFLECTIONS -> settings.reflectionsEnabled();
            case COLORED_BLOCK_LIGHT -> settings.coloredBlockLightEnabled() && DeferredBlockLightConfig.current().enabled();
            case DYNAMIC_LIGHTS -> settings.dynamicLightsEnabled();
            case WATER -> settings.waterEnabled();
            case TAA -> DeferredTemporalConfig.current().taaEnabled();
            case EXPOSURE -> DeferredPostConfig.current().exposureEnabled();
            case BLOOM -> DeferredPostConfig.current().bloomEnabled();
            case PARTICIPATING_MEDIA -> settings.participatingMediaEnabled() && DeferredFroxelConfig.current().enabled();
            case DEPTH_OF_FIELD -> DeferredCameraPostConfig.current().depthOfFieldEnabled();
            case MOTION_BLUR -> DeferredCameraPostConfig.current().motionBlurEnabled();
            case CLOUDS -> DeferredEnvironmentFeatureConfig.current().cloudsEnabled();
            case SKY -> DeferredEnvironmentFeatureConfig.current().skyEnabled();
            case WEATHER -> DeferredEnvironmentFeatureConfig.current().weatherEnabled();
        };
    }

    /** Histories whose semantics become stale when this subsystem changes enabled state. */
    Set<DeferredTemporalHistoryId> temporalHistories() {
        return switch (this) {
            case TAA -> EnumSet.of(DeferredTemporalHistoryId.TAA);
            case REFLECTIONS -> EnumSet.of(DeferredTemporalHistoryId.REFLECTIONS);
            case INDIRECT_LIGHT -> EnumSet.of(DeferredTemporalHistoryId.INDIRECT_LIGHT);
            case WATER -> EnumSet.of(DeferredTemporalHistoryId.WATER, DeferredTemporalHistoryId.WATER_REFLECTIONS);
            case PARTICIPATING_MEDIA -> EnumSet.of(DeferredTemporalHistoryId.FROXEL_MEDIA);
            case CLOUDS -> EnumSet.of(
                    DeferredTemporalHistoryId.CLOUDS,
                    DeferredTemporalHistoryId.CLOUDS_HIGH,
                    DeferredTemporalHistoryId.CLOUDS_CONVECTIVE
            );
            case EXPOSURE -> EnumSet.of(DeferredTemporalHistoryId.EXPOSURE);
            default -> Set.of();
        };
    }
}
