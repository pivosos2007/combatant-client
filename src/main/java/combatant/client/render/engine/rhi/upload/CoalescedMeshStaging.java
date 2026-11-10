/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

/**
 * Gathers monotonically allocated arena ranges into one contiguous staging upload.
 * The source is copied immediately: MeshBuilder storage may be reused before GPU submission.
 * The staging allocation grows with the pending range, not with the entire GPU arena capacity.
 */
final class CoalescedMeshStaging {
    private static final int INITIAL_CAPACITY = 64 * 1024;

    private ByteBuffer storage;
    private int firstOffset = -1;
    private int endOffset;

    void append(int offset, int bytes, ByteBuffer source) {
        if (offset < 0 || bytes <= 0 || source == null || source.remaining() != bytes
                || offset > Integer.MAX_VALUE - bytes) {
            throw new IllegalArgumentException("Invalid staged mesh upload range");
        }
        if (firstOffset < 0) {
            firstOffset = offset;
            endOffset = offset;
        }
        if (offset < endOffset) {
            throw new IllegalStateException("Mesh arena staging ranges must be monotonic and non-overlapping");
        }
        int required = offset + bytes - firstOffset;
        ensureCapacity(required);
        ByteBuffer destination = storage.duplicate();
        destination.position(offset - firstOffset);
        destination.put(source.duplicate());
        endOffset = offset + bytes;
    }

    boolean hasPending() {
        return firstOffset >= 0;
    }

    /** Returns the number of GPU write calls emitted (zero or one). */
    int drain(BiConsumer<Integer, ByteBuffer> upload) {
        if (!hasPending()) return 0;
        int start = firstOffset;
        int length = endOffset - start;
        ByteBuffer contents = storage.duplicate();
        contents.position(0);
        contents.limit(length);
        upload.accept(start, contents);
        firstOffset = -1;
        endOffset = 0;
        return 1;
    }

    private void ensureCapacity(int required) {
        if (storage != null && storage.capacity() >= required) return;
        int capacity = storage == null ? INITIAL_CAPACITY : storage.capacity();
        while (capacity < required) {
            capacity = capacity > Integer.MAX_VALUE / 2 ? required : capacity * 2;
        }
        ByteBuffer expanded = ByteBuffer.allocateDirect(capacity);
        if (storage != null) {
            ByteBuffer previous = storage.duplicate();
            previous.clear();
            previous.limit(endOffset - firstOffset);
            expanded.put(previous);
        }
        storage = expanded;
    }
}
