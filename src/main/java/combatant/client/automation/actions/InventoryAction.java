/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.actions;

import combatant.client.automation.AbstractAction;
import combatant.client.automation.ActionContext;
import combatant.client.automation.ActionLease;
import combatant.client.automation.ActionResource;
import combatant.client.automation.ActionResult;
import combatant.client.automation.FailureReason;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Hotbar and inventory work through {@link InventorySwap}, with this action as the backend owner so its leases can be
 * told from everyone else's and are dropped when it ends or is revoked.
 *
 * <ul>
 *   <li>{@link #select}: really changes the selected hotbar slot (HOTBAR); succeeds once it is observed.</li>
 *   <li>{@link #lease}: silently leases the server-side slot (HOTBAR), re-leasing every tick, until a condition ends.
 *       The client selection is untouched and the server slot is restored on release.</li>
 *   <li>{@link #swapScreenSlots}: one pickup swap of two container screen slots (INVENTORY plus an inventory lease).
 *       It goes through the backend's Grim gate, which may queue it until the server sees a stationary player, and
 *       succeeds when the expected slot content is observed.</li>
 * </ul>
 */
public final class InventoryAction extends AbstractAction {

    private enum Mode {
        SELECT,
        LEASE,
        SWAP,
        DROP
    }

    private final Mode mode;
    private final IntSupplier slot;
    private final int leaseTicks;
    private final BooleanSupplier keepHolding;
    private final int holdTicks;
    private final int screenSlotA;
    private final int screenSlotB;
    private final BooleanSupplier expected;
    private final int timeoutTicks;

    private int leased;
    private boolean issued;
    private boolean executed;
    private long issuedTick;

    private InventoryAction(String name, Mode mode, IntSupplier slot, int leaseTicks, BooleanSupplier keepHolding,
                            int holdTicks, int screenSlotA, int screenSlotB,
                            BooleanSupplier expected, int timeoutTicks) {
        super(name);
        this.mode = mode;
        this.slot = slot;
        this.leaseTicks = Math.max(1, leaseTicks);
        this.keepHolding = keepHolding;
        this.holdTicks = holdTicks;
        this.screenSlotA = screenSlotA;
        this.screenSlotB = screenSlotB;
        this.expected = expected;
        this.timeoutTicks = Math.max(1, timeoutTicks);
    }

    /** Selects a hotbar slot for real (the player sees it) and succeeds once the selection is observed. */
    public static InventoryAction select(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) throw new IllegalArgumentException("hotbar slot " + hotbarSlot);
        return new InventoryAction("SelectHotbar", Mode.SELECT, () -> hotbarSlot, 1, null, 0, -1, -1, null, 20);
    }

    /**
     * Leases the server-side hotbar slot for as long as {@code keepHolding} is true (checked first each tick); a
     * negative slot from {@code slot} holds the resource without leasing. Succeeds when {@code keepHolding} turns
     * false. The lease is renewed every tick with {@code leaseTicks} of slack, so it also lapses on its own if the
     * action stops ticking.
     */
    public static InventoryAction lease(IntSupplier slot, int leaseTicks, BooleanSupplier keepHolding) {
        return new InventoryAction("LeaseHotbar", Mode.LEASE, Objects.requireNonNull(slot, "slot"), leaseTicks,
                Objects.requireNonNull(keepHolding, "keepHolding"), -1, -1, -1, null, 20);
    }

    /** Leases {@code hotbarSlot} for {@code ticks} ticks once granted, then succeeds. */
    public static InventoryAction leaseFor(int hotbarSlot, int ticks) {
        if (hotbarSlot < 0 || hotbarSlot > 8) throw new IllegalArgumentException("hotbar slot " + hotbarSlot);
        return new InventoryAction("LeaseHotbar", Mode.LEASE, () -> hotbarSlot, 2, null, Math.max(1, ticks), -1, -1,
                null, 20);
    }

    /**
     * One pickup swap of two container screen slots.
     *
     * @param expected      true once the swap's result is visible in the client inventory (required: a queued
     *                      swap runs later, and the result is the only proof it ran)
     * @param timeoutTicks  how long to wait for the click window and the result before failing
     */
    public static InventoryAction swapScreenSlots(int screenSlotA, int screenSlotB,
                                                  BooleanSupplier expected, int timeoutTicks) {
        if (screenSlotA < 0 || screenSlotB < 0 || screenSlotA == screenSlotB) {
            throw new IllegalArgumentException("slots " + screenSlotA + ", " + screenSlotB);
        }
        return new InventoryAction("SwapSlots", Mode.SWAP, null, 1, null, 0, screenSlotA, screenSlotB,
                Objects.requireNonNull(expected, "expected"), timeoutTicks);
    }

    /**
     * Throws the whole stack of one container screen slot out, through the same gate as a swap. Succeeds when
     * {@code expected} reports the result (normally: the slot no longer holds the stack).
     */
    public static InventoryAction dropScreenSlot(int screenSlot,
                                                 BooleanSupplier expected, int timeoutTicks) {
        if (screenSlot < 0) throw new IllegalArgumentException("slot " + screenSlot);
        return new InventoryAction("DropSlot", Mode.DROP, null, 1, null, 0, screenSlot, -1,
                Objects.requireNonNull(expected, "expected"), timeoutTicks);
    }

    /**
     * Moves the item currently at main-inventory index {@code inventorySlot} (0..35, 0..8 = hotbar) into hotbar slot
     * {@code hotbarSlot}; the previous hotbar content goes to the source slot.
     */
    public static InventoryAction moveToHotbar(int inventorySlot, int hotbarSlot,
                                               int timeoutTicks) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) throw new IllegalStateException("no player");
        if (inventorySlot < 0 || inventorySlot >= 36 || hotbarSlot < 0 || hotbarSlot > 8
                || inventorySlot == hotbarSlot) {
            throw new IllegalArgumentException("slots " + inventorySlot + " -> " + hotbarSlot);
        }
        ItemStack moving = player.getInventory().getItem(inventorySlot).copy();
        int source = InventorySwap.mapInventoryToScreenSlot(inventorySlot);
        int target = InventorySwap.mapHotbarToScreenSlot(hotbarSlot);
        BooleanSupplier arrived = () -> {
            LocalPlayer p = Minecraft.getInstance().player;
            return p != null && ItemStack.matches(p.getInventory().getItem(hotbarSlot), moving);
        };
        return swapScreenSlots(source, target, arrived, timeoutTicks);
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return ActionResult.failure(FailureReason.PRECONDITION, "no player");
        return switch (mode) {
            case SELECT -> tickSelect(ctx);
            case LEASE -> tickLease(ctx);
            case SWAP, DROP -> tickSwap(ctx, player);
        };
    }

    private ActionResult tickSelect(ActionContext ctx) {
        int wanted = slot.getAsInt();
        if (InventorySwap.INSTANCE.clientSelectedSlot() == wanted) return ActionResult.SUCCESS;
        if (elapsedTicks(ctx) >= timeoutTicks) return ActionResult.failure(FailureReason.TIMEOUT, "slot not selected");
        if (!acquire(ctx, ActionResource.HOTBAR)) return ActionResult.RUNNING;
        InventorySwap.INSTANCE.selectHotbar(wanted);
        return ActionResult.RUNNING;
    }

    private ActionResult tickLease(ActionContext ctx) {
        if (keepHolding != null && !keepHolding.getAsBoolean()) return ActionResult.SUCCESS;
        if (holdTicks >= 0 && leased >= holdTicks) return ActionResult.SUCCESS;
        if (!acquire(ctx, ActionResource.HOTBAR)) return ActionResult.RUNNING;

        int wanted = slot.getAsInt();
        InventorySwap swap = InventorySwap.INSTANCE;
        if (wanted < 0) {
            swap.releaseHotbar(this);
            return ActionResult.RUNNING;
        }
        // The lease is refused only while someone else's legacy lease is active; keep waiting for it.
        if (swap.leaseHotbar(this, wanted, leaseTicks)) leased++;
        return ActionResult.RUNNING;
    }

    private ActionResult tickSwap(ActionContext ctx, LocalPlayer player) {
        if (expected.getAsBoolean()) return ActionResult.SUCCESS;
        if (elapsedTicks(ctx) >= timeoutTicks) {
            if (!issued) return ActionResult.failure(FailureReason.TIMEOUT, "inventory busy");
            return ActionResult.failure(FailureReason.REJECTED,
                    executed ? "swap ran but the result is not visible" : "swap window never opened");
        }
        if (!acquire(ctx, ActionResource.INVENTORY)) return ActionResult.RUNNING;
        InventorySwap swap = InventorySwap.INSTANCE;
        // Keep the inventory lease alive while waiting so legacy users cannot interleave clicks.
        if (!swap.leaseInventory(this, 3)) return ActionResult.RUNNING;
        if (issued) return ActionResult.RUNNING;

        boolean accepted = swap.command(this, InventoryActionKind.INVENTORY_CLICK, () -> {
            if (!isLive()) return;
            executed = mode == Mode.DROP ? swap.throwSlot(screenSlotA) : swap.pickupSwap(screenSlotA, screenSlotB);
        });
        if (accepted) {
            issued = true;
            issuedTick = ctx.tick();
        }
        return ActionResult.RUNNING;
    }

    @Override
    protected void onRevoked(ActionLease lease) {
        drop();
    }

    @Override
    protected void onEnd(ActionContext ctx, ActionResult result) {
        drop();
    }

    private void drop() {
        InventorySwap swap = InventorySwap.INSTANCE;
        swap.releaseHotbar(this);
        swap.releaseInventory(this);
        swap.cancelQueuedInventoryActions(this);
    }
}
