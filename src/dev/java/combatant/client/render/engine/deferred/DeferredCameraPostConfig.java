/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Immutable user-facing camera-post policy consumed by the deferred post graph. */
public final class DeferredCameraPostConfig {
    public static final int MOTION_TILE_SIZE = 16;

    private static final AtomicReference<Snapshot> CURRENT = new AtomicReference<>(Snapshot.defaults());

    private DeferredCameraPostConfig() {
    }

    public static Snapshot current() {
        return CURRENT.get();
    }

    public static void apply(Snapshot snapshot) {
        CURRENT.set(Objects.requireNonNull(snapshot, "snapshot").validated());
    }

    public record Snapshot(
            boolean depthOfFieldEnabled,
            boolean depthOfFieldAutofocus,
            float depthOfFieldFarStart,
            float depthOfFieldFarTransition,
            float depthOfFieldStrength,
            float depthOfFieldMaxRadiusPixels,
            int depthOfFieldSampleCount,
            float depthOfFieldEdgeProtection,
            float depthOfFieldFocusSmoothing,
            boolean motionBlurEnabled,
            float motionBlurStrength,
            float motionBlurMaxPixels,
            float motionBlurMinMotionPixels,
            float motionBlurShutterScale,
            int motionBlurSampleCount,
            float motionBlurDepthEdgeProtection
    ) {
        public Snapshot validated() {
            return new Snapshot(
                    depthOfFieldEnabled,
                    depthOfFieldAutofocus,
                    finiteClamp(depthOfFieldFarStart, 0.0f, 512.0f, 4.0f),
                    finiteClamp(depthOfFieldFarTransition, 0.01f, 1024.0f, 24.0f),
                    finiteClamp(depthOfFieldStrength, 0.0f, 2.0f, 0.65f),
                    finiteClamp(depthOfFieldMaxRadiusPixels, 0.0f, 64.0f, 8.0f),
                    clamp(depthOfFieldSampleCount, 4, 64),
                    finiteClamp(depthOfFieldEdgeProtection, 0.0f, 8.0f, 0.85f),
                    finiteClamp(depthOfFieldFocusSmoothing, 0.0f, 32.0f, 8.0f),
                    motionBlurEnabled,
                    finiteClamp(motionBlurStrength, 0.0f, 2.0f, 0.42f),
                    finiteClamp(motionBlurMaxPixels, 0.0f, 128.0f, 18.0f),
                    finiteClamp(motionBlurMinMotionPixels, 0.0f, 8.0f, 0.45f),
                    finiteClamp(motionBlurShutterScale, 0.0f, 6.0f, 1.0f),
                    clamp(motionBlurSampleCount, 4, 64),
                    finiteClamp(motionBlurDepthEdgeProtection, 0.0f, 8.0f, 1.0f)
            );
        }

        public static Snapshot defaults() {
            return new Snapshot(
                    false, true, 4.0f, 24.0f, 0.65f, 8.0f, 16, 0.85f, 8.0f,
                    false, 0.42f, 18.0f, 0.45f, 1.0f, 12, 1.0f
            );
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static float finiteClamp(float value, float min, float max, float fallback) {
            float safe = Float.isFinite(value) ? value : fallback;
            return Math.max(min, Math.min(max, safe));
        }
    }
}
