/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * How a block fits into a build that must not fire while it is put together: the phase it is placed in, the
 * cell it hangs on, and which way the player has to look for it to come out facing right.
 *
 * <p>Phases, lowest first: plain blocks that carry others, then the parts that hang on them (torches, levers,
 * buttons, rails, plates, repeaters, comparators), then the parts that move or fire when powered (pistons,
 * dispensers, droppers, hoppers, lamps), then the power itself (redstone blocks and torches, dust last, unlit
 * ones after lit ones), then whatever reacts to anything at all (TNT, sculk sensors). Observers go after the
 * block they watch, and after all power when that block changes with the circuit.</p>
 */
public final class RedstoneRules {

    public static final int SUPPORT = 0;
    public static final int ATTACHED = 10;
    public static final int MECHANISM = 20;
    /** First order of the power band: nothing in it goes in before every lower cell is done. */
    public static final int POWER = 30;
    public static final int POWER_OFF = 32;
    public static final int DUST = 34;
    public static final int DUST_OFF = 35;
    /** Observers watching something the circuit changes: last of the power band, once it settled. */
    public static final int SETTLED_OBSERVER = 36;
    /** First order of the late band: TNT and sculk sensors, after everything else. */
    public static final int LATE = 40;

    private RedstoneRules() {
    }

