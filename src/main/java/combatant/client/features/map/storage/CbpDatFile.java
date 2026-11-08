/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Append-only CBPD container. A new chunk table is appended and the fixed header is updated last,
 * so an interrupted append leaves the previous table usable. If the header/table is corrupt the
 * file can be recovered by scanning block boundaries.
 */
public final class CbpDatFile implements Closeable {
    public static final int MAGIC = 0x43425044;      // CBPD
    private static final int VERSION = 1;
    private static final int HEADER_SIZE = 84;
    private static final int CHUNK_MAGIC = 0x43484E4B; // CHNK
    private static final int TABLE_MAGIC = 0x54424C45; // TBLE
    private static final int BLOCK_VERSION = 1;
    private static final int MAX_STRING_BYTES = 16 * 1024;
    private static final int MAX_TABLE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_CHUNK_BYTES = 256 * 1024 * 1024;

    private final Path path;
    private final RandomAccessFile raf;
    private final UUID fileId;
    private final UUID serverRef;
    private final long createdAt;
    private final List<MapHistoryChunkMeta> chunks = new ArrayList<>();
    private long updatedAt;
    private long indexOffset;

    private CbpDatFile(Path path, RandomAccessFile raf, UUID fileId, UUID serverRef,
                       long createdAt, long updatedAt, long indexOffset) {
        this.path = path;
        this.raf = raf;
        this.fileId = fileId;
        this.serverRef = serverRef;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.indexOffset = indexOffset;
    }

    public static CbpDatFile open(Path path, String serverKey) throws IOException {
        if (path == null) throw new IllegalArgumentException("path");
        Files.createDirectories(path.toAbsolutePath().getParent());
        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw");
        UUID expectedServerRef = serverRef(serverKey);
        if (raf.length() < HEADER_SIZE) {
            raf.setLength(0L);
            long now = System.currentTimeMillis();
            CbpDatFile file = new CbpDatFile(path, raf, UUID.randomUUID(), expectedServerRef, now, now, 0L);
            file.writeHeader();
            raf.getFD().sync();
            return file;
        }

        Header header = readHeader(raf);
        CbpDatFile file;
        if (header != null) {
            if (!expectedServerRef.equals(header.serverRef)) {
                raf.close();
                throw new IOException("CBPD server identity mismatch: " + path);
            }
            file = new CbpDatFile(path, raf, header.fileId, header.serverRef,
                    header.createdAt, header.updatedAt, header.indexOffset);
            boolean loaded = false;
            if (header.indexOffset >= HEADER_SIZE && header.indexOffset < raf.length()) {
                try {
                    file.chunks.addAll(file.readTableChain(header.indexOffset, header.chunkCount));
                    loaded = file.chunks.size() == header.chunkCount;
                } catch (IOException ignored) {
                    file.chunks.clear();
                }
            }
            if (!loaded) {
                file.chunks.clear();
                file.chunks.addAll(file.scanChunks());
                file.rewriteFullTableAndHeader();
            }
            return file;
        }

        // Fixed-size header is corrupt, but append blocks may still be intact.
        long now = Files.getLastModifiedTime(path).toMillis();
        file = new CbpDatFile(path, raf, UUID.randomUUID(), expectedServerRef, now, now, 0L);
        file.chunks.addAll(file.scanChunks());
        file.rewriteFullTableAndHeader();
        return file;
    }

