/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

/** Per-frame UI compiler and clip counters exported to Tracy. */
public record UiPipelineStatsSnapshot(
        long frameId,
        int analyticClipPushes,
        int stencilClipPushes,
        int stencilMaskPasses,
        int stencilMaskPrimitives,
        int stencilRestorePasses,
        int stencilTestTransitions,
        int stencilClears,
        int stencilRenderPasses,
        int stencilMaxDepth,
        int msaaLayerAttempts,
        int msaaLayerBegins,
        int msaaLayerFallbacks,
        int msaaResolves,
        int msaaResolveFailures,
        int msaaComposites,
        int msaaDiscards,
        int msaaTargetAllocations,
        int msaaTargetResizes,
        long msaaPixelSamples,
        int directPasses,
        int itemPasses,
        int mixedItemPasses,
        int legacyPasses,
        int legacyMixedItems,
        int legacyBlurCapture,
        int legacyPreparedGlass,
        int legacyUnsupported
) {
}
