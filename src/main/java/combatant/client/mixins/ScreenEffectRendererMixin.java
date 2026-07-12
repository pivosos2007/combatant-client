/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.mixins.accessors.InGameOverlayRendererAccessor;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

    @Redirect(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
            )
    )
    private static void combatant$maybeRenderFireOverlay(
            PoseStack matrices,
            SubmitNodeCollector vertexConsumers,
            TextureAtlasSprite sprite
    ) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) return;

        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null && noRender.off("fire_overlay")) {
            if (!noRender.fireOnlyWhenResistant()) {
                return;
            }
            if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                return;
            }
        }

        if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return;
        }

        InGameOverlayRendererAccessor.callRenderFireOverlay(matrices, vertexConsumers, sprite);
    }

    @Redirect(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitBlockSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
            )
    )
    private static void combatant$maybeRenderBlockOverlay(
            TextureAtlasSprite sprite,
            PoseStack matrices,
            SubmitNodeCollector vertexConsumers,
            int color
    ) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null && noRender.off("block_overlay")) {
            return;
        }

        InGameOverlayRendererAccessor.callRenderInWallOverlay(sprite, matrices, vertexConsumers, color);
    }
}



