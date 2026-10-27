/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

public interface LeaseHolder {
    /**
     * Called synchronously when the coordinator takes a resource away. The holder must drop any
     * external state bound to it (RotationManager request, InventorySwap lease) before returning,
     * because the new owner may claim the same backend in the same call. Must not acquire leases.
     */
    void onLeaseRevoked(ActionLease lease, ActionLease.RevokeReason reason);

    /**
     * The object that owns this holder's InventorySwap hotbar lease. Defaults to the holder itself;
     * actions that share a long-lived slot lease with their queue (BlockPlacer) return that owner so
     * the legacy probe does not report the shared lease as external.
     */
    default Object slotOwner() {
        return this;
    }
}
