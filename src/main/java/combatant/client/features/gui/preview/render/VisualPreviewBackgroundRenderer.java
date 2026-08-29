/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.TextureTarget;
import combatant.client.features.gui.preview.VisualPreviewSceneContext;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.uniform.impl.VisualPreviewBackgroundUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;

/** Fast stationary panorama: the cloud field evolves and turns, but the camera never advances. */
public enum VisualPreviewBackgroundRenderer {
    ;

    private static final long TIME_ORIGIN = System.nanoTime();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static ProjectionMatrixBuffer projection;
    private static long lastVolumeFrame = Long.MIN_VALUE;
    private static int volumeWidth;
    private static int volumeHeight;

    public static void render(VisualPreviewSceneContext scene) {
        Minecraft mc = scene.minecraft();
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.mainRenderTarget() == null) return;

        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        if (width <= 0 || height <= 0) return;

        // The volume is intentionally evaluated at half linear resolution and at most every other
        // frame. It is a smooth background field, so full-resolution marching only burns GPU time.
        int renderWidth = Math.max(320, (width + 1) / 2);
        int renderHeight = Math.max(180, (height + 1) / 2);
        TextureTarget volumeTarget = CombatantRenderSystem.resources().persistentFramebuffer(
                "visual-preview-cloud-volume",
                renderWidth,
                renderHeight,
                false,
                "VisualPreviewBackground"
        );
        long frame = CombatantRenderSystem.resources().frameId();
        boolean targetChanged = renderWidth != volumeWidth || renderHeight != volumeHeight;
        boolean updateVolume = targetChanged || lastVolumeFrame == Long.MIN_VALUE || frame - lastVolumeFrame >= 2L;

        Themes.Theme theme = Theme.theme();
        int accent = theme == null ? 0xFF5CC8E7 : theme.accent();
        int background = theme == null ? 0xFF0A0E15 : theme.windowBg();
        float time = (float) ((System.nanoTime() - TIME_ORIGIN) / 1_000_000_000.0);
        if (updateVolume) {
            // Camera look/orbit rotates the world-anchored panorama. Subject-local rotation and
            // camera dolly never affect it, so MMB and wheel cannot scale/drag the sky.
            VisualPreviewBackgroundUniforms.update(
                    renderWidth,
                    renderHeight,
                    time,
                    scene.screen().cameraX(),
                    -scene.screen().cameraY(),
                    scene.screen().cameraZ(),
                    // The shader multiplies ray vectors on the left (vec2 * mat2), which applies
                    // the transpose of rot(). Feed the inverse sign so the panorama follows the
                    // actual camera orientation instead of rotating together with the subject.
                    (float) Math.toRadians(-scene.screen().cameraYaw()),
                    (float) Math.toRadians(-scene.screen().cameraPitch()),
                    accent,
                    background
            );
        }

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4f previousMeshProjection = MeshRenderer.projection();
        boolean previousRendering3D = RenderState.rendering3D;
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            if (projection == null) projection = new ProjectionMatrixBuffer("combatant-preview-bg-projection");
            Matrix4f identity = IDENTITY.identity();
            RenderSystem.setProjectionMatrix(projection.getBuffer(identity), ProjectionType.PERSPECTIVE);
            MeshRenderer.setProjection(identity);
            RenderState.rendering3D = false;

            if (updateVolume) {
                FullScreenRenderer.begin("Combatant Visual Preview Cloud Panorama")
                        .attachment(volumeTarget)
                        .pipeline(CombatantRenderPipelines.VISUAL_PREVIEW_CLOUDS)
                        .uniform("VisualPreviewBackground", VisualPreviewBackgroundUniforms.get())
                        .end();
                lastVolumeFrame = frame;
                volumeWidth = renderWidth;
                volumeHeight = renderHeight;
            }
            FullScreenRenderer.begin("Combatant Visual Preview Cloud Upscale")
                    .attachment(mc.gameRenderer.mainRenderTarget())
                    .pipeline(CombatantRenderPipelines.POSTPROCESS_COPY)
                    .sampler("u_Texture", volumeTarget.getColorTextureView(), PostProcessManager.getSampler())
                    .end();
        } finally {
            modelView.popMatrix();
            MeshRenderer.setProjection(previousMeshProjection);
            RenderState.rendering3D = previousRendering3D;
            if (previousProjection != null && previousProjectionType != null) {
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
        }
    }
}
