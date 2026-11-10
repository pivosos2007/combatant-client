/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CoalescedMeshStagingTest {
    private static ByteBuffer data(int size, int value) {
        ByteBuffer source = ByteBuffer.allocate(size + 2);
        for (int i = 0; i < size + 2; i++) source.put((byte) value);
        source.position(1);
        source.limit(size + 1);
        return source;
    }

    @Test
    void mergesRangesWithPaddingAndCopiesOriginalSourceImmediately() {
        CoalescedMeshStaging staging = new CoalescedMeshStaging();
        ByteBuffer first = data(12, 7);
        staging.append(2048, 12, first);
        first.put(1, (byte) 99);
        staging.append(2064, 8, data(8, 3));
        List<Integer> offsets = new ArrayList<>();
        assertEquals(1, staging.drain((offset, bytes) -> {
            offsets.add(offset);
            assertEquals(24, bytes.remaining());
            assertEquals(7, bytes.get(0));
            assertEquals(7, bytes.get(11));
            assertEquals(3, bytes.get(16));
            assertEquals(3, bytes.get(23));
        }));
        assertEquals(List.of(2048), offsets);
        assertFalse(staging.hasPending());
        assertEquals(0, staging.drain((offset, bytes) -> fail("Empty staging must not upload")));
        staging.append(5000, 5, data(5, 9));
        staging.drain((offset, bytes) -> {
            assertEquals(5000, offset.intValue());
            assertEquals(5, bytes.remaining());
        });
    }

    @Test
    void handlesCapacityGrowthAndRejectsOverlappingWrites() {
        CoalescedMeshStaging staging = new CoalescedMeshStaging();
        staging.append(0, 40_000, data(40_000, 1));
        staging.append(40_016, 50_000, data(50_000, 2));
        assertThrows(IllegalStateException.class, () -> staging.append(80_000, 2, data(2, 0)));
        staging.drain((offset, bytes) -> {
            assertEquals(90_016, bytes.remaining());
            assertEquals(1, bytes.get(39_999));
            assertEquals(2, bytes.get(40_016));
            assertEquals(2, bytes.get(90_015));
        });
    }
}
