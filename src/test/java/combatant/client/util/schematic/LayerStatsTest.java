/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LayerStatsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void countsBlocksPerLayerAndPerPaletteEntry() {
        BlockState doubleSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        BlockState doorLower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState doorUpper = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        BlockState[] palette = {Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), doubleSlab, doorLower, doorUpper};
        // 2x3x2 (x, y, z): layer 0 = 3 stone + air, layer 1 = slab + door bottom, layer 2 = door top.
        short[] blocks = {
                1, 1, 1, 0,
                2, 3, 0, 0,
                0, 4, 0, 0
        };
        Schematic schematic = new Schematic("t", "t", "", "", 2, 3, 2, palette,
                new String[]{"air", "stone", "slab", "door_l", "door_u"}, blocks, 6, 1, List.of());

        LayerStats stats = LayerStats.compute(schematic);
        assertEquals(3, stats.layerCount());
        assertEquals(3, stats.blocksInLayer(0));
        assertEquals(2, stats.blocksInLayer(1));
        assertEquals(1, stats.blocksInLayer(2));
        assertEquals(3, stats.maxLayerBlocks());
        assertArrayEquals(new int[]{0, 3, 1, 1, 1}, stats.paletteTotals());
        assertArrayEquals(new int[]{0, 0, 1, 1, 0}, stats.layerHistogram(1));

        List<SchematicMaterials.Entry> total = SchematicMaterials.materials(schematic, stats.paletteTotals());
        assertEquals(3, total.size());
        assertEquals(new SchematicMaterials.Entry(Items.STONE, 3), total.get(0));
        // Double slab costs two slabs, the door's upper half costs nothing.
        assertEquals(2, count(total, Items.OAK_SLAB.asItem()));
        assertEquals(1, count(total, Items.OAK_DOOR.asItem()));
    }

    @Test
    void classifiesWorldStatesAgainstTheExpectedState() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertEquals(SchematicDiff.CORRECT, SchematicDiff.classify(stone, stone));
        assertEquals(SchematicDiff.MISSING, SchematicDiff.classify(Blocks.AIR.defaultBlockState(), stone));
        assertEquals(SchematicDiff.MISSING, SchematicDiff.classify(Blocks.WATER.defaultBlockState(), stone));
        assertEquals(SchematicDiff.WRONG, SchematicDiff.classify(Blocks.DIRT.defaultBlockState(), stone));
    }

    private static int count(List<SchematicMaterials.Entry> entries, net.minecraft.world.item.Item item) {
        for (SchematicMaterials.Entry entry : entries) {
            if (entry.item() == item) return entry.count();
        }
        return 0;
    }
}
