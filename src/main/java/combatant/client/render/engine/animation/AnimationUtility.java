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

    public static float deltaTime() {
        return FastFps.getDeltaSeconds();
    }

    public static float time(float speed) {
        return (System.currentTimeMillis() % 1_000_000L) * speed;
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

    public static float snap(float value, float target, float epsilon) {
        return Math.abs(value - target) <= Math.max(0f, epsilon) ? target : value;
    }

    public static float smoothstep(float value) {
        float t = clamp01(value);
        return t * t * (3f - 2f * t);
    }

    public static float easeOutCubic(float value) {
        float t = clamp01(value);
        return 1f - (float) Math.pow(1f - t, 3f);
    }

    public static float easeInCubic(float value) {
        float t = clamp01(value);
        return t * t * t;
    }

    public static float easeInOutCubic(float value) {
        float t = clamp01(value);
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3f) * 0.5f;
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

    public static float easeInBack(float value) {
        return easeInBack(value, 1.70158f);
    }

    public static float easeInBack(float value, float overshoot) {
        float t = clamp01(value);
        float c1 = Math.max(0f, overshoot);
        float c3 = c1 + 1f;
        return c3 * t * t * t - c1 * t * t;
    }

    public static boolean blink(long intervalMs) {
        if (intervalMs <= 0L) return true;
        return (System.currentTimeMillis() / intervalMs) % 2L == 0L;
    }

    public static float fast(float end, float start, float multiple) {
        float clampedDelta = Mth.clamp(deltaTime() * multiple, 0f, 1f);
        return approach(end, start, clampedDelta);
    }
}
