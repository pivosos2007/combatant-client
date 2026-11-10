/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;

/** Requests a movement and position-packet freeze; modules may enable the request. */
@Getter
public final class PlayerFreezeQueryEvent extends Event {
    private final LocalPlayer player;
    private boolean frozen;

    public PlayerFreezeQueryEvent(LocalPlayer player) {
        this.player = player;
    }

    /**
     * Latches a freeze request. Once enabled by one subscriber, another
     * subscriber cannot clear it during the same dispatch.
     */
    public void setFrozen(boolean frozen) { if (frozen) this.frozen = true; }
}
