/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.vulkan;

import com.mojang.renderpearl.backend.vulkan.Destroyable;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanPhysicalDevice;
import combatant.client.mixininterface.IVulkanBackendInfo;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.shader.RhiFeatureSupport;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.RhiValidation;
import combatant.client.render.engine.rhi.shader.RhiVolumeCapabilities;
import combatant.client.render.engine.rhi.shader.RhiVolumeView;
import combatant.client.render.engine.rhi.shader.StorageVolumeDescriptor;
import combatant.client.render.engine.rhi.shader.VolumeViewDescriptor;
import combatant.client.util.logging.DebugLog;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK12.*;

/** VkImage 3D allocation using the exact VkDevice/VMA allocator owned by Mojang. */
final class VulkanStorageVolume implements RhiStorageVolume, Destroyable {
    private final StorageVolumeDescriptor descriptor;
    private final VulkanDevice ownerDevice;
    private final VkDevice device;
    private final long allocator;
    private final long image;
    private final long allocation;
    private final Map<ViewKey, Long> imageViews = new HashMap<>();
    private final RhiStats stats;
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean generalLayout;

    VulkanStorageVolume(IVulkanBackendInfo backend, StorageVolumeDescriptor descriptor, RhiStats stats) {
        if (backend == null || backend.combatant$vma() == 0L || backend.combatant$vkDevice() == null) {
            throw new IllegalStateException("Mojang Vulkan device/VMA bridge is unavailable");
        }
        if (!(backend instanceof VulkanDevice vulkanDevice)) {
            throw new IllegalStateException("Vulkan backend bridge is not the active Mojang VulkanDevice");
        }
        this.descriptor = descriptor;
        this.ownerDevice = vulkanDevice;
        this.device = backend.combatant$vkDevice();
        this.allocator = backend.combatant$vma();
        this.stats = stats;
        int usage = imageUsage(descriptor);
        RhiVolumeCapabilities capabilities = capabilities(backend, descriptor);
        if (!capabilities.supportsDescriptor(descriptor)) {
            throw new UnsupportedOperationException(firstFailure(capabilities, descriptor));
        }

        long createdImage = 0L;
        long createdAllocation = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_3D)
                    .mipLevels(descriptor.mipLevels())
                    .arrayLayers(1)
                    .format(VulkanConst.toVk(descriptor.format()))
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .samples(VK_SAMPLE_COUNT_1_BIT);
            imageInfo.extent().set(descriptor.width(), descriptor.height(), descriptor.depth());

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            LongBuffer pImage = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            int createResult = vmaCreateImage(allocator, imageInfo, allocationInfo, pImage, pAllocation, null);
            if (createResult != VK_SUCCESS) {
                throw new IllegalStateException("vmaCreateImage(3D) failed: VkResult=" + createResult
                        + " label=" + descriptor.label());
            }
            createdImage = pImage.get(0);
            createdAllocation = pAllocation.get(0);
        } catch (RuntimeException | Error t) {
            if (createdImage != 0L && createdAllocation != 0L) {
                vmaDestroyImage(allocator, createdImage, createdAllocation);
            }
            throw t;
        }

        this.image = createdImage;
        this.allocation = createdAllocation;
        stats.storageVolumeAllocated(descriptor.approximateByteSize());
        DebugLog.debug("[CombatantRHI/Vulkan] allocated 3D volume '{}' {}x{}x{} mips={} format={} usage=0x{} approxBytes={}",
                descriptor.label(), descriptor.width(), descriptor.height(), descriptor.depth(), descriptor.mipLevels(),
                descriptor.format(), Integer.toHexString(usage), descriptor.approximateByteSize());
    }

    static RhiVolumeCapabilities capabilities(IVulkanBackendInfo backend, StorageVolumeDescriptor descriptor) {
        if (backend == null || backend.combatant$physicalDevice() == null) {
            return RhiVolumeCapabilities.unsupported("Mojang Vulkan physical-device bridge is unavailable");
        }
        if (descriptor == null) return RhiVolumeCapabilities.unsupported("Volume descriptor is null");
        VulkanPhysicalDevice physicalDevice = backend.combatant$physicalDevice();
        int vkFormat = VulkanConst.toVk(descriptor.format());
        if (vkFormat == VK_FORMAT_UNDEFINED) {
            RhiFeatureSupport format = RhiFeatureSupport.unsupported(
                    "Vulkan has no format mapping for 3D volume: " + descriptor.format(),
                    RhiFeatureSupport.Fallback.ALTERNATE_FORMAT);
            return new RhiVolumeCapabilities(format, format, format, format, format, format);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties formatProperties = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(physicalDevice.vkPhysicalDevice(), vkFormat, formatProperties);
            int optimal = formatProperties.optimalTilingFeatures();

            RhiFeatureSupport storage = !descriptor.storage()
                    ? RhiFeatureSupport.unsupported(
                    "3D volume descriptor lacks STORAGE_IMAGE usage",
                    RhiFeatureSupport.Fallback.RECREATE_WITH_REQUIRED_USAGE)
                    : ((optimal & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) != 0
                    ? RhiFeatureSupport.available()
                    : RhiFeatureSupport.unsupported(
                    "Vulkan format lacks STORAGE_IMAGE support for optimal tiling: " + descriptor.format(),
                    RhiFeatureSupport.Fallback.ALTERNATE_FORMAT));
            RhiFeatureSupport sampled = !descriptor.sampled()
                    ? RhiFeatureSupport.unsupported(
                    "3D volume descriptor lacks TEXTURE_BINDING usage",
                    RhiFeatureSupport.Fallback.RECREATE_WITH_REQUIRED_USAGE)
                    : ((optimal & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) != 0
                    ? RhiFeatureSupport.available()
                    : RhiFeatureSupport.unsupported(
                    "Vulkan format lacks SAMPLED_IMAGE support for optimal tiling: " + descriptor.format(),
                    RhiFeatureSupport.Fallback.ALTERNATE_FORMAT));

            int usage = imageUsage(descriptor);
            VkImageFormatProperties properties = VkImageFormatProperties.calloc(stack);
            int result = vkGetPhysicalDeviceImageFormatProperties(
                    physicalDevice.vkPhysicalDevice(), vkFormat, VK_IMAGE_TYPE_3D,
                    VK_IMAGE_TILING_OPTIMAL, usage, 0, properties);
            if (result != VK_SUCCESS) {
                RhiFeatureSupport allocation = RhiFeatureSupport.unsupported(
                        "Vulkan 3D image configuration is unsupported: format=" + descriptor.format()
                                + " usage=0x" + Integer.toHexString(usage) + " VkResult=" + result,
                        RhiFeatureSupport.Fallback.DROP_OPTIONAL_USAGE);
                return new RhiVolumeCapabilities(allocation, storage, sampled, allocation, allocation, allocation);
            }
            VkExtent3D max = properties.maxExtent();
            if (descriptor.width() > max.width() || descriptor.height() > max.height() || descriptor.depth() > max.depth()) {
                RhiFeatureSupport allocation = RhiFeatureSupport.unsupported(
                        "Vulkan 3D image exceeds device limit " + max.width() + "x" + max.height() + "x" + max.depth()
                                + ": " + descriptor.width() + "x" + descriptor.height() + "x" + descriptor.depth(),
                        RhiFeatureSupport.Fallback.REDUCE_RESOURCE);
                return new RhiVolumeCapabilities(allocation, storage, sampled, allocation, allocation, allocation);
            }
            if (descriptor.mipLevels() > properties.maxMipLevels()) {
                RhiFeatureSupport allocation = RhiFeatureSupport.unsupported(
                        "Vulkan 3D image mip count exceeds device limit " + properties.maxMipLevels()
                                + ": " + descriptor.mipLevels(),
                        RhiFeatureSupport.Fallback.REDUCE_RESOURCE);
                return new RhiVolumeCapabilities(allocation, storage, sampled, allocation, allocation, allocation);
            }

            RhiFeatureSupport allocation = RhiFeatureSupport.available();
            RhiFeatureSupport mipViews = sampled;
            RhiFeatureSupport copy = (descriptor.copySource() || descriptor.copyDestination())
                    ? RhiFeatureSupport.available()
                    : RhiFeatureSupport.unsupported(
                    "3D volume descriptor lacks COPY_SRC/COPY_DST usage",
                    RhiFeatureSupport.Fallback.RECREATE_WITH_REQUIRED_USAGE);

            RhiFeatureSupport clear;
            if (!descriptor.copyDestination()) {
                clear = RhiFeatureSupport.unsupported(
                        "3D volume descriptor lacks COPY_DST usage required by the RHI clear contract",
                        RhiFeatureSupport.Fallback.RECREATE_WITH_REQUIRED_USAGE);
            } else {
                VkImageFormatProperties clearProperties = VkImageFormatProperties.calloc(stack);
                int clearResult = vkGetPhysicalDeviceImageFormatProperties(
                        physicalDevice.vkPhysicalDevice(), vkFormat, VK_IMAGE_TYPE_3D,
                        VK_IMAGE_TILING_OPTIMAL, usage, 0, clearProperties);
                clear = clearResult == VK_SUCCESS
                        ? RhiFeatureSupport.available()
                        : RhiFeatureSupport.unsupported(
                        "Vulkan format/usage combination cannot be used for native clear: VkResult=" + clearResult,
                        RhiFeatureSupport.Fallback.ALTERNATE_FORMAT);
            }
            return new RhiVolumeCapabilities(allocation, storage, sampled, mipViews, copy, clear);
        }
    }

    private static int imageUsage(StorageVolumeDescriptor descriptor) {
        int usage = 0;
        if (descriptor.storage()) usage |= VK_IMAGE_USAGE_STORAGE_BIT;
        if (descriptor.sampled()) usage |= VK_IMAGE_USAGE_SAMPLED_BIT;
        if (descriptor.copySource()) usage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        if (descriptor.copyDestination()) usage |= VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        return usage;
    }

    private static String firstFailure(RhiVolumeCapabilities capabilities, StorageVolumeDescriptor descriptor) {
        if (!capabilities.allocation().supported()) return capabilities.allocation().reason();
        if (descriptor.storage() && !capabilities.storageViews().supported()) return capabilities.storageViews().reason();
        if (descriptor.sampled() && !capabilities.sampledViews().supported()) return capabilities.sampledViews().reason();
        if ((descriptor.copySource() || descriptor.copyDestination()) && !capabilities.copy().supported()) return capabilities.copy().reason();
        return "Vulkan 3D volume descriptor is unsupported";
    }

    @Override
    public StorageVolumeDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String backendName() {
        return "Vulkan";
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    long image() {
        ensureOpen();
        return image;
    }

    long imageView(int mipLevel) {
        return imageView(new RhiVolumeView(this, VolumeViewDescriptor.storage(mipLevel)));
    }

    long imageView(RhiVolumeView view) {
        ensureOpen();
        if (view == null || view.volume() != this) {
            throw new IllegalArgumentException("Volume view does not belong to this Vulkan resource");
        }
        view.requireValid();
        VolumeViewDescriptor descriptor = view.descriptor();
        ViewKey key = new ViewKey(descriptor.baseMipLevel(), descriptor.mipLevels());
        Long existing = imageViews.get(key);
        if (existing != null) return existing;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_3D)
                    .format(VulkanConst.toVk(this.descriptor.format()));
            viewInfo.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(descriptor.baseMipLevel())
                    .levelCount(descriptor.mipLevels())
                    .baseArrayLayer(0)
                    .layerCount(1);
            LongBuffer pView = stack.mallocLong(1);
            int result = vkCreateImageView(device, viewInfo, null, pView);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImageView(3D) failed: VkResult=" + result
                        + " label=" + this.descriptor.label() + " baseMip=" + descriptor.baseMipLevel()
                        + " mipLevels=" + descriptor.mipLevels());
            }
            long created = pView.get(0);
            imageViews.put(key, created);
            stats.storageVolumeViewCreated();
            DebugLog.debug("[CombatantRHI/Vulkan] created 3D view '{}' baseMip={} mips={} usage={}",
                    this.descriptor.label(), descriptor.baseMipLevel(), descriptor.mipLevels(), descriptor.usages());
            return created;
        }
    }

    void ensureGeneralLayout(VkCommandBuffer commandBuffer) {
        ensureOpen();
        if (generalLayout) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int dstAccess = 0;
            if (descriptor.storage() && descriptor.access().readable()) dstAccess |= VK_ACCESS_SHADER_READ_BIT;
            if (descriptor.storage() && descriptor.access().writable()) dstAccess |= VK_ACCESS_SHADER_WRITE_BIT;
            if (descriptor.sampled()) dstAccess |= VK_ACCESS_SHADER_READ_BIT;
            if (descriptor.copySource()) dstAccess |= VK_ACCESS_TRANSFER_READ_BIT;
            if (descriptor.copyDestination()) dstAccess |= VK_ACCESS_TRANSFER_WRITE_BIT;

            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(0)
                    .dstAccessMask(dstAccess)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(descriptor.mipLevels())
                    .baseArrayLayer(0)
                    .layerCount(1);

            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    0, null, null, barrier);
        }
        generalLayout = true;
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Storage volume is closed: " + descriptor.label());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            RhiValidation.doubleDestroy("Vulkan storage volume", descriptor.label());
            return;
        }
        ownerDevice.createCommandEncoder().queueForDestroy(this);
    }

    @Override
    public void destroy() {
        Set<Long> uniqueViews = new HashSet<>(imageViews.values());
        for (long view : uniqueViews) vkDestroyImageView(device, view, null);
        imageViews.clear();
        vmaDestroyImage(allocator, image, allocation);
        stats.storageVolumeDestroyed(descriptor.approximateByteSize());
        DebugLog.debug("[CombatantRHI/Vulkan] destroyed 3D volume '{}'", descriptor.label());
    }

    private record ViewKey(int baseMipLevel, int mipLevels) {}
}
