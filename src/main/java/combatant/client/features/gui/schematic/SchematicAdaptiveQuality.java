/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.schematic;

/**
 * Frame-rate pressure is independent of distance LOD: it only moves the existing
 * LOD transitions and view distance after sustained low FPS. A one-frame hitch never drops a tier.
 */
final class SchematicAdaptiveQuality {
    static final int DEFAULT_TARGET_FPS = 40;
    private static final float REDUCE_DELAY = 0.75f;
    private static final float RECOVER_DELAY = 2.5f;
    private int targetFps = DEFAULT_TARGET_FPS;
    private float lowSeconds, highSeconds;
    private float quality = 1f;
    private float target = 1f;

    void setTargetFps(int fps) {
        targetFps = Math.max(20, Math.min(240, fps));
    }

    int targetFps() { return targetFps; }

    float quality() {
        return quality;
    }

    void update(float seconds, int fps) {
        float dt = Math.max(0f, Math.min(0.1f, seconds));
        if (fps <= 0) return; // FastFps has not yet obtained a stable sample.
        if (fps < targetFps) {
            lowSeconds += dt;
            highSeconds = 0f;
            if (lowSeconds >= REDUCE_DELAY) {
                float pressure = (targetFps - fps) / (float) targetFps;
                target = Math.max(0.60f, Math.min(target, 1f - pressure * 0.72f));
            }
        } else if (fps >= targetFps + 7) {
            highSeconds += dt;
            lowSeconds = 0f;
            if (highSeconds >= RECOVER_DELAY) {
                target = 1f;
            }
        } else {
            lowSeconds = highSeconds = 0f;
        }
        float smoothing = 1f - (float) Math.exp(-3.0f * dt);
        quality += (target - quality) * smoothing;
    }
}
