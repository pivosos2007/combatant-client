/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation;

/**
 * Player control domains that automation leases. One action owns each domain at a time; the
 * {@link ActionCoordinator} arbitrates by priority. Movement keys are split so a navigation task
 * can steer while another task holds only sneak (eagle bridging) or only jump.
 */
public enum ActionResource {
    /** Server-facing rotation, backed by {@code RotationManager}. */
    ROTATION,
    /** Forward/backward/left/right movement keys. */
    MOVEMENT,
    JUMP,
    SNEAK,
    SPRINT,
    /** Server-selected hotbar slot, backed by {@code InventorySwap} hotbar leases. */
    HOTBAR,
    /** Container clicks and inventory transactions, backed by {@code InventorySwap} inventory leases. */
    INVENTORY,
    /** Block use/destroy, item use and entity interaction packets. */
    INTERACTION;

    static final ActionResource[] VALUES = values();
}
