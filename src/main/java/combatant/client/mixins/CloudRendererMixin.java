/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.sky.DeferredSkyRenderer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla billboard clouds from overlapping the new volumetric sky pass. */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void combatant$replaceVanillaClouds(CloudStatus status, RenderPass pass, CallbackInfo ci) {
        if (replaceClouds()) ci.cancel();
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void combatant$replaceVanillaCloudsOit(CloudStatus status, OitStage stage,
                                                   GpuTextureView depth, OitRenderPassProvider.Parameters parameters,
                                                   CallbackInfo ci) {
        if (replaceClouds()) ci.cancel();
    }

    private static boolean replaceClouds() {
        if (IrisRuntime.isShaderpackRendererActive() || Minecraft.getInstance().level == null) return false;
        ReimaginedVisual visual = Modules.get(ReimaginedVisual.class);
        return visual != null && visual.isShaderSkyboxEnabled() && DeferredSkyRenderer.hasWorkingTargets();
    }
}
