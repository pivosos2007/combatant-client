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
import net.minecraft.world.phys.Vec3;

/** Carries the mutable velocity associated with an active movement effect. */
@Getter
public final class PlayerEffectVelocityEvent extends Event {
    private final LocalPlayer player;
    private final PlayerEffectMoveEvent.EffectType effectType;
    @Setter
    private Vec3 velocity;

    public PlayerEffectVelocityEvent(LocalPlayer player, PlayerEffectMoveEvent.EffectType effectType, Vec3 velocity) {
        this.player = player;
        this.effectType = effectType;
        this.velocity = velocity;
    }

}
