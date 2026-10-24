/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Posted on the client thread before the current level is replaced (dimension change, join) or
 * dropped (disconnect). Positions, entities and leases tied to the old level are invalid after it.
 */
public final class WorldChangeEvent extends Event {

    public enum Reason {
        /** {@code Minecraft.setLevel}: joining a server or changing dimension. */
        LEVEL_CHANGE,
        /** {@code Minecraft.disconnect}. */
        DISCONNECT
    }

    private final Reason reason;
    private final ClientLevel newLevel;

    public WorldChangeEvent(Reason reason, ClientLevel newLevel) {
        this.reason = reason;
        this.newLevel = newLevel;
    }

    public Reason reason() {
        return reason;
    }

    /** The incoming level, null on disconnect. */
    public ClientLevel newLevel() {
        return newLevel;
    }
}
