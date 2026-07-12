/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.bed;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.block.BlockSearchUtil;

import java.util.*;
import java.util.function.BiPredicate;

public enum BedBlockUtil {
    ;

    public static boolean isBed(BlockState state) {
        return state != null && state.getBlock() instanceof BedBlock;
    }

    public static Direction anotherBedPartDirection(BlockState state) {
        if (!isBed(state)) {
            return null;
        }
        return BedBlock.getConnectedDirection(state);
    }

    public static BlockPos canonicalBedPos(BlockPos pos, BlockState state) {
        if (pos == null) {
            return null;
        }

        Direction anotherPartDirection = anotherBedPartDirection(state);
        if (anotherPartDirection == null) {
            return pos.immutable();
        }

        BlockPos otherPart = pos.relative(anotherPartDirection);
        return pos.asLong() <= otherPart.asLong() ? pos.immutable() : otherPart.immutable();
    }

    public static List<LayeredBlockPos> searchBedLayer(BlockPos pos, BlockState state, int layers) {
        if (pos == null || !isBed(state) || layers <= 0) {
            return List.of();
        }

        Direction anotherPartDirection = anotherBedPartDirection(state);
        if (anotherPartDirection == null) {
            return List.of();
        }

        Direction bedDirection = anotherPartDirection.getOpposite();
        Direction left;
        Direction right;
        if (bedDirection.getAxis() == Direction.Axis.X) {
            left = Direction.SOUTH;
            right = Direction.NORTH;
        } else {
            left = Direction.WEST;
            right = Direction.EAST;
        }

        List<LayeredBlockPos> results = new ArrayList<>();
        results.addAll(searchLayer(pos, layers, bedDirection, Direction.UP, left, right));
        results.addAll(searchLayer(pos.relative(anotherPartDirection), layers, anotherPartDirection, Direction.UP, left, right));
        return results;
    }

    public static List<LayeredBlockPos> searchLayer(BlockPos origin, int layers, Direction... directions) {
        if (origin == null || layers <= 0 || directions == null || directions.length == 0) {
            return List.of();
        }

        List<LayeredBlockPos> results = new ArrayList<>();
        ArrayDeque<LayeredBlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        BlockPos start = origin.immutable();
        queue.add(new LayeredBlockPos(0, start));
        visited.add(start);

        while (!queue.isEmpty()) {
            LayeredBlockPos next = queue.removeFirst();
            if (next.layer() > 0) {
                results.add(next);
            }

            if (next.layer() >= layers) {
                continue;
            }

            for (Direction direction : directions) {
                BlockPos candidate = next.pos().relative(direction);
                if (!visited.add(candidate)) {
                    continue;
                }
                queue.addLast(new LayeredBlockPos(next.layer() + 1, candidate.immutable()));
            }
        }

        return results;
    }

    public static BedSearchEntry findClosestBed(
            BlockGetter world,
            Vec3 center,
            double range,
            BiPredicate<BlockPos, BlockState> filter
    ) {
        if (world == null || center == null || range < 0.0) {
            return null;
        }

        return BlockSearchUtil.searchBlocksInCuboid(world, center, range, (pos, state) ->
                        isBed(state) && (filter == null || filter.test(pos, state)))
                .stream()
                .min(Comparator.comparingDouble(entry -> Vec3.atCenterOf(entry.pos()).distanceToSqr(center)))
                .map(entry -> new BedSearchEntry(entry.pos(), entry.state()))
                .orElse(null);
    }

    public record LayeredBlockPos(int layer, BlockPos pos) {
    }

    public record BedSearchEntry(BlockPos pos, BlockState state) {
    }
}
