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
import combatant.client.automation.placement.PlacementExecutor;
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.automation.placement.PlacementSlot;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Right-clicks one existing block with the item a finder picks (charging or detonating a respawn anchor, using a
 * button, ...). The item is brought to the hand through a silent hotbar lease of {@code slotOwner}, like placements.
 *
 * <p>Holds INTERACTION, the hotbar slot (main hand) and, in NORMAL mode, ROTATION: every tick it requests the rotation
 * to the block centre and clicks in {@link ExecutionPhase#AFTER_ROTATION} once it owns the active rotation and this
 * tick's rotation traces onto the block. NO_ROTATION clicks the block at once, on the traced face when the current
 * look already hits it.
 *
 * <p>Success: the optional effect predicate turned true within {@code effectTimeoutTicks} after the click, or at the
 * click when there is none.
 */
public final class InteractBlockAction extends AbstractAction {

    /** Ticks without a click before the block is given up on. */
    static final int AIM_TIMEOUT_TICKS = 20;

    /**
     * @param range               reach to the block, blocks
     * @param wallRange           reach through walls: blocks closer than this need no line of sight
     * @param rotate              aiming mode
     * @param movementCorrection  movement correction while the rotation is held
     * @param slotResetTicks      ticks the swapped hotbar slot is kept after the click
     * @param swing               swing the hand on success
     * @param effectTimeoutTicks  ticks to wait for the effect after the click
     */
    public record Settings(double range, double wallRange, PlacementRotationMode rotate,
                           MovementCorrection movementCorrection, int slotResetTicks, boolean swing,
                           int effectTimeoutTicks) {
        public Settings {
            Objects.requireNonNull(rotate, "rotate");
            Objects.requireNonNull(movementCorrection, "movementCorrection");
            range = Math.max(0.0, range);
            wallRange = Math.max(0.0, wallRange);
            slotResetTicks = Math.max(0, slotResetTicks);
            effectTimeoutTicks = Math.max(1, effectTimeoutTicks);
        }
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Object slotOwner;
    private final BlockPos pos;
    private final Function<LocalPlayer, PlacementSlot> slotFinder;
    private final Settings settings;
    private final BooleanSupplier effect;
    private final Runnable clickStep = this::clickStep;

    private BlockInteractionPipeline.RotationPlan rotationPlan;
    private boolean clicked;
    private long clickedTick;
    private long tickNow;
    private ActionResult clickFailure;

    /**
     * @param slotOwner  owner of the hotbar lease; the same object the neighbouring place/use actions use
     * @param slotFinder finds the slot to click with; re-run every tick, null means the item is gone
     * @param effect     true once the world shows the click worked; null succeeds at the click
     */
    public InteractBlockAction(Object slotOwner, String name, BlockPos pos,
                               Function<LocalPlayer, PlacementSlot> slotFinder, Settings settings,
                               BooleanSupplier effect) {
        super(name != null ? name : "InteractBlock");
        this.slotOwner = Objects.requireNonNull(slotOwner, "slotOwner");
        this.pos = pos.immutable();
        this.slotFinder = Objects.requireNonNull(slotFinder, "slotFinder");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.effect = effect;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        tickNow = ctx.tick();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return ActionResult.failure(FailureReason.PRECONDITION, "no world");
        }
        if (clickFailure != null) return clickFailure;
        if (clicked) return observeEffect(ctx);
        if (elapsedTicks(ctx) > AIM_TIMEOUT_TICKS) {
            return ActionResult.failure(FailureReason.TIMEOUT, "block never clickable");
        }

        PlacementSlot slot = slotFinder.apply(player);
        if (slot == null) return ActionResult.failure(FailureReason.MISSING_ITEM, "no item to click with");

        boolean normal = settings.rotate() == PlacementRotationMode.NORMAL;
        boolean acquired;
        if (normal && slot.needsHotbar()) {
            acquired = acquire(ctx, ActionResource.INTERACTION, ActionResource.HOTBAR, ActionResource.ROTATION);
        } else if (normal) {
            acquired = acquire(ctx, ActionResource.INTERACTION, ActionResource.ROTATION);
        } else if (slot.needsHotbar()) {
            acquired = acquire(ctx, ActionResource.INTERACTION, ActionResource.HOTBAR);
        } else {
            acquired = acquire(ctx, ActionResource.INTERACTION);
        }
        if (!acquired) return ActionResult.RUNNING;

        if (normal) {
            if (rotationPlan == null) {
                // Lifetime 2: the request must still be alive when RotationUpdateEvent POST runs the click.
                rotationPlan = new BlockInteractionPipeline.RotationPlan(
                        true, List.of(), 2, 4.0f, false, settings.movementCorrection(), ctx.priority());
            }
            Vec3 center = Vec3.atCenterOf(pos);
            BlockInteractionPipeline.INSTANCE.rotateTo(this,
                    Rotation.lookingAt(center, player.getEyePosition()).normalize(), center, rotationPlan, null);
        }
        ctx.defer(ExecutionPhase.AFTER_ROTATION, this, clickStep);
        return ActionResult.RUNNING;
    }

    private ActionResult observeEffect(ActionContext ctx) {
        if (effect == null || effect.getAsBoolean()) return ActionResult.SUCCESS;
        if (ctx.tick() - clickedTick > settings.effectTimeoutTicks()) {
            return ActionResult.failure(FailureReason.REJECTED, "no effect after click");
        }
        return ActionResult.RUNNING;
    }

    /** Runs after this tick's rotation was computed, before the movement packet. */
    private void clickStep() {
        LocalPlayer player = mc.player;
        if (player == null || !isLive() || clicked) return;
        boolean normal = settings.rotate() == PlacementRotationMode.NORMAL;
        if (normal && !RotationManager.INSTANCE.ownsActiveRotation(this)) return;

        PlacementSlot slot = slotFinder.apply(player);
        if (slot == null) return;
        BlockHitResult hit = resolveHit(player, normal);
        if (hit == null) return;

        InteractionResult result = BlockInteractionPipeline.INSTANCE.useOnBlock(
                slotOwner, player, slot.hand(), slot.hotbarSlot(), settings.slotResetTicks(), hit, settings.swing());
        if (result == null || !result.consumesAction()) {
            clickFailure = ActionResult.failure(FailureReason.REJECTED, "click not accepted: " + result);
            return;
        }
        clicked = true;
        clickedTick = tickNow;
    }

    /** The click target: the traced hit of this tick's rotation, or a synthetic hit inside wall range. */
    private BlockHitResult resolveHit(LocalPlayer player, boolean normal) {
        Rotation rotation = normal ? PlacementExecutor.rotationOfThisTick(player)
                : new Rotation(player.getYRot(), player.getXRot(), true);
        BlockHitResult traced = BlockInteractionPipeline.trace(player, rotation, Math.max(settings.range(),
                settings.wallRange()));
        double distanceSq = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
        if (traced != null && traced.getType() == HitResult.Type.BLOCK && pos.equals(traced.getBlockPos())
                && distanceSq <= settings.range() * settings.range()) {
            return traced;
        }
        double wall = settings.wallRange();
        boolean withinWall = wall > 0.0 && distanceSq <= wall * wall;
        boolean withinReach = !normal && distanceSq <= settings.range() * settings.range();
        if (!withinWall && !withinReach) return null;
        Direction side = player.getEyeY() >= pos.getY() + 0.5 ? Direction.UP : Direction.DOWN;
        return new BlockHitResult(Vec3.atCenterOf(pos), side, pos, false);
    }

    @Override
    protected void onCancel(ActionContext ctx) {
        RotationManager.INSTANCE.release(this, true);
    }

    @Override
    protected void onEnd(ActionContext ctx, ActionResult result) {
        RotationManager.INSTANCE.release(this, true);
    }

    @Override
    protected void onRevoked(ActionLease lease) {
        RotationManager.INSTANCE.release(this, true);
        InventorySwap.INSTANCE.releaseHotbar(slotOwner);
    }
}
