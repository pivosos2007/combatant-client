/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import java.util.Arrays;

/**
 * Prints frame-time statistics to stdout every few seconds when started with
 * {@code -Dcombatant.profiler.fpsLog=true}.
 *
 * <p>Sampling profilers report where time goes but not how many frames it was spread over, so two
 * recordings are only comparable once their frame counts are known. This is that missing number: it
 * costs one static boolean read per frame while disabled.</p>
 */
public enum FrameRateLog {
    ;

    private static final boolean ENABLED = Boolean.getBoolean("combatant.profiler.fpsLog");
    private static final long WINDOW_NANOS = 5_000_000_000L;
    private static final int MAX_SAMPLES = 1 << 16;

    private static final double[] FRAME_MS = new double[MAX_SAMPLES];
    private static int count;
    private static long lastFrameNanos;
    private static long windowStartNanos;

    public static void onFrame() {
        if (!ENABLED) return;
        long now = System.nanoTime();
        if (lastFrameNanos != 0L && count < MAX_SAMPLES) {
            FRAME_MS[count++] = (now - lastFrameNanos) / 1.0e6;
        }
        lastFrameNanos = now;
        if (windowStartNanos == 0L) {
            windowStartNanos = now;
            return;
        }
        if (now - windowStartNanos < WINDOW_NANOS || count == 0) return;

        double[] sorted = Arrays.copyOf(FRAME_MS, count);
        Arrays.sort(sorted);
        double total = 0.0;
        for (double ms : sorted) total += ms;
        double seconds = (now - windowStartNanos) / 1.0e9;
        System.out.printf("[FPS-LOG] frames=%d fps=%.1f avg=%.3fms p50=%.3fms p99=%.3fms max=%.3fms%n",
                count, count / seconds, total / count,
                sorted[count / 2], sorted[Math.min(count - 1, (int) (count * 0.99))], sorted[count - 1]);
        count = 0;
        windowStartNanos = now;
    }
}
