/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.visuals;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Root registry/runtime for Combatant's world visual stack.
 */
public enum CombatantVisuals {
    ;
    private static final List<CombatantVisualPass> PASSES = new ArrayList<>();
    private static final CombatantVisualFrame FRAME = new CombatantVisualFrame();

    private static TextureTarget ping;
    private static TextureTarget pong;
    private static int bufferW = -1;
    private static int bufferH = -1;
    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        initialized = true;
        DebugLog.renderThread("[Combatant] Visual stack initialized");
    }

    public static void registerPass(CombatantVisualPass pass) {
        if (pass == null || PASSES.contains(pass)) return;
        PASSES.add(pass);
        PASSES.sort(Comparator
                .comparing(CombatantVisualPass::getPhase)
                .thenComparing(passEntry -> passEntry.getId().toString()));
        if (initialized) {
            pass.init();
        }
    }

    public static List<CombatantVisualPass> getPasses() {
        return Collections.unmodifiableList(PASSES);
    }

    public static CombatantVisualFrame frame() {
        return FRAME;
    }

    public static void renderWorldBase(Minecraft mc, float tickDelta) {
        if (!initialized || mc == null) return;
        if (!hasEnabledPasses()) return;

        RenderTarget framebuffer = mc.gameRenderer.mainRenderTarget();
        if (framebuffer == null) return;

        var colorView = framebuffer.getColorTextureView();
        var depthView = PreTranslucentDepth.getDepthView();
        if (depthView == null) {
            depthView = framebuffer.getDepthTextureView();
        }
        if (colorView == null) return;

        ensureBuffers(mc);
        if (ping == null || pong == null) return;

        var pingView = ping.getColorTextureView();
        var pongView = pong.getColorTextureView();
        if (pingView == null || pongView == null) return;

        var mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        boolean prevRendering3D = RenderState.rendering3D;
        Matrix4f previousProjection = MeshRenderer.projection();
        GpuBufferSlice previousProjectionBuffer = com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = com.mojang.blaze3d.systems.RenderSystem.getProjectionType();
        mv.pushMatrix();
        mv.identity();
        MeshRenderer.setProjection(new Matrix4f().identity());
        RenderState.rendering3D = false;

        try {
            FRAME.begin(framebuffer.width, framebuffer.height, tickDelta, colorView, depthView);
            copy(colorView, pingView);

            boolean usePing = true;
            boolean anyApplied = false;

            for (CombatantVisualPass pass : PASSES) {
                if (pass == null || !pass.isEnabled()) continue;
                try {
                    pass.prepareFrame(FRAME);
                    var src = usePing ? pingView : pongView;
                    var dst = usePing ? pongView : pingView;
                    boolean applied = pass.render(FRAME, src, dst);
                    if (applied) {
                        usePing = !usePing;
                        anyApplied = true;
                    }
                } catch (Throwable t) {
                    DebugLog.error("[Combatant] Visual pass render failed: " + pass.getId(), t);
                }
            }

            if (anyApplied) {
                var finalView = usePing ? pingView : pongView;
                copy(finalView, colorView);
            }
        } finally {
            MeshRenderer.setProjection(previousProjection);
            if (previousProjectionBuffer != null && previousProjectionType != null) {
                com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(previousProjectionBuffer, previousProjectionType);
            }
            RenderState.rendering3D = prevRendering3D;
            mv.popMatrix();
        }
    }

    private static boolean hasEnabledPasses() {
        for (CombatantVisualPass pass : PASSES) {
            if (pass != null && pass.isEnabled()) return true;
        }
        return false;
    }

    public static void onResourceReload(ResourceManager manager) {
        for (CombatantVisualPass pass : PASSES) {
            try {
                pass.onResourceReload(manager);
            } catch (Throwable t) {
                DebugLog.error("[Combatant] Visual pass reload failed: " + pass.getId(), t);
            }
        }
    }

    private static void ensureBuffers(Minecraft mc) {
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w <= 0 || h <= 0) return;

        ping = CombatantRenderSystem.resources().persistentFramebuffer("combatant-visuals-ping", w, h, false, "CombatantVisuals");
        pong = CombatantRenderSystem.resources().persistentFramebuffer("combatant-visuals-pong", w, h, false, "CombatantVisuals");
        bufferW = w;
        bufferH = h;
    }

    private static void copy(com.mojang.blaze3d.textures.GpuTextureView src,
                             com.mojang.blaze3d.textures.GpuTextureView dst) {
        if (src == null || dst == null || src == dst) return;

        FullScreenRenderer.ensureInit();
        FullScreenRenderer.begin("Combatant Fullscreen Pass")
                .attachment(dst)
                .pipeline(CombatantRenderPipelines.POSTPROCESS_COPY)
                .sampler("u_Texture", src, PostProcessManager.getSampler())
                .end();
    }
}
