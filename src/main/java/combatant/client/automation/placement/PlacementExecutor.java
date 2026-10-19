/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.placement;

import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.block.scaffold.ScaffoldPlacementTarget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;

import java.util.function.BooleanSupplier;

/**
 * Validates and sends one placement click, and registers the confirmation ticket for it. Runs
 * inside {@code ExecutionPhase.AFTER_ROTATION}: the click is checked against the rotation this
 * tick's movement packet will carry, and goes out before that packet.
 */
public final class PlacementExecutor {

    public enum Status {
        /** Rotation does not hit the support yet, or no hit could be built; nothing was sent. */
        NOT_READY,
        /** The cell is no longer free; nothing was sent. */
        BLOCKED,
        /** The client refused the interaction; nothing reached the server. */
        FAILED,
        /** The click was sent; the ticket tells whether the server accepted it. */
        SENT
    }

    public record Attempt(Status status, PlacementConfirmation.Ticket ticket) {
        static final Attempt NOT_READY = new Attempt(Status.NOT_READY, null);
        static final Attempt BLOCKED = new Attempt(Status.BLOCKED, null);
        static final Attempt FAILED = new Attempt(Status.FAILED, null);
    }

    private PlacementExecutor() {
    }

    /** The rotation this tick's movement packet will carry, as far as the client knows. */
    public static Rotation rotationOfThisTick(LocalPlayer player) {
        Rotation current = RotationManager.INSTANCE.getCurrentRotation();
        return current != null ? current : new Rotation(player.getYRot(), player.getXRot(), true);
    }

    public static Attempt place(Object owner, LocalPlayer player, PlacementTarget target,
                                PlacementConstraints constraints) {
        PlacementSlot slot = target.slot();
        BlockPos pos = target.pos();
        ScaffoldPlacementTarget support = target.support();
        boolean normal = constraints.rotate() == PlacementRotationMode.NORMAL;
        Rotation verification = normal ? rotationOfThisTick(player) : support.getRotation();

        if (normal && !PlacementPlanner.canReach(player, support.getInteractedBlockPos(), verification, constraints)) {
            return Attempt.NOT_READY;
        }

        BlockHitResult hit = raytrace(player, support.getInteractedBlockPos(), verification,
                support.getDirection(), constraints);
        if (hit == null && (constraints.constructFailResult() || !normal)) {
            hit = new BlockHitResult(support.getHitVec(), support.getDirection(), support.getInteractedBlockPos(), false);
        }
        if (hit == null) return Attempt.NOT_READY;
        if (target.check() != null && !target.check().accepts(player, verification, hit)) return Attempt.NOT_READY;

        if (slot.stack().getItem() instanceof BlockItem && !player.level().getBlockState(pos).canBeReplaced()) {
            return Attempt.BLOCKED;
        }

        PlacementTracker tracker = PlacementTracker.INSTANCE;
        tracker.beginCapture();
        InteractionResult result = BlockInteractionPipeline.INSTANCE.useOnBlock(
                owner, player, slot.hand(), slot.hotbarSlot(), constraints.randomSlotResetTicks(), hit,
                constraints.swing());
        if (result == null || !result.consumesAction()) return Attempt.FAILED;

        return new Attempt(Status.SENT, tracker.track(constraints.confirmTimeoutTicks(), expectation(slot, pos)));
    }

    /** What the world shows once the server accepted the placement. */
    static BooleanSupplier expectation(PlacementSlot slot, BlockPos pos) {
        Block expected = slot.stack().getItem() instanceof BlockItem item && !(item instanceof StandingAndWallBlockItem)
                ? item.getBlock() : null;
        BlockPos fixed = pos.immutable();
        return () -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return false;
            BlockState state = level.getBlockState(fixed);
            return expected != null ? state.is(expected) : !state.canBeReplaced();
        };
    }

    private static BlockHitResult raytrace(LocalPlayer player, BlockPos interactedPos, Rotation rotation,
                                           Direction direction, PlacementConstraints constraints) {
        BlockHitResult hit = BlockInteractionPipeline.trace(player, rotation, constraints.traceRange());
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && interactedPos.equals(hit.getBlockPos())) {
            return new BlockHitResult(hit.getLocation(), direction, interactedPos, false);
        }
        if (constraints.constructFailResult()) {
            return new BlockHitResult(Vec3.atCenterOf(interactedPos), direction, interactedPos, false);
        }
        return null;
    }
}
