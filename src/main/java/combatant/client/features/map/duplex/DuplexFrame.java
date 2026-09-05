/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

import java.util.Arrays;
import java.util.UUID;

public record DuplexFrame(
        DuplexMessageType type,
        long sequence,
        UUID sessionId,
        UUID senderId,
        byte[] payload
) {
    public DuplexFrame {
        if (type == null || sessionId == null || senderId == null) {
            throw new IllegalArgumentException("Missing frame identity");
        }
        payload = payload == null ? new byte[0] : payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DuplexFrame other)) return false;
        return type == other.type && sequence == other.sequence
                && sessionId.equals(other.sessionId) && senderId.equals(other.senderId)
                && Arrays.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        return 31 * java.util.Objects.hash(type, sequence, sessionId, senderId) + Arrays.hashCode(payload);
    }
}
