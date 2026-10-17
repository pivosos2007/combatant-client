/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanInstance;
import com.mojang.renderpearl.backend.vulkan.VulkanPhysicalDevice;
import com.mojang.renderpearl.backend.vulkan.checkpoints.CheckpointExtension;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageFormatProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceDepthStencilResolvePropertiesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceLimits;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;
import combatant.client.mixininterface.IVulkanBackendInfo;
import combatant.client.util.logging.DebugLog;

import static org.lwjgl.vulkan.VK12.*;

@Mixin(VulkanDevice.class)
public abstract class VulkanDeviceMixin implements IVulkanBackendInfo {
    @Unique private VkDevice combatant$vkDevice;
    @Unique private long combatant$vma;
    @Unique private VulkanPhysicalDevice combatant$physicalDevice;
    @Unique private long combatant$minStorageBufferOffsetAlignment = 1L;
    @Unique private long combatant$maxStorageBufferRange;

    @Override
    public VkDevice combatant$vkDevice() {
        return combatant$vkDevice;
    }

    @Override
    public long combatant$vma() {
        return combatant$vma;
    }

    @Override
    public VulkanPhysicalDevice combatant$physicalDevice() {
        return combatant$physicalDevice;
    }

    @Override
    public long combatant$minStorageBufferOffsetAlignment() {
        return combatant$minStorageBufferOffsetAlignment;
    }

    @Override
    public long combatant$maxStorageBufferRange() {
        return combatant$maxStorageBufferRange;
    }


    @Inject(method = "<init>", at = @At("RETURN"))
    private void combatant$captureMsaaCapabilities(VulkanInstance instance,
                                                   VulkanPhysicalDevice physicalDevice,
                                                   FeatureSet enabledFeatures,
                                                   VkDevice device,
                                                   long vma,
                                                   CheckpointExtension checkpointExtension,
                                                   CallbackInfo ci) {
        combatant$vkDevice = device;
        combatant$vma = vma;
        combatant$physicalDevice = physicalDevice;
        if (physicalDevice == null) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Copy the limits we need while querying the VkPhysicalDevice directly. The LWJGL
            // structs exposed by Mojang are native-backed views and must not become long-lived
            // configuration state for Combatant.
            VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(physicalDevice.vkPhysicalDevice(), deviceProperties);
            VkPhysicalDeviceLimits limits = deviceProperties.limits();
            combatant$minStorageBufferOffsetAlignment = Math.max(1L, limits.minStorageBufferOffsetAlignment());
            combatant$maxStorageBufferRange = Integer.toUnsignedLong(limits.maxStorageBufferRange());
            int framebufferSamples = limits.framebufferColorSampleCounts()
                    & limits.framebufferDepthSampleCounts()
                    & limits.framebufferStencilSampleCounts();

            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);
            vkGetPhysicalDeviceFeatures(physicalDevice.vkPhysicalDevice(), features);
            VulkanRenderStateBridge.configureAdvancedShaderCapabilities(
                    physicalDevice.computeQueueFamilyAndIndex() != null,
                    features.tessellationShader(),
                    features.geometryShader(),
                    combatant$maxStorageBufferRange > 0L
            );
            int textureUsage = GpuTexture.USAGE_COPY_DST
                    | GpuTexture.USAGE_COPY_SRC
                    | GpuTexture.USAGE_TEXTURE_BINDING
                    | GpuTexture.USAGE_RENDER_ATTACHMENT;
            framebufferSamples &= combatant$imageSampleCounts(
                    stack,
                    physicalDevice,
                    GpuFormat.RGBA8_UNORM,
                    VulkanConst.textureUsageToVk(textureUsage, GpuFormat.RGBA8_UNORM)
            );
            framebufferSamples &= combatant$imageSampleCounts(
                    stack,
                    physicalDevice,
                    GpuFormat.D32_FLOAT,
                    VulkanConst.textureUsageToVk(textureUsage, GpuFormat.D32_FLOAT)
            );
            framebufferSamples &= combatant$imageSampleCounts(
                    stack,
                    physicalDevice,
                    GpuFormat.S8_UINT,
                    VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
            );

            VkPhysicalDeviceDepthStencilResolvePropertiesKHR resolve =
                    VkPhysicalDeviceDepthStencilResolvePropertiesKHR.calloc(stack).sType$Default();
            VkPhysicalDeviceProperties2 properties2 = VkPhysicalDeviceProperties2.calloc(stack)
                    .sType$Default()
                    .pNext(resolve);
            vkGetPhysicalDeviceProperties2(physicalDevice.vkPhysicalDevice(), properties2);
            VulkanRenderStateBridge.configureMsaaCapabilities(
                    framebufferSamples,
                    resolve.supportedDepthResolveModes(),
                    resolve.independentResolveNone()
            );
        } catch (Throwable t) {
            VulkanRenderStateBridge.configureMsaaCapabilities(1, 0, false);
            DebugLog.warnOnChange(
                    "msaa.vulkan.capabilities.failed",
                    t.getClass().getSimpleName() + "|" + t.getMessage(),
                    "[MSAA/Vulkan] capability query failed; MSAA remains disabled: %s: %s",
                    t.getClass().getSimpleName(),
                    t.getMessage()
            );
        }
    }

    @Unique
    private static int combatant$imageSampleCounts(MemoryStack stack,
                                                   VulkanPhysicalDevice physicalDevice,
                                                   GpuFormat format,
                                                   int usage) {
        VkImageFormatProperties properties = VkImageFormatProperties.calloc(stack);
        int result = vkGetPhysicalDeviceImageFormatProperties(
                physicalDevice.vkPhysicalDevice(),
                VulkanConst.toVk(format),
                VK_IMAGE_TYPE_2D,
                VK_IMAGE_TILING_OPTIMAL,
                usage,
                0,
                properties
        );
        return result == VK_SUCCESS ? properties.sampleCounts() : VK_SAMPLE_COUNT_1_BIT;
    }

}
