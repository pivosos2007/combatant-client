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
import combatant.client.util.block.interaction.FaceVisibility;
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
 * Validates and sends one placement click, and registers the confirmation ticket for it. Runs inside
 * {@code ExecutionPhase.AFTER_ROTATION}, so the click goes out before this tick's movement packet.
 *
 * <p>NORMAL mode clicks only along the look the server already has: the rotation and eyes of the last movement
 * packet. GrimAC settles queued clicks before the rotation of the click's own tick arrives whenever a slot change or
 * transaction reply comes first or that packet arrives late, and after one miss it re-checks every click that way;
 * a rotation sent together with the click is too late then. The owner therefore aims first and clicks on a later
 * tick (see {@link #aimedByServer}).</p>
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

    /**
     * Whether a click on the support may go out now. The support may have been planned on an earlier tick, and the
     * server judges the click from the position it last received, so the face has to face those eyes as well and the
     * support has to be in their reach; NORMAL mode also needs {@link #aimedByServer}.
     */
    public static boolean readyToClick(LocalPlayer player, ScaffoldPlacementTarget support,
                                       PlacementConstraints constraints) {
        BlockPos interactedPos = support.getInteractedBlockPos();
        if (!FaceVisibility.isVisible(player, interactedPos, support.getDirection())) return false;
        if (!BlockInteractionPipeline.cubeWithinReach(player, interactedPos, constraints.traceRange())) return false;
        return constraints.rotate() != PlacementRotationMode.NORMAL || aimedByServer(player, support, constraints);
    }

    /**
     * Whether the rotation the server last received already points at the support, from the eyes it last received:
     * the ray meets the support's cube within reach (GrimAC RotationPlace), and either reaches the support with
     * nothing in between or the support is within wall range.
     */
    public static boolean aimedByServer(LocalPlayer player, ScaffoldPlacementTarget support,
                                        PlacementConstraints constraints) {
        Rotation look = RotationManager.INSTANCE.getServerRotation();
        Vec3 eyes = FaceVisibility.serverEyePosition(player);
        BlockPos interactedPos = support.getInteractedBlockPos();
        if (!BlockInteractionPipeline.serverLookMeetsCube(player, interactedPos, constraints.traceRange())) return false;
        double wallRange = constraints.wallRange();
        if (wallRange > 0.0 && Vec3.atCenterOf(interactedPos).distanceToSqr(eyes) <= wallRange * wallRange) return true;
        BlockHitResult hit = BlockInteractionPipeline.trace(player, eyes, look, constraints.range());
        return hit != null && hit.getType() == HitResult.Type.BLOCK && interactedPos.equals(hit.getBlockPos());
    }

    public static Attempt place(Object owner, LocalPlayer player, PlacementTarget target,
                                PlacementConstraints constraints) {
        PlacementSlot slot = target.slot();
        BlockPos pos = target.pos();
        ScaffoldPlacementTarget support = target.support();
        BlockPos interactedPos = support.getInteractedBlockPos();
        boolean normal = constraints.rotate() == PlacementRotationMode.NORMAL;
        if (!readyToClick(player, support, constraints)) return Attempt.NOT_READY;

        Rotation look = normal ? RotationManager.INSTANCE.getServerRotation() : support.getRotation();
        Vec3 eyes = normal ? FaceVisibility.serverEyePosition(player) : player.getEyePosition();
        BlockHitResult hit = tracedClick(player, eyes, look, support, constraints);
        // Aimed but traced onto another face (or through a wall in wall range), or clicking without rotating: the
        // cursor goes on the planned face.
        if (hit == null) hit = new BlockHitResult(support.getHitVec(), support.getDirection(), interactedPos, false);
        if (target.check() != null && !target.check().accepts(player, look, hit)) return Attempt.NOT_READY;

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

    /** The click the look produces when it traces onto the planned face of the support, or null. */
    private static BlockHitResult tracedClick(LocalPlayer player, Vec3 eyes, Rotation look,
                                              ScaffoldPlacementTarget support, PlacementConstraints constraints) {
        BlockPos interactedPos = support.getInteractedBlockPos();
        Direction direction = support.getDirection();
        BlockHitResult hit = BlockInteractionPipeline.trace(player, eyes, look, constraints.traceRange());
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && interactedPos.equals(hit.getBlockPos())
                && hit.getDirection() == direction) {
            return new BlockHitResult(hit.getLocation(), direction, interactedPos, false);
        }
        return null;
    }
}
