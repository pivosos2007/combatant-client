/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.config.MainConfig;
import combatant.client.features.gui.mainmenu.CombatantMainMenuScreen;
import combatant.client.render.engine.postprocess.MenuBackgroundRenderer;
import combatant.client.runtime.RuntimeGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Panorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces every vanilla panorama request with the configured Combatant menu background. */
@Mixin(Panorama.class)
public abstract class PanoramaMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$replaceVanillaPanorama(GuiGraphicsExtractor context,
                                                   int width,
                                                   int height,
                                                   CallbackInfo ci) {
        if (!RuntimeGate.canRunRender()) return;
        if (CombatantMainMenuScreen.shouldUseVanillaTitleScreen()) return;
        if (!MainConfig.get().isCombatantMainMenuEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        // Do not create PanoramaRenderState and do not enqueue the vanilla panorama overlay at all.
        MenuBackgroundRenderer.renderConfigured(mc);
        ci.cancel();
    }
}
