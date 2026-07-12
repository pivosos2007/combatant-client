package combatant.client.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanBindGroupLayout;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.render.engine.rhi.backend.vulkan.debug.VulkanCrashDiagnostics;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;

@Mixin(VulkanRenderPipeline.class)
public abstract class VulkanRenderPipelineDiagnosticsMixin {
    @Inject(
            method = "compile",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK12;vkCreateGraphicsPipelines(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I", ordinal = 0, shift = At.Shift.BEFORE)
    )
    private static void combatant$logPipelineCompile(VulkanDevice device,
                                                     VulkanBindGroupLayout layout,
                                                     RenderPipeline pipeline,
                                                     long vertexModule,
                                                     long fragmentModule,
                                                     CallbackInfoReturnable<VulkanRenderPipeline> cir,
                                                     @Local VkPipelineRenderingCreateInfoKHR rendering,
                                                     @Local VkPipelineDepthStencilStateCreateInfo depthStencil) {
        VulkanCrashDiagnostics.breadcrumbQuiet("pipeline.compile.before",
                "pipeline", pipeline == null || pipeline.getLocation() == null ? "<unknown>" : pipeline.getLocation(),
                "samples", VulkanRenderStateBridge.currentVkSampleCount(),
                "stencil", VulkanRenderStateBridge.pipelineNeedsStencilFormat(pipeline),
                "stencilMode", VulkanRenderStateBridge.stencilMode());
    }
}
