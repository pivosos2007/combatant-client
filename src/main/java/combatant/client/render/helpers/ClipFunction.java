/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.renderer.RenderWarp;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.*;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiClipStack;
import combatant.client.render.engine.renderer.ui.clip.UiClipStrategy;
import combatant.client.render.engine.renderer.ui.clip.UiMsaaClipLayer;
import combatant.client.render.engine.rhi.clip.ShapeClipBackend;
import combatant.client.util.logging.DebugLog;

/**
 * Compatibility facade over the renderer-owned shape clipping stack.
 *
 * <p>This state is deliberately independent from {@link ScissorFunction}. The current RHI backend
 * still lowers masks through stencil while analytic pipeline variants are introduced.</p>
 */
public enum ClipFunction {
    ;
    private static final int MAX_SHAPE_DEPTH = 250;
    private static final int MASK_COLOR = 0xFFFFFFFF;
    private static final boolean DEBUG_CLIP_SCENE_PREFERS_MSAA = Boolean.getBoolean("combatant.render.debug.clipScene")
            || enabledValue(System.getenv("COMBATANT_UI_CLIP_DEBUG"));
    private static final boolean STENCIL_DEBUG_OVERLAY = Boolean.getBoolean("combatant.render.debug.stencilOverlay");
    private static final UiClipStack STACK = new UiClipStack(MAX_SHAPE_DEPTH);
    private static final double[] POINTS = new double[512];
    private static boolean warnedShapeUnsupported;
    private static boolean warnedShapeDepth;
    private static boolean warnedAnalyticRequired;

