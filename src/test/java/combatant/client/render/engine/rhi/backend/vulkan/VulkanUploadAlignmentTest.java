/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.util.TransientBlockAllocator;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanUploadAlignment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VulkanUploadAlignmentTest {
    @Test
    void rgbaUploadAfterOddGlyphSizeHasTexelAlignedOffset() {
        try (var allocator = allocator()) {
            allocator.allocate(35, 1, 35, 1);
            var vanilla = allocator.allocate(64, 1, 64, 1);
            assertNotEquals(0, vanilla.offset() % 4);
        }
        try (var allocator = allocator()) {
            allocator.allocate(35, VulkanUploadAlignment.staging(1), 35, 1);
            var rgba = allocator.allocate(64, VulkanUploadAlignment.staging(1), 64, 1);
            assertEquals(36, rgba.offset());
            assertEquals(0, rgba.offset() % GpuFormat.RGBA8_UNORM.blockSize());
        }
    }

    @Test
    void mixedTextureFormatsKeepTheirTexelAlignment() {
        try (var allocator = allocator()) {
            for (GpuFormat format : GpuFormat.values()) {
                if (!format.hasColorAspect()) continue;
                allocator.allocate(7, VulkanUploadAlignment.staging(1), 7, 1);
                long bytes = 5L * format.blockSize();
                var upload = allocator.allocate(bytes, VulkanUploadAlignment.staging(format.blockSize()), bytes, 1);
                assertEquals(0, upload.offset() % format.blockSize(), format.name());
                assertEquals(0, upload.offset() % 4, format.name());
            }
        }
    }

    @Test
    void strongerCallerAlignmentIsPreserved() {
        for (long alignment : new long[]{1, 2, 3, 4, 6, 8, 12, 16, 256, 1L << 40}) {
            long actual = VulkanUploadAlignment.staging(alignment);
            assertEquals(0, actual % alignment);
            assertEquals(0, actual % 4);
        }
    }

    private static TransientBlockAllocator<Block> allocator() {
        return new TransientBlockAllocator<>(524288L, Long.highestOneBit(Long.MAX_VALUE),
                TransientBlockAllocator.Allocator.create(size -> new Block(), block -> {}));
    }

    private record Block() implements TransientBlockAllocator.Allocator.Block {
        @Override public boolean suboptimal() { return false; }
    }
}
