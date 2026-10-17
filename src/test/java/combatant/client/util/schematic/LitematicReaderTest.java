/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LitematicReaderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bitsPerEntryHasAFloorOfTwo() {
        assertEquals(2, LitematicReader.bitsFor(1));
        assertEquals(2, LitematicReader.bitsFor(2));
        assertEquals(2, LitematicReader.bitsFor(4));
        assertEquals(3, LitematicReader.bitsFor(5));
        assertEquals(4, LitematicReader.bitsFor(16));
        assertEquals(5, LitematicReader.bitsFor(17));
        assertEquals(10, LitematicReader.bitsFor(1000));
    }

    @Test
    void packAndUnpackRoundTripForNonPowerOfTwoPalettes() {
        Random random = new Random(1234L);
        for (int paletteSize : new int[]{3, 5, 7, 17, 33, 100, 1000, 5000}) {
            int bits = LitematicReader.bitsFor(paletteSize);
            // Odd lengths make the last entries straddle the final long boundary too.
            int count = 64 * 3 + 13;
            int[] values = new int[count];
            for (int i = 0; i < count; i++) values[i] = random.nextInt(paletteSize);
            long[] packed = LitematicReader.pack(values, bits);
            assertEquals((count * (long) bits + 63) / 64, packed.length);
            assertArrayEquals(values, LitematicReader.unpack(packed, bits, count), "palette " + paletteSize);
        }
    }

    @Test
    void entriesSpanLongBoundariesInsteadOfPadding() {
        // 5 bits: entry 12 starts at bit 60, so its low four bits sit in long 0 and its top bit in long 1.
        long[] data = new long[2];
        data[0] = 0b0110L << 60;
        data[1] = 0b1L;
        assertEquals(0b10110L, LitematicReader.unpackAt(data, 5, 0b11111L, 12));

        int[] values = new int[13];
        values[12] = 0b10110;
        long[] packed = LitematicReader.pack(values, 5);
        assertEquals(2, packed.length);
        assertEquals(data[0], packed[0]);
        assertEquals(data[1], packed[1]);
    }

    @Test
    void negativeSizesGrowTowardsNegativeCoordinates() {
        assertEquals(3, LitematicReader.normalizedMin(5, -3));
        assertEquals(5, LitematicReader.normalizedMin(5, 3));
        assertEquals(-1, LitematicReader.normalizedMin(0, -2));
    }

    @Test
    void readsASyntheticLitematicWithTwoRegions(@TempDir Path dir) throws Exception {
        CompoundTag root = new CompoundTag();
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", "Test House");
        meta.putString("Author", "LO");
        root.put("Metadata", meta);
        root.putInt("Version", 7);

        CompoundTag regions = new CompoundTag();
        // Region A: 3x2x2 stone floor + an upside-down east stair at local (2,1,1).
        int[] a = new int[3 * 2 * 2];
        for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) a[index(x, 0, z, 3, 2)] = 1;
        a[index(2, 1, 1, 3, 2)] = 2;
        regions.put("A", region(0, 0, 0, 3, 2, 2, a,
                entry("minecraft:air", Map.of()),
                entry("minecraft:stone", Map.of()),
                entry("minecraft:oak_stairs", Map.of("facing", "east", "half", "top", "shape", "straight",
                        "waterlogged", "false"))));
        // Region B has a negative size: Position (4,1,1) with Size (-2,-1,-2) covers x 3..4, y 1, z 0..1.
        int[] b = new int[2 * 2];
        b[index(0, 0, 0, 2, 2)] = 1;
        b[index(1, 0, 1, 2, 2)] = 2;
        regions.put("B", region(4, 1, 1, -2, -1, -2, b,
                entry("minecraft:air", Map.of()),
                entry("minecraft:glass", Map.of()),
                entry("minecraft:definitely_not_a_block", Map.of())));
        root.put("Regions", regions);

        Path file = dir.resolve("house.litematic");
        NbtIo.writeCompressed(root, file);

        Schematic schematic = LitematicReader.read(file);
        assertEquals("Test House", schematic.name());
        assertEquals("house", schematic.fileName());
        assertEquals("LO", schematic.author());
        assertEquals(5, schematic.sizeX());
        assertEquals(2, schematic.sizeY());
        assertEquals(2, schematic.sizeZ());
        assertEquals(2, schematic.regionCount());
        // 6 stone + 1 stair + 1 glass; the unknown block became air.
        assertEquals(8, schematic.totalBlocks());
        assertFalse(schematic.warnings().isEmpty());

        assertSame(Blocks.STONE.defaultBlockState(), schematic.state(1, 0, 1));
        BlockState stair = schematic.state(2, 1, 1);
        assertSame(Blocks.OAK_STAIRS, stair.getBlock());
        assertEquals(Direction.EAST, stair.getValue(StairBlock.FACING));
        assertEquals(Half.TOP, stair.getValue(StairBlock.HALF));
        assertSame(Blocks.GLASS.defaultBlockState(), schematic.state(3, 1, 0));
        assertTrue(schematic.state(4, 1, 1).isAir());
        assertTrue(schematic.state(4, 0, 0).isAir());

        SchematicHeader header = LitematicReader.readHeader(file, 7L);
        assertEquals("Test House", header.name());
        assertEquals("5x2x2", header.dimensions());
        assertEquals(2, header.regionCount());
    }

    @Test
    void truncatedBlockStateArrayIsRejected() {
        CompoundTag root = new CompoundTag();
        CompoundTag regions = new CompoundTag();
        CompoundTag region = region(0, 0, 0, 8, 8, 8, new int[8 * 8 * 8],
                entry("minecraft:air", Map.of()), entry("minecraft:stone", Map.of()));
        region.putLongArray("BlockStates", new long[1]);
        regions.put("R", region);
        root.put("Regions", regions);
        assertThrows(java.io.IOException.class,
                () -> LitematicReader.parse(root, "broken", LitematicReader::resolveState));
    }

    @Test
    void hugeDeclaredLongArrayFailsCleanlyInsteadOfAllocating(@TempDir Path dir) throws Exception {
        // ~100 bytes on disk that claim a 16 GB BlockStates array; the accounter must refuse before allocating.
        Path file = hostileArray(dir.resolve("bomb.litematic"), Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> LitematicReader.read(file));
        headerSurvives(file);
    }

    @Test
    void negativeArrayLengthIsAMalformedFileNotACrash(@TempDir Path dir) throws Exception {
        Path file = hostileArray(dir.resolve("negative.litematic"), Integer.MIN_VALUE);
        assertThrows(IOException.class, () -> LitematicReader.read(file));
        headerSurvives(file);
    }

    @Test
    void headerSkipsBlockArraysLargerThanItsOwnQuota(@TempDir Path dir) throws Exception {
        CompoundTag root = new CompoundTag();
        CompoundTag regions = new CompoundTag();
        CompoundTag region = region(-3, 0, 2, 4, 5, 6, new int[4 * 5 * 6],
                entry("minecraft:air", Map.of()), entry("minecraft:stone", Map.of()));
        // 24 MB of longs: more than the header quota, so this only passes if the array is skipped, not collected.
        region.putLongArray("BlockStates", new long[3 * 1024 * 1024]);
        regions.put("Big", region);
        root.put("Regions", regions);
        Path file = dir.resolve("big.litematic");
        NbtIo.writeCompressed(root, file);

        SchematicHeader header = LitematicReader.readHeader(file, 1L);
        assertFalse(header.failed());
        assertEquals("4x5x6", header.dimensions());
        assertEquals(1, header.regionCount());
    }

    @Test
    void regionsFarApartAreRejectedWithoutIntOverflow() {
        CompoundTag root = new CompoundTag();
        CompoundTag regions = new CompoundTag();
        regions.put("East", region(2_000_000_000, 0, 0, 1, 1, 1, new int[1],
                entry("minecraft:air", Map.of()), entry("minecraft:stone", Map.of())));
        regions.put("West", region(-2_000_000_000, 0, 0, 1, 1, 1, new int[1],
                entry("minecraft:air", Map.of()), entry("minecraft:stone", Map.of())));
        root.put("Regions", regions);
        IOException error = assertThrows(IOException.class,
                () -> LitematicReader.parse(root, "far", LitematicReader::resolveState));
        assertTrue(error.getMessage().contains("too large"), error.getMessage());
    }

    @Test
    void regionAtTheIntEdgeStillParses() throws Exception {
        // max = position + size - 1 overflows int here; long box math keeps it a plain 2x1x1 build.
        CompoundTag root = new CompoundTag();
        CompoundTag regions = new CompoundTag();
        regions.put("Edge", region(Integer.MAX_VALUE, Integer.MIN_VALUE, 0, 2, 1, 1, new int[]{1, 1},
                entry("minecraft:air", Map.of()), entry("minecraft:stone", Map.of())));
        root.put("Regions", regions);
        Schematic schematic = LitematicReader.parse(root, "edge", LitematicReader::resolveState);
        assertEquals(2, schematic.sizeX());
        assertEquals(2, schematic.totalBlocks());
    }

    @Test
    void minValueSizeAndOverlongAxesAreRejected() {
        for (int[] size : new int[][]{{Integer.MIN_VALUE, 1, 1}, {1, Integer.MIN_VALUE, 1},
                {LitematicReader.MAX_AXIS_SPAN + 1, 1, 1}, {1, 1, -(LitematicReader.MAX_AXIS_SPAN + 1)}}) {
            CompoundTag root = new CompoundTag();
            CompoundTag regions = new CompoundTag();
            CompoundTag region = region(0, 0, 0, 1, 1, 1, new int[1],
                    entry("minecraft:air", Map.of()), entry("minecraft:stone", Map.of()));
            region.put("Size", vec(size[0], size[1], size[2]));
            regions.put("R", region);
            root.put("Regions", regions);
            assertThrows(IOException.class, () -> LitematicReader.parse(root, "huge", LitematicReader::resolveState),
                    size[0] + "x" + size[1] + "x" + size[2]);
        }
    }

    @Test
    void laterRegionOverwritesOverlapsOnlyWithNonAir() throws Exception {
        CompoundTag root = new CompoundTag();
        CompoundTag regions = new CompoundTag();
        // Regions apply in name order: A lays two stone, B on the same cells has air then glass.
        regions.put("A", region(0, 0, 0, 2, 1, 1, new int[]{1, 1},
                entry("minecraft:air", Map.of()), entry("minecraft:stone", Map.of())));
        regions.put("B", region(0, 0, 0, 2, 1, 1, new int[]{0, 1},
                entry("minecraft:air", Map.of()), entry("minecraft:glass", Map.of())));
        root.put("Regions", regions);
        Schematic schematic = LitematicReader.parse(root, "overlap", LitematicReader::resolveState);
        assertSame(Blocks.STONE.defaultBlockState(), schematic.state(0, 0, 0));
        assertSame(Blocks.GLASS.defaultBlockState(), schematic.state(1, 0, 0));
        assertEquals(2, schematic.totalBlocks());
    }

    /** The header pass skips the array without allocating; it may then fail or see an empty region, never crash. */
    private static void headerSurvives(Path file) {
        try {
            assertEquals("0x0x0", LitematicReader.readHeader(file, 0L).dimensions());
        } catch (IOException expected) {
            // A clean load error is fine too.
        }
    }

    /** A gzip NBT file whose only region declares a BlockStates LongArray of {@code length} and then ends. */
    private static Path hostileArray(Path file, int length) throws IOException {
        try (OutputStream raw = Files.newOutputStream(file);
             DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(10);
            out.writeUTF("Regions");
            out.writeByte(10);
            out.writeUTF("R");
            out.writeByte(12);
            out.writeUTF("BlockStates");
            out.writeInt(length);
            out.writeLong(0L);
        }
        return file;
    }

    private static int index(int x, int y, int z, int sizeX, int sizeZ) {
        return (y * sizeZ + z) * sizeX + x;
    }

    private static CompoundTag entry(String name, Map<String, String> properties) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        if (!properties.isEmpty()) {
            CompoundTag props = new CompoundTag();
            properties.forEach(props::putString);
            tag.put("Properties", props);
        }
        return tag;
    }

    private static CompoundTag region(int px, int py, int pz, int sx, int sy, int sz, int[] cells, CompoundTag... palette) {
        CompoundTag region = new CompoundTag();
        region.put("Position", vec(px, py, pz));
        region.put("Size", vec(sx, sy, sz));
        ListTag list = new ListTag();
        list.addAll(List.of(palette));
        region.put("BlockStatePalette", list);
        region.putLongArray("BlockStates", LitematicReader.pack(cells, LitematicReader.bitsFor(palette.length)));
        return region;
    }

    private static CompoundTag vec(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }
}
