/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import lombok.Getter;
import net.minecraft.network.protocol.Packet;
import combatant.client.events.Event;
import combatant.client.util.network.TransferOrigin;

public class PacketEvent extends Event {
    private final Packet<?> packet;
    @Getter
    private final TransferOrigin origin;
    @Getter
    private final boolean original;

    public PacketEvent(Packet<?> packet) {
        this(packet, null, true);
    }

    public PacketEvent(Packet<?> packet, TransferOrigin origin, boolean original) {
        this.packet = packet;
        this.origin = origin;
        this.original = original;
    }

    @SuppressWarnings("unchecked")
    public <T extends Packet<?>> T getPacket() {
        return (T) this.packet;
    }

    public static class Send extends PacketEvent {
        public Send(Packet<?> packet) {
            super(packet, TransferOrigin.OUTGOING, true);
        }

        public Send(Packet<?> packet, boolean original) {
            super(packet, TransferOrigin.OUTGOING, original);
        }
    }

    public static class Receive extends PacketEvent {
        public Receive(Packet<?> packet) {
            super(packet, TransferOrigin.INCOMING, true);
        }

        public Receive(Packet<?> packet, boolean original) {
            super(packet, TransferOrigin.INCOMING, original);
        }
    }

    public static class SendPost extends PacketEvent {
        public SendPost(Packet<?> packet) {
            super(packet, TransferOrigin.OUTGOING, true);
        }
        @SuppressWarnings("unused")
        public SendPost(Packet<?> packet, boolean original) {
            super(packet, TransferOrigin.OUTGOING, original);
        }
    }

    public static class ReceivePost extends PacketEvent {
        public ReceivePost(Packet<?> packet) {
            super(packet, TransferOrigin.INCOMING, true);
        }
        @SuppressWarnings("unused")
        public ReceivePost(Packet<?> packet, boolean original) {
            super(packet, TransferOrigin.INCOMING, original);
        }
    }
}
