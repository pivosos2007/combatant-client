/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.hmi_recode.HoldMyItems;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.ViewModel;

@Environment(EnvType.CLIENT)
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void combatant$skipTickWithoutPlayer(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player == null) {
            ci.cancel();
        }
    }

    @Inject(
            method = "submitArmWithItem",
            at = @At("HEAD")
    )
    private void combatant$applyOffsets(
            AbstractClientPlayer player,
            float tickDelta,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            PoseStack matrices,
            SubmitNodeCollector queue,
            int light,
            CallbackInfo ci
    ) {
        ViewModel viewModel = Modules.get(ViewModel.class);
        if (viewModel == null || !viewModel.isEnabled()) return;

        viewModel.syncHmiBackendForRender();
        if (viewModel.isHmiModeActive()) {
            HoldMyItems.beginHandRender(player, tickDelta, hand, swingProgress, item, equipProgress, matrices);
            return;
        }
        if (!viewModel.isBasicModeActive()) return;

        matrices.translate(
                viewModel.swingX.get(),
                viewModel.swingY.get(),
                viewModel.swingZ.get() + (isInLiquid(player) ? viewModel.liquidOffsetZ.get() : 0.0f)
        );
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void combatant$endHmiHandRender(
            AbstractClientPlayer player,
            float tickDelta,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            PoseStack matrices,
            SubmitNodeCollector queue,
            int light,
            CallbackInfo ci
    ) {
        ViewModel viewModel = Modules.get(ViewModel.class);
        if (viewModel == null || !viewModel.isHmiModeActive()) return;
        HoldMyItems.endHandRender();
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void combatant$applyHmiHandRelative(
            PoseStack matrices,
            SubmitNodeCollector queue,
            int light,
            float equipProgress,
            float swingProgress,
            HumanoidArm arm,
            CallbackInfo ci
    ) {
        ViewModel viewModel = Modules.get(ViewModel.class);
        if (viewModel == null || !viewModel.isHmiModeActive()) return;
        HoldMyItems.applyHandRelative(matrices);
    }

    @ModifyVariable(
            method = "submitArmWithItem",
            at = @At("HEAD"),
            argsOnly = true,
            index = 7)
    private float combatant$scaleEquipProgress(float equipProgress) {
        ViewModel viewModel = Modules.get(ViewModel.class);
        if (viewModel == null || !viewModel.isBasicModeActive()) return equipProgress;
        return equipProgress * viewModel.equipLowering.get();
    }

    @Unique
    private boolean isInLiquid(AbstractClientPlayer player) {
        return player != null && (player.isInWater() || player.isInLava());
    }

    @Inject(
            method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
            )
    )
    private void combatant$scaleMiniItems(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext ctx,
            PoseStack ms,
            SubmitNodeCollector queue,
            int light,
            CallbackInfo ci
    ) {
        ViewModel viewModel = Modules.get(ViewModel.class);
        if (viewModel == null || !viewModel.isEnabled()) return;
        if (!ctx.firstPerson()) return;

        viewModel.syncHmiBackendForRender();
        if (viewModel.isHmiModeActive()) {
            HoldMyItems.applyItemPose(stack, ms);
            // MiniItems is a ViewModel composition layer, not a Basic-only animation.
            // Apply it after HMI so resource-pack poses keep their original pivot/translation.
            viewModel.applyMini(ms, stack);
            return;
        }

        viewModel.applyMini(ms, stack);
    }

    @Inject(
            method = "swingArm",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$customSwing(
            float swingProgress,
            PoseStack ms,
            int direction,
            HumanoidArm arm,
            CallbackInfo ci
    ) {
        ViewModel viewModel = Modules.get(ViewModel.class);
        if (viewModel == null || !viewModel.isBasicModeActive()) return;
        if (viewModel.mc.player == null) return;

        ItemStack stack = viewModel.mc.player.getMainHandItem();
        if (!viewModel.shouldSwing(stack))
            return;

        if (viewModel.shouldBypassRotationTransform(stack))
            return;

        // Cancel vanilla swing entirely
        ci.cancel();

        viewModel.applySwingAnimation(swingProgress, ms, arm);
    }
}





