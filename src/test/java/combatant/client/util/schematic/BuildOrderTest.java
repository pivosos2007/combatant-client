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
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildOrderTest {

    private static final int SX = 7, SY = 3, SZ = 3;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Builds a schematic from {x, y, z} -> state assignments; everything else is air. */
    private static final class Builder {
        final List<BlockState> palette = new ArrayList<>(List.of(Blocks.AIR.defaultBlockState()));
        final short[] blocks;
        final int sx, sy, sz;

        Builder(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.blocks = new short[sx * sy * sz];
        }

        Builder set(int x, int y, int z, BlockState state) {
            int index = palette.indexOf(state);
            if (index < 0) {
                palette.add(state);
                index = palette.size() - 1;
            }
            blocks[(y * sz + z) * sx + x] = (short) index;
            return this;
        }

        Schematic build() {
            String[] ids = new String[palette.size()];
            Arrays.fill(ids, "x");
            int total = 0;
            for (short b : blocks) if (b != 0) total++;
            return new Schematic("t", "t", "", "", sx, sy, sz, palette.toArray(new BlockState[0]), ids, blocks,
                    total, 1, List.of());
        }
    }

    private static BlockState observer(Direction facing) {
        return Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, facing);
    }

    /**
     * A tiny machine on a stone floor: an observer line watching east, a sticky piston door, a torch and dust
     * clock, a repeater, a wall lever, a ceiling lever under the top layer, sand on a post and TNT.
     */
    private static Schematic machine() {
        Builder b = new Builder(SX, SY, SZ);
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = 0; x < SX; x++) for (int z = 0; z < SZ; z++) b.set(x, 0, z, stone);
        b.set(0, 1, 0, observer(Direction.EAST)).set(1, 1, 0, observer(Direction.EAST)).set(2, 1, 0, observer(Direction.EAST));
        b.set(3, 1, 0, stone);
        b.set(4, 1, 0, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.SOUTH));
        b.set(4, 1, 1, stone);
        b.set(5, 1, 0, Blocks.REDSTONE_TORCH.defaultBlockState());
        b.set(6, 1, 0, Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedstoneWireBlock.POWER, 15));
        b.set(6, 1, 1, Blocks.REDSTONE_WIRE.defaultBlockState());
        b.set(3, 1, 2, Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.DELAY, 4));
        b.set(4, 1, 2, stone);
        b.set(5, 1, 2, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.EAST));
        b.set(2, 1, 2, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.CEILING));
        b.set(2, 2, 2, stone);
        b.set(0, 1, 2, stone);
        b.set(0, 2, 2, Blocks.SAND.defaultBlockState());
        b.set(6, 1, 2, Blocks.TNT.defaultBlockState());
        return b.build();
    }

    private static int[] cells(Schematic schematic) {
        int count = 0;
        for (int i = 0; i < schematic.volume(); i++) if (schematic.paletteIndexAt(i) != 0) count++;
        int[] cells = new int[count];
        int next = 0;
        for (int i = 0; i < schematic.volume(); i++) if (schematic.paletteIndexAt(i) != 0) cells[next++] = i;
        return cells;
    }

    private static int cell(Schematic schematic, int[] cells, int x, int y, int z) {
        return Arrays.binarySearch(cells, schematic.index(x, y, z));
    }

    @Test
    void phasesAndDependenciesOfTheTinyMachine() {
        Schematic s = machine();
        int[] cells = cells(s);
        BuildOrder order = BuildOrder.compute(s, cells);

        assertEquals(RedstoneRules.SUPPORT, order.order(cell(s, cells, 3, 0, 0)));
        assertEquals(RedstoneRules.ATTACHED, order.order(cell(s, cells, 3, 1, 2)));
        assertEquals(RedstoneRules.MECHANISM, order.order(cell(s, cells, 4, 1, 0)));
        assertEquals(RedstoneRules.POWER, order.order(cell(s, cells, 5, 1, 0)));
        assertEquals(RedstoneRules.DUST, order.order(cell(s, cells, 6, 1, 0)));
        assertEquals(RedstoneRules.DUST_OFF, order.order(cell(s, cells, 6, 1, 1)));
        assertEquals(RedstoneRules.LATE, order.order(cell(s, cells, 6, 1, 2)));
        // The last observer watches plain stone; the ones behind it watch an observer, which pulses with power.
        assertEquals(RedstoneRules.MECHANISM, order.order(cell(s, cells, 2, 1, 0)));
        assertEquals(RedstoneRules.SETTLED_OBSERVER, order.order(cell(s, cells, 1, 1, 0)));
        assertEquals(RedstoneRules.SETTLED_OBSERVER, order.order(cell(s, cells, 0, 1, 0)));

        assertEquals(cell(s, cells, 3, 1, 0), order.dependency(cell(s, cells, 2, 1, 0)));
        assertEquals(cell(s, cells, 2, 1, 0), order.dependency(cell(s, cells, 1, 1, 0)));
        assertEquals(cell(s, cells, 6, 0, 0), order.dependency(cell(s, cells, 6, 1, 0)));
        assertEquals(cell(s, cells, 4, 1, 2), order.dependency(cell(s, cells, 5, 1, 2)));
        assertEquals(cell(s, cells, 2, 2, 2), order.dependency(cell(s, cells, 2, 1, 2)));
        assertEquals(cell(s, cells, 0, 1, 2), order.dependency(cell(s, cells, 0, 2, 2)));
        assertEquals(BuildOrder.NO_DEPENDENCY, order.dependency(cell(s, cells, 4, 1, 0)));
        // The block the door piston pushes would stand where the player looks at the piston from.
        assertEquals(cell(s, cells, 4, 1, 0), order.dependency(cell(s, cells, 4, 1, 1)));
    }

    /** Places the machine the way the builder does: layer by layer within the open band, lowest phase first. */
    @Test
    void simulatedBuildNeverPowersAnythingEarly() {
        Schematic s = machine();
        int[] cells = cells(s);
        BuildOrder order = BuildOrder.compute(s, cells);
        boolean[] placed = new boolean[cells.length];
        int[] open = new int[BuildOrder.BANDS];
        for (int c = 0; c < cells.length; c++) open[order.cellBand(c)]++;
        List<Integer> sequence = new ArrayList<>();
        int layer = 0;
        while (sequence.size() < cells.length) {
            int band = BuildOrder.openBand(open, 0);
            // Move up past layers whose open cells are all deferred.
            while (layer < SY - 1 && layerSettled(s, cells, order, placed, layer, band)) layer++;
            int best = -1;
            for (int c = 0; c < cells.length; c++) {
                if (placed[c] || !order.eligible(c, band, d -> placed[d])) continue;
                int y = order.layerOf(c);
                // Lower layers only hold deferred cells once the builder is past them.
                if (y > layer || y < layer && !order.deferred(c, 0, d -> false) && order.cellBand(c) == 0) continue;
                if (best < 0 || key(order, c) < key(order, best)) best = c;
            }
            assertNotEquals(-1, best, "stuck after " + sequence.size() + " cells at layer " + layer);
            placed[best] = true;
            open[order.cellBand(best)]--;
            sequence.add(best);
        }

        int lastLowBand = -1, firstPower = Integer.MAX_VALUE;
        for (int i = 0; i < sequence.size(); i++) {
            int c = sequence.get(i);
            int dep = order.dependency(c);
            if (dep != BuildOrder.NO_DEPENDENCY) {
                assertTrue(sequence.indexOf(dep) < i, "cell " + c + " placed before what it depends on");
            }
            if (order.cellBand(c) == 0) lastLowBand = i;
            else firstPower = Math.min(firstPower, i);
        }
        assertTrue(lastLowBand < firstPower, "power placed while low cells were missing");
        int tnt = cell(s, cells, 6, 1, 2);
        assertEquals(tnt, (int) sequence.get(sequence.size() - 1), "TNT goes last");
        int dust = sequence.indexOf(cell(s, cells, 6, 1, 0));
        assertTrue(sequence.indexOf(cell(s, cells, 5, 1, 0)) < dust, "torches before dust");
        assertTrue(dust < sequence.indexOf(cell(s, cells, 0, 1, 0)), "observers of changing blocks after dust");
    }

    private static double key(BuildOrder order, int c) {
        int o = order.order(c);
        return o >= RedstoneRules.POWER ? 1.0e6 + o * 100 + order.layerOf(c) : order.layerOf(c) * 100 + o;
    }

    private static boolean layerSettled(Schematic s, int[] cells, BuildOrder order, boolean[] placed, int layer, int band) {
        for (int c = 0; c < cells.length; c++) {
            if (order.layerOf(c) != layer || placed[c]) continue;
            if (!order.deferred(c, band, d -> placed[d])) return false;
        }
        return true;
    }

    @Test
    void ceilingLeverWaitsForTheLayerAbove() {
        Schematic s = machine();
        int[] cells = cells(s);
        BuildOrder order = BuildOrder.compute(s, cells);
        int lever = cell(s, cells, 2, 1, 2);
        assertTrue(order.deferred(lever, 2, d -> false));
        assertFalse(order.eligible(lever, 2, d -> false));
        assertTrue(order.eligible(lever, 2, d -> true));
        assertFalse(order.deferred(lever, 2, d -> true));
    }

    @Test
    void observersWatchingEachOtherDoNotDeadlock() {
        Builder b = new Builder(2, 1, 1);
        b.set(0, 0, 0, observer(Direction.EAST)).set(1, 0, 0, observer(Direction.WEST));
        Schematic s = b.build();
        BuildOrder order = BuildOrder.compute(s, cells(s));
        boolean one = order.dependency(0) == BuildOrder.NO_DEPENDENCY;
        boolean two = order.dependency(1) == BuildOrder.NO_DEPENDENCY;
        assertTrue(one ^ two, "exactly one edge of the loop is dropped");
    }

    @Test
    void sidewaysHoppersWaitForTheBlockTheyFeed() {
        BlockState east = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST);
        BlockState down = Blocks.HOPPER.defaultBlockState();
        Builder b = new Builder(3, 1, 1);
        b.set(0, 0, 0, east).set(1, 0, 0, east).set(2, 0, 0, down);
        Schematic s = b.build();
        BuildOrder order = BuildOrder.compute(s, cells(s));
        assertEquals(1, order.dependency(0));
        assertEquals(2, order.dependency(1));
        assertEquals(BuildOrder.NO_DEPENDENCY, order.dependency(2));
    }

    @Test
    void openBandWaitsForEveryLowerCell() {
        assertEquals(0, BuildOrder.openBand(new int[]{3, 2, 1}, 0));
        assertEquals(1, BuildOrder.openBand(new int[]{0, 2, 1}, 0));
        assertEquals(2, BuildOrder.openBand(new int[]{0, 0, 1}, 0));
        assertEquals(2, BuildOrder.openBand(new int[]{0, 0, 0}, 0));
        assertEquals(1, BuildOrder.openBand(new int[]{4, 2, 1}, 1));
    }
}
