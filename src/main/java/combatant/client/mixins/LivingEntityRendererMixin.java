/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NameTags;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.features.module.modules.visuals.SeeInvisibles;
import combatant.client.render.ViewObstructionFadeContext;
import combatant.client.render.ViewObstructionFadeState;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> extends EntityRenderer<T, S> {
    protected LivingEntityRendererMixin(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Shadow
    public abstract Identifier getTextureLocation(S state);

    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$hideVanillaLabels(T entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player && Modules.get(NameTags.class) != null && Modules.get(NameTags.class).isEnabled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void combatant$updateViewObstructionFadeState(T entity, S state, float tickProgress, CallbackInfo ci) {
        if (!(state instanceof ViewObstructionFadeState fadeState)) {
            return;
        }

        NoRender noRender = Modules.get(NoRender.class);
        boolean obstructionFadeActive = noRender != null && noRender.shouldFadeEntity(entity);
        float obstructionFadeAlpha = obstructionFadeActive ? noRender.getEntityFadeAlpha(entity) : 1.0f;
        obstructionFadeActive = obstructionFadeActive && obstructionFadeAlpha < 0.99f;

        SeeInvisibles seeInvisibles = Modules.get(SeeInvisibles.class);
        boolean seeInvisibleActive = seeInvisibles != null && seeInvisibles.shouldRenderInvisiblePlayer(entity);
        float seeInvisibleAlpha = seeInvisibleActive ? seeInvisibles.getInvisiblePlayerAlpha01() : 1.0f;
        boolean seeInvisibleFadeActive = seeInvisibleActive && seeInvisibleAlpha < 0.99f;
        if (seeInvisibleActive) {
            state.isInvisibleToPlayer = false;
        }

        boolean active = obstructionFadeActive || seeInvisibleFadeActive;
        float alpha = obstructionFadeAlpha;
        if (seeInvisibleFadeActive) {
            alpha = Math.min(alpha, seeInvisibleAlpha);
        }
        fadeState.combatant$setViewObstructionFadeActive(active);
        fadeState.combatant$setViewObstructionFadeAlpha(active ? alpha : 1.0f);
        fadeState.combatant$setSeeInvisibleFadeActive(seeInvisibleFadeActive);
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F")
    )
    private float combatant$renderRotationYaw(float original, @Local(argsOnly = true) T entity, @Local(argsOnly = true) float tickProgress) {
        if (!(entity instanceof LocalPlayer player)) {
            return original;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != player || ClientScreen.current() instanceof AbstractContainerScreen<?>) {
            return original;
        }

        Rotation current = RotationManager.INSTANCE.getCurrentRotation();
        Rotation previous = RotationManager.INSTANCE.getPreviousRotation();
        if (current == null) {
            return original;
        }

        float prevYaw = previous != null ? previous.yaw() : current.yaw();
        if (prevYaw == player.getYRot() && current.yaw() == player.getYRot()) {
            return original;
        }

        return Mth.rotLerp(tickProgress, prevYaw, current.yaw());
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F")
    )
    private float combatant$renderRotationPitch(float original, @Local(argsOnly = true) T entity, @Local(argsOnly = true) float tickProgress) {
        if (!(entity instanceof LocalPlayer player)) {
            return original;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != player || ClientScreen.current() instanceof AbstractContainerScreen<?>) {
            return original;
        }

        Rotation current = RotationManager.INSTANCE.getCurrentRotation();
        Rotation previous = RotationManager.INSTANCE.getPreviousRotation();
        if (current == null) {
            return original;
        }

        float prevPitch = previous != null ? previous.pitch() : current.pitch();
        if (prevPitch == player.getXRot() && current.pitch() == player.getXRot()) {
            return original;
        }

        return Mth.lerp(tickProgress, prevPitch, current.pitch());
    }


    @ModifyExpressionValue(
            method = "submit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;")
    )
    private RenderType combatant$useTranslucentRenderTypeForViewFade(RenderType original, @Local(argsOnly = true) S state) {
        if (original == null || original.hasBlending()) {
            return original;
        }
        if (IrisRuntime.isRenderingShadowPass()) {
            return original;
        }
        if (!(state instanceof ViewObstructionFadeState fadeState)
                || !fadeState.combatant$isViewObstructionFadeActive()
                || fadeState.combatant$getViewObstructionFadeAlpha() >= 0.99f) {
            return original;
        }
        return RenderTypes.entityTranslucent(getTextureLocation(state), true);
    }

    @ModifyConstant(
            method = "submit",
            constant = @Constant(intValue = 654311423)
    )
    private int combatant$seeInvisibles$overrideInvisiblePlayerAlpha(int vanillaTint, S state) {
        SeeInvisibles seeInvisibles = Modules.get(SeeInvisibles.class);
        if (seeInvisibles != null
                && state instanceof ViewObstructionFadeState fadeState
                && fadeState.combatant$isSeeInvisibleFadeActive()
                && seeInvisibles.shouldRenderInvisiblePlayer(state.entityType, state.isInvisible)) {
            return seeInvisibles.getInvisiblePlayerTintArgb();
        }
        return vanillaTint;
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void combatant$pushViewObstructionFadeContext(S state, com.mojang.blaze3d.vertex.PoseStack matrixStack, net.minecraft.client.renderer.SubmitNodeCollector orderedRenderCommandQueue, net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState, CallbackInfo ci) {
        ViewObstructionFadeContext.push(state instanceof ViewObstructionFadeState fadeState ? fadeState : null);
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void combatant$popViewObstructionFadeContext(S state, com.mojang.blaze3d.vertex.PoseStack matrixStack, net.minecraft.client.renderer.SubmitNodeCollector orderedRenderCommandQueue, net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState, CallbackInfo ci) {
        ViewObstructionFadeContext.pop();
    }
}
