/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

/**
 * Reports resources occupied by code that does not go through the coordinator yet, such as a
 * module holding an {@code InventorySwap} hotbar lease directly. Such holders cannot be preempted,
 * so the coordinator treats them as busy regardless of priority.
 */
@FunctionalInterface
public interface ResourceProbe {
    ResourceProbe NONE = (resource, requester) -> false;

    boolean externallyHeld(ActionResource resource, LeaseHolder requester);
}
