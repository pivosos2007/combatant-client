package combatant.client.mixins;

import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanInstance;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.checkpoints.CheckpointExtension;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.engine.rhi.backend.vulkan.debug.VulkanCrashDiagnostics;

import java.util.Set;

@Mixin(VulkanDevice.class)
public abstract class VulkanDeviceDiagnosticsMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void combatant$dumpVulkanDevice(ShaderSource shaderSource,
                                            VulkanInstance instance,
                                            VulkanPhysicalDevice physicalDevice,
                                            Set<String> enabledExtensions,
                                            VkDevice device,
                                            long vma,
                                            CheckpointExtension checkpointExtension,
                                            CallbackInfo ci) {
        VulkanCrashDiagnostics.dumpDevice(instance, physicalDevice, enabledExtensions);
    }
}
