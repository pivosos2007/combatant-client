/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.hmi_recode.script;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 74 Float64 values plus 14 reused string/list metadata fields.
 * One instance belongs to one V8 isolate and must not escape its synchronized execute call.
 */
public final class HmiInputBuffer {
    public static final int VALUE_COUNT = 74;
    public static final int BYTE_COUNT = VALUE_COUNT * Double.BYTES;
    public static final int METADATA_COUNT = 14;
    private final ByteBuffer bytes;
    private final Object[] metadata = new Object[METADATA_COUNT];

    public HmiInputBuffer(ByteBuffer bytes) {
        if (bytes == null || !bytes.isDirect() || bytes.isReadOnly() || bytes.capacity() < BYTE_COUNT) {
            throw new IllegalArgumentException("HMI input must be a writable native Float64Array buffer");
        }
        this.bytes = bytes.duplicate().order(ByteOrder.nativeOrder());
    }

    public Object[] metadata() { return metadata; }

    public void put(int index, double value) {
        if (index < 0 || index >= VALUE_COUNT) throw new IndexOutOfBoundsException("HMI input index " + index);
        bytes.putDouble(index * Double.BYTES, value);
    }
    public void put(int index, float value) { put(index, (double) value); }
    public void put(int index, int value) { put(index, (double) value); }
    public void put(int index, boolean value) { put(index, value ? 1.0 : 0.0); }
    public void meta(int index, Object value) {
        if (index < 0 || index >= METADATA_COUNT) throw new IndexOutOfBoundsException("HMI metadata index " + index);
        metadata[index] = value;
    }
    public double number(int index) { return bytes.getDouble(index * Double.BYTES); }
}
