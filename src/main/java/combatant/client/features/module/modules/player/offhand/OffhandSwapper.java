/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.offhand;

import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventoryMover;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.PacketWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.BooleanSupplier;

/**
 * Tracks the single in-flight "move inventory slot into the offhand" request.
 * <p>
 * Lifecycle: {@link #request} (waiting for the click) -> the click is sent ({@code issuedTick} set) -> the offhand is
 * seen holding the item for {@link #CONFIRM_TICKS} (done) or {@link #TIMEOUT_TICKS} pass (abandoned). Clicks go
 * through {@link InventoryMover}, so the backend serializes them and enforces the Grim stationary rules.
 */
public final class OffhandSwapper {
    public static final int CONFIRM_TICKS = 2;
    public static final int TIMEOUT_TICKS = 10;
    private static final int LEASE_TICKS = 24;

    private final Minecraft mc = Minecraft.getInstance();
    private final Object owner;
    private final BooleanSupplier enabled;
    private final OffhandThreats threats;
    private final OffhandItems items;
    private final InventoryLeaseKeeper lease;
    private final Runnable onTimeout;

    private boolean pending;
    private ItemStack expected = ItemStack.EMPTY;
    private int requestTick = -1;
    private int issuedTick = -1;
    private int deadlineTick = -1;
    private int quietUntilTick = -1;
    /** Bumped on every reset so a click that was queued before it cannot touch the next request. */
    private int generation;

    public OffhandSwapper(Object owner, BooleanSupplier enabled, OffhandThreats threats, OffhandItems items,
                          InventoryLeaseKeeper lease, Runnable onTimeout) {
        this.owner = owner;
        this.enabled = enabled;
        this.threats = threats;
        this.items = items;
        this.lease = lease;
        this.onTimeout = onTimeout;
    }

    public boolean isPending() {
        return pending;
    }

    /** True while the request waits for its click (not sent yet). */
    public boolean isAwaitingClick() {
        return pending && issuedTick < 0;
    }

    public boolean isQuiet(LocalPlayer player) {
        return player.tickCount <= quietUntilTick;
    }

    /**
     * Asks for the item in inventory slot {@code slot} (0-35) to be moved into the offhand. An urgent request (a
     * totem needed now) may pause movement until the server sees a stationary player; a passive one is only made
     * while that already holds, so it never freezes the player. {@code false}: nothing was requested, retry later.
     */
    public boolean request(int slot, boolean urgent) {
        LocalPlayer player = mc.player;
        if (pending || slot < 0 || slot >= 36 || player == null) return false;
        if (!urgent && !PacketWindow.INSTANCE.stationaryForClick()) return false;
        ItemStack wanted = player.getInventory().getItem(slot);
        if (wanted.isEmpty() || !lease.acquire(LEASE_TICKS)) return false;

        pending = true;
        expected = wanted.copy();
        requestTick = player.tickCount;
        issuedTick = -1;
        deadlineTick = -1;
        int token = generation;
        boolean accepted = InventoryMover.toOffhand(owner, slot,
                stack -> token == generation && stillWanted(slot, stack, urgent),
                () -> onClickSent(token));
        if (!accepted) {
            reset();
            lease.releaseIfIdle();
        }
        return accepted;
    }

    /** A queued passive swap must never keep an emergency totem from being requested. */
    public void yieldToEmergency(LocalPlayer player) {
        if (!isAwaitingClick() || expected.is(Items.TOTEM_OF_UNDYING)) return;
        if (items.findTotemSlot(player) < 0 || !threats.shouldHoldTotem(player)) return;
        InventorySwap.INSTANCE.cancelQueuedInventoryActions(owner, InventoryActionKind.INVENTORY_CLICK);
        reset();
        lease.releaseIfIdle();
    }

    /**
     * Advances the request. Returns {@code true} while it still owns the offhand slot (the caller must not decide
     * anything else about the offhand this tick).
     */
    public boolean update(LocalPlayer player) {
        if (!pending) return false;
        if (player == null) {
            reset();
            return false;
        }

        if (issuedTick < 0) {
            boolean queued = InventorySwap.INSTANCE.hasQueuedInventoryAction(owner, InventoryActionKind.INVENTORY_CLICK);
            if (!queued || player.tickCount > requestTick + TIMEOUT_TICKS) {
                // The click was refused when it ran, or never got its window.
                reset();
                lease.releaseIfIdle();
                return false;
            }
            return true;
        }

        if (player.tickCount >= issuedTick + CONFIRM_TICKS && !expected.isEmpty()
                && ItemStack.isSameItemSameComponents(player.getOffhandItem(), expected)) {
            reset();
            quietUntilTick = player.tickCount + CONFIRM_TICKS;
            lease.scheduleRelease(player, 1);
            return false;
        }

        if (player.tickCount > deadlineTick) {
            reset();
            onTimeout.run();
            quietUntilTick = player.tickCount;
            lease.releaseIfIdle();
            return false;
        }
        return true;
    }

    public void reset() {
        pending = false;
        expected = ItemStack.EMPTY;
        requestTick = -1;
        issuedTick = -1;
        deadlineTick = -1;
        generation++;
    }

    private boolean stillWanted(int slot, ItemStack source, boolean urgent) {
        LocalPlayer player = mc.player;
        return enabled.getAsBoolean() && pending && player != null
                && ItemStack.isSameItemSameComponents(source, expected)
                && urgent == threats.shouldHoldTotem(player);
    }

    private void onClickSent(int token) {
        LocalPlayer player = mc.player;
        if (token != generation || !pending || player == null) return;
        issuedTick = player.tickCount;
        deadlineTick = player.tickCount + TIMEOUT_TICKS;
    }
}
