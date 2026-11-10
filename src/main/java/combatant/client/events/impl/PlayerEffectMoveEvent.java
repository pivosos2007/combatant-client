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

/** Carries the mutable movement vector affected by levitation or slow falling. */
@Getter
public final class PlayerEffectMoveEvent extends Event {
    public enum EffectType {
        LEVITATION,
        SLOW_FALLING
    }

    private final LocalPlayer player;
    private final EffectType effectType;
    @Setter
    private Vec3 movement;

    public PlayerEffectMoveEvent(LocalPlayer player, EffectType effectType, Vec3 movement) {
        this.player = player;
        this.effectType = effectType;
        this.movement = movement;
    }

}
