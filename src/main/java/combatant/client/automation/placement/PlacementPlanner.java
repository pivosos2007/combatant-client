/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.placement;

import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.block.interaction.FaceVisibility;
import combatant.client.util.block.scaffold.ScaffoldPlacementTarget;
import combatant.client.util.block.scaffold.ScaffoldTargetFinder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chooses which queued cells can be placed now and on which support face. Reads the world but
 * keeps no state: the caller owns the inaccessible set, which this class only adds to.
 */
public final class PlacementPlanner {

    private static final List<BlockPos> NO_OFFSETS = List.of(BlockPos.ZERO);

    private PlacementPlanner() {
    }

    /**
     * Plans up to {@code max} targets in queue order.
     *
     * @param inaccessible cells to skip; cells found blocked or out of reach are added to it
     */
    public static List<PlacementTarget> plan(LocalPlayer player,
                                             Iterable<BlockPos> queue,
                                             Set<BlockPos> inaccessible,
                                             PlacementSlot slot,
                                             PlacementConstraints constraints,
                                             int max) {
        List<PlacementTarget> targets = new ArrayList<>(Math.max(1, max));
        if (player == null || slot == null || slot.stack().isEmpty()) return targets;
        for (BlockPos pos : queue) {
            if (targets.size() >= max) break;
            PlacementTarget target = planOne(player, pos, slot, constraints, inaccessible);
            if (target != null) targets.add(target);
        }
        return targets;
    }

    /** The target for one cell, or null when it is blocked, unreachable or has no support. */
    public static PlacementTarget planOne(LocalPlayer player,
                                          BlockPos pos,
                                          PlacementSlot slot,
                                          PlacementConstraints constraints,
                                          Set<BlockPos> inaccessible) {
        if (inaccessible != null && inaccessible.contains(pos)) return null;
        if (isBlocked(player, pos, constraints.ignoreEntityCollision())) {
            if (inaccessible != null) inaccessible.add(pos);
            return null;
        }
        ScaffoldPlacementTarget support = findVisibleSupport(player, pos, slot.stack());
        if (support == null) return null;
        if (!canReach(player, support.getInteractedBlockPos(), support.getRotation(), constraints)) {
            if (inaccessible != null) inaccessible.add(pos);
            return null;
        }
        return new PlacementTarget(pos.immutable(), support, slot);
    }

    /**
     * A fresh, reachable support for a cell that is still free; null when none exists now.
     * Unlike {@link #planOne} this never judges the cell itself.
     */
    public static ScaffoldPlacementTarget findSupport(LocalPlayer player,
                                                      BlockPos pos,
                                                      ItemStack stack,
                                                      PlacementConstraints constraints) {
        ScaffoldPlacementTarget support = findVisibleSupport(player, pos, stack);
        if (support == null) return null;
        return canReach(player, support.getInteractedBlockPos(), support.getRotation(), constraints) ? support : null;
    }

    /**
     * The best support whose clicked face the eyes see now and from where the server last saw them. No vanilla
     * ray enters a face from behind (GrimAC PositionPlace), so wall range never widens the faces on offer.
     */
    private static ScaffoldPlacementTarget findVisibleSupport(LocalPlayer player, BlockPos pos, ItemStack stack) {
        return ScaffoldTargetFinder.findTarget(player, pos, stack, NO_OFFSETS, null, false, null, null,
                (support, face) -> FaceVisibility.isVisible(player, support, face));
    }

    /** Occupied by a block that does not yield, or (unless ignored) by a pickable entity. */
    public static boolean isBlocked(LocalPlayer player, BlockPos pos, boolean ignoreEntityCollision) {
        if (!player.level().getBlockState(pos).canBeReplaced()) return true;
        if (ignoreEntityCollision) return false;
        List<Entity> entities = player.level().getEntities(
                player,
                new AABB(pos),
                entity -> entity != null && !entity.isRemoved() && !entity.isSpectator() && entity.isPickable());
        return !entities.isEmpty();
    }

    /**
     * Within wall range, or (NORMAL) the given rotation traces onto the support within range, or
     * (NO_ROTATION) within plain range.
     */
    public static boolean canReach(LocalPlayer player, BlockPos interactedPos, Rotation rotation,
                                   PlacementConstraints constraints) {
        double wallRange = constraints.wallRange();
        Vec3 center = Vec3.atCenterOf(interactedPos);
        double distanceSq = center.distanceToSqr(player.getEyePosition());
        if (distanceSq <= wallRange * wallRange) return true;

        if (constraints.rotate() != PlacementRotationMode.NORMAL) {
            double range = constraints.range();
            return distanceSq <= range * range;
        }

        BlockHitResult hit = BlockInteractionPipeline.trace(player, rotation, constraints.range());
        return hit != null && interactedPos.equals(hit.getBlockPos());
    }

    /** Whether clicking this block opens or toggles it instead of placing against it. */
    public static boolean needsSneak(LocalPlayer player, ScaffoldPlacementTarget support) {
        return isInteractable(player.level().getBlockState(support.getInteractedBlockPos()));
    }

    public static boolean isInteractable(BlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        return block instanceof BedBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof EnderChestBlock
                || block instanceof CraftingTableBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof BrewingStandBlock
                || block instanceof EnchantingTableBlock
                || block instanceof AnvilBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof SmithingTableBlock
                || block instanceof StonecutterBlock
                || block instanceof GrindstoneBlock
                || block instanceof BeaconBlock
                || block instanceof NoteBlock;
    }
}
