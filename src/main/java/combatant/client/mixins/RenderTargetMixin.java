/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import com.mojang.blaze3d.pipeline.RenderTarget;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Converts vanilla depth copies into resolves when the source is a Vulkan MSAA target. */
@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {
    @Inject(method = "copyDepthFrom", at = @At("HEAD"), cancellable = true)
    private void combatant$resolveMultisampledDepth(RenderTarget source, CallbackInfo ci) {
        if (!VulkanRenderStateBridge.vulkanBackendActive() || source == null) return;

        RenderTarget destination = (RenderTarget) (Object) this;
        if (source.getDepthTextureView() == null || destination.getDepthTextureView() == null) return;
        int sourceSamples = VulkanRenderStateBridge.samplesFor(source.getDepthTextureView());
        int destinationSamples = VulkanRenderStateBridge.samplesFor(destination.getDepthTextureView());
        if (sourceSamples <= 1 || destinationSamples != 1) return;

        boolean resolved = CombatantRenderSystem.rhi().msaa().resolveDepthSnapshot(
                source.getDepthTextureView(), destination.getDepthTextureView());
        if (!resolved) {
            throw new IllegalStateException("Failed to resolve Vulkan MSAA depth before vanilla depth copy");
        }
        ci.cancel();
    }
}
