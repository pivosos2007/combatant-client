package combatant.client.features.module.modules.player.printer;

import combatant.client.features.module.modules.player.printer.PlacementOrientation.Requirement;
import combatant.client.features.module.modules.player.printer.PlacementOrientation.Skip;
import combatant.client.features.module.modules.player.printer.PlacementOrientation.VerticalHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlacementOrientationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void secondHalvesAreSkipped() {
        BlockState upperDoor = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        assertEquals(Skip.UPPER_HALF, PlacementOrientation.analyze(upperDoor).skip());
        assertTrue(PlacementOrientation.analyze(Blocks.OAK_DOOR.defaultBlockState()).placeable());
        BlockState head = Blocks.STRAW_BED.defaultBlockState().setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
        assertEquals(Skip.BED_HEAD, PlacementOrientation.analyze(head).skip());
        assertTrue(PlacementOrientation.analyze(Blocks.STRAW_BED.defaultBlockState()).placeable());
    }

    @Test
    void unproducibleStatesAreReported() {
        assertEquals(Skip.DOUBLE_SLAB, PlacementOrientation.analyze(
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)).skip());
        assertEquals(Skip.STACKED, PlacementOrientation.analyze(
                Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3)).skip());
        assertEquals(Skip.NONE, PlacementOrientation.analyze(Blocks.CANDLE.defaultBlockState()).skip());
        assertEquals(Skip.DERIVED, PlacementOrientation.analyze(Blocks.PISTON_HEAD.defaultBlockState()).skip());
        assertEquals(Skip.DERIVED, PlacementOrientation.analyze(Blocks.WATER.defaultBlockState()).skip());
    }

    @Test
    void slabHalfRestrictsFaceAndHeight() {
        Requirement top = PlacementOrientation.analyze(
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        assertEquals(VerticalHalf.TOP, top.half());
        assertTrue(top.allows(Direction.DOWN, 0.0));
        assertFalse(top.allows(Direction.UP, 0.9));
        assertTrue(top.allows(Direction.NORTH, 0.75));
        assertFalse(top.allows(Direction.NORTH, 0.25));

        Requirement bottom = PlacementOrientation.analyze(Blocks.STONE_SLAB.defaultBlockState());
        assertTrue(bottom.allows(Direction.UP, 0.0));
        assertFalse(bottom.allows(Direction.DOWN, 0.1));
        assertTrue(bottom.allows(Direction.EAST, 0.25));
        assertFalse(bottom.allows(Direction.EAST, 0.75));
    }

    @Test
    void stairsAndTrapdoorsFollowTheirHalfAndNeedRotation() {
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
        Requirement need = PlacementOrientation.analyze(stair);
        assertEquals(VerticalHalf.TOP, need.half());
        assertTrue(need.rotationDecides());
        assertEquals(VerticalHalf.BOTTOM,
                PlacementOrientation.analyze(Blocks.OAK_TRAPDOOR.defaultBlockState()).half());
    }

    @Test
    void clickFaceRules() {
        Requirement sidewaysHopper = PlacementOrientation.analyze(
                Blocks.HOPPER.defaultBlockState().setValue(BlockStateProperties.FACING_HOPPER, Direction.EAST));
        assertEquals(Direction.WEST, sidewaysHopper.clickFace());
        assertFalse(sidewaysHopper.allows(Direction.EAST, 0.5));
        Requirement downHopper = PlacementOrientation.analyze(Blocks.HOPPER.defaultBlockState());
        assertEquals(Direction.Axis.Y, downHopper.clickAxis());
        assertTrue(downHopper.allows(Direction.UP, 0.5));
        assertFalse(downHopper.allows(Direction.NORTH, 0.5));

        Requirement log = PlacementOrientation.analyze(
                Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        assertEquals(Direction.Axis.X, log.clickAxis());
        assertTrue(log.allows(Direction.WEST, 0.5));
        assertFalse(log.allows(Direction.UP, 0.5));
        assertFalse(log.rotationDecides());

        Requirement rod = PlacementOrientation.analyze(
                Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        assertEquals(Direction.NORTH, rod.clickFace());
    }

    @Test
    void lookDependentBlocksNeedRotation() {
        assertTrue(PlacementOrientation.rotationDecides(Blocks.REPEATER.defaultBlockState()));
        assertTrue(PlacementOrientation.rotationDecides(Blocks.COMPARATOR.defaultBlockState()));
        assertTrue(PlacementOrientation.rotationDecides(Blocks.LEVER.defaultBlockState()));
        assertTrue(PlacementOrientation.rotationDecides(Blocks.STONE_BUTTON.defaultBlockState()));
        assertTrue(PlacementOrientation.rotationDecides(Blocks.WALL_TORCH.defaultBlockState()));
        assertTrue(PlacementOrientation.rotationDecides(Blocks.PISTON.defaultBlockState()));
        assertTrue(PlacementOrientation.rotationDecides(Blocks.RAIL.defaultBlockState()) == false);
        assertFalse(PlacementOrientation.rotationDecides(Blocks.STONE.defaultBlockState()));
        assertTrue(PlacementOrientation.analyze(Blocks.STONE.defaultBlockState()).allows(Direction.UP, 0.5));
    }

    @Test
    void ordersLowestLayerThenNearestAndBatchesByKey() {
        List<BlockPos> cells = new ArrayList<>(List.of(
                new BlockPos(0, 5, 0), new BlockPos(9, 4, 0), new BlockPos(1, 4, 0), new BlockPos(1, 4, 0)));
        PrinterTargets.order(cells, c -> c, 0, 4, 0);
        assertEquals(List.of(new BlockPos(1, 4, 0), new BlockPos(1, 4, 0), new BlockPos(9, 4, 0),
                new BlockPos(0, 5, 0)), cells);

        List<String> items = List.of("stone", "stone", "glass", "stone");
        assertEquals(List.of("stone", "stone"), PrinterTargets.batch(items, s -> s, 2));
        assertEquals(List.of("stone", "stone", "stone"), PrinterTargets.batch(items, s -> s, 10));
        assertTrue(PrinterTargets.batch(List.<String>of(), s -> s, 3).isEmpty());
    }
}
