/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/**
 * Stable signal bundle for temporal consumers. Algorithms remain subsystem-specific; this record
 * only defines which lifecycle-consistent motion/depth/reactive/disocclusion set belongs to a
 * temporal boundary.
 */
public record DeferredTemporalConsumerContract(
        Boundary boundary,
        DeferredResource velocity,
        DeferredResource motionValidity,
        DeferredResource depth,
        DeferredResource reactiveMask,
        DeferredResource disocclusionMask
) {
    public enum Boundary {
        PRE_TRANSLUCENCY,
        FINAL_FRAME
    }

    private static final DeferredTemporalConsumerContract PRE_TRANSLUCENCY =
            new DeferredTemporalConsumerContract(
                    Boundary.PRE_TRANSLUCENCY,
                    DeferredResource.VELOCITY,
                    DeferredResource.MOTION_VALIDITY,
                    DeferredResource.RESOLVED_DEPTH,
                    DeferredResource.REACTIVE_MASK,
                    DeferredResource.DISOCCLUSION_MASK);

    private static final DeferredTemporalConsumerContract FINAL_FRAME =
            new DeferredTemporalConsumerContract(
                    Boundary.FINAL_FRAME,
                    DeferredResource.FINAL_VELOCITY,
                    DeferredResource.FINAL_MOTION_VALIDITY,
                    DeferredResource.FINAL_RESOLVED_DEPTH,
                    DeferredResource.FINAL_REACTIVE_MASK,
                    DeferredResource.FINAL_DISOCCLUSION_MASK);

    public static DeferredTemporalConsumerContract preTranslucency() {
        return PRE_TRANSLUCENCY;
    }

    public static DeferredTemporalConsumerContract finalFrame() {
        return FINAL_FRAME;
    }
}
