/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.player;

/**
 * Correlates the same physical use press seen by pick() and startUseItem().
 * Observing a queued press is not the same as consuming it from KeyMapping:
 * vanilla must still perform its normal consumeClick() lifecycle.
 */
final class AccuratePlacementPressLedger {
    private int observed;

    int observe(int queuedPresses) {
        int count = Math.max(0, queuedPresses);
        int fresh = Math.max(0, count - observed);
        observed = count;
        return fresh;
    }

    void reset() {
        observed = 0;
    }
}
