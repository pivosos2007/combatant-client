/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.engine.core.CombatantRenderSystem;

@Mixin(targets = "com.mojang.blaze3d.opengl.DirectStateAccess$Emulated")
public abstract class BufferManagerDefaultBufferManagerMixin {
    @Inject(method = "bindFrameBufferTextures", at = @At("HEAD"), cancellable = true)
    private void combatant$setupFramebuffer(int framebuffer,
                                            int[] colorAttachments,
                                            int[] colorMipLevels,
                                            int depthAttachment,
                                            int depthMipLevel,
                                            int bindTarget,
                                            CallbackInfo ci) {
        if (colorAttachments == null || colorAttachments.length != 1) {
            return;
        }

        int colorAttachment = colorAttachments[0];
        int mipLevel = colorMipLevels != null && colorMipLevels.length > 0 ? colorMipLevels[0] : depthMipLevel;
        if (CombatantRenderSystem.rhi().msaa().setupFramebuffer(framebuffer, colorAttachment, depthAttachment, mipLevel, bindTarget)) {
            ci.cancel();
        }
    }
}





