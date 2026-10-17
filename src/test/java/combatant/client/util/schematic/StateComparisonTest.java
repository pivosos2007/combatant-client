/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateComparisonTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void powerAndLightAreDerivedButDelayAndModeAreNot() {
        BlockState repeater = Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.DELAY, 2);
        assertTrue(StateComparison.matches(repeater.setValue(RepeaterBlock.POWERED, true)
                .setValue(RepeaterBlock.LOCKED, true), repeater));
        assertFalse(StateComparison.matches(repeater.setValue(RepeaterBlock.DELAY, 3), repeater));
        assertFalse(StateComparison.matches(repeater.setValue(RepeaterBlock.FACING, Direction.EAST), repeater));

        BlockState comparator = Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.MODE, ComparatorMode.SUBTRACT);
        assertTrue(StateComparison.matches(comparator.setValue(ComparatorBlock.POWERED, true), comparator));
        assertFalse(StateComparison.matches(comparator.setValue(ComparatorBlock.MODE, ComparatorMode.COMPARE), comparator));

        BlockState torch = Blocks.REDSTONE_TORCH.defaultBlockState();
        assertTrue(StateComparison.matches(torch.setValue(RedstoneTorchBlock.LIT, false), torch));
        assertTrue(StateComparison.matches(Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true),
                Blocks.REDSTONE_LAMP.defaultBlockState()));
    }

    @Test
    void pistonsObserversDispensersAndHoppersKeepTheirFacing() {
        BlockState piston = Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP);
        assertTrue(StateComparison.matches(piston.setValue(PistonBaseBlock.EXTENDED, true), piston));
        assertFalse(StateComparison.matches(piston.setValue(PistonBaseBlock.FACING, Direction.DOWN), piston));

        BlockState observer = Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST);
        assertTrue(StateComparison.matches(observer.setValue(ObserverBlock.POWERED, true), observer));
        assertFalse(StateComparison.matches(observer.setValue(ObserverBlock.FACING, Direction.EAST), observer));

        BlockState dropper = Blocks.DROPPER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        assertTrue(StateComparison.matches(dropper.setValue(BlockStateProperties.TRIGGERED, true), dropper));

        BlockState hopper = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH);
        assertTrue(StateComparison.matches(hopper.setValue(HopperBlock.ENABLED, false), hopper));
        assertFalse(StateComparison.matches(hopper.setValue(HopperBlock.FACING, Direction.DOWN), hopper));
    }

    @Test
    void dustAndStringConnectionsAreAutoComputed() {
        BlockState dust = Blocks.REDSTONE_WIRE.defaultBlockState();
        BlockState wired = dust.setValue(RedstoneWireBlock.POWER, 15).setValue(RedstoneWireBlock.NORTH, RedstoneSide.UP)
                .setValue(RedstoneWireBlock.EAST, RedstoneSide.NONE);
        assertTrue(StateComparison.matches(wired, dust));

        BlockState string = Blocks.TRIPWIRE.defaultBlockState();
        assertTrue(StateComparison.matches(string.setValue(TripWireBlock.ATTACHED, true)
                .setValue(TripWireBlock.DISARMED, true).setValue(TripWireBlock.NORTH, true), string));
    }

    @Test
    void leverPositionNoteAndHandOpenedDoorsMustMatch() {
        BlockState lever = Blocks.LEVER.defaultBlockState();
        assertFalse(StateComparison.matches(lever.setValue(LeverBlock.POWERED, true), lever));

        BlockState note = Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE, 12);
        assertTrue(StateComparison.matches(note.setValue(NoteBlock.INSTRUMENT, NoteBlockInstrument.BELL)
                .setValue(NoteBlock.POWERED, true), note));
        assertFalse(StateComparison.matches(note.setValue(NoteBlock.NOTE, 11), note));

        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        BlockState openByHand = door.setValue(DoorBlock.OPEN, true);
        assertFalse(StateComparison.matches(door, openByHand));
        BlockState openByPower = openByHand.setValue(DoorBlock.POWERED, true);
        assertTrue(StateComparison.matches(door, openByPower));
        assertFalse(StateComparison.matches(door.setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT), door));

        BlockState trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState();
        assertTrue(StateComparison.matches(trapdoor.setValue(TrapDoorBlock.WATERLOGGED, true), trapdoor));
    }

    @Test
    void derivedCellsAcceptEmptyOrTheirOwnBlock() {
        BlockState head = Blocks.PISTON_HEAD.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(air, head));
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(Blocks.MOVING_PISTON.defaultBlockState(), head));
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(Blocks.STONE.defaultBlockState(), head));
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(air, Blocks.WATER.defaultBlockState()));

        BlockState repeater = Blocks.REPEATER.defaultBlockState();
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(repeater.setValue(RepeaterBlock.POWERED, true), repeater));
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(repeater.setValue(RepeaterBlock.DELAY, 4), repeater));
        assertEquals(SchematicDiff.MISSING, SchematicDiff.classify(air, repeater));
    }
}
