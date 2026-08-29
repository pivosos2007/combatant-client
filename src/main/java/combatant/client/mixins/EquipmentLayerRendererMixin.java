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
import combatant.client.features.playeranimator.PlayerRigInstance;
import combatant.client.features.playeranimator.PlayerRigRenderContext;
import combatant.client.features.playeranimator.render.PlayerRigCpuRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.render.ViewObstructionFadeContext;
import combatant.client.render.iris.IrisRuntime;

@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
    @WrapOperation(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
            )
    )
    private void combatant$submitRiggedArmor(
            OrderedSubmitNodeCollector collector,
            Model<?> model,
            Object modelState,
            PoseStack matrices,
            RenderType renderType,
            int light,
            int overlay,
            int tint,
            TextureAtlasSprite sprite,
            int outlineColor,
            ModelFeatureRenderer.CrumblingOverlay crumbling,
            Operation<Void> original
    ) {
        PlayerRigInstance rig = PlayerRigRenderContext.current();
        if (rig != null && model instanceof HumanoidModel<?> humanoid
                && PlayerRigCpuRenderer.submitArmor(
                        collector, humanoid, rig, matrices, renderType, light, overlay, tint, sprite,
                        outlineColor, crumbling
                )) {
            return;
        }
        original.call(collector, model, modelState, matrices, renderType, light, overlay, tint,
                sprite, outlineColor, crumbling);
    }

    @Redirect(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")
    )
    private RenderType combatant$useTranslucentArmorWhenViewFaded(Identifier texture) {
        if (IrisRuntime.isRenderingShadowPass()) {
            return RenderTypes.armorCutoutNoCull(texture);
        }
        if (ViewObstructionFadeContext.isActive() && ViewObstructionFadeContext.alpha() < 0.99f) {
            return RenderTypes.armorTranslucent(texture);
        }
        return RenderTypes.armorCutoutNoCull(texture);
    }
}
