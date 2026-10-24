/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.block.placer;

import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.PlaceBlockAction;
import combatant.client.automation.placement.PlacementBackoff;
import combatant.client.automation.placement.PlacementConstraints;
import combatant.client.automation.placement.PlacementPlanner;
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.automation.placement.PlacementSlot;
import combatant.client.automation.placement.PlacementTarget;
import combatant.client.automation.placement.SlotFinder;
import combatant.client.features.module.Module;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;


import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The placement queue of a module, run on the automation runtime.
 *
 * <p>The module keeps the wanted cells current with {@link #update} and calls {@link #tick} once per
 * client tick. Each tick without a running task the placer plans the next cells with
 * {@link PlacementPlanner} and submits one {@link AutomationTask} holding a {@link PlaceBlockAction}.
 * A cell leaves the queue only when the server confirmed the block. Rejected or unconfirmed cells
 * stay queued and are counted; after {@value #FAILURES_BEFORE_BACKOFF} failures a cell is left out
 * of planning for {@value #BACKOFF_TICKS} ticks. Tasks that were preempted or could not get their
 * resources are simply planned again on the next tick.
 */
public final class BlockPlacer {

    static final int FAILURES_BEFORE_BACKOFF = 3;
    static final int BACKOFF_TICKS = 20;
    /** Failures older than this are forgotten. */
    static final int FAILURE_MEMORY_TICKS = 200;
    /** Cells planned per task in NORMAL mode: one click per tick, acknowledgements overlap. */
    static final int NORMAL_PIPELINE = 3;

    private final Minecraft mc = Minecraft.getInstance();

    private final Module owner;
    private final int priority;
    private final SlotFinder slotFinder;
    private final Supplier<PlacementConstraints> constraintsSupplier;
    private final IntSupplier cooldownMinSupplier;
    private final IntSupplier cooldownMaxSupplier;
    private final IntSupplier blocksPerTickSupplier;
    private final BooleanSupplier ignoreOpenInventorySupplier;
    private final BooleanSupplier ignoreUsingItemSupplier;

    private final LinkedHashSet<BlockPos> queue = new LinkedHashSet<>();
    private final HashSet<BlockPos> inaccessible = new HashSet<>();
    private final PlacementBackoff<BlockPos> backoff =
            new PlacementBackoff<>(FAILURES_BEFORE_BACKOFF, BACKOFF_TICKS, FAILURE_MEMORY_TICKS);

    private boolean enabled;
    private long tickCounter;
    private int ticksToWait;
    private AutomationTask activeTask;
    private PlaceBlockAction activeAction;

    /**
     * @param owner                 module owning the tasks; they end when it is disabled
     * @param priority              task priority, also the rotation priority
     * @param slotFinder            what to place with
     * @param constraintsSupplier   placement policy, read once per planned task
     * @param cooldownMin           minimum ticks to wait after a confirmed placement
     * @param cooldownMax           maximum ticks to wait after a confirmed placement
     * @param blocksPerTick         clicks per tick in NO_ROTATION mode
     * @param ignoreOpenInventory   keep placing while a container screen is open
     * @param ignoreUsingItem       keep placing while using an item
     */
    public BlockPlacer(
            Module owner,
            int priority,
            SlotFinder slotFinder,
            Supplier<PlacementConstraints> constraintsSupplier,
            IntSupplier cooldownMin,
            IntSupplier cooldownMax,
            IntSupplier blocksPerTick,
            BooleanSupplier ignoreOpenInventory,
            BooleanSupplier ignoreUsingItem
    ) {
        this.owner = owner;
        this.priority = priority;
        this.slotFinder = slotFinder;
        this.constraintsSupplier = constraintsSupplier;
        this.cooldownMinSupplier = cooldownMin;
        this.cooldownMaxSupplier = cooldownMax;
        this.blocksPerTickSupplier = blocksPerTick;
        this.ignoreOpenInventorySupplier = ignoreOpenInventory;
        this.ignoreUsingItemSupplier = ignoreUsingItem;
    }
    private static int randomBetween(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
        cancelActive("placer disabled");
        InventorySwap.INSTANCE.releaseHotbar(this);
        queue.clear();
        inaccessible.clear();
        backoff.clear();
        ticksToWait = 0;
    }

    public void tick() {
        if (!enabled) return;
        InventorySwap.INSTANCE.tick();
        tickCounter++;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            cancelActive("no world");
            InventorySwap.INSTANCE.releaseHotbar(this);
            return;
        }

        boolean inventoryOpen = !ignoreOpenInventorySupplier.getAsBoolean()
                && ClientScreen.current() instanceof AbstractContainerScreen<?>;
        boolean usingItem = !ignoreUsingItemSupplier.getAsBoolean() && player.isUsingItem();
        if (inventoryOpen || usingItem) {
            cancelActive("player busy");
            return;
        }

        if (activeTask != null) return;
        if (ticksToWait > 0) {
            ticksToWait--;
            return;
        }
        if (queue.isEmpty()) return;

        PlacementSlot slot = slotFinder.find(null);
        if (slot == null || slot.stack().isEmpty()) return;

        PlacementConstraints constraints = constraintsSupplier.get();
        for (BlockPos pos : queue) {
            if (backoff.isBackedOff(pos, tickCounter)) inaccessible.add(pos);
        }

        int placementsPerTick = Math.max(1, blocksPerTickSupplier.getAsInt());
        int batch;
        if (constraints.rotate() == PlacementRotationMode.NO_ROTATION) {
            batch = placementsPerTick;
        } else {
            // A delay between placements means one cell per task; without it the clicks overlap.
            batch = cooldownMaxSupplier.getAsInt() > 0 || cooldownMinSupplier.getAsInt() > 0 ? 1 : NORMAL_PIPELINE;
        }

        List<PlacementTarget> targets = PlacementPlanner.plan(player, queue, inaccessible, slot, constraints, batch);
        if (targets.isEmpty()) return;

        PlaceBlockAction action = new PlaceBlockAction(this, targets, constraints, placementsPerTick);
        AutomationTask task = AutomationTask.builder(owner, "BlockPlacer:" + owner.name(), action)
                .priority(priority)
                .onFinish(this::onTaskFinished)
                .build();
        activeAction = action;
        activeTask = task;
        AutomationRuntime.INSTANCE.submit(task);
    }

    /** Replaces the wanted cells. Cells already clicked and awaiting the server stay under the running task. */
    public void update(Iterable<BlockPos> positions) {
        LinkedHashSet<BlockPos> next = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            if (pos != null) next.add(pos.immutable());
        }

        queue.retainAll(next);
        queue.addAll(next);

        if (activeAction != null) activeAction.dropUnsent(pos -> !next.contains(pos));
    }

    /** Forgets every wanted cell. A click already sent keeps its task until the server answered. */
    public void clear() {
        queue.clear();
        inaccessible.clear();
        backoff.clear();
        if (activeAction != null) activeAction.dropUnsent(pos -> true);
    }

    public boolean isDone() {
        return queue.isEmpty();
    }

    public List<BlockPos> getQueuedPositions() {
        return List.copyOf(queue);
    }

    public BlockPos getCurrentPlacementPos() {
        return activeAction == null ? null : activeAction.currentPos();
    }

    private void cancelActive(String reason) {
        AutomationTask task = activeTask;
        activeTask = null;
        activeAction = null;
        if (task != null) task.cancel(reason);
    }

    private void onTaskFinished(AutomationTask task) {
        if (task != activeTask) return;
        PlaceBlockAction action = activeAction;
        activeTask = null;
        activeAction = null;
        if (!enabled || action == null) return;

        boolean confirmed = false;
        for (PlaceBlockAction.Entry entry : action.entries()) {
            switch (entry.outcome()) {
                case CONFIRMED -> {
                    queue.remove(entry.pos());
                    backoff.recordSuccess(entry.pos());
                    confirmed = true;
                }
                case REJECTED, TIMEOUT -> backoff.recordFailure(entry.pos(), tickCounter);
                case PENDING, SKIPPED -> {
                }
            }
        }
        if (confirmed) ticksToWait = randomBetween(cooldownMinSupplier.getAsInt(), cooldownMaxSupplier.getAsInt());
    }
}
