/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoanchor;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoAnchorInteractionUtil {
    private AutoAnchorInteractionUtil() {
    }

    public static Vec3 anchorVec(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    public static boolean hasSolidPlaceSupport(Level level, BlockPos placePos) {
        if (level == null || placePos == null) return false;
        for (Direction direction : Direction.values()) {
            BlockPos support = placePos.relative(direction);
            if (!level.isInWorldBounds(support)) continue;

            BlockState state = level.getBlockState(support);
            if (!state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, support).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static BlockHitResult getInteractResult(
            Level level,
            LocalPlayer player,
            BlockPos pos,
            boolean existingAnchor,
            boolean allowAirPlace,
            AutoAnchorPlacementMode mode,
            float range,
            float wallRange
    ) {
        if (existingAnchor || allowAirPlace) {
            return getAnchorInteractResult(level, player, pos, mode, range, wallRange);
        }
        return getPlaceSupportInteract(level, player, pos, mode, range, wallRange);
    }

    public static BlockHitResult getAnchorInteractResult(
            Level level,
            LocalPlayer player,
            BlockPos pos,
            AutoAnchorPlacementMode mode,
            float range,
            float wallRange
    ) {
        if (mode == AutoAnchorPlacementMode.STRICT) {
            return getStrictAnchorInteract(level, player, pos, range, wallRange);
        }
        if (mode == AutoAnchorPlacementMode.LEGIT) {
            return getLegitAnchorInteract(level, player, pos, range, wallRange);
        }
        return getDefaultAnchorInteract(level, player, pos, range, wallRange);
    }

    public static BlockHitResult getDefaultAnchorInteract(Level level,
                                                          LocalPlayer player,
                                                          BlockPos pos,
                                                          float range,
                                                          float wallRange) {
        if (level == null || player == null || pos == null) return null;

        Vec3 vec = Vec3.atCenterOf(pos);
        Vec3 eyes = player.getEyePosition();
        double distanceSq = eyes.distanceToSqr(vec);
        if (distanceSq > range * range) return null;

        if (!isVisibleOrWithinWallRange(level, player, eyes, vec, pos, distanceSq, wallRange)) {
            return null;
        }

        Direction side = player.getEyeY() >= pos.getY() + 0.5 ? Direction.UP : Direction.DOWN;
        return new BlockHitResult(vec, side, pos, false);
    }

    public static BlockHitResult getStrictAnchorInteract(Level level,
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

    public static BlockHitResult getLegitAnchorInteract(Level level,
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


    public static BlockHitResult getSyntheticAnchorInteractResult(LocalPlayer player,
                                                                  BlockPos pos,
                                                                  float range) {
        if (player == null || pos == null) return null;
        Vec3 vec = Vec3.atCenterOf(pos);
        if (player.getEyePosition().distanceToSqr(vec) > range * range) return null;
        Direction side = player.getEyeY() >= pos.getY() + 0.5 ? Direction.UP : Direction.DOWN;
        return new BlockHitResult(vec, side, pos, false);
    }

    public static BlockHitResult getPlaceSupportInteract(Level level,
                                                         LocalPlayer player,
                                                         BlockPos placePos,
                                                         AutoAnchorPlacementMode mode,
                                                         float range,
                                                         float wallRange) {
        if (level == null || player == null || placePos == null) return null;

        Vec3 eyes = player.getEyePosition();
        double bestDistance = Double.MAX_VALUE;
        BlockHitResult best = null;
        for (Direction direction : Direction.values()) {
            BlockPos support = placePos.relative(direction);
            if (!level.isInWorldBounds(support)) continue;

            BlockState state = level.getBlockState(support);
            if (state.isAir() || state.canBeReplaced() || state.getCollisionShape(level, support).isEmpty()) {
                continue;
            }

            Direction face = direction.getOpposite();
            BlockHitResult candidate = mode == AutoAnchorPlacementMode.LEGIT
                    ? getBestSupportFacePoint(level, player, support, face, range, wallRange)
                    : getSupportFaceCenter(level, player, support, face, range, wallRange);
            if (candidate == null) continue;

            double distanceSq = eyes.distanceToSqr(candidate.getLocation());
            if (distanceSq < bestDistance) {
                bestDistance = distanceSq;
                best = candidate;
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

    public static InteractionHand heldAnchorHand(LocalPlayer player) {
        if (player == null) return null;
        if (player.getOffhandItem().is(Items.RESPAWN_ANCHOR)) return InteractionHand.OFF_HAND;
        if (player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) return InteractionHand.MAIN_HAND;
        return null;
    }

    public static InteractionHand heldGlowstoneHand(LocalPlayer player) {
        if (player == null) return null;
        if (player.getOffhandItem().is(Items.GLOWSTONE)) return InteractionHand.OFF_HAND;
        if (player.getMainHandItem().is(Items.GLOWSTONE)) return InteractionHand.MAIN_HAND;
        return null;
    }

    public static InteractionHand heldDetonatorHand(LocalPlayer player) {
        if (player == null) return null;
        if (!isGlowstone(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (!isGlowstone(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    public static boolean isAnchor(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.RESPAWN_ANCHOR);
    }

    public static boolean isGlowstone(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.GLOWSTONE);
    }

    public static boolean isDetonator(ItemStack stack) {
        return stack == null || stack.isEmpty() || !stack.is(Items.GLOWSTONE);
    }
}
