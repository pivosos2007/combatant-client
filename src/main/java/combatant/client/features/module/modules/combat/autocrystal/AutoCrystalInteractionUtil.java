/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoCrystalInteractionUtil {
    private AutoCrystalInteractionUtil() {
    }

    public static AABB predictedCrystalBox(BlockPos pos) {
        return new AABB(
                pos.getX(),
                pos.getY() + 1.0,
                pos.getZ(),
                pos.getX() + 1.0,
                pos.getY() + 3.0,
                pos.getZ() + 1.0
        );
    }

    public static Vec3 crystalVec(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }

    public static BlockHitResult getCrystalInteractResult(
            Level level,
            LocalPlayer player,
            BlockPos pos,
            Vec3 crystalVec,
            AutoCrystalPlacementMode mode,
            float placeRange,
            float wallRange
    ) {
        if (mode == AutoCrystalPlacementMode.STRICT) {
            return getStrictInteract(level, player, pos, placeRange);
        }
        return getDefaultInteract(level, player, crystalVec, pos, placeRange, wallRange);
    }

    public static BlockHitResult getStrictInteract(Level level, LocalPlayer player, BlockPos pos, float placeRange) {
        if (player == null || level == null || pos == null) {
            return null;
        }

        Vec3 eyes = player.getEyePosition();
        Vec3[] samples = {
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.999, pos.getZ() + 0.5),
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.001),
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.999),
                new Vec3(pos.getX() + 0.001, pos.getY() + 0.5, pos.getZ() + 0.5),
                new Vec3(pos.getX() + 0.999, pos.getY() + 0.5, pos.getZ() + 0.5)
        };
        Direction[] faces = {
                Direction.UP,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.WEST,
                Direction.EAST
        };

        double placeRangeSq = placeRange * placeRange;
        for (int i = 0; i < samples.length; i++) {
            Vec3 sample = samples[i];
            if (eyes.distanceToSqr(sample) > placeRangeSq) {
                continue;
            }

            BlockHitResult hit = level.clip(new ClipContext(
                    eyes,
                    sample,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            if (!pos.equals(hit.getBlockPos())) {
                continue;
            }
            if (hit.getDirection() != faces[i]) {
                continue;
            }

            return new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false);
        }

        return null;
    }

    public static BlockHitResult getDefaultInteract(
            Level level,
            LocalPlayer player,
            Vec3 crystalVec,
            BlockPos pos,
            float placeRange,
            float wallRange
    ) {
        if (player == null || level == null || pos == null) {
            return null;
        }

        double distanceSq = player.getEyePosition().distanceToSqr(crystalVec);
        double placeRangeSq = placeRange * placeRange;
        if (distanceSq > placeRangeSq) {
            return null;
        }

        BlockHitResult wallCheck = level.clip(new ClipContext(
                player.getEyePosition(),
                crystalVec,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (wallCheck != null
                && wallCheck.getType() == HitResult.Type.BLOCK
                && !pos.equals(wallCheck.getBlockPos())) {
            double wallRangeSq = wallRange * wallRange;
            if (distanceSq > wallRangeSq) {
                return null;
            }
        }

        return new BlockHitResult(
                crystalVec,
                level.isInWorldBounds(pos.above()) ? Direction.UP : Direction.DOWN,
                pos,
                false
        );
    }

    public static BlockHitResult getObsidianInteractResult(Level level, LocalPlayer player, BlockPos pos, float placeRange) {
        if (player == null || level == null || pos == null) {
            return null;
        }

        Vec3 eyes = player.getEyePosition();
        double rangeSq = placeRange * placeRange;
        for (Direction direction : Direction.values()) {
            BlockPos support = pos.relative(direction);
            if (!level.isInWorldBounds(support)) continue;
            var state = level.getBlockState(support);
            if (state.isAir() || state.getCollisionShape(level, support).isEmpty()) continue;

            Direction face = direction.getOpposite();
            Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
            Vec3 hitVec = Vec3.atCenterOf(support).add(normal.scale(0.5));
            if (eyes.distanceToSqr(hitVec) > rangeSq) continue;

            BlockHitResult ray = level.clip(new ClipContext(
                    eyes,
                    hitVec,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            if (ray != null && ray.getType() == HitResult.Type.BLOCK && !support.equals(ray.getBlockPos())) {
                continue;
            }

            return new BlockHitResult(hitVec, face, support, false);
        }

        return null;
    }

    public static AutoCrystalHand findCrystalHand(LocalPlayer player) {
        if (player == null) {
            return null;
        }

        if (player.getOffhandItem().is(Items.END_CRYSTAL)) {
            return new AutoCrystalHand(InteractionHand.OFF_HAND, -1);
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.END_CRYSTAL)) {
                return new AutoCrystalHand(InteractionHand.MAIN_HAND, slot);
            }
        }

        return null;
    }

    public static AutoCrystalHand findObsidianHand(LocalPlayer player) {
        if (player == null) {
            return null;
        }

        if (player.getOffhandItem().is(Items.OBSIDIAN)) {
            return new AutoCrystalHand(InteractionHand.OFF_HAND, -1);
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.OBSIDIAN)) {
                return new AutoCrystalHand(InteractionHand.MAIN_HAND, slot);
            }
        }

        return null;
    }
}
