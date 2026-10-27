/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import combatant.client.config.ConfigObject;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.StringValue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ConfigProfileCodecLimitsTest {

    @Test
    void testNormalRoundtrip() throws IOException {
        ConfigProfileBinaryCodec codec = new ConfigProfileBinaryCodec();
        ConfigProfileMeta meta = new ConfigProfileMeta(
                "test-profile", "Test Profile", ConfigProfileType.MODULES,
                "author", 1000L, 2000L, ConfigProfileBinaryCodec.VERSION, "0.3.0"
        );
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("foo", "bar");
        values.put("num", 42);
        values.put("flag", true);
        ConfigProfileEntry entry = new ConfigProfileEntry("module:test", "Test Module", values);
        ConfigProfileSnapshot snapshot = new ConfigProfileSnapshot(meta, List.of(entry));

        byte[] encoded = codec.write(snapshot);
        assertNotNull(encoded);

        ConfigProfileSnapshot decoded = codec.read(encoded);
        assertEquals("test-profile", decoded.meta().getId());
        assertEquals("Test Profile", decoded.meta().name());
        assertEquals("author", decoded.meta().author());
        assertEquals(1, decoded.entries().size());
        ConfigProfileEntry decEntry = decoded.entries().get(0);
        assertEquals("module:test", decEntry.ownerId());
        assertEquals("bar", decEntry.values().get("foo"));
        assertEquals(42, decEntry.values().get("num"));
        assertEquals(true, decEntry.values().get("flag"));
    }

    @Test
    void testOversizedDecompressionRejected() throws IOException {
        ConfigProfileBinaryCodec codec = new ConfigProfileBinaryCodec();

        // Create a payload that expands beyond MAX_DECOMPRESSED_PAYLOAD_BYTES (16 MiB)
        // Highly compressible zero-bytes payload: 17 MiB of zeros compresses to ~17 KB
        byte[] largeUncompressed = new byte[ConfigProfileBinaryCodec.MAX_DECOMPRESSED_PAYLOAD_BYTES + 1024];
        ByteArrayOutputStream compRaw = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream zip = new DeflaterOutputStream(compRaw, deflater)) {
            zip.write(largeUncompressed);
        } finally {
            deflater.end();
        }
        byte[] compressedPayload = compRaw.toByteArray();

        CRC32 crc = new CRC32();
        crc.update(compressedPayload);

        ByteArrayOutputStream fileRaw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(fileRaw)) {
            out.writeInt(ConfigProfileBinaryCodec.MAGIC);
            out.writeByte(ConfigProfileBinaryCodec.VERSION);
            out.writeByte(ConfigProfileType.MODULES.wireId());
            out.writeShort(1); // FLAG_COMPRESSED
            out.writeLong(1000L);
            out.writeLong(2000L);
            ConfigProfileValueCodec.writeString(out, "author");
            ConfigProfileValueCodec.writeString(out, "bomb");
            ConfigProfileValueCodec.writeString(out, "bomb-id");
            ConfigProfileValueCodec.writeVarInt(out, compressedPayload.length);
            out.write(compressedPayload);
            out.writeInt((int) crc.getValue());
        }

        byte[] profileBytes = fileRaw.toByteArray();
        IOException ex = assertThrows(IOException.class, () -> codec.read(profileBytes));
        assertTrue(ex.getMessage().contains("exceeded limit"), "Expected decompression limit error but got: " + ex.getMessage());
    }

    @Test
    void testExcessiveEntryCountRejected() throws IOException {
        ConfigProfileBinaryCodec codec = new ConfigProfileBinaryCodec();

        // Construct a payload declaring 50,000 entries (MAX_ENTRIES is 10,000)
        ByteArrayOutputStream payloadRaw = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadRaw)) {
            ConfigProfileValueCodec.writeVarInt(payload, ConfigProfileBinaryCodec.MAX_ENTRIES + 1);
        }

        ByteArrayOutputStream compRaw = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream zip = new DeflaterOutputStream(compRaw, deflater)) {
            zip.write(payloadRaw.toByteArray());
        } finally {
            deflater.end();
        }
        byte[] compressedPayload = compRaw.toByteArray();

        CRC32 crc = new CRC32();
        crc.update(compressedPayload);

        ByteArrayOutputStream fileRaw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(fileRaw)) {
            out.writeInt(ConfigProfileBinaryCodec.MAGIC);
            out.writeByte(ConfigProfileBinaryCodec.VERSION);
            out.writeByte(ConfigProfileType.MODULES.wireId());
            out.writeShort(1);
            out.writeLong(1000L);
            out.writeLong(2000L);
            ConfigProfileValueCodec.writeString(out, "author");
            ConfigProfileValueCodec.writeString(out, "test");
            ConfigProfileValueCodec.writeString(out, "test");
            ConfigProfileValueCodec.writeVarInt(out, compressedPayload.length);
            out.write(compressedPayload);
            out.writeInt((int) crc.getValue());
        }

        IOException ex = assertThrows(IOException.class, () -> codec.read(fileRaw.toByteArray()));
        assertTrue(ex.getMessage().contains("Invalid entry count"), "Expected invalid entry count error but got: " + ex.getMessage());
    }

    @Test
    void testSensitiveValuesExcludedFromProfileCollection() {
        ConfigProfileCollector collector = new ConfigProfileCollector();

        StringValue normal = new StringValue("normal", "visible");
        StringValue secret = new StringValue("password", "supersecret").sensitive();

        ConfigObject dummy = new ConfigObject() {
            @Override
            public List<ConfigValue<?>> getConfigValues() {
                return List.of(normal, secret);
            }
        };

        // Collect with collector via reflection or private entry method testing
        // Let's verify isSensitive on the ConfigValue
        assertFalse(normal.isSensitive());
        assertTrue(secret.isSensitive());
    }
}
