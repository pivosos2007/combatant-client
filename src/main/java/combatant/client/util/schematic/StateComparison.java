/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which differences between a schematic state and the world count as a wrong block.
 *
 * <p>A schematic is a snapshot of a running machine. Power levels, lit lamps, extended pistons, triggered
 * dispensers and the auto-computed connections of dust and string are what the circuit does at that instant,
 * not something a builder places; once the parts are in, the world derives them again. Only the state the
 * player decides with a click (facing, half, delay, mode, note, hinge, attach face, a lever's position, a hand
 * opened door) has to match.</p>
 */
public final class StateComparison {

    /** Derived on every block that has them. */
    private static final Property<?>[] ALWAYS_IGNORED = {
            BlockStateProperties.POWER,
            BlockStateProperties.LIT,
            BlockStateProperties.TRIGGERED,
            BlockStateProperties.EXTENDED,
            BlockStateProperties.ENABLED,
            BlockStateProperties.LOCKED,
            BlockStateProperties.SIGNAL_FIRE,
            BlockStateProperties.SCULK_SENSOR_PHASE,
            BlockStateProperties.CRAFTING,
            // No builder can pour water into a cell; a block placed in water picks it up by itself.
            BlockStateProperties.WATERLOGGED
    };

    private static final Map<Block, Property<?>[]> IGNORED = new ConcurrentHashMap<>();

    private StateComparison() {
    }

    /** Whether {@code actual} counts as the schematic's {@code expected} state. */
    public static boolean matches(BlockState actual, BlockState expected) {
        if (actual == expected) return true;
        if (actual == null || expected == null || actual.getBlock() != expected.getBlock()) return false;
        for (Property<?> property : expected.getProperties()) {
            if (ignored(expected, property)) continue;
            if (!actual.getValue(property).equals(expected.getValue(property))) return false;
        }
        return true;
    }

    /** Whether {@code property} of {@code expected} is derived by the world rather than chosen by the builder. */
    public static boolean ignored(BlockState expected, Property<?> property) {
        for (Property<?> candidate : ignoredFor(expected.getBlock())) {
            if (candidate == property) return true;
        }
        // A powered door or gate is open because of the circuit; an unpowered one was opened by hand.
        if (property == BlockStateProperties.OPEN && opensByPower(expected.getBlock())
                && expected.hasProperty(BlockStateProperties.POWERED)) {
            return expected.getValue(BlockStateProperties.POWERED);
        }
        return false;
    }

    /**
     * Cells a builder never places: piston heads and moving pistons appear when the piston extends, fluids and
     * bubble columns need a bucket. Any replaceable world state (or the same block) is as good as it gets.
     */
    public static boolean derived(BlockState expected) {
        Block block = expected.getBlock();
        return block instanceof PistonHeadBlock || block instanceof MovingPistonBlock
                || block instanceof LiquidBlock || block instanceof BubbleColumnBlock;
    }

    /** Whether a derived cell's world state is acceptable. */
    public static boolean derivedSatisfied(BlockState actual, BlockState expected) {
        if (actual.getBlock() == expected.getBlock() || actual.canBeReplaced()) return true;
        // An extended piston leaves its head or a moving block in the cell in front of it.
        return expected.getBlock() instanceof PistonHeadBlock
                && (actual.getBlock() instanceof PistonHeadBlock || actual.getBlock() instanceof MovingPistonBlock);
    }

    private static boolean opensByPower(Block block) {
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock;
    }

    private static Property<?>[] ignoredFor(Block block) {
        // Blocks do not override equals or hashCode, so this map is keyed by identity.
        return IGNORED.computeIfAbsent(block, StateComparison::computeIgnored);
    }

    private static Property<?>[] computeIgnored(Block block) {
        List<Property<?>> out = new ArrayList<>(List.of(ALWAYS_IGNORED));
        // A lever's position is the player's choice; everywhere else powered follows the circuit.
        if (!(block instanceof LeverBlock)) out.add(BlockStateProperties.POWERED);
        if (block instanceof RedstoneWireBlock) {
            out.add(RedstoneWireBlock.NORTH);
            out.add(RedstoneWireBlock.EAST);
            out.add(RedstoneWireBlock.SOUTH);
            out.add(RedstoneWireBlock.WEST);
        }
        if (block instanceof TripWireBlock) {
            out.add(TripWireBlock.ATTACHED);
            out.add(TripWireBlock.DISARMED);
            out.add(TripWireBlock.NORTH);
            out.add(TripWireBlock.EAST);
            out.add(TripWireBlock.SOUTH);
            out.add(TripWireBlock.WEST);
        }
        if (block instanceof TripWireHookBlock) out.add(TripWireHookBlock.ATTACHED);
        // The instrument follows the block underneath (or a head on top).
        if (block instanceof NoteBlock) out.add(NoteBlock.INSTRUMENT);
        return out.toArray(new Property<?>[0]);
    }
}
