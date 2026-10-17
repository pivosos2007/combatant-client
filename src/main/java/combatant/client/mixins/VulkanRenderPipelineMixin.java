/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanPipelineVariants;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK12.*;

@Mixin(VulkanRenderPipeline.class)
public abstract class VulkanRenderPipelineMixin {
    @Inject(method = "compile", at = @At("RETURN"))
    private static void combatant$rememberOriginalCreateInfo(VulkanDevice device,
                                                             BackendRenderPipeline.CreateInfo createInfo,
                                                             CallbackInfoReturnable<VulkanRenderPipeline> cir) {
        VulkanPipelineVariants.registerBase(cir.getReturnValue(), createInfo);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void combatant$closeVariants(CallbackInfo ci) {
        VulkanPipelineVariants.release((VulkanRenderPipeline) (Object) this);
    }

    @Redirect(method = "compile", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/system/MemoryStack;ints(II)Ljava/nio/IntBuffer;"))
    private static IntBuffer combatant$dynamicStencilReference(MemoryStack stack, int first, int second) {
        if (VulkanPipelineVariants.compilingVariant()
                && VulkanRenderStateBridge.currentPipelineUsesDynamicStencilReference()) {
            return stack.ints(first, second, VK_DYNAMIC_STATE_STENCIL_REFERENCE);
        }
        return stack.ints(first, second);
    }

    @ModifyArg(method = "compile", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VkPipelineRasterizationStateCreateInfo;cullMode(I)Lorg/lwjgl/vulkan/VkPipelineRasterizationStateCreateInfo;"))
    private static int combatant$stencilMaskCull(int original) {
        return VulkanPipelineVariants.compilingVariant() && VulkanRenderStateBridge.suppressColorWrites()
                ? VK_CULL_MODE_NONE : original;
    }

    @ModifyArg(method = "compile", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VkPipelineDepthStencilStateCreateInfo;depthTestEnable(Z)Lorg/lwjgl/vulkan/VkPipelineDepthStencilStateCreateInfo;"))
    private static boolean combatant$stencilMaskDepthTest(boolean original) {
        return original && !(VulkanPipelineVariants.compilingVariant() && VulkanRenderStateBridge.suppressColorWrites());
    }

    @ModifyArg(method = "compile", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VkPipelineDepthStencilStateCreateInfo;depthWriteEnable(Z)Lorg/lwjgl/vulkan/VkPipelineDepthStencilStateCreateInfo;"))
    private static boolean combatant$stencilMaskDepthWrite(boolean original) {
        return original && !(VulkanPipelineVariants.compilingVariant() && VulkanRenderStateBridge.suppressColorWrites());
    }

    @ModifyArg(method = "compile", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;rasterizationSamples(I)Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;"))
    private static int combatant$renderPassSamples(int original) {
        return VulkanPipelineVariants.compilingVariant()
                ? VulkanRenderStateBridge.currentVkSampleCount() : original;
    }

    @ModifyArg(method = "compile", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VkPipelineColorBlendAttachmentState$Buffer;colorWriteMask(I)Lorg/lwjgl/vulkan/VkPipelineColorBlendAttachmentState$Buffer;"))
    private static int combatant$stencilMaskColorWrites(int original) {
        return VulkanPipelineVariants.compilingVariant() && VulkanRenderStateBridge.suppressColorWrites()
                ? 0 : original;
    }

    @Inject(method = "compile", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VK12;vkCreateGraphicsPipelines(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I",
            ordinal = 0, shift = At.Shift.BEFORE))
    private static void combatant$stencilPipeline(VulkanDevice device,
                                                 BackendRenderPipeline.CreateInfo createInfo,
                                                 CallbackInfoReturnable<VulkanRenderPipeline> cir,
                                                 @Local VkPipelineRenderingCreateInfoKHR rendering,
                                                 @Local VkPipelineDepthStencilStateCreateInfo depthStencil) {
        if (!VulkanPipelineVariants.compilingVariant()) return;
        if (!VulkanRenderStateBridge.currentRenderPassHasStencilAttachment()) {
            depthStencil.stencilTestEnable(false);
            rendering.stencilAttachmentFormat(VK_FORMAT_UNDEFINED);
            return;
        }
        rendering.stencilAttachmentFormat(VulkanConst.toVk(GpuFormat.S8_UINT));
        configureStencilState(depthStencil, VulkanRenderStateBridge.currentCompilingPipeline());
    }

    @Unique
    private static void configureStencilState(VkPipelineDepthStencilStateCreateInfo state, RenderPipeline pipeline) {
        VulkanRenderStateBridge.StencilMode mode = pipeline != null
                && VulkanRenderStateBridge.stencilTestEnabledForCurrentPipeline(pipeline)
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
                writeMask = 0;
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
