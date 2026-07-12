/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.WorldTweaks;
import combatant.client.mixininterface.IItemEntityRenderState;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin extends EntityRenderer<ItemEntity, ItemEntityRenderState> {
    @Shadow
    @Final
    private RandomSource random;

    protected ItemEntityRendererMixin(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void combatant$captureOnGround(ItemEntity itemEntity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        ((IItemEntityRenderState) state).combatant$setOnGround(itemEntity.onGround());
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void combatant$renderItemPhysics(
            ItemEntityRenderState state,
            PoseStack matrices,
            SubmitNodeCollector queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        WorldTweaks module = Modules.get(WorldTweaks.class);
        if (module == null || !module.isItemPhysicsEnabled() || state.item.isEmpty()) {
            return;
        }

        matrices.pushPose();
        AABB box = state.item.getModelBoundingBox();
        float baseY = -((float) box.minY) + 0.0625F;
        matrices.translate(0.0F, baseY, 0.0F);

        if (((IItemEntityRenderState) state).combatant$isOnGround()) {
            matrices.mulPose(Axis.XP.rotationDegrees(90.0F));
        } else {
            float rotation = ItemEntity.getSpin(state.ageInTicks, state.bobOffset);
            matrices.mulPose(Axis.XP.rotationDegrees(rotation * 300.0F));
        }

        ItemEntityRenderer.submitMultipleFromCount(matrices, queue, state.lightCoords, state, this.random, box);
        matrices.popPose();
        super.submit(state, matrices, queue, cameraState);
        ci.cancel();
    }
}
