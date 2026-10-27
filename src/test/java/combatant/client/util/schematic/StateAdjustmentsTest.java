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
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateAdjustmentsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BlockState repeater(int delay) {
        return Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.DELAY, delay);
    }

    @Test
    void repeaterDelayStepsForwardAndWraps() {
        // {placed delay, wanted delay, clicks}: each click adds one tick, four wraps back to one.
        int[][] table = {
                {1, 1, 0}, {1, 2, 1}, {1, 3, 2}, {1, 4, 3},
                {2, 1, 3}, {3, 2, 3}, {4, 1, 1}, {4, 4, 0}, {2, 4, 2}
        };
        for (int[] row : table) {
            assertEquals(row[2], StateAdjustments.clicksNeeded(repeater(row[0]), repeater(row[1])),
                    "delay " + row[0] + " -> " + row[1]);
        }
        // A powered or locked repeater still only needs its delay set.
        assertEquals(1, StateAdjustments.clicksNeeded(repeater(1).setValue(RepeaterBlock.POWERED, true), repeater(2)));
        // Facing is decided by the placement; no click fixes it.
        assertEquals(StateAdjustments.IMPOSSIBLE,
                StateAdjustments.clicksNeeded(repeater(2).setValue(RepeaterBlock.FACING, Direction.EAST), repeater(2)));
    }

    @Test
    void twoValuedStatesTakeOneClick() {
        BlockState compare = Blocks.COMPARATOR.defaultBlockState();
        assertEquals(1, StateAdjustments.clicksNeeded(compare, compare.setValue(ComparatorBlock.MODE, ComparatorMode.SUBTRACT)));
        BlockState lever = Blocks.LEVER.defaultBlockState();
        assertEquals(1, StateAdjustments.clicksNeeded(lever, lever.setValue(LeverBlock.POWERED, true)));
        assertTrue(StateAdjustments.activates(lever.setValue(LeverBlock.POWERED, true)));
        assertFalse(StateAdjustments.activates(lever));
        BlockState sensor = Blocks.DAYLIGHT_DETECTOR.defaultBlockState();
        assertEquals(1, StateAdjustments.clicksNeeded(sensor, sensor.setValue(DaylightDetectorBlock.INVERTED, true)));
        BlockState trapdoor = Blocks.SPRUCE_TRAPDOOR.defaultBlockState();
        assertEquals(1, StateAdjustments.clicksNeeded(trapdoor, trapdoor.setValue(TrapDoorBlock.OPEN, true)));
    }

    @Test
    void notePitchCountsUpToTwentyFour() {
        BlockState note = Blocks.NOTE_BLOCK.defaultBlockState();
        assertEquals(24, StateAdjustments.clicksNeeded(note.setValue(NoteBlock.NOTE, 0), note.setValue(NoteBlock.NOTE, 24)));
        assertEquals(1, StateAdjustments.clicksNeeded(note.setValue(NoteBlock.NOTE, 24), note.setValue(NoteBlock.NOTE, 0)));
        assertEquals(5, StateAdjustments.clicksNeeded(note.setValue(NoteBlock.NOTE, 7), note.setValue(NoteBlock.NOTE, 12)));
    }

    @Test
    void ironDoorsAndTrapdoorsCannotBeOpenedByHand() {
        BlockState iron = Blocks.IRON_DOOR.defaultBlockState();
        assertEquals(StateAdjustments.IMPOSSIBLE, StateAdjustments.clicksNeeded(iron, iron.setValue(DoorBlock.OPEN, true)));
        BlockState ironTrap = Blocks.IRON_TRAPDOOR.defaultBlockState();
        assertEquals(StateAdjustments.IMPOSSIBLE,
                StateAdjustments.clicksNeeded(ironTrap, ironTrap.setValue(TrapDoorBlock.OPEN, true)));
        // Opened by power in the schematic: nothing to click, power opens it.
        assertEquals(0, StateAdjustments.clicksNeeded(iron, iron.setValue(DoorBlock.OPEN, true).setValue(DoorBlock.POWERED, true)));
        BlockState oak = Blocks.OAK_DOOR.defaultBlockState();
        assertEquals(1, StateAdjustments.clicksNeeded(oak, oak.setValue(DoorBlock.OPEN, true)));
    }
}
