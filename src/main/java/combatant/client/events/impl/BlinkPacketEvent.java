/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.events.impl;

import lombok.Getter;
import net.minecraft.network.protocol.Packet;
import combatant.client.events.Event;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.network.TransferOrigin;

/**
 * Resolves competing packet-delay decisions from registered listeners.
 * Actions are ordered {@code FLUSH < PASS < QUEUE}; lower-priority decisions
 * cannot replace an earlier higher-priority decision.
 */
public class BlinkPacketEvent extends Event {
    private final Packet<?> packet;
    @Getter
    private final TransferOrigin origin;
    @Getter
    private BlinkManager.Action action = BlinkManager.Action.FLUSH;

    public BlinkPacketEvent(Packet<?> packet, TransferOrigin origin) {
        this.packet = packet;
        this.origin = origin;
    }

    @SuppressWarnings("unchecked")
    public <T extends Packet<?>> T getPacket() {
        return (T) packet;
    }

    /**
     * Raises the requested action priority; {@code null} and weaker actions are ignored.
     * The default action is {@link BlinkManager.Action#FLUSH}.
     */
    public void setAction(BlinkManager.Action action) {
        if (action == null || this.action == action || this.action.getPriority() >= action.getPriority()) {
            return;
        }
        this.action = action;
    }
}
