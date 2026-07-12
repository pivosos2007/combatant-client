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
        }
        return fileRaw.toByteArray();
    }

    public ConfigProfileMeta readMeta(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Header header = readHeader(in);
            return header.meta();
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
            return new ConfigProfileSnapshot(header.meta(), entries);
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
        if (payloadLength < 0) throw new IOException("Negative payload length");
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
        List<ConfigProfileEntry> entries = new ArrayList<>(Math.max(0, entryCount));
        for (int i = 0; i < entryCount; i++) {
            String ownerId = ConfigProfileValueCodec.readString(in);
            String displayName = ConfigProfileValueCodec.readString(in);
            int valueCount = ConfigProfileValueCodec.readVarInt(in);
            java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
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
        try (InflaterInputStream zip = new InflaterInputStream(new ByteArrayInputStream(bytes))) {
            zip.transferTo(out);
        }
        return out.toByteArray();
    }

    private record Header(ConfigProfileMeta meta, int flags, int payloadLength) {
    }
}
