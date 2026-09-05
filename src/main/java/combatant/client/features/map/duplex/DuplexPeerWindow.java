/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DuplexPeerWindow {
    private final ConcurrentHashMap<UUID, Long> lastSequence = new ConcurrentHashMap<>();

    public boolean accept(DuplexFrame frame) {
        if (frame == null) return false;
        return lastSequence.compute(frame.senderId(), (id, previous) -> {
            if (previous == null || frame.sequence() > previous) return frame.sequence();
            return previous;
        }) == frame.sequence();
    }

    public void clear() {
        lastSequence.clear();
    }
}
