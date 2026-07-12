/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;

import java.nio.IntBuffer;

import static com.mojang.blaze3d.GpuFormat.S8_UINT;
import static org.lwjgl.vulkan.VK12.*;

@Mixin(VulkanRenderPipeline.class)
public abstract class VulkanRenderPipelineMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private static void combatant$beginCompile(VulkanDevice device,
                                               VulkanBindGroupLayout layout,
                                               RenderPipeline pipeline,
                                               long vertexModule,
                                               long fragmentModule,
                                               CallbackInfoReturnable<VulkanRenderPipeline> cir) {
        if (VulkanRenderStateBridge.vulkanBackendActive()) {
            VulkanRenderStateBridge.beginPipelineCompile(pipeline);
        }
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private static void combatant$endCompile(VulkanDevice device,
                                             VulkanBindGroupLayout layout,
                                             RenderPipeline pipeline,
                                             long vertexModule,
                                             long fragmentModule,
                                             CallbackInfoReturnable<VulkanRenderPipeline> cir) {
        VulkanRenderStateBridge.endPipelineCompile();
    }

    @Redirect(
            method = "compile",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryStack;ints(II)Ljava/nio/IntBuffer;")
    )
    private static IntBuffer combatant$addDynamicStates(MemoryStack stack, int first, int second) {
        if (!VulkanRenderStateBridge.vulkanBackendActive()) {
            return stack.ints(first, second);
        }
        if (!VulkanRenderStateBridge.forceDisableStencil()) {
            return stack.ints(first, second, VK_DYNAMIC_STATE_STENCIL_REFERENCE);
        }
        return stack.ints(first, second);
    }

    @ModifyArg(
            method = "compile",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkPipelineRasterizationStateCreateInfo;cullMode(I)Lorg/lwjgl/vulkan/VkPipelineRasterizationStateCreateInfo;"),
            index = 0
    )
    private static int combatant$disableCullForStencilMask(int original) {
        return VulkanRenderStateBridge.vulkanBackendActive() && VulkanRenderStateBridge.suppressColorWrites() ? VK_CULL_MODE_NONE : original;
    }

    @ModifyArg(
            method = "compile",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkPipelineDepthStencilStateCreateInfo;depthTestEnable(Z)Lorg/lwjgl/vulkan/VkPipelineDepthStencilStateCreateInfo;"),
            index = 0
    )
    private static boolean combatant$disableDepthTestForStencilMask(boolean original) {
        return (!VulkanRenderStateBridge.vulkanBackendActive() || !VulkanRenderStateBridge.suppressColorWrites()) && original;
    }

    @ModifyArg(
            method = "compile",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkPipelineDepthStencilStateCreateInfo;depthWriteEnable(Z)Lorg/lwjgl/vulkan/VkPipelineDepthStencilStateCreateInfo;"),
            index = 0
    )
    private static boolean combatant$disableDepthWriteForStencilMask(boolean original) {
        return (!VulkanRenderStateBridge.vulkanBackendActive() || !VulkanRenderStateBridge.suppressColorWrites()) && original;
    }

    @ModifyArg(
            method = "compile",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;rasterizationSamples(I)Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;"),
            index = 0
    )
    private static int combatant$useCurrentRenderPassSamples(int original) {
        return VulkanRenderStateBridge.vulkanBackendActive() ? VulkanRenderStateBridge.currentVkSampleCount() : original;
    }

    @ModifyArg(
            method = "compile",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkPipelineColorBlendAttachmentState$Buffer;colorWriteMask(I)Lorg/lwjgl/vulkan/VkPipelineColorBlendAttachmentState$Buffer;"),
            index = 0
    )
    private static int combatant$suppressColorWritesForStencilMask(int original) {
        return VulkanRenderStateBridge.vulkanBackendActive() && VulkanRenderStateBridge.suppressColorWrites() ? 0 : original;
    }

    @Inject(
            method = "compile",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK12;vkCreateGraphicsPipelines(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I", ordinal = 0, shift = At.Shift.BEFORE)
    )
    private static void combatant$configureStencilPipeline(VulkanDevice device,
                                                           VulkanBindGroupLayout layout,
                                                           RenderPipeline pipeline,
                                                           long vertexModule,
                                                           long fragmentModule,
                                                           CallbackInfoReturnable<VulkanRenderPipeline> cir,
                                                           @Local VkPipelineRenderingCreateInfoKHR rendering,
                                                           @Local VkPipelineDepthStencilStateCreateInfo depthStencil) {
        if (!VulkanRenderStateBridge.vulkanBackendActive()) return;
        if (!VulkanRenderStateBridge.pipelineNeedsStencilFormat(pipeline)) {
            depthStencil.stencilTestEnable(false);
            rendering.stencilAttachmentFormat(VK_FORMAT_UNDEFINED);
            return;
        }
        rendering.stencilAttachmentFormat(VulkanConst.toVk(S8_UINT));
        configureStencilState(depthStencil, pipeline);
    }

    @Unique
    private static void configureStencilState(VkPipelineDepthStencilStateCreateInfo state, RenderPipeline pipeline) {
        VulkanRenderStateBridge.StencilMode mode = VulkanRenderStateBridge.stencilTestEnabledForCurrentPipeline(pipeline)
                ? VulkanRenderStateBridge.stencilMode()
                : VulkanRenderStateBridge.StencilMode.DISABLED;
        if (mode == VulkanRenderStateBridge.StencilMode.DISABLED) {
            state.stencilTestEnable(false);
            return;
        }

        int passOp;
        int writeMask;
        switch (mode) {
            case WRITE -> {
                passOp = VK_STENCIL_OP_INCREMENT_AND_CLAMP;
                writeMask = 0xFF;
            }
            case RESTORE -> {
                passOp = VK_STENCIL_OP_DECREMENT_AND_CLAMP;
                writeMask = 0xFF;
            }
            case TEST -> {
                passOp = VK_STENCIL_OP_KEEP;
                writeMask = 0x00;
            }
            default -> {
                state.stencilTestEnable(false);
                return;
            }
        }

        state.stencilTestEnable(true);
        state.front()
                .failOp(VK_STENCIL_OP_KEEP)
                .passOp(passOp)
                .depthFailOp(VK_STENCIL_OP_KEEP)
                .compareOp(VK_COMPARE_OP_EQUAL)
                .compareMask(0xFF)
                .writeMask(writeMask)
                .reference(VulkanRenderStateBridge.dynamicStencilReference());
        state.back().set(state.front());
    }
}
