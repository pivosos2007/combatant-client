package combatant.client.features.gui.component.solid;

final class SolidMotionSignal {
    private float value;
    private float from;
    private float target;
    private float elapsedMs;
    private long durationMs;

    SolidMotionSignal(float value) {
        this.value = this.from = this.target = value;
    }

    void target(float next, long durationMs) {
        next = clamp01(next);
        if (Float.compare(next, target) == 0) return;
        from = value;
        target = next;
        elapsedMs = 0.0f;
        this.durationMs = Math.max(1L, durationMs);
    }

    void snap(float next) {
        value = from = target = clamp01(next);
        elapsedMs = 0.0f;
    }

    void advance(float deltaMs) {
        if (Float.compare(value, target) == 0) return;
        elapsedMs = Math.min(durationMs, elapsedMs + Math.max(0.0f, deltaMs));
        float t = elapsedMs / durationMs;
        value = from + (target - from) * cubicBezier(t, 0.45f, 1.45f, 0.49f, 1.15f);
        if (elapsedMs >= durationMs) value = target;
    }

    float value() { return clamp01(value); }
    float target() { return target; }
    boolean settled() { return Float.compare(value, target) == 0; }

    static float cubicBezier(float x, float x1, float y1, float x2, float y2) {
        if (x <= 0.0f) return 0.0f;
        if (x >= 1.0f) return 1.0f;
        float u = x;
        for (int i = 0; i < 8; i++) {
            float estimate = bezier(u, x1, x2);
            float derivative = bezierDerivative(u, x1, x2);
            if (Math.abs(estimate - x) < 1.0e-5f || Math.abs(derivative) < 1.0e-6f) break;
            u = Math.max(0.0f, Math.min(1.0f, u - (estimate - x) / derivative));
        }
        return bezier(u, y1, y2);
    }

    private static float bezier(float t, float a, float b) {
        float inv = 1.0f - t;
        return 3.0f * inv * inv * t * a + 3.0f * inv * t * t * b + t * t * t;
    }

    private static float bezierDerivative(float t, float a, float b) {
        return 3.0f * ((1.0f - t) * (1.0f - 3.0f * t) * a + (2.0f * t - 3.0f * t * t) * b) + 3.0f * t * t;
    }

    private static float clamp01(float v) { return Math.max(0.0f, Math.min(1.0f, v)); }
}
