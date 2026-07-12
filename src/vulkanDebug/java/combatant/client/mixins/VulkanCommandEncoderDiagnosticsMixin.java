package combatant.client.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.render.engine.rhi.backend.vulkan.debug.VulkanCrashDiagnostics;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;

@Mixin(VulkanCommandEncoder.class)
public abstract class VulkanCommandEncoderDiagnosticsMixin {
    @Inject(method = "createRenderPass", at = @At("HEAD"))
    private void combatant$renderPassCreateHead(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPassBackend> cir) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.create.head",
                "colors", descriptor == null || descriptor.colorAttachments() == null ? -1 : descriptor.colorAttachments().size(),
                "depth", descriptor != null && descriptor.depthAttachment() != null,
                "samples", VulkanRenderStateBridge.currentRenderPassSamples(),
                "stencil", VulkanRenderStateBridge.currentRenderPassNeedsStencilAttachment());
    }

    @Inject(
            method = "createRenderPass",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdBeginRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderingInfo;)V", shift = At.Shift.BEFORE)
    )
    private void combatant$beforeBeginRendering(RenderPassDescriptor descriptor,
                                                CallbackInfoReturnable<RenderPassBackend> cir,
                                                @Local VkRenderingInfo renderingInfo) {
        int colorResolves = 0;
        VkRenderingAttachmentInfo.Buffer colors = renderingInfo == null ? null : renderingInfo.pColorAttachments();
        if (colors != null) {
            for (int i = 0; i < colors.remaining(); i++) {
                if (colors.get(i).resolveImageView() != 0L) colorResolves++;
            }
        }
        VkRenderingAttachmentInfo depth = renderingInfo == null ? null : renderingInfo.pDepthAttachment();
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.beginrendering.before",
                "colors", descriptor == null || descriptor.colorAttachments() == null ? -1 : descriptor.colorAttachments().size(),
                "depth", descriptor != null && descriptor.depthAttachment() != null,
                "samples", VulkanRenderStateBridge.currentRenderPassSamples(),
                "stencil", VulkanRenderStateBridge.currentRenderPassNeedsStencilAttachment(),
                "colorResolves", colorResolves,
                "depthResolveMode", depth == null ? 0 : depth.resolveMode(),
                "depthResolveView", depth == null ? 0L : depth.resolveImageView(),
                "renderingInfo", renderingInfo == null ? 0L : renderingInfo.address());
    }

    @Inject(
            method = "createRenderPass",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdBeginRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderingInfo;)V", shift = At.Shift.AFTER)
    )
    private void combatant$afterBeginRendering(RenderPassDescriptor descriptor,
                                               CallbackInfoReturnable<RenderPassBackend> cir) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.beginrendering.after");
    }

    @Inject(method = "createRenderPass", at = @At("RETURN"))
    private void combatant$renderPassCreated(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPassBackend> cir) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.create.return",
                "backend", cir.getReturnValue() == null ? "<null>" : cir.getReturnValue().getClass().getName());
    }

    @Inject(method = "submitRenderPass", at = @At("HEAD"))
    private void combatant$submitVulkanRenderPassHead(CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.submit.head");
    }

    @Inject(
            method = "submitRenderPass",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdEndRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;)V", shift = At.Shift.BEFORE)
    )
    private void combatant$beforeEndRendering(CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.endrendering.before");
    }

    @Inject(
            method = "submitRenderPass",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdEndRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;)V", shift = At.Shift.AFTER)
    )
    private void combatant$afterEndRendering(CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.endrendering.after");
    }

    @Inject(method = "submitRenderPass", at = @At("TAIL"))
    private void combatant$submitVulkanRenderPassTail(CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.submit.tail");
    }
}
