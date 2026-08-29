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
import combatant.client.features.playeranimator.PlayerRigSocket;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Routes held items through the anatomical hand sockets instead of vanilla arm pivots. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @WrapOperation(
            method = "submitArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V"
            )
    )
    private void combatant$translateItemToRig(
            ArmedModel model,
            EntityRenderState state,
            HumanoidArm arm,
            PoseStack matrices,
            Operation<Void> original
    ) {
        PlayerRigInstance rig = PlayerRigRenderContext.current();
        if (rig == null) {
            original.call(model, state, arm, matrices);
            return;
        }

        PlayerRigSocket socket = arm == HumanoidArm.LEFT
                ? PlayerRigSocket.LEFT_ITEM
                : PlayerRigSocket.RIGHT_ITEM;
        matrices.mulPose(rig.socketMatrix(socket, new Matrix4f()));
    }
}
