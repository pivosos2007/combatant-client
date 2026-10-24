/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.actions;

/**
 * Pure world-poll confirmation that a block is really gone. The client predicts a break and removes the block at
 * once; the server may put it back a few ticks later. Removal counts as confirmed only after it was observed for
 * {@code confirmTicks} consecutive ticks, and a block that keeps reappearing is a rejection.
 */
public final class RemovalConfirmation {

    public enum State {
        PENDING,
        CONFIRMED,
        REJECTED
    }

    private final int confirmTicks;
    private final int maxReappearances;
    private int goneTicks;
    private int reappearances;
    private boolean wasGone;

    public RemovalConfirmation(int confirmTicks, int maxReappearances) {
        this.confirmTicks = Math.max(1, confirmTicks);
        this.maxReappearances = Math.max(0, maxReappearances);
    }

    /** @param gone whether the block is currently absent from the client world */
    public State update(boolean gone) {
        if (gone) {
            goneTicks++;
            wasGone = true;
            return goneTicks >= confirmTicks ? State.CONFIRMED : State.PENDING;
        }
        goneTicks = 0;
        if (wasGone) {
            wasGone = false;
            reappearances++;
        }
        return reappearances > maxReappearances ? State.REJECTED : State.PENDING;
    }

    public int reappearances() {
        return reappearances;
    }
}
