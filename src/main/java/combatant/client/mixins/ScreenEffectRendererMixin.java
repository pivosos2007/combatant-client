/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NoRender;

/**
 * Wraps (not redirects) the overlay calls so other mods that hook the same calls, such as Punchy, still apply.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

    @WrapOperation(
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
            TextureAtlasSprite sprite,
            Operation<Void> original
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

        original.call(matrices, vertexConsumers, sprite);
    }

    @WrapOperation(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitBlockSprite(Lnet/minecraft/resources/Identifier;FFFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
            )
    )
    private static void combatant$maybeRenderBlockOverlay(
            Identifier atlasLocation,
            float u0,
            float v0,
            float u1,
            float v1,
            PoseStack matrices,
            SubmitNodeCollector vertexConsumers,
            int color,
            Operation<Void> original
    ) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null && noRender.off("block_overlay")) {
            return;
        }

        original.call(atlasLocation, u0, v0, u1, v1, matrices, vertexConsumers, color);
    }
}
