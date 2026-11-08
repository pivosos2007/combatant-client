/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.vulkan;

import java.util.Arrays;
import java.util.function.IntConsumer;

final class VulkanStorageUploadRing {
    private final long[] owners;
    private int uploadsPerSubmit;
    private long activeSubmit = Long.MIN_VALUE;
    private int cursor;

    VulkanStorageUploadRing(int submitSlots, int uploadsPerSubmit) {
        if (submitSlots <= 0 || uploadsPerSubmit <= 0) throw new IllegalArgumentException("Ring capacity must be positive");
        this.owners = new long[submitSlots];
        Arrays.fill(owners, Long.MIN_VALUE);
        this.uploadsPerSubmit = uploadsPerSubmit;
    }

    boolean hasSnapshot() {
        return activeSubmit != Long.MIN_VALUE;
    }

    long beginSnapshot(long submit, long completedSubmit, IntConsumer growStorage) {
        int slot = Math.floorMod(submit, owners.length);
        if (submit != activeSubmit) {
            long previous = owners[slot];
            if (previous > completedSubmit) {
                throw new IllegalStateException("Storage buffer submit ring would overwrite in-flight data: slot="
                        + slot + " owner=" + previous + " completed=" + completedSubmit + " current=" + submit);
            }
            owners[slot] = submit;
            activeSubmit = submit;
            cursor = 0;
        }
        if (cursor == uploadsPerSubmit) {
            int capacity = Math.multiplyExact(uploadsPerSubmit, 2);
            // The callback must retain the previous allocation for already-recorded descriptors.
            growStorage.accept(capacity);
            uploadsPerSubmit = capacity;
        }
        return (long) slot * uploadsPerSubmit + cursor++;
    }
}
