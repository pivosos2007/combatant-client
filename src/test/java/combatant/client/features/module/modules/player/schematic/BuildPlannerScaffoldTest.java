/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A floating target cell gets a helper chain through schematic-air cells only. */
class BuildPlannerScaffoldTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final class FakeLevel implements BlockGetter {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }

    private static FakeLevel floor() {
        FakeLevel level = new FakeLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = 5; x <= 15; x++) {
            for (int z = 5; z <= 15; z++) level.blocks.put(new BlockPos(x, 63, z), stone);
        }
        return level;
    }

    @Test
    void floatingCellTwoAboveFloorChainsThroughSchematicAirOnly() {
        FakeLevel level = floor();
        BlockPos target = new BlockPos(10, 65, 10);
        // The schematic holds a block directly under the target: the chain must go around it.
        Set<BlockPos> schematicBlocks = Set.of(target, new BlockPos(10, 64, 10));
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        assertFalse(BuildPlanner.hasSupport(level, target));

        int placed = 0;
        while (!BuildPlanner.hasSupport(level, target)) {
            List<BlockPos> roots = BuildPlanner.scaffoldRoots(level, target, pos -> !schematicBlocks.contains(pos), 6);
            assertFalse(roots.isEmpty(), "no helper cell after " + placed + " helpers");
            BlockPos root = roots.getFirst();
            assertTrue(BuildPlanner.hasSupport(level, root), "root " + root + " has nothing to click");
            assertFalse(schematicBlocks.contains(root), "helper inside a schematic block cell " + root);
            level.blocks.put(root, cobble);
            assertTrue(++placed <= 2, "chain longer than expected");
        }
        assertTrue(placed == 2);
    }

    @Test
    void chainLongerThanTheLimitPlansNothing() {
        FakeLevel level = floor();
        BlockPos target = new BlockPos(10, 71, 10);
        assertTrue(BuildPlanner.scaffoldRoots(level, target, pos -> true, 6).isEmpty());
        assertFalse(BuildPlanner.scaffoldRoots(level, target, pos -> true, 7).isEmpty());
    }

    @Test
    void helperNeverGoesIntoBlockedCells() {
        FakeLevel level = floor();
        BlockPos target = new BlockPos(10, 65, 10);
        assertTrue(BuildPlanner.scaffoldRoots(level, target, pos -> false, 6).isEmpty());
    }
}
