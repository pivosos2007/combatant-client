/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sky;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.features.module.modules.visuals.WorldTweaks;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.pipeline.EffectRenderPipelines;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.engine.uniform.impl.SkyboxShaderUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/** Replaces the vanilla Overworld sky inside the existing LevelRenderer framegraph. */
public enum CustomSkyboxRenderer {
    ;

    public static void render(CameraRenderState camera, GpuBufferSlice fog,
                              SkyRenderState sky, RenderTarget framebuffer) {
        ReimaginedVisual visual = Modules.get(ReimaginedVisual.class);
        if (camera == null || camera.viewRotationMatrix == null || camera.projectionMatrix == null
                || sky == null || fog == null || framebuffer == null || visual == null
                || !visual.isShaderSkyboxEnabled() || IrisRuntime.isModLoaded()) {
            return;
        }

        // The world and the sky must use the exact same camera orientation and
        // projection, including zoom, aspect-ratio overrides, and view effects.
        // Hand-reconstructed yaw/pitch/FOV reverses axes and can drift from the
        // actual camera orientation under view-bobbing and modified FOV.
        Matrix4f view = CombatantWorldMatrices.isValid()
                ? CombatantWorldMatrices.positionMatrix() : null;
        Matrix4f projection = CombatantWorldMatrices.isValid()
                ? CombatantWorldMatrices.renderProjectionMatrix() : null;
        if (view == null) view = new Matrix4f(camera.viewRotationMatrix);
        if (projection == null) projection = new Matrix4f(camera.projectionMatrix);
        if (Math.abs(view.determinant()) < 1.0e-7f
                || Math.abs(projection.determinant()) < 1.0e-7f) {
            return;
        }

        Matrix4f previousProjection = MeshRenderer.projection();
        boolean previousRendering3D = RenderState.rendering3D;
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            RenderState.rendering3D = false;
            RenderSystem.setShaderFog(fog);
            modelView.identity();
            MeshRenderer.setProjection(new Matrix4f());
            Vec3 position = CombatantWorldMatrices.cameraPosition();
            float x = position == null ? 0f : (float) (position.x % 8192.0);
            float y = position == null ? 80f : (float) position.y;
            float z = position == null ? 0f : (float) (position.z % 8192.0);
            Minecraft mc = Minecraft.getInstance();
            float time = (float) (DreamSkyClock.seconds() % 20000.0);
            float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            float rain = mc.level == null ? 0f : mc.level.getRainLevel(partial);
            float thunder = mc.level == null ? 0f : mc.level.getThunderLevel(partial);
            WorldTweaks worldTweaks = Modules.get(WorldTweaks.class);
            float fogStrength = .15f;
            if (visual.isDreamWorldTweaksPaletteSelected() && worldTweaks != null) {
                if (worldTweaks.isFogModifyEnabled()) fogStrength = .72f;
                else if (worldTweaks.disableOverworldFog()) fogStrength = 0f;
            }
            SkyboxShaderUniforms.update(
                    new Matrix4f(projection).invert(), new Matrix4f(view).invert(),
                    sky.skyColor.x(), sky.skyColor.y(), sky.skyColor.z(),
                    DreamSkyLighting.nightFactor(sky),
                    Math.max(1, framebuffer.width), Math.max(1, framebuffer.height),
                    sky.sunAngle, sky.moonAngle, sky.starAngle,
                    time, rain, fogStrength,
                    x, y, z,
                    visual.dreamCloudHeight(), visual.dreamCloudThickness(),
                    visual.dreamCloudCoverage(), visual.dreamCloudScale(),
                    DreamSkyLayout.starX(), DreamSkyLayout.starY(),
                    DreamSkyLayout.cloudX(), DreamSkyLayout.cloudZ(),
                    visual.areDreamFarCloudsEnabled(), visual.areDreamCirrusEnabled(),
                    visual.dreamCloudDensity(), visual.dreamSkyPalette(), thunder,
                    visual.isDreamAuroraEnabled() ? visual.dreamAuroraIntensity() : 0f,
                    visual.dreamAuroraSpeed());
            boolean hdrWorld = framebuffer.getColorTextureView().texture().getFormat()
                    == combatant.client.render.engine.hdr.SceneColor.FORMAT;
            RenderTarget skyTarget = hdrWorld ? framebuffer : CombatantRenderSystem.resources().frameTransientHdr(
                    "dreamcore-sky-hdr", framebuffer.width, framebuffer.height, false, "dreamcore-sky");
            FullScreenRenderer.begin("Combatant Dreamcore HDR Sky")
                    .attachment(skyTarget)
                    .pipeline(EffectRenderPipelines.WORLD_REIMAGINED_SKYBOX_HDR)
                    .uniform("Fog", fog)
                    .uniform("SkyboxShader", SkyboxShaderUniforms.get())
                    .end();
            if (!hdrWorld) {
                FullScreenRenderer.begin("Combatant Dreamcore Sky Presentation")
                        .attachment(framebuffer)
                        .pipeline(EffectRenderPipelines.WORLD_REIMAGINED_SKY_PRESENT)
                        .sampler("u_SkyHDR", skyTarget.getColorTextureView(),
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                        .end();
            }
        } finally {
            modelView.popMatrix();
            MeshRenderer.setProjection(previousProjection);
            RenderState.rendering3D = previousRendering3D;
        }
    }
}
