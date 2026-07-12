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

package combatant.client.util.block.scaffold;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public enum ScaffoldTargetFinder {
    ;

    public static ScaffoldPlacementTarget findTarget(LocalPlayer player, BlockPos targetPos, ItemStack stack) {
        return findTarget(player, targetPos, stack, List.of(BlockPos.ZERO), null, false, null, null);
    }

    public static ScaffoldPlacementTarget findTarget(
            LocalPlayer player,
            BlockPos targetPos,
            ItemStack stack,
            List<BlockPos> offsets,
            Comparator<BlockPos> comparator,
            boolean considerFacingAwayFaces
    ) {
        return findTarget(player, targetPos, stack, offsets, comparator, considerFacingAwayFaces, null, null);
    }

    public static ScaffoldPlacementTarget findTarget(
            LocalPlayer player,
            BlockPos targetPos,
            ItemStack stack,
            List<BlockPos> offsets,
            Comparator<BlockPos> comparator,
            boolean considerFacingAwayFaces,
            ScaffoldFacePositionFactory facePositionFactory
    ) {
        return findTarget(
                player,
                targetPos,
                stack,
                offsets,
                comparator,
                considerFacingAwayFaces,
                facePositionFactory,
                null
        );
    }

    public static ScaffoldPlacementTarget findTarget(
            LocalPlayer player,
            BlockPos targetPos,
            ItemStack stack,
            List<BlockPos> offsets,
            Comparator<BlockPos> comparator,
            boolean considerFacingAwayFaces,
            ScaffoldFacePositionFactory facePositionFactory,
            Vec3 placementEyePos
    ) {
        if (player == null || player.level() == null || stack == null || stack.isEmpty()) return null;
        if (isBlockSolid(player.level().getBlockState(targetPos), player, targetPos)) {
            return null;
        }

        Rotation baseRotation = RotationManager.INSTANCE.getServerRotation();
        Vec3 eyePos = placementEyePos != null
                ? placementEyePos
                : player.position().add(0.0, player.getEyeHeight(player.getPose()), 0.0);
        boolean preferUpperHalf = ScaffoldBlockItemSelection.shouldPreferUpperHalf(stack);
        List<BlockPos> investigationTargets = buildInvestigationTargets(targetPos, offsets, comparator);

        for (BlockPos placementPos : investigationTargets) {
            BlockState stateToInvestigate = player.level().getBlockState(placementPos);
            if (isBlockSolid(stateToInvestigate, player, placementPos)) {
                continue;
            }

            TargetMode targetMode = (stateToInvestigate.isAir() || !stateToInvestigate.getFluidState().isEmpty())
                    ? TargetMode.PLACE_AT_NEIGHBOR
                    : TargetMode.REPLACE_EXISTING_BLOCK;
            if (targetMode == TargetMode.REPLACE_EXISTING_BLOCK
                    && !canBeReplacedWith(player, placementPos, stack, stateToInvestigate)) {
                continue;
            }

            TargetPlan targetPlan = findBestTargetPlan(
                    player,
                    placementPos,
                    targetMode,
                    eyePos,
                    baseRotation,
                    considerFacingAwayFaces
            );
            if (targetPlan == null) {
                continue;
            }

            BlockState supportState = player.level().getBlockState(targetPlan.interactedPos);
            var shape = supportState.getShape(
                    player.level(),
                    targetPlan.interactedPos,
                    CollisionContext.of(player)
            );
            if (shape.isEmpty()) {
                continue;
            }

            PointOnFace pointOnFace = findTargetPointOnFace(
                    shape.toAabbs(),
                    targetPlan.interactedPos,
                    targetPlan.direction,
                    preferUpperHalf,
                    facePositionFactory
            );
            if (pointOnFace == null) {
                continue;
            }

            Vec3 hitVec = pointOnFace.point;
            Rotation rotation = Rotation.lookingAt(hitVec, eyePos).normalize();
            return new ScaffoldPlacementTarget(
                    targetPlan.interactedPos.immutable(),
                    placementPos.immutable(),
                    targetPlan.direction,
                    hitVec,
                    pointOnFace.face.minY(),
                    rotation
            );
        }

        return null;
    }

    private static TargetPlan findBestTargetPlan(
            LocalPlayer player,
            BlockPos placementPos,
            TargetMode targetMode,
            Vec3 eyePos,
            Rotation baseRotation,
            boolean considerFacingAwayFaces
    ) {
        TargetPlan bestPlan = null;
        float bestAngle = Float.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            var world = player.level();
            if (world == null) continue;

            BlockPos interactedPos = switch (targetMode) {
                case PLACE_AT_NEIGHBOR -> placementPos.relative(direction.getOpposite());
                case REPLACE_EXISTING_BLOCK -> placementPos;
            };
            BlockState supportState = world.getBlockState(interactedPos);
            if (targetMode == TargetMode.PLACE_AT_NEIGHBOR && supportState.canBeReplaced()) continue;

            Vec3 targetPositionOnBlock = centerOfFace(interactedPos, direction);

            if (!considerFacingAwayFaces && pointsAwayFromPlayer(eyePos, targetPositionOnBlock, direction)) {
                continue;
            }

            Rotation targetRotation = Rotation.lookingAt(targetPositionOnBlock, eyePos).normalize();
            float angle = baseRotation.angleTo(targetRotation);
            if (angle < bestAngle) {
                bestAngle = angle;
                bestPlan = new TargetPlan(interactedPos, direction);
            }
        }

        return bestPlan;
    }

    private static PointOnFace findTargetPointOnFace(
            List<AABB> boxes,
            BlockPos interactedPos,
            Direction direction,
            boolean preferUpperHalf,
            ScaffoldFacePositionFactory facePositionFactory
    ) {
        PointOnFace best = null;
        double bestDirectionalDistance = Double.NEGATIVE_INFINITY;
        double bestY = Double.NEGATIVE_INFINITY;

        for (AABB box : boxes) {
            ScaffoldFacePositionFactory.Face face = ScaffoldFacePositionFactory.Face.fromBox(box.move(
                    interactedPos.getX(),
                    interactedPos.getY(),
                    interactedPos.getZ()
            ), direction);

            ScaffoldFacePositionFactory.Face searchFace = face;
            if (preferUpperHalf && face.maxY() - interactedPos.getY() >= 0.9) {
                ScaffoldFacePositionFactory.Face truncated = face.truncateMinY(interactedPos.getY() + 0.6);
                if (truncated != null) {
                    searchFace = truncated;
                }
            }

            Vec3 point = facePositionFactory != null
                    ? facePositionFactory.producePoint(searchFace)
                    : searchFace.center();
            if (point == null) continue;

            double directionalDistance = directionalSupportDistance(point, direction, interactedPos);
            if (directionalDistance > bestDirectionalDistance
                    || (Math.abs(directionalDistance - bestDirectionalDistance) < 1.0E-7 && point.y > bestY)) {
                bestDirectionalDistance = directionalDistance;
                bestY = point.y;
                best = new PointOnFace(face, point);
            }
        }

        return best;
    }

    private static List<BlockPos> buildInvestigationTargets(BlockPos targetPos, List<BlockPos> offsets, Comparator<BlockPos> comparator) {
        List<BlockPos> candidates = new ArrayList<>();
        if (offsets == null || offsets.isEmpty()) {
            candidates.add(targetPos);
        } else {
            for (BlockPos offset : offsets) {
                candidates.add(targetPos.offset(offset));
            }
        }

        if (comparator != null) {
            candidates.sort((a, b) -> comparator.compare(b, a));
        }
        return candidates;
    }

    private static boolean isBlockSolid(BlockState state, LocalPlayer player, BlockPos pos) {
        return state != null
                && player != null
                && player.level() != null
                && state.isFaceSturdy(player.level(), pos, Direction.UP, SupportType.CENTER);
    }

    private static boolean canBeReplacedWith(
            LocalPlayer player,
            BlockPos pos,
            ItemStack stack,
            BlockState state
    ) {
        if (player == null || stack == null || state == null) {
            return false;
        }

        BlockPlaceContext context = new BlockPlaceContext(
                player.level(),
                player,
                InteractionHand.MAIN_HAND,
                stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
        return state.canBeReplaced(context);
    }

    private static boolean pointsAwayFromPlayer(Vec3 eyePos, Vec3 hitVec, Direction direction) {
        Vec3 delta = eyePos.subtract(hitVec);
        Vec3 normal = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        return delta.normalize().dot(normal) < 0.0;
    }

    private static Vec3 centerOfFace(BlockPos pos, Direction side) {
        double x = pos.getX() + 0.5 + side.getStepX() * 0.5;
        double y = pos.getY() + 0.5 + side.getStepY() * 0.5;
        double z = pos.getZ() + 0.5 + side.getStepZ() * 0.5;
        return new Vec3(x, y, z);
    }

    private static double directionalSupportDistance(Vec3 point, Direction direction) {
        return directionalSupportDistance(point, direction, BlockPos.ZERO);
    }

    private static double directionalSupportDistance(Vec3 point, Direction direction, BlockPos origin) {
        Vec3 centered = point.subtract(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
        Vec3 directionVector = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        Vec3 projected = new Vec3(
                centered.x * directionVector.x,
                centered.y * directionVector.y,
                centered.z * directionVector.z
        );
        return projected.lengthSqr();
    }

    private enum TargetMode {
        PLACE_AT_NEIGHBOR,
        REPLACE_EXISTING_BLOCK
    }

    private record PointOnFace(ScaffoldFacePositionFactory.Face face, Vec3 point) {
    }

    private record TargetPlan(BlockPos interactedPos, Direction direction) {
    }
}
