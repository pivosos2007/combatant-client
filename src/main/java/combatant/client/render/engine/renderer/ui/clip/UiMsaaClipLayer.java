/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.clip;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.msaa.MsaaFramebuffer;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.RenderWarp;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.rhi.scissor.GlobalScissorState;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.UIBatchUniforms;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * One transient, bounded MSAA color layer for the outermost MSAA/stencil UI clip.
 * Nested complex clips reuse its S8 attachment and advance only the stencil reference.
 */
public final class UiMsaaClipLayer {
    private static final int COLOR_PAD_PIXELS = 2;
    private static final int TARGET_BUCKET_PIXELS = 16;
    private static final String RESOLVE_OWNER = "UiMsaaClipLayer.resolve";

    private static @Nullable MsaaFramebuffer msaaTarget;
    private static @Nullable TextureTarget resolveTarget;
    private static @Nullable ActiveLayer active;
    private static @Nullable MeshBuilder compositeMesh;
    private static boolean colorClearPending;
    private static final ThreadLocal<double[]> WARP_BOUNDS = ThreadLocal.withInitial(() -> new double[4]);
    private static int allocatedSamples;
    private static String state = "idle";

    private UiMsaaClipLayer() {
    }

    public static boolean begin(UiClipSnapshot snapshot, @Nullable RenderWarp warp) {
        if (snapshot == null || !snapshot.usesMsaaStencil()) return false;
        if (active != null) return true;

        Minecraft minecraft = Minecraft.getInstance();
        ViewportContext viewport = ViewportContext.current();
        if (minecraft == null || minecraft.gameRenderer == null || viewport == null) {
            state = "missing_context";
            return false;
        }
        if (!CombatantRenderSystem.rhi().msaa().supported()) {
            state = "msaa_unsupported";
            return false;
        }

        RenderTarget parentTarget = minecraft.gameRenderer.mainRenderTarget();
        GpuTextureView parentColor = currentColorAttachment(
                parentTarget != null ? parentTarget.getColorTextureView() : null);
        if (parentColor == null) {
            state = "missing_parent_color";
            return false;
        }

        UiRect sourceBounds = snapshot.logicalBounds();
        UiRect screenBounds = warpedBounds(sourceBounds, warp);
        PixelBounds pixels = pixelBounds(screenBounds, viewport);
        if (pixels == null) {
            state = "empty_bounds";
            return false;
        }

        int requestedSamples = UiClipStack.MSAA_SAMPLES;
        try {
            ensureTargets(pixels.width(), pixels.height(), requestedSamples);
            if (msaaTarget == null || resolveTarget == null || msaaTarget.getColorTextureView() == null) {
                state = "allocation_failed";
                return false;
            }

            UiRect logicalBounds = pixels.logicalBounds(viewport);
            GlobalScissorState.Snapshot parentScissor = GlobalScissorState.snapshot();
            active = new ActiveLayer(
                    snapshot.id(), viewport, parentColor, logicalBounds,
                    pixels.left(), pixels.top(), pixels.width(), pixels.height(), parentScissor
            );
            colorClearPending = true;
            applyLocalViewport(viewport);
            replaceScissorForLocalLayer(parentScissor);
            state = "active";
            DebugLog.stencilOnChange(
                    "ui.clip.msaa.active",
                    pixels.width() + "x" + pixels.height() + "|" + msaaTarget.getSamples(),
                    "[UI clip/MSAA] local layer active: color=RGBA8 size=%dx%d samples=%d stencil=S8 bounds=%s",
                    pixels.width(), pixels.height(), msaaTarget.getSamples(), logicalBounds
            );
            return true;
        } catch (Throwable t) {
            ActiveLayer failed = active;
            active = null;
            colorClearPending = false;
            if (failed != null) {
                restoreScissor(failed.parentScissor());
                ViewportContext.applyCaptured(failed.parentViewport());
            }
            state = "begin_failed";
            DebugLog.warnOnChange(
                    "ui.clip.msaa.begin_failed",
                    t.getClass().getSimpleName() + "|" + t.getMessage(),
                    "[UI clip/MSAA] local layer begin failed; using direct stencil fallback: %s: %s",
                    t.getClass().getSimpleName(), t.getMessage()
            );
            return false;
        }
    }

