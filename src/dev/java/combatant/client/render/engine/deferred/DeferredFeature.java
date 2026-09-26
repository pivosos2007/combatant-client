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
    COLORED_BLOCK_LIGHT,
    TAA,
    EXPOSURE,
    BLOOM,
    DEPTH_OF_FIELD,
    MOTION_BLUR;

    boolean productionEnabled(DeferredRuntimeConfig.Snapshot settings) {
        if (settings == null) settings = DeferredRuntimeConfig.current();
        return switch (this) {
            case COLORED_BLOCK_LIGHT -> settings.coloredBlockLightEnabled() && DeferredBlockLightConfig.current().enabled();
            case TAA -> DeferredTemporalConfig.current().taaEnabled();
            case EXPOSURE -> DeferredPostConfig.current().exposureEnabled();
            case BLOOM -> DeferredPostConfig.current().bloomEnabled();
            case DEPTH_OF_FIELD -> DeferredCameraPostConfig.current().depthOfFieldEnabled();
            case MOTION_BLUR -> DeferredCameraPostConfig.current().motionBlurEnabled();
        };
    }

    /** Histories whose semantics become stale when this subsystem changes enabled state. */
    Set<DeferredTemporalHistoryId> temporalHistories() {
        return switch (this) {
            case TAA -> EnumSet.of(DeferredTemporalHistoryId.TAA);
            case EXPOSURE -> EnumSet.of(DeferredTemporalHistoryId.EXPOSURE);
            default -> Set.of();
        };
    }
}

