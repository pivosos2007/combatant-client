/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.player.LocalPlayer;

/** Carries mutable effect gravity alongside the unchanged normal-gravity reference. */
@Getter
public final class PlayerEffectGravityEvent extends Event {
    private final LocalPlayer player;
    private final double normalGravity;
    @Setter
    private double gravity;

    public PlayerEffectGravityEvent(LocalPlayer player, double gravity, double normalGravity) {
        this.player = player;
        this.gravity = gravity;
        this.normalGravity = normalGravity;
    }

}
