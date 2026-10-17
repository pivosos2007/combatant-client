/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan;

import com.mojang.renderpearl.backend.vulkan.Destroyable;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import combatant.client.mixininterface.IVulkanBackendInfo;
import combatant.client.mixininterface.IVulkanCommandEncoderAccess;
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
import java.util.Arrays;
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
    // Match Mojang's own in-flight submission limit. The extra slot means a submit-local
    // snapshot is never reused until the encoder reports its previous owner as completed.
    private static final int SUBMIT_RING_SIZE = VulkanCommandEncoder.MAX_SUBMITS_IN_FLIGHT + 1;
    private static final int MAX_UPLOADS_PER_SUBMIT = 64;
    private final StorageBufferDescriptor descriptor;
    private final RhiStats stats;
    private final VulkanDevice ownerDevice;
    private final long allocator;
    private final long buffer;
    private final long allocation;
    private final long mappedAddress;
    private final long logicalSize;
    private final long uploadStride;
    private long activeSubmitIndex = Long.MIN_VALUE;
    private int activeSubmitSlot = -1;
    private final long[] submitRingOwners = new long[SUBMIT_RING_SIZE];
    private int uploadCursor;
    private long currentUploadBase;
    private final AtomicBoolean closed = new AtomicBoolean();

    VulkanStorageBuffer(IVulkanBackendInfo backend,
                        StorageBufferDescriptor descriptor,
                        RhiStats stats) {
        if (backend == null || backend.combatant$vma() == 0L) {
            throw new IllegalStateException("Mojang Vulkan VMA allocator is unavailable");
        }
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        if (!(backend instanceof VulkanDevice device)) {
            throw new IllegalStateException("Vulkan backend bridge is not the active Mojang VulkanDevice");
        }
        this.descriptor = descriptor;
        this.stats = stats;
        Arrays.fill(this.submitRingOwners, Long.MIN_VALUE);
        this.ownerDevice = device;
        this.allocator = backend.combatant$vma();

        this.logicalSize = descriptor.byteSize();
        if (logicalSize <= 0L) throw new IllegalArgumentException("Storage buffer size must be positive");
        long alignment = Math.max(1L, backend.combatant$minStorageBufferOffsetAlignment());
        this.uploadStride = align(logicalSize, alignment);
        long ringSlots = Math.multiplyExact((long) SUBMIT_RING_SIZE, MAX_UPLOADS_PER_SUBMIT);
        long physicalSize = Math.multiplyExact(uploadStride, ringSlots);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                    | VK_BUFFER_USAGE_TRANSFER_SRC_BIT
                    | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            if (descriptor.indirectSource()) usage |= VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;

            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(physicalSize)
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

    long bindingOffset(long logicalOffset) {
        ensureOpen();
        if (logicalOffset < 0L || logicalOffset > logicalSize) {
            throw new IndexOutOfBoundsException("Storage binding offset outside logical buffer: " + logicalOffset);
        }
        return Math.addExact(currentUploadBase, logicalOffset);
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
        if (end > logicalSize) {
            throw new IndexOutOfBoundsException("Storage upload exceeds buffer: end=" + end
                    + " capacity=" + logicalSize + " label=" + descriptor.label());
        }

        // offset==0 starts a new logical snapshot. This is the normal Combatant compute path and
        // guarantees that descriptors recorded earlier in the same Vulkan submission keep seeing
        // the bytes that belonged to their dispatch instead of a later CPU overwrite.
        if (destinationOffset == 0L || activeSubmitIndex == Long.MIN_VALUE) {
            beginUploadSnapshot();
        }
        long physicalOffset = Math.addExact(currentUploadBase, destinationOffset);
        ByteBuffer destination = MemoryUtil.memByteBuffer(mappedAddress + physicalOffset, (int) bytes);
        destination.put(src);
        vmaFlushAllocation(allocator, allocation, physicalOffset, bytes);
        if (stats != null) stats.storageBufferUpload(bytes);
    }

    private void beginUploadSnapshot() {
        VulkanCommandEncoder encoder = ownerDevice.createCommandEncoder();
        if (!(encoder instanceof IVulkanCommandEncoderAccess access)) {
            throw new IllegalStateException("Vulkan command encoder bridge is unavailable for storage upload");
        }
        long submitIndex = access.combatant$currentSubmitIndex();
        if (submitIndex != activeSubmitIndex) {
            int submitSlot = Math.floorMod(submitIndex, SUBMIT_RING_SIZE);
            long previousOwner = submitRingOwners[submitSlot];
            long completedSubmitIndex = access.combatant$completedSubmitIndex();
            if (previousOwner != Long.MIN_VALUE && previousOwner > completedSubmitIndex) {
                throw new IllegalStateException("Storage buffer submit ring would overwrite in-flight data: label="
                        + descriptor.label() + " slot=" + submitSlot + " owner=" + previousOwner
                        + " completed=" + completedSubmitIndex + " current=" + submitIndex);
            }
            submitRingOwners[submitSlot] = submitIndex;
            activeSubmitIndex = submitIndex;
            activeSubmitSlot = submitSlot;
            uploadCursor = 0;
        }
        if (uploadCursor >= MAX_UPLOADS_PER_SUBMIT) {
            throw new IllegalStateException("Storage buffer upload ring exhausted in one submission: label="
                    + descriptor.label() + " max=" + MAX_UPLOADS_PER_SUBMIT);
        }
        if (activeSubmitSlot < 0) {
            throw new IllegalStateException("Storage buffer submit slot is unavailable: " + descriptor.label());
        }
        long slot = (long) activeSubmitSlot * MAX_UPLOADS_PER_SUBMIT + uploadCursor++;
        currentUploadBase = Math.multiplyExact(slot, uploadStride);
    }

    private static long align(long value, long alignment) {
        if (alignment <= 1L) return value;
        long remainder = value % alignment;
        return remainder == 0L ? value : Math.addExact(value, alignment - remainder);
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Storage buffer is closed: " + descriptor.label());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ownerDevice.createCommandEncoder().queueForDestroy(
                (Destroyable) () -> vmaDestroyBuffer(allocator, buffer, allocation)
        );
    }
}
