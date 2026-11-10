/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiPipelineTelemetryTest {
    @Test
    void recordsOnlyMergedDirectSequencesAndResetsPerFrame() {
        UiPipelineTelemetry.beginFrame(42);
        UiPipelineTelemetry.recordDirectPass();
        UiPipelineTelemetry.recordDirectPass();
        UiPipelineTelemetry.recordDirectCoalescing(1);
        UiPipelineTelemetry.recordDirectCoalescing(3);

        var first = UiPipelineTelemetry.snapshot();
        assertEquals(42, first.frameId());
        assertEquals(2, first.directPasses());
        assertEquals(1, first.directCoalescedGroups());
        assertEquals(2, first.directPassesSaved());

        UiPipelineTelemetry.beginFrame(43);
        var next = UiPipelineTelemetry.snapshot();
        assertEquals(43, next.frameId());
        assertEquals(0, next.directPasses());
        assertEquals(0, next.directCoalescedGroups());
        assertEquals(0, next.directPassesSaved());
    }
}
