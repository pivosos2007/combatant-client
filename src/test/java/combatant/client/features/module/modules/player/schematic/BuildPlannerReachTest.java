/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import combatant.client.util.aiming.data.Rotation;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block next to a player standing on solid floor: the ray to a sampled point of the floor block's top face
 * lands on that face, and the traced hit passes the anticheat click legality, so the planner and the click-time
 * check agree.
 */
class BuildPlannerReachTest {

    private static final double REACH = 4.5;

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

    private static Vec3 onFace(BlockPos support, Direction face, double u, double v) {
        Vec3 c = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return switch (face.getAxis()) {
            case X -> c.add(0.0, v, u);
            case Y -> c.add(u, 0.0, v);
            case Z -> c.add(u, v, 0.0);
        };
    }

    @Test
    void floorBlockNextToAStandingPlayerPassesTraceAndLegalClick() {
        FakeLevel level = new FakeLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = 5; x <= 15; x++) {
            for (int z = 5; z <= 15; z++) level.blocks.put(new BlockPos(x, 63, z), stone);
        }
        Vec3 eyes = new Vec3(10.5, 64.0 + 1.62, 10.5);
        int accepted = 0;
        int total = 0;
        for (Direction side : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            for (int dist = 1; dist <= 3; dist++) {
                BlockPos target = new BlockPos(10, 64, 10).relative(side, dist);
                BlockPos support = target.below();
                for (double u : new double[]{0.0, -0.3, 0.3}) {
                    for (double v : new double[]{0.0, -0.3, 0.3}) {
                        Vec3 point = onFace(support, Direction.UP, u, v);
                        double d = eyes.distanceTo(point);
                        if (d > 4.0) continue;
                        total++;
                        Rotation rot = Rotation.lookingAt(point, eyes);
                        BlockHitResult hit = BuildPlanner.trace(level, CollisionContext.empty(), eyes, rot, d + 0.5);
                        assertNotNull(hit, "ray misses for " + point);
                        assertEquals(support, hit.getBlockPos(), "ray lands on " + hit.getBlockPos());
                        assertEquals(Direction.UP, hit.getDirection());
                        assertTrue(BuildPlanner.legalClick(eyes, rot, support,
                                level.getBlockState(support).getShape(level, support), Direction.UP, hit.getLocation(), REACH),
                                "illegal click at " + hit.getLocation() + " target " + target);
                        accepted++;
                    }
                }
            }
        }
        assertTrue(total > 0 && accepted == total);
    }
}