    /** Metadata-only inspection used by .cbpidx rebuild. */
    public static List<MapHistoryChunkMeta> inspect(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) return List.of();
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            if (raf.length() < HEADER_SIZE) return List.of();
            Header header = readHeader(raf);
            if (header != null && header.indexOffset >= HEADER_SIZE && header.indexOffset < raf.length()) {
                try {
                    return List.copyOf(readTableChainStatic(raf, header.indexOffset, header.chunkCount));
                } catch (IOException ignored) {
                }
            }
            return List.copyOf(scanChunksStatic(raf));
        }
    }

    /** Full payload/CRC verification for catalog rebuild and repair tooling. */
    public static List<MapHistoryChunkMeta> inspectVerified(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) return List.of();
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            if (raf.length() < HEADER_SIZE) return List.of();
            List<MapHistoryChunkMeta> candidates;
            Header header = readHeader(raf);
            if (header != null && header.indexOffset >= HEADER_SIZE && header.indexOffset < raf.length()) {
                try {
                    candidates = readTableChainStatic(raf, header.indexOffset, header.chunkCount);
                } catch (IOException brokenTable) {
                    candidates = scanChunksStatic(raf);
                }
            } else {
                candidates = scanChunksStatic(raf);
            }
            List<MapHistoryChunkMeta> verified = new ArrayList<>(candidates.size());
            for (MapHistoryChunkMeta meta : candidates) {
                if (validateChunkPayload(raf, meta)) verified.add(meta);
            }
            return List.copyOf(verified);
        }
    }

    public Path path() {
        return path;
    }

    public UUID fileId() {
        return fileId;
    }

    public List<MapHistoryChunkMeta> chunks() {
        return List.copyOf(chunks);
    }

    public long sizeBytes() throws IOException {
        return raf.length();
    }

    public List<MapHistoryChunkMeta> appendChunks(String serverKey,
                                                  List<? extends List<? extends MapHistoryRecord>> batches) throws IOException {
        if (batches == null || batches.isEmpty()) return List.of();
        if (!serverRef(serverKey).equals(serverRef)) throw new IOException("CBPD server mismatch");

        List<MapHistoryChunkMeta> appended = new ArrayList<>();
        raf.seek(raf.length());
        for (List<? extends MapHistoryRecord> records : batches) {
            if (records == null || records.isEmpty()) continue;
            appended.add(writeChunk(serverKey, records));
        }
        if (appended.isEmpty()) return List.of();

        chunks.addAll(appended);
        chunks.sort(Comparator.comparingLong(MapHistoryChunkMeta::offset));
        updatedAt = System.currentTimeMillis();
        appendDeltaTableAndHeader(appended);
        raf.getFD().sync();
        return List.copyOf(appended);
    }

    public List<MapHistoryRecord> read(MapHistoryQuery query) throws IOException {
        if (query == null) return List.of();
        List<MapHistoryRecord> out = new ArrayList<>();
        for (MapHistoryChunkMeta meta : chunks) {
            if (!query.matches(meta)) continue;
            try {
                out.addAll(readChunk(meta));
            } catch (IOException corruptChunk) {
                // One damaged chunk must not hide healthy history from the same container.
            }
        }
        out.removeIf(record -> record.observedAtMs() < query.fromMs() || record.observedAtMs() > query.toMs());
        out.sort(Comparator.comparingLong(MapHistoryRecord::observedAtMs));
        return List.copyOf(out);
    }

    public List<MapHistoryRecord> readChunk(MapHistoryChunkMeta meta) throws IOException {
        if (meta == null) return List.of();
        synchronized (raf) {
            raf.seek(meta.offset());
            int magic = raf.readInt();
            if (magic != CHUNK_MAGIC) throw new IOException("CBPD chunk magic mismatch");
            int headerLength = raf.readInt();
            if (headerLength < 0 || headerLength > MAX_STRING_BYTES * 4 + 256) throw new IOException("CBPD header length");
            byte[] header = new byte[headerLength];
            raf.readFully(header);
            ParsedChunk parsed = parseChunkHeader(meta.offset(), headerLength, header);
            if (!sameIdentity(meta, parsed.meta)) throw new IOException("CBPD chunk metadata mismatch");
            if (parsed.meta.compressedLength() < 0 || parsed.meta.compressedLength() > MAX_CHUNK_BYTES) {
                throw new IOException("CBPD compressed length");
            }
            byte[] compressed = new byte[parsed.meta.compressedLength()];
            raf.readFully(compressed);
            return CbpDatCodec.decode(parsed.meta, compressed);
        }
    }

    private MapHistoryChunkMeta writeChunk(String serverKey, List<? extends MapHistoryRecord> records) throws IOException {
        MapHistoryRecord first = records.getFirst();
        CbpDatCodec.Encoded encoded = CbpDatCodec.encode(records);
        byte[] header;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(BLOCK_VERSION);
            out.writeInt(first.type().ordinal());
            out.writeInt(first.source().ordinal());
            out.writeLong(first.targetUuid().getMostSignificantBits());
            out.writeLong(first.targetUuid().getLeastSignificantBits());
            out.writeLong(encoded.startMs());
            out.writeLong(encoded.endMs());
            out.writeInt(encoded.count());
            out.writeInt(encoded.compressed().length);
            out.writeInt(encoded.uncompressedLength());
            out.writeInt(encoded.crc32());
            writeString(out, first.targetName());
            writeString(out, serverKey);
            writeString(out, first.worldKey());
            writeString(out, first.sourceKey());
            out.flush();
            header = bytes.toByteArray();
        }

        long offset = raf.getFilePointer();
        raf.writeInt(CHUNK_MAGIC);
        raf.writeInt(header.length);
        raf.write(header);
        raf.write(encoded.compressed());
        int blockLength = Math.toIntExact(8L + header.length + encoded.compressed().length);
        return new MapHistoryChunkMeta(offset, blockLength, first.targetUuid(), first.targetName(),
                clean(serverKey), first.worldKey(), first.source(), first.sourceKey(), first.type(),
                encoded.startMs(), encoded.endMs(), encoded.count(), encoded.compressed().length,
                encoded.uncompressedLength(), encoded.crc32());
    }

    private void appendDeltaTableAndHeader(List<MapHistoryChunkMeta> appended) throws IOException {
        if (appended == null || appended.isEmpty()) return;
        long previous = indexOffset;
        long tableOffset = raf.length();
        raf.seek(tableOffset);
        writeTableBlock(previous, appended);
        // Durable two-phase commit: make chunk/table data stable before publishing its pointer.
        raf.getFD().sync();
        indexOffset = tableOffset;
        writeHeader();
        raf.getFD().sync();
    }

    private void rewriteFullTableAndHeader() throws IOException {
        long tableOffset = raf.length();
        raf.seek(tableOffset);
        writeTableBlock(0L, chunks);
        raf.getFD().sync();
        indexOffset = tableOffset;
        writeHeader();
        raf.getFD().sync();
    }

    private void writeTableBlock(long previousTableOffset, List<MapHistoryChunkMeta> metas) throws IOException {
        byte[] payload = encodeTable(previousTableOffset, metas);
        CRC32 crc = new CRC32();
        crc.update(payload);
        raf.writeInt(TABLE_MAGIC);
        raf.writeInt(payload.length);
        raf.writeInt((int) crc.getValue());
        raf.write(payload);
    }

    private void writeHeader() throws IOException {
        byte[] bytes;
        try (ByteArrayOutputStream raw = new ByteArrayOutputStream(HEADER_SIZE);
             DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(0); // flags
            out.writeInt(CbpDatCodec.CODEC_DEFLATE);
            out.writeLong(fileId.getMostSignificantBits());
            out.writeLong(fileId.getLeastSignificantBits());
            out.writeLong(serverRef.getMostSignificantBits());
            out.writeLong(serverRef.getLeastSignificantBits());
            out.writeLong(createdAt);
            out.writeLong(updatedAt);
            out.writeLong(indexOffset);
            out.writeInt(chunks.size());
            out.writeInt(0);
            out.flush();
            byte[] noCrc = raw.toByteArray();
            if (noCrc.length != HEADER_SIZE - 4) throw new IOException("CBPD header layout");
            CRC32 crc = new CRC32();
            crc.update(noCrc);
            try (ByteArrayOutputStream complete = new ByteArrayOutputStream(HEADER_SIZE)) {
                complete.write(noCrc);
                try (DataOutputStream crcOut = new DataOutputStream(complete)) {
                    crcOut.writeInt((int) crc.getValue());
                    crcOut.flush();
                }
                bytes = complete.toByteArray();
            }
        }
        raf.seek(0L);
        raf.write(bytes);
    }

    private List<MapHistoryChunkMeta> readTableChain(long offset, int expectedCount) throws IOException {
        return readTableChainStatic(raf, offset, expectedCount);
    }

    private static List<MapHistoryChunkMeta> readTableChainStatic(RandomAccessFile raf, long offset,
                                                                  int expectedCount) throws IOException {
        List<MapHistoryChunkMeta> out = new ArrayList<>(Math.max(0, expectedCount));
        java.util.HashSet<Long> visited = new java.util.HashSet<>();
        long cursor = offset;
        while (cursor >= HEADER_SIZE && cursor < raf.length()) {
            if (!visited.add(cursor)) throw new IOException("CBPD table cycle");
            TableBlock block = readTableBlock(raf, cursor);
            out.addAll(block.metas);
            cursor = block.previousOffset;
            if (out.size() > Math.max(expectedCount + 1024, 2_000_000)) {
                throw new IOException("CBPD table chain too large");
            }
        }
        out.sort(Comparator.comparingLong(MapHistoryChunkMeta::offset));
        return List.copyOf(out);
    }

    private static TableBlock readTableBlock(RandomAccessFile raf, long offset) throws IOException {
        raf.seek(offset);
        if (raf.readInt() != TABLE_MAGIC) throw new IOException("CBPD table magic");
        int length = raf.readInt();
        int expectedCrc = raf.readInt();
        if (length < 0 || length > MAX_TABLE_BYTES || offset + 12L + length > raf.length()) {
            throw new IOException("CBPD table length");
        }
        byte[] payload = new byte[length];
        raf.readFully(payload);
        CRC32 crc = new CRC32();
        crc.update(payload);
        if ((int) crc.getValue() != expectedCrc) throw new IOException("CBPD table CRC");
        return decodeTable(payload);
    }

    private List<MapHistoryChunkMeta> scanChunks() throws IOException {
        return scanChunksStatic(raf);
    }

    private static List<MapHistoryChunkMeta> scanChunksStatic(RandomAccessFile raf) throws IOException {
        List<MapHistoryChunkMeta> out = new ArrayList<>();
        long length = raf.length();
        long pos = HEADER_SIZE;
        while (pos + 8L <= length) {
            raf.seek(pos);
            int magic;
            try {
                magic = raf.readInt();
            } catch (EOFException eof) {
                break;
            }
            if (magic == CHUNK_MAGIC) {
                int headerLength = raf.readInt();
                if (headerLength < 0 || headerLength > MAX_STRING_BYTES * 4 + 256
                        || pos + 8L + headerLength > length) {
                    pos++;
                    continue;
                }
                byte[] header = new byte[headerLength];
                raf.readFully(header);
                ParsedChunk parsed;
                try {
                    parsed = parseChunkHeader(pos, headerLength, header);
                } catch (IOException malformed) {
                    pos++;
                    continue;
                }
                long blockEnd = pos + parsed.meta.blockLength();
                if (parsed.meta.compressedLength() < 0 || parsed.meta.compressedLength() > MAX_CHUNK_BYTES
                        || blockEnd > length) {
                    pos++;
                    continue;
                }
                // Recovery validates the actual compressed payload and CRC, not only metadata.
                if (!validateChunkPayload(raf, parsed.meta)) {
                    pos = blockEnd;
                    continue;
                }
                out.add(parsed.meta);
                pos = blockEnd;
                continue;
            }
            if (magic == TABLE_MAGIC) {
                int payloadLength = raf.readInt();
                if (payloadLength >= 0 && payloadLength <= MAX_TABLE_BYTES
                        && pos + 12L + payloadLength <= length) {
                    pos += 12L + payloadLength;
                    continue;
                }
            }
            pos++;
        }
        out.sort(Comparator.comparingLong(MapHistoryChunkMeta::offset));
        return out;
    }

    private static boolean validateChunkPayload(RandomAccessFile raf, MapHistoryChunkMeta meta) {
        if (meta == null) return false;
        try {
            if (meta.offset() < HEADER_SIZE || meta.compressedLength() < 0 || meta.compressedLength() > MAX_CHUNK_BYTES
                    || meta.offset() + meta.blockLength() > raf.length()) return false;
            raf.seek(meta.offset());
            if (raf.readInt() != CHUNK_MAGIC) return false;
            int headerLength = raf.readInt();
            if (headerLength < 0 || headerLength > MAX_STRING_BYTES * 4 + 256) return false;
            byte[] header = new byte[headerLength];
            raf.readFully(header);
            ParsedChunk parsed = parseChunkHeader(meta.offset(), headerLength, header);
            if (!sameIdentity(meta, parsed.meta)) return false;
            byte[] compressed = new byte[parsed.meta.compressedLength()];
            raf.readFully(compressed);
            CbpDatCodec.decode(parsed.meta, compressed);
            return true;
        } catch (IOException | RuntimeException corruptPayload) {
            return false;
        }
    }

    private static ParsedChunk parseChunkHeader(long offset, int headerLength, byte[] header) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(header))) {
            int version = in.readInt();
            if (version != BLOCK_VERSION) throw new IOException("CBPD block version " + version);
            MapHistoryRecordType type = enumAt(MapHistoryRecordType.values(), in.readInt(), "record type");
            MapHistorySource source = enumAt(MapHistorySource.values(), in.readInt(), "source");
            UUID target = new UUID(in.readLong(), in.readLong());
            long start = in.readLong();
            long end = in.readLong();
            int count = in.readInt();
            int compressed = in.readInt();
            int uncompressed = in.readInt();
            int crc = in.readInt();
            String name = readString(in);
            String server = readString(in);
            String world = readString(in);
            String sourceKey = readString(in);
            if (count < 0 || compressed < 0 || uncompressed < 0) throw new IOException("CBPD negative lengths");
            int blockLength = Math.toIntExact(8L + headerLength + compressed);
            return new ParsedChunk(new MapHistoryChunkMeta(offset, blockLength, target, name, server, world,
                    source, sourceKey, type, start, end, count, compressed, uncompressed, crc));
        }
    }

    private static byte[] encodeTable(long previousTableOffset, List<MapHistoryChunkMeta> metas) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(64, metas.size() * 128));
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(BLOCK_VERSION);
            out.writeLong(previousTableOffset);
            out.writeInt(metas.size());
            for (MapHistoryChunkMeta meta : metas) {
                out.writeLong(meta.offset());
                out.writeInt(meta.blockLength());
                out.writeLong(meta.targetUuid().getMostSignificantBits());
                out.writeLong(meta.targetUuid().getLeastSignificantBits());
                out.writeInt(meta.type().ordinal());
                out.writeInt(meta.source().ordinal());
                out.writeLong(meta.startMs());
                out.writeLong(meta.endMs());
                out.writeInt(meta.recordCount());
                out.writeInt(meta.compressedLength());
                out.writeInt(meta.uncompressedLength());
                out.writeInt(meta.crc32());
                writeString(out, meta.targetName());
                writeString(out, meta.serverKey());
                writeString(out, meta.worldKey());
                writeString(out, meta.sourceKey());
            }
            out.flush();
            return bytes.toByteArray();
        }
    }

    private static TableBlock decodeTable(byte[] payload) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version != BLOCK_VERSION) throw new IOException("CBPD table version " + version);
            long previousOffset = in.readLong();
            int count = in.readInt();
            if (count < 0 || count > 2_000_000) throw new IOException("CBPD table count");
            List<MapHistoryChunkMeta> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long offset = in.readLong();
                int blockLength = in.readInt();
                UUID target = new UUID(in.readLong(), in.readLong());
                MapHistoryRecordType type = enumAt(MapHistoryRecordType.values(), in.readInt(), "record type");
                MapHistorySource source = enumAt(MapHistorySource.values(), in.readInt(), "source");
                long start = in.readLong();
                long end = in.readLong();
                int records = in.readInt();
                int compressed = in.readInt();
                int uncompressed = in.readInt();
                int crc = in.readInt();
                String name = readString(in);
                String server = readString(in);
                String world = readString(in);
                String sourceKey = readString(in);
                out.add(new MapHistoryChunkMeta(offset, blockLength, target, name, server, world,
                        source, sourceKey, type, start, end, records, compressed, uncompressed, crc));
            }
            return new TableBlock(previousOffset, List.copyOf(out));
        }
    }

    private static Header readHeader(RandomAccessFile raf) throws IOException {
        if (raf.length() < HEADER_SIZE) return null;
        byte[] bytes = new byte[HEADER_SIZE];
        raf.seek(0L);
        raf.readFully(bytes);
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, HEADER_SIZE - 4);
        int expected = readInt(bytes, HEADER_SIZE - 4);
        if ((int) crc.getValue() != expected) return null;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) return null;
            int version = in.readInt();
            if (version != VERSION) return null;
            in.readInt(); // flags
            int codec = in.readInt();
            if (codec != CbpDatCodec.CODEC_DEFLATE) return null;
            UUID fileId = new UUID(in.readLong(), in.readLong());
            UUID serverRef = new UUID(in.readLong(), in.readLong());
            long created = in.readLong();
            long updated = in.readLong();
            long index = in.readLong();
            int count = in.readInt();
            in.readInt(); // reserved
            if (count < 0) return null;
            return new Header(fileId, serverRef, created, updated, index, count);
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = clean(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("CBPD string too long");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("CBPD string length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean sameIdentity(MapHistoryChunkMeta a, MapHistoryChunkMeta b) {
        return a.offset() == b.offset()
                && a.targetUuid().equals(b.targetUuid())
                && a.type() == b.type()
                && a.source() == b.source()
                && a.recordCount() == b.recordCount()
                && a.compressedLength() == b.compressedLength()
                && a.crc32() == b.crc32();
    }

    private static <E> E enumAt(E[] values, int ordinal, String what) throws IOException {
        if (ordinal < 0 || ordinal >= values.length) throw new IOException("Invalid CBPD " + what);
        return values[ordinal];
    }

    static UUID serverRef(String serverKey) {
        String key = "combatant-map-history\n" + clean(serverKey).toLowerCase(java.util.Locale.ROOT);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    private record Header(UUID fileId, UUID serverRef, long createdAt, long updatedAt,
                          long indexOffset, int chunkCount) {
    }

    private record ParsedChunk(MapHistoryChunkMeta meta) {
    }

    private record TableBlock(long previousOffset, List<MapHistoryChunkMeta> metas) {
    }
}