    public static void end(long outerSnapshotId) {
        ActiveLayer layer = active;
        if (layer == null || layer.snapshotId() != outerSnapshotId) return;

        boolean resolved = false;
        try {
            if (msaaTarget != null && resolveTarget != null) {
                resolved = CombatantRenderSystem.rhi().msaa().resolve(msaaTarget, resolveTarget, true, false);
            }
        } catch (Throwable t) {
            DebugLog.warnOnChange(
                    "ui.clip.msaa.resolve_failed",
                    t.getClass().getSimpleName() + "|" + t.getMessage(),
                    "[UI clip/MSAA] local layer resolve failed: %s: %s",
                    t.getClass().getSimpleName(), t.getMessage()
            );
        } finally {
            active = null;
            colorClearPending = false;
            restoreScissor(layer.parentScissor());
            ViewportContext.applyCaptured(layer.parentViewport());
        }

        if (resolved) {
            composite(layer);
            state = "resolved";
        } else {
            state = "resolve_failed";
        }
    }

    public static boolean active() {
        return active != null;
    }

    public static @Nullable GpuTextureView currentColorAttachment(@Nullable GpuTextureView fallback) {
        ActiveLayer layer = active;
        if (layer == null || msaaTarget == null) return fallback;
        GpuTextureView view = msaaTarget.getColorTextureView();
        return view != null ? view : fallback;
    }

    /** Folds the local color clear into the first mask draw instead of opening a clear-only pass. */
    public static @Nullable Integer consumePendingColorClear(@Nullable GpuTextureView colorAttachment) {
        if (!colorClearPending || active == null || msaaTarget == null || colorAttachment == null) return null;
        GpuTextureView local = msaaTarget.getColorTextureView();
        if (local == null || local.texture() == null || colorAttachment.texture() != local.texture()) return null;
        colorClearPending = false;
        return 0x00000000;
    }

    /** Keeps the caller's logical coordinate system, changing only the projection window. */
    public static ViewportContext viewportFor(ViewportContext source) {
        ActiveLayer layer = active;
        if (layer == null || source == null) return source;
        UiRect bounds = layer.logicalBounds();
        Matrix4f matrix = localProjection(bounds);
        return new ViewportContext(
                source.framebufferWidth(), source.framebufferHeight(), source.scaleFactor(),
                source.width(), source.height(), source.uiScale(), source.projectionMode(), matrix
        );
    }

    public static void applyLocalViewport(ViewportContext source) {
        ViewportContext local = viewportFor(source);
        if (local != null) ViewportContext.applyCaptured(local);
    }

    /** Maps a parent-framebuffer scissor to this layer's bottom-left framebuffer coordinates. */
    public static int[] mapFramebufferScissor(@Nullable int[] parent) {
        ActiveLayer layer = active;
        if (layer == null || parent == null || parent.length != 4) return parent;

        int layerBottom = layer.parentViewport().framebufferHeight() - layer.topPx() - layer.heightPx();
        int x1 = Math.max(0, parent[0] - layer.leftPx());
        int y1 = Math.max(0, parent[1] - layerBottom);
        int x2 = Math.min(layer.widthPx(), parent[0] + parent[2] - layer.leftPx());
        int y2 = Math.min(layer.heightPx(), parent[1] + parent[3] - layerBottom);
        if (x2 <= x1 || y2 <= y1) return new int[]{0, 0, 1, 1};
        return new int[]{x1, y1, x2 - x1, y2 - y1};
    }

    public static float[] uniformLayer() {
        ActiveLayer layer = active;
        if (layer == null) {
            ViewportContext viewport = ViewportContext.current();
            float width = viewport != null ? Math.max(1.0f, viewport.width()) : 1.0f;
            float height = viewport != null ? Math.max(1.0f, viewport.height()) : 1.0f;
            return new float[]{0.0f, 0.0f, width, height};
        }
        UiRect bounds = layer.logicalBounds();
        return new float[]{bounds.x(), bounds.y(), bounds.width(), bounds.height()};
    }

