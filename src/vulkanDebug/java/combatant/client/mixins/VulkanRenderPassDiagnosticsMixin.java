package combatant.client.mixins;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.mojang.blaze3d.vulkan.checkpoints.CheckpointExtension;
import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.engine.rhi.backend.vulkan.debug.VulkanCrashDiagnostics;

import java.nio.IntBuffer;
import java.util.Collection;
import java.util.function.Supplier;

@Mixin(VulkanRenderPass.class)
public abstract class VulkanRenderPassDiagnosticsMixin {

    @Inject(method = "<init>", at = @At("HEAD"))
    private static void combatant$ctorHead(VulkanDevice device,
                                           VulkanCommandEncoder encoder,
                                           VkCommandBuffer commandBuffer,
                                           CheckpointExtension.CheckpointStorage checkpointStorage,
                                           RenderPass.RenderArea renderArea,
                                           int outputWidth,
                                           int outputHeight,
                                           boolean hasDepth,
                                           Supplier<String> label,
                                           CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.object.ctor.head",
                "label", labelText(label),
                "area", areaText(renderArea),
                "output", outputWidth + "x" + outputHeight,
                "depth", hasDepth,
                "commandBuffer", commandBuffer == null ? 0L : commandBuffer.address());
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void combatant$ctorTail(VulkanDevice device,
                                    VulkanCommandEncoder encoder,
                                    VkCommandBuffer commandBuffer,
                                    CheckpointExtension.CheckpointStorage checkpointStorage,
                                    RenderPass.RenderArea renderArea,
                                    int outputWidth,
                                    int outputHeight,
                                    boolean hasDepth,
                                    Supplier<String> label,
                                    CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.object.ctor.tail",
                "label", labelText(label),
                "area", areaText(renderArea));
    }


    @Inject(method = "setPipeline", at = @At("HEAD"))
    private void combatant$setPipelineHead(RenderPipeline pipeline, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.setpipeline.head", "pipeline", pipelineId(pipeline));
    }

    @Inject(
            method = "setPipeline",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vulkan/VulkanDevice;getOrCompilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)Lcom/mojang/blaze3d/vulkan/VulkanRenderPipeline;", shift = At.Shift.BEFORE)
    )
    private void combatant$compilePipelineBefore(RenderPipeline pipeline, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.pipeline.get.before", "pipeline", pipelineId(pipeline));
    }

    @Inject(
            method = "setPipeline",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vulkan/VulkanDevice;getOrCompilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)Lcom/mojang/blaze3d/vulkan/VulkanRenderPipeline;", shift = At.Shift.AFTER)
    )
    private void combatant$compilePipelineAfter(RenderPipeline pipeline, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.pipeline.get.after", "pipeline", pipelineId(pipeline));
    }

    @Inject(
            method = "setPipeline",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK12;vkCmdBindPipeline(Lorg/lwjgl/vulkan/VkCommandBuffer;IJ)V", shift = At.Shift.BEFORE)
    )
    private void combatant$bindPipelineBefore(RenderPipeline pipeline, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.bindpipeline.before", "pipeline", pipelineId(pipeline));
    }

    @Inject(
            method = "setPipeline",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK12;vkCmdBindPipeline(Lorg/lwjgl/vulkan/VkCommandBuffer;IJ)V", shift = At.Shift.AFTER)
    )
    private void combatant$bindPipelineAfter(RenderPipeline pipeline, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.bindpipeline.after", "pipeline", pipelineId(pipeline));
    }

    @Inject(method = "setPipeline", at = @At("TAIL"))
    private void combatant$setPipelineTail(RenderPipeline pipeline, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.setpipeline.tail", "pipeline", pipelineId(pipeline));
    }

    @Inject(method = "bindTexture", at = @At("HEAD"))
    private void combatant$bindTextureHead(String name, GpuTextureView view, GpuSampler sampler, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.bindtexture.head", "name", name, "view", objectText(view), "sampler", objectText(sampler));
    }

    @Inject(method = "setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"))
    private void combatant$setUniformSliceHead(String name, GpuBufferSlice slice, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.uniform.slice.head", "name", name, "slice", sliceText(slice));
    }

    @Inject(method = "setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBuffer;)V", at = @At("HEAD"))
    private void combatant$setUniformBufferHead(String name, GpuBuffer buffer, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.uniform.buffer.head", "name", name, "buffer", bufferText(buffer));
    }

    @Inject(method = "enableScissor", at = @At("HEAD"))
    private void combatant$enableScissorHead(int x, int y, int width, int height, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.scissor.enable.head", "rect", x + "," + y + " " + width + "x" + height);
    }

    @Inject(method = "setVertexBuffer", at = @At("HEAD"))
    private void combatant$vertexBufferHead(int slot, GpuBufferSlice slice, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.vertexbuffer.head", "slot", slot, "slice", sliceText(slice));
    }

    @Inject(method = "setIndexBuffer", at = @At("HEAD"))
    private void combatant$indexBufferHead(GpuBuffer buffer, IndexType indexType, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.indexbuffer.head", "buffer", bufferText(buffer), "type", indexType);
    }

    @Inject(method = "pushDescriptors", at = @At("HEAD"))
    private void combatant$pushDescriptorsHead(CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.pushdescriptors.head");
    }

    @Inject(
            method = "pushDescriptors",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRPushDescriptor;vkCmdPushDescriptorSetKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;IJILorg/lwjgl/vulkan/VkWriteDescriptorSet$Buffer;)V", shift = At.Shift.BEFORE)
    )
    private void combatant$pushDescriptorsNativeBefore(CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.pushdescriptors.native.before");
    }

    @Inject(
            method = "pushDescriptors",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRPushDescriptor;vkCmdPushDescriptorSetKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;IJILorg/lwjgl/vulkan/VkWriteDescriptorSet$Buffer;)V", shift = At.Shift.AFTER)
    )
    private void combatant$pushDescriptorsNativeAfter(CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.pushdescriptors.native.after");
    }

    @Inject(method = "pushDescriptors", at = @At("TAIL"))
    private void combatant$pushDescriptorsTail(CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.pushdescriptors.tail");
    }

    @Inject(method = "drawIndexed", at = @At("HEAD"))
    private void combatant$drawIndexedHead(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbDraw("renderpass.drawindexed.head",
                "indices", indexCount,
                "instances", instanceCount,
                "firstIndex", firstIndex,
                "vertexOffset", vertexOffset,
                "firstInstance", firstInstance);
    }

    @Inject(
            method = "drawIndexed",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK12;vkCmdDrawIndexed(Lorg/lwjgl/vulkan/VkCommandBuffer;IIIII)V", shift = At.Shift.BEFORE)
    )
    private void combatant$drawIndexedNativeBefore(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbDraw("renderpass.drawindexed.native.before", "indices", indexCount);
    }

    @Inject(
            method = "drawIndexed",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK12;vkCmdDrawIndexed(Lorg/lwjgl/vulkan/VkCommandBuffer;IIIII)V", shift = At.Shift.AFTER)
    )
    private void combatant$drawIndexedNativeAfter(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbDraw("renderpass.drawindexed.native.after", "indices", indexCount);
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void combatant$drawHead(int vertexCount, int instanceCount, int firstVertex, int firstInstance, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbDraw("renderpass.draw.head",
                "vertices", vertexCount,
                "instances", instanceCount,
                "firstVertex", firstVertex,
                "firstInstance", firstInstance);
    }

    @Inject(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK12;vkCmdDraw(Lorg/lwjgl/vulkan/VkCommandBuffer;IIII)V", shift = At.Shift.BEFORE)
    )
    private void combatant$drawNativeBefore(int vertexCount, int instanceCount, int firstVertex, int firstInstance, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbDraw("renderpass.draw.native.before", "vertices", vertexCount);
    }

    @Inject(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK12;vkCmdDraw(Lorg/lwjgl/vulkan/VkCommandBuffer;IIII)V", shift = At.Shift.AFTER)
    )
    private void combatant$drawNativeAfter(int vertexCount, int instanceCount, int firstVertex, int firstInstance, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbDraw("renderpass.draw.native.after", "vertices", vertexCount);
    }

    @Inject(method = "multiDraw(Ljava/nio/IntBuffer;III)V", at = @At("HEAD"))
    private void combatant$multiDrawHead(IntBuffer buffer, int firstInstance, int drawCount, int stride, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbDraw("renderpass.multidraw.head", "draws", drawCount, "stride", stride, "remaining", buffer == null ? -1 : buffer.remaining());
    }

    @Inject(method = "multiDrawIndexed(Ljava/nio/IntBuffer;III)V", at = @At("HEAD"))
    private void combatant$multiDrawIndexedHead(IntBuffer buffer, int firstInstance, int drawCount, int stride, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbDraw("renderpass.multidrawindexed.head", "draws", drawCount, "stride", stride, "remaining", buffer == null ? -1 : buffer.remaining());
    }

    @Inject(method = "multiDrawIndexed(Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)V", at = @At("HEAD"))
    private void combatant$multiDrawIndexedPointerHead(PointerBuffer firstIndex, IntBuffer indexCount, IntBuffer baseVertex, int drawCount, CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbDraw("renderpass.multidrawindexed.pointer.head", "draws", drawCount);
    }

    @Inject(method = "drawMultipleIndexed", at = @At("HEAD"))
    private <T> void combatant$drawMultipleIndexedHead(Collection<RenderPass.Draw<T>> draws,
                                                       GpuBuffer indexBuffer,
                                                       IndexType indexType,
                                                       Collection<String> dynamicUniforms,
                                                       T extraData,
                                                       CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet("renderpass.drawmultipleindexed.head",
                "draws", draws == null ? -1 : draws.size(),
                "indexBuffer", bufferText(indexBuffer),
                "indexType", indexType,
                "dynamicUniforms", dynamicUniforms == null ? -1 : dynamicUniforms.size());
    }

    private static String pipelineId(RenderPipeline pipeline) {
        if (pipeline == null) return "<null>";
        try {
            return String.valueOf(pipeline.getLocation());
        } catch (Throwable ignored) {
            return pipeline.toString();
        }
    }

    private static String labelText(Supplier<String> label) {
        if (label == null) return "<null>";
        try {
            String value = label.get();
            return value == null ? "<null>" : value;
        } catch (Throwable t) {
            return "<label-error:" + t.getClass().getSimpleName() + ">";
        }
    }

    private static String areaText(RenderPass.RenderArea area) {
        if (area == null) return "<null>";
        return area.x() + "," + area.y() + " " + area.width() + "x" + area.height();
    }

    private static String sliceText(GpuBufferSlice slice) {
        if (slice == null) return "<null>";
        try {
            return "offset=" + slice.offset() + " buffer=" + bufferText(slice.buffer());
        } catch (Throwable t) {
            return "<slice-error:" + t.getClass().getSimpleName() + ">";
        }
    }

    private static String bufferText(GpuBuffer buffer) {
        if (buffer == null) return "<null>";
        try {
            return buffer.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(buffer)) + " closed=" + buffer.isClosed();
        } catch (Throwable t) {
            return buffer.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(buffer)) + " error=" + t.getClass().getSimpleName();
        }
    }

    private static String objectText(Object object) {
        if (object == null) return "<null>";
        return object.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }
}
