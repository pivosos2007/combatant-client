/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import net.minecraft.world.phys.Vec3;
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
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.SkySunUniforms;
import combatant.client.render.engine.uniform.impl.SkyboxShaderUniforms;

public enum CustomSkyboxRenderer {
    ;
    private static final float MIN_TWILIGHT_SKYBOX_ALPHA = 0.38f;
    private static final double SKY_SIZE = 160.0;
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
        skyRendering.renderSkyDisc(sky.skyColor);

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
        if (shouldRenderShaderSun()) {
            renderShaderSun(mc, mc.gameRenderer.mainRenderTarget(), camera, sky, tickDelta);
        }

        mv.popMatrix();
        MeshRenderer.setProjection(previousProjection);
        RenderState.rendering3D = prevRendering3D;

        if (!shouldRenderShaderSun()) {
            PoseStack skyMatrices = new PoseStack();
            skyRendering.renderSunriseAndSunset(skyMatrices, sky.sunAngle, sky.sunriseAndSunsetColor);
        }
        if (sky.shouldRenderDarkDisc) {
            skyRendering.renderDarkDisc();
        }
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
                sky.skyColor & 0x00FFFFFF,
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
            MeshRenderer.begin()
                    .attachments(framebuffer)
                    .pipeline(CombatantRenderPipelines.WORLD_REIMAGINED_SKYBOX_SHADER)
                    .mesh(FullScreenRenderer.vbo, FullScreenRenderer.ibo)
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
        camera.extractRenderState(cameraRenderState, fov);
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
        float angle = (accessor.combatant$getNauseaEffectTime() + tickDelta * accessor.combatant$getNauseaEffectSpeed())
                * Mth.DEG_TO_RAD;
        projection.rotate(angle, axis);
        projection.scale(1.0f / scale, 1.0f, 1.0f);
        projection.rotate(-angle, axis);
    }

    private static float smoothstep(float x) {
        float t = Mth.clamp((x - (float) 0.0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float getTwilightFade(SkyRenderState sky) {
        float sunriseAlpha = ARGB.alphaFloat(sky.sunriseAndSunsetColor);
        return smoothstep(sunriseAlpha);
    }

    private static boolean shouldRenderShaderSun() {
        return ReimaginedVisual.isWorldSunEnabledStatic();
    }

    private static void renderShaderSun(Minecraft mc, com.mojang.blaze3d.pipeline.RenderTarget framebuffer, Camera camera, SkyRenderState sky, float tickDelta) {
        if (mc.level == null) return;

        Vec3 sunDir = new Vec3(-Math.sin(sky.sunAngle), Math.cos(sky.sunAngle), 0.0).normalize();
        float sunVisibility = Mth.clamp(((float) sunDir.y + 0.0625f) / 0.125f, 0.0f, 1.0f);
        if (sunVisibility <= 0.001f) return;

        float dayFactor = Mth.clamp((float) sunDir.y, 0.0f, 1.0f);
        float rainFactor = mc.level.getRainLevel(tickDelta);
        float rainFactor2 = rainFactor * rainFactor;
        float rainFade = 1.0f - rainFactor * 0.7f;
        if (rainFade <= 0.001f) return;

        float sunSizeSetting = ReimaginedVisual.getWorldSunSizeStatic();
        float sunGlowSetting = ReimaginedVisual.getWorldSunGlowStatic();
        float sunIntensitySetting = ReimaginedVisual.getWorldSunIntensityStatic();

        float yawRad = camera.yRot() * ((float) Math.PI / 180.0f);
        float pitchRad = camera.xRot() * ((float) Math.PI / 180.0f);
        Vec3 forward = new Vec3(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();
        Vec3 right = new Vec3(0.0, 1.0, 0.0).cross(forward);
        if (right.lengthSqr() < 1.0E-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 up = forward.cross(right).normalize();

        double viewDot = sunDir.dot(forward);
        if (viewDot <= -0.2) return;

        double distance = SKY_SIZE * 0.92;
        double coreRadius = SKY_SIZE * (0.045 + sunSizeSetting * 0.95);
        double baseGlowScale = 1.08 + sunGlowSetting * 0.55;
        float lookFactor = Mth.clamp((float) ((viewDot - 0.45) / 0.50), 0.0f, 1.0f);
        lookFactor = lookFactor * lookFactor * (3.0f - 2.0f * lookFactor);
        float glareFactor = ReimaginedVisual.isWorldSunGlareEnabledStatic() ? lookFactor : 0.0f;
        double glowRadius = coreRadius * (baseGlowScale + glareFactor * 1.35);
        double radius = glowRadius;
        Vec3 center = sunDir.scale(distance);

        double angularRadius = Math.max(coreRadius / distance, 0.012);
        float sunrise = 1.0f - Math.abs(dayFactor * 2.0f - 1.0f);
        float noonFactor = 1.0f - sunrise;
        float shadowTimeVar1 = Math.abs(sunVisibility - 0.5f) * 2.0f;
        float shadowTimeVar2 = shadowTimeVar1 * shadowTimeVar1;
        float shadowTime = shadowTimeVar2 * shadowTimeVar2;

        int red = 255;
        int green = Mth.clamp((int) ((0.70f + 0.20f * dayFactor + 0.06f * sunrise) * 255.0f), 0, 255);
        int blue = Mth.clamp((int) ((0.38f + 0.48f * dayFactor - 0.08f * sunrise) * 255.0f), 0, 255);
        float alphaFactor = Mth.clamp(rainFade, 0.0f, 1.0f);
        int alpha = Mth.clamp((int) (alphaFactor * 255.0f), 0, 255);

        float innerRatio = (float) Mth.clamp(coreRadius / glowRadius, 0.08, 0.9);
        float haloStrength = Mth.clamp(
                (0.08f + sunGlowSetting * 0.16f + glareFactor * 0.42f)
                        * sunIntensitySetting * rainFade,
                0.0f,
                1.10f
        );
        float coreStrength = Mth.clamp(1.10f * sunIntensitySetting * rainFade, 0.0f, 2.2f);
        float rayStrength = 0.0f;

        MeshBuilder mesh = new MeshBuilder(CombatantRenderPipelines.WORLD_SKY_SUN);
        mesh.begin();
        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(center.x - right.x * radius - up.x * radius, center.y - right.y * radius - up.y * radius, center.z - right.z * radius - up.z * radius)
                .vec2(0.0, 0.0).color(red, green, blue, alpha).next();
        int i2 = mesh.vec3(center.x + right.x * radius - up.x * radius, center.y + right.y * radius - up.y * radius, center.z + right.z * radius - up.z * radius)
                .vec2(1.0, 0.0).color(red, green, blue, alpha).next();
        int i3 = mesh.vec3(center.x + right.x * radius + up.x * radius, center.y + right.y * radius + up.y * radius, center.z + right.z * radius + up.z * radius)
                .vec2(1.0, 1.0).color(red, green, blue, alpha).next();
        int i4 = mesh.vec3(center.x - right.x * radius + up.x * radius, center.y - right.y * radius + up.y * radius, center.z - right.z * radius + up.z * radius)
                .vec2(0.0, 1.0).color(red, green, blue, alpha).next();
        mesh.quad(i1, i2, i3, i4);
        mesh.end();

        SkySunUniforms.update(innerRatio, haloStrength, coreStrength, rayStrength);
        MeshRenderer.begin()
                .attachments(framebuffer)
                .pipeline(CombatantRenderPipelines.WORLD_SKY_SUN)
                .mesh(mesh)
                .uniform("SkySun", SkySunUniforms.get())
                .end();

    }

}