    public static DebugStatus debugStatus() {
        ActiveLayer layer = active;
        return new DebugStatus(
                state,
                layer != null,
                msaaTarget != null ? msaaTarget.getSamples() : 0,
                layer != null ? layer.widthPx() : 0,
                layer != null ? layer.heightPx() : 0,
                layer != null ? layer.logicalBounds() : null
        );
    }

    public static void shutdown() {
        active = null;
        closeMesh(compositeMesh);
        compositeMesh = null;
        colorClearPending = false;
        if (msaaTarget != null) {
            MsaaFramebuffer retired = msaaTarget;
            CombatantRenderSystem.rhi().msaa().abandonTarget(retired);
            retired.destroyBuffers();
            msaaTarget = null;
        }
        resolveTarget = null;
        allocatedSamples = 0;
        state = "shutdown";
    }

    private static void ensureTargets(int width, int height, int samples) {
        if (msaaTarget == null || allocatedSamples != samples) {
            if (msaaTarget != null) {
                CombatantRenderSystem.rhi().msaa().abandonTarget(msaaTarget);
                msaaTarget.destroyBuffers();
            }
            msaaTarget = new MsaaFramebuffer("combatant-ui-msaa-clip", width, height, false, samples);
            allocatedSamples = samples;
        } else if (msaaTarget.width != width || msaaTarget.height != height) {
            msaaTarget.resize(width, height);
        }
        resolveTarget = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-ui-msaa-clip-resolve", width, height, false, RESOLVE_OWNER
        );
    }

    private static void composite(ActiveLayer layer) {
        if (resolveTarget == null || resolveTarget.getColorTextureView() == null) return;
        GpuSampler sampler = PostProcessManager.getSampler();
        if (sampler == null) return;

        closeMesh(compositeMesh);
        compositeMesh = texturedFramebufferQuad(layer.logicalBounds());
        ViewportContext viewport = layer.parentViewport();
        UIBatchUniforms.update(viewport.framebufferWidth(), viewport.framebufferHeight());
        MeshRenderer.begin()
                .attachments(layer.parentColor(), null)
                .pipeline(CombatantRenderPipelines.UI_TEXTURED_PREMULTIPLIED_ALPHA)
                .mesh(compositeMesh)
                .uniform("UIBatch", UIBatchUniforms.get())
                .sampler("u_Texture", resolveTarget.getColorTextureView(), sampler)
                .end();
    }

    private static MeshBuilder texturedFramebufferQuad(UiRect bounds) {
        MeshBuilder mesh = new MeshBuilder(
                CombatantVertexFormats.POS2_TEXTURE_COLOR, PrimitiveTopology.TRIANGLES, 4, 6
        );
        mesh.begin();
        float x = bounds.x();
        float y = bounds.y();
        float x2 = x + bounds.width();
        float y2 = y + bounds.height();
        // Render-target textures use a bottom-left framebuffer origin.
        // The resolved pixels are already warped inside this screen-space AABB. Applying the
        // current RenderWarp again here would distort and crop the composite a second time.
        int i1 = mesh.raw2(x, y).raw2(0.0, 1.0).color(255, 255, 255, 255).next();
        int i2 = mesh.raw2(x, y2).raw2(0.0, 0.0).color(255, 255, 255, 255).next();
        int i3 = mesh.raw2(x2, y2).raw2(1.0, 0.0).color(255, 255, 255, 255).next();
        int i4 = mesh.raw2(x2, y).raw2(1.0, 1.0).color(255, 255, 255, 255).next();
        mesh.quad(i1, i2, i3, i4);
        mesh.end();
        return mesh;
    }

    private static UiRect warpedBounds(UiRect bounds, @Nullable RenderWarp warp) {
        if (bounds == null || bounds.empty() || warp == null || !warp.active()) return bounds;
        double[] mapped = WARP_BOUNDS.get();
        warp.mapBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height(), mapped);
        return new UiRect((float) mapped[0], (float) mapped[1], (float) mapped[2], (float) mapped[3]);
    }

    private static @Nullable PixelBounds pixelBounds(UiRect bounds, ViewportContext viewport) {
        if (bounds == null || bounds.empty() || viewport.width() <= 0.0f || viewport.height() <= 0.0f) return null;
        float sx = viewport.framebufferWidth() / viewport.width();
        float sy = viewport.framebufferHeight() / viewport.height();
        if (!(sx > 0.0f) || !(sy > 0.0f)) return null;

        int left = Math.max(0, (int) Math.floor(bounds.x() * sx) - COLOR_PAD_PIXELS);
        int top = Math.max(0, (int) Math.floor(bounds.y() * sy) - COLOR_PAD_PIXELS);
        int right = Math.min(viewport.framebufferWidth(),
                (int) Math.ceil((bounds.x() + bounds.width()) * sx) + COLOR_PAD_PIXELS);
        int bottom = Math.min(viewport.framebufferHeight(),
                (int) Math.ceil((bounds.y() + bounds.height()) * sy) + COLOR_PAD_PIXELS);
        if (right <= left || bottom <= top) return null;

        // Animation and fractional UI scaling commonly alternate by one pixel. Stable allocation
        // buckets keep that harmless jitter from recreating the MSAA color/S8/resolve targets.
        int width = Math.min(viewport.framebufferWidth(), roundUp(right - left, TARGET_BUCKET_PIXELS));
        int height = Math.min(viewport.framebufferHeight(), roundUp(bottom - top, TARGET_BUCKET_PIXELS));
        left = Math.max(0, Math.min(left, viewport.framebufferWidth() - width));
        top = Math.max(0, Math.min(top, viewport.framebufferHeight() - height));
        return new PixelBounds(left, top, width, height);
    }

    private static int roundUp(int value, int bucket) {
        return ((Math.max(1, value) + bucket - 1) / bucket) * bucket;
    }

    private static Matrix4f localProjection(UiRect bounds) {
        Projection projection = new Projection();
        projection.setupOrtho(-1000.0f, 1000.0f,
                Math.max(1.0f, bounds.width()), Math.max(1.0f, bounds.height()), true);
        return projection.getMatrix(new Matrix4f()).translate(-bounds.x(), -bounds.y(), 0.0f);
    }

    private static void replaceScissorForLocalLayer(@Nullable GlobalScissorState.Snapshot parent) {
        if (parent == null) return;
        int[] mapped = mapFramebufferScissor(parent.rect());
        GlobalScissorState.replace(mapped[0], mapped[1], mapped[2], mapped[3]);
    }

    private static void restoreScissor(@Nullable GlobalScissorState.Snapshot parent) {
        if (parent == null) {
            if (GlobalScissorState.isSet()) GlobalScissorState.pop();
            return;
        }
        GlobalScissorState.replace(parent.x(), parent.y(), parent.width(), parent.height());
    }

    private static void closeMesh(@Nullable MeshBuilder mesh) {
        if (mesh != null) mesh.close();
    }

    private record PixelBounds(int left, int top, int width, int height) {
        UiRect logicalBounds(ViewportContext viewport) {
            float sx = viewport.framebufferWidth() / viewport.width();
            float sy = viewport.framebufferHeight() / viewport.height();
            return new UiRect(left / sx, top / sy, width / sx, height / sy);
        }
    }

    private record ActiveLayer(long snapshotId,
                               ViewportContext parentViewport,
                               GpuTextureView parentColor,
                               UiRect logicalBounds,
                               int leftPx,
                               int topPx,
                               int widthPx,
                               int heightPx,
                               @Nullable GlobalScissorState.Snapshot parentScissor) {
    }

    public record DebugStatus(String state,
                              boolean active,
                              int samples,
                              int width,
                              int height,
                              @Nullable UiRect logicalBounds) {
    }
}
