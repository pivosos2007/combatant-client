/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sky;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.mixins.accessors.GameRendererAccessor;
import combatant.client.mixins.accessors.LocalPlayerAccessor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.renderer.MeshRenderer;

public enum CustomSkyboxRenderer {
    ;
    private static float lastSunAngle;
    private static boolean lastSunAngleValid;

    public static float getLastSunAngle() {
        return lastSunAngle;
    }

    public static boolean hasLastSunAngle() {
        return lastSunAngleValid;
    }

    public static void render(Camera camera, GpuBufferSlice fog, SkyRenderState sky, SkyRenderer skyRendering,
                              com.mojang.blaze3d.pipeline.RenderTarget sceneTarget) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || skyRendering == null) return;
        lastSunAngle = sky.sunAngle;
        lastSunAngleValid = true;

        RenderSystem.setShaderFog(fog);
        // RenderPearl 26.3 owns the sky RenderPass; individual draws are no longer public.
        skyRendering.render(fog, sky);

        DeltaTracker tickCounter = mc.getDeltaTracker();
        float tickDelta = tickCounter != null ? tickCounter.getGameTimeDeltaPartialTick(true) : 0.0f;

        float fov = camera.getFov();
        Matrix4f projection = buildWorldProjection(mc, camera, fov, tickDelta);
        Matrix4f previousProjection = MeshRenderer.projection();
        MeshRenderer.setProjection(projection);

        boolean prevRendering3D = RenderState.rendering3D;
        RenderState.rendering3D = false;

        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        Matrix4f camRot = new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()));
        mv.mul(camRot);
        ReimaginedVisual visual = Modules.get(ReimaginedVisual.class);
        if (visual != null && visual.isShaderSkyboxEnabled()) {
            DeferredSkyRenderer.render(mc, camera, sky, visual, sceneTarget, fog,
                    tickDelta, projection);
        }
        mv.popMatrix();
        MeshRenderer.setProjection(previousProjection);
        RenderState.rendering3D = prevRendering3D;

    }

    private static Matrix4f buildWorldProjection(Minecraft mc, Camera camera, float fov, float tickDelta) {
        Matrix4f capturedProjection = CombatantWorldMatrices.renderProjectionMatrix();
        if (capturedProjection != null) {
            return capturedProjection;
        }

        GameRenderer renderer = mc.gameRenderer;
        GameRendererAccessor accessor = (GameRendererAccessor) renderer;
        CameraRenderState cameraRenderState = new CameraRenderState();
        camera.extractRenderState(cameraRenderState, mc.getDeltaTracker());
        Matrix4f projection = new Matrix4f(cameraRenderState.projectionMatrix);

        PoseStack viewEffects = new PoseStack();
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender == null || !noRender.off("camera_shake")) {
            accessor.invokeTiltViewWhenHurt(cameraRenderState, viewEffects);
        }

        boolean freecamEnabled = Modules.get(Freecam.class) != null && Modules.get(Freecam.class).isEnabled();
        if (mc.options.bobView().get()
                && !freecamEnabled
                && (noRender == null || !noRender.off("view_bob"))) {
            accessor.invokeBobView(cameraRenderState, viewEffects);
        }

        projection.mul(viewEffects.last().pose());
        applyNauseaProjection(mc, accessor, projection, tickDelta);
        return projection;
    }

    private static void applyNauseaProjection(Minecraft mc,
                                              GameRendererAccessor accessor,
                                              Matrix4f projection,
                                              float tickDelta) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        float distortionScale = mc.options.screenEffectScale().get().floatValue();
        if (distortionScale <= 0.0f) return;

        float nauseaIntensity = 0.0f;
        if (player instanceof LocalPlayerAccessor playerAccessor) {
            nauseaIntensity = Mth.lerp(
                    tickDelta,
                    playerAccessor.combatant$getLastNauseaIntensity(),
                    playerAccessor.combatant$getNauseaIntensity()
            );
        }

        float nauseaEffect = player.getEffectBlendFactor(MobEffects.NAUSEA, tickDelta);
        float strength = Math.max(nauseaIntensity, nauseaEffect) * (distortionScale * distortionScale);
        if (strength <= 0.0f) return;

        float scale = 5.0f / (strength * strength + 5.0f) - strength * 0.04f;
        scale *= scale;
        Vector3f axis = new Vector3f(0.0f, Mth.SQRT_OF_TWO / 2.0f, Mth.SQRT_OF_TWO / 2.0f);
        float angle = player.getSpinningEffectAngle(tickDelta) * Mth.DEG_TO_RAD;
        projection.rotate(angle, axis);
        projection.scale(1.0f / scale, 1.0f, 1.0f);
        projection.rotate(-angle, axis);
    }

}
