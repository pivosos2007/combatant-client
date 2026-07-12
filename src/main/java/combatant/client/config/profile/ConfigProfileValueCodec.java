/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

enum ConfigProfileValueCodec {
    ;
    private static final byte NULL = 0;
    private static final byte BOOLEAN = 1;
    private static final byte INT = 2;
    private static final byte LONG = 3;
    private static final byte FLOAT = 4;
    private static final byte DOUBLE = 5;
    private static final byte STRING = 6;
    private static final byte LIST = 7;
    private static final byte MAP = 8;

    static void writeValue(DataOutputStream out, Object value) throws IOException {
        if (value == null) {
            out.writeByte(NULL);
        } else if (value instanceof Boolean b) {
            out.writeByte(BOOLEAN);
            out.writeBoolean(b);
        } else if (value instanceof Integer i) {
            out.writeByte(INT);
            writeVarInt(out, i);
        } else if (value instanceof Long l) {
            out.writeByte(LONG);
            out.writeLong(l);
        } else if (value instanceof Float f) {
            out.writeByte(FLOAT);
            out.writeFloat(f);
        } else if (value instanceof Number n) {
            out.writeByte(DOUBLE);
            out.writeDouble(n.doubleValue());
        } else if (value instanceof CharSequence s) {
            out.writeByte(STRING);
            writeString(out, s.toString());
        } else if (value instanceof Iterable<?> iterable) {
            out.writeByte(LIST);
            List<Object> list = new ArrayList<>();
            for (Object item : iterable) list.add(item);
            writeVarInt(out, list.size());
            for (Object item : list) writeValue(out, item);
        } else if (value instanceof Map<?, ?> map) {
            out.writeByte(MAP);
            writeVarInt(out, map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                writeString(out, String.valueOf(entry.getKey()));
                writeValue(out, entry.getValue());
            }
        } else {
            out.writeByte(STRING);
            writeString(out, String.valueOf(value));
        }
    }

    static Object readValue(DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        return switch (type) {
            case NULL -> null;
            case BOOLEAN -> in.readBoolean();
            case INT -> readVarInt(in);
            case LONG -> in.readLong();
            case FLOAT -> in.readFloat();
            case DOUBLE -> in.readDouble();
            case STRING -> readString(in);
            case LIST -> {
                int size = readVarInt(in);
                List<Object> list = new ArrayList<>(Math.max(0, size));
                for (int i = 0; i < size; i++) list.add(readValue(in));
                yield list;
            }
            case MAP -> {
                int size = readVarInt(in);
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) {
                    map.put(readString(in), readValue(in));
                }
                yield map;
            }
            default -> throw new IOException("Unknown profile value type: " + type);
        };
    }

    static void writeString(DataOutputStream out, String value) throws IOException {
        if (value == null) value = "";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0) throw new IOException("Negative string length: " + length);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int v = value;
        while ((v & 0xFFFFFF80) != 0L) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v & 0x7F);
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new IOException("VarInt is too big");
        } while ((read & 0x80) != 0);
        return result;
    }
}
