/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

/**
 * Paces the sneak key so rapid toggles never look like a fast-sneak cheat (Vulcan BadPackets K): a sneak is held
 * for at least {@link #MIN_HOLD} ticks and two sneak starts are at least {@link #MIN_START_GAP} ticks apart.
 * Call {@link #next} exactly once per tick.
 */
final class SneakPacing {
    static final int MIN_HOLD = 2;
    static final int MIN_START_GAP = 3;

    private boolean sneaking;
    private int held;
    private int sinceStart = MIN_START_GAP;

    /** The sneak state to send this tick given what the caller wants. */
    boolean next(boolean want) {
        if (sinceStart < MIN_START_GAP) sinceStart++;
        if (sneaking) {
            if (!want && held >= MIN_HOLD) {
                sneaking = false;
            } else {
                held++;
            }
        } else if (want && sinceStart >= MIN_START_GAP) {
            sneaking = true;
            held = 1;
            sinceStart = 0;
        }
        return sneaking;
    }

    void reset() {
        sneaking = false;
        held = 0;
        sinceStart = MIN_START_GAP;
    }
}
