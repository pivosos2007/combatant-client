package combatant.client.render.engine.rhi.backend.vulkan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;

class VulkanStorageUploadRingTest {
    @Test
    void expandsPast64UploadsWithoutOverwritingRecordedSnapshots() {
        VulkanStorageUploadRing ring = new VulkanStorageUploadRing(3, 64);
        Storage storage = new Storage();
        List<Snapshot> recorded = new ArrayList<>();
        assertFalse(ring.hasSnapshot());

        for (long submit = 2; submit <= 4; submit++) {
            for (int upload = 0; upload < 1500; upload++) {
                int offset = Math.toIntExact(ring.beginSnapshot(submit, 0, storage));
                long value = submit * 10000 + upload;
                storage.current[offset] = value;
                recorded.add(new Snapshot(storage.current, offset, value));
            }
        }

        assertTrue(ring.hasSnapshot());
        assertEquals(List.of(128, 256, 512, 1024, 2048), storage.growth);
        for (Snapshot snapshot : recorded) {
            assertEquals(snapshot.value(), snapshot.allocation()[snapshot.offset()]);
        }
    }

    @Test
    void reusesSubmitSlotOnlyAfterItsPreviousOwnerCompletes() {
        VulkanStorageUploadRing ring = new VulkanStorageUploadRing(3, 64);
        IntConsumer noGrowth = capacity -> fail("Unexpected growth");
        assertEquals(128, ring.beginSnapshot(2, 0, noGrowth));
        assertEquals(0, ring.beginSnapshot(3, 0, noGrowth));
        assertEquals(64, ring.beginSnapshot(4, 1, noGrowth));

        assertThrows(IllegalStateException.class, () -> ring.beginSnapshot(5, 1, noGrowth));
        assertEquals(128, ring.beginSnapshot(5, 2, noGrowth));
        assertEquals(129, ring.beginSnapshot(5, 2, noGrowth));
    }

    @Test
    void retainsExpandedCapacityAcrossSubmitWraparound() {
        VulkanStorageUploadRing ring = new VulkanStorageUploadRing(3, 64);
        Storage storage = new Storage();
        for (long submit = 2; submit < 30; submit++) {
            for (int upload = 0; upload < 100; upload++) {
                long offset = ring.beginSnapshot(submit, submit - 2, storage);
                assertTrue(offset >= 0 && offset < storage.current.length);
            }
        }
        assertEquals(List.of(128), storage.growth);
    }

    @Test
    void failedGrowthCanBeRetriedWithoutAdvancingTheSnapshot() {
        VulkanStorageUploadRing ring = new VulkanStorageUploadRing(3, 64);
        Storage storage = new Storage();
        for (int i = 0; i < 64; i++) ring.beginSnapshot(2, 0, storage);
        long[] original = storage.current;

        assertThrows(IllegalStateException.class, () -> ring.beginSnapshot(2, 0, capacity -> {
            throw new IllegalStateException("Allocation failed");
        }));
        assertSame(original, storage.current);
        assertEquals(320, ring.beginSnapshot(2, 0, storage));
        assertEquals(321, ring.beginSnapshot(2, 0, storage));
        assertEquals(List.of(128), storage.growth);
    }

    private static final class Storage implements IntConsumer {
        private long[] current = new long[3 * 64];
        private final List<Integer> growth = new ArrayList<>();

        @Override
        public void accept(int uploadsPerSubmit) {
            current = new long[3 * uploadsPerSubmit];
            growth.add(uploadsPerSubmit);
        }
    }

    private record Snapshot(long[] allocation, int offset, long value) { }
}
