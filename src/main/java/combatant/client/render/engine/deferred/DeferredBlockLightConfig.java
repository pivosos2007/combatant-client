/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/** Numerical/quality policy for the renderer-owned colored block-light volume. */
record DeferredBlockLightConfig(
        boolean enabled,
        int sizeX,
        int sizeY,
        int sizeZ,
        int originAlignment,
        int seedRefreshIntervalFrames,
        int propagationIterationsPerFrame,
        float surfaceSampleOffset,
        float edgeFadeStart,
        float edgeFadeEnd
) {
    /*
     * The block-light field is a persistent, camera-position-centered world-space volume. Camera
     * rotation never changes its coverage; only block-aligned camera translation scrolls the field.
     * Propagation advances a small bounded amount every frame instead of rebuilding many full-volume
     * flood-fill passes whenever the camera crosses a cell boundary.
     */
    private static final DeferredBlockLightConfig DEFAULT = new DeferredBlockLightConfig(
            true,
            64, 48, 64,
            4,
            4,
            1,
            0.5f,
            0.75f,
            1.0f
    );

    DeferredBlockLightConfig {
        sizeX = clamp(sizeX, 8, 128);
        sizeY = clamp(sizeY, 8, 128);
        sizeZ = clamp(sizeZ, 8, 128);
        originAlignment = clamp(originAlignment, 1, 16);
        seedRefreshIntervalFrames = clamp(seedRefreshIntervalFrames, 1, 120);
        propagationIterationsPerFrame = clamp(propagationIterationsPerFrame, 1, 4);
        surfaceSampleOffset = finiteClamp(surfaceSampleOffset, 0.0f, 1.0f, 0.5f);
        edgeFadeStart = finiteClamp(edgeFadeStart, 0.0f, 1.0f, 0.75f);
        edgeFadeEnd = finiteClamp(edgeFadeEnd, edgeFadeStart + 0.01f, 1.25f, 1.0f);
    }

    static DeferredBlockLightConfig current() {
        return DEFAULT;
    }

    int atlasColumns() {
        return Math.max(1, (int) Math.ceil(Math.sqrt(sizeZ)));
    }

    int atlasRows() {
        return (sizeZ + atlasColumns() - 1) / atlasColumns();
    }

    int atlasWidth() {
        return Math.multiplyExact(sizeX, atlasColumns());
    }

    int atlasHeight() {
        return Math.multiplyExact(sizeY, atlasRows());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
