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
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;

/** Server abilities packet before vanilla handles it; cancellation suppresses that handling. */
@Getter
@RequiredArgsConstructor
public final class ClientPlayerAbilitiesEvent extends Event {
    private final ClientboundPlayerAbilitiesPacket packet;
}
