/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes authenticated DUPLEX frames independently of their TCP length prefix.
 * The binary body uses big-endian fields and ends with a 32-byte HMAC-SHA256;
 * frame payloads are limited to 4,096 bytes.
 */
public final class DuplexCodec {
    private static final int MAGIC = 0x43424458;
    private static final int VERSION = 1;
    private static final int MAC_BYTES = 32;
    private static final int MAX_PAYLOAD_BYTES = 4096;
    private static final String LEGACY_TEXT_PREFIX = "CBX1:";

    private DuplexCodec() {}

    /**
     * Encodes a frame and appends a MAC derived from the session token.
     * A null or blank token uses the codec's fixed fallback key.
     *
     * @throws IllegalArgumentException if the payload exceeds 4,096 bytes
     */
    public static byte[] encodeBinary(DuplexFrame frame, String token) {
        byte[] payload = frame.payload();
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        }
        ByteBuffer body = ByteBuffer.allocate(4 + 1 + 1 + 8 + 16 + 16 + 2 + payload.length)
                .order(ByteOrder.BIG_ENDIAN);
        body.putInt(MAGIC).put((byte) VERSION).put((byte) frame.type().ordinal()).putLong(frame.sequence());
        putUuid(body, frame.sessionId());
        putUuid(body, frame.senderId());
        body.putShort((short) payload.length).put(payload);
        byte[] unsigned = body.array();
        byte[] mac = hmac(unsigned, token);
        ByteBuffer out = ByteBuffer.allocate(unsigned.length + MAC_BYTES);
        out.put(unsigned).put(mac);
        return out.array();
    }

    /**
     * Verifies the frame MAC before parsing its version, type and payload length.
     * Trailing or truncated payload bytes are rejected.
     *
     * @throws SecurityException if the MAC does not match
     * @throws IllegalArgumentException if the frame is malformed or unsupported
     */
    public static DuplexFrame decodeBinary(byte[] bytes, String token) {
        if (bytes == null || bytes.length < 4 + 1 + 1 + 8 + 16 + 16 + 2 + MAC_BYTES) {
            throw new IllegalArgumentException("Truncated Duplex frame");
        }
        int bodyLen = bytes.length - MAC_BYTES;
        byte[] body = java.util.Arrays.copyOf(bytes, bodyLen);
        byte[] actual = java.util.Arrays.copyOfRange(bytes, bodyLen, bytes.length);
        if (!MessageDigest.isEqual(actual, hmac(body, token))) {
            throw new SecurityException("Invalid Duplex frame MAC");
        }

        ByteBuffer in = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN);
        if (in.getInt() != MAGIC) throw new IllegalArgumentException("Invalid Duplex frame magic");
        if (Byte.toUnsignedInt(in.get()) != VERSION) throw new IllegalArgumentException("Unsupported Duplex frame version");
        int type = Byte.toUnsignedInt(in.get());
        DuplexMessageType[] values = DuplexMessageType.values();
        if (type >= values.length) throw new IllegalArgumentException("Unknown Duplex message type");
        long sequence = in.getLong();
        UUID session = getUuid(in);
        UUID sender = getUuid(in);
        int payloadLen = Short.toUnsignedInt(in.getShort());
        if (payloadLen > MAX_PAYLOAD_BYTES || payloadLen != in.remaining()) {
            throw new IllegalArgumentException("Invalid Duplex payload length");
        }
        byte[] payload = new byte[payloadLen];
        in.get(payload);
        return new DuplexFrame(values[type], sequence, session, sender, payload);
    }

    /**
     * Encodes the binary frame as unpadded URL-safe Base64 with a {@code CBX1:} prefix.
     * The local TCP transport instead carries the binary form.
     */
    public static String encode(DuplexFrame frame, String token) {
        return LEGACY_TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(encodeBinary(frame, token));
    }

    /**
     * Decodes the prefixed text representation and applies normal binary MAC validation.
     *
     * @throws IllegalArgumentException if the prefix or Base64 text is malformed
     */
    public static DuplexFrame decode(String line, String token) {
        if (line == null || !line.startsWith(LEGACY_TEXT_PREFIX)) {
            throw new IllegalArgumentException("Invalid Duplex frame prefix");
        }
        return decodeBinary(Base64.getUrlDecoder().decode(line.substring(LEGACY_TEXT_PREFIX.length())), token);
    }

    private static byte[] hmac(byte[] body, String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String material = token == null || token.isBlank() ? "combatant-local-duplex" : token;
            byte[] key = material.getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(body);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static void putUuid(ByteBuffer out, UUID id) {
        out.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
    }

    private static UUID getUuid(ByteBuffer in) {
        return new UUID(in.getLong(), in.getLong());
    }
}
