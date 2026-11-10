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
import lombok.RequiredArgsConstructor;

/** Sound packet before vanilla handling; cancellation suppresses playback. */
@Getter
@RequiredArgsConstructor
public final class SoundFilterEvent extends Event {
    private final Identifier soundId;
}
