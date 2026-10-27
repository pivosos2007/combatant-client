/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.offhand;

import combatant.client.util.combat.TapRules;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventoryMover;
import combatant.client.util.player.inventory.InventorySlotLocks;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.PacketWindow;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Opt-in "human" totem refill: after a pop (or with an empty offhand) open the inventory, wait a random delay, move a
 * totem into the offhand / a hotbar slot and close the inventory again.
 * <p>
 * Phases: IDLE -> (OPENING) -> WAIT_BEFORE -> SWAPPING -> (WAIT_BEFORE again while more is needed) -> WAIT_AFTER ->
 * (CLOSING) -> IDLE. It yields to any swap the offhand policy has in flight, so only one path owns the slot.
 */
public final class OffhandRefillSession {
    private static final int SESSION_TIMEOUT_TICKS = 80;
    private static final int OPEN_WAIT_TICKS = 3;

    private enum Phase { IDLE, OPENING, WAIT_BEFORE, SWAPPING, WAIT_AFTER, CLOSING }

    private final Minecraft mc = Minecraft.getInstance();
    private final Object owner;
    private final OffhandSettings settings;
    private final BooleanSupplier enabled;
    private final OffhandSwapper swapper;
    private final OffhandThreats threats;
    private final OffhandItems items;
    private final BooleanSupplier mainhandRestoring;

    private Phase phase = Phase.IDLE;
    private int wait;
    private int sessionTicks;
    private boolean weOpened;
    /** A click or close of this session was handed to the backend and has not reported back yet. */
    private boolean actionPending;
    private boolean hadTotem;
    private boolean popped;

    public OffhandRefillSession(Object owner, OffhandSettings settings, BooleanSupplier enabled,
                                OffhandSwapper swapper, OffhandThreats threats, OffhandItems items,
                                BooleanSupplier mainhandRestoring) {
        this.owner = owner;
        this.settings = settings;
        this.enabled = enabled;
        this.swapper = swapper;
        this.threats = threats;
        this.items = items;
        this.mainhandRestoring = mainhandRestoring;
    }

    public void tick() {
        LocalPlayer player = mc.player;
        if (!settings.inventoryRefill().get()) {
            if (phase != Phase.IDLE) abort();
            hadTotem = false;
            popped = false;
            return;
        }
        if (player == null || mc.level == null || mc.gameMode == null) {
            reset();
            hadTotem = false;
            return;
        }

        boolean hasTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        if (hadTotem && !hasTotem) popped = true;
        hadTotem = hasTotem;

        if (phase == Phase.IDLE) {
            tickIdle(player, hasTotem);
            return;
        }

        if (!player.isAlive() || ++sessionTicks > SESSION_TIMEOUT_TICKS || swapper.isPending() && !actionPending) {
            abort();
            return;
        }
        if (actionPending && phase == Phase.SWAPPING && !hasQueuedClick()) actionPending = false;

        switch (phase) {
            case OPENING -> {
                if (ClientScreen.current() instanceof InventoryScreen) enterWait(Phase.WAIT_BEFORE);
                else if (wait-- <= 0) abort();
            }
            case WAIT_BEFORE -> {
                if (!(ClientScreen.current() instanceof InventoryScreen)) {
                    abort();
                } else if (wait > 0) {
                    wait--;
                } else if (PacketWindow.INSTANCE.stationaryForClick()) {
                    phase = Phase.SWAPPING;
                    swap(player);
                }
            }
            case SWAPPING -> {
                if (!actionPending) {
                    if (needsMore(player)) enterWait(Phase.WAIT_BEFORE);
                    else enterWait(Phase.WAIT_AFTER);
                }
            }
            case WAIT_AFTER -> {
                if (wait-- <= 0) close(player);
            }
            case CLOSING -> {
                if (!actionPending) finish();
            }
            default -> { }
        }
    }

    /** Drops the session and the pop memory without touching the screen (module disabled / world left). */
    public void reset() {
        phase = Phase.IDLE;
        wait = 0;
        sessionTicks = 0;
        weOpened = false;
        actionPending = false;
    }

    public void resetAll() {
        reset();
        hadTotem = false;
        popped = false;
    }

    private void tickIdle(LocalPlayer player, boolean hasTotem) {
        if (!player.isAlive()) return;
        if (hasTotem) {
            popped = false;
            return;
        }
        // Offhand's own policy owns the slot while it has a swap in flight or an emergency totem to place.
        if (swapper.isPending() || mainhandRestoring.getAsBoolean()) return;
        if (threats.shouldHoldTotem(player) && items.findTotemSlot(player) >= 0) return;
        if (!(popped || player.getOffhandItem().isEmpty()) || findSource(player, -1) < 0) return;
        begin(player);
    }

