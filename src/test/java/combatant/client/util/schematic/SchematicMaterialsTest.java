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
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchematicMaterialsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockState BED = BuiltInRegistries.BLOCK
            .getValue(Identifier.withDefaultNamespace("red_bed")).defaultBlockState();

    private record Row(BlockState state, Item item, int count) {
    }

    @Test
    void blockToItemAndCountTable() {
        Row[] table = {
                new Row(Blocks.REDSTONE_WIRE.defaultBlockState(), Items.REDSTONE, 1),
                new Row(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST), Items.TORCH, 1),
                new Row(Blocks.REDSTONE_WALL_TORCH.defaultBlockState(), Items.REDSTONE_TORCH, 1),
                new Row(Blocks.REDSTONE_TORCH.defaultBlockState(), Items.REDSTONE_TORCH, 1),
                new Row(Blocks.REPEATER.defaultBlockState(), Items.REPEATER, 1),
                new Row(Blocks.COMPARATOR.defaultBlockState(), Items.COMPARATOR, 1),
                new Row(Blocks.STICKY_PISTON.defaultBlockState(), Items.STICKY_PISTON, 1),
                new Row(Blocks.OBSERVER.defaultBlockState(), Items.OBSERVER, 1),
                new Row(Blocks.TRIPWIRE.defaultBlockState(), Items.STRING, 1),
                new Row(Blocks.TRIPWIRE_HOOK.defaultBlockState(), Items.TRIPWIRE_HOOK, 1),
                new Row(Blocks.POWERED_RAIL.defaultBlockState(), Items.POWERED_RAIL, 1),
                new Row(Blocks.PISTON_HEAD.defaultBlockState(), Items.AIR, 0),
                new Row(Blocks.MOVING_PISTON.defaultBlockState(), Items.AIR, 0),
                new Row(Blocks.WATER.defaultBlockState(), Items.AIR, 0),
                new Row(Blocks.LAVA.defaultBlockState(), Items.AIR, 0),
                new Row(Blocks.BUBBLE_COLUMN.defaultBlockState(), Items.AIR, 0),
                new Row(Blocks.WATER_CAULDRON.defaultBlockState(), Items.CAULDRON, 1),
                new Row(Blocks.LAVA_CAULDRON.defaultBlockState(), Items.CAULDRON, 1),
                new Row(Blocks.POWDER_SNOW_CAULDRON.defaultBlockState(), Items.CAULDRON, 1),
                new Row(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Items.OAK_DOOR, 0),
                new Row(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Items.OAK_DOOR, 1),
                new Row(BED.setValue(BedBlock.PART, BedPart.HEAD), BED.getBlock().asItem(), 0),
                new Row(BED.setValue(BedBlock.PART, BedPart.FOOT), BED.getBlock().asItem(), 1),
                new Row(Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Items.TALL_GRASS, 0),
                new Row(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), Items.OAK_SLAB, 2),
                new Row(Blocks.OAK_SLAB.defaultBlockState(), Items.OAK_SLAB, 1),
                new Row(Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3), Items.CANDLE, 3),
                new Row(Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 4), Items.SEA_PICKLE, 4),
                new Row(Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 2), Items.TURTLE_EGG, 2),
                new Row(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 5), Items.SNOW, 5),
                new Row(Blocks.PINK_PETALS.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 3), Items.PINK_PETALS, 3),
                new Row(Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.NORTH, true)
                        .setValue(BlockStateProperties.DOWN, true), Items.GLOW_LICHEN, 2),
                new Row(Blocks.TNT.defaultBlockState(), Items.TNT, 1),
                new Row(Blocks.AIR.defaultBlockState(), Items.AIR, 0)
        };
        assertEquals("minecraft:red_bed", BuiltInRegistries.ITEM.getKey(BED.getBlock().asItem()).toString());
        for (Row row : table) {
            assertEquals(row.item, SchematicMaterials.itemFor(row.state), "item for " + row.state);
            assertEquals(row.count, SchematicMaterials.itemsPerCell(row.state), "count for " + row.state);
        }
    }
}
