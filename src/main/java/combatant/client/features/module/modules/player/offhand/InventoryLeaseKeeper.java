/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.offhand;

import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.player.LocalPlayer;

import java.util.function.BooleanSupplier;

/**
 * The one inventory lease Offhand holds while any of its components has a window click in flight. Components
 * acquire it and schedule its release; it is only dropped once none of them is busy.
 */
public final class InventoryLeaseKeeper {
    private static final int HELD_TICKS = 80;

    private final Object owner;
    private BooleanSupplier busy = () -> false;
    private int releaseTick = -1;

    public InventoryLeaseKeeper(Object owner) {
        this.owner = owner;
    }

    /** What keeps the lease alive; set once after every component exists. */
    public void setBusy(BooleanSupplier busy) {
        this.busy = busy;
    }

    public boolean acquire(int ticks) {
        return InventorySwap.INSTANCE.leaseInventory(owner, ticks);
    }

    /** Release {@code delayTicks} from now unless something is busy by then. */
    public void scheduleRelease(LocalPlayer player, int delayTicks) {
        if (player == null) return;
        releaseTick = Math.max(releaseTick, player.tickCount + Math.max(0, delayTicks));
    }

    /** Drops the lease right now when nothing is busy. */
    public void releaseIfIdle() {
        if (!busy.getAsBoolean()) InventorySwap.INSTANCE.releaseInventory(owner);
    }

    /** Per tick: keeps the lease while busy, drops it once a scheduled release is due and nothing is busy. */
    public void tick(LocalPlayer player) {
        if (busy.getAsBoolean()) {
            InventorySwap.INSTANCE.leaseInventory(owner, HELD_TICKS);
            return;
        }
        if (player != null && releaseTick >= 0 && player.tickCount > releaseTick) {
            InventorySwap.INSTANCE.releaseInventory(owner);
            releaseTick = -1;
        }
    }

    public void reset() {
        releaseTick = -1;
        InventorySwap.INSTANCE.releaseInventory(owner);
    }
}
