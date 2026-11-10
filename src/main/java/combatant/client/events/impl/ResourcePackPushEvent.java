/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.Connection;
import lombok.RequiredArgsConstructor;

/** Resource-pack push before vanilla processing; cancellation suppresses vanilla handling. */
@Getter
@RequiredArgsConstructor
public final class ResourcePackPushEvent extends Event {
    private final ClientboundResourcePackPushPacket packet;
    private final Connection connection;
}
