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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Shadow
    private void renderPlayerArm(
            PoseStack matrices,
            SubmitNodeCollector queue,
            int light,
            float equipProgress,
            float swingProgress,
            HumanoidArm arm
    ) {
        throw new AssertionError();
    }

    @Shadow
    public abstract void renderItem(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext context,
            PoseStack matrices,
            SubmitNodeCollector queue,
            int light
    );

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void combatant$skipTickWithoutPlayer(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player == null) {
            ci.cancel();
        }
    }

    @Inject(
            method = "submitArmWithItem",
            at = @At("HEAD"),
            cancellable = true
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
            // Maps have a dedicated vanilla two-hand/one-hand pipeline. Keep that specialized path
            // intact; ordinary held items use the HMI scene below so vanilla item/use/swing
            // transforms cannot be stacked a second time on top of the scripted HMI pose.
            if (item.has(DataComponents.MAP_ID)) return;

            // Vanilla also suppresses first-person hands while scoping. Since this branch replaces
            // submitArmWithItem for HMI, cancellation is required here as well.
            if (player.isScoping()) {
                ci.cancel();
                return;
            }

            HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                    ? player.getMainArm()
                    : player.getMainArm().getOpposite();
            ItemDisplayContext displayContext = arm == HumanoidArm.RIGHT
                    ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

            matrices.pushPose();
            try {
                // Keep the user base translation outside the scripted HMI transform stack.
                // This makes X/Y/Z stable camera-space offsets, matching Basic ViewModel behavior
                // instead of rotating the offset axes with item-specific scripted poses.
                viewModel.applyHmiBaseOffset(matrices);

                HoldMyItems.beginHandRender(
                        player,
                        tickDelta,
                        hand,
                        swingProgress,
                        item,
                        equipProgress,
                        matrices,
                        viewModel.hmiMotionSettings()
                );

                if (item.isEmpty()) {
                    // Match vanilla visibility semantics for the empty main hand, but let the HMI
                    // hand scripts own its pose.
                    if (hand == InteractionHand.MAIN_HAND && !player.isInvisible()) {
                        matrices.pushPose();
                        try {
                            renderPlayerArm(matrices, queue, light, 0.0f, 0.0f, arm);
                        } finally {
                            matrices.popPose();
                        }
                    }
                } else {
                    if (viewModel.shouldRenderHmiHoldingHands() && !player.isInvisible()) {
                        matrices.pushPose();
                        try {
                            renderPlayerArm(matrices, queue, light, 0.0f, 0.0f, arm);
                        } finally {
                            matrices.popPose();
                        }
                    }

                    matrices.pushPose();
                    try {
                        // renderItem remains the vanilla 26.2 model submission path. The HMI item
                        // pose + MiniItems layer is injected immediately before ItemStackRenderState
                        // submission below, inside the active HMI hand scope.
                        renderItem(player, item, displayContext, matrices, queue, light);
                    } finally {
                        matrices.popPose();
                    }
                }
            } finally {
                HoldMyItems.endHandRender();
                matrices.popPose();
            }

            ci.cancel();
            return;
        }
        if (!viewModel.isBasicModeActive()) return;

        matrices.translate(
                viewModel.swingX.get(),
                viewModel.swingY.get(),
                viewModel.swingZ.get() + (isInLiquid(player) ? viewModel.liquidOffsetZ.get() : 0.0f)
        );
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
        if (viewModel == null || !viewModel.isEnabled()) return;

        if (viewModel.isHmiModeActive()) {
            // HMI owns swings in JavaScript. This still matters for the dedicated vanilla map path,
            // which is intentionally not replaced by the generic HMI held-item renderer above.
            ci.cancel();
            return;
        }

        if (!viewModel.isBasicModeActive() || viewModel.mc.player == null) return;

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





