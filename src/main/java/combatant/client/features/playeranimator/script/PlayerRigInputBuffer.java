/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playeranimator.script;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Native, per-isolate input for PlayerRig. The V8 Float64Array owns the memory;
 * Java only retains the direct ByteBuffer view while that isolate is alive.
 * String fields are a small reusable sideband, converted by Javet once per evaluation.
 */
public final class PlayerRigInputBuffer implements PlayerRigScriptContext.Sink {
    public static final int VALUE_COUNT = 72;
    public static final int BYTE_COUNT = VALUE_COUNT * Double.BYTES;
    public static final int STRING_COUNT = 14;

    private final ByteBuffer bytes;
    private final Object[] strings = new Object[STRING_COUNT];

    public PlayerRigInputBuffer(ByteBuffer bytes) {
        if (bytes == null || !bytes.isDirect() || bytes.isReadOnly() || bytes.capacity() < BYTE_COUNT) {
            throw new IllegalArgumentException("PlayerRig input must be a writable native Float64Array buffer");
        }
        this.bytes = bytes.duplicate().order(ByteOrder.nativeOrder());
    }

    public Object[] strings() { return strings; }

    public void set(int index, double value) { put(index, value); }
    public void set(int index, float value) { put(index, value); }
    public void set(int index, int value) { put(index, value); }
    public void set(int index, boolean value) { put(index, value); }
    public void set(int index, String value) { put(index, value); }

    public void put(int index, double value) {
        check(index);
        bytes.putDouble(index * Double.BYTES, value);
    }

    public void put(int index, float value) { put(index, (double) value); }
    public void put(int index, int value) { put(index, (double) value); }
    public void put(int index, boolean value) { put(index, value ? 1.0 : 0.0); }

    public void put(int index, String value) {
        int stringIndex = stringIndex(index);
        strings[stringIndex] = value;
    }

    public double number(int index) { check(index); return bytes.getDouble(index * Double.BYTES); }

    public static int stringIndex(int index) {
        return switch (index) {
            case 0 -> 0; case 17 -> 1; case 18 -> 2; case 19 -> 3;
            case 20 -> 4; case 21 -> 5; case 29 -> 6; case 30 -> 7;
            case 31 -> 8; case 56 -> 9; case 60 -> 10; case 61 -> 11;
            case 66 -> 12; case 67 -> 13;
            default -> throw new IllegalArgumentException("Not a PlayerRig string field: " + index);
        };
    }

    private static void check(int index) {
        if (index < 0 || index >= VALUE_COUNT) {
            throw new IndexOutOfBoundsException("PlayerRig input index " + index);
        }
    }
}
