/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SchematicPlacementTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void worldToLocalRoundTripsForEveryRotationAndMirror() {
        Random random = new Random(42L);
        BlockPos origin = new BlockPos(-120, 64, 3077);
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                SchematicPlacement placement = new SchematicPlacement(origin, rotation, mirror);
                for (int i = 0; i < 200; i++) {
                    int x = random.nextInt(64), y = random.nextInt(32), z = random.nextInt(64);
                    BlockPos world = placement.toWorld(x, y, z);
                    String label = rotation + "/" + mirror;
                    assertEquals(x, placement.localX(world.getX(), world.getZ()), label);
                    assertEquals(y, placement.localY(world.getY()), label);
                    assertEquals(z, placement.localZ(world.getX(), world.getZ()), label);
                }
            }
        }
    }

    @Test
    void matchesVanillaStructureTemplateTransform() {
        BlockPos origin = new BlockPos(10, 70, -5);
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                SchematicPlacement placement = new SchematicPlacement(origin, rotation, mirror);
                for (int x = 0; x < 4; x++) {
                    for (int z = 0; z < 3; z++) {
                        BlockPos vanilla = StructureTemplate.transform(new BlockPos(x, 2, z), mirror, rotation, BlockPos.ZERO)
                                .offset(origin);
                        assertEquals(vanilla, placement.toWorld(x, 2, z), rotation + "/" + mirror);
                    }
                }
            }
        }
    }

    @Test
    void transformsBlockStatesWithTheSameRotation() {
        BlockState north = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        SchematicPlacement placement = new SchematicPlacement(BlockPos.ZERO, Rotation.CLOCKWISE_90, Mirror.NONE);
        assertEquals(Direction.EAST, placement.transform(north).getValue(StairBlock.FACING));

        SchematicPlacement mirrored = new SchematicPlacement(BlockPos.ZERO, Rotation.NONE, Mirror.LEFT_RIGHT);
        assertEquals(Direction.SOUTH, mirrored.transform(north).getValue(StairBlock.FACING));
    }

    @Test
    void worldBoundsCoverTheRotatedBox() {
        SchematicPlacement placement = new SchematicPlacement(new BlockPos(0, 10, 0), Rotation.CLOCKWISE_90, Mirror.NONE);
        // A 5 (x) by 3 (z) footprint rotated clockwise spans x -2..0 and z 0..4.
        assertArrayEquals(new int[]{-2, 10, 0, 0, 13, 4}, placement.worldBounds(5, 4, 3));
    }
}
