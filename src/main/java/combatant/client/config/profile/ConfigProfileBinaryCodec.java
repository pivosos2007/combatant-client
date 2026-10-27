/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class ConfigProfileBinaryCodec {
    public static final int VERSION = 1;
    static final int MAGIC = 0x43424346; // CBCF
    private static final int FLAG_COMPRESSED = 1;
    public static final int MAX_DECOMPRESSED_PAYLOAD_BYTES = 16 * 1024 * 1024; // 16 MiB
    public static final int MAX_PAYLOAD_LENGTH = 16 * 1024 * 1024; // 16 MiB
    public static final int MAX_ENTRIES = 10_000;
    public static final int MAX_VALUES_PER_ENTRY = 10_000;


    public ConfigProfileBinaryCodec() {
    }

    public byte[] write(ConfigProfileSnapshot snapshot) throws IOException {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is null");
        ByteArrayOutputStream payloadRaw = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadRaw)) {
            writePayload(payload, snapshot.entries());
        }

        byte[] payloadBytes = compress(payloadRaw.toByteArray());
        CRC32 crc = new CRC32();
        crc.update(payloadBytes);

        ByteArrayOutputStream fileRaw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(fileRaw)) {
            ConfigProfileMeta meta = snapshot.meta();
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeByte(meta.type().wireId());
            out.writeShort(FLAG_COMPRESSED);
            out.writeLong(meta.createdAt());
            out.writeLong(meta.updatedAt());
            ConfigProfileValueCodec.writeString(out, meta.author());
            ConfigProfileValueCodec.writeString(out, meta.name());
            ConfigProfileValueCodec.writeString(out, meta.getId());
            ConfigProfileValueCodec.writeVarInt(out, payloadBytes.length);
            out.write(payloadBytes);
            out.writeInt((int) crc.getValue());
            // Optional trailer after the CRC: readers of older builds stop at the CRC and ignore it.
            if (!meta.combatantVersion().isEmpty()) {
                ConfigProfileValueCodec.writeString(out, meta.combatantVersion());
            }
        }
        return fileRaw.toByteArray();
    }

    public ConfigProfileMeta readMeta(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Header header = readHeader(in);
            ConfigProfileMeta meta = header.meta();
            try {
                in.skipNBytes(header.payloadLength() + 4L); // payload + CRC
            } catch (EOFException truncated) {
                return meta;
            }
            String savedBy = readOptionalTrailer(in);
            return savedBy.isEmpty() ? meta : meta.withCombatantVersion(savedBy);
        }
    }

    /** Reads the optional saving-version trailer; empty when absent (old profiles) or unreadable. */
    private static String readOptionalTrailer(DataInputStream in) {
        try {
            if (in.available() <= 0) return "";
            String value = ConfigProfileValueCodec.readString(in);
            return value == null ? "" : value;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    public ConfigProfileSnapshot read(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Header header = readHeader(in);
            byte[] payload = new byte[header.payloadLength()];
            in.readFully(payload);
            int expectedCrc = in.readInt();
            CRC32 crc = new CRC32();
            crc.update(payload);
            if ((int) crc.getValue() != expectedCrc) {
                throw new IOException("Config profile checksum mismatch: " + header.meta().name());
            }
            byte[] rawPayload = (header.flags() & FLAG_COMPRESSED) != 0 ? decompress(payload) : payload;
            List<ConfigProfileEntry> entries;
            try (DataInputStream payloadIn = new DataInputStream(new ByteArrayInputStream(rawPayload))) {
                entries = readPayload(payloadIn);
            }
            ConfigProfileMeta meta = header.meta();
            String savedBy = readOptionalTrailer(in);
            if (!savedBy.isEmpty()) meta = meta.withCombatantVersion(savedBy);
            return new ConfigProfileSnapshot(meta, entries);
        }
    }

    private Header readHeader(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) throw new IOException("Invalid profile magic");
        int version = in.readUnsignedByte();
        if (version != VERSION) throw new IOException("Unsupported profile version: " + version);
        ConfigProfileType type = ConfigProfileType.fromWireId(in.readUnsignedByte());
        int flags = in.readUnsignedShort();
        long createdAt = in.readLong();
        long updatedAt = in.readLong();
        String author = ConfigProfileValueCodec.readString(in);
        String name = ConfigProfileValueCodec.readString(in);
        String id = ConfigProfileValueCodec.readString(in);
        int payloadLength = ConfigProfileValueCodec.readVarInt(in);
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_LENGTH) {
            throw new IOException("Invalid payload length: " + payloadLength);
        }
        ConfigProfileMeta meta = new ConfigProfileMeta(id, name, type, author, createdAt, updatedAt, version);
        return new Header(meta, flags, payloadLength);
    }

    private void writePayload(DataOutputStream out, List<ConfigProfileEntry> entries) throws IOException {
        ConfigProfileValueCodec.writeVarInt(out, entries.size());
        for (ConfigProfileEntry entry : entries) {
            ConfigProfileValueCodec.writeString(out, entry.ownerId());
            ConfigProfileValueCodec.writeString(out, entry.displayName());
            ConfigProfileValueCodec.writeVarInt(out, entry.values().size());
            for (Map.Entry<String, Object> value : entry.values().entrySet()) {
                ConfigProfileValueCodec.writeString(out, value.getKey());
                ConfigProfileValueCodec.writeValue(out, value.getValue());
            }
        }
    }

    private List<ConfigProfileEntry> readPayload(DataInputStream in) throws IOException {
        int entryCount = ConfigProfileValueCodec.readVarInt(in);
        if (entryCount < 0 || entryCount > MAX_ENTRIES) {
            throw new IOException("Invalid entry count: " + entryCount);
        }
        List<ConfigProfileEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            String ownerId = ConfigProfileValueCodec.readString(in);
            String displayName = ConfigProfileValueCodec.readString(in);
            int valueCount = ConfigProfileValueCodec.readVarInt(in);
            if (valueCount < 0 || valueCount > MAX_VALUES_PER_ENTRY) {
                throw new IOException("Invalid value count: " + valueCount);
            }
            java.util.Map<String, Object> values = new java.util.LinkedHashMap<>(valueCount);
            for (int j = 0; j < valueCount; j++) {
                values.put(ConfigProfileValueCodec.readString(in), ConfigProfileValueCodec.readValue(in));
            }
            entries.add(new ConfigProfileEntry(ownerId, displayName, values));
        }
        return entries;
    }

    private byte[] compress(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream zip = new DeflaterOutputStream(out, deflater)) {
            zip.write(bytes);
        } finally {
            deflater.end();
        }
        return out.toByteArray();
    }

    private byte[] decompress(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int totalRead = 0;
        try (InflaterInputStream zip = new InflaterInputStream(new ByteArrayInputStream(bytes))) {
            int n;
            while ((n = zip.read(buffer)) != -1) {
                totalRead += n;
                if (totalRead > MAX_DECOMPRESSED_PAYLOAD_BYTES) {
                    throw new IOException("Decompressed config profile exceeded limit of "
                            + MAX_DECOMPRESSED_PAYLOAD_BYTES + " bytes");
                }
                out.write(buffer, 0, n);
            }
        }
        return out.toByteArray();
    }

    private record Header(ConfigProfileMeta meta, int flags, int payloadLength) {
    }
}
