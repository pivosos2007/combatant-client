/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import net.minecraft.network.chat.Component;
import combatant.client.events.Event;

/**
 * Fired when an in-game HUD overlay message is shown (action bar).
 */
public class PvpOverlayEvent extends Event {
    public final Component message;
    public final boolean tinted;
    public final long timeMs;

    public PvpOverlayEvent(Component message, boolean tinted) {
        this(message, tinted, System.currentTimeMillis());
    }

    public PvpOverlayEvent(Component message, boolean tinted, long timeMs) {
        this.message = message;
        this.tinted = tinted;
        this.timeMs = timeMs;
    }
}
