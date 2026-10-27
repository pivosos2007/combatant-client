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

/**
 * Brings a consumable into the main hand and puts everything back afterwards.
 * <p>
 * A hotbar item is selected with {@link InventorySwap#selectHotbar}. An inventory item is first swapped into a hotbar
 * slot (one window click), and the slot is selected on a LATER tick, because the backend refuses a held-slot change
 * unless the Grim window allows it ({@code selectHotbar} returns {@code false}; we retry every tick).
 * <p>
 * Restoring is requested by the consumer and carried out at the start of a following tick ({@link #tick}): first the
 * window click that undoes the swap, then the player's own selection via {@link InventorySwap#restoreHotbar}, which
 * itself defers the server-side slot change when the current Grim window (for example after a release) forbids it.
 * Nothing here sends a slot change in the tick a use was released.
 */
public final class MainhandSwitcher {
    public enum State { READY, PENDING, FAILED }

    private static final int LEASE_TICKS = 80;

    private final Minecraft mc = Minecraft.getInstance();
    private final Object owner;
    private final OffhandSettings settings;
    private final OffhandItems items;
    private final InventoryLeaseKeeper lease;

    // What the main hand must show before the consumer may use it.
    private ItemStack expectedMainhand = ItemStack.EMPTY;
    private int readyTick = -1;
    private int deadlineTick = -1;

    // The player's own selection, to put back.
    private int previousSelectedSlot = -1;
    private boolean restoreSelected;
    private int autoSelectedSlot = -1;
    /** Hotbar slot to select once the inventory swap click is out; -1 when none. */
    private int selectSlot = -1;

    // The borrowed hotbar slot: item came from restoreInventorySlot, goes back there.
    private int restoreInventorySlot = -1;
    private int restoreHotbarSlot = -1;
    private boolean restoreInventoryPending;
    private boolean forwardInFlight;
    private boolean backInFlight;
    private int flightTick = -1;

    private boolean restoreRequested;
    private int restoreNotBeforeTick;
    private int quietUntilTick = -1;
    /** Bumped on every reset so a click queued before it cannot touch later state. */
    private int generation;

    public MainhandSwitcher(Object owner, OffhandSettings settings, OffhandItems items, InventoryLeaseKeeper lease) {
        this.owner = owner;
        this.settings = settings;
        this.items = items;
        this.lease = lease;
    }

    // ---- queries -------------------------------------------------------------------------------------------

    /** A borrowed hotbar slot is (or is about to be) out of place; the inventory lease is needed. */
    public boolean isInventoryBusy() {
        return restoreInventoryPending || forwardInFlight || backInFlight;
    }

    /** A window click of this component has been handed to the backend and is not confirmed yet. */
    public boolean isSwapInFlight() {
        return forwardInFlight || backInFlight;
    }

    /** Something (slot selection or inventory swap) will have to be undone. */
    public boolean needsRestore() {
        return restoreSelected || isInventoryBusy() || selectSlot >= 0;
    }

    public boolean isRestoring() {
        return restoreRequested;
    }

    /** Swapped, but nothing is expected in the main hand any more (the consumer is done with it). */
    public boolean isHoldingUnexpectedSwap() {
        return restoreInventoryPending && expectedMainhand.isEmpty();
    }

    public boolean isQuiet(LocalPlayer player) {
        return player.tickCount <= quietUntilTick;
    }

    // ---- bringing the item to the main hand ----------------------------------------------------------------

    /** Makes {@code wanted} (at inventory slot {@code slot}) the main-hand item; {@code PENDING} means call again. */
    public State ensure(LocalPlayer player, int slot, ItemStack wanted) {
        if (wanted == null || wanted.isEmpty()) return State.FAILED;
        restoreRequested = false;

        if (!expectedMainhand.isEmpty()) {
            boolean switching = forwardInFlight || selectSlot >= 0;
            if (!switching && ItemStack.isSameItemSameComponents(player.getMainHandItem(), expectedMainhand)) {
                if (player.tickCount < readyTick) return State.PENDING;
                clearExpectation();
                return State.READY;
            }
            if (player.tickCount <= deadlineTick) return State.PENDING;
            clearExpectation();
            return State.FAILED;
        }

        if (ItemStack.isSameItemSameComponents(player.getMainHandItem(), wanted)) return State.READY;
        if (slot < 0 || slot >= 36) return State.FAILED;

        int selected = InventorySwap.INSTANCE.clientSelectedSlot();
        if (slot < 9) {
            rememberSelection(selected, slot);
            if (!InventorySwap.INSTANCE.selectHotbar(slot)) return State.PENDING;
            autoSelectedSlot = slot;
            expect(player, wanted, OffhandSwapper.CONFIRM_TICKS, OffhandSwapper.TIMEOUT_TICKS);
            return State.PENDING;
        }

        if (isInventoryBusy()) return State.PENDING;
        // A passive swap must not freeze the player: it waits for a stationary moment.
        if (!PacketWindow.INSTANCE.stationaryForClick() || !lease.acquire(LEASE_TICKS)) return State.PENDING;

        int target = items.findEmptyHotbarSlot(player);
        if (target == -1) target = selected;
        rememberSelection(selected, target);

        restoreInventorySlot = slot;
        restoreHotbarSlot = target;
        forwardInFlight = true;
        flightTick = player.tickCount;
        expectedMainhand = wanted.copy();
        readyTick = Integer.MAX_VALUE;
        deadlineTick = player.tickCount + 3 * OffhandSwapper.TIMEOUT_TICKS;

        int token = generation;
        int hotbar = target;
        boolean accepted = InventoryMover.toHotbarSlot(owner, slot, hotbar, false,
                stack -> token == generation && ItemStack.isSameItemSameComponents(stack, wanted),
                () -> onForwardDone(token, hotbar));
        if (!accepted) {
            dropForward();
            lease.releaseIfIdle();
        }
        return State.PENDING;
    }

