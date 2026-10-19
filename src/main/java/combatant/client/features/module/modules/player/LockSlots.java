/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.player.inventory.InventorySlotLocks;

/**
 * Keeps the chosen hotbar slots (and optionally the offhand) where they are: no dropping, shift-clicking, dragging,
 * number-key or offhand swapping of those stacks. The guards live in the shared {@link InventorySlotLocks}
 * reservations, which already cover container clicks, creative-inventory writes, Q/offhand keys and the HUD tint on
 * the locked slots; this module only holds the reservations while it is enabled. Automation that runs through the
 * inventory runtime respects them as well.
 */
@ModuleInfo(
        id = "lockslots",
        displayName = "LockSlots",
        aliases = {"SlotLock", "HotbarLock"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.lockslots.description")
public final class LockSlots extends Module {

    private static final int HOTBAR_SLOTS = 9;

    private final BooleanValue[] hotbar = new BooleanValue[HOTBAR_SLOTS];
    private final BooleanValue offhand = bool("lockSlotsOffhand", "lock_offhand", false);

    /** One reservation key per slot: {@link InventorySlotLocks} keeps a single slot per owner. */
    private final Object[] owners = new Object[HOTBAR_SLOTS + 1];

    public LockSlots() {
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            hotbar[i] = bool("lockSlotsSlot" + (i + 1), "slot_" + (i + 1), false);
        }
        for (int i = 0; i < owners.length; i++) owners[i] = new Object();
    }

    @Override
    public void onDisable() {
        for (Object owner : owners) InventorySlotLocks.release(owner);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        // Re-claimed every tick: the reservation table is cleared on every new player session.
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            if (hotbar[i].get()) {
                InventorySlotLocks.claim(owners[i], i, "LockSlots");
            } else {
                InventorySlotLocks.release(owners[i]);
            }
        }
        Object offhandOwner = owners[HOTBAR_SLOTS];
        if (offhand.get()) {
            InventorySlotLocks.claim(offhandOwner, InventorySlotLocks.OFFHAND, "LockSlots");
        } else {
            InventorySlotLocks.release(offhandOwner);
        }
    }
}
