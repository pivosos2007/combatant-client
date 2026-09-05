/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NoRender;

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

@Unique
    private static boolean combatant$noRenderOff(String key) {
        NoRender noRender = Modules.get(NoRender.class);
        return noRender != null && noRender.offWorld(key);
    }

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void combatant$noRenderSkyDisc(int color, CallbackInfo ci) {
        if (combatant$noRenderOff("sky")) ci.cancel();
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void combatant$noRenderEndSky(CallbackInfo ci) {
        if (combatant$noRenderOff("sky")) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void combatant$noRenderSun(float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (combatant$noRenderOff("sun")) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void combatant$noRenderMoon(MoonPhase moonPhase, float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (combatant$noRenderOff("moon")) ci.cancel();
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void combatant$noRenderStars(float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (combatant$noRenderOff("stars")) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void combatant$noRenderSunrise(PoseStack poseStack, float angle, int color, CallbackInfo ci) {
        if (combatant$noRenderOff("sun")) ci.cancel();
    }
}
