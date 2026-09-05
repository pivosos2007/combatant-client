/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanInstance;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.mojang.blaze3d.vulkan.checkpoints.CheckpointExtension;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageFormatProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceDepthStencilResolvePropertiesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceLimits;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;
import combatant.client.mixininterface.IVulkanBackendInfo;
import combatant.client.util.logging.DebugLog;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.vulkan.VK12.*;

@Mixin(VulkanDevice.class)
public abstract class VulkanDeviceMixin implements IVulkanBackendInfo {
    @Shadow
    private ShaderSource defaultShaderSource;

    @Unique
    private final Map<VulkanRenderStateBridge.PipelineVariantKey, VulkanRenderPipeline> combatant$pipelineVariants = new HashMap<>();
    @Unique private VkDevice combatant$vkDevice;
    @Unique private long combatant$vma;
    @Unique private VulkanPhysicalDevice combatant$physicalDevice;

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


    @Invoker("compilePipeline")
    protected abstract VulkanRenderPipeline combatant$compilePipelineVariant(RenderPipeline pipeline, ShaderSource source);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void combatant$captureMsaaCapabilities(ShaderSource shaderSource,
                                                   VulkanInstance instance,
                                                   VulkanPhysicalDevice physicalDevice,
                                                   Set<String> enabledExtensions,
                                                   VkDevice device,
                                                   long vma,
                                                   CheckpointExtension checkpointExtension,
                                                   CallbackInfo ci) {
        combatant$vkDevice = device;
        combatant$vma = vma;
        combatant$physicalDevice = physicalDevice;
        if (physicalDevice == null || physicalDevice.vkPhysicalDeviceProperties() == null) return;
        VkPhysicalDeviceLimits limits = physicalDevice.vkPhysicalDeviceProperties().limits();
        int framebufferSamples = limits.framebufferColorSampleCounts()
                & limits.framebufferDepthSampleCounts()
                & limits.framebufferStencilSampleCounts();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);
            vkGetPhysicalDeviceFeatures(physicalDevice.vkPhysicalDevice(), features);
            VulkanRenderStateBridge.configureAdvancedShaderCapabilities(
                    physicalDevice.computeQueueFamilyAndIndex() != null,
                    features.tessellationShader(),
                    features.geometryShader(),
                    limits.maxStorageBufferRange() > 0
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
            VkPhysicalDeviceProperties2 properties = VkPhysicalDeviceProperties2.calloc(stack)
                    .sType$Default()
                    .pNext(resolve);
            vkGetPhysicalDeviceProperties2(physicalDevice.vkPhysicalDevice(), properties);
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

    @Inject(method = "getOrCompilePipeline", at = @At("HEAD"), cancellable = true)
    private void combatant$getOrCompilePipelineVariant(RenderPipeline pipeline,
                                                       CallbackInfoReturnable<VulkanRenderPipeline> cir) {
        if (!VulkanRenderStateBridge.needsPipelineVariant(pipeline)) return;

        VulkanRenderStateBridge.PipelineVariantKey key = VulkanRenderStateBridge.pipelineVariantKey(pipeline);
        VulkanRenderPipeline variant = combatant$pipelineVariants.get(key);
        if (variant != null) {
            cir.setReturnValue(variant);
            return;
        }

        try {
            variant = combatant$compilePipelineVariant(pipeline, defaultShaderSource);
            if (variant == null || !variant.isValid()) {
                throw new IllegalStateException("Vulkan returned an invalid pipeline variant");
            }
            combatant$pipelineVariants.put(key, variant);
            DebugLog.stencilOnChange(
                    "vulkan.pipeline.variant.compile",
                    key.toString(),
                    "[Vulkan/RHI] compiled pipeline variant %s",
                    key
            );
            cir.setReturnValue(variant);
        } catch (Throwable t) {
            if (key.usesMsaa()) {
                VulkanRenderStateBridge.disableMsaaAfterFailure("pipeline variant compile " + key, t);
            }
            if (key.usesStencil()) {
                VulkanRenderStateBridge.disableStencilAfterFailure("pipeline variant compile " + key, t);
            }
            DebugLog.error("[Vulkan/RHI] pipeline variant compile failed for " + key, t);
            // The current dynamic-rendering scope is already active. Falling through to vanilla's
            // single-sample/no-stencil pipeline would bind an incompatible VkPipeline and can lose
            // the device, so abort on the CPU and only disable the feature for future passes.
            throw t instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("Failed to compile Vulkan pipeline variant " + key, t);
        }
    }

    @Inject(method = "clearPipelineCache", at = @At("RETURN"))
    private void combatant$clearPipelineVariants(CallbackInfo ci) {
        if (combatant$pipelineVariants.isEmpty()) return;
        for (VulkanRenderPipeline pipeline : combatant$pipelineVariants.values()) {
            try {
                pipeline.destroy();
            } catch (Throwable t) {
                DebugLog.warnOnChange(
                        "vulkan.pipeline.variant.destroy.failed",
                        t.getClass().getSimpleName() + "|" + t.getMessage(),
                        "[Vulkan/RHI] failed to destroy pipeline variant: %s: %s",
                        t.getClass().getSimpleName(),
                        t.getMessage()
                );
            }
        }
        combatant$pipelineVariants.clear();
    }
}
