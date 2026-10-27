/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * Placement order of the cells of one schematic: a phase per cell (see {@link RedstoneRules}) and at most one
 * cell that has to be in the world first (the block a torch hangs on, the sand's floor, what an observer watches,
 * the block a sideways hopper feeds, whose face is the only click that gives that facing).
 *
 * <p>Phases fall into bands. Inside the lowest band the order is a preference: the builder still goes layer by
 * layer and only sorts by phase within a layer. The power band and the late band are hard gates over the whole
 * build: no power goes in while a single lower cell is missing, so nothing fires half built.</p>
 */
public final class BuildOrder {

    public static final int BANDS = 3;
    public static final int NO_DEPENDENCY = -1;

    private final Schematic schematic;
    private final int[] cells;
    private final byte[] order;
    private final int[] dependency;

    private BuildOrder(Schematic schematic, int[] cells, byte[] order, int[] dependency) {
        this.schematic = schematic;
        this.cells = cells;
        this.order = order;
        this.dependency = dependency;
    }

    /** @param cells the non-air schematic indices, ascending */
    public static BuildOrder compute(Schematic schematic, int[] cells) {
        int count = cells.length;
        byte[] base = new byte[count];
        int[] dependency = new int[count];
        boolean[] watchesChange = new boolean[count];
        for (int cell = 0; cell < count; cell++) {
            BlockState state = schematic.paletteState(schematic.paletteIndexAt(cells[cell]));
            base[cell] = (byte) RedstoneRules.baseOrder(state);
            Direction toDependency = dependencyDirection(state);
            dependency[cell] = toDependency == null ? NO_DEPENDENCY : neighbour(schematic, cells, cells[cell], toDependency);
            if (dependency[cell] != NO_DEPENDENCY && RedstoneRules.observedDirection(state) != null) {
                BlockState watched = schematic.paletteState(schematic.paletteIndexAt(cells[dependency[cell]]));
                watchesChange[cell] = RedstoneRules.changesWithPower(watched);
            }
        }
        keepLookFree(schematic, cells, dependency);
        breakCycles(dependency);
        return new BuildOrder(schematic, cells, propagate(base, dependency, watchesChange), dependency);
    }

    private static Direction dependencyDirection(BlockState state) {
        Direction observed = RedstoneRules.observedDirection(state);
        if (observed != null) return observed;
        Direction support = RedstoneRules.supportDirection(state);
        if (support != null) return support;
        Direction feeds = RedstoneRules.feedsInto(state);
        if (feeds != null) return feeds;
        return RedstoneRules.needsBlockBelow(state) ? Direction.DOWN : null;
    }

    /**
     * A piston or dropper is placed looking at it from the side it faces, an observer from behind: a plain
     * block in that cell placed first would stand between the player and the only clicks that give the facing
     * (the block a flush door pushes, for one). That cell waits for the piston instead.
     */
    private static void keepLookFree(Schematic schematic, int[] cells, int[] dependency) {
        for (int cell = 0; cell < cells.length; cell++) {
            BlockState state = schematic.paletteState(schematic.paletteIndexAt(cells[cell]));
            Direction look = RedstoneRules.requiredLook(state);
            if (look == null || RedstoneRules.supportDirection(state) != null) continue;
            int inTheWay = neighbour(schematic, cells, cells[cell], look.getOpposite());
            if (inTheWay == NO_DEPENDENCY || dependency[inTheWay] != NO_DEPENDENCY) continue;
            BlockState blocking = schematic.paletteState(schematic.paletteIndexAt(cells[inTheWay]));
            if (RedstoneRules.baseOrder(blocking) != RedstoneRules.SUPPORT || StateComparison.derived(blocking)) continue;
            dependency[inTheWay] = cell;
        }
    }

