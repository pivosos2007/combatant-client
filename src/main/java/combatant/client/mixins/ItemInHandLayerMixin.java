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
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Routes held items through solved anatomical hand sockets without double-applying vanilla grip offsets. */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    private static final float MODEL_UNIT = 1f / 16f;
    private static final Matrix4fc RIGHT_ADULT_POST_INVERSE = inverseVanillaPost(HumanoidArm.RIGHT, false);
    private static final Matrix4fc LEFT_ADULT_POST_INVERSE = inverseVanillaPost(HumanoidArm.LEFT, false);
    private static final Matrix4fc RIGHT_BABY_POST_INVERSE = inverseVanillaPost(HumanoidArm.RIGHT, true);
    private static final Matrix4fc LEFT_BABY_POST_INVERSE = inverseVanillaPost(HumanoidArm.LEFT, true);
    private static final ThreadLocal<Matrix4f> SOCKET_MATRIX = ThreadLocal.withInitial(Matrix4f::new);

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

        // submitArmWithItem() applies -90X, +180Y and a grip translation immediately after
        // translateToHand(). The rig socket already represents the final vanilla-compatible grip,
        // so pre-cancel that fixed post transform here. Vanilla then reapplies it and the resulting
        // matrix is exactly the solved socket matrix instead of socket * gripOffset (the old feet bug).
        Matrix4f socketMatrix = SOCKET_MATRIX.get();
        rig.socketMatrix(socket, socketMatrix);
        matrices.mulPose(socketMatrix);
        matrices.mulPose(vanillaPostInverse(arm, state instanceof LivingEntityRenderState living && living.isBaby));
    }

    private static Matrix4fc vanillaPostInverse(HumanoidArm arm, boolean baby) {
        if (baby) return arm == HumanoidArm.LEFT ? LEFT_BABY_POST_INVERSE : RIGHT_BABY_POST_INVERSE;
        return arm == HumanoidArm.LEFT ? LEFT_ADULT_POST_INVERSE : RIGHT_ADULT_POST_INVERSE;
    }

    private static Matrix4f inverseVanillaPost(HumanoidArm arm, boolean baby) {
        float x = baby ? 0f : (arm == HumanoidArm.LEFT ? -1f : 1f) * MODEL_UNIT;
        float y = (baby ? 1f : 2f) * MODEL_UNIT;
        float z = (baby ? -4.5f : -10f) * MODEL_UNIT;
        return new Matrix4f()
                .rotateX(-((float) Math.PI * 0.5f))
                .rotateY((float) Math.PI)
                .translate(x, y, z)
                .invert();
    }
}
