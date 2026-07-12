/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;

import java.util.function.Supplier;

@Mixin(VulkanRenderPass.class)
public abstract class VulkanRenderPassMixin {
    @Invoker("commandBuffer")
    protected abstract VkCommandBuffer combatant$commandBuffer();

    @Inject(method = "pushDebugGroup", at = @At("HEAD"), cancellable = true)
    private void combatant$disableRenderPassDebugGroup(Supplier<String> label, CallbackInfo ci) {
        if (!VulkanRenderStateBridge.forceDisableDebugLabels()) return;
        ci.cancel();
    }

    @Inject(method = "popDebugGroup", at = @At("HEAD"), cancellable = true)
    private void combatant$disableRenderPassDebugGroupPop(CallbackInfo ci) {
        if (!VulkanRenderStateBridge.forceDisableDebugLabels()) return;
        ci.cancel();
    }

    @Inject(method = "setPipeline", at = @At("TAIL"))
    private void combatant$applyDynamicPipelineState(RenderPipeline pipeline, CallbackInfo ci) {
        if (!VulkanRenderStateBridge.vulkanBackendActive()) return;
        VkCommandBuffer commandBuffer = combatant$commandBuffer();
        if (!VulkanRenderStateBridge.forceDisableStencil() && VulkanRenderStateBridge.currentRenderPassHasStencilAttachment()) {
            VK12.vkCmdSetStencilReference(
                    commandBuffer,
                    VK12.VK_STENCIL_FACE_FRONT_AND_BACK,
                    VulkanRenderStateBridge.dynamicStencilReference()
            );
        }
    }
}
