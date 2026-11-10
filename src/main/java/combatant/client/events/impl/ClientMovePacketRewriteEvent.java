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
import lombok.Setter;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** Request a replacement for an outgoing movement packet before connection submission. */
@Getter
@RequiredArgsConstructor
public final class ClientMovePacketRewriteEvent extends Event {
    private final ServerboundMovePlayerPacket packet;
    @Setter
    private ServerboundMovePlayerPacket replacement;
}
