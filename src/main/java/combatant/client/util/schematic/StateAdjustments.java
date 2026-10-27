/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * States no placement produces but a few clicks on the placed block do: a repeater's delay, a comparator's
 * mode, a note block's pitch, a lever's position, a door, trapdoor or gate opened by hand and an inverted
 * daylight sensor. Each click advances the one property by a fixed step, the way vanilla's use action does.
 */
public final class StateAdjustments {

    /** The block's state differs in a way clicking cannot fix. */
    public static final int IMPOSSIBLE = -1;
    /** Most clicks one adjustment can need: a note block a full octave and a half away. */
    public static final int MAX_CLICKS = 24;

    private StateAdjustments() {
    }

    /**
     * Clicks that turn {@code actual} into {@code expected}: 0 when they already match (see
     * {@link StateComparison}), {@link #IMPOSSIBLE} when they differ in anything a click does not change.
     */
    public static int clicksNeeded(BlockState actual, BlockState expected) {
        if (actual.getBlock() != expected.getBlock()) return IMPOSSIBLE;
        Property<?> adjustable = adjustableProperty(expected);
        int clicks = 0;
        for (Property<?> property : expected.getProperties()) {
            if (StateComparison.ignored(expected, property)) continue;
            if (actual.getValue(property).equals(expected.getValue(property))) continue;
            if (property != adjustable) return IMPOSSIBLE;
            clicks = clicksFor(actual, expected);
        }
        return clicks;
    }

    /** The property this block's use action steps, or {@code null} when clicking does not change it. */
    public static Property<?> adjustableProperty(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof RepeaterBlock) return RepeaterBlock.DELAY;
        if (block instanceof ComparatorBlock) return ComparatorBlock.MODE;
        if (block instanceof NoteBlock) return NoteBlock.NOTE;
        if (block instanceof LeverBlock) return LeverBlock.POWERED;
        if (block instanceof DaylightDetectorBlock) return DaylightDetectorBlock.INVERTED;
        if (block instanceof DoorBlock door) return door.type().canOpenByHand() ? DoorBlock.OPEN : null;
        if (block instanceof TrapDoorBlock) return state.is(Blocks.IRON_TRAPDOOR) ? null : TrapDoorBlock.OPEN;
        if (block instanceof FenceGateBlock) return FenceGateBlock.OPEN;
        return null;
    }

    /** Switching a lever on powers the build: that click waits until everything else is in. */
    public static boolean activates(BlockState expected) {
        return expected.getBlock() instanceof LeverBlock && expected.getValue(LeverBlock.POWERED);
    }

    private static int clicksFor(BlockState actual, BlockState expected) {
        Block block = expected.getBlock();
        if (block instanceof RepeaterBlock) {
            return cyclic(actual.getValue(RepeaterBlock.DELAY) - 1, expected.getValue(RepeaterBlock.DELAY) - 1, 4);
        }
        if (block instanceof NoteBlock) {
            return cyclic(actual.getValue(NoteBlock.NOTE), expected.getValue(NoteBlock.NOTE), 25);
        }
        // Mode, lever, open and inverted are two-valued: one click flips them.
        return 1;
    }

    /** Steps forward from {@code from} to {@code to} on a ring of {@code size} values. */
    static int cyclic(int from, int to, int size) {
        return Math.floorMod(to - from, size);
    }

    /** The door's lower half takes the click; the upper half follows it. */
    public static boolean clickedElsewhere(BlockState expected) {
        return expected.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && expected.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                == DoubleBlockHalf.UPPER;
    }
}
