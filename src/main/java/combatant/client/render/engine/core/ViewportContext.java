/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;

/**
 * Unified 2D viewport/projection runtime state.
 * <p>
 * Callers use ViewportContext for
 * projection mode switches, UI scale information, DrawContext tracking and immutable
 * per-frame viewport snapshots.
 */
public record ViewportContext(
        int framebufferWidth,
        int framebufferHeight,
        float scaleFactor,
        float width,
        float height,
        float uiScale,
        ProjectionMode projectionMode,
        Matrix4f projectionMatrix
) {
    private static ProjectionMatrixBuffer matrixBuffer;
    private static final Matrix4f LAST_BOUND_PROJECTION = new Matrix4f();
    private static GpuBufferSlice lastBoundProjectionSlice;
    private static long lastBoundProjectionFrame = Long.MIN_VALUE;

    // HUD producers frequently alternate UNSCALED/LOGICAL and SCALED in one extraction.
    // Cache one immutable snapshot per standard mode so each return to SCALED does not
    // allocate another Projection + Matrix4f. CUSTOM depends on the caller's current size.
    private static final ViewportContext[] STANDARD_CAPTURE_CACHE = new ViewportContext[ProjectionMode.values().length];
    private static volatile ViewportContext current = fallback(ProjectionMode.SCALED);
    private static float activeUiScale = 1.0f;
    private static GuiGraphicsExtractor currentContext;
    private static ProjectionMode activeMode = ProjectionMode.SCALED;


    private static ProjectionMatrixBuffer matrixBuffer() {
        ProjectionMatrixBuffer buffer = matrixBuffer;
        if (buffer == null) {
            buffer = new ProjectionMatrixBuffer("combatant-projection-matrix");
            matrixBuffer = buffer;
        }
        return buffer;
    }

    /**
     * Avoid writing the same projection UBO multiple times in one frame. The guard checks the
     * actual RenderSystem binding as well as the frame and matrix, so vanilla or another render
     * owner changing its projection always forces a correct rebind. GPU slices never cross frames.
     */
    private static void bindProjection(Matrix4f matrix) {
        RenderFrameContext frame = CombatantRenderSystem.currentContext();
        long frameId = frame != null ? frame.frameId() : Long.MIN_VALUE;
        if (frame != null && lastBoundProjectionSlice != null
                && lastBoundProjectionFrame == frameId
                && RenderSystem.getProjectionMatrixBuffer() == lastBoundProjectionSlice
                && RenderSystem.getProjectionType() == ProjectionType.ORTHOGRAPHIC
                && LAST_BOUND_PROJECTION.equals(matrix, 0.0f)) {
            return;
        }
        GpuBufferSlice slice = matrixBuffer().getBuffer(matrix);
        RenderSystem.setProjectionMatrix(slice, ProjectionType.ORTHOGRAPHIC);
        lastBoundProjectionSlice = slice;
        lastBoundProjectionFrame = frameId;
        LAST_BOUND_PROJECTION.set(matrix);
    }

    public static ViewportContext capture() {
        return capture(activeMode);
    }

    public static ViewportContext capture(ProjectionMode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return fallback(mode);
        }

        int fbw = Math.max(1, mc.getWindow().getWidth());
        int fbh = Math.max(1, mc.getWindow().getHeight());
        float scaleFactor = Math.max(1.0f, (float) mc.getWindow().getGuiScale());

        float width;
        float height;
        float uiScale;
        switch (mode) {
            case UNSCALED -> {
                width = fbw;
                height = fbh;
                uiScale = 1.0f;
            }
            case LOGICAL -> {
                width = HudScale.virtualWidth(fbw, fbh);
                height = HudScale.virtualHeight(fbw, fbh);
                uiScale = HudScale.scale(fbw, fbh);
            }
            case CUSTOM -> {
                ViewportContext previous = current;
                width = previous != null ? previous.width : fbw;
                height = previous != null ? previous.height : fbh;
                uiScale = previous != null ? previous.uiScale : 1.0f;
            }
            case SCALED -> {
                width = fbw / scaleFactor;
                height = fbh / scaleFactor;
                uiScale = 1.0f;
            }
            default -> throw new IllegalStateException("Unhandled projection mode: " + mode);
        }

        // Standard HUD projection modes commonly alternate during a single frame.
        // Cache per mode rather than only the most recently installed viewport.
        // Resizing, GUI scale changes and HudScale recalculation invalidate by dimensions.
        ViewportContext previous = mode == ProjectionMode.CUSTOM
                ? current : STANDARD_CAPTURE_CACHE[mode.ordinal()];
        if (previous != null && previous.projectionMode == mode
                && previous.projectionMatrix != null
                && previous.framebufferWidth == fbw && previous.framebufferHeight == fbh
                && Float.compare(previous.scaleFactor, scaleFactor) == 0
                && Float.compare(previous.width, width) == 0
                && Float.compare(previous.height, height) == 0
                && Float.compare(previous.uiScale, uiScale) == 0) {
            return previous;
        }
        ViewportContext snapshot = new ViewportContext(fbw, fbh, scaleFactor, width, height, uiScale,
                mode, matrixFor(width, height));
        if (mode != ProjectionMode.CUSTOM) STANDARD_CAPTURE_CACHE[mode.ordinal()] = snapshot;
        return snapshot;
    }

    public static ViewportContext current() {
        return current;
    }

    public static GuiGraphicsExtractor getCurrentContext() {
        return currentContext;
    }

    public static float getScaleFactor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return 1.0f;
        return (float) mc.getWindow().getGuiScale();
    }

    public static float getUiScale() {
        return activeUiScale;
    }

    public static ProjectionMode activeMode() {
        return activeMode;
    }

    public static void beginUnscaled(GuiGraphicsExtractor ctx) {
        begin(ctx, ProjectionMode.UNSCALED);
    }

    public static void beginUnscaledLogical(GuiGraphicsExtractor ctx) {
        begin(ctx, ProjectionMode.LOGICAL);
    }

    /**
     * Attach the extractor and switch to logical HUD projection without creating a new
     * GuiGraphics stratum. Use this only from hooks that are already inside an exact
     * vanilla stratum, for example PlayerTabOverlay.extractRenderState.
     */
    public static void beginCurrentStratumUnscaledLogical(GuiGraphicsExtractor ctx) {
        begin(ctx, ProjectionMode.LOGICAL, false);
    }

    public static void beginScaled(GuiGraphicsExtractor ctx) {
        begin(ctx, ProjectionMode.SCALED);
    }

    public static void end(GuiGraphicsExtractor ctx) {
        begin(ctx, ProjectionMode.SCALED);
    }

    /**
     * Restore vanilla scaled projection without allocating another GuiGraphics stratum.
     * Use after beginCurrentStratumUnscaledLogical() from hooks that are already inside
     * a vanilla-created stratum.
     */
    public static void endCurrentStratum(GuiGraphicsExtractor ctx) {
        begin(ctx, ProjectionMode.SCALED, false);
    }

    public static void unscaledProjection() {
        apply(ProjectionMode.UNSCALED);
    }

    public static void logicalProjection() {
        apply(ProjectionMode.LOGICAL);
    }

    public static void scaledProjection() {
        apply(ProjectionMode.SCALED);
    }


    /**
     * Apply an already captured 2D viewport/projection without causing Renderer2D flushes.
     * Used by render-thread replay of 2D batches recorded during GUI extraction.
     */
    public static void applyCaptured(ViewportContext next) {
        if (next == null) return;
        Matrix4f matrix = next.projectionMatrix != null ? new Matrix4f(next.projectionMatrix) : matrixFor(next.width, next.height);
        bindProjection(matrix);
        MeshRenderer.setProjection(matrix);
        activeUiScale = next.uiScale;
        activeMode = next.projectionMode;
        current = new ViewportContext(
                next.framebufferWidth,
                next.framebufferHeight,
                next.scaleFactor,
                next.width,
                next.height,
                next.uiScale,
                next.projectionMode,
                matrix
        );
        RenderState.rendering3D = false;
    }

    public static void projectionForSize(float width, float height) {
        float safeWidth = Math.max(1.0f, width);
        float safeHeight = Math.max(1.0f, height);
        Minecraft mc = Minecraft.getInstance();
        int fbw = mc != null && mc.getWindow() != null ? Math.max(1, mc.getWindow().getWidth()) : (int) safeWidth;
        int fbh = mc != null && mc.getWindow() != null ? Math.max(1, mc.getWindow().getHeight()) : (int) safeHeight;
        float scaleFactor = mc != null && mc.getWindow() != null ? Math.max(1.0f, (float) mc.getWindow().getGuiScale()) : 1.0f;

        Matrix4f matrix = matrixFor(safeWidth, safeHeight);
        ViewportContext next = new ViewportContext(fbw, fbh, scaleFactor, safeWidth, safeHeight, 1.0f, ProjectionMode.CUSTOM, new Matrix4f(matrix));
        if (isActiveProjection(next, ProjectionMode.CUSTOM)) {
            bindProjection(matrix);
            MeshRenderer.setProjection(matrix);
            current = next;
            RenderState.rendering3D = false;
            return;
        }

        Renderer2D.flushBatch(Renderer2D.FlushReason.PROJECTION);
        bindProjection(matrix);
        MeshRenderer.setProjection(matrix);

        activeUiScale = 1.0f;
        activeMode = ProjectionMode.CUSTOM;
        current = next;
        RenderState.rendering3D = false;
    }

    private static void begin(GuiGraphicsExtractor ctx, ProjectionMode mode) {
        begin(ctx, mode, true);
    }

    private static void begin(GuiGraphicsExtractor ctx, ProjectionMode mode, boolean nextStratum) {
        currentContext = ctx;
        if (nextStratum && ctx != null) ctx.nextStratum();
        apply(mode, true);
    }

    private static void apply(ProjectionMode mode) {
        apply(mode, true);
    }

    private static void apply(ProjectionMode mode, boolean flush) {
        ViewportContext next = capture(mode);
        if (isActiveProjection(next, mode)) {
            bindProjection(next.projectionMatrix);
            MeshRenderer.setProjection(next.projectionMatrix);
            activeUiScale = next.uiScale;
            activeMode = mode;
            current = next;
            RenderState.rendering3D = false;
            return;
        }

        if (flush) Renderer2D.flushBatch(Renderer2D.FlushReason.PROJECTION);
        bindProjection(next.projectionMatrix);
        MeshRenderer.setProjection(next.projectionMatrix);
        activeUiScale = next.uiScale;
        activeMode = mode;
        current = next;
        RenderState.rendering3D = false;
    }

    private static boolean isActiveProjection(ViewportContext next, ProjectionMode mode) {
        ViewportContext previous = current;
        return previous != null
                && activeMode == mode
                && previous.framebufferWidth == next.framebufferWidth
                && previous.framebufferHeight == next.framebufferHeight
                && Float.compare(previous.scaleFactor, next.scaleFactor) == 0
                && Float.compare(previous.width, next.width) == 0
                && Float.compare(previous.height, next.height) == 0
                && Float.compare(previous.uiScale, next.uiScale) == 0;
    }

    private static ViewportContext fallback(ProjectionMode mode) {
        Matrix4f matrix = matrixFor(1.0f, 1.0f);
        return new ViewportContext(1, 1, 1.0f, 1.0f, 1.0f, 1.0f, mode, matrix);
    }

    private static Matrix4f matrixFor(float width, float height) {
        Projection projection = new Projection();
        projection.setupOrtho(-1000.0f, 1000.0f, width, height, true);
        return projection.getMatrix(new Matrix4f());
    }

    public enum ProjectionMode {
        /**
         * Framebuffer pixel coordinates.
         */
        UNSCALED,
        /**
         * Combatant logical HUD coordinates from HudScale.
         */
        LOGICAL,
        /**
         * Vanilla scaled GUI coordinates.
         */
        SCALED,
        /**
         * Explicit caller-provided projection size.
         */
        CUSTOM
    }
}
