/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.debug;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.util.logging.DebugLog;

/**
 * Render-thread 2D diagnostics.
 *
 * <p>There are two independent probes:</p>
 * <ul>
 *     <li>GUI-state probe: writes vanilla {@link GuiGraphicsExtractor#fill} rectangles during HUD extraction.</li>
 *     <li>Immediate probe: draws with {@link Renderer2D} after vanilla {@code GuiRenderer.render()}.</li>
 * </ul>
 *
 * <p>This deliberately does not move production HUD rendering. It only tells which side is broken:
 * vanilla GUI/root-layer extraction or Combatant immediate Renderer2D/RHI.</p>
 */
public enum RenderThread2DDebugRenderer {
    ;

    private static ProjectionMatrixBuffer debugProjection;
    private static final long LOG_INTERVAL_NS = 1_000_000_000L;

    private static volatile long immediateFrame;
    private static volatile long guiStateFrame;
    private static volatile long lastImmediateNs;
    private static volatile long lastGuiStateNs;
    private static volatile int lastFramebufferWidth;
    private static volatile int lastFramebufferHeight;
    private static volatile int lastGuiWidth;
    private static volatile int lastGuiHeight;
    private static volatile int lastImmediateDraws;
    private static volatile int lastImmediateBatches;
    private static volatile int lastImmediateVertices;
    private static volatile int lastImmediateIndices;
    private static volatile int lastGuiStateRects;
    private static volatile String lastImmediateError = "";
    private static volatile String lastGuiStateError = "";

    private static long lastImmediateLogNs;
    private static long lastGuiStateLogNs;
    private static boolean immediateFailureLogged;
    private static boolean guiStateFailureLogged;


    private static ProjectionMatrixBuffer debugProjection() {
        ProjectionMatrixBuffer buffer = debugProjection;
        if (buffer == null) {
            buffer = new ProjectionMatrixBuffer("combatant-debug-2d-render-thread-projection");
            debugProjection = buffer;
        }
        return buffer;
    }

