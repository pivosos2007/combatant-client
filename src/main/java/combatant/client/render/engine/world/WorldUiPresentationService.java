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

        float screenAlpha;
        float worldAlpha;
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
                // Fade out the world presentation first, then fade the screen presentation in.
                // The two variants never overlap, so the transition stays smooth without rendering
                // duplicate nameplates/items at the same time.
                double midpoint = (safePolicy.handoffStartDistance() + safePolicy.handoffEndDistance()) * 0.5;
                worldAlpha = 1.0f - smoothstep(
                        safePolicy.handoffStartDistance(), midpoint, safeDistance);
                screenAlpha = smoothstep(
                        midpoint, safePolicy.handoffEndDistance(), safeDistance);
            }
            default -> throw new IllegalStateException("Unexpected presentation mode: " + safeMode);
        }

        // Keep billboards in physical world units. Distance/projection compensation effectively turns
        // them into constant-screen-size UI and makes their world footprint grow as the camera moves
        // away. A fixed scale gives normal perspective: farther billboards become smaller on screen.
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
            return new Policy(0.0168, 14.0, 16.0, 28.0, 0.38, 3.50);
        }
    }

    public record Snapshot(float screenAlpha, float worldAlpha, double worldUnitsPerPixel) {
    }
}
