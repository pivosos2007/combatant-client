/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

/**
 * Render-thread counters for UI clipping and pass compilation.
 *
 * <p>The frame lifecycle exports this snapshot straight to Tracy before presentation.</p>
 */
public enum UiPipelineTelemetry {
    ;

    private static long frameId;
    private static int analyticClipPushes;
    private static int stencilClipPushes;
    private static int stencilMaskPasses;
    private static int stencilMaskPrimitives;
    private static int stencilRestorePasses;
    private static int stencilTestTransitions;
    private static int stencilClears;
    private static int stencilRenderPasses;
    private static int stencilMaxDepth;
    private static int msaaLayerAttempts;
    private static int msaaLayerBegins;
    private static int msaaLayerFallbacks;
    private static int msaaResolves;
    private static int msaaResolveFailures;
    private static int msaaComposites;
    private static int msaaTargetAllocations;
    private static int msaaTargetResizes;
    private static long msaaPixelSamples;
    private static int directPasses;
    private static int itemPasses;
    private static int mixedItemPasses;
    private static int legacyPasses;
    private static int legacyMixedItems;
    private static int legacyBlurCapture;
    private static int legacyPreparedGlass;
    private static int legacyUnsupported;

    public static void beginFrame(long nextFrameId) {
        frameId = nextFrameId;
        analyticClipPushes = 0;
        stencilClipPushes = 0;
        stencilMaskPasses = 0;
        stencilMaskPrimitives = 0;
        stencilRestorePasses = 0;
        stencilTestTransitions = 0;
        stencilClears = 0;
        stencilRenderPasses = 0;
        stencilMaxDepth = 0;
        msaaLayerAttempts = 0;
        msaaLayerBegins = 0;
        msaaLayerFallbacks = 0;
        msaaResolves = 0;
        msaaResolveFailures = 0;
        msaaComposites = 0;
        msaaTargetAllocations = 0;
        msaaTargetResizes = 0;
        msaaPixelSamples = 0L;
        directPasses = 0;
        itemPasses = 0;
        mixedItemPasses = 0;
        legacyPasses = 0;
        legacyMixedItems = 0;
        legacyBlurCapture = 0;
        legacyPreparedGlass = 0;
        legacyUnsupported = 0;
    }

    public static void recordAnalyticClipPush() {
        analyticClipPushes++;
    }

    public static void recordStencilClipPush(int depth) {
        stencilClipPushes++;
        stencilMaxDepth = Math.max(stencilMaxDepth, depth);
    }

    public static void recordStencilMaskPass(int primitives) {
        stencilMaskPasses++;
        stencilMaskPrimitives += Math.max(0, primitives);
    }

    public static void recordStencilRestorePass() {
        stencilRestorePasses++;
    }

    public static void recordStencilTestTransition() {
        stencilTestTransitions++;
    }

    public static void recordStencilClear() {
        stencilClears++;
    }

    public static void recordStencilRenderPass() {
        stencilRenderPasses++;
    }

    public static void recordMsaaLayerAttempt() {
        msaaLayerAttempts++;
    }

    public static void recordMsaaLayerBegin(int width, int height, int samples) {
        msaaLayerBegins++;
        msaaPixelSamples += Math.max(0L, (long) width * height * Math.max(1, samples));
    }

    public static void recordMsaaLayerFallback() {
        msaaLayerFallbacks++;
    }

    public static void recordMsaaResolve(boolean success) {
        if (success) msaaResolves++;
        else msaaResolveFailures++;
    }

    public static void recordMsaaComposite() {
        msaaComposites++;
    }

    public static void recordMsaaTargetAllocation() {
        msaaTargetAllocations++;
    }

    public static void recordMsaaTargetResize() {
        msaaTargetResizes++;
    }

    public static void recordDirectPass() {
        directPasses++;
    }

    public static void recordItemPass() {
        itemPasses++;
    }

    public static void recordMixedItemPass() {
        mixedItemPasses++;
    }

    public static void recordLegacyPass(String reason) {
        legacyPasses++;
        String value = reason == null ? "" : reason;
        boolean classified = false;
        if (value.contains("mixed_items")) {
            legacyMixedItems++;
            classified = true;
        }
        if (value.contains("blur_capture")) {
            legacyBlurCapture++;
            classified = true;
        }
        if (value.contains("prepared_glass")) {
            legacyPreparedGlass++;
            classified = true;
        }
        if (!classified || value.contains("unknown") || value.contains("missing_")
                || value.contains("unsupported")) {
            legacyUnsupported++;
        }
    }

    public static UiPipelineStatsSnapshot snapshot() {
        return new UiPipelineStatsSnapshot(
                frameId,
                analyticClipPushes,
                stencilClipPushes,
                stencilMaskPasses,
                stencilMaskPrimitives,
                stencilRestorePasses,
                stencilTestTransitions,
                stencilClears,
                stencilRenderPasses,
                stencilMaxDepth,
                msaaLayerAttempts,
                msaaLayerBegins,
                msaaLayerFallbacks,
                msaaResolves,
                msaaResolveFailures,
                msaaComposites,
                msaaTargetAllocations,
                msaaTargetResizes,
                msaaPixelSamples,
                directPasses,
                itemPasses,
                mixedItemPasses,
                legacyPasses,
                legacyMixedItems,
                legacyBlurCapture,
                legacyPreparedGlass,
                legacyUnsupported
        );
    }
}
