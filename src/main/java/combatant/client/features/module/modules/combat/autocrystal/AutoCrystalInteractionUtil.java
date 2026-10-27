/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import combatant.client.util.block.interaction.FaceVisibility;
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

import java.util.List;

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

    /**
     * Whether a ray from {@code eye} along {@code direction} enters the unit cube at {@code pos} through
     * {@code face}. This is the test GrimAC's RotationPlace runs against the rotation the server last saw.
     */
    public static boolean rayEntersFace(Vec3 eye, Vec3 direction, BlockPos pos, Direction face, double range) {
        if (eye == null || direction == null || pos == null || face == null) {
            return false;
        }
        BlockHitResult hit = AABB.clip(List.of(new AABB(0, 0, 0, 1, 1, 1)), eye, eye.add(direction.scale(range)), pos);
        return hit != null && hit.getBlockPos().equals(pos) && hit.getDirection() == face;
    }

    /** Margin the aim keeps inside an entity box so rotation rounding cannot turn a hit into a miss. */
    public static final double AIM_INSET = 0.1;

    /** Whether a ray from {@code eye} along {@code direction} reaches {@code box} within {@code range}. */
    public static boolean rayHitsBox(Vec3 eye, Vec3 direction, AABB box, double range) {
        if (eye == null || direction == null || box == null) {
            return false;
        }
        return box.contains(eye) || box.clip(eye, eye.add(direction.scale(range))).isPresent();
    }

    public static Vec3 crystalVec(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }

    /** Distance from {@code point} to the nearest point of {@code box}, squared; zero when the point is inside. */
    public static double distanceSqToBox(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    public static BlockHitResult getCrystalInteractResult(
            Level level,
            LocalPlayer player,
            BlockPos pos,
            AutoCrystalPlacementMode mode,
            float placeRange,
            float wallRange
    ) {
        if (mode == AutoCrystalPlacementMode.STRICT) {
            return getStrictInteract(level, player, pos, placeRange);
        }
        return getDefaultInteract(level, player, pos, placeRange, wallRange);
    }

    public static BlockHitResult getStrictInteract(Level level, LocalPlayer player, BlockPos pos, float placeRange) {
        if (player == null || level == null || pos == null) {
            return null;
        }

        Vec3 eyes = player.getEyePosition();
        Vec3 sentEyes = FaceVisibility.serverEyePosition(player);
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
            if (eyes.distanceToSqr(sample) > placeRangeSq || sentEyes.distanceToSqr(sample) > placeRangeSq) {
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
            if (hit.getDirection() != faces[i] || !FaceVisibility.isVisible(player, pos, faces[i])) {
                continue;
            }

            return new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false);
        }

        return null;
    }

    /**
     * Clicks the top face when it is visible. Otherwise the click goes on the face the eyes can really see, because
     * GrimAC's PositionPlace rejects a click on a face the eyes are behind; visible means from the eyes now and from
     * the position the server last received, which judges a click sent before the movement packet. The cursor is the
     * face centre, so it lies on the clicked face as FabricatedPlace and Vulcan BadPackets 5 require. Both eyes
     * must also be within place range (FarPlace).
     */
    public static BlockHitResult getDefaultInteract(
            Level level,
            LocalPlayer player,
            BlockPos pos,
            float placeRange,
            float wallRange
    ) {
        if (player == null || level == null || pos == null || !level.isInWorldBounds(pos.above())) {
            return null;
        }

        Direction face = FaceVisibility.isVisible(player, pos, Direction.UP)
                ? Direction.UP
                : FaceVisibility.bestFace(player, pos);
        if (face == null) {
            return null;
        }
        Vec3 faceVec = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));

        Vec3 eyes = player.getEyePosition();
        double distanceSq = Math.max(eyes.distanceToSqr(faceVec),
                FaceVisibility.serverEyePosition(player).distanceToSqr(faceVec));
        double placeRangeSq = placeRange * placeRange;
        if (distanceSq > placeRangeSq) {
            return null;
        }

        BlockHitResult wallCheck = level.clip(new ClipContext(
                eyes,
                faceVec,
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

        return new BlockHitResult(faceVec, face, pos, false);
    }

    public static BlockHitResult getObsidianInteractResult(Level level, LocalPlayer player, BlockPos pos, float placeRange) {
        if (player == null || level == null || pos == null) {
            return null;
        }

        Vec3 eyes = player.getEyePosition();
        Vec3 sentEyes = FaceVisibility.serverEyePosition(player);
        double rangeSq = placeRange * placeRange;
        for (Direction direction : Direction.values()) {
            BlockPos support = pos.relative(direction);
            if (!level.isInWorldBounds(support)) continue;
            var state = level.getBlockState(support);
            if (state.isAir() || state.getCollisionShape(level, support).isEmpty()) continue;

            Direction face = direction.getOpposite();
            Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
            Vec3 hitVec = Vec3.atCenterOf(support).add(normal.scale(0.5));
            if (eyes.distanceToSqr(hitVec) > rangeSq || sentEyes.distanceToSqr(hitVec) > rangeSq) continue;
            // GrimAC PositionPlace: the eyes, now and as the server last saw them, must be in front of the clicked face.
            if (!FaceVisibility.isVisible(player, support, face)) continue;

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
