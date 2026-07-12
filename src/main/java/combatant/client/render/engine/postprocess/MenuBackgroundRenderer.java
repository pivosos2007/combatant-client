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
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import combatant.client.config.MainConfig;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.uniform.impl.MenuBackgroundUniforms;
import combatant.client.runtime.RuntimeGate;

public enum MenuBackgroundRenderer {
    ;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final int DEFAULT_ACCENT_RGB = 0x5CC8E7;
    private static final int DEFAULT_BG_RGB = 0x0E1720;
    private static ProjectionMatrixBuffer projection;
    private static boolean deferredPending;
    private static boolean deferredAurora;

    public static void render(Minecraft mc) {
        render(mc, false);
    }

    public static void render(Minecraft mc, boolean aurora) {
        if (RuntimeGate.isPanic()) return;
        if (mc == null) return;
        if (Renderer2D.isDeferredExtractRecording()) {
            deferredPending = true;
            deferredAurora = aurora;
            return;
        }

        renderNow(mc, aurora);
    }

    public static void drainDeferred(Minecraft mc) {
        if (!deferredPending) return;
        boolean aurora = deferredAurora;
        deferredPending = false;
        renderNow(mc, aurora);
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
