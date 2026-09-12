/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.animation;

/**
 * Small retained scalar signal used by runtime-driven component visuals.
 *
 * <p>The script tree supplies semantic targets (hover/selected/enabled/etc.);
 * the Java runtime owns interpolation so those states continue to animate even
 * when a cached JS tree is not rebuilt every frame.</p>
 */
public final class UiMotionSignal {
    private boolean initialized;
    private float start;
    private float current;
    private float target;
    private long startedAtNanos;
    private long durationNanos;
    private UiEasing easing = UiEasing.EASE_OUT_CUBIC;

    public float sample(float nextTarget, long durationMs, UiEasing nextEasing, long nowNanos) {
        float safeTarget = Float.isFinite(nextTarget) ? nextTarget : 0.0f;
        UiEasing safeEasing = nextEasing != null ? nextEasing : UiEasing.EASE_OUT_CUBIC;
        long safeDuration = Math.max(0L, durationMs) * 1_000_000L;

        if (!initialized) {
            initialized = true;
            start = current = target = safeTarget;
            startedAtNanos = nowNanos;
            durationNanos = safeDuration;
            easing = safeEasing;
            return current;
        }

        current = valueAt(nowNanos);
        if (Float.compare(target, safeTarget) != 0 || durationNanos != safeDuration || easing != safeEasing) {
            start = current;
            target = safeTarget;
            startedAtNanos = nowNanos;
            durationNanos = safeDuration;
            easing = safeEasing;
        }
        current = valueAt(nowNanos);
        return current;
    }

    public float value() {
        return current;
    }

    private float valueAt(long nowNanos) {
        if (!initialized) return 0.0f;
        if (durationNanos <= 0L) return target;
        float t = (float) ((double) (nowNanos - startedAtNanos) / (double) durationNanos);
        if (t <= 0.0f) return start;
        if (t >= 1.0f) return target;
        float eased = easing.apply(t);
        return start + (target - start) * eased;
    }
}
