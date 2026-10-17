/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicFilesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void extensionsAreMatchedCaseInsensitivelyAndStripped() {
        assertEquals(".schem", SchematicFiles.extensionOf("House.SCHEM"));
        assertEquals(".nbt", SchematicFiles.extensionOf("a.nbt"));
        assertEquals(".litematic", SchematicFiles.extensionOf("x.litematic"));
        assertNull(SchematicFiles.extensionOf("notes.txt"));
        assertEquals("House", SchematicFiles.baseName("House.SCHEM"));
        assertEquals("notes.txt", SchematicFiles.baseName("notes.txt"));
        assertEquals("schem", SchematicFiles.formatLabel("House.schem"));
        assertEquals("", SchematicFiles.formatLabel("notes.txt"));
    }

    @Test
    void parsesPropertiesOutOfASpongePaletteKey() {
        SpongeSchemReader.ParsedState plain = SpongeSchemReader.parseState("minecraft:stone");
        assertEquals("minecraft:stone", plain.name());
        assertTrue(plain.properties().isEmpty());
        SpongeSchemReader.ParsedState stairs = SpongeSchemReader.parseState("minecraft:oak_stairs[facing=east, half=top]");
        assertEquals("minecraft:oak_stairs", stairs.name());
        assertEquals("east", stairs.properties().get("facing"));
        assertEquals("top", stairs.properties().get("half"));
    }

    @Test
    void readsASpongeV2SchematicWithMultiByteVarInts(@TempDir Path dir) throws Exception {
        // 2x1x3 cells: stone, air, stone, stone, oak_stairs, stone. A palette index of 200 forces a two byte var-int.
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putShort("Width", (short) 2);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 3);
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:stone", 1);
        palette.putInt("minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]", 200);
        root.put("Palette", palette);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int value : new int[]{1, 0, 1, 1, 200, 1}) writeVarInt(data, value);
        root.put("BlockData", new ByteArrayTag(data.toByteArray()));
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", "Mini");
        meta.putString("Author", "tester");
        root.put("Metadata", meta);

        Path file = dir.resolve("mini.schem");
        NbtIo.writeCompressed(root, file);

        Schematic schematic = SchematicFiles.read(file);
        assertEquals("Mini", schematic.name());
        assertEquals("mini", schematic.fileName());
        assertEquals("tester", schematic.author());
        assertEquals(2, schematic.sizeX());
        assertEquals(1, schematic.sizeY());
        assertEquals(3, schematic.sizeZ());
        assertEquals(5, schematic.totalBlocks());
        assertSame(Blocks.STONE, schematic.paletteState(schematic.paletteIndexAt(0)).getBlock());
        assertEquals(0, schematic.paletteIndexAt(1));
        assertSame(Blocks.OAK_STAIRS, schematic.paletteState(schematic.paletteIndexAt(4)).getBlock());

        SchematicHeader header = SchematicFiles.readHeader(file, 9L);
        assertEquals("2x1x3", header.dimensions());
        assertEquals(5, header.totalBlocks());
        assertEquals(9L, header.lastModified());
    }

    @Test
    void readsASpongeV3SchematicNestedUnderSchematic() throws Exception {
        CompoundTag body = new CompoundTag();
        body.putInt("Version", 3);
        body.putShort("Width", (short) 1);
        body.putShort("Height", (short) 2);
        body.putShort("Length", (short) 1);
        CompoundTag blocks = new CompoundTag();
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:glass", 1);
        blocks.put("Palette", palette);
        blocks.put("Data", new ByteArrayTag(new byte[]{1, 1}));
        body.put("Blocks", blocks);
        CompoundTag root = new CompoundTag();
        root.put("Schematic", body);

        Schematic schematic = SchematicFiles.parse(root, "tower", LitematicReader::resolveState);
        assertEquals(2, schematic.totalBlocks());
        assertEquals("tower", schematic.name());
        assertSame(Blocks.GLASS, schematic.paletteState(schematic.paletteIndexAt(1)).getBlock());
    }

    @Test
    void truncatedSpongeBlockDataIsAnError() {
        CompoundTag root = new CompoundTag();
        root.putShort("Width", (short) 2);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 2);
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:stone", 1);
        root.put("Palette", palette);
        root.put("BlockData", new ByteArrayTag(new byte[]{1, 1}));
        assertThrows(java.io.IOException.class, () -> SchematicFiles.parse(root, "bad", LitematicReader::resolveState));
    }

    @Test
    void overflowingSpongeVarIntIsAnIoErrorNotAnArrayIndexError() {
        CompoundTag root = new CompoundTag();
        root.putShort("Width", (short) 1);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 1);
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:stone", 0);
        root.put("Palette", palette);
        root.put("BlockData", new ByteArrayTag(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F}));
        java.io.IOException e = assertThrows(java.io.IOException.class,
                () -> SchematicFiles.parse(root, "bad", LitematicReader::resolveState));
        assertTrue(e.getMessage().contains("negative palette index"), e.getMessage());
    }

    @Test
    void readsAVanillaStructureAndSkipsStructureVoid() throws Exception {
        CompoundTag root = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(1));
        root.put("size", size);
        ListTag palette = new ListTag();
        palette.add(named("minecraft:stone"));
        palette.add(named("minecraft:structure_void"));
        palette.add(named("minecraft:glass"));
        root.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(block(0, 0, 0, 0));
        blocks.add(block(1, 0, 0, 1));
        blocks.add(block(0, 1, 0, 2));
        blocks.add(block(5, 5, 5, 0));
        root.put("blocks", blocks);

        Schematic schematic = SchematicFiles.parse(root, "vanilla", LitematicReader::resolveState);
        assertEquals(2, schematic.totalBlocks());
        assertEquals(2, schematic.sizeX());
        assertEquals(2, schematic.sizeY());
        assertEquals(1, schematic.sizeZ());
        assertSame(Blocks.STONE, schematic.paletteState(schematic.paletteIndexAt(0)).getBlock());
        assertEquals(0, schematic.paletteIndexAt(1));
        assertSame(Blocks.GLASS, schematic.paletteState(schematic.paletteIndexAt(2)).getBlock());
        assertEquals(1, schematic.warnings().size());
    }

    @Test
    void unknownLayoutIsRejected() {
        assertThrows(java.io.IOException.class, () -> SchematicFiles.parse(new CompoundTag(), "x", LitematicReader::resolveState));
    }

    private static CompoundTag named(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    private static CompoundTag block(int x, int y, int z, int state) {
        CompoundTag tag = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        tag.put("pos", pos);
        tag.putInt("state", state);
        return tag;
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}
