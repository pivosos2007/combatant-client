/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.animation;

import net.minecraft.util.Mth;
import combatant.client.util.FastFps;

public enum AnimationUtility {
    ;

    private static final float DEFAULT_DELTA_SECONDS = 1.0f / 60.0f;
    private static final float MAX_CLOCK_DELTA_SECONDS = 0.25f;

    private static long lastMillis;
    private static long lastNanos;
    private static long frameMillis;
    private static long frameNanos;
    private static float millisDeltaSeconds = DEFAULT_DELTA_SECONDS;
    private static float nanosDeltaSeconds = DEFAULT_DELTA_SECONDS;
    private static double fpsTimelineMillis;

    /**
     * Animation clock source. Unspecified animation timing defaults to {@link Mode#FPS}.
     */
    public enum Mode {
        MILLIS,
        FPS,
        NANOS
    }

    /**
     * Advances the cached wall-clock/high-resolution animation clocks once per presented frame.
     * This keeps every animation queried during the same frame on one timestamp instead of
     * calling the system clocks independently for every widget.
     */
    public static void onFrame() {
        long nowMillis = System.currentTimeMillis();
        long nowNanos = System.nanoTime();

        if (lastMillis != 0L) {
            millisDeltaSeconds = sanitizeDelta((nowMillis - lastMillis) / 1_000.0f);
        }
        if (lastNanos != 0L) {
            nanosDeltaSeconds = sanitizeDelta((float) ((nowNanos - lastNanos) / 1_000_000_000.0));
        }

        frameMillis = nowMillis;
        frameNanos = nowNanos;
        lastMillis = nowMillis;
        lastNanos = nowNanos;
        fpsTimelineMillis += Math.max(0.0f, FastFps.getDeltaSeconds()) * 1_000.0;
    }

    public static void resetTiming() {
        lastMillis = 0L;
        lastNanos = 0L;
        frameMillis = 0L;
        frameNanos = 0L;
        millisDeltaSeconds = DEFAULT_DELTA_SECONDS;
        nanosDeltaSeconds = DEFAULT_DELTA_SECONDS;
        fpsTimelineMillis = 0.0;
    }

    public static float deltaTime() {
        return deltaTime(Mode.FPS);
    }

    public static float deltaTime(Mode mode) {
        Mode resolved = mode == null ? Mode.FPS : mode;
        return switch (resolved) {
            case MILLIS -> millisDeltaSeconds;
            case NANOS -> nanosDeltaSeconds;
            case FPS -> FastFps.getDeltaSeconds();
        };
    }

    public static float time(float speed) {
        return time(speed, Mode.FPS);
    }

    public static float time(float speed, Mode mode) {
        return (float) ((timelineMillis(mode) % 1_000_000.0) * speed);
    }

    private static double timelineMillis(Mode mode) {
        Mode resolved = mode == null ? Mode.FPS : mode;
        return switch (resolved) {
            case MILLIS -> frameMillis != 0L ? frameMillis : System.currentTimeMillis();
            case NANOS -> (frameNanos != 0L ? frameNanos : System.nanoTime()) / 1_000_000.0;
            case FPS -> fpsTimelineMillis;
        };
    }

    private static float sanitizeDelta(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0.0f || deltaSeconds > MAX_CLOCK_DELTA_SECONDS) {
            return DEFAULT_DELTA_SECONDS;
        }
        return deltaSeconds;
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    public static float lerp(float start, float end, float t) {
        return start + (end - start) * clamp01(t);
    }

    public static double lerp(double start, double end, double t) {
        return start + (end - start) * clamp(t, 0.0, 1.0);
    }

    public static float approach(float value, float target, float speed) {
        return lerp(value, target, speed);
    }

    public static double approach(double value, double target, double speed) {
        return lerp(value, target, speed);
    }

    public static float approach(float value, float target, float dt, float speed) {
        return approach(value, target, dt * speed);
    }

    public static float approach(float value, float target, float speed, Mode mode) {
        return approach(value, target, deltaTime(mode), speed);
    }

    public static float snap(float value, float target, float epsilon) {
        return Math.abs(value - target) <= Math.max(0f, epsilon) ? target : value;
    }

    public static float smoothstep(float value) {
        float t = clamp01(value);
        return t * t * (3f - 2f * t);
    }

    public static float cubicEase(float value) {
        return smoothstep(value);
    }

    public static float easeInSine(float value) {
        float t = clamp01(value);
        return 1f - (float) Math.cos(t * Math.PI * 0.5);
    }

    public static float easeOutSine(float value) {
        float t = clamp01(value);
        return (float) Math.sin(t * Math.PI * 0.5);
    }

    public static float easeInOutSine(float value) {
        float t = clamp01(value);
        return -((float) Math.cos(Math.PI * t) - 1f) * 0.5f;
    }

    public static float easeInQuad(float value) {
        float t = clamp01(value);
        return t * t;
    }

    public static float easeOutQuad(float value) {
        float t = clamp01(value);
        float p = 1f - t;
        return 1f - p * p;
    }

    public static float easeInOutQuad(float value) {
        float t = clamp01(value);
        if (t < 0.5f) return 2f * t * t;
        float p = -2f * t + 2f;
        return 1f - p * p * 0.5f;
    }

    public static float easeInCubic(float value) {
        float t = clamp01(value);
        return t * t * t;
    }

    public static float easeOutCubic(float value) {
        float t = clamp01(value);
        float p = 1f - t;
        return 1f - p * p * p;
    }

