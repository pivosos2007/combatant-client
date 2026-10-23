/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the builder's pure logic over the real schematics in {@code run/schematics}. */
class RealSchematicsTest {

    private static final Path DIRECTORY = Path.of("run", "schematics");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<Path> files() throws IOException {
        if (!Files.isDirectory(DIRECTORY)) return List.of();
        try (Stream<Path> stream = Files.list(DIRECTORY)) {
            return stream.filter(path -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.endsWith(".schem") || name.endsWith(".litematic");
            }).sorted().toList();
        }
    }

    private static int[] cells(Schematic schematic) {
        int[] all = new int[schematic.volume()];
        int count = 0;
        for (int i = 0; i < all.length; i++) {
            if (!schematic.paletteState(schematic.paletteIndexAt(i)).isAir()) all[count++] = i;
        }
        return java.util.Arrays.copyOf(all, count);
    }

    private static BlockState state(Schematic schematic, int[] cells, int cell) {
        return schematic.paletteState(schematic.paletteIndexAt(cells[cell]));
    }

    @Test
    void everySchematicLoads() throws IOException {
        List<Path> files = files();
        assertTrue(!files.isEmpty(), "no schematics in " + DIRECTORY.toAbsolutePath());
        for (Path path : files) {
            Schematic schematic = SchematicFormats.read(path);
            assertNotNull(schematic, path.toString());
            assertTrue(schematic.totalBlocks() > 0, path + " is empty");
            assertEquals(schematic.totalBlocks(), cells(schematic).length, path + " block count");
        }
    }

    @Test
    void solidBlocksAndContainersComeBeforeWireRepeatersObserversAndKelp() throws IOException {
        for (Path path : files()) {
            Schematic schematic = SchematicFormats.read(path);
            int[] cells = cells(schematic);
            BuildOrder order = BuildOrder.compute(schematic, cells);
            assertEquals(cells.length, order.cellCount());

            int maxSolid = Integer.MIN_VALUE;
            int minLate = Integer.MAX_VALUE;
            for (int cell = 0; cell < cells.length; cell++) {
                BlockState state = state(schematic, cells, cell);
                Block block = state.getBlock();
                int value = order.order(cell);
                boolean late = block instanceof RedstoneWireBlock || block instanceof RepeaterBlock
                        || block instanceof ObserverBlock || block instanceof ComparatorBlock
                        || BuiltInRegistries.BLOCK.getKey(block).getPath().contains("kelp");
                if (block instanceof HopperBlock || block instanceof PistonBaseBlock) {
                    // Mechanisms sit above plain support blocks and below the power band.
                    assertTrue(value >= RedstoneRules.MECHANISM, path + " mechanism order " + value);
                }
                if (!late && RedstoneRules.baseOrder(state) == RedstoneRules.SUPPORT && order.dependency(cell) == BuildOrder.NO_DEPENDENCY) {
                    maxSolid = Math.max(maxSolid, value);
                }
                if (block instanceof RedstoneWireBlock) {
                    assertTrue(order.cellBand(cell) >= 1, path + " wire must be in the power band");
                    minLate = Math.min(minLate, value);
                }
                if (block instanceof RepeaterBlock || block instanceof ComparatorBlock) {
                    assertTrue(value >= RedstoneRules.ATTACHED, path + " diode order " + value);
                }
                if (block instanceof ObserverBlock) {
                    assertTrue(value >= RedstoneRules.MECHANISM, path + " observer order " + value);
                }
            }
            if (maxSolid != Integer.MIN_VALUE && minLate != Integer.MAX_VALUE) {
                assertTrue(maxSolid < minLate, path + ": solid blocks must precede wire");
            }
            // An independent cell is never gated later than the cell it depends on.
            for (int cell = 0; cell < cells.length; cell++) {
                int dep = order.dependency(cell);
                if (dep != BuildOrder.NO_DEPENDENCY) {
                    assertTrue(order.order(cell) >= order.order(dep), path + " dependency order");
                }
            }
        }
    }

    @Test
    void stateAdjustmentsAndRedstoneRulesHoldForRealComponents() throws IOException {
        List<String> seen = new ArrayList<>();
        for (Path path : files()) {
            Schematic schematic = SchematicFormats.read(path);
            int[] cells = cells(schematic);
            for (int cell = 0; cell < cells.length; cell++) {
                BlockState state = state(schematic, cells, cell);
                Block block = state.getBlock();
                // Identity needs no clicks, and adjusting never claims a different block is fixable.
                assertEquals(0, StateAdjustments.clicksNeeded(state, state), path.toString());

                if (block instanceof RepeaterBlock) {
                    seen.add("repeater");
                    assertEquals(RepeaterBlock.DELAY, StateAdjustments.adjustableProperty(state));
                    int delay = state.getValue(RepeaterBlock.DELAY);
                    for (int other = 1; other <= 4; other++) {
                        BlockState actual = state.setValue(RepeaterBlock.DELAY, other);
                        assertEquals(Math.floorMod(delay - other, 4), StateAdjustments.clicksNeeded(actual, state));
                    }
                    assertEquals(state.getValue(RepeaterBlock.FACING).getOpposite(), RedstoneRules.requiredYaw(state));
                    assertEquals(Direction.DOWN, RedstoneRules.supportDirection(state));
                    assertEquals(RedstoneRules.ATTACHED, RedstoneRules.baseOrder(state));
                } else if (block instanceof ComparatorBlock) {
                    seen.add("comparator");
                    assertEquals(ComparatorBlock.MODE, StateAdjustments.adjustableProperty(state));
                    BlockState flipped = state.cycle(ComparatorBlock.MODE);
                    assertEquals(1, StateAdjustments.clicksNeeded(flipped, state));
                    assertEquals(state.getValue(ComparatorBlock.FACING).getOpposite(), RedstoneRules.requiredYaw(state));
                    assertEquals(Direction.DOWN, RedstoneRules.supportDirection(state));
                } else if (block instanceof ObserverBlock) {
                    seen.add("observer");
                    Direction facing = state.getValue(ObserverBlock.FACING);
                    assertEquals(facing, RedstoneRules.observedDirection(state));
                    assertEquals(facing, RedstoneRules.requiredLook(state));
                    assertEquals(RedstoneRules.MECHANISM, RedstoneRules.baseOrder(state));
                    assertTrue(RedstoneRules.changesWithPower(state));
                    assertEquals(StateAdjustments.IMPOSSIBLE,
                            StateAdjustments.clicksNeeded(state.setValue(ObserverBlock.FACING, facing.getOpposite()), state));
                } else if (block instanceof PistonBaseBlock) {
                    seen.add("piston");
                    Direction facing = state.getValue(PistonBaseBlock.FACING);
                    assertEquals(facing.getOpposite(), RedstoneRules.requiredLook(state));
                    assertEquals(RedstoneRules.MECHANISM, RedstoneRules.baseOrder(state));
                    assertTrue(RedstoneRules.reactsToPower(state));
                    assertTrue(RedstoneRules.quasiConnected(state));
                    assertEquals(null, StateAdjustments.adjustableProperty(state));
                } else if (block instanceof HopperBlock) {
                    seen.add("hopper");
                    Direction facing = state.getValue(HopperBlock.FACING);
                    Direction feeds = RedstoneRules.feedsInto(state);
                    assertEquals(facing == Direction.DOWN ? null : facing, feeds);
                    assertEquals(feeds, RedstoneRules.requiredYaw(state));
                    assertEquals(RedstoneRules.MECHANISM, RedstoneRules.baseOrder(state));
                    assertNotEquals(null, state.getBlock());
                }
            }
        }
        assertTrue(!seen.isEmpty(), "the schematics contain no redstone components at all");
    }
}
