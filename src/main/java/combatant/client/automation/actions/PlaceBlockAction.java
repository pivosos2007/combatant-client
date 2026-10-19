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
import combatant.client.automation.ExecutionPhase;
import combatant.client.automation.FailureReason;
import combatant.client.automation.placement.PlacementConfirmation;
import combatant.client.automation.placement.PlacementConstraints;
import combatant.client.automation.placement.PlacementExecutor;
import combatant.client.automation.placement.PlacementPlanner;
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.automation.placement.PlacementSlot;
import combatant.client.automation.placement.PlacementTarget;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.block.scaffold.ScaffoldPlacementTarget;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Places a small batch of blocks and waits for the server to confirm each one.
 *
 * <p>Holds INTERACTION, the main-hand HOTBAR slot (when the item is not in the offhand), ROTATION
 * (NORMAL mode) and SNEAK (while clicking an interactable support). Every tick it re-solves the
 * support face of the next cell, requests the rotation, and clicks in
 * {@link ExecutionPhase#AFTER_ROTATION}, only while it owns the active rotation and the rotation of
 * that tick hits the support. NORMAL mode clicks one cell per tick; NO_ROTATION up to
 * {@code placementsPerTick}. Clicks are not serialised: later cells go out while earlier ones await
 * their acknowledgement. Ends with SUCCESS when something was confirmed and nothing was rejected
 * or timed out; per-cell results stay readable through {@link #entries()}.
 */
public final class PlaceBlockAction extends AbstractAction {

    public enum Outcome {
        /** Not clicked yet, or clicked and still waiting for the verdict. */
        PENDING,
        CONFIRMED,
        /** The client refused the click or the server undid the block. */
        REJECTED,
        /** No acknowledgement in time (or the cell was never aimable before the deadline). */
        TIMEOUT,
        /** The cell became occupied or was dropped before it was clicked. */
        SKIPPED
    }

    /** Ticks without a click while cells are still waiting to be aimed at before they are abandoned. */
    static final int AIM_TIMEOUT_TICKS = 20;

    public static final class Entry {
        private final BlockPos pos;
        private PlacementTarget target;
        private Outcome outcome = Outcome.PENDING;
        private PlacementConfirmation.Ticket ticket;
        private boolean sent;

        private Entry(PlacementTarget target) {
            this.pos = target.pos();
            this.target = target;
        }

        public BlockPos pos() {
            return pos;
        }

        public PlacementTarget target() {
            return target;
        }

        public Outcome outcome() {
            return outcome;
        }

        /** True once a click for this cell went to the server. */
        public boolean isSent() {
            return sent;
        }

        private boolean awaitsClick() {
            return outcome == Outcome.PENDING && !sent;
        }
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Entry> entries;
    private final List<Entry> entriesView;
    private final PlacementConstraints constraints;
    private final PlacementSlot slot;
    private final Object slotOwner;
    private final int placementsPerTick;
    private final Runnable clickStep = this::clickStep;

    private boolean acquired;
    private long lastProgressTick;
    private long stepTick;
    private int sneakSetTicks;
    private boolean sneakReady;
    private BlockInteractionPipeline.RotationPlan rotationPlan;

    /**
     * @param targets           cells to place, in order; all share the first target's slot
     * @param placementsPerTick clicks per tick in NO_ROTATION mode (NORMAL always clicks one)
     */
    public PlaceBlockAction(Object slotOwner, List<PlacementTarget> targets, PlacementConstraints constraints,
                            int placementsPerTick) {
        super("PlaceBlock");
        if (targets == null || targets.isEmpty()) throw new IllegalArgumentException("no placement targets");
        this.slotOwner = slotOwner;
        this.constraints = constraints;
        this.slot = targets.get(0).slot();
        this.placementsPerTick = Math.max(1, placementsPerTick);
        this.entries = new ArrayList<>(targets.size());
        for (PlacementTarget target : targets) {
            entries.add(new Entry(new PlacementTarget(
                    target.pos(), target.support(), slot, target.pinned(), target.check())));
        }
        this.entriesView = Collections.unmodifiableList(entries);
    }

    public List<Entry> entries() {
        return entriesView;
    }

    public Outcome outcomeOf(BlockPos pos) {
        for (Entry entry : entries) {
            if (entry.pos.equals(pos)) return entry.outcome;
        }
        return null;
    }

    /** The cell currently being aimed at or clicked, or null when every cell is clicked or settled. */
    public BlockPos currentPos() {
        Entry entry = firstAwaitingClick();
        return entry != null ? entry.pos : null;
    }

    /** Settles cells that were not clicked yet and are no longer wanted. */
    public void dropUnsent(Predicate<BlockPos> unwanted) {
        for (Entry entry : entries) {
            if (entry.awaitsClick() && unwanted.test(entry.pos)) entry.outcome = Outcome.SKIPPED;
        }
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return ActionResult.failure(FailureReason.PRECONDITION, "no world");
        }

        if (!acquired) {
            if (!acquireResources(ctx)) {
                return ActionResult.failure(FailureReason.RESOURCE_BUSY, "placement resources busy");
            }
            acquired = true;
            lastProgressTick = ctx.tick();
        }

        syncTickets();

        Entry current = firstAwaitingClick();
        if (current == null) return finishIfSettled();

        if (!slot.stillHolds(player)) {
            return ActionResult.failure(FailureReason.MISSING_ITEM, "placement item left its slot");
        }

        if (ctx.tick() - lastProgressTick > AIM_TIMEOUT_TICKS) {
            for (Entry entry : entries) {
                if (entry.awaitsClick()) entry.outcome = Outcome.TIMEOUT;
            }
            return finishIfSettled();
        }

        if (!refresh(player, current)) {
            return current.awaitsClick() ? ActionResult.RUNNING : finishIfSettledOrRunning();
        }

        updateSneak(ctx, player, current);

        if (constraints.rotate() == PlacementRotationMode.NORMAL) {
            if (rotationPlan == null) {
                // Lifetime 2: RotationManager.update() ages requests before RotationUpdateEvent POST, where
                // the click checks ownsActiveRotation; with 1 the request is already gone by then.
                rotationPlan = new BlockInteractionPipeline.RotationPlan(
                        true, List.of(), 2, 4.0f, false, constraints.movementCorrection(), ctx.priority());
            }
            ScaffoldPlacementTarget support = current.target.support();
            BlockInteractionPipeline.INSTANCE.rotateTo(
                    this, support.getRotation(), support.getHitVec(), rotationPlan, null);
        }

        stepTick = ctx.tick();
        ctx.defer(ExecutionPhase.AFTER_ROTATION, this, clickStep);
        return ActionResult.RUNNING;
    }

    private boolean acquireResources(ActionContext ctx) {
        boolean rotate = constraints.rotate() == PlacementRotationMode.NORMAL;
        boolean hotbar = slot.needsHotbar();
        if (rotate && hotbar) return acquire(ctx, ActionResource.INTERACTION, ActionResource.HOTBAR, ActionResource.ROTATION);
        if (rotate) return acquire(ctx, ActionResource.INTERACTION, ActionResource.ROTATION);
        if (hotbar) return acquire(ctx, ActionResource.INTERACTION, ActionResource.HOTBAR);
        return acquire(ctx, ActionResource.INTERACTION);
    }

    /**
     * Re-solves the support of a cell that still awaits its click. Returns false when the cell was
     * settled instead (occupied) so the caller moves on.
     */
    private boolean refresh(LocalPlayer player, Entry entry) {
        if (PlacementPlanner.isBlocked(player, entry.pos, constraints.ignoreEntityCollision())) {
            entry.outcome = Outcome.SKIPPED;
            return false;
        }
        if (entry.target.pinned()) return true;
        ScaffoldPlacementTarget support = PlacementPlanner.findSupport(player, entry.pos, slot.stack(), constraints);
        if (support != null) entry.target = entry.target.withSupport(support);
        return true;
    }

    private void updateSneak(ActionContext ctx, LocalPlayer player, Entry current) {
        boolean needed = false;
        for (Entry entry : entries) {
            if (entry.awaitsClick() && PlacementPlanner.needsSneak(player, entry.target.support())) {
                needed = true;
                break;
            }
        }
        if (!needed) {
            ctx.coordinator().release(this, ActionResource.SNEAK);
            sneakSetTicks = 0;
            sneakReady = false;
            return;
        }
        // The server learns about sneaking with the movement packet of the tick it was set, so the
        // click waits until the previous tick(s) already carried it.
        sneakReady = sneakSetTicks >= Math.max(1, constraints.sneakTicks()) && player.isShiftKeyDown();
        if (acquire(ctx, ActionResource.SNEAK)) {
            ctx.setSneak(this, true);
            sneakSetTicks++;
        } else {
            sneakSetTicks = 0;
        }
    }

    /** Runs after this tick's rotation was computed, before the movement packet. */
    private void clickStep() {
        LocalPlayer player = mc.player;
        if (player == null) return;
        boolean normal = constraints.rotate() == PlacementRotationMode.NORMAL;
        if (normal && !RotationManager.INSTANCE.ownsActiveRotation(this)) return;

        int clicks = 0;
        int limit = normal ? 1 : placementsPerTick;
        for (Entry entry : entries) {
            if (clicks >= limit) break;
            if (!entry.awaitsClick()) continue;

            if (!normal && !refresh(player, entry)) continue;
            if (PlacementPlanner.needsSneak(player, entry.target.support()) && !sneakReady) {
                if (normal) break;
                continue;
            }

            PlacementExecutor.Attempt attempt = PlacementExecutor.place(slotOwner, player, entry.target, constraints);
            switch (attempt.status()) {
                case NOT_READY -> {
                    if (normal) return;
                }
                case BLOCKED -> entry.outcome = Outcome.SKIPPED;
                case FAILED -> {
                    entry.outcome = Outcome.REJECTED;
                    lastProgressTick = stepTick;
                    clicks++;
                }
                case SENT -> {
                    entry.sent = true;
                    entry.ticket = attempt.ticket();
                    lastProgressTick = stepTick;
                    clicks++;
                }
            }
            if (normal) return;
        }
    }

    private Entry firstAwaitingClick() {
        for (Entry entry : entries) {
            if (entry.awaitsClick()) return entry;
        }
        return null;
    }

    private void syncTickets() {
        for (Entry entry : entries) {
            if (entry.ticket == null || entry.outcome != Outcome.PENDING) continue;
            switch (entry.ticket.outcome()) {
                case CONFIRMED -> entry.outcome = Outcome.CONFIRMED;
                case REJECTED -> entry.outcome = Outcome.REJECTED;
                case TIMED_OUT -> entry.outcome = Outcome.TIMEOUT;
                case PENDING -> {
                }
            }
        }
    }

    private ActionResult finishIfSettledOrRunning() {
        return firstAwaitingClick() == null ? finishIfSettled() : ActionResult.RUNNING;
    }

    /** Success/failure once no cell is pending; RUNNING while a verdict is still outstanding. */
    private ActionResult finishIfSettled() {
        syncTickets();
        boolean confirmed = false;
        boolean rejected = false;
        boolean timedOut = false;
        for (Entry entry : entries) {
            switch (entry.outcome) {
                case PENDING -> {
                    return ActionResult.RUNNING;
                }
                case CONFIRMED -> confirmed = true;
                case REJECTED -> rejected = true;
                case TIMEOUT -> timedOut = true;
                case SKIPPED -> {
                }
            }
        }
        if (rejected) return ActionResult.failure(FailureReason.REJECTED, "server rejected placement");
        if (timedOut) return ActionResult.failure(FailureReason.TIMEOUT, "placement not confirmed in time");
        if (confirmed) return ActionResult.SUCCESS;
        return ActionResult.failure(FailureReason.PRECONDITION, "nothing to place");
    }

    @Override
    protected void onCancel(ActionContext ctx) {
        release();
    }

    @Override
    protected void onEnd(ActionContext ctx, ActionResult result) {
        release();
    }

    @Override
    protected void onRevoked(ActionLease lease) {
        release();
        // Preempted: whoever took over may need the hotbar right now, so the shared slot lease goes too.
        InventorySwap.INSTANCE.releaseHotbar(slotOwner);
    }

    @Override
    public Object slotOwner() {
        return slotOwner;
    }

    /**
     * Drops rotation state and pending tickets. The hotbar slot lease belongs to {@link #slotOwner}
     * and is kept so consecutive batches do not re-send the slot; InventorySwap resets it after the
     * configured delay.
     */
    private void release() {
        RotationManager.INSTANCE.release(this, true);
        for (Entry entry : entries) {
            if (entry.ticket != null && !entry.ticket.isResolved()) entry.ticket.abandon();
        }
    }
}
