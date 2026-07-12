package combatant.client.mixins;

import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.render.engine.rhi.backend.vulkan.debug.VulkanCrashDiagnostics;

@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanQueue$Submission")
public abstract class VulkanQueueSubmissionMixin {
    @Redirect(
            method = "close",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRSynchronization2;vkQueueSubmit2KHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo2$Buffer;J)I")
    )
    private int combatant$diagnoseQueueSubmit(VkQueue queue, VkSubmitInfo2.Buffer submits, long fence) {
        int submitCount = submits == null ? -1 : submits.remaining();
        VulkanCrashDiagnostics.breadcrumbQuiet("queue.submit.before", "submits", submitCount, "fence", fence);
        int result = KHRSynchronization2.vkQueueSubmit2KHR(queue, submits, fence);
        VulkanCrashDiagnostics.breadcrumbQuiet("queue.submit.after", "result", result, "submits", submitCount, "fence", fence);
        if (result != 0) {
            VulkanCrashDiagnostics.breadcrumb("queue.submit.failed", "result", result, "submits", submitCount, "fence", fence);
        }
        return result;
    }
}
