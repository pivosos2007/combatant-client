/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan;

import combatant.client.mixininterface.IVulkanBackendInfo;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Combatant-owned Vulkan storage buffer allocated from Mojang's existing VMA allocator.
 *
 * <p>The allocation is persistently mapped when VMA can provide host-visible memory. This is the
 * first native-storage layer only; descriptor/pipeline/dispatch ownership remains separate.</p>
 */
final class VulkanStorageBuffer implements RhiStorageBuffer {
    private final StorageBufferDescriptor descriptor;
    private final RhiStats stats;
    private final long allocator;
    private final long buffer;
    private final long allocation;
    private final long mappedAddress;
    private final AtomicBoolean closed = new AtomicBoolean();

    VulkanStorageBuffer(IVulkanBackendInfo backend,
                        StorageBufferDescriptor descriptor,
                        RhiStats stats) {
        if (backend == null || backend.combatant$vma() == 0L) {
            throw new IllegalStateException("Mojang Vulkan VMA allocator is unavailable");
        }
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        this.descriptor = descriptor;
        this.stats = stats;
        this.allocator = backend.combatant$vma();

        long size = descriptor.byteSize();
        if (size <= 0L) throw new IllegalArgumentException("Storage buffer size must be positive");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                    | VK_BUFFER_USAGE_TRANSFER_SRC_BIT
                    | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            if (descriptor.indirectSource()) usage |= VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;

            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO)
                    .flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
                            | VMA_ALLOCATION_CREATE_MAPPED_BIT);

            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            VmaAllocationInfo info = VmaAllocationInfo.calloc(stack);

            int result = vmaCreateBuffer(
                    allocator,
                    bufferInfo,
                    allocationInfo,
                    pBuffer,
                    pAllocation,
                    info
            );
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vmaCreateBuffer failed: VkResult=" + result
                        + " label=" + descriptor.label());
            }

            this.buffer = pBuffer.get(0);
            this.allocation = pAllocation.get(0);
            this.mappedAddress = info.pMappedData();
        }

        if (buffer == 0L || allocation == 0L || mappedAddress == 0L) {
            if (buffer != 0L && allocation != 0L) {
                vmaDestroyBuffer(allocator, buffer, allocation);
            }
            throw new IllegalStateException("Vulkan storage allocation is not persistently mapped: "
                    + descriptor.label());
        }
    }

    long vkBuffer() {
        ensureOpen();
        return buffer;
    }

    long allocation() {
        ensureOpen();
        return allocation;
    }

    @Override
    public StorageBufferDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void upload(ByteBuffer source, long destinationOffset) {
        ensureOpen();
        if (source == null) return;
        if (destinationOffset < 0L) throw new IllegalArgumentException("destinationOffset");

        ByteBuffer src = source.duplicate();
        long bytes = src.remaining();
        if (bytes == 0L) return;
        long end = Math.addExact(destinationOffset, bytes);
        if (end > descriptor.byteSize()) {
            throw new IndexOutOfBoundsException("Storage upload exceeds buffer: end=" + end
                    + " capacity=" + descriptor.byteSize() + " label=" + descriptor.label());
        }

        ByteBuffer destination = MemoryUtil.memByteBuffer(mappedAddress + destinationOffset, (int) bytes);
        destination.put(src);
        vmaFlushAllocation(allocator, allocation, destinationOffset, bytes);
        if (stats != null) stats.storageBufferUpload(bytes);
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Storage buffer is closed: " + descriptor.label());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        vmaDestroyBuffer(allocator, buffer, allocation);
    }
}
