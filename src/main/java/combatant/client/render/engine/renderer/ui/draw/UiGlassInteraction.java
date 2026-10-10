/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

/** Per-element liquidGL-style optical impulse. Positions/offsets are normalized to surface bounds. */
public record UiGlassInteraction(float x, float y, float dx, float dy,
                                 float activity, float press, float open, boolean local) {
    /** Legacy Java-drawn surfaces use the frame-level pointer when no owner is available. */
    public static final UiGlassInteraction FRAME_POINTER = new UiGlassInteraction(
            0.5f, 0.5f, 0f, 0f, 0f, 0f, 0f, false);

    public UiGlassInteraction {
        x = finite(x, 0.5f);
        y = finite(y, 0.5f);
        dx = clamp(finite(dx, 0f), -0.20f, 0.20f);
        dy = clamp(finite(dy, 0f), -0.20f, 0.20f);
        activity = clamp(finite(activity, 0f), 0f, 1f);
        press = clamp(finite(press, 0f), 0f, 1f);
        open = clamp(finite(open, 0f), 0f, 1f);
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
}
