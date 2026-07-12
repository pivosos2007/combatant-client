package combatant.client.mixins;

import com.mojang.blaze3d.vulkan.checkpoints.CheckpointExtension;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.engine.rhi.backend.vulkan.debug.VulkanCrashDiagnostics;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;

import java.util.function.Supplier;

@Mixin(targets = "com.mojang.blaze3d.vulkan.checkpoints.AbstractCheckpointStorage")
public abstract class VulkanCheckpointDiagnosticsMixin {
    @Inject(method = "recordCheckpoint(Lorg/lwjgl/vulkan/VkCommandBuffer;Lcom/mojang/blaze3d/vulkan/checkpoints/CheckpointExtension$CheckpointType;Ljava/util/function/Supplier;)V", at = @At("HEAD"))
    private void combatant$logCheckpoint(VkCommandBuffer commandBuffer,
                                         CheckpointExtension.CheckpointType type,
                                         Supplier<String> label,
                                         CallbackInfo ci) {
        VulkanCrashDiagnostics.breadcrumbQuiet(VulkanRenderStateBridge.forceDisableCheckpoints() ? "checkpoint.disabled" : "checkpoint.record",
                "type", type,
                "label", safeLabel(label),
                "commandBuffer", commandBuffer == null ? 0L : commandBuffer.address());
    }

    private static String safeLabel(Supplier<String> label) {
        if (label == null) return "<null>";
        try {
            return label.get();
        } catch (Throwable t) {
            return "<label-error:" + VulkanCrashDiagnostics.throwable(t) + ">";
        }
    }
}
