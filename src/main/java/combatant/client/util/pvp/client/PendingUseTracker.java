/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp.client;

import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks item-use attempts pending server confirmation.
 * Records the affected slot, item count and attempt tick.
 */
public class PendingUseTracker {

    private final Map<Integer, Pending> pending = new HashMap<>();

    /**
     * Records an item-use attempt for a slot.
     */
    public void start(Item item, int slot, int count, long now) {
        if (slot >= 0 && slot <= 8) {
            slot += 36;
        }
        pending.put(slot, new Pending(item, slot, count, now));
    }

    /**
 * Removes the pending attempt and returns it if the observed item count is no greater than
 * the count recorded at submission. A greater count discards the attempt and returns null.
 */
    public Pending consumeIfConfirmed(int slot, int newCount) {
        Pending p = pending.get(slot);
        if (p == null) return null;

        if (newCount <= p.count) {
            pending.remove(slot);
            return p;
        }

        // The server restored the consumed stack.
        if (newCount > p.count) {
            pending.remove(slot);
        }

        return null;
    }

    public void clear() {
        pending.clear();
    }

    public record Pending(Item item, int slot, int count, long startedAtMs) {
    }
}
