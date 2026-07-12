/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public enum BlockSearchUtil {
    ;

    public static List<BlockSearchEntry> searchBlocksInCuboid(
            BlockGetter world,
            Vec3 center,
            double range,
            BiPredicate<BlockPos, BlockState> filter
    ) {
        return searchBlocksInCuboid(world, center, range, range, filter);
    }

    public static List<BlockSearchEntry> searchBlocksInCuboid(
            BlockGetter world,
            Vec3 center,
            double rangeXZ,
            double rangeY,
            BiPredicate<BlockPos, BlockState> filter
    ) {
        if (world == null || center == null || rangeXZ < 0.0 || rangeY < 0.0) {
            return List.of();
        }

        int minX = Mth.floor(center.x - rangeXZ);
        int minY = Mth.floor(center.y - rangeY);
        int minZ = Mth.floor(center.z - rangeXZ);
        int maxX = Mth.floor(center.x + rangeXZ);
        int maxY = Mth.floor(center.y + rangeY);
        int maxZ = Mth.floor(center.z + rangeXZ);

        List<BlockSearchEntry> results = new ArrayList<>();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockPos pos = mutable.immutable();
                    BlockState state = world.getBlockState(pos);
                    if (filter != null && !filter.test(pos, state)) {
                        continue;
                    }
                    results.add(new BlockSearchEntry(pos, state));
                }
            }
        }

        return results;
    }

    public record BlockSearchEntry(BlockPos pos, BlockState state) {
    }
}
