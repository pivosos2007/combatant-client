/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.features.gui.hud.nondraggable.impl.BetterTooltips;

@Mixin(TooltipRenderUtil.class)
public class TooltipRenderUtilMixin {

    @Redirect(
            method = "extractTooltipBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
            )
    )
    private static void redirectDrawGuiTexture(
            GuiGraphicsExtractor ctx,
            RenderPipeline pipeline,
            Identifier sprite,
            int x, int y, int w, int h
    ) {
        BetterTooltips tooltips = BetterTooltips.get();
        if (tooltips == null || !tooltips.isTooltipAlphaEnabled()) {
            ctx.blitSprite(pipeline, sprite, x, y, w, h);
            return;
        }
        int argb = tooltips.tooltipColor();
        ctx.blitSprite(pipeline, sprite, x, y, w, h, argb);
    }
}




