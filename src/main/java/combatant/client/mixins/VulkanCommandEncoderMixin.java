/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;
import combatant.client.mixininterface.IVulkanCommandEncoderAccess;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import combatant.client.render.engine.profiler.UiPipelineTelemetry;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.backend.vulkan.clip.VulkanShapeClipBridge;
import combatant.client.render.engine.rhi.backend.vulkan.msaa.VulkanMsaaResolveBridge;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;

import static org.lwjgl.vulkan.VK12.*;

@Mixin(VulkanCommandEncoder.class)
public abstract class VulkanCommandEncoderMixin implements IVulkanCommandEncoderAccess {
    @Unique
    private boolean combatant$renderPassActive;

    @Override
    @Invoker("commandBuffer")
    public abstract VkCommandBuffer combatant$commandBuffer();

    @Override
    public boolean combatant$renderPassActive() {
        return combatant$renderPassActive;
    }

    @Override
    @Accessor("currentSubmitIndex")
    public abstract long combatant$currentSubmitIndex();

    @Inject(method = "createRenderPass", at = @At("HEAD"))
    private void combatant$beginVulkanRenderPassState(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPassBackend> cir) {
        VulkanMsaaResolveBridge.beginRenderPass();
        VulkanRenderStateBridge.beginRenderPass(descriptor);
    }

    @Inject(method = "createRenderPass", at = @At("RETURN"))
    private void combatant$markRenderPassActive(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPassBackend> cir) {
        combatant$renderPassActive = cir.getReturnValue() != null;
    }


    @Inject(
            method = "createRenderPass",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdBeginRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderingInfo;)V", shift = At.Shift.BEFORE)
    )
    private void combatant$configureAttachments(RenderPassDescriptor descriptor,
                                                CallbackInfoReturnable<RenderPassBackend> cir,
                                                @Local MemoryStack stack,
                                                @Local VkRenderingInfo renderingInfo) {
        if (VulkanRenderStateBridge.currentRenderPassNeedsStencilAttachment()) {
            GpuTextureView view = VulkanShapeClipBridge.stencilAttachmentFor(descriptor);
            if (!(view instanceof VulkanGpuTextureView vkView) || view.isClosed()) {
                VulkanRenderStateBridge.markCurrentRenderPassStencilAttachment(false);
            } else {
                VulkanRenderStateBridge.markCurrentRenderPassStencilAttachment(true);
                boolean clear = VulkanShapeClipBridge.consumeStencilClear();
                if (clear) UiPipelineTelemetry.recordStencilClear();
                VkRenderingAttachmentInfo stencilAttachment = VkRenderingAttachmentInfo.calloc(stack).sType$Default();
                stencilAttachment
                        .imageView(vkView.vkImageView())
                        .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                        .loadOp(clear ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD);
                if (clear) {
                    VkClearDepthStencilValue clearValue = VkClearDepthStencilValue.calloc(stack).depth(0.0f).stencil(0);
                    stencilAttachment.clearValue(VkClearValue.calloc(stack).depthStencil(clearValue));
                }
                renderingInfo.pStencilAttachment(stencilAttachment);
            }
        }

        try {
            VulkanMsaaResolveBridge.configure(descriptor, renderingInfo);
        } catch (Throwable t) {
            boolean snapshotResolve = VulkanMsaaResolveBridge.snapshotActive();
            VulkanMsaaResolveBridge.abortRenderPass();
            if (!snapshotResolve) {
                VulkanRenderStateBridge.disableMsaaAfterFailure("dynamic rendering resolve configuration", t);
            }
            throw t instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("Failed to configure Vulkan MSAA resolve", t);
        }
    }


    @Inject(method = "submitRenderPass", at = @At("TAIL"))
    private void combatant$endVulkanRenderPassState(CallbackInfo ci) {
        combatant$renderPassActive = false;
        VulkanMsaaResolveBridge.finishRenderPass();
        try {
            CombatantRenderSystem.rhi().shapeClip().endRenderPass();
        } catch (Throwable ignored) {
            // Render pass teardown must never make Vulkan submission fail.
        }
        VulkanRenderStateBridge.endRenderPass();
    }
}
