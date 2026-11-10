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
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/** Boat tick completed; subscribers apply controller-owned rotation in priority order. */
@Getter
@RequiredArgsConstructor
public final class BoatRotationUpdateEvent extends Event {
    private final LocalPlayer player;
    private final AbstractBoat boat;
}