    private static boolean enabledValue(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    /** @deprecated use {@link ScissorFunction#pushRaw(float, float, float, float)} for raster scissor semantics. */
    @Deprecated
    public static boolean pushRaw(float x, float y, float width, float height) {
        return pushRect(x, y, width, height);
    }

    /** Shape-aware rectangular clip. This no longer aliases {@link ScissorFunction}. */
    public static boolean pushRect(double x, double y, double width, double height) {
        return push(UiShape.rect(x, y, width, height));
    }

    public static boolean pushRoundedRect(double x, double y, double width, double height, double radius) {
        return push(UiShape.roundedRect(x, y, width, height, radius));
    }

    public static boolean pushRoundedRect(double x, double y, double width, double height,
                                          double topLeft, double topRight, double bottomRight, double bottomLeft) {
        return push(UiShape.roundedRect(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft));
    }

    /**
     * Requires the clip boundary to remain shader-visible. Returns false instead of silently
     * promoting to stencil when the shape, parent strategy or analytic stack capacity cannot comply.
     */
    public static boolean pushAnalyticRequired(UiShape shape) {
        if (!STACK.canPushAnalytic(shape)) {
            warnAnalyticRequired(shape);
            return false;
        }
        return push(shape, UiClipStrategy.ANALYTIC);
    }

    public static boolean pushRoundedRectAnalyticRequired(double x, double y, double width, double height,
                                                          double radius) {
        return pushAnalyticRequired(UiShape.roundedRect(x, y, width, height, radius));
    }

    public static boolean pushRoundedRectAnalyticRequired(double x, double y, double width, double height,
                                                          double topLeft, double topRight,
                                                          double bottomRight, double bottomLeft) {
        return pushAnalyticRequired(UiShape.roundedRect(
                x, y, width, height, topLeft, topRight, bottomRight, bottomLeft));
    }

    /** Transitional fallback for subtrees whose material families cannot yet consume analytic clip state. */
    public static boolean pushRoundedRectMsaaStencil(double x, double y, double width, double height, double radius) {
        return push(UiShape.roundedRect(x, y, width, height, radius), UiClipStrategy.MSAA_STENCIL);
    }

    public static boolean pushChamferedRect(double x, double y, double width, double height, double chamfer) {
        return push(UiShape.chamferedRect(x, y, width, height, chamfer, chamfer, chamfer, chamfer));
    }

    public static boolean pushCircle(double cx, double cy, double radius) {
        return push(UiShape.circle(cx, cy, radius));
    }

    public static boolean push(UiShape shape) {
        return push(shape, DEBUG_CLIP_SCENE_PREFERS_MSAA ? UiClipStrategy.MSAA_STENCIL : null);
    }

    public static boolean pushMsaaStencil(UiShape shape) {
        return push(shape, UiClipStrategy.MSAA_STENCIL);
    }

    private static boolean push(UiShape shape, UiClipStrategy requestedStrategy) {
        if (shape == null) return false;
        UiRect bounds = shape.bounds();
        if (bounds == null || bounds.empty()) return false;

        if (!STACK.canPush()) {
            warnShapeDepth();
            return false;
        }

        Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
        // A clipped liquid-glass consumer must refresh the shared blur before this
        // shape installs its clip state. The refresh remains on the normal
        // shared Kawase chain and therefore never needs a clip-state bypass.
        Renderer2D.prepareLiquidGlassBlurBeforeShapeClipIfRequested();
        UiClipSnapshot parentSnapshot = STACK.current();
        UiClipStack.Layer layer = requestedStrategy != null
                ? STACK.push(shape, requestedStrategy)
                : STACK.push(shape);
        if (layer == null) return false;
        UiClipSnapshot snapshot = layer.snapshot();
        if (snapshot.usesAnalyticPipeline()) {
            return true;
        }

        ShapeClipBackend clip = clipBackend();
        if (!clip.supported()) {
            STACK.pop();
            warnShapeUnsupported(shape, clip, "backend unsupported");
            return false;
        }
        int parent = layer.parentReference();
        int reference = layer.reference();
        boolean clear = parent == 0;
        boolean rebuildFromAnalyticParent = parentSnapshot.usesAnalyticPipeline();
        boolean beginsLocalMsaaLayer = !parentSnapshot.usesMsaaStencil();
        RenderWarp clipWarp = RenderWarpStack.current();
        String attachmentReason = "ClipFunction.push kind=" + shape.kind() + " bounds=" + bounds;

        if (Renderer2D.isDeferredExtractRecording()) {
            Renderer2D.deferRenderThreadAction(() -> {
                try (RenderWarpStack.Scope ignored = RenderWarpStack.push(clipWarp)) {
                    if (beginsLocalMsaaLayer) {
                        UiMsaaClipLayer.begin(snapshot, clipWarp);
                    }
                    ShapeClipBackend renderClip = clipBackend();
                    renderClip.requireRenderPassAttachment(attachmentReason);
                    if (clear || rebuildFromAnalyticParent) {
                        renderClip.requestClear("first shape layer");
                    }
                    if (rebuildFromAnalyticParent) {
                        renderStencilSnapshot(renderClip, snapshot);
                    } else {
                        renderClip.beginWrite(parent, reference);
                        renderMaskShape(shape);
                        renderClip.beginTest(reference);
                    }
                    renderStencilDebugProbe(shape, reference, false);
                }
            });
        } else {
            if (beginsLocalMsaaLayer) {
                UiMsaaClipLayer.begin(snapshot, clipWarp);
            }
            clip.requireRenderPassAttachment(attachmentReason);
            if (clear || rebuildFromAnalyticParent) {
                clip.requestClear("first shape layer");
            }
            if (rebuildFromAnalyticParent) {
                renderStencilSnapshot(clip, snapshot);
            } else {
                clip.beginWrite(parent, reference);
                renderMaskShape(shape);
                clip.beginTest(reference);
            }
            renderStencilDebugProbe(shape, reference, false);
        }

        return true;
    }

    public static Scope scope(UiShape shape) {
        boolean pushed = push(shape);
        return new Scope(pushed);
    }

    public static Scope rectScope(double x, double y, double width, double height) {
        boolean pushed = pushRect(x, y, width, height);
        return new Scope(pushed);
    }

    public static void pop() {
        if (STACK.depth() == 0) return;
        // Flush while the closing scope is still current so deferred submit metadata captures it.
        Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
        UiClipStack.Layer layer = STACK.pop();
        if (layer == null) return;
        UiClipSnapshot previous = STACK.current();
        if (layer.snapshot().usesAnalyticPipeline()) {
            return;
        }
        int previousReference = previous.stencilReference();
        boolean returnsToAnalytic = previous.usesAnalyticPipeline();
        boolean endsLocalMsaaLayer = !previous.usesMsaaStencil();
        RenderWarp clipWarp = RenderWarpStack.current();
        if (Renderer2D.isDeferredExtractRecording()) {
            Renderer2D.deferRenderThreadAction(() -> {
                try (RenderWarpStack.Scope ignored = RenderWarpStack.push(clipWarp)) {
                    ShapeClipBackend renderClip = clipBackend();
                    if (returnsToAnalytic || previousReference == 0) {
                        renderClip.disable();
                        if (endsLocalMsaaLayer) {
                            UiMsaaClipLayer.end(layer.snapshot().id());
                        }
                        return;
                    }
                    renderStencilDebugProbe(layer.shape(), layer.reference(), true);
                    Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
                    renderClip.beginRestore(layer.reference(), layer.parentReference());
                    renderMaskShape(layer.shape());
                    if (previousReference > 0) {
                        renderClip.beginTest(previousReference);
                    } else {
                        renderClip.disable();
                    }
                }
            });
            return;
        }

        ShapeClipBackend clip = clipBackend();
        if (returnsToAnalytic || previousReference == 0) {
            clip.disable();
            if (endsLocalMsaaLayer) {
                UiMsaaClipLayer.end(layer.snapshot().id());
            }
            return;
        }
        renderStencilDebugProbe(layer.shape(), layer.reference(), true);
        Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
        clip.beginRestore(layer.reference(), layer.parentReference());
        renderMaskShape(layer.shape());
        applyPreviousShapeTest();
    }

    public static int depth() {
        return STACK.depth();
    }

    public static boolean isShapeClipActive() {
        return currentSnapshot().active();
    }

    public static UiClipSnapshot currentSnapshot() {
        return STACK.current();
    }

    /**
     * @deprecated use {@link #isShapeClipActive()} — the backend is no longer required to be stencil.
     */
    @Deprecated
    public static boolean isStencilActive() {
        return isShapeClipActive();
    }

    private static void warnShapeUnsupported(UiShape shape, ShapeClipBackend backend, String reason) {
        if (warnedShapeUnsupported) return;
        warnedShapeUnsupported = true;
        UiRect bounds = shape.bounds();
        DebugLog.warnOnChange(
                "clipfunction.shape.unsupported",
                reason + "|" + shape.kind() + "|" + bounds + "|" + STACK.depth() + "|" + backend,
                "ClipFunction: shape push rejected: %s. kind=%s bounds=%s depth=%d backend=%s",
                reason,
                shape.kind(),
                bounds,
                STACK.depth(),
                backend
        );
    }

    private static void warnShapeDepth() {
        if (warnedShapeDepth) return;
        warnedShapeDepth = true;
        DebugLog.warn("ClipFunction: shape clip stack exceeded %d layers. Clip push rejected.", MAX_SHAPE_DEPTH);
    }

    private static void warnAnalyticRequired(UiShape shape) {
        if (warnedAnalyticRequired) return;
        warnedAnalyticRequired = true;
        DebugLog.warn("ClipFunction: ANALYTIC_REQUIRED clip rejected instead of falling back. kind=%s bounds=%s depth=%d",
                shape != null ? shape.kind() : null,
                shape != null ? shape.bounds() : null,
                STACK.depth());
    }

    private static void applyPreviousShapeTest() {
        int reference = currentShapeReference();
        if (reference > 0) {
            clipBackend().beginTest(reference);
        } else {
            clipBackend().disable();
        }
    }


    private static void renderStencilDebugProbe(UiShape shape, int reference, boolean overlay) {
        if (!DebugLog.isStencilDebugEnabled() || !STENCIL_DEBUG_OVERLAY) return;
        UiRect b = shape.bounds();
        if (b == null || b.empty()) return;

        int fill = overlay ? 0x5AFF2A2A : 0x2600FF7A;
        int border = overlay ? 0xFFFFE600 : 0xC000FF7A;
        boolean auto = !Renderer2D.isBatching();
        if (auto) Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(b.x(), b.y(), b.width(), b.height(), fill);
        Renderer2D.COLOR.boxLines(b.x(), b.y(), b.width(), b.height(), border);
        if (auto) {
            Renderer2D.COLOR.render();
        } else {
            Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
        }
        DebugLog.stencilOnChange(
                "clipfunction.debug.probe",
                (overlay ? "overlay|" : "push|") + shape.kind() + "|" + reference + "|" + b,
                "ClipFunction debug probe rendered through stencil ref=%d kind=%s overlay=%s bounds=%s",
                reference,
                shape.kind(),
                overlay,
                b
        );
    }

    private static ShapeClipBackend clipBackend() {
        return CombatantRenderSystem.rhi().shapeClip();
    }

    private static int currentShapeReference() {
        return STACK.currentReference();
    }

    private static void renderMaskShape(UiShape shape) {
        boolean auto = !Renderer2D.isBatching();
        if (auto) Renderer2D.COLOR.begin();
        emitMaskShape(Renderer2D.COLOR, shape);
        if (auto) {
            Renderer2D.COLOR.render();
        } else {
            Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
        }
    }

    private static void renderStencilSnapshot(ShapeClipBackend clip, UiClipSnapshot snapshot) {
        int reference = 0;
        for (UiShape primitive : snapshot.primitives()) {
            int next = reference + 1;
            clip.beginWrite(reference, next);
            renderMaskShape(primitive);
            reference = next;
        }
        clip.beginTest(reference);
    }

    private static void emitMaskShape(Renderer2D renderer, UiShape shape) {
        UiRect b = shape.bounds();
        switch (shape.kind()) {
            case RECT -> emitRectMask(renderer, shape, b);
            case CIRCLE -> emitCircleMask(renderer, b, shape.radius());
            case POLYGON -> emitPolygonMask(renderer, shape);
            case FLEXIBLE_BOX -> emitBoxMask(renderer, shape.box(), b);
            default -> renderer.quad(b.x(), b.y(), b.width(), b.height(), MASK_COLOR);
        }
    }

    private static void emitRectMask(Renderer2D renderer, UiShape shape, UiRect b) {
        if (shape.cornerMode() == UiCornerMode.ROUNDED) {
            int count = buildRoundedRectPolygon(b, shape.roundedRadii());
            renderer.polygon(POINTS, count, MASK_COLOR);
            return;
        }
        if (shape.cornerMode() == UiCornerMode.CHAMFERED) {
            int count = buildChamferedRectPolygon(b, shape.chamferRadii());
            renderer.polygon(POINTS, count, MASK_COLOR);
            return;
        }
        renderer.quad(b.x(), b.y(), b.width(), b.height(), MASK_COLOR);
    }

    private static void emitCircleMask(Renderer2D renderer, UiRect b, float radius) {
        float cx = b.x() + b.width() * 0.5f;
        float cy = b.y() + b.height() * 0.5f;
        float r = radius > 0.0001f ? radius : Math.min(b.width(), b.height()) * 0.5f;
        int count = buildEllipsePolygon(cx, cy, r, r, 40);
        renderer.polygon(POINTS, count, MASK_COLOR);
    }

    private static void emitPolygonMask(Renderer2D renderer, UiShape shape) {
        double[] points = shape.points();
        int count = Math.min(shape.pointCount(), points != null ? points.length / 2 : 0);
        if (!shape.closed() || count < 3) {
            UiRect b = shape.bounds();
            renderer.quad(b.x(), b.y(), b.width(), b.height(), MASK_COLOR);
            return;
        }
        renderer.polygon(points, count, MASK_COLOR);
    }

    private static void emitBoxMask(Renderer2D renderer, UiBoxShape box, UiRect fallbackBounds) {
        if (box == null || !box.hasOnlyStraightEdges()) {
            renderer.quad(fallbackBounds.x(), fallbackBounds.y(), fallbackBounds.width(), fallbackBounds.height(), MASK_COLOR);
            return;
        }
        int count = buildBoxPolygon(box);
        if (count >= 3) {
            renderer.polygon(POINTS, count, MASK_COLOR);
        } else {
            renderer.quad(fallbackBounds.x(), fallbackBounds.y(), fallbackBounds.width(), fallbackBounds.height(), MASK_COLOR);
        }
    }

    private static int buildRoundedRectPolygon(UiRect b, UiCornerRadii radii) {
        resetPoints();
        float x = b.x();
        float y = b.y();
        float w = b.width();
        float h = b.height();
        float maxR = Math.max(0.0f, Math.min(w, h) * 0.5f);
        float tl = Math.min(radii.topLeft(), maxR);
        float tr = Math.min(radii.topRight(), maxR);
        float br = Math.min(radii.bottomRight(), maxR);
        float bl = Math.min(radii.bottomLeft(), maxR);

        int n = 0;
        n = appendArc(n, x + tl, y + tl, tl, tl, 180.0, 270.0);
        n = appendArc(n, x + w - tr, y + tr, tr, tr, 270.0, 360.0);
        n = appendArc(n, x + w - br, y + h - br, br, br, 0.0, 90.0);
        n = appendArc(n, x + bl, y + h - bl, bl, bl, 90.0, 180.0);
        return n;
    }

    private static int buildChamferedRectPolygon(UiRect b, UiChamferRadii radii) {
        resetPoints();
        float x = b.x();
        float y = b.y();
        float w = b.width();
        float h = b.height();
        float tlX = clamp(radii.topLeftX(), 0, w * 0.5f);
        float tlY = clamp(radii.topLeftY(), 0, h * 0.5f);
        float trX = clamp(radii.topRightX(), 0, w * 0.5f);
        float trY = clamp(radii.topRightY(), 0, h * 0.5f);
        float brX = clamp(radii.bottomRightX(), 0, w * 0.5f);
        float brY = clamp(radii.bottomRightY(), 0, h * 0.5f);
        float blX = clamp(radii.bottomLeftX(), 0, w * 0.5f);
        float blY = clamp(radii.bottomLeftY(), 0, h * 0.5f);
        int n = 0;
        n = addPoint(n, x + tlX, y);
        n = addPoint(n, x + w - trX, y);
        n = addPoint(n, x + w, y + trY);
        n = addPoint(n, x + w, y + h - brY);
        n = addPoint(n, x + w - brX, y + h);
        n = addPoint(n, x + blX, y + h);
        n = addPoint(n, x, y + h - blY);
        n = addPoint(n, x, y + tlY);
        return n;
    }

    private static int buildEllipsePolygon(double cx, double cy, double rx, double ry, int requestedSegments) {
        resetPoints();
        int segments = Math.max(12, Math.min(96, requestedSegments));
        int n = 0;
        for (int i = 0; i < segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            n = addPoint(n, cx + Math.cos(angle) * rx, cy + Math.sin(angle) * ry);
        }
        return n;
    }

    private static int buildBoxPolygon(UiBoxShape box) {
        resetPoints();
        UiRect b = box.bounds();
        float x = b.x();
        float y = b.y();
        float w = b.width();
        float h = b.height();
        int n = 0;
        n = appendCorner(n, x, y, w, h, box.topLeft(), 0);
        n = appendCorner(n, x, y, w, h, box.topRight(), 1);
        n = appendCorner(n, x, y, w, h, box.bottomRight(), 2);
        n = appendCorner(n, x, y, w, h, box.bottomLeft(), 3);
        return n;
    }

    private static int appendCorner(int n, float x, float y, float w, float h, UiCornerSpec corner, int index) {
        UiCornerSpec c = corner != null ? corner : UiCornerSpec.SQUARE;
        if (c.kind() == UiCornerKind.ROUNDED || c.kind() == UiCornerKind.CONCAVE_ROUNDED) {
            float rx = Math.min(c.radiusX(), w * 0.5f);
            float ry = Math.min(c.radiusY(), h * 0.5f);
            return switch (index) {
                case 0 -> appendArc(n, x + rx, y + ry, rx, ry, 180.0, 270.0);
                case 1 -> appendArc(n, x + w - rx, y + ry, rx, ry, 270.0, 360.0);
                case 2 -> appendArc(n, x + w - rx, y + h - ry, rx, ry, 0.0, 90.0);
                default -> appendArc(n, x + rx, y + h - ry, rx, ry, 90.0, 180.0);
            };
        }
        if (c.kind() == UiCornerKind.CHAMFERED || c.kind() == UiCornerKind.NOTCHED) {
            float cx = Math.min(c.cutX(), w * 0.5f);
            float cy = Math.min(c.cutY(), h * 0.5f);
            return switch (index) {
                case 0 -> addChamfer(n, x + cx, y, x, y + cy);
                case 1 -> addChamfer(n, x + w - cx, y, x + w, y + cy);
                case 2 -> addChamfer(n, x + w, y + h - cy, x + w - cx, y + h);
                default -> addChamfer(n, x + cx, y + h, x, y + h - cy);
            };
        }
        return switch (index) {
            case 0 -> addPoint(n, x, y);
            case 1 -> addPoint(n, x + w, y);
            case 2 -> addPoint(n, x + w, y + h);
            default -> addPoint(n, x, y + h);
        };
    }

    private static int addChamfer(int n, double x0, double y0, double x1, double y1) {
        n = addPoint(n, x0, y0);
        return addPoint(n, x1, y1);
    }

    private static int appendArc(int n, double cx, double cy, double rx, double ry, double startDeg, double endDeg) {
        if (rx <= 0.0001 || ry <= 0.0001) {
            return addPoint(n, cx, cy);
        }
        int segments = Math.max(3, Math.min(12, (int) Math.ceil(Math.max(rx, ry) / 4.0)));
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double angle = Math.toRadians(startDeg + (endDeg - startDeg) * t);
            n = addPoint(n, cx + Math.cos(angle) * rx, cy + Math.sin(angle) * ry);
        }
        return n;
    }

    private static int addPoint(int n, double x, double y) {
        if (n <= 0 || POINTS[(n - 1) * 2] != x || POINTS[(n - 1) * 2 + 1] != y) {
            if ((n + 1) * 2 > POINTS.length) return n;
            POINTS[n * 2] = x;
            POINTS[n * 2 + 1] = y;
            return n + 1;
        }
        return n;
    }

    private static void resetPoints() {
        // The active count is returned by builders; old values are ignored.
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Scope implements AutoCloseable {
        private boolean active;

        private Scope(boolean active) {
            this.active = active;
        }

        public boolean active() {
            return active;
        }

        @Override
        public void close() {
            if (!active) return;
            active = false;
            ClipFunction.pop();
        }
    }
}
