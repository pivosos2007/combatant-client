/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

/** Shared, stateless presentation policy for world-anchored UI. */
public enum WorldUiPresentationService {
    ;

    public static Snapshot resolve(Mode mode, double distance, Policy policy) {
        return resolve(mode, distance, policy, Double.NaN, 0.0);
    }

    public static Snapshot resolve(Mode mode,
                                   double distance,
                                   Policy policy,
                                   double projectionYScale,
                                   double logicalViewportHeight) {
        Mode safeMode = mode != null ? mode : Mode.HYBRID;
        Policy safePolicy = policy != null ? policy : Policy.defaults();
        double safeDistance = Math.max(0.0, distance);

        float screenAlpha = 0;
        float worldAlpha = 0;
        double worldScale = safePolicy.physicalWorldUnitsPerPixel();
        switch (safeMode) {
            case SCREEN -> {
                screenAlpha = 1.0f;
                worldAlpha = 0.0f;
            }
            case WORLD -> {
                screenAlpha = 0.0f;
                worldAlpha = 1.0f;
            }
            case HYBRID -> {
                screenAlpha = smoothstep(
                        safePolicy.handoffStartDistance(), safePolicy.handoffEndDistance(), safeDistance);
                worldAlpha = 1.0f - screenAlpha;
                double compensation;
                if (Double.isFinite(projectionYScale)
                        && Math.abs(projectionYScale) > 0.0001
                        && logicalViewportHeight > 1.0) {
                    double exactUnitsPerLogicalPixel = 2.0 * safeDistance
                            / (Math.abs(projectionYScale) * logicalViewportHeight);
                    compensation = exactUnitsPerLogicalPixel / worldScale;
                } else {
                    compensation = safeDistance / Math.max(0.001, safePolicy.referenceDistance());
                }
                compensation = Math.max(safePolicy.minimumCompensation(),
                        Math.min(safePolicy.maximumCompensation(), compensation));
                double compensatedScale = worldScale * compensation;
                float compensationBlend = smoothstep(
                        safePolicy.referenceDistance() * 0.45,
                        safePolicy.referenceDistance(),
                        safeDistance);
                worldScale += (compensatedScale - worldScale) * compensationBlend;
            }
        }
        return new Snapshot(screenAlpha, worldAlpha, worldScale);
    }

    private static float smoothstep(double start, double end, double value) {
        if (end <= start) return value >= end ? 1.0f : 0.0f;
        double t = Math.max(0.0, Math.min(1.0, (value - start) / (end - start)));
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    public enum Mode {
        SCREEN,
        WORLD,
        HYBRID
    }

    public record Policy(double physicalWorldUnitsPerPixel,
                         double referenceDistance,
                         double handoffStartDistance,
                         double handoffEndDistance,
                         double minimumCompensation,
                         double maximumCompensation) {
        public Policy {
            physicalWorldUnitsPerPixel = Math.max(0.00001, physicalWorldUnitsPerPixel);
            referenceDistance = Math.max(0.001, referenceDistance);
            handoffStartDistance = Math.max(0.0, handoffStartDistance);
            handoffEndDistance = Math.max(handoffStartDistance + 0.001, handoffEndDistance);
            minimumCompensation = Math.max(0.01, minimumCompensation);
            maximumCompensation = Math.max(minimumCompensation, maximumCompensation);
        }

        public static Policy defaults() {
            return new Policy(0.0185, 14.0, 16.0, 28.0, 0.38, 3.50);
        }
    }

    public record Snapshot(float screenAlpha, float worldAlpha, double worldUnitsPerPixel) {
    }
}
