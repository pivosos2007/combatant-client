/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris;

import com.google.common.collect.ImmutableSet;
import com.mojang.renderpearl.api.textures.GpuTexture;
import combatant.client.render.iris.IrisShaderpackMsaaIntegration;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Limits attachment substitution to framebuffers used by shaderpack world raster passes. */
@Pseudo
@Mixin(value = RenderTargets.class, remap = false)
public abstract class IrisRenderTargetsMsaaMixin {
    @Shadow
    private GpuTexture currentDepthTexture;

    @Inject(method = "createGbufferFramebuffer", at = @At("RETURN"), remap = false)
    private void combatant$captureGbufferFramebuffer(ImmutableSet<Integer> stageReadsFromAlt,
                                                      int[] drawBuffers,
                                                      CallbackInfoReturnable<GlFramebuffer> cir) {
        IrisShaderpackMsaaIntegration.captureGbufferFramebuffer(
                cir.getReturnValue(), drawBuffers, currentDepthTexture);
    }
}