    public static void extractGuiStateProbe(GuiGraphicsExtractor ctx) {
        if (!DebugLog.isRenderThreadDebugEnabled()) return;
        if (ctx == null) {
            recordGuiStateFailure("GuiGraphicsExtractor null");
            return;
        }

        try {
            int gw = Math.max(1, ctx.guiWidth());
            int gh = Math.max(1, ctx.guiHeight());
            int rects = drawGuiStateProbe(ctx, gw, gh);

            guiStateFrame++;
            lastGuiStateNs = System.nanoTime();
            lastGuiWidth = gw;
            lastGuiHeight = gh;
            lastGuiStateRects = rects;
            lastGuiStateError = "";
            guiStateFailureLogged = false;

            logGuiStateIfDue(gw, gh, rects);
        } catch (Throwable t) {
            recordGuiStateFailure(t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    public static void renderImmediateAfterGui(DeltaTracker tickCounter) {
        if (!DebugLog.isRenderThreadDebugEnabled()) return;

        try {
            RenderSystem.assertOnRenderThread();
        } catch (Throwable t) {
            recordImmediateFailure("not on render thread: " + t.getClass().getSimpleName());
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) {
            recordImmediateFailure("minecraft/gameRenderer null");
            return;
        }
        if (Renderer2D.COLOR == null) {
            recordImmediateFailure("Renderer2D.COLOR null");
            return;
        }

        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        if (target == null) {
            recordImmediateFailure("main render target null");
            return;
        }

        int fbw = Math.max(1, target.width);
        int fbh = Math.max(1, target.height);

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4f previousMeshProjection = MeshRenderer.projection();
        boolean previousRendering3D = RenderState.rendering3D;

        var modelView = RenderSystem.getModelViewStack();
        boolean pushedModelView = false;
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.HUD_MAIN, "debug:2d_immediate_after_gui")) {
            modelView.pushMatrix();
            pushedModelView = true;
            modelView.identity();

            Matrix4f projection = framebufferOrtho(fbw, fbh);
            RenderSystem.setProjectionMatrix(debugProjection().getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
            MeshRenderer.setProjection(projection);
            RenderState.rendering3D = false;

            Renderer2D.BatchStats stats = Renderer2D.getBatchStats();
            int beforeFrameDraws = stats.getFrameDraws();
            int beforeFrameBatches = stats.getFrameBatches();
            int beforeFrameVertices = stats.getFrameVertices();
            int beforeFrameIndices = stats.getFrameIndices();

            drawImmediateProbe(fbw, fbh);

            immediateFrame++;
            lastImmediateNs = System.nanoTime();
            lastFramebufferWidth = fbw;
            lastFramebufferHeight = fbh;
            lastImmediateDraws = Math.max(0, stats.getFrameDraws() - beforeFrameDraws);
            lastImmediateBatches = Math.max(0, stats.getFrameBatches() - beforeFrameBatches);
            lastImmediateVertices = Math.max(0, stats.getFrameVertices() - beforeFrameVertices);
            lastImmediateIndices = Math.max(0, stats.getFrameIndices() - beforeFrameIndices);
            lastImmediateError = "";
            immediateFailureLogged = false;

            logImmediateIfDue(fbw, fbh, stats);
        } catch (Throwable t) {
            recordImmediateFailure(t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        } finally {
            if (pushedModelView) {
                modelView.popMatrix();
            }
            MeshRenderer.setProjection(previousMeshProjection);
            RenderState.rendering3D = previousRendering3D;
            if (previousProjection != null && previousProjectionType != null) {
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
        }
    }

    private static Matrix4f framebufferOrtho(int width, int height) {
        Projection projection = new Projection();
        projection.setupOrtho(-1000.0f, 1000.0f, Math.max(1.0f, width), Math.max(1.0f, height), true);
        return projection.getMatrix(new Matrix4f());
    }

    private static int drawGuiStateProbe(GuiGraphicsExtractor ctx, int gw, int gh) {
        int rects = 0;
        ctx.nextStratum();

        // Vanilla GUI/root-layer probe. It uses scaled GUI coordinates and vanilla GUI render states.
        ctx.fill(2, 2, 16, 16, 0xFFFF3344);
        rects++;
        ctx.fill(Math.max(0, gw - 16), 2, gw - 2, 16, 0xFF44FF66);
        rects++;
        ctx.fill(2, Math.max(0, gh - 16), 16, gh - 2, 0xFF4488FF);
        rects++;
        ctx.fill(Math.max(0, gw - 16), Math.max(0, gh - 16), gw - 2, gh - 2, 0xFFFFFF44);
        rects++;

        ctx.fill(8, 24, Math.min(gw - 8, 190), Math.min(gh - 8, 52), 0xCC111119);
        rects++;
        ctx.fill(12, 28, Math.min(gw - 12, 44), Math.min(gh - 12, 48), 0xFFFF3344);
        rects++;
        ctx.fill(48, 28, Math.min(gw - 12, 80), Math.min(gh - 12, 48), 0xFF44FF66);
        rects++;
        ctx.fill(84, 28, Math.min(gw - 12, 116), Math.min(gh - 12, 48), 0xFF4488FF);
        rects++;

        return rects;
    }

    private static void drawImmediateProbe(int fbw, int fbh) {
        Renderer2D renderer = Renderer2D.COLOR;
        double panelX = 8.0;
        double panelY = 72.0;
        double panelW = 292.0;
        double panelH = 92.0;
        double pulse = (immediateFrame % 120L) / 119.0;
        double markerX = panelX + 110.0 + pulse * 150.0;

        renderer.begin();

        // Framebuffer-space immediate Renderer2D/RHI probe. No text/glyph dependency.
        renderer.quad(0, 0, 12, 12, 0xFFFF3355);
        renderer.quad(fbw - 12.0, 0, 12, 12, 0xFF55FF66);
        renderer.quad(0, fbh - 12.0, 12, 12, 0xFF5599FF);
        renderer.quad(fbw - 12.0, fbh - 12.0, 12, 12, 0xFFFFFF55);

        renderer.quad(panelX, panelY, panelW, panelH, 0xCC050509);
        renderer.boxLines(panelX, panelY, panelW, panelH, 0xFFFF33FF);
        renderer.line(panelX, panelY, panelX + panelW, panelY + panelH, 0x80FFFFFF);
        renderer.line(panelX + panelW, panelY, panelX, panelY + panelH, 0x80FFFFFF);

        renderer.quad(panelX + 14.0, panelY + 14.0, 24.0, 24.0, 0xFFFF3355);
        renderer.quad(panelX + 44.0, panelY + 14.0, 24.0, 24.0, 0xFF55FF66);
        renderer.quad(panelX + 74.0, panelY + 14.0, 24.0, 24.0, 0xFF5599FF);
        renderer.quad(panelX + 14.0, panelY + 50.0, 244.0, 16.0, 0x66333333);
        renderer.boxLines(panelX + 14.0, panelY + 50.0, 244.0, 16.0, 0x99FFFFFF);
        renderer.quad(markerX, panelY + 51.5, 28.0, 13.0, 0xFFFFFF55);

        renderer.boxLines(18.0, 18.0, Math.max(1.0, fbw - 36.0), Math.max(1.0, fbh - 36.0), 0x55FFFFFF);

        renderer.render();
    }

    private static void logGuiStateIfDue(int gw, int gh, int rects) {
        long now = System.nanoTime();
        if (now - lastGuiStateLogNs < LOG_INTERVAL_NS) return;
        lastGuiStateLogNs = now;
        DebugLog.renderThread(
                "2D debug gui_state frame=%d gui=%dx%d rects=%d err=%s",
                guiStateFrame,
                gw,
                gh,
                rects,
                lastGuiStateError == null || lastGuiStateError.isBlank() ? "none" : lastGuiStateError
        );
    }

    private static void logImmediateIfDue(int fbw, int fbh, Renderer2D.BatchStats stats) {
        long now = System.nanoTime();
        if (now - lastImmediateLogNs < LOG_INTERVAL_NS) return;
        lastImmediateLogNs = now;
        DebugLog.renderThread(
                "2D debug immediate after_gui frame=%d fb=%dx%d drawDelta=%d/%d v=%d i=%d lastBatch=%d/%d reason=%s err=%s",
                immediateFrame,
                fbw,
                fbh,
                lastImmediateDraws,
                lastImmediateBatches,
                lastImmediateVertices,
                lastImmediateIndices,
                stats.getLastDraws(),
                stats.getLastOrder(),
                stats.getLastFlushReason(),
                stats.getLastError() == null || stats.getLastError().isBlank() ? "none" : stats.getLastError()
        );
    }

    private static void recordImmediateFailure(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "unknown" : reason;
        lastImmediateError = safeReason;
        if (!immediateFailureLogged) {
            immediateFailureLogged = true;
            DebugLog.renderThread("2D debug immediate skipped/failed: %s", safeReason);
        }
    }

    private static void recordGuiStateFailure(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "unknown" : reason;
        lastGuiStateError = safeReason;
        if (!guiStateFailureLogged) {
            guiStateFailureLogged = true;
            DebugLog.renderThread("2D debug gui_state skipped/failed: %s", safeReason);
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                immediateFrame,
                guiStateFrame,
                lastImmediateNs,
                lastGuiStateNs,
                lastFramebufferWidth,
                lastFramebufferHeight,
                lastGuiWidth,
                lastGuiHeight,
                lastImmediateDraws,
                lastImmediateBatches,
                lastImmediateVertices,
                lastImmediateIndices,
                lastGuiStateRects,
                lastImmediateError,
                lastGuiStateError
        );
    }

    public record Snapshot(long immediateFrame,
                           long guiStateFrame,
                           long lastImmediateNs,
                           long lastGuiStateNs,
                           int framebufferWidth,
                           int framebufferHeight,
                           int guiWidth,
                           int guiHeight,
                           int immediateDraws,
                           int immediateBatches,
                           int immediateVertices,
                           int immediateIndices,
                           int guiStateRects,
                           String immediateError,
                           String guiStateError) {
    }
}
