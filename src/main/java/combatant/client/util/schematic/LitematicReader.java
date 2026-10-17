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
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.visitors.CollectToTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.nio.file.Files;
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
    /** Longest merged span on any axis; also keeps every index and offset well inside int range. */
    public static final int MAX_AXIS_SPAN = 1 << 15;
    /** Compressed size cap: a legitimate 64M-cell schematic is far smaller than this on disk. */
    public static final long MAX_FILE_BYTES = 128L * 1024L * 1024L;
    /**
     * Heap the NBT reader may account for. Arrays are accounted before they are allocated, so a tiny gzip that
     * declares a multi-GB LongArray fails here instead of taking the client down.
     */
    public static final long NBT_QUOTA_BYTES = 256L * 1024L * 1024L;
    /** The header pass skips every block array, so it needs only the metadata and region boxes. */
    static final long HEADER_QUOTA_BYTES = 16L * 1024L * 1024L;
    private static final String AIR_ID = "minecraft:air";
    private static final int MAX_WARNINGS = 32;

    private LitematicReader() {
    }

    @FunctionalInterface
    public interface PaletteResolver {
        BlockState resolve(String name, Map<String, String> properties, List<String> warnings);
    }

    public static Schematic read(Path path) throws IOException {
        return parse(readBounded(path), SchematicFiles.baseName(path), LitematicReader::resolveState);
    }

    /** Reads metadata and region boxes only; block palettes and state arrays are skipped while streaming. */
    public static SchematicHeader readHeader(Path path, long lastModified) throws IOException {
        checkFileSize(path);
        HeaderVisitor visitor = new HeaderVisitor();
        try {
            NbtIo.parseCompressed(path, visitor, NbtAccounter.create(HEADER_QUOTA_BYTES));
        } catch (RuntimeException e) {
            throw new IOException("Malformed schematic: " + e.getMessage(), e);
        }
        if (!(visitor.getResult() instanceof CompoundTag root)) throw new IOException("Schematic root is not a compound");
        CompoundTag meta = root.getCompoundOrEmpty("Metadata");
        int[] size = enclosingSize(root);
        long total = meta.getLongOr("TotalBlocks", meta.getIntOr("TotalBlocks", -1));
        return new SchematicHeader(
                path,
                path.getFileName().toString(),
                meta.getStringOr("Name", SchematicFiles.baseName(path)),
                meta.getStringOr("Author", ""),
                size[0], size[1], size[2],
                total,
                root.getCompoundOrEmpty("Regions").keySet().size(),
                lastModified,
                "");
    }

    /** Reads a gzip NBT file with the size cap and heap quota every schematic format shares. */
    static CompoundTag readBounded(Path path) throws IOException {
        checkFileSize(path);
        try {
            return NbtIo.readCompressed(path, NbtAccounter.create(NBT_QUOTA_BYTES));
        } catch (RuntimeException e) {
            // NbtAccounterException, negative array lengths and ReportedNbtException all mean a malformed file.
            throw new IOException("Malformed schematic: " + e.getMessage(), e);
        }
    }

    private static void checkFileSize(Path path) throws IOException {
        long bytes = Files.size(path);
        if (bytes > MAX_FILE_BYTES) throw new IOException("File too large: " + (bytes >> 20) + " MB");
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

        // Box math in long: positions near +/-2^31 plus sizes would otherwise wrap into a small, valid-looking box.
        long minX = Long.MAX_VALUE, minY = Long.MAX_VALUE, minZ = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE, maxY = Long.MIN_VALUE, maxZ = Long.MIN_VALUE;
        for (RegionData r : parsed) {
            minX = Math.min(minX, r.minX);
            minY = Math.min(minY, r.minY);
            minZ = Math.min(minZ, r.minZ);
            maxX = Math.max(maxX, r.minX + r.sizeX - 1);
            maxY = Math.max(maxY, r.minY + r.sizeY - 1);
            maxZ = Math.max(maxZ, r.minZ + r.sizeZ - 1);
        }
        long spanX = maxX - minX + 1;
        long spanY = maxY - minY + 1;
        long spanZ = maxZ - minZ + 1;
        if (spanX > MAX_AXIS_SPAN || spanY > MAX_AXIS_SPAN || spanZ > MAX_AXIS_SPAN
                || spanX * spanY * spanZ > MAX_VOLUME) {
            throw new IOException("Schematic too large: " + spanX + "x" + spanY + "x" + spanZ);
        }
        int sizeX = (int) spanX;
        int sizeY = (int) spanY;
        int sizeZ = (int) spanZ;
        long volume = spanX * spanY * spanZ;

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
                                   long minX, long minY, long minZ, int sizeX, int sizeZ, List<String> warnings) throws IOException {
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
        int ox = (int) (region.minX - minX);
        int oy = (int) (region.minY - minY);
        int oz = (int) (region.minZ - minZ);
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
                    // Overlaps: a later region (by name order) overwrites only with its non-air blocks, so its air
                    // never clears what an earlier region put there (air cells were already skipped above).
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

    static long normalizedMin(long position, long size) {
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
        long x = Math.abs((long) size.getIntOr("x", 0));
        long y = Math.abs((long) size.getIntOr("y", 0));
        long z = Math.abs((long) size.getIntOr("z", 0));
        if (x > 0 && y > 0 && z > 0) return new int[]{clampSpan(x), clampSpan(y), clampSpan(z)};

        long minX = Long.MAX_VALUE, minY = Long.MAX_VALUE, minZ = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE, maxY = Long.MIN_VALUE, maxZ = Long.MIN_VALUE;
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        for (String name : regions.keySet()) {
            CompoundTag region = regions.getCompoundOrEmpty(name);
            long[] min = new long[3];
            long[] len = new long[3];
            if (!regionBox(region, min, len)) continue;
            minX = Math.min(minX, min[0]);
            minY = Math.min(minY, min[1]);
            minZ = Math.min(minZ, min[2]);
            maxX = Math.max(maxX, min[0] + len[0] - 1);
            maxY = Math.max(maxY, min[1] + len[1] - 1);
            maxZ = Math.max(maxZ, min[2] + len[2] - 1);
        }
        if (minX == Long.MAX_VALUE) return new int[]{0, 0, 0};
        return new int[]{clampSpan(maxX - minX + 1), clampSpan(maxY - minY + 1), clampSpan(maxZ - minZ + 1)};
    }

    private static int clampSpan(long span) {
        return (int) Math.min(Integer.MAX_VALUE, span);
    }

    private static boolean regionBox(CompoundTag region, long[] min, long[] len) {
        CompoundTag pos = region.getCompoundOrEmpty("Position");
        CompoundTag size = region.getCompoundOrEmpty("Size");
        long sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
        if (sx == 0 || sy == 0 || sz == 0) return false;
        min[0] = normalizedMin(pos.getIntOr("x", 0), sx);
        min[1] = normalizedMin(pos.getIntOr("y", 0), sy);
        min[2] = normalizedMin(pos.getIntOr("z", 0), sz);
        len[0] = Math.abs(sx);
        len[1] = Math.abs(sy);
        len[2] = Math.abs(sz);
        return true;
    }

    private record RegionData(String name, long minX, long minY, long minZ, int sizeX, int sizeY, int sizeZ,
                              ListTag palette, long[] states) {
        static RegionData of(String name, CompoundTag tag) throws IOException {
            long[] min = new long[3];
            long[] len = new long[3];
            if (!regionBox(tag, min, len)) return null;
            if (len[0] > MAX_AXIS_SPAN || len[1] > MAX_AXIS_SPAN || len[2] > MAX_AXIS_SPAN) {
                throw new IOException("Region '" + name + "' too large: " + len[0] + "x" + len[1] + "x" + len[2]);
            }
            long[] states = tag.getLongArray("BlockStates").orElse(new long[0]);
            return new RegionData(name, min[0], min[1], min[2], (int) len[0], (int) len[1], (int) len[2],
                    tag.getListOrEmpty("BlockStatePalette"), states);
        }
    }

    /**
     * Collects the tree like {@link CollectToTag} but skips everything inside a region except its Position and
     * Size, plus the metadata preview image, so the library can list a folder of large files cheaply.
     */
    private static final class HeaderVisitor extends CollectToTag {
        private final List<String> path = new ArrayList<>();

        @Override
        public StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
            path.clear();
            path.add("");
            return super.visitRootEntry(type);
        }

        @Override
        public StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String name) {
            if (skipped(name)) return StreamTagVisitor.EntryResult.SKIP;
            StreamTagVisitor.EntryResult result = super.visitEntry(type, name);
            if (result == StreamTagVisitor.EntryResult.ENTER && isContainer(type)) path.add(name);
            return result;
        }

        @Override
        public StreamTagVisitor.EntryResult visitElement(TagType<?> type, int index) {
            StreamTagVisitor.EntryResult result = super.visitElement(type, index);
            if (result == StreamTagVisitor.EntryResult.ENTER && isContainer(type)) path.add("[]");
            return result;
        }

        @Override
        public StreamTagVisitor.ValueResult visitContainerEnd() {
            if (!path.isEmpty()) path.removeLast();
            return super.visitContainerEnd();
        }

        private boolean skipped(String name) {
            if (path.size() == 3 && "Regions".equals(path.get(1))) return !"Position".equals(name) && !"Size".equals(name);
            return path.size() == 2 && "Metadata".equals(path.get(1)) && "PreviewImageData".equals(name);
        }

        private static boolean isContainer(TagType<?> type) {
            return type == CompoundTag.TYPE || type == ListTag.TYPE;
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

        int indexOf(String name, Map<String, String> props) throws IOException {
            String key = props.isEmpty() ? name : name + props.toString().replace('{', '[').replace('}', ']').replace(", ", ",");
            Integer existing = byKey.get(key);
            if (existing != null) return existing;

            BlockState state = resolver.resolve(name, props, warnings);
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
