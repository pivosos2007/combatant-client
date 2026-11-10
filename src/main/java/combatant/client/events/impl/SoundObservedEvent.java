/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import lombok.RequiredArgsConstructor;

/** Sound received before vanilla handling; unresolved entity sound packets are not posted. */
@Getter
@RequiredArgsConstructor
public final class SoundObservedEvent extends Event {
    private final Identifier soundId;
    private final Vec3 position;
}
