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
import net.minecraft.util.ARGB;
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
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.pipeline.EffectRenderPipelines;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.uniform.impl.SkyboxShaderUniforms;

public enum CustomSkyboxRenderer {
    ;
    private static final float MIN_TWILIGHT_SKYBOX_ALPHA = 0.38f;
    private static float lastSunAngle;
    private static boolean lastSunAngleValid;

    public static float getLastSunAngle() {
        return lastSunAngle;
    }

    public static boolean hasLastSunAngle() {
        return lastSunAngleValid;
    }

    public static void render(Camera camera, GpuBufferSlice fog, SkyRenderState sky, SkyRenderer skyRendering) {
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
        float twilightFade = getTwilightFade(sky);
        float skyboxAlpha = Mth.lerp(twilightFade, 1.0f, MIN_TWILIGHT_SKYBOX_ALPHA);

        ReimaginedVisual visual = Modules.get(ReimaginedVisual.class);
        boolean useShaderSkybox = visual != null && visual.isShaderSkyboxEnabled();

        if (useShaderSkybox) {
            renderShaderSkybox(mc, mc.gameRenderer.mainRenderTarget(), camera, fog, sky, skyboxAlpha, tickDelta, fov, projection);
        }
        mv.popMatrix();
        MeshRenderer.setProjection(previousProjection);
        RenderState.rendering3D = prevRendering3D;

    }

    private static void renderShaderSkybox(Minecraft mc,
                                           com.mojang.blaze3d.pipeline.RenderTarget framebuffer,
                                           Camera camera,
                                           GpuBufferSlice fog,
                                           SkyRenderState sky,
                                           float alpha,
                                           float tickDelta,
                                           float fov,
                                           Matrix4f worldProjection) {
        if (alpha <= 0.001f || fog == null) return;
        ReimaginedVisual visual = Modules.get(ReimaginedVisual.class);
        if (visual == null || !visual.isShaderSkyboxEnabled()) return;

        float time = AnimationUtility.time(0.001f);
        float width = mc.getWindow() != null ? mc.getWindow().getWidth() : 1.0f;
        float height = mc.getWindow() != null ? mc.getWindow().getHeight() : 1.0f;
        float yawRad = (float) Math.toRadians(-camera.yRot());
        float pitchRad = (float) Math.toRadians(camera.xRot());
        float shaderFov = extractProjectionFov(worldProjection, fov);
        SkyboxShaderUniforms.update(
                visual.getShaderSkyboxColor(),
                ARGB.colorFromVector3f(sky.skyColor) & 0x00FFFFFF,
                alpha,
                visual.getSkyboxSkyFogBlend(),
                time,
                visual.getSkyboxShaderMotionSpeed(),
                5.0f,
                0.01f,
                width,
                height,
                yawRad,
                pitchRad,
                shaderFov,
                visual.isSkyboxShaderAuroraEnabled() ? 1.0f : 0.0f,
                visual.getSkyboxShaderAuroraIntensity(),
                visual.getSkyboxShaderAuroraSpeed(),
                visual.getSkyboxShaderSmallStars(),
                visual.getSkyboxShaderDustStars(),
                visual.getSkyboxShaderMediumStars(),
                visual.getSkyboxShaderLargeStars(),
                visual.getSkyboxShaderStarBrightness(),
                visual.getSkyboxShaderTwinkleStrength(),
                visual.getSkyboxShaderLayerMask()
        );

        FullScreenRenderer.ensureInit();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        MeshRenderer.setProjection(new Matrix4f());
        try {
            FullScreenRenderer.begin("Combatant Reimagined Shader Sky")
                    .attachment(framebuffer)
                    .pipeline(EffectRenderPipelines.WORLD_REIMAGINED_SKYBOX_SHADER)
                    .uniform("Fog", fog)
                    .uniform("SkyboxShader", SkyboxShaderUniforms.get())
                    .end();
        } finally {
            MeshRenderer.setProjection(worldProjection);
            mv.popMatrix();
        }
    }

    private static float extractProjectionFov(Matrix4f projection, float fallbackFov) {
        if (projection == null) {
            return fallbackFov;
        }
        float m11 = Math.abs(projection.m11());
        if (!Float.isFinite(m11) || m11 <= 1.0e-4f) {
            return fallbackFov;
        }
        float fov = (float) Math.toDegrees(2.0 * Math.atan(1.0 / m11));
        return Float.isFinite(fov) ? Mth.clamp(fov, 1.0f, 179.0f) : fallbackFov;
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

    private static float smoothstep(float x) {
        float t = Mth.clamp((x - (float) 0.0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float getTwilightFade(SkyRenderState sky) {
        float sunriseAlpha = sky.sunriseAndSunsetColor.w();
        return smoothstep(sunriseAlpha);
    }

}
