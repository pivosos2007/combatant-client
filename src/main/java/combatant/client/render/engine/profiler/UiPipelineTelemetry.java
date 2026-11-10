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
    private static int msaaDiscards;
    private static int msaaTargetAllocations;
    private static int msaaTargetResizes;
    private static long msaaPixelSamples;
    private static int directPasses;
    private static int directCoalescedGroups;
    private static int directPassesSaved;
    private static int itemPasses;
    private static int mixedItemPasses;
    private static int captureAwarePasses;
    private static int captureAwareMixedItems;
    private static int captureAwareBlurCapture;
    private static int captureAwarePreparedGlass;
    private static int captureAwareUnsupported;

    /** Reasons the last ordered UI entry could not absorb the next one. */
    public enum BatchBreakCause { ORDER, TYPE, SCISSOR, CLIP, SAMPLER, PARAMETERS }

    private static int hudMarkersEmitted;
    private static int hudMarkersSkipped;
    private static int batchBreakOrder;
    private static int batchBreakType;
    private static int batchBreakScissor;
    private static int batchBreakClip;
    private static int batchBreakSampler;
    private static int batchBreakParameters;

    public static void recordHudMarker(boolean emitted) {
        if (emitted) hudMarkersEmitted++;
        else hudMarkersSkipped++;
    }

    public static void recordBatchBreak(BatchBreakCause reason) {
        switch (reason) {
            case ORDER -> batchBreakOrder++;
            case TYPE -> batchBreakType++;
            case SCISSOR -> batchBreakScissor++;
            case CLIP -> batchBreakClip++;
            case SAMPLER -> batchBreakSampler++;
            case PARAMETERS -> batchBreakParameters++;
        }
    }

    public static void beginFrame(long nextFrameId) {
        frameId = nextFrameId;
        hudMarkersEmitted = hudMarkersSkipped = 0;
        batchBreakOrder = batchBreakType = batchBreakScissor = batchBreakClip = 0;
        batchBreakSampler = batchBreakParameters = 0;
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
        msaaDiscards = 0;
        msaaTargetAllocations = 0;
        msaaTargetResizes = 0;
        msaaPixelSamples = 0L;
        directPasses = 0;
        directCoalescedGroups = 0;
        directPassesSaved = 0;
        itemPasses = 0;
        mixedItemPasses = 0;
        captureAwarePasses = 0;
        captureAwareMixedItems = 0;
        captureAwareBlurCapture = 0;
        captureAwarePreparedGlass = 0;
        captureAwareUnsupported = 0;
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

    public static void recordMsaaDiscard() {
        msaaDiscards++;
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

    /** Counts facade pass boundaries removed by adjacent direct RHI stream coalescing. */
    public static void recordDirectCoalescing(int originalPasses) {
        if (originalPasses < 2) return;
        directCoalescedGroups++;
        directPassesSaved += originalPasses - 1;
    }

    public static void recordItemPass() {
        itemPasses++;
    }

    public static void recordMixedItemPass() {
        mixedItemPasses++;
    }

    public static void recordCaptureAwarePass(String reason) {
        captureAwarePasses++;
        String value = reason == null ? "" : reason;
        boolean classified = false;
        if (value.contains("mixed_items")) {
            captureAwareMixedItems++;
            classified = true;
        }
        if (value.contains("blur_capture")) {
            captureAwareBlurCapture++;
            classified = true;
        }
        if (value.contains("prepared_glass")) {
            captureAwarePreparedGlass++;
            classified = true;
        }
        if (!classified || value.contains("unknown") || value.contains("missing_")
                || value.contains("unsupported")) {
            captureAwareUnsupported++;
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
                msaaDiscards,
                msaaTargetAllocations,
                msaaTargetResizes,
                msaaPixelSamples,
                directPasses,
                directCoalescedGroups,
                directPassesSaved,
                itemPasses,
                mixedItemPasses,
                captureAwarePasses,
                captureAwareMixedItems,
                captureAwareBlurCapture,
                captureAwarePreparedGlass,
                captureAwareUnsupported,
                hudMarkersEmitted,
                hudMarkersSkipped,
                batchBreakOrder,
                batchBreakType,
                batchBreakScissor,
                batchBreakClip,
                batchBreakSampler,
                batchBreakParameters
        );
    }
}
