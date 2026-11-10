/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the greatest accepted sequence number per peer sender.
 * A frame is accepted only when its sequence advances the recorded value.
 */
public final class DuplexPeerWindow {
    private final ConcurrentHashMap<UUID, Long> lastSequence = new ConcurrentHashMap<>();

    /** Rejects stale or repeated sequences for each sender identity. */
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
