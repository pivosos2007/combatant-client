/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.features.gui.hud.nondraggable.impl.tab.CustomTabList;
import combatant.client.runtime.RuntimeGate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$extractCustomTabList(GuiGraphicsExtractor context,
                                                int width,
                                                Scoreboard scoreboard,
                                                Objective objective,
                                                CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        if (!CustomTabList.shouldReplaceVanilla()) return;
        CustomTabList.renderVanillaTabStratum(context, width);
        ci.cancel();
    }
}
