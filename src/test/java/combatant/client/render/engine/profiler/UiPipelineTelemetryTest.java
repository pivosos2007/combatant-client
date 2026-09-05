/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class UiPipelineTelemetryTest {
    @Test
    void capturesClipMsaaAndCompilerCounters() {
        UiPipelineTelemetry.beginFrame(41L);
        UiPipelineTelemetry.recordAnalyticClipPush();
        UiPipelineTelemetry.recordStencilClipPush(3);
        UiPipelineTelemetry.recordStencilMaskPass(2);
        UiPipelineTelemetry.recordStencilRestorePass();
        UiPipelineTelemetry.recordStencilTestTransition();
        UiPipelineTelemetry.recordStencilClear();
        UiPipelineTelemetry.recordStencilRenderPass();
        UiPipelineTelemetry.recordMsaaLayerAttempt();
        UiPipelineTelemetry.recordMsaaLayerBegin(100, 50, 2);
        UiPipelineTelemetry.recordMsaaResolve(true);
        UiPipelineTelemetry.recordMsaaComposite();
        UiPipelineTelemetry.recordDirectPass();
        UiPipelineTelemetry.recordItemPass();
        UiPipelineTelemetry.recordMixedItemPass();
        UiPipelineTelemetry.recordLegacyPass("mixed_items+blur_capture");

        UiPipelineStatsSnapshot s = UiPipelineTelemetry.snapshot();
        assertEquals(41L, s.frameId());
        assertEquals(1, s.analyticClipPushes());
        assertEquals(1, s.stencilClipPushes());
        assertEquals(3, s.stencilMaxDepth());
        assertEquals(1, s.stencilMaskPasses());
        assertEquals(2, s.stencilMaskPrimitives());
        assertEquals(1, s.stencilRenderPasses());
        assertEquals(1, s.msaaLayerBegins());
        assertEquals(10_000L, s.msaaPixelSamples());
        assertEquals(1, s.mixedItemPasses());
        assertEquals(1, s.legacyPasses());
        assertEquals(1, s.legacyMixedItems());
        assertEquals(1, s.legacyBlurCapture());
        assertEquals(0, s.legacyPreparedGlass());
    }

    @Test
    void beginFrameResetsAllCounters() {
        UiPipelineTelemetry.beginFrame(1L);
        UiPipelineTelemetry.recordMsaaLayerFallback();
        UiPipelineTelemetry.recordLegacyPass("prepared_glass");
        UiPipelineTelemetry.beginFrame(2L);

        UiPipelineStatsSnapshot s = UiPipelineTelemetry.snapshot();
        assertEquals(2L, s.frameId());
        assertEquals(0, s.msaaLayerFallbacks());
        assertEquals(0, s.legacyPasses());
        assertEquals(0, s.legacyPreparedGlass());
    }
}