    private void begin(LocalPlayer player) {
        sessionTicks = 0;
        weOpened = false;
        if (ClientScreen.current() instanceof InventoryScreen) {
            enterWait(Phase.WAIT_BEFORE);
            return;
        }
        if (!settings.refillOpenInventory().get() || ClientScreen.current() != null) return;
        weOpened = true;
        ClientScreen.show(new InventoryScreen(player));
        phase = Phase.OPENING;
        wait = OPEN_WAIT_TICKS;
    }

    private void enterWait(Phase next) {
        phase = next;
        wait = TapRules.pickTicks(settings.refillMinDelay().get(), settings.refillMaxDelay().get(),
                ThreadLocalRandom.current().nextDouble());
    }

    private boolean needsMore(LocalPlayer player) {
        return offhandNeeded(player) || hotbarNeeded(player);
    }

    private boolean offhandNeeded(LocalPlayer player) {
        return !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                && !swapper.isPending()
                && !InventorySlotLocks.blocks(InventorySlotLocks.OFFHAND, owner)
                && findSource(player, hotbarIndex()) >= 0;
    }

    private boolean hotbarNeeded(LocalPlayer player) {
        int slot = hotbarIndex();
        return slot >= 0 && !player.getInventory().getItem(slot).is(Items.TOTEM_OF_UNDYING)
                && findSource(player, slot) >= 0;
    }

    private int hotbarIndex() {
        return settings.refillHotbarSlot().get() - 1;
    }

    private int findSource(LocalPlayer player, int excludedSlot) {
        return InventoryMover.pickTotemSlot(player, settings.refillSaveEnchanted().get(),
                settings.refillPreferHotbar().get(), excludedSlot);
    }

    /** Sends one move: offhand first, then the hotbar slot. */
    private void swap(LocalPlayer player) {
        boolean toOffhand = offhandNeeded(player);
        int hotbar = hotbarIndex();
        int source = toOffhand || hotbarNeeded(player) ? findSource(player, hotbar) : -1;
        if (source < 0) {
            enterWait(Phase.WAIT_AFTER);
            return;
        }
        Predicate<ItemStack> stillValid = stack -> enabled.getAsBoolean() && settings.inventoryRefill().get()
                && !(toOffhand && swapper.isPending())
                && ClientScreen.current() instanceof InventoryScreen
                && InventoryMover.isTotem(stack);
        Runnable done = () -> actionPending = false;
        actionPending = true;
        boolean accepted = toOffhand
                ? InventoryMover.toOffhand(owner, source, stillValid, done)
                : InventoryMover.toHotbarSlot(owner, source, hotbar, false, stillValid, done);
        if (!accepted) {
            actionPending = false;
            abort();
        }
    }

    private void close(LocalPlayer player) {
        if (!weOpened || !settings.refillCloseAfter().get()) {
            finish();
            return;
        }
        phase = Phase.CLOSING;
        actionPending = true;
        if (!closeInventory(true)) {
            actionPending = false;
            finish();
        }
    }

    private void finish() {
        popped = false;
        reset();
    }

    /** Gives up the session; closes the screen if the refill opened it. */
    private void abort() {
        // Cancelling by owner would also drop Offhand's own queued swaps, so only cancel the refill's
        // actions when Offhand has nothing of its own in flight.
        if (!swapper.isPending()) InventorySwap.INSTANCE.cancelQueuedInventoryActions(owner);
        if (weOpened && ClientScreen.current() instanceof InventoryScreen) closeInventory(false);
        popped = false;
        reset();
    }

    /** Closes the inventory screen through the serialized window queue. */
    private boolean closeInventory(boolean tracked) {
        return InventorySwap.INSTANCE.command(owner, InventoryActionKind.INVENTORY_CLOSE, () -> {
            try {
                LocalPlayer current = mc.player;
                if (current != null && ClientScreen.current() instanceof InventoryScreen) current.closeContainer();
            } finally {
                if (tracked) actionPending = false;
            }
        });
    }

    private boolean hasQueuedClick() {
        return InventorySwap.INSTANCE.hasQueuedInventoryAction(owner, InventoryActionKind.INVENTORY_CLICK);
    }
}
