/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.printer;

import combatant.client.automation.placement.PlacementTarget;
import combatant.client.mixins.accessors.BlockItemInvoker;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.block.scaffold.ScaffoldPlacementTarget;
import combatant.client.util.schematic.StateAdjustments;
import combatant.client.util.schematic.StateComparison;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Finds the click (support block, face, hit point, look) that makes the block item produce the schematic state.
 * Candidates are pruned by {@link PlacementOrientation.Requirement#allows}, then each survivor is run through the
 * block's own placement rule with the player turned to the aim rotation, exactly what the server will do. The
 * returned target is pinned and carries a {@link PlacementTarget.ClickCheck} that repeats the simulation with the
 * rotation the server judges the click with (the one it already has), so a block whose facing follows the look is
 * never clicked while the rotation is still turning.
 */
public final class PrinterClickPlanner {

    private static final double[] ACROSS = {0.0, -0.3, 0.3};
    private static final double[] SIDE_HEIGHTS = {-0.25, 0.25};
    private static final double[] FINE = {-0.44, -0.15, 0.15, 0.44};

    private PrinterClickPlanner() {
    }

    /** The pinned click for one cell, or null when no support/face reproduces the wanted state. */
    public static boolean accepts(LocalPlayer player, Level level, ItemStack stack, BlockPos target,
                                  BlockState wanted, Rotation rotation, BlockHitResult hit) {
        BlockState result = simulate(player, level, stack, target, hit.getBlockPos(), hit.getDirection(),
                hit.getLocation(), rotation);
        return result != null && exact(result, wanted);
    }

    public static ScaffoldPlacementTarget find(LocalPlayer player, Level level, BlockPos target, BlockState wanted,
                                               ItemStack stack, PlacementOrientation.Requirement need,
                                               boolean aimAtHit, double range) {
        return findClick(player, level, target, wanted, stack, need, aimAtHit, range);
    }

    static ScaffoldPlacementTarget findClick(LocalPlayer player, Level level, BlockPos target, BlockState wanted,
                                             ItemStack stack, PlacementOrientation.Requirement need,
                                             boolean aimAtHit, double range) {
        if (!(stack.getItem() instanceof BlockItem)) return null;
        Vec3 eyes = player.getEyePosition();
        double rangeSq = range * range;
        ScaffoldPlacementTarget best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            double[] across = pass == 0 ? ACROSS : FINE;
            for (Direction toSupport : Direction.values()) {
                BlockPos support = target.relative(toSupport);
                BlockState supportState = level.getBlockState(support);
                if (supportState.canBeReplaced() || supportState.getShape(level, support).isEmpty()) continue;
                Direction face = toSupport.getOpposite();
                Vec3 center = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5,
                        face.getStepZ() * 0.5);
                double facing = (eyes.x - center.x) * face.getStepX() + (eyes.y - center.y) * face.getStepY()
                        + (eyes.z - center.z) * face.getStepZ();
                if (facing <= 0.0) continue;
                double[] heights = pass == 1 ? FINE : face.getAxis() == Direction.Axis.Y ? ACROSS : SIDE_HEIGHTS;
                for (double u : across) {
                    for (double v : heights) {
                        Vec3 point = pointOnFace(center, face, u, v);
                        double distanceSq = eyes.distanceToSqr(point);
                        if (distanceSq > rangeSq || distanceSq >= bestDistance) continue;
                        if (!need.allows(face, point.y - target.getY())) continue;
                        Rotation rotation = Rotation.lookingAt(point, eyes);
                        BlockState result = simulate(player, level, stack, target, support, face, point,
                                aimAtHit ? rotation : null);
                        if (result == null || !exact(result, wanted)) continue;
                        BlockHitResult hit = trace(player, eyes, rotation, Math.sqrt(distanceSq) + 0.5);
                        if (hit == null || !hit.getBlockPos().equals(support) || hit.getDirection() != face) continue;
                        best = new ScaffoldPlacementTarget(support, target, face, point, target.getY(), rotation);
                        bestDistance = distanceSq;
                    }
                }
            }
        }
        return best;
    }

    /** Whether a click producing {@code result} builds {@code wanted}, up to derived state and later hand clicks. */
    static boolean exact(BlockState result, BlockState wanted) {
        return result == wanted || StateComparison.matches(result, wanted)
                || result.getBlock() == wanted.getBlock()
                && StateAdjustments.clicksNeeded(result, wanted) != StateAdjustments.IMPOSSIBLE;
    }

    /** The state the block item would place for this click; {@code rotation} replaces the player's look briefly. */
    static BlockState simulate(LocalPlayer player, Level level, ItemStack stack, BlockPos target, BlockPos support,
                               Direction face, Vec3 hit, Rotation rotation) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        try {
            if (rotation != null) {
                player.setYRot(rotation.yaw());
                player.setXRot(rotation.pitch());
            }
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(hit, face, support, false));
            if (!context.canPlace() || !context.getClickedPos().equals(target)) return null;
            BlockState state = ((BlockItemInvoker) blockItem).combatant$getPlacementState(context);
            if (state == null || !state.canSurvive(level, target)) return null;
            if (!level.isUnobstructed(state, target, CollisionContext.placementContext(player))) return null;
            return state;
        } catch (RuntimeException e) {
            return null;
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    private static BlockHitResult trace(LocalPlayer player, Vec3 eyes, Rotation rotation, double range) {
        Vec3 end = eyes.add(rotation.directionVector().scale(range));
        HitResult hit = player.level().clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    private static Vec3 pointOnFace(Vec3 center, Direction face, double u, double v) {
        return switch (face.getAxis()) {
            case X -> center.add(0.0, v, u);
            case Y -> center.add(u, 0.0, v);
            case Z -> center.add(u, v, 0.0);
        };
    }
}
