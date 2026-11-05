/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.xaero.minimap;

import combatant.client.config.subsystem.MapUiConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xaero.common.minimap.render.MinimapRenderer;

/**
 * Keeps Xaero's native minimap-arrow geometry/opacity intact while sourcing its RGB from Combatant's
 * Theme/Custom player-arrow setting. The first drawArrow invocation is Xaero's black shadow; only the
 * second invocation is the visible arrow and is modified here.
 */
@Pseudo
@Mixin(MinimapRenderer.class)
public abstract class MinimapRendererMixin {

    @ModifyArgs(
            method = "renderMinimap",
            at = @At(
                    value = "INVOKE",
                    target = "Lxaero/common/minimap/render/MinimapRenderer;drawArrow(Lcom/mojang/blaze3d/vertex/PoseStack;FDDFFFFLnet/minecraft/client/renderer/rendertype/PreparedRenderType$Texture;Lxaero/lib/client/config/ClientConfigManager;)V",
                    ordinal = 1
            )
    )
    private void combatant$overrideNativeArrowRgb(Args args) {
        int color = MapUiConfig.get().resolvedArrowColorArgb();
        args.set(4, ((color >>> 16) & 0xFF) / 255.0f);
        args.set(5, ((color >>> 8) & 0xFF) / 255.0f);
        args.set(6, (color & 0xFF) / 255.0f);
    }
}