    private void rememberSelection(int selected, int target) {
        if (settings.returnSlot().get() && !restoreSelected && selected != target) {
            previousSelectedSlot = selected;
            restoreSelected = true;
        }
    }

    private void expect(LocalPlayer player, ItemStack wanted, int confirmTicks, int timeoutTicks) {
        expectedMainhand = wanted.copy();
        readyTick = player.tickCount + confirmTicks;
        deadlineTick = player.tickCount + timeoutTicks;
    }

    private void clearExpectation() {
        expectedMainhand = ItemStack.EMPTY;
        readyTick = -1;
        deadlineTick = -1;
    }

    private void onForwardDone(int token, int hotbar) {
        if (token != generation) return;
        forwardInFlight = false;
        restoreInventoryPending = true;
        selectSlot = hotbar;
    }

    private void dropForward() {
        forwardInFlight = false;
        restoreInventorySlot = -1;
        restoreHotbarSlot = -1;
        selectSlot = -1;
        clearExpectation();
    }

    // ---- restoring -----------------------------------------------------------------------------------------

    /**
     * Asks for everything to be put back, from {@code notBeforeTick} on. Carried out by {@link #tick}, never in the
     * calling tick, so it cannot follow a release or use packet of that tick with a slot change.
     */
    public void requestRestore(int notBeforeTick) {
        if (!needsRestore()) {
            autoSelectedSlot = -1;
            return;
        }
        restoreRequested = true;
        restoreNotBeforeTick = Math.max(restoreNotBeforeTick, notBeforeTick);
    }

    /** Start-of-tick work: selects the swapped slot, drops lost clicks, carries out a requested restore. */
    public void tick(LocalPlayer player) {
        expireLostClicks(player);
        if (restoreRequested) {
            if (player.tickCount >= restoreNotBeforeTick) restoreStep(player);
        } else if (selectSlot >= 0 && !forwardInFlight && InventorySwap.INSTANCE.selectHotbar(selectSlot)) {
            autoSelectedSlot = selectSlot;
            selectSlot = -1;
            expect(player, expectedMainhand, OffhandSwapper.CONFIRM_TICKS, OffhandSwapper.TIMEOUT_TICKS);
        }
    }

    private void restoreStep(LocalPlayer player) {
        if (forwardInFlight || backInFlight) return;
        selectSlot = -1;
        if (restoreInventoryPending) {
            sendSwapBack(player);
            return;
        }
        restoreSelection();
        restoreRequested = false;
        restoreNotBeforeTick = 0;
    }

    private void sendSwapBack(LocalPlayer player) {
        backInFlight = true;
        flightTick = player.tickCount;
        int token = generation;
        if (!InventoryMover.exchangeWithHotbar(owner, restoreHotbarSlot, restoreInventorySlot,
                () -> onBackDone(token))) {
            backInFlight = false;
        }
    }

    private void onBackDone(int token) {
        LocalPlayer player = mc.player;
        if (token != generation) return;
        backInFlight = false;
        restoreInventoryPending = false;
        restoreInventorySlot = -1;
        restoreHotbarSlot = -1;
        clearExpectation();
        if (player != null) {
            quietUntilTick = Math.max(quietUntilTick, player.tickCount + OffhandSwapper.CONFIRM_TICKS);
            lease.scheduleRelease(player, OffhandSwapper.CONFIRM_TICKS);
        }
    }

    private void restoreSelection() {
        if (restoreSelected) {
            int current = InventorySwap.INSTANCE.clientSelectedSlot();
            if (previousSelectedSlot >= 0 && (autoSelectedSlot < 0 || current == autoSelectedSlot)) {
                InventorySwap.INSTANCE.restoreHotbar(previousSelectedSlot);
            }
            restoreSelected = false;
            previousSelectedSlot = -1;
        }
        autoSelectedSlot = -1;
    }

    /**
     * Module disabled: undo what can be undone right now. The swap-back click is handed to the backend (it may run
     * a tick later); the player's own selection comes back at once (the server follows when its window allows).
     */
    public void shutdown(LocalPlayer player) {
        if (player != null) {
            if (restoreInventoryPending && !backInFlight && !forwardInFlight) {
                InventoryMover.exchangeWithHotbar(owner, restoreHotbarSlot, restoreInventorySlot, null);
            }
            restoreSelection();
        }
        reset();
    }

    private void expireLostClicks(LocalPlayer player) {
        if (!isSwapInFlight() || player.tickCount <= flightTick) return;
        boolean queued = InventorySwap.INSTANCE.hasQueuedInventoryAction(owner, InventoryActionKind.INVENTORY_CLICK);
        if (queued && player.tickCount <= flightTick + 3 * OffhandSwapper.TIMEOUT_TICKS) return;
        // The click was refused when it ran, or never got its window.
        if (forwardInFlight) {
            dropForward();
            lease.releaseIfIdle();
        }
        backInFlight = false;
    }

    /** Forgets everything without touching the inventory (pause, world change, after {@link #shutdown}). */
    public void reset() {
        generation++;
        clearExpectation();
        previousSelectedSlot = -1;
        restoreSelected = false;
        autoSelectedSlot = -1;
        selectSlot = -1;
        restoreInventorySlot = -1;
        restoreHotbarSlot = -1;
        restoreInventoryPending = false;
        forwardInFlight = false;
        backInFlight = false;
        flightTick = -1;
        restoreRequested = false;
        restoreNotBeforeTick = 0;
    }
}
