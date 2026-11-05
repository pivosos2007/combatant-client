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
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;

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
    private static final int INITIAL_UPLOADS_PER_SUBMIT = 64;
    private final StorageBufferDescriptor descriptor;
    private final RhiStats stats;
    private final VulkanDevice ownerDevice;
    private final long allocator;
    private NativeAllocation storage;
    private final long logicalSize;
    private final long uploadStride;
    private long activeSubmitIndex = Long.MIN_VALUE;
    private final long[] submitRingOwners = new long[SUBMIT_RING_SIZE];
    private final VulkanStorageUploadRing uploads =
            new VulkanStorageUploadRing(SUBMIT_RING_SIZE, INITIAL_UPLOADS_PER_SUBMIT);
    private long currentUploadBase;
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean staticUploaded;

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
        long ringSlots = descriptor.staticUpload() ? 1L : descriptor.gpuOwned() ? SUBMIT_RING_SIZE
                : Math.multiplyExact((long) SUBMIT_RING_SIZE, INITIAL_UPLOADS_PER_SUBMIT);
        long physicalSize = Math.multiplyExact(uploadStride, ringSlots);
        this.storage = allocate(physicalSize);
    }

    private NativeAllocation allocate(long physicalSize) {
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

            // Compute outputs never receive CPU snapshots and stay in device-local VRAM.
            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
            if (descriptor.gpuOwned() || descriptor.staticUpload()) {
                allocationInfo.usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            } else {
                allocationInfo.usage(VMA_MEMORY_USAGE_AUTO)
                        .flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
                                | VMA_ALLOCATION_CREATE_MAPPED_BIT);
            }

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

            NativeAllocation created = new NativeAllocation(pBuffer.get(0), pAllocation.get(0), info.pMappedData());
            if (created.buffer() == 0L || created.allocation() == 0L
                    || (created.mappedAddress() == 0L && !descriptor.gpuOwned() && !descriptor.staticUpload())) {
                if (created.buffer() != 0L && created.allocation() != 0L) {
                    vmaDestroyBuffer(allocator, created.buffer(), created.allocation());
                }
                throw new IllegalStateException("Vulkan storage allocation is not persistently mapped: "
                        + descriptor.label());
            }
            return created;
        }
    }

    long vkBuffer() {
        ensureOpen();
        return storage.buffer();
    }

    long allocation() {
        ensureOpen();
        return storage.allocation();
    }

    long bindingOffset(long logicalOffset) {
        ensureOpen();
        if (logicalOffset < 0L || logicalOffset > logicalSize) {
            throw new IndexOutOfBoundsException("Storage binding offset outside logical buffer: " + logicalOffset);
        }
        if (descriptor.gpuOwned()) selectGpuOwnedSubmitSlot();
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
        if (descriptor.gpuOwned()) throw new IllegalStateException("GPU-owned buffer cannot accept CPU snapshot uploads");
        if (destinationOffset < 0L) throw new IllegalArgumentException("destinationOffset");

        ByteBuffer src = source.duplicate();
        long bytes = src.remaining();
        if (bytes == 0L) return;
        long end = Math.addExact(destinationOffset, bytes);
        if (end > logicalSize) {
            throw new IndexOutOfBoundsException("Storage upload exceeds buffer: end=" + end
                    + " capacity=" + logicalSize + " label=" + descriptor.label());
        }
        if (descriptor.staticUpload()) {
            uploadStaticOnce(src, destinationOffset, bytes);
            return;
        }

        // offset==0 starts a new logical snapshot. This is the normal Combatant compute path and
        // guarantees that descriptors recorded earlier in the same Vulkan submission keep seeing
        // the bytes that belonged to their dispatch instead of a later CPU overwrite.
        if (destinationOffset == 0L || !uploads.hasSnapshot()) {
            beginUploadSnapshot();
        }
        long physicalOffset = Math.addExact(currentUploadBase, destinationOffset);
        ByteBuffer destination = MemoryUtil.memByteBuffer(storage.mappedAddress() + physicalOffset, (int) bytes);
        destination.put(src);
        vmaFlushAllocation(allocator, storage.allocation(), physicalOffset, bytes);
        if (stats != null) stats.storageBufferUpload(bytes);
    }

    /** A single transfer into device-local storage, independent of per-submission snapshot capacity. */
    private void uploadStaticOnce(ByteBuffer source, long offset, long bytes) {
        if (staticUploaded) throw new IllegalStateException("Static SSBO is immutable after initial upload: " + descriptor.label());
        if (offset != 0 || bytes != logicalSize)
            throw new IllegalArgumentException("Static SSBO requires one complete upload: " + descriptor.label());
        VulkanCommandEncoder encoder = ownerDevice.createCommandEncoder();
        if (!(encoder instanceof IVulkanCommandEncoderAccess access) || access.combatant$renderPassActive())
            throw new IllegalStateException("Static SSBO copy must be recorded outside an active Vulkan render pass");
        NativeAllocation staging = allocateUploadStaging(bytes);
        try {
            ByteBuffer mapped = MemoryUtil.memByteBuffer(staging.mappedAddress(), (int) bytes);
            mapped.put(source);
            vmaFlushAllocation(allocator, staging.allocation(), 0L, bytes);
            VkCommandBuffer commandBuffer = access.combatant$commandBuffer();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCopy.Buffer copy = VkBufferCopy.calloc(1, stack);
                copy.get(0).srcOffset(0L).dstOffset(0L).size(bytes);
                vkCmdCopyBuffer(commandBuffer, staging.buffer(), storage.buffer(), copy);
                VkBufferMemoryBarrier.Buffer memory = VkBufferMemoryBarrier.calloc(1, stack);
                memory.get(0).sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(storage.buffer()).offset(0L).size(bytes);
                vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_VERTEX_SHADER_BIT,
                        0, null, memory, null);
            }
            staticUploaded = true;
            if (stats != null) stats.storageBufferUpload(bytes);
        } finally {
            // Staging stays alive until the encoder's transfer has actually completed.
            encoder.queueForDestroy((Destroyable) () -> vmaDestroyBuffer(allocator,
                    staging.buffer(), staging.allocation()));
        }
    }

    private NativeAllocation allocateUploadStaging(long bytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(bytes)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO)
                    .flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
                            | VMA_ALLOCATION_CREATE_MAPPED_BIT);
            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            VmaAllocationInfo info = VmaAllocationInfo.calloc(stack);
            int result = vmaCreateBuffer(allocator, bufferInfo, allocationInfo, pBuffer, pAllocation, info);
            if (result != VK_SUCCESS) throw new IllegalStateException("Static SSBO staging allocation failed: " + result);
            NativeAllocation output = new NativeAllocation(pBuffer.get(0), pAllocation.get(0), info.pMappedData());
            if (output.mappedAddress() == 0L) {
                vmaDestroyBuffer(allocator, output.buffer(), output.allocation());
                throw new IllegalStateException("Static SSBO staging buffer is not mapped");
            }
            return output;
        }
    }

    private void selectGpuOwnedSubmitSlot() {
        VulkanCommandEncoder encoder = ownerDevice.createCommandEncoder();
        if (!(encoder instanceof IVulkanCommandEncoderAccess access))
            throw new IllegalStateException("Vulkan command encoder submit access unavailable");
        long submitIndex = access.combatant$currentSubmitIndex();
        if (submitIndex == activeSubmitIndex) return;
        int slot = Math.floorMod(submitIndex, SUBMIT_RING_SIZE);
        long previousOwner = submitRingOwners[slot];
        if (previousOwner != Long.MIN_VALUE && previousOwner > access.combatant$completedSubmitIndex())
            throw new IllegalStateException("GPU-owned SSBO submit slot is still in flight: " + descriptor.label());
        submitRingOwners[slot] = submitIndex;
        activeSubmitIndex = submitIndex;
        currentUploadBase = Math.multiplyExact((long) slot, uploadStride);
    }

    private void beginUploadSnapshot() {
        VulkanCommandEncoder encoder = ownerDevice.createCommandEncoder();
        if (!(encoder instanceof IVulkanCommandEncoderAccess access)) {
            throw new IllegalStateException("Vulkan command encoder bridge is unavailable for storage upload");
        }
        long slot = uploads.beginSnapshot(access.combatant$currentSubmitIndex(),
                access.combatant$completedSubmitIndex(), this::growUploadStorage);
        currentUploadBase = Math.multiplyExact(slot, uploadStride);
    }

    private void growUploadStorage(int uploadsPerSubmit) {
        long slots = Math.multiplyExact((long) SUBMIT_RING_SIZE, uploadsPerSubmit);
        NativeAllocation replacement = allocate(Math.multiplyExact(uploadStride, slots));
        NativeAllocation previous = storage;
        storage = replacement;
        // Recorded descriptors retain the old VkBuffer. Retire it only after its submission completes.
        retire(previous);
    }

    private void retire(NativeAllocation retired) {
        ownerDevice.createCommandEncoder().queueForDestroy(
                (Destroyable) () -> vmaDestroyBuffer(allocator, retired.buffer(), retired.allocation()));
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
        retire(storage);
    }

    private record NativeAllocation(long buffer, long allocation, long mappedAddress) { }
}
