/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.player.LocalPlayer;

/** Allows movement modules to treat lateral or backward key presses as forward impulse for sprint gating. */
@Getter
@RequiredArgsConstructor
public final class DirectionalSprintQueryEvent extends Event {
    private final LocalPlayer player;
    private boolean allowed;

    public void allow() {
        allowed = true;
    }
}
