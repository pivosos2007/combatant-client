/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.SkyRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.render.engine.msaa.MsaaWorldTarget;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Shadow
    @Final
    private RenderTarget renderTarget;

    @Redirect(
            method = {
                    "renderSkyDisc",
                    "renderDarkDisc",
                    "renderSun",
                    "renderMoon",
                    "renderStars",
                    "renderSunriseAndSunset",
                    "renderEndSky",
                    "renderEndFlash"
            },
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/SkyRenderer;renderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private RenderTarget combatant$currentWorldTarget(SkyRenderer instance) {
        RenderTarget msaa = MsaaWorldTarget.getFramebufferOverride();
        return msaa != null ? msaa : renderTarget;
    }
}
