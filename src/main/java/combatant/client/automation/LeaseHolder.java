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
     * Returns the owner used when acquiring this action's hotbar lease.
     * Actions sharing a lease return their common owner; otherwise the holder owns its own lease.
     */
    default Object slotOwner() {
        return this;
    }
}
