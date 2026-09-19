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
        int propagationIterationsOnRebuild,
        float chromaContourBlend,
        float surfaceSampleOffset,
        float edgeFadeStart,
        float edgeFadeEnd
) {
    /*
     * The block-light field is camera-position-centered and section aligned. The expensive color
     * propagation is rebuilt only when the covered section set or Minecraft light data changes;
     * unchanged frames only resolve the already converged volume.
     */
    private static final DeferredBlockLightConfig DEFAULT = new DeferredBlockLightConfig(
            true,
            128, 64, 128,
            16,
            600,
            15,
            0.18f,
            0.5f,
            0.90f,
            1.0f
    );

    DeferredBlockLightConfig {
        sizeX = clamp(sizeX, 8, 128);
        sizeY = clamp(sizeY, 8, 128);
        sizeZ = clamp(sizeZ, 8, 128);
        originAlignment = clamp(originAlignment, 1, 16);
        seedRefreshIntervalFrames = clamp(seedRefreshIntervalFrames, 1, 3600);
        propagationIterationsOnRebuild = clamp(propagationIterationsOnRebuild, 1, 15);
        chromaContourBlend = finiteClamp(chromaContourBlend, 0.0f, 0.5f, 0.18f);
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
