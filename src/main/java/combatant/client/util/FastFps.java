/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util;

import lombok.Getter;

public enum FastFps {
    ;

    private static final double NANOS_PER_SEC = 1_000_000_000.0;
    private static final long FPS_SMOOTHING_TAU_NANOS = 150_000_000L;
    private static final long MIN_FRAME_DISCONTINUITY_NANOS = 250_000_000L;
    private static final long INITIAL_FRAME_DISCONTINUITY_NANOS = 5_000_000_000L;
    private static final double FRAME_DISCONTINUITY_MULTIPLIER = 4.0;
    private static final float DEFAULT_DELTA_SECONDS = 1.0f / 60.0f;

    private static long lastFrameNanos;
    private static double smoothedFrameNanos;
    private static float deltaSeconds = DEFAULT_DELTA_SECONDS;

    @Getter
    private static int fps;

    public static void onFrame() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }

        long frameNanos = now - lastFrameNanos;
        lastFrameNanos = now;
        if (frameNanos <= 0L) {
            return;
        }

        // A minimized window, debugger stop or resource reload is not a rendered frame.
        // Feeding that gap into the FPS EMA poisons it for several seconds after resume.
        long discontinuityNanos = smoothedFrameNanos > 0.0
                ? Math.max(MIN_FRAME_DISCONTINUITY_NANOS, (long) (smoothedFrameNanos * FRAME_DISCONTINUITY_MULTIPLIER))
                : INITIAL_FRAME_DISCONTINUITY_NANOS;
        if (frameNanos >= discontinuityNanos) {
            smoothedFrameNanos = 0.0;
            deltaSeconds = DEFAULT_DELTA_SECONDS;
            return;
        }

        deltaSeconds = (float) (frameNanos / NANOS_PER_SEC);

        if (smoothedFrameNanos == 0.0) {
            smoothedFrameNanos = frameNanos;
        } else {
            double alpha = 1.0 - Math.exp(-frameNanos / (double) FPS_SMOOTHING_TAU_NANOS);
            smoothedFrameNanos += (frameNanos - smoothedFrameNanos) * alpha;
        }

        fps = (int) Math.round(NANOS_PER_SEC / smoothedFrameNanos);
    }

    public static float getDeltaSeconds() {
        return deltaSeconds;
    }

    public static void reset() {
        lastFrameNanos = 0L;
        smoothedFrameNanos = 0.0;
        deltaSeconds = DEFAULT_DELTA_SECONDS;
        fps = 0;
    }
}
