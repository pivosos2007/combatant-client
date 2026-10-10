/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.block.interaction;

import combatant.client.util.aiming.data.Rotation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Resolves physically reachable surface points against the real OUTLINE voxel shape.
 * No through-wall or center-of-cube assumption. The caller retains the result until it
 * becomes invalid, so a smoother cannot chase different points every tick.
 */
public final class BlockAimResolver {
    private static final double[] SAMPLES = {0.2, 0.5, 0.8};

    private BlockAimResolver() { }

    public record Aim(BlockPos block, Direction face, Vec3 point) { }

    public static boolean visible(LocalPlayer player, Aim aim, double range) {
        if (player == null || aim == null || player.level() == null) return false;
        Vec3 eyes = player.getEyePosition();
        if (!MiningPolicy.withinReach(eyes.distanceToSqr(aim.point()), range)) return false;
        return hitsBlock(player, aim.block(), aim.point(), range) != null;
    }

    public static Aim resolve(LocalPlayer player, ClientLevel level, BlockPos pos,
                              Direction preferredFace, double range, Aim previous) {
        if (player == null || level == null || pos == null || range <= 0.0) return null;
        if (previous != null && previous.block().equals(pos) && visible(player, previous, range)) {
            return previous;
        }
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) return null;

        Vec3 eyes = player.getEyePosition();
        if (eyes.distanceToSqr(Vec3.atCenterOf(pos)) > (range + 0.87) * (range + 0.87)) return null;
        Rotation view = new Rotation(player.getYRot(), player.getXRot(), false);
        double bestScore = Double.MAX_VALUE;
        Aim best = null;
        for (AABB aabb : shape.toAabbs()) {
            // A non-full cube may expose surfaces through its interior or edges.
            for (Direction face : Direction.values()) {
                for (double u : SAMPLES) for (double v : SAMPLES) {
                    Vec3 point = surface(pos, aabb, face, u, v);
                    if (!MiningPolicy.withinReach(eyes.distanceToSqr(point), range)) continue;
                    BlockHitResult hit = hitsBlock(player, pos, point, range);
                    if (hit == null) continue;
                    Rotation desired = Rotation.lookingAt(point, eyes);
                    double score = view.angleTo(desired)
                            + eyes.distanceTo(point) * 0.15
                            + (preferredFace != null && hit.getDirection() != preferredFace ? 3.0 : 0.0);
                    // Prefer the face center over corners at equal angles.
                    score += (Math.abs(u - 0.5) + Math.abs(v - 0.5)) * 0.03;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Aim(pos.immutable(), hit.getDirection(), point);
                    }
                }
            }
        }
        return best;
    }

    private static Vec3 surface(BlockPos pos, AABB aabb, Direction face, double u, double v) {
        MiningSurfaceSamples.Point sample = MiningSurfaceSamples.onFace(
                aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ,
                MiningSurfaceSamples.Face.valueOf(face.name()), u, v);
        return new Vec3(pos.getX() + sample.x(), pos.getY() + sample.y(), pos.getZ() + sample.z());
    }

    private static BlockHitResult hitsBlock(LocalPlayer player, BlockPos pos, Vec3 point, double range) {
        Vec3 eyes = player.getEyePosition();
        Vec3 delta = point.subtract(eyes);
        if (delta.lengthSqr() < 1.0E-9) return null;
        // Extend slightly beyond the point: a surface hit must not be lost to numerical error.
        Vec3 end = eyes.add(delta.normalize().scale(range));
        HitResult hit = player.level().clip(new ClipContext(
                eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(pos)
                && MiningPolicy.withinReach(eyes.distanceToSqr(blockHit.getLocation()), range)) {
            return blockHit;
        }
        return null;
    }
}
