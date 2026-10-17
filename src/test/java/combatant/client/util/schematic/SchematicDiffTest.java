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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicDiffTest {

    private static final BlockPos ORIGIN = new BlockPos(10, 64, -5);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** 2x3x1: layer 0 two stone, layer 1 one stone (x=0), layer 2 two stone. Five cells in index order. */
    private static Schematic tower() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        short[] blocks = {1, 1, 1, 0, 1, 1};
        return new Schematic("t", "t", "", "", 2, 3, 1, new BlockState[]{air, stone}, new String[]{"air", "stone"},
                blocks, 5, 1, List.of());
    }

    /** A world of explicit states (air elsewhere) that records every position the diff reads. */
    private static final class FakeWorld implements SchematicDiff.WorldView {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Set<BlockPos> unloaded = new HashSet<>();
        final List<BlockPos> reads = new ArrayList<>();

        FakeWorld put(int x, int y, int z, BlockState state) {
            states.put(ORIGIN.offset(x, y, z), state);
            return this;
        }

        @Override
        public BlockState stateAt(BlockPos pos) {
            BlockPos key = pos.immutable();
            reads.add(key);
            if (unloaded.contains(key)) return null;
            return states.getOrDefault(key, Blocks.AIR.defaultBlockState());
        }
    }

    @Test
    void layoutIsSharedAcrossPlacements() {
        Schematic schematic = tower();
        SchematicDiff.Layout layout = SchematicDiff.Layout.of(schematic);
        SchematicDiff a = new SchematicDiff(layout, SchematicPlacement.at(ORIGIN));
        SchematicDiff b = new SchematicDiff(layout, SchematicPlacement.at(ORIGIN).rotatedClockwise());
        SchematicDiff fresh = new SchematicDiff(schematic, SchematicPlacement.at(ORIGIN));
        assertSame(a.order(), b.order());
        assertEquals(5, a.cellCount());
        for (int cell = 0; cell < 5; cell++) assertEquals(fresh.schematicIndex(cell), b.schematicIndex(cell));
        assertEquals(0, a.layerStart(0));
        assertEquals(2, a.layerStart(1));
        assertEquals(3, a.layerStart(2));
        assertEquals(5, a.layerEnd(2));
        assertEquals(fresh.openTotal(), b.openTotal());
    }

    @Test
    void layerIsCompleteOnceEveryCellIsCorrectOrWrong() {
        SchematicDiff diff = new SchematicDiff(tower(), SchematicPlacement.at(ORIGIN));
        FakeWorld world = new FakeWorld();
        world.put(0, 0, 0, Blocks.STONE.defaultBlockState());
        assertFalse(diff.layerComplete(0), "unchecked cells keep a layer open");

        diff.tick(world, 0, 5);
        assertEquals(1, diff.correct(0));
        assertEquals(1, diff.missing(0));
        assertFalse(diff.layerComplete(0), "a missing cell keeps the layer open");

        // A wrong block is finished as far as the layer goes: the builder cannot fix it by placing.
        world.put(1, 0, 0, Blocks.DIRT.defaultBlockState());
        diff.tick(world, 0, 5);
        assertEquals(1, diff.wrong(0));
        assertTrue(diff.layerComplete(0));

        // An unloaded chunk turns a checked cell back into unknown, which reopens the layer.
        world.unloaded.add(ORIGIN.offset(1, 0, 0));
        diff.tick(world, 0, 5);
        assertEquals(0, diff.wrong(0));
        assertFalse(diff.layerComplete(0));
    }

    @Test
    void focusCursorWrapsInsideTheWindowOnly() {
        SchematicDiff diff = new SchematicDiff(tower(), SchematicPlacement.at(ORIGIN));
        FakeWorld world = new FakeWorld();
        diff.setFocus(1, 2);
        // Window = cells 2..4 (layer 1 has one cell, layer 2 two): budget 2 then 2 wraps back to cell 2.
        diff.tick(world, 2, 0);
        diff.tick(world, 2, 0);
        List<BlockPos> expected = List.of(ORIGIN.offset(0, 1, 0), ORIGIN.offset(0, 2, 0), ORIGIN.offset(1, 2, 0),
                ORIGIN.offset(0, 1, 0));
        assertEquals(expected, world.reads);
        assertEquals(SchematicDiff.UNKNOWN, diff.status(0));
        assertEquals(SchematicDiff.UNKNOWN, diff.status(1));

        // A budget larger than the window checks each cell once, not the window twice.
        world.reads.clear();
        diff.setFocus(1, 1);
        diff.tick(world, 10, 0);
        assertEquals(List.of(ORIGIN.offset(0, 1, 0)), world.reads);

        // The global cursor wraps around the whole build.
        world.reads.clear();
        diff.setFocus(-1, -1);
        diff.tick(world, 0, 4);
        diff.tick(world, 0, 3);
        assertEquals(7, world.reads.size());
        assertEquals(ORIGIN, world.reads.get(5));
    }

    @Test
    void classifySeparatesMissingWrongAndCorrect() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(stone, stone));
        assertEquals(SchematicDiff.MISSING, SchematicDiff.classify(Blocks.AIR.defaultBlockState(), stone));
        assertEquals(SchematicDiff.MISSING, SchematicDiff.classify(Blocks.SHORT_GRASS.defaultBlockState(), stone));
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(Blocks.DIRT.defaultBlockState(), stone));
    }

    @Test
    void classifyIgnoresDerivedPropertiesButNotChosenOnes() {
        BlockState repeater = Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.EAST)
                .setValue(RepeaterBlock.DELAY, 3);
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(repeater.setValue(RepeaterBlock.POWERED, true), repeater));
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(repeater.setValue(RepeaterBlock.DELAY, 1), repeater));
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(repeater.setValue(RepeaterBlock.FACING, Direction.WEST), repeater));

        // A lever's position is the player's choice.
        BlockState lever = Blocks.LEVER.defaultBlockState();
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(lever.setValue(LeverBlock.POWERED, true), lever));

        BlockState wire = Blocks.REDSTONE_WIRE.defaultBlockState();
        BlockState lit = wire.setValue(RedstoneWireBlock.POWER, 15).setValue(RedstoneWireBlock.NORTH, RedstoneSide.SIDE);
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(lit, wire));

        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(stair.setValue(StairBlock.WATERLOGGED, true), stair));
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(stair.setValue(StairBlock.FACING, Direction.SOUTH), stair));

        // A hand-opened door must be open; a powered open door is open because of the circuit.
        BlockState handOpened = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, true);
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(handOpened.setValue(DoorBlock.OPEN, false), handOpened));
        BlockState powerOpened = handOpened.setValue(DoorBlock.POWERED, true);
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(
                powerOpened.setValue(DoorBlock.OPEN, false).setValue(DoorBlock.POWERED, false), powerOpened));
    }

    @Test
    void derivedCellsAcceptAnyReplaceableState() {
        BlockState water = Blocks.WATER.defaultBlockState();
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(Blocks.AIR.defaultBlockState(), water));
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(Blocks.STONE.defaultBlockState(), water));
        BlockState head = Blocks.PISTON_HEAD.defaultBlockState();
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(Blocks.MOVING_PISTON.defaultBlockState(), head));
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(Blocks.DIRT.defaultBlockState(), head));
    }
}