    private static int neighbour(Schematic schematic, int[] cells, int index, Direction direction) {
        int x = schematic.xOf(index) + direction.getStepX();
        int y = schematic.yOf(index) + direction.getStepY();
        int z = schematic.zOf(index) + direction.getStepZ();
        if (!schematic.contains(x, y, z)) return NO_DEPENDENCY;
        int found = Arrays.binarySearch(cells, schematic.index(x, y, z));
        return found >= 0 ? found : NO_DEPENDENCY;
    }

    /**
     * Every cell has at most one dependency, so the graph is a set of chains that may end in a loop (two
     * observers watching each other). A loop would wait forever; its closing edge is dropped.
     */
    private static void breakCycles(int[] dependency) {
        int count = dependency.length;
        byte[] mark = new byte[count];
        for (int start = 0; start < count; start++) {
            if (mark[start] != 0) continue;
            int cell = start;
            while (cell != NO_DEPENDENCY && mark[cell] == 0) {
                mark[cell] = 1;
                int next = dependency[cell];
                if (next != NO_DEPENDENCY && mark[next] == 1) {
                    dependency[cell] = NO_DEPENDENCY;
                    next = NO_DEPENDENCY;
                }
                cell = next;
            }
            for (cell = start; cell != NO_DEPENDENCY && mark[cell] == 1; cell = dependency[cell]) mark[cell] = 2;
        }
    }

    /** A cell goes no earlier than what it depends on; an observer of a changing block waits for power to settle. */
    private static byte[] propagate(byte[] base, int[] dependency, boolean[] watchesChange) {
        int count = base.length;
        byte[] order = new byte[count];
        boolean[] done = new boolean[count];
        int[] chain = new int[16];
        for (int start = 0; start < count; start++) {
            int length = 0;
            for (int cell = start; cell != NO_DEPENDENCY && !done[cell]; cell = dependency[cell]) {
                if (length == chain.length) chain = Arrays.copyOf(chain, length * 2);
                chain[length++] = cell;
            }
            for (int i = length - 1; i >= 0; i--) {
                int cell = chain[i];
                int value = base[cell];
                int dep = dependency[cell];
                if (dep != NO_DEPENDENCY) {
                    value = Math.max(value, order[dep]);
                    if (watchesChange[cell] || order[dep] >= RedstoneRules.SETTLED_OBSERVER) {
                        value = Math.max(value, RedstoneRules.SETTLED_OBSERVER);
                    }
                }
                order[cell] = (byte) value;
                done[cell] = true;
            }
        }
        return order;
    }

    public static int band(int order) {
        if (order >= RedstoneRules.LATE) return 2;
        return order >= RedstoneRules.POWER ? 1 : 0;
    }

    public int cellCount() {
        return order.length;
    }

    public int order(int cell) {
        return order[cell];
    }

    public int cellBand(int cell) {
        return band(order[cell]);
    }

    public int dependency(int cell) {
        return dependency[cell];
    }

    public int layerOf(int cell) {
        return schematic.yOf(cells[cell]);
    }

    /**
     * The highest band allowed to place, given how many cells of each band are still open: a band opens once
     * every lower band is done. {@code forced} is a band the builder opened early because it could do nothing else.
     */
    public static int openBand(int[] openPerBand, int forced) {
        int band = 0;
        while (band < BANDS - 1 && openPerBand[band] == 0) band++;
        return Math.max(band, forced);
    }

    /** Whether the cell may be placed now: its band is open and the cell it depends on is in the world. */
    public boolean eligible(int cell, int openBand, IntPredicate present) {
        if (band(order[cell]) > openBand) return false;
        int dep = dependency[cell];
        return dep == NO_DEPENDENCY || present.test(dep);
    }

    /**
     * Whether an open cell of its layer may be left behind when the builder moves up: its band is still closed,
     * or it waits for a cell in a higher layer.
     */
    public boolean deferred(int cell, int openBand, IntPredicate present) {
        if (band(order[cell]) > openBand) return true;
        int dep = dependency[cell];
        return dep != NO_DEPENDENCY && layerOf(dep) > layerOf(cell) && !present.test(dep);
    }
}
