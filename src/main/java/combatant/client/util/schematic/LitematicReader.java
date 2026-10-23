/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads Litematica {@code .litematic} files without the Litematica mod.
 *
 * <p>Every region is merged into one dense box. Litematica stores each region's block indices as one
 * continuous little-endian bit stream across the long array (entries can straddle two longs), with
 * {@code max(2, ceil(log2(paletteSize)))} bits per entry and index {@code (y * sizeZ + z) * sizeX + x}.</p>
 */
public final class LitematicReader {

    public static final String EXTENSION = ".litematic";
    // A dense short per cell: 64M cells is already 128 MB, anything bigger is not a build anyone places by hand.
    public static final long MAX_VOLUME = 64L * 1024L * 1024L;
    private static final String AIR_ID = "minecraft:air";
    private static final int MAX_WARNINGS = 32;

    private LitematicReader() {
    }

    @FunctionalInterface
    public interface PaletteResolver {
        BlockState resolve(String name, Map<String, String> properties, List<String> warnings);
    }

    public static Schematic read(Path path) throws IOException {
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        return parse(root, SchematicFormats.baseName(path), LitematicReader::resolveState);
    }

    public static SchematicHeader readHeader(Path path, long lastModified) throws IOException {
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        CompoundTag meta = root.getCompoundOrEmpty("Metadata");
        int[] size = enclosingSize(root);
        long total = meta.getLongOr("TotalBlocks", meta.getIntOr("TotalBlocks", -1));
        return new SchematicHeader(
                path,
                path.getFileName().toString(),
                meta.getStringOr("Name", SchematicFormats.baseName(path)),
                meta.getStringOr("Author", ""),
                size[0], size[1], size[2],
                total,
                root.getCompoundOrEmpty("Regions").keySet().size(),
                lastModified,
                "");
    }

    public static Schematic parse(CompoundTag root, String fallbackName, PaletteResolver resolver) throws IOException {
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        if (regions.keySet().isEmpty()) throw new IOException("No regions in schematic");

        List<RegionData> parsed = new ArrayList<>();
        for (String regionName : new TreeSet<>(regions.keySet())) {
            RegionData region = RegionData.of(regionName, regions.getCompoundOrEmpty(regionName));
            if (region != null) parsed.add(region);
        }
        if (parsed.isEmpty()) throw new IOException("Every region is empty");

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (RegionData r : parsed) {
            minX = Math.min(minX, r.minX);
            minY = Math.min(minY, r.minY);
            minZ = Math.min(minZ, r.minZ);
            maxX = Math.max(maxX, r.minX + r.sizeX - 1);
            maxY = Math.max(maxY, r.minY + r.sizeY - 1);
            maxZ = Math.max(maxZ, r.minZ + r.sizeZ - 1);
        }
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > MAX_VOLUME) throw new IOException("Schematic too large: " + sizeX + "x" + sizeY + "x" + sizeZ);

        List<String> warnings = new ArrayList<>();
        PaletteBuilder palette = new PaletteBuilder(resolver, warnings);
        short[] blocks = new short[(int) volume];
        for (RegionData region : parsed) {
            fillRegion(region, palette, blocks, minX, minY, minZ, sizeX, sizeZ, warnings);
        }

        int total = 0;
        for (short block : blocks) {
            if (block != 0) total++;
        }

