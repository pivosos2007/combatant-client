/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/**
 * Compares the non-air cells of a placed schematic against the world, a bounded number of cells per tick.
 *
 * <p>Cells are kept in schematic index order, which is y-major, so every layer is one contiguous range of
 * {@link #cellCount()}. Each tick checks part of the focused layer window and part of the whole build, so
 * the layers being worked on converge within a few ticks while the rest of the build still catches up.</p>
 */
public final class SchematicDiff {

    public static final byte UNKNOWN = 0;
    public static final byte CORRECT = 1;
    public static final byte MISSING = 2;
    public static final byte WRONG = 3;

    private final Schematic schematic;
    private final SchematicPlacement placement;
    private final BlockState[] worldPalette;
    private final int[] cells;
    private final int[] layerStart;
    private final byte[] status;
    private final int[] correct;
    private final int[] missing;
    private final int[] wrong;
    private final BuildOrder order;
    /** Cells per band that are neither correct nor wrong: what still holds a band's gate shut. */
    private final int[] openPerBand = new int[BuildOrder.BANDS];
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private int globalCursor;
    private int focusCursor;
    private int focusLow = -1;
    private int focusHigh = -1;
    private int revision;

    public SchematicDiff(Schematic schematic, SchematicPlacement placement) {
        this.schematic = schematic;
        this.placement = placement;
        this.worldPalette = new BlockState[schematic.paletteSize()];
        for (int i = 0; i < worldPalette.length; i++) {
            worldPalette[i] = placement.transform(schematic.paletteState(i));
        }

        int sizeY = schematic.sizeY();
        int area = schematic.layerArea();
        this.layerStart = new int[sizeY + 1];
        int count = 0;
        for (int i = 0; i < schematic.volume(); i++) {
            if (schematic.paletteIndexAt(i) != 0) count++;
        }
        this.cells = new int[count];
        int next = 0;
        for (int y = 0; y < sizeY; y++) {
            layerStart[y] = next;
            int start = y * area;
            for (int i = start; i < start + area; i++) {
                if (schematic.paletteIndexAt(i) != 0) cells[next++] = i;
            }
        }
        layerStart[sizeY] = next;
        this.status = new byte[count];
        this.correct = new int[sizeY];
        this.missing = new int[sizeY];
        this.wrong = new int[sizeY];
        this.order = BuildOrder.compute(schematic, cells);
        for (int cell = 0; cell < count; cell++) openPerBand[order.cellBand(cell)]++;
    }

    public Schematic schematic() {
        return schematic;
    }

    public SchematicPlacement placement() {
        return placement;
    }

    public int revision() {
        return revision;
    }

    public int cellCount() {
        return cells.length;
    }

    public int layerStart(int y) {
        return layerStart[Math.max(0, Math.min(y, layerStart.length - 1))];
    }

    public int layerEnd(int y) {
        return layerStart(y + 1);
    }

    public int schematicIndex(int cell) {
        return cells[cell];
    }

    /** The diff cell of a schematic index, or -1 when that index is air. Cells are kept in index order. */
    public int cellOf(int schematicIndex) {
        int found = Arrays.binarySearch(cells, schematicIndex);
        return found >= 0 ? found : -1;
    }

    public BuildOrder order() {
        return order;
    }

    /** Cells of each band still missing or unchecked. */
    public int[] openPerBand() {
        return openPerBand;
    }

    public int openTotal() {
        int total = 0;
        for (int open : openPerBand) total += open;
        return total;
    }

    public byte status(int cell) {
        return status[cell];
    }

    public BlockState expectedState(int cell) {
        return worldPalette[schematic.paletteIndexAt(cells[cell])];
    }

    public int correct(int y) {
        return y < 0 || y >= correct.length ? 0 : correct[y];
    }

    public int missing(int y) {
        return y < 0 || y >= missing.length ? 0 : missing[y];
    }

    public int wrong(int y) {
        return y < 0 || y >= wrong.length ? 0 : wrong[y];
    }

    public int checked(int y) {
        return correct(y) + missing(y) + wrong(y);
    }

    public int total(int y) {
        return layerEnd(y) - layerStart(y);
    }

    public boolean layerComplete(int y) {
        return total(y) == correct(y) + wrong(y);
    }

    public void setFocus(int low, int high) {
        if (low == focusLow && high == focusHigh) return;
        focusLow = low;
        focusHigh = high;
        focusCursor = layerStart(low);
    }

    public BlockPos.MutableBlockPos worldPos(int cell, BlockPos.MutableBlockPos out) {
        int index = cells[cell];
        return placement.toWorld(schematic.xOf(index), schematic.yOf(index), schematic.zOf(index), out);
    }

    /** Checks up to {@code focusBudget} cells of the focus window and {@code globalBudget} cells of the whole build. */
    public void tick(Level level, int focusBudget, int globalBudget) {
        if (level == null || cells.length == 0) return;
        if (focusLow >= 0) {
            int start = layerStart(focusLow);
            int end = layerEnd(focusHigh);
            int span = end - start;
            if (span > 0) {
                if (focusCursor < start || focusCursor >= end) focusCursor = start;
                int steps = Math.min(focusBudget, span);
                for (int i = 0; i < steps; i++) {
                    refresh(level, focusCursor);
                    if (++focusCursor >= end) focusCursor = start;
                }
            }
        }
        int steps = Math.min(globalBudget, cells.length);
        for (int i = 0; i < steps; i++) {
            refresh(level, globalCursor);
            if (++globalCursor >= cells.length) globalCursor = 0;
        }
    }

    public byte refresh(Level level, int cell) {
        worldPos(cell, scratch);
        byte next;
        if (!level.hasChunkAt(scratch)) {
            next = UNKNOWN;
        } else {
            next = classify(level.getBlockState(scratch), expectedState(cell));
        }
        set(cell, next);
        return next;
    }

    public static byte classify(BlockState actual, BlockState expected) {
        if (actual == expected) return CORRECT;
        if (StateComparison.derived(expected)) return StateComparison.derivedSatisfied(actual, expected) ? CORRECT : WRONG;
        if (StateComparison.matches(actual, expected)) return CORRECT;
        if (actual.canBeReplaced()) return MISSING;
        return WRONG;
    }

    private void set(int cell, byte next) {
        byte previous = status[cell];
        if (previous == next) return;
        int y = schematic.yOf(cells[cell]);
        adjust(previous, y, -1);
        adjust(next, y, 1);
        boolean wasOpen = previous == UNKNOWN || previous == MISSING;
        boolean isOpen = next == UNKNOWN || next == MISSING;
        if (wasOpen != isOpen) openPerBand[order.cellBand(cell)] += isOpen ? 1 : -1;
        status[cell] = next;
        revision++;
    }

    private void adjust(byte value, int y, int delta) {
        switch (value) {
            case CORRECT -> correct[y] += delta;
            case MISSING -> missing[y] += delta;
            case WRONG -> wrong[y] += delta;
            default -> {
            }
        }
    }

    public void reset() {
        Arrays.fill(status, UNKNOWN);
        Arrays.fill(correct, 0);
        Arrays.fill(missing, 0);
        Arrays.fill(wrong, 0);
        Arrays.fill(openPerBand, 0);
        for (int cell = 0; cell < cells.length; cell++) openPerBand[order.cellBand(cell)]++;
        globalCursor = 0;
        focusCursor = layerStart(Math.max(0, focusLow));
        revision++;
    }
}