    public static float easeInOutCubic(float value) {
        float t = clamp01(value);
        if (t < 0.5f) return 4f * t * t * t;
        float p = -2f * t + 2f;
        return 1f - p * p * p * 0.5f;
    }

    public static float easeInQuart(float value) {
        float t = clamp01(value);
        float t2 = t * t;
        return t2 * t2;
    }

    public static float easeOutQuart(float value) {
        float t = clamp01(value);
        float p = 1f - t;
        float p2 = p * p;
        return 1f - p2 * p2;
    }

    public static float easeInOutQuart(float value) {
        float t = clamp01(value);
        if (t < 0.5f) {
            float t2 = t * t;
            return 8f * t2 * t2;
        }
        float p = -2f * t + 2f;
        float p2 = p * p;
        return 1f - p2 * p2 * 0.5f;
    }

    public static float easeInQuint(float value) {
        float t = clamp01(value);
        float t2 = t * t;
        return t2 * t2 * t;
    }

    public static float easeOutQuint(float value) {
        float t = clamp01(value);
        float p = 1f - t;
        float p2 = p * p;
        return 1f - p2 * p2 * p;
    }

    public static float easeInOutQuint(float value) {
        float t = clamp01(value);
        if (t < 0.5f) {
            float t2 = t * t;
            return 16f * t2 * t2 * t;
        }
        float p = -2f * t + 2f;
        float p2 = p * p;
        return 1f - p2 * p2 * p * 0.5f;
    }

    public static float easeInExpo(float value) {
        float t = clamp01(value);
        return t == 0f ? 0f : (float) Math.pow(2.0, 10.0 * t - 10.0);
    }

    public static float easeOutExpo(float value) {
        float t = clamp01(value);
        return t == 1f ? 1f : 1f - (float) Math.pow(2.0, -10.0 * t);
    }

    public static float easeInOutExpo(float value) {
        float t = clamp01(value);
        if (t == 0f) return 0f;
        if (t == 1f) return 1f;
        return t < 0.5f
                ? (float) Math.pow(2.0, 20.0 * t - 10.0) * 0.5f
                : (2f - (float) Math.pow(2.0, -20.0 * t + 10.0)) * 0.5f;
    }

    public static float easeInCirc(float value) {
        float t = clamp01(value);
        return 1f - (float) Math.sqrt(Math.max(0f, 1f - t * t));
    }

    public static float easeOutCirc(float value) {
        float t = clamp01(value);
        float p = t - 1f;
        return (float) Math.sqrt(Math.max(0f, 1f - p * p));
    }

    public static float easeInOutCirc(float value) {
        float t = clamp01(value);
        if (t < 0.5f) {
            float p = 2f * t;
            return (1f - (float) Math.sqrt(Math.max(0f, 1f - p * p))) * 0.5f;
        }
        float p = -2f * t + 2f;
        return ((float) Math.sqrt(Math.max(0f, 1f - p * p)) + 1f) * 0.5f;
    }

    public static float easeInBack(float value) {
        return easeInBack(value, 1.70158f);
    }

    public static float easeInBack(float value, float overshoot) {
        float t = clamp01(value);
        float c1 = Math.max(0f, overshoot);
        float c3 = c1 + 1f;
        return c3 * t * t * t - c1 * t * t;
    }

    public static float easeOutBack(float value) {
        return easeOutBack(value, 1.70158f);
    }

    public static float easeOutBack(float value, float overshoot) {
        float t = clamp01(value);
        float c1 = Math.max(0f, overshoot);
        float c3 = c1 + 1f;
        float p = t - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }

    public static float easeInOutBack(float value) {
        return easeInOutBack(value, 1.70158f);
    }

    public static float easeInOutBack(float value, float overshoot) {
        float t = clamp01(value);
        float c2 = Math.max(0f, overshoot) * 1.525f;
        if (t < 0.5f) {
            float p = 2f * t;
            return p * p * ((c2 + 1f) * p - c2) * 0.5f;
        }
        float p = 2f * t - 2f;
        return (p * p * ((c2 + 1f) * p + c2) + 2f) * 0.5f;
    }

    public static float easeOutBounce(float value) {
        float t = clamp01(value);
        final float n1 = 7.5625f;
        final float d1 = 2.75f;

        if (t < 1f / d1) {
            return n1 * t * t;
        }
        if (t < 2f / d1) {
            float p = t - 1.5f / d1;
            return n1 * p * p + 0.75f;
        }
        if (t < 2.5f / d1) {
            float p = t - 2.25f / d1;
            return n1 * p * p + 0.9375f;
        }

        float p = t - 2.625f / d1;
        return n1 * p * p + 0.984375f;
    }

    public static float easeInBounce(float value) {
        float t = clamp01(value);
        return 1f - easeOutBounce(1f - t);
    }

    public static float easeInOutBounce(float value) {
        float t = clamp01(value);
        return t < 0.5f
                ? (1f - easeOutBounce(1f - 2f * t)) * 0.5f
                : (1f + easeOutBounce(2f * t - 1f)) * 0.5f;
    }

    public static boolean blink(long intervalMs) {
        return blink(intervalMs, Mode.FPS);
    }

    public static boolean blink(long intervalMs, Mode mode) {
        if (intervalMs <= 0L) return true;
        return ((long) (timelineMillis(mode) / intervalMs)) % 2L == 0L;
    }

    public static float fast(float end, float start, float multiple) {
        return fast(end, start, multiple, Mode.FPS);
    }

    public static float fast(float end, float start, float multiple, Mode mode) {
        float clampedDelta = Mth.clamp(deltaTime(mode) * multiple, 0f, 1f);
        return approach(end, start, clampedDelta);
    }
}
