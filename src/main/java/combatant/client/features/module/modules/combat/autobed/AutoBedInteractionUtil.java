/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autobed;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoBedInteractionUtil {
    private AutoBedInteractionUtil() {
    }

    public static Vec3 bedExplosionVec(BlockPos bedBlockPos) {
        return Vec3.atCenterOf(bedBlockPos);
    }

    public static Vec3 bedPairCenter(BlockPos footPos, BlockPos headPos) {
        return Vec3.atCenterOf(footPos).add(Vec3.atCenterOf(headPos)).scale(0.5);
    }

    public static boolean isBedBlock(BlockState state) {
        return state != null && state.is(BlockTags.BEDS);
    }

    public static boolean canPlaceBedAt(Level level, BlockPos footPos, BlockPos headPos) {
        if (level == null || footPos == null || headPos == null) return false;
        if (!level.isInWorldBounds(footPos) || !level.isInWorldBounds(headPos)) return false;
        if (!level.getBlockState(footPos).canBeReplaced()) return false;
        if (!level.getBlockState(headPos).canBeReplaced()) return false;
        // Vanilla BedBlock placement only requires the second half to be replaceable/border-valid.
        // The actual clicked support only has to resolve BlockPlaceContext#getClickedPos() to the foot block.
        return hasPlaceSupport(level, footPos);
    }

    public static boolean hasBedSupport(Level level, BlockPos pos) {
        return hasPlaceSupport(level, pos);
    }

    public static boolean hasPlaceSupport(Level level, BlockPos placePos) {
        if (level == null || placePos == null) return false;
        for (Direction face : Direction.values()) {
            BlockPos support = placePos.relative(face.getOpposite());
            if (isSolidSupport(level, support)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSolidSupport(Level level, BlockPos support) {
        if (level == null || support == null || !level.isInWorldBounds(support)) return false;
        BlockState state = level.getBlockState(support);
        return !state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, support).isEmpty();
    }

    public static BlockPos resolveOtherPart(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !isBedBlock(state)) return null;
        Direction facing = bedFacing(state);
        if (facing == null) return null;
        BedPart part = bedPart(state);
        if (part == BedPart.HEAD) {
            return pos.relative(facing.getOpposite());
        }
        return pos.relative(facing);
    }

    public static BlockPos resolveFootPos(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !isBedBlock(state)) return null;
        Direction facing = bedFacing(state);
        if (facing == null) return null;
        BedPart part = bedPart(state);
        return part == BedPart.HEAD ? pos.relative(facing.getOpposite()) : pos;
    }

    public static BlockPos resolveHeadPos(BlockState state, BlockPos pos) {
        if (state == null || pos == null || !isBedBlock(state)) return null;
        Direction facing = bedFacing(state);
        if (facing == null) return null;
        BedPart part = bedPart(state);
        return part == BedPart.HEAD ? pos : pos.relative(facing);
    }

    public static Direction bedFacing(BlockState state) {
        if (state == null) return Direction.NORTH;
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) return state.getValue(HorizontalDirectionalBlock.FACING);
        return Direction.NORTH;
    }

    public static BedPart bedPart(BlockState state) {
        if (state != null && state.hasProperty(BedBlock.PART)) return state.getValue(BedBlock.PART);
        return BedPart.FOOT;
    }

    public static BlockHitResult getBedInteractResult(Level level,
                                                      LocalPlayer player,
                                                      BlockPos pos,
                                                      AutoBedPlacementMode mode,
                                                      float range,
                                                      float wallRange) {
        if (mode == AutoBedPlacementMode.STRICT) {
            return getStrictBlockInteract(level, player, pos, range, wallRange);
        }
        if (mode == AutoBedPlacementMode.LEGIT) {
            return getLegitBlockInteract(level, player, pos, range, wallRange);
        }
        return getDefaultBlockInteract(level, player, pos, range, wallRange);
    }

    public static BlockHitResult getPlaceSupportInteract(Level level,
                                                         LocalPlayer player,
                                                         BlockPos footPos,
                                                         BlockPos headPos,
                                                         AutoBedPlacementMode mode,
                                                         float range,
                                                         float wallRange) {
        if (level == null || player == null || footPos == null || headPos == null) return null;

        // BedItem derives the bed FOOT from BlockPlaceContext#getClickedPos().
        // Clicking any solid neighbour face that points into footPos is valid, so beds can be placed from
        // a single side/top support block. The head half only has to be replaceable and unobstructed.
        return getBestFootPlacementInteract(level, player, footPos, mode, range, wallRange);
    }

    private static BlockHitResult getBestFootPlacementInteract(Level level,
                                                               LocalPlayer player,
                                                               BlockPos placePos,
                                                               AutoBedPlacementMode mode,
                                                               float range,
                                                               float wallRange) {
        Vec3 eyes = player.getEyePosition();
        double bestDistance = Double.MAX_VALUE;
        BlockHitResult best = null;
        for (Direction face : Direction.values()) {
            BlockPos support = placePos.relative(face.getOpposite());
            if (!isSolidSupport(level, support)) continue;
            BlockHitResult hit = mode == AutoBedPlacementMode.LEGIT
                    ? getBestSupportFacePoint(level, player, support, face, range, wallRange)
                    : getSupportFaceCenter(level, player, support, face, range, wallRange);
            if (hit == null) continue;
            double distanceSq = eyes.distanceToSqr(hit.getLocation());
            if (distanceSq < bestDistance) {
                bestDistance = distanceSq;
                best = hit;
            }
        }
        return best;
    }

    public static BlockHitResult getSyntheticBedInteractResult(LocalPlayer player, BlockPos pos, float range) {
        if (player == null || pos == null) return null;
        Vec3 vec = Vec3.atCenterOf(pos);
        if (player.getEyePosition().distanceToSqr(vec) > range * range) return null;
        Direction side = player.getEyeY() >= pos.getY() + 0.5 ? Direction.UP : Direction.DOWN;
        return new BlockHitResult(vec, side, pos, false);
    }

    public static float yawForFacing(Direction facing) {
        if (facing == null) return 0.0f;
        return switch (facing) {
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    public static BlockHitResult getDefaultBlockInteract(Level level,
                                                         LocalPlayer player,
                                                         BlockPos pos,
                                                         float range,
                                                         float wallRange) {
        if (level == null || player == null || pos == null) return null;

        Vec3 vec = Vec3.atCenterOf(pos);
        Vec3 eyes = player.getEyePosition();
        double distanceSq = eyes.distanceToSqr(vec);
        if (distanceSq > range * range) return null;
        if (!isVisibleOrWithinWallRange(level, player, eyes, vec, pos, distanceSq, wallRange)) return null;

        Direction side = player.getEyeY() >= pos.getY() + 0.5 ? Direction.UP : Direction.DOWN;
        return new BlockHitResult(vec, side, pos, false);
    }

    public static BlockHitResult getStrictBlockInteract(Level level,
                                                        LocalPlayer player,
                                                        BlockPos pos,
                                                        float range,
                                                        float wallRange) {
        if (level == null || player == null || pos == null) return null;

        Vec3 eyes = player.getEyePosition();
        double bestDistance = Double.MAX_VALUE;
        BlockHitResult best = null;
        for (Direction direction : Direction.values()) {
            Vec3 normal = Vec3.atLowerCornerOf(direction.getUnitVec3i());
            Vec3 sample = Vec3.atCenterOf(pos).add(normal.scale(0.5));
            double distanceSq = eyes.distanceToSqr(sample);
            if (distanceSq > range * range) continue;
            if (!isVisibleOrWithinWallRange(level, player, eyes, sample, pos, distanceSq, wallRange)) continue;
            if (distanceSq < bestDistance) {
                bestDistance = distanceSq;
                best = new BlockHitResult(sample, direction, pos, false);
            }
        }
        return best;
    }

    public static BlockHitResult getLegitBlockInteract(Level level,
                                                       LocalPlayer player,
                                                       BlockPos pos,
                                                       float range,
                                                       float wallRange) {
        if (level == null || player == null || pos == null) return null;

        Vec3 eyes = player.getEyePosition();
        double bestDistance = Double.MAX_VALUE;
        BlockHitResult best = null;
        for (float x = 0.1f; x <= 0.9f; x += 0.2f) {
            for (float y = 0.1f; y <= 0.9f; y += 0.2f) {
                for (float z = 0.1f; z <= 0.9f; z += 0.2f) {
                    Vec3 point = new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    double distanceSq = eyes.distanceToSqr(point);
                    if (distanceSq > range * range) continue;
                    if (!isVisibleOrWithinWallRange(level, player, eyes, point, pos, distanceSq, wallRange)) continue;
                    if (distanceSq < bestDistance) {
                        bestDistance = distanceSq;
                        best = new BlockHitResult(point, Direction.UP, pos, false);
                    }
                }
            }
        }
        return best;
    }

    private static BlockHitResult getSupportFaceCenter(Level level,
                                                       LocalPlayer player,
                                                       BlockPos support,
                                                       Direction face,
                                                       float range,
                                                       float wallRange) {
        Vec3 eyes = player.getEyePosition();
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        Vec3 hitVec = Vec3.atCenterOf(support).add(normal.scale(0.5));
        double distanceSq = eyes.distanceToSqr(hitVec);
        if (distanceSq > range * range) return null;
        if (!isVisibleOrWithinWallRange(level, player, eyes, hitVec, support, distanceSq, wallRange)) return null;
        return new BlockHitResult(hitVec, face, support, false);
    }

    private static BlockHitResult getBestSupportFacePoint(Level level,
                                                          LocalPlayer player,
                                                          BlockPos support,
                                                          Direction face,
                                                          float range,
                                                          float wallRange) {
        Vec3 eyes = player.getEyePosition();
        double bestDistance = Double.MAX_VALUE;
        Vec3 bestPoint = null;
        for (float a = 0.2f; a <= 0.8f; a += 0.3f) {
            for (float b = 0.2f; b <= 0.8f; b += 0.3f) {
                Vec3 point = pointOnFace(support, face, a, b);
                double distanceSq = eyes.distanceToSqr(point);
                if (distanceSq > range * range) continue;
                if (!isVisibleOrWithinWallRange(level, player, eyes, point, support, distanceSq, wallRange)) continue;
                if (distanceSq < bestDistance) {
                    bestDistance = distanceSq;
                    bestPoint = point;
                }
            }
        }
        return bestPoint == null ? null : new BlockHitResult(bestPoint, face, support, false);
    }

    private static Vec3 pointOnFace(BlockPos pos, Direction face, float a, float b) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        return switch (face) {
            case DOWN -> new Vec3(x + a, y, z + b);
            case UP -> new Vec3(x + a, y + 1.0, z + b);
            case NORTH -> new Vec3(x + a, y + b, z);
            case SOUTH -> new Vec3(x + a, y + b, z + 1.0);
            case WEST -> new Vec3(x, y + a, z + b);
            case EAST -> new Vec3(x + 1.0, y + a, z + b);
        };
    }

    private static boolean isVisibleOrWithinWallRange(Level level,
                                                      LocalPlayer player,
                                                      Vec3 eyes,
                                                      Vec3 point,
                                                      BlockPos expectedBlock,
                                                      double distanceSq,
                                                      float wallRange) {
        BlockHitResult wall = level.clip(new ClipContext(
                eyes,
                point,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (wall == null || wall.getType() != HitResult.Type.BLOCK || expectedBlock.equals(wall.getBlockPos())) {
            return true;
        }
        return distanceSq <= wallRange * wallRange;
    }

    public static InteractionHand heldBedHand(LocalPlayer player) {
        if (player == null) return null;
        if (isBed(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        if (isBed(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        return null;
    }

    public static InteractionHand heldDetonatorHand(LocalPlayer player) {
        if (player == null) return null;
        if (!isBed(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (!isBed(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    public static boolean isBed(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ItemTags.BEDS);
    }

    public static boolean isDetonator(ItemStack stack) {
        return stack == null || stack.isEmpty() || !stack.is(ItemTags.BEDS);
    }
}
