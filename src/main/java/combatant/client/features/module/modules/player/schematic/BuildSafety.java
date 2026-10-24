/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import combatant.client.util.schematic.BuildOrder;
import combatant.client.util.schematic.RedstoneRules;
import combatant.client.util.schematic.SchematicDiff;
import combatant.client.util.schematic.SchematicPlacement;
import combatant.client.util.schematic.StateComparison;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps a redstone build from firing while it goes up: which cells may go in now (their band is open, what they
 * hang on or watch is there), which open cells a layer may leave behind, and whether placing a cell this tick
 * would power something the schematic shows idle.
 */
final class BuildSafety {

    enum Hazard {
        NONE,
        /** Placing now would fire or move something; waiting may let the circuit settle. */
        DEFER,
        /** TNT that would be lit on landing: never placed while that holds. */
        NEVER
    }

    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
    private int forcedBand;

    void reset() {
        forcedBand = 0;
    }

    int forcedBand() {
        return forcedBand;
    }

    /** Opens the next band early: the builder had nothing else left it could do. */
    void force(int band) {
        forcedBand = Math.max(forcedBand, Math.min(band, BuildOrder.BANDS - 1));
    }

    int openBand(SchematicDiff diff) {
        return BuildOrder.openBand(diff.openPerBand(), forcedBand);
    }

    /** Whether a later band still waits behind the open one. */
    boolean gated(SchematicDiff diff) {
        int open = openBand(diff);
        int[] perBand = diff.openPerBand();
        for (int band = open + 1; band < perBand.length; band++) {
            if (perBand[band] > 0) return true;
        }
        return false;
    }

    /** A cell counts as there once anything solid holds its place; piston heads and fluids always do. */
    boolean present(Level level, SchematicDiff diff, int cell) {
        if (StateComparison.derived(diff.expectedState(cell))) return true;
        diff.worldPos(cell, scratch);
        return !level.getBlockState(scratch).canBeReplaced();
    }

    boolean eligible(Level level, SchematicDiff diff, int cell, int openBand) {
        return diff.order().eligible(cell, openBand, dep -> present(level, diff, dep));
    }

    /** Cells a lower layer may keep open: power, late cells and cells hanging on a higher layer. */
    static boolean deferrable(SchematicDiff diff, int cell) {
        return diff.order().deferred(cell, 0, dep -> false);
    }

    /** Every open cell of the layer is waiting for a closed band or for something above it. */
    boolean layerSettled(Level level, SchematicDiff diff, int y, int openBand) {
        if (diff.layerComplete(y)) return true;
        BuildOrder order = diff.order();
        for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
            if (!open(diff.status(cell))) continue;
            if (!order.deferred(cell, openBand, dep -> present(level, diff, dep))) return false;
        }
        return true;
    }

    /** Whether any layer below {@code below} still holds a deferred cell to come back for. */
    static boolean backlogOpen(SchematicDiff diff, int below) {
        for (int y = 0; y < below; y++) {
            if (openCount(diff, y) == 0) continue;
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
                if (open(diff.status(cell)) && deferrable(diff, cell)) return true;
            }
        }
        return false;
    }

    static int openCount(SchematicDiff diff, int y) {
        return diff.total(y) - diff.correct(y) - diff.wrong(y);
    }

    static boolean open(byte status) {
        return status == SchematicDiff.UNKNOWN || status == SchematicDiff.MISSING;
    }

    /**
     * Sort weight of a cell: below the power band the layer comes first and the phase second, so supports go in
     * before what hangs on them; inside the power band the phase comes first over the whole build (sources,
     * then unlit torches, then dust, then the observers waiting for it all to settle).
     */
    static double major(int order, int y) {
        return order >= RedstoneRules.POWER ? 1.0e7 + order * 1.0e4 + y : y * 100.0 + order;
    }

    /** Whether placing {@code expected} at {@code target} now would power something the schematic shows idle. */
    Hazard hazard(Level level, SchematicDiff diff, BlockPos target, BlockState expected) {
        if (RedstoneRules.reactsToPower(expected) && RedstoneRules.shownIdle(expected) && powered(level, target, expected)) {
            return expected.getBlock() instanceof TntBlock ? Hazard.NEVER : Hazard.DEFER;
        }
        int order = RedstoneRules.baseOrder(expected);
        if (order == RedstoneRules.POWER_OFF) {
            // Shown unlit: it lights up (and powers its neighbours) unless the block it hangs on is powered now.
            Direction toSupport = RedstoneRules.supportDirection(expected);
            if (toSupport != null && !level.hasSignal(target.relative(toSupport), toSupport)) return Hazard.DEFER;
            return Hazard.NONE;
        }
        if (order == RedstoneRules.DUST_OFF) {
            return level.getBestNeighborSignal(target) > 0 ? Hazard.DEFER : Hazard.NONE;
        }
        // A lit source next to a piston or TNT the schematic shows idle fires it (a BUD or a snapshot mid-pulse).
        if (order == RedstoneRules.POWER && idleNeighbour(level, diff, target)) return Hazard.DEFER;
        return Hazard.NONE;
    }

    private static boolean powered(Level level, BlockPos target, BlockState expected) {
        if (level.hasNeighborSignal(target)) return true;
        return RedstoneRules.quasiConnected(expected) && level.hasNeighborSignal(target.above());
    }

    /** A placed neighbour that reacts to power and that the schematic shows idle (a retracted piston, unlit TNT). */
    private boolean idleNeighbour(Level level, SchematicDiff diff, BlockPos target) {
        SchematicPlacement placement = diff.placement();
        for (Direction direction : Direction.values()) {
            neighbour.setWithOffset(target, direction);
            BlockState actual = level.getBlockState(neighbour);
            if (!RedstoneRules.reactsToPower(actual)) continue;
            int x = placement.localX(neighbour.getX(), neighbour.getZ());
            int y = placement.localY(neighbour.getY());
            int z = placement.localZ(neighbour.getX(), neighbour.getZ());
            if (!diff.schematic().contains(x, y, z)) continue;
            int cell = diff.cellOf(diff.schematic().index(x, y, z));
            if (cell < 0) continue;
            BlockState expected = diff.expectedState(cell);
            if (expected.getBlock() == actual.getBlock() && RedstoneRules.shownIdle(expected)) return true;
        }
        return false;
    }
}
