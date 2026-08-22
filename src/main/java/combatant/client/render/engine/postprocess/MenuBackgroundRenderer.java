/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess;


import combatant.client.features.theme.Theme;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.renderer.Renderer2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import combatant.client.config.MainConfig;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.uniform.impl.MenuBackgroundUniforms;
import combatant.client.render.engine.uniform.impl.UIBatchUniforms;
import combatant.client.runtime.RuntimeGate;

public enum MenuBackgroundRenderer {
    ;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final int DEFAULT_ACCENT_RGB = 0x5CC8E7;
    private static final int DEFAULT_BG_RGB = 0x0E1720;
    private static ProjectionMatrixBuffer projection;
    private static boolean deferredPending;
    private static boolean deferredAurora;
    private static Identifier deferredTexture;

    public static void render(Minecraft mc) {
        render(mc, false);
    }

    public static void render(Minecraft mc, boolean aurora) {
        if (RuntimeGate.isPanic()) return;
        if (mc == null) return;
        if (Renderer2D.isDeferredExtractRecording()) {
            deferredPending = true;
            deferredAurora = aurora;
            deferredTexture = null;
            return;
        }

        renderNow(mc, aurora);
    }

    /** Queues a plain cover-fit image before the GUI glass source is captured. */
    public static void renderTexture(Minecraft mc, Identifier texture) {
        if (RuntimeGate.isPanic() || mc == null || texture == null) return;
        if (Renderer2D.isDeferredExtractRecording()) {
            deferredPending = true;
            deferredTexture = texture;
            return;
        }
        renderTextureNow(mc, texture);
    }

    public static void drainDeferred(Minecraft mc) {
        if (!deferredPending) return;
        boolean aurora = deferredAurora;
        Identifier texture = deferredTexture;
        deferredPending = false;
        deferredTexture = null;
        if (texture != null) renderTextureNow(mc, texture);
        else renderNow(mc, aurora);
    }

    private static void renderTextureNow(Minecraft mc, Identifier textureId) {
        if (RuntimeGate.isPanic() || mc == null || textureId == null) return;
        RenderTarget framebuffer = mc.gameRenderer.mainRenderTarget();
        if (framebuffer == null) return;
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        if (width <= 0 || height <= 0) return;

        AbstractTexture texture = mc.getTextureManager().getTexture(textureId);
        if (texture == null || texture.getTextureView() == null || texture.getSampler() == null) return;

        FullScreenRenderer.ensureInit();
        UIBatchUniforms.update(width, height);
        var modelView = RenderSystem.getModelViewStack();
        boolean pushedModelView = false;
        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4f previousMeshProjection = MeshRenderer.projection();
        boolean previousRendering3D = RenderState.rendering3D;

        try {
            modelView.pushMatrix();
            pushedModelView = true;
            modelView.identity();
            if (projection == null) projection = new ProjectionMatrixBuffer("combatant-menu-bg-projection");
            Matrix4f identityProjection = IDENTITY.identity();
            RenderSystem.setProjectionMatrix(projection.getBuffer(identityProjection), ProjectionType.PERSPECTIVE);
            MeshRenderer.setProjection(identityProjection);
            RenderState.rendering3D = false;

            FullScreenRenderer.begin("Combatant Main Menu Texture")
                    .attachment(framebuffer)
                    .pipeline(CombatantRenderPipelines.MAIN_MENU_TEXTURE_BACKGROUND)
                    .uniform("UIBatch", UIBatchUniforms.get())
                    .sampler("u_Texture", texture.getTextureView(), texture.getSampler())
                    .end();
        } finally {
            if (pushedModelView) modelView.popMatrix();
            MeshRenderer.setProjection(previousMeshProjection);
            RenderState.rendering3D = previousRendering3D;
            if (previousProjection != null && previousProjectionType != null) {
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
        }
    }

    private static void renderNow(Minecraft mc, boolean aurora) {
        if (RuntimeGate.isPanic()) return;
        if (mc == null) return;

        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        if (fb == null) return;

        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w <= 0 || h <= 0) return;

        FullScreenRenderer.ensureInit();

        float time = (float) (Util.getMillis() / 1000.0);
        int accentRgb = DEFAULT_ACCENT_RGB;
        int bgRgb = DEFAULT_BG_RGB;
        MainConfig cfg = MainConfig.get();
        if (cfg != null && cfg.isMenuBackgroundUseTheme()) {
            Themes.Theme theme = Theme.theme();
            if (theme != null) {
                accentRgb = theme.accent() & 0x00FFFFFF;
                bgRgb = theme.windowBg() & 0x00FFFFFF;
            }
        }
        MenuBackgroundUniforms.update(w, h, time, accentRgb, bgRgb);

        var mv = RenderSystem.getModelViewStack();
        boolean pushedModelView = false;

        GpuBufferSlice prevProj = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType prevProjType = RenderSystem.getProjectionType();
        Matrix4f prevMeshProjection = MeshRenderer.projection();
        boolean prevRendering3D = RenderState.rendering3D;

        try {
            mv.pushMatrix();
            pushedModelView = true;
            mv.identity();

            if (projection == null) {
                projection = new ProjectionMatrixBuffer("combatant-menu-bg-projection");
            }
            Matrix4f proj = IDENTITY.identity();
            RenderSystem.setProjectionMatrix(projection.getBuffer(proj), ProjectionType.PERSPECTIVE);
            MeshRenderer.setProjection(proj);
            RenderState.rendering3D = false;

            var pipeline = aurora
                    ? CombatantRenderPipelines.MENU_BACKGROUND_AURORA
                    : CombatantRenderPipelines.MENU_BACKGROUND_WAVES;

            FullScreenRenderer.begin("Combatant Fullscreen Pass")
                    .attachment(fb)
                    .pipeline(pipeline)
                    .uniform("MenuBackground", MenuBackgroundUniforms.get())
                    .end();
        } finally {
            if (pushedModelView) {
                mv.popMatrix();
            }
            MeshRenderer.setProjection(prevMeshProjection);
            RenderState.rendering3D = prevRendering3D;
            if (prevProj != null && prevProjType != null) {
                RenderSystem.setProjectionMatrix(prevProj, prevProjType);
            }
        }
    }
}
