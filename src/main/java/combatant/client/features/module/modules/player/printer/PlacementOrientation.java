/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.printer;

import combatant.client.util.schematic.RedstoneRules;
import combatant.client.util.schematic.StateComparison;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What one block item click can and cannot produce, derived from the wanted state alone: whether the cell is
 * worth placing at all, which faces of the support block may be clicked, where on the face (top or bottom half),
 * and whether the player's look decides the result. The click planner uses this to prune candidates and the
 * module to report blocks that placement can never produce; the block's own placement rule (simulated by the
 * planner) stays the final authority.
 */
public final class PlacementOrientation {

    /** Why a cell is left alone. */
    public enum Skip {
        NONE,
        /** Upper half of a door or tall plant: the lower half's placement creates it. */
        UPPER_HALF,
        /** Head of a bed: placing the foot creates it. */
        BED_HEAD,
        /** Candles, pickles, eggs, snow layers, petals beyond the first: needs repeated clicks on one cell. */
        STACKED,
        /** A double slab needs two clicks on the same cell. */
        DOUBLE_SLAB,
        /** Piston heads, fluids and the like appear by themselves. */
        DERIVED,
        /** No item places this block. */
        NO_ITEM
    }

    /** Top or bottom half of a slab, stair or trapdoor. */
    public enum VerticalHalf { BOTTOM, TOP }

    /**
     * @param skip             reason to leave the cell, {@link Skip#NONE} when it can be placed
     * @param half             required vertical half, or null
     * @param clickFace        the only face of the support block that produces the state, or null for any
     * @param clickAxis        the axis the clicked face must lie on, or null for any
     * @param rotationDecides  the player's look (or yaw) influences the result, so the click needs a rotation
     */
    public record Requirement(Skip skip, VerticalHalf half, Direction clickFace, Direction.Axis clickAxis,
                              boolean rotationDecides) {

        public boolean placeable() {
            return skip == Skip.NONE;
        }

        /**
         * Whether clicking {@code face} of the support at a height {@code hitY} (0..1 inside the placed cell's
         * row) can produce the state.
         */
        public boolean allows(Direction face, double hitY) {
            if (clickFace != null && face != clickFace) return false;
            if (clickAxis != null && face.getAxis() != clickAxis) return false;
            if (half == VerticalHalf.BOTTOM) return face != Direction.DOWN && (face == Direction.UP || hitY <= 0.5);
            if (half == VerticalHalf.TOP) return face == Direction.DOWN || face != Direction.UP && hitY > 0.5;
            return true;
        }
    }

    private PlacementOrientation() {
    }

    public static Requirement analyze(BlockState state) {
        Skip skip = skip(state);
        if (skip != Skip.NONE) return new Requirement(skip, null, null, null, false);

        VerticalHalf half = null;
        if (state.getBlock() instanceof SlabBlock) {
            half = state.getValue(SlabBlock.TYPE) == SlabType.TOP ? VerticalHalf.TOP : VerticalHalf.BOTTOM;
        } else if (state.getBlock() instanceof StairBlock || state.getBlock() instanceof TrapDoorBlock) {
            half = state.getValue(BlockStateProperties.HALF)
                    == net.minecraft.world.level.block.state.properties.Half.TOP
                    ? VerticalHalf.TOP : VerticalHalf.BOTTOM;
        }

        Direction clickFace = null;
        Direction.Axis clickAxis = null;
        var block = state.getBlock();
        if (block instanceof HopperBlock) {
            Direction facing = state.getValue(HopperBlock.FACING);
            if (facing == Direction.DOWN) clickAxis = Direction.Axis.Y;
            else clickFace = facing.getOpposite();
        } else if (block instanceof ShulkerBoxBlock || block instanceof AmethystClusterBlock
                || block instanceof EndRodBlock || block instanceof LightningRodBlock) {
            clickFace = state.getValue(BlockStateProperties.FACING);
        } else if (block instanceof RotatedPillarBlock) {
            clickAxis = state.getValue(RotatedPillarBlock.AXIS);
        }

        return new Requirement(Skip.NONE, half, clickFace, clickAxis, rotationDecides(state));
    }

    private static Skip skip(BlockState state) {
        if (StateComparison.derived(state)) return Skip.DERIVED;
        if (state.getBlock().asItem() == Items.AIR) return Skip.NO_ITEM;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return Skip.UPPER_HALF;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return Skip.BED_HEAD;
        }
        if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            return Skip.DOUBLE_SLAB;
        }
        if (stacked(state, BlockStateProperties.CANDLES) || stacked(state, BlockStateProperties.PICKLES)
                || stacked(state, BlockStateProperties.EGGS) || stacked(state, BlockStateProperties.LAYERS)
                || stacked(state, BlockStateProperties.FLOWER_AMOUNT)
                || stacked(state, BlockStateProperties.SEGMENT_AMOUNT)) {
            return Skip.STACKED;
        }
        return Skip.NONE;
    }

    private static boolean stacked(BlockState state, net.minecraft.world.level.block.state.properties.IntegerProperty p) {
        return state.hasProperty(p) && state.getValue(p) > 1;
    }

    /** Whether the player's rotation can change the state this click produces. */
    public static boolean rotationDecides(BlockState state) {
        return RedstoneRules.lookDecides(state)
                || state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || state.hasProperty(BlockStateProperties.FACING)
                || state.hasProperty(BlockStateProperties.ORIENTATION)
                || state.hasProperty(BlockStateProperties.ROTATION_16)
                || state.hasProperty(BlockStateProperties.ATTACH_FACE);
    }
}