        CompoundTag meta = root.getCompoundOrEmpty("Metadata");
        return new Schematic(
                meta.getStringOr("Name", fallbackName),
                fallbackName,
                meta.getStringOr("Author", ""),
                meta.getStringOr("Description", ""),
                sizeX, sizeY, sizeZ,
                palette.states(),
                palette.ids(),
                blocks,
                total,
                parsed.size(),
                List.copyOf(warnings));
    }

    private static void fillRegion(RegionData region, PaletteBuilder palette, short[] blocks,
                                   int minX, int minY, int minZ, int sizeX, int sizeZ, List<String> warnings) throws IOException {
        int paletteSize = region.palette.size();
        if (paletteSize == 0) return;
        int[] globalIds = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            globalIds[i] = palette.indexOf(region.palette.getCompoundOrEmpty(i));
        }

        int bits = bitsFor(paletteSize);
        long volume = (long) region.sizeX * region.sizeY * region.sizeZ;
        long needed = (volume * bits + 63L) / 64L;
        if (region.states.length < needed) {
            throw new IOException("Region '" + region.name + "' has " + region.states.length + " longs, needs " + needed);
        }

        long mask = (1L << bits) - 1L;
        int outOfRange = 0;
        int ox = region.minX - minX;
        int oy = region.minY - minY;
        int oz = region.minZ - minZ;
        int index = 0;
        for (int y = 0; y < region.sizeY; y++) {
            for (int z = 0; z < region.sizeZ; z++) {
                int rowBase = ((y + oy) * sizeZ + (z + oz)) * sizeX + ox;
                for (int x = 0; x < region.sizeX; x++, index++) {
                    int local = (int) unpackAt(region.states, bits, mask, index);
                    if (local == 0) continue;
                    if (local >= paletteSize) {
                        outOfRange++;
                        continue;
                    }
                    int global = globalIds[local];
                    // Later regions overwrite overlaps, the same as Litematica pasting them in order.
                    if (global != 0) blocks[rowBase + x] = (short) global;
                }
            }
        }
        if (outOfRange > 0) addWarning(warnings, region.name + ": " + outOfRange + " entries outside the palette");
    }

    public static int bitsFor(int paletteSize) {
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
    }

    public static long unpackAt(long[] data, int bits, long mask, long index) {
        long startOffset = index * bits;
        int startLong = (int) (startOffset >>> 6);
        int endLong = (int) (((index + 1L) * bits - 1L) >>> 6);
        int bitOffset = (int) (startOffset & 63L);
        if (startLong == endLong) {
            return (data[startLong] >>> bitOffset) & mask;
        }
        return ((data[startLong] >>> bitOffset) | (data[endLong] << (64 - bitOffset))) & mask;
    }

    public static int[] unpack(long[] data, int bits, int count) {
        long mask = (1L << bits) - 1L;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = (int) unpackAt(data, bits, mask, i);
        }
        return out;
    }

    public static long[] pack(int[] values, int bits) {
        long mask = (1L << bits) - 1L;
        long[] out = new long[(int) (((long) values.length * bits + 63L) / 64L)];
        for (int i = 0; i < values.length; i++) {
            long value = values[i] & mask;
            long startOffset = (long) i * bits;
            int startLong = (int) (startOffset >>> 6);
            int endLong = (int) (((long) (i + 1) * bits - 1L) >>> 6);
            int bitOffset = (int) (startOffset & 63L);
            out[startLong] |= value << bitOffset;
            if (startLong != endLong) {
                out[endLong] |= value >>> (64 - bitOffset);
            }
        }
        return out;
    }

    /** Litematica allows negative sizes: the region then grows from its position towards negative coordinates. */
    public static int normalizedMin(int position, int size) {
        return size < 0 ? position + size + 1 : position;
    }

    public static BlockState resolveState(String name, Map<String, String> properties, List<String> warnings) {
        Identifier id = Identifier.tryParse(name);
        Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty()) {
            addWarning(warnings, "Unknown block " + name + ", using air");
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = block.get().defaultBlockState();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = block.get().getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                addWarning(warnings, name + ": unknown property " + entry.getKey());
                continue;
            }
            BlockState next = withValue(state, property, entry.getValue());
            if (next == null) {
                addWarning(warnings, name + ": bad value " + entry.getKey() + "=" + entry.getValue());
                continue;
            }
            state = next;
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String raw) {
        Optional<T> value = property.getValue(raw);
        return value.map(v -> state.setValue(property, v)).orElse(null);
    }

    static void addWarning(List<String> warnings, String warning) {
        if (warnings.size() < MAX_WARNINGS && !warnings.contains(warning)) warnings.add(warning);
    }

    private static int[] enclosingSize(CompoundTag root) {
        CompoundTag size = root.getCompoundOrEmpty("Metadata").getCompoundOrEmpty("EnclosingSize");
        int x = Math.abs(size.getIntOr("x", 0));
        int y = Math.abs(size.getIntOr("y", 0));
        int z = Math.abs(size.getIntOr("z", 0));
        if (x > 0 && y > 0 && z > 0) return new int[]{x, y, z};

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        for (String name : regions.keySet()) {
            CompoundTag region = regions.getCompoundOrEmpty(name);
            int[] min = new int[3];
            int[] len = new int[3];
            if (!regionBox(region, min, len)) continue;
            minX = Math.min(minX, min[0]);
            minY = Math.min(minY, min[1]);
            minZ = Math.min(minZ, min[2]);
            maxX = Math.max(maxX, min[0] + len[0] - 1);
            maxY = Math.max(maxY, min[1] + len[1] - 1);
            maxZ = Math.max(maxZ, min[2] + len[2] - 1);
        }
        if (minX == Integer.MAX_VALUE) return new int[]{0, 0, 0};
        return new int[]{maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
    }

    private static boolean regionBox(CompoundTag region, int[] min, int[] len) {
        CompoundTag pos = region.getCompoundOrEmpty("Position");
        CompoundTag size = region.getCompoundOrEmpty("Size");
        int sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
        if (sx == 0 || sy == 0 || sz == 0) return false;
        min[0] = normalizedMin(pos.getIntOr("x", 0), sx);
        min[1] = normalizedMin(pos.getIntOr("y", 0), sy);
        min[2] = normalizedMin(pos.getIntOr("z", 0), sz);
        len[0] = Math.abs(sx);
        len[1] = Math.abs(sy);
        len[2] = Math.abs(sz);
        return true;
    }

    private record RegionData(String name, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                              ListTag palette, long[] states) {
        static RegionData of(String name, CompoundTag tag) {
            int[] min = new int[3];
            int[] len = new int[3];
            if (!regionBox(tag, min, len)) return null;
            long[] states = tag.getLongArray("BlockStates").orElse(new long[0]);
            return new RegionData(name, min[0], min[1], min[2], len[0], len[1], len[2],
                    tag.getListOrEmpty("BlockStatePalette"), states);
        }
    }

    /** Merges region palettes into one; index 0 is always air so an empty cell is a zero short. */
    static final class PaletteBuilder {
        private final PaletteResolver resolver;
        private final List<String> warnings;
        private final Map<String, Integer> byKey = new HashMap<>();
        private final List<BlockState> states = new ArrayList<>();
        private final List<String> ids = new ArrayList<>();

        PaletteBuilder(PaletteResolver resolver, List<String> warnings) {
            this.resolver = resolver;
            this.warnings = warnings;
            byKey.put(AIR_ID, 0);
            states.add(Blocks.AIR.defaultBlockState());
            ids.add(AIR_ID);
        }

        int indexOf(CompoundTag entry) throws IOException {
            String name = entry.getStringOr("Name", AIR_ID);
            Map<String, String> props = new TreeMap<>();
            CompoundTag properties = entry.getCompoundOrEmpty("Properties");
            for (String key : properties.keySet()) {
                props.put(key, properties.getStringOr(key, ""));
            }
            return indexOf(name, props);
        }

        int indexOf(String name, Map<String, String> sortedProps) throws IOException {
            String key = sortedProps.isEmpty() ? name
                    : name + sortedProps.toString().replace('{', '[').replace('}', ']').replace(", ", ",");
            Integer existing = byKey.get(key);
            if (existing != null) return existing;

            BlockState state = resolver.resolve(name, sortedProps, warnings);
            int index = state == null || state.isAir() ? 0 : states.size();
            if (index != 0) {
                if (index > 0xFFFF) throw new IOException("Palette has more than 65535 states");
                states.add(state);
                ids.add(key);
            }
            byKey.put(key, index);
            return index;
        }

        BlockState[] states() {
            return states.toArray(new BlockState[0]);
        }

        String[] ids() {
            return ids.toArray(new String[0]);
        }
    }
}
