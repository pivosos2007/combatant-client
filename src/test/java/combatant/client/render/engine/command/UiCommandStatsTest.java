/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class UiCommandStatsTest {
    @Test
    void legacySpecialPassesAreReportedSeparatelyFromCompiledPasses() {
        UiCommandStats stats = new UiCommandStats();
        stats.beginFrame(17L);
        stats.addCompiledPasses(5);
        stats.addCompiledOrderedBatches(11);
        stats.addCompiledLegacySpecialPasses(2);

        UiStatsSnapshot snapshot = stats.snapshot();
        assertEquals(5, snapshot.compiledPasses());
        assertEquals(11, snapshot.compiledOrderedBatches());
        assertEquals(2, snapshot.compiledLegacySpecialPasses());
    }

    @Test
    void aNewFrameResetsLegacySpecialPassCount() {
        UiCommandStats stats = new UiCommandStats();
        stats.beginFrame(1L);
        stats.addCompiledLegacySpecialPasses(3);
        stats.beginFrame(2L);

        assertEquals(0, stats.snapshot().compiledLegacySpecialPasses());
    }
}
