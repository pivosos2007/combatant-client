/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.StairBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpongeSchematicReaderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** 2 x 1 x 2: stone, air / stairs (multi-byte palette id), stone. Palette ids above 127 need a two-byte var-int. */
    private static byte[] blockData(int stairsId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(1);               // (0,0,0) stone
        out.write(0);               // (1,0,0) air
        writeVarInt(out, stairsId); // (0,0,1) stairs
        out.write(1);               // (1,0,1) stone
        return out.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static CompoundTag palette(int stairsId) {
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:stone", 1);
        palette.putInt("minecraft:oak_stairs[facing=south,half=top]", stairsId);
        return palette;
    }

    @Test
    void readsVersion2WithMultiByteVarInts(@TempDir Path dir) throws Exception {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putShort("Width", (short) 2);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 2);
        root.put("Palette", palette(300));
        root.putByteArray("BlockData", blockData(300));
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", "Test");
        meta.putString("Author", "me");
        root.put("Metadata", meta);
        Path file = dir.resolve("farm.schem");
        NbtIo.writeCompressed(root, file);

        Schematic schematic = SchematicFormats.read(file);
        assertEquals("Test", schematic.name());
        assertEquals("farm", schematic.fileName());
        assertEquals(2, schematic.sizeX());
        assertEquals(2, schematic.sizeZ());
        assertEquals(3, schematic.totalBlocks());
        assertSame(Blocks.STONE, schematic.paletteState(schematic.paletteIndex(0, 0, 0)).getBlock());
        assertEquals(0, schematic.paletteIndex(1, 0, 0));
        var stairs = schematic.paletteState(schematic.paletteIndex(0, 0, 1));
        assertSame(Blocks.OAK_STAIRS, stairs.getBlock());
        assertEquals(Half.TOP, stairs.getValue(StairBlock.HALF));
    }

    @Test
    void readsVersion3UnderTheSchematicCompound(@TempDir Path dir) throws Exception {
        CompoundTag blocks = new CompoundTag();
        blocks.put("Palette", palette(2));
        blocks.putByteArray("Data", blockData(2));
        CompoundTag inner = new CompoundTag();
        inner.putInt("Version", 3);
        inner.putShort("Width", (short) 2);
        inner.putShort("Height", (short) 1);
        inner.putShort("Length", (short) 2);
        inner.put("Blocks", blocks);
        CompoundTag root = new CompoundTag();
        root.put("Schematic", inner);
        Path file = dir.resolve("v3.schem");
        NbtIo.writeCompressed(root, file);

        Schematic schematic = SchematicFormats.read(file);
        assertEquals(3, schematic.totalBlocks());
        assertSame(Blocks.OAK_STAIRS, schematic.paletteState(schematic.paletteIndex(0, 0, 1)).getBlock());
    }

    @Test
    void truncatedBlockDataIsAnError(@TempDir Path dir) throws Exception {
        CompoundTag root = new CompoundTag();
        root.putShort("Width", (short) 4);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 4);
        root.put("Palette", palette(2));
        root.putByteArray("BlockData", new byte[]{1, 1});
        Path file = dir.resolve("short.schem");
        NbtIo.writeCompressed(root, file);
        assertThrows(java.io.IOException.class, () -> SchematicFormats.read(file));
    }

    @Test
    void readsVanillaStructureFiles(@TempDir Path dir) throws Exception {
        CompoundTag root = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(1));
        root.put("size", size);
        ListTag palette = new ListTag();
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        root.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.putInt("state", 0);
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(1));
        pos.add(IntTag.valueOf(0));
        pos.add(IntTag.valueOf(0));
        block.put("pos", pos);
        blocks.add(block);
        root.put("blocks", blocks);
        Path file = dir.resolve("tiny.nbt");
        NbtIo.writeCompressed(root, file);

        Schematic schematic = SchematicFormats.read(file);
        assertEquals(1, schematic.totalBlocks());
        assertEquals(0, schematic.paletteIndex(0, 0, 0));
        assertSame(Blocks.STONE, schematic.paletteState(schematic.paletteIndex(1, 0, 0)).getBlock());
    }
}
