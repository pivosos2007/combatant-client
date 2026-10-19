/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

import combatant.client.util.player.inventory.InventorySwap;

/**
 * Reports slot leases taken directly through {@link InventorySwap} by modules that are not
 * migrated to the runtime. Runtime actions lease InventorySwap with themselves as owner, so their
 * own backend lease is never reported as external.
 */
final class LegacyResourceProbe implements ResourceProbe {

    @Override
    public boolean externallyHeld(ActionResource resource, LeaseHolder requester) {
        InventorySwap swap = InventorySwap.INSTANCE;
        return switch (resource) {
            case HOTBAR -> swap.isHotbarLeased() && !swap.isHotbarLeasedBy(requester)
                    && !swap.isHotbarLeasedBy(requester.slotOwner());
            case INVENTORY -> (swap.isInventoryLeased() && !swap.isInventoryLeasedBy(requester))
                    || swap.hasPendingStrictActions();
            default -> false;
        };
    }
}
