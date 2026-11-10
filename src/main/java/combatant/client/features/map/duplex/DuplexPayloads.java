/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Serializes bearing observations and length-prefixed UTF-8 fields for DUPLEX.
 * Text payloads contain at most 255 fields, each at most 1,024 encoded bytes;
 * decoding rejects truncated fields and trailing bytes.
 */
public final class DuplexPayloads {
    private DuplexPayloads() {}

    public static byte[] bearing(DuplexBearingSample sample) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
             DataOutputStream out = new DataOutputStream(bytes)) {
            writeUuid(out, sample.targetUuid());
            out.writeDouble(sample.observerX());
            out.writeDouble(sample.observerZ());
            out.writeDouble(sample.bearingRadians());
            out.writeLong(sample.observedAtMs());
            out.writeLong(sample.sourceRevision());
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads one bearing sample and rejects any trailing payload bytes.
     * The observed time and source revision are preserved as transmitted.
     *
     * @throws IllegalArgumentException if the payload is truncated or has extra bytes
     */
    public static DuplexBearingSample readBearing(byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            DuplexBearingSample sample = new DuplexBearingSample(readUuid(in), in.readDouble(), in.readDouble(),
                    in.readDouble(), in.readLong(), in.readLong());
            if (in.available() != 0) throw new IllegalArgumentException("Trailing bearing payload bytes");
            return sample;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid bearing payload", e);
        }
    }

    /**
     * Encodes up to 255 strings. Null fields are encoded as empty strings.
     * Limits apply to UTF-8 bytes, not Java character count.
     *
     * @throws IllegalArgumentException if a field exceeds 1,024 bytes or the count exceeds 255
     */
    public static byte[] text(String... values) {
        if (values.length > 255) throw new IllegalArgumentException("Too many text fields");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(values.length);
            for (String value : values) {
                byte[] raw = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
                if (raw.length > 1024) throw new IllegalArgumentException("Text field too large");
                out.writeShort(raw.length);
                out.write(raw);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String[] readText(byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int count = in.readUnsignedByte();
            String[] values = new String[count];
            for (int i = 0; i < count; i++) {
                int len = in.readUnsignedShort();
                if (len > 1024) throw new IllegalArgumentException("Text field too large");
                byte[] raw = new byte[len];
                in.readFully(raw);
                values[i] = new String(raw, StandardCharsets.UTF_8);
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing text payload bytes");
            return values;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid text payload", e);
        }
    }

    private static void writeUuid(DataOutput out,UUID id)throws IOException{
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static UUID readUuid(DataInput in)throws IOException{return new UUID(in.readLong(),in.readLong());}
}
