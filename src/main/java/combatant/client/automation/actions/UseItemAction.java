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
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapMode;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.player.inventory.SlotResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Uses an item found by predicate. Main hand: the item is brought into hand through {@link InventorySwap}
 * ({@code withSwap}, so the module's {@link InventorySwapPolicy} decides when a silent swap or inventory click may
 * run) and used in the {@link ExecutionPhase#AFTER_ROTATION} phase, against the rotation the server has seen. Off hand:
 * used in place.
 *
 * <p>Resources: INTERACTION always; ROTATION when an aim is configured; HOTBAR when the item is not in the selected
 * slot; INVENTORY when it has to be fetched from the main inventory.
 *
 * <p>Success: the optional effect predicate turned true within {@code effectTimeoutTicks} after the use; immediately
 * after the use when there is none.
 */
public final class UseItemAction extends AbstractAction {

    private static final int MAX_PENDING_TICKS = 40;

    private final Predicate<ItemStack> item;
    private final InteractionHand hand;
    private final InventorySwapMode mode;
    private final InventorySwapPolicy policy;
    private final boolean swing;
    private final AimRequirement aim;
    private final BooleanSupplier effect;
    private final int effectTimeoutTicks;

    private boolean queued;
    private long queuedTick;
    private boolean used;
    private long usedTick;
    private ActionResult useFailure;

    private UseItemAction(Builder b) {
        super("UseItem");
        this.item = b.item;
        this.hand = b.hand;
        this.mode = b.mode;
        this.policy = b.policy;
        this.swing = b.swing;
        this.aim = b.aim;
        this.effect = b.effect;
        this.effectTimeoutTicks = b.effectTimeoutTicks;
    }

    public static Builder builder(Predicate<ItemStack> item) {
        return new Builder(item);
    }

    public static final class Builder {
        private final Predicate<ItemStack> item;
        private InteractionHand hand = InteractionHand.MAIN_HAND;
        private InventorySwapMode mode = InventorySwapMode.SILENT_FULL;
        private InventorySwapPolicy policy = InventorySwapPolicy.NONE;
        private boolean swing = true;
        private AimRequirement aim;
        private BooleanSupplier effect;
        private int effectTimeoutTicks = 20;

        private Builder(Predicate<ItemStack> item) {
            this.item = Objects.requireNonNull(item, "item");
        }

        public Builder hand(InteractionHand hand) {
            this.hand = Objects.requireNonNull(hand, "hand");
            return this;
        }

        /** How the item reaches the hand; ignored for the off hand. */
        public Builder mode(InventorySwapMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /** Module-provided policy deciding when swaps and the use itself are allowed. */
        public Builder policy(InventorySwapPolicy policy) {
            this.policy = policy != null ? policy : InventorySwapPolicy.NONE;
            return this;
        }

        public Builder swing(boolean swing) {
            this.swing = swing;
            return this;
        }

        /** Requires the rotation to reach the point before the item is used. */
        public Builder aimAt(Supplier<Vec3> point, RotateAction.Settings settings) {
            this.aim = AimRequirement.atPoint(point, settings);
            return this;
        }

        /** Requires the rotation to reach the goal before the item is used. */
        public Builder aimTo(Supplier<Rotation> rotation, RotateAction.Settings settings) {
            this.aim = AimRequirement.toRotation(rotation, settings);
            return this;
        }

        /** Success only once {@code effect} is observed within {@code timeoutTicks} after the use. */
        public Builder effect(BooleanSupplier effect, int timeoutTicks) {
            this.effect = effect;
            this.effectTimeoutTicks = Math.max(1, timeoutTicks);
            return this;
        }

        public UseItemAction build() {
            return new UseItemAction(this);
        }
    }

    /** What has to be held to put the item in hand right now. */
    private record Source(SlotResult slot, boolean needsHotbar, boolean needsInventory) {
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().gameMode == null) {
            return ActionResult.failure(FailureReason.PRECONDITION, "no player");
        }
        if (useFailure != null) return useFailure;
        if (used) return observeEffect(ctx);
        if (queued && ctx.tick() - queuedTick > MAX_PENDING_TICKS) {
            return ActionResult.failure(FailureReason.TIMEOUT, "swap/use never ran");
        }
        if (!acquire(ctx, ActionResource.INTERACTION)) return ActionResult.RUNNING;

        if (aim != null) {
            if (!acquire(ctx, ActionResource.INTERACTION, ActionResource.ROTATION)) return ActionResult.RUNNING;
            if (!aim.request(player, ctx.priority(), this)) {
                return ActionResult.failure(FailureReason.INVALID_TARGET, "no aim goal");
            }
        }
        if (queued) return ActionResult.RUNNING;

        Source source = locate(player);
        if (source == null) return ActionResult.failure(FailureReason.MISSING_ITEM, "no matching item");
        if (source.needsHotbar && !acquire(ctx, ActionResource.HOTBAR)) return ActionResult.RUNNING;
        if (source.needsInventory && !acquire(ctx, ActionResource.INVENTORY)) return ActionResult.RUNNING;

        ctx.defer(ExecutionPhase.AFTER_ROTATION, this, () -> perform(ctx));
        return ActionResult.RUNNING;
    }

    private ActionResult observeEffect(ActionContext ctx) {
        if (effect == null || effect.getAsBoolean()) return ActionResult.SUCCESS;
        if (ctx.tick() - usedTick > effectTimeoutTicks) {
            return ActionResult.failure(FailureReason.REJECTED, "no effect after use");
        }
        return ActionResult.RUNNING;
    }

    /** Where the item is and which resources moving it costs; null when absent. */
    private Source locate(LocalPlayer player) {
        if (hand == InteractionHand.OFF_HAND) {
            return item.test(player.getOffhandItem()) ? new Source(SlotResult.empty(), false, false) : null;
        }
        InventorySwap swap = InventorySwap.INSTANCE;
        int selected = swap.effectiveSelectedSlot();
        ItemStack inHand = player.getInventory().getItem(selected);
        if (!inHand.isEmpty() && item.test(inHand)) {
            return new Source(new SlotResult(selected, true, inHand), false, false);
        }
        SlotResult found = swap.findSlotForMode(item, mode);
        if (!found.found()) return null;
        return new Source(found, true, found.isInventory());
    }

    private void perform(ActionContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || used || queued) return;
        if (aim != null && !aim.aligned(this)) return;

        Source source = locate(player);
        if (source == null) {
            useFailure = ActionResult.failure(FailureReason.MISSING_ITEM, "item gone before use");
            return;
        }
        // Resources are granted on the tick action; the inventory may have changed since, so re-check.
        if (source.needsHotbar && !holds(ctx, ActionResource.HOTBAR)) return;
        if (source.needsInventory && !holds(ctx, ActionResource.INVENTORY)) return;

        Runnable use = () -> {
            if (!isLive()) return;
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null || Minecraft.getInstance().gameMode == null) return;
            InteractionResult result = InventorySwap.INSTANCE.useItem(hand);
            if (result == null || !result.consumesAction()) {
                useFailure = ActionResult.failure(FailureReason.REJECTED, "use not accepted: " + result);
                return;
            }
            if (swing) p.swing(hand, p.getItemInHand(hand).getInteractAnimation(), false);
            used = true;
            usedTick = ctx.tick();
        };

        InventorySwap swap = InventorySwap.INSTANCE;
        boolean accepted;
        if (hand == InteractionHand.OFF_HAND) {
            accepted = swap.command(InventoryActionKind.USE_ITEM, policy, use);
        } else {
            accepted = swap.withSwap(source.slot, mode, policy, InventoryActionKind.USE_ITEM, use);
        }
        if (!accepted) return;
        if (!used && useFailure == null) {
            queued = true;
            queuedTick = ctx.tick();
        }
    }

    @Override
    protected void onRevoked(ActionLease lease) {
        if (aim != null) aim.release(this);
    }

    @Override
    protected void onEnd(ActionContext ctx, ActionResult result) {
        if (aim != null) aim.release(this);
    }
}
