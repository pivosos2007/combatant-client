package combatant.client.features.gui.component.solid;

final class SolidScrollState {
    private float target;
    private float actual;
    private float maximum;
    private long visibleForMs;
    private boolean dragging;
    private float dragOffset;
    private final SolidMotionSignal visibility = new SolidMotionSignal(0.0f);
    private final SolidMotionSignal hover = new SolidMotionSignal(0.0f);

    void maximum(float maximum) {
        this.maximum = Math.max(0.0f, maximum);
        target = clamp(target, 0.0f, this.maximum);
        actual = clamp(actual, 0.0f, this.maximum);
    }

    void wheel(float delta) {
        target = clamp(target - delta * SolidStyleTokens.WHEEL_STEP, 0.0f, maximum);
        reveal();
    }

    void tick(float deltaMs, boolean hovered) {
        actual += (target - actual) * Math.min(1.0f, Math.max(0.0f, deltaMs) * 0.02f);
        if (Math.abs(target - actual) < 0.05f) actual = target;
        this.hover.target(hovered ? 1.0f : 0.0f, SolidStyleTokens.HOVER_MOTION_MS);
        this.hover.advance(deltaMs);
        if (visibleForMs > 0L) visibleForMs = Math.max(0L, visibleForMs - (long) deltaMs);
        visibility.target(maximum > 0.5f && (visibleForMs > 0L || hovered || dragging) ? 1.0f : 0.0f,
                SolidStyleTokens.VISIBILITY_MOTION_MS);
        visibility.advance(deltaMs);
    }

    void reveal() { visibleForMs = SolidStyleTokens.SCROLLBAR_TIMEOUT_MS; }
    float actual() { return actual; }
    float maximum() { return maximum; }
    float visibility() { return visibility.value(); }
    float hover() { return hover.value(); }
    boolean dragging() { return dragging; }
    void beginDrag(float offset) { dragging = true; dragOffset = offset; reveal(); }
    void endDrag() { dragging = false; }

    void dragTo(float pointer, float trackStart, float travel) {
        if (!dragging || travel <= 0.0f) return;
        float ratio = clamp((pointer - dragOffset - trackStart) / travel, 0.0f, 1.0f);
        target = actual = ratio * maximum;
        reveal();
    }

    void trackTo(float pointer, float trackStart, float trackLength) {
        if (trackLength <= 0.0f) return;
        target = clamp((pointer - trackStart) / trackLength * maximum, 0.0f, maximum);
        reveal();
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
