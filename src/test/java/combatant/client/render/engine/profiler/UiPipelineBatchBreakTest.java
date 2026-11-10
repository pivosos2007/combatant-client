/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.profiler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UiPipelineBatchBreakTest {
    @Test
    void recordsExclusiveBreakReasonsAndResetsOnFrameStart() {
        UiPipelineTelemetry.beginFrame(100);
        UiPipelineTelemetry.recordBatchBreak(UiPipelineTelemetry.BatchBreakCause.ORDER);
        UiPipelineTelemetry.recordBatchBreak(UiPipelineTelemetry.BatchBreakCause.TYPE);
        UiPipelineTelemetry.recordBatchBreak(UiPipelineTelemetry.BatchBreakCause.SCISSOR);
        UiPipelineTelemetry.recordBatchBreak(UiPipelineTelemetry.BatchBreakCause.CLIP);
        UiPipelineTelemetry.recordBatchBreak(UiPipelineTelemetry.BatchBreakCause.SAMPLER);
        UiPipelineTelemetry.recordBatchBreak(UiPipelineTelemetry.BatchBreakCause.PARAMETERS);
        UiPipelineTelemetry.recordHudMarker(true);
        UiPipelineTelemetry.recordHudMarker(false);
        UiPipelineTelemetry.recordHudMarker(false);
        UiPipelineStatsSnapshot stats = UiPipelineTelemetry.snapshot();
        assertEquals(100, stats.frameId());
        assertEquals(1, stats.batchBreakOrder());
        assertEquals(1, stats.batchBreakType());
        assertEquals(1, stats.batchBreakScissor());
        assertEquals(1, stats.batchBreakClip());
        assertEquals(1, stats.batchBreakSampler());
        assertEquals(1, stats.batchBreakParameters());
        assertEquals(1, stats.hudMarkersEmitted());
        assertEquals(2, stats.hudMarkersSkipped());

        UiPipelineTelemetry.beginFrame(101);
        UiPipelineStatsSnapshot reset = UiPipelineTelemetry.snapshot();
        assertEquals(0, reset.batchBreakOrder());
        assertEquals(0, reset.batchBreakType());
        assertEquals(0, reset.batchBreakScissor());
        assertEquals(0, reset.batchBreakClip());
        assertEquals(0, reset.batchBreakSampler());
        assertEquals(0, reset.batchBreakParameters());
        assertEquals(0, reset.hudMarkersEmitted());
        assertEquals(0, reset.hudMarkersSkipped());
    }
}
