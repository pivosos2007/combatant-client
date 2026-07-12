/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.CameraClip;
import combatant.client.features.module.modules.visuals.FovControl;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.features.module.modules.visuals.Zoom;

@Mixin(Camera.class)
public abstract class CameraMixin {


    @Shadow
    private boolean initialized;
    @Shadow
    private Level level;
    @Shadow
    private Entity entity;

    @Unique
    private static float combatant$getGameFov() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return 70.0f;
        return mc.options.fov().get();
    }

    @Unique
    private static float combatant$getFovEffectScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return 1.0f;
        return mc.options.fovEffectScale().get().floatValue();
    }

    @Shadow
    protected abstract void move(float surge, float heave, float sway);

    @Inject(method = "getMaxZoom(F)F", at = @At("HEAD"), cancellable = true)
    private void combatant$disableCameraCollision(float desiredDistance, CallbackInfoReturnable<Float> cir) {
        CameraClip mod = Modules.get(CameraClip.class);
        if (mod != null && mod.isEnabled() && isThirdPerson()) {
            // Skip collision shortening and apply distance multiplier
            float dist = desiredDistance * mod.distanceMultiplier();
            cir.setReturnValue(dist);
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void combatant$applyCameraOffsets(DeltaTracker tickCounter, CallbackInfo ci) {
        CameraClip mod = Modules.get(CameraClip.class);
        if (mod == null || !mod.isEnabled() || !isThirdPerson()) return;

        float back = mod.backOffset();
        float up = mod.upOffset();
        float right = mod.rightOffset();

        if (back != 0.0f || up != 0.0f || right != 0.0f) {
            // moveBy uses (surge, heave, sway); negative surge moves backward
            move(-back, up, right);
        }
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void combatant$adjustCalculatedFov(float tickDelta, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(combatant$adjustFovValue(cir.getReturnValue()));
    }

    @Unique
    private float combatant$adjustFovValue(float fov) {
        FovControl module = Modules.get(FovControl.class);
        Zoom zoom = Modules.get(Zoom.class);

        boolean hasFovControl = module != null && module.isEnabled();
        boolean hasZoom = zoom != null && zoom.isEnabled() && zoom.shouldApplyZoom();
        if (!hasFovControl && !hasZoom) return fov;

        if (hasFovControl && module.useCustomFov()) {
            float base = combatant$getGameFov();
            float custom = module.getCustomFov();
            if (base > 0.01f) {
                fov = fov * (custom / base);
            } else {
                fov = custom;
            }
        }

        if (hasFovControl && module.disableFluidFov()) {
            FogType type = ((Camera) (Object) this).getFluidInCamera();
            if (type == FogType.WATER || type == FogType.LAVA) {
                float effectScale = combatant$getFovEffectScale();
                float factor = Mth.lerp(effectScale, 1.0f, 0.85714287f);
                if (factor > 0.0001f) {
                    fov /= factor;
                }
            }
        }

        if (hasZoom) {
            float divisor = zoom.getZoomDivisor();
            if (divisor > 1.0f) {
                fov /= divisor;
            }
        }

        return fov;
    }

    @Unique
    private boolean isThirdPerson() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return false;
        CameraType p = mc.options.getCameraType();
        return p == CameraType.THIRD_PERSON_BACK || p == CameraType.THIRD_PERSON_FRONT;
    }

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract Matrix4f getViewRotationMatrix(Matrix4f matrix);

    @Invoker("createProjectionMatrixForCulling")
    protected abstract Matrix4f combatant$createProjectionMatrixForCulling();

    @Invoker("prepareCullFrustum")
    protected abstract void combatant$prepareCullFrustum(Matrix4fc viewMatrix, Matrix4f projectionMatrix, Vec3 cameraPos);

    @Inject(method = "update", at = @At("TAIL"))
    private void freecam$override(
            DeltaTracker tickCounter,
            CallbackInfo ci
    ) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc == null || !fc.isEnabled() || fc.camEntity == null) return;

        this.initialized = true;
        if (Minecraft.getInstance().level != null) {
            this.level = Minecraft.getInstance().level;
        }
        this.entity = fc.camEntity;

        this.setRotation(fc.camYaw, fc.camPitch);

        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);
        Vec3 p = fc.getCameraPos(tickDelta);
        fc.syncCameraEntity(fc.camPos, fc.camPosPrev);

        this.setPosition(p);
        this.combatant$prepareCullFrustum(this.getViewRotationMatrix(new Matrix4f()), this.combatant$createProjectionMatrixForCulling(), p);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void combatant$freecamExtractRenderState(CameraRenderState renderState, float tickDelta, CallbackInfo ci) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc == null || !fc.isEnabled() || fc.camEntity == null) return;

        renderState.smartCull = false;
    }
}