    /** Placement phase of a block on its own, before dependencies raise it. */
    public static int baseOrder(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof TntBlock || block instanceof SculkSensorBlock) return LATE;
        if (block instanceof RedStoneWireBlock) return state.getValue(RedStoneWireBlock.POWER) > 0 ? DUST : DUST_OFF;
        if (block instanceof RedstoneTorchBlock) return state.getValue(RedstoneTorchBlock.LIT) ? POWER : POWER_OFF;
        if (block instanceof PoweredBlock || block instanceof DaylightDetectorBlock) return POWER;
        if (block instanceof PistonBaseBlock || block instanceof DispenserBlock || block instanceof HopperBlock
                || block instanceof ObserverBlock || block instanceof CrafterBlock || block instanceof NoteBlock
                || block instanceof RedstoneLampBlock || block instanceof CopperBulbBlock
                || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof LightningRodBlock) return MECHANISM;
        if (supportDirection(state) != null || block instanceof TripWireBlock) return ATTACHED;
        return SUPPORT;
    }

    /** The direction of the cell this block hangs on, or {@code null} when it stands on its own. */
    public static Direction supportDirection(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof FaceAttachedHorizontalDirectionalBlock) {
            AttachFace face = state.getValue(BlockStateProperties.ATTACH_FACE);
            return switch (face) {
                case FLOOR -> Direction.DOWN;
                case CEILING -> Direction.UP;
                case WALL -> state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
            };
        }
        if (block instanceof WallTorchBlock) return state.getValue(WallTorchBlock.FACING).getOpposite();
        if (block instanceof RedstoneWallTorchBlock) return state.getValue(RedstoneWallTorchBlock.FACING).getOpposite();
        if (block instanceof TripWireHookBlock) return state.getValue(TripWireHookBlock.FACING).getOpposite();
        if (block instanceof WallSignBlock) return state.getValue(WallSignBlock.FACING).getOpposite();
        if (block instanceof LanternBlock) return state.getValue(LanternBlock.HANGING) ? Direction.UP : Direction.DOWN;
        if (block instanceof TorchBlock || block instanceof RedstoneTorchBlock || block instanceof DiodeBlock
                || block instanceof RedStoneWireBlock || block instanceof BasePressurePlateBlock
                || block instanceof BaseRailBlock || block instanceof CarpetBlock || block instanceof DoorBlock
                || block instanceof StandingSignBlock) return Direction.DOWN;
        return hanging(block, state) ? Direction.UP : null;
    }

    /** Blocks that grow or hang from the cell above them: no click holds them up without it. */
    private static boolean hanging(Block block, BlockState state) {
        if (block instanceof net.minecraft.world.level.block.CeilingHangingSignBlock
                || block instanceof net.minecraft.world.level.block.CaveVinesBlock
                || block instanceof net.minecraft.world.level.block.CaveVinesPlantBlock
                || block instanceof net.minecraft.world.level.block.WeepingVinesBlock
                || block instanceof net.minecraft.world.level.block.WeepingVinesPlantBlock
                || block instanceof net.minecraft.world.level.block.HangingRootsBlock
                || block instanceof net.minecraft.world.level.block.SporeBlossomBlock) return true;
        if (block instanceof net.minecraft.world.level.block.MangrovePropaguleBlock) {
            return state.getValue(net.minecraft.world.level.block.MangrovePropaguleBlock.HANGING);
        }
        if (block instanceof net.minecraft.world.level.block.PointedDripstoneBlock) {
            return state.getValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION) == Direction.DOWN;
        }
        if (block instanceof net.minecraft.world.level.block.BellBlock) {
            return state.getValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT)
                    == net.minecraft.world.level.block.state.properties.BellAttachType.CEILING;
        }
        return false;
    }

    /**
     * The side a hopper feeds into: its facing comes from the clicked face, so a sideways hopper can only be
     * placed by clicking the block it points at. {@code null} for anything else and for hoppers facing down.
     */
    public static Direction feedsInto(BlockState state) {
        if (!(state.getBlock() instanceof HopperBlock)) return null;
        Direction facing = state.getValue(HopperBlock.FACING);
        return facing == Direction.DOWN ? null : facing;
    }

    /** Sand, gravel, concrete powder, anvils and scaffolding fall (or break) without the block under them. */
    public static boolean needsBlockBelow(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FallingBlock || block instanceof ScaffoldingBlock;
    }

    /** The side an observer watches, or {@code null} for anything else. */
    public static Direction observedDirection(BlockState state) {
        return state.getBlock() instanceof ObserverBlock ? state.getValue(ObserverBlock.FACING) : null;
    }

    /**
     * The direction the player has to look (the nearest axis of the look vector) for the block's own placement
     * rule to produce this state, or {@code null} when the look does not decide the facing.
     */
    public static Direction requiredLook(BlockState state) {
        Block block = state.getBlock();
        // Wall levers, buttons and torches go on the first block along the look that holds them: the look has to
        // run into their wall before it runs into the floor.
        if (block instanceof FaceAttachedHorizontalDirectionalBlock
                && state.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        }
        if (block instanceof WallTorchBlock) return state.getValue(WallTorchBlock.FACING).getOpposite();
        if (block instanceof RedstoneWallTorchBlock) return state.getValue(RedstoneWallTorchBlock.FACING).getOpposite();
        if (block instanceof PistonBaseBlock) return state.getValue(PistonBaseBlock.FACING).getOpposite();
        if (block instanceof ObserverBlock) return state.getValue(ObserverBlock.FACING);
        if (block instanceof DispenserBlock) return state.getValue(DispenserBlock.FACING).getOpposite();
        if (block instanceof CrafterBlock) return state.getValue(BlockStateProperties.ORIENTATION).front().getOpposite();
        return null;
    }

    /**
     * The horizontal direction the player has to face (only the yaw counts) for the block to come out facing
     * right: repeaters and comparators point away from the player, floor and ceiling levers and buttons, doors
     * and gates along the look, sideways hoppers towards what they feed. {@code null} when the yaw does not
     * decide it.
     */
    public static Direction requiredYaw(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DiodeBlock) return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        if (block instanceof FaceAttachedHorizontalDirectionalBlock
                && state.getValue(BlockStateProperties.ATTACH_FACE) != AttachFace.WALL) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (block instanceof DoorBlock || block instanceof FenceGateBlock) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        // A sideways hopper is clicked on the face of the block it feeds, which only shows looking that way.
        return feedsInto(state);
    }

    /** Whether the look (all of it, or only the yaw) decides how this block comes out. */
    public static boolean lookDecides(BlockState state) {
        return requiredLook(state) != null || requiredYaw(state) != null;
    }

    /** Whether the block's state changes with the circuit, so an observer watching it pulses while power goes in. */
    public static boolean changesWithPower(BlockState state) {
        Block block = state.getBlock();
        return reactsToPower(state) || block instanceof RedStoneWireBlock || block instanceof RedstoneTorchBlock
                || block instanceof DiodeBlock || block instanceof ObserverBlock || block instanceof HopperBlock
                || block instanceof BaseRailBlock || block instanceof DaylightDetectorBlock;
    }

    /** Blocks that do something (move, fire, light, open, explode) when they receive power. */
    public static boolean reactsToPower(BlockState state) {
        Block block = state.getBlock();
        return block instanceof PistonBaseBlock || block instanceof DispenserBlock || block instanceof RedstoneLampBlock
                || block instanceof TntBlock || block instanceof NoteBlock || block instanceof DoorBlock
                || block instanceof TrapDoorBlock || block instanceof FenceGateBlock || block instanceof CrafterBlock
                || block instanceof CopperBulbBlock || block instanceof PoweredRailBlock;
    }

    /** The schematic shows this reacting block unpowered: built powered, it would fire or move. */
    public static boolean shownIdle(BlockState state) {
        if (state.getBlock() instanceof TntBlock) return true;
        if (state.hasProperty(BlockStateProperties.EXTENDED)) return !state.getValue(BlockStateProperties.EXTENDED);
        if (state.hasProperty(BlockStateProperties.TRIGGERED)) return !state.getValue(BlockStateProperties.TRIGGERED);
        if (state.hasProperty(BlockStateProperties.POWERED)) return !state.getValue(BlockStateProperties.POWERED);
        if (state.hasProperty(BlockStateProperties.LIT)) return !state.getValue(BlockStateProperties.LIT);
        return false;
    }

    /** Pistons, dispensers and droppers are also powered through the block above them. */
    public static boolean quasiConnected(BlockState state) {
        Block block = state.getBlock();
        return block instanceof PistonBaseBlock || block instanceof DispenserBlock;
    }

    /** Anything a redstone build is made of, for the materials and layer views. */
    public static boolean isComponent(BlockState state) {
        Block block = state.getBlock();
        return state.isSignalSource() || reactsToPower(state) || changesWithPower(state)
                || block instanceof SlimeBlock || block instanceof HoneyBlock || block instanceof TargetBlock
                || block instanceof TripWireBlock || block instanceof TripWireHookBlock
                || block instanceof BasePressurePlateBlock || block instanceof ButtonBlock || block instanceof LeverBlock
                || block instanceof LightningRodBlock || block instanceof SculkSensorBlock
                || block instanceof PistonHeadBlock || block instanceof MovingPistonBlock;
    }
}
