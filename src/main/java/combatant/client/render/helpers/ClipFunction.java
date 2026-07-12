/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.*;
import combatant.client.render.engine.renderer.ui.draw.*;
import combatant.client.render.engine.rhi.clip.ShapeClipBackend;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Higher-level clipping stack.
 *
 * <p>Rectangles are routed to ScissorFunction. Non-rect primitives use the active RHI
 * shape-clip backend. The GL backend currently implements this with stencil.</p>
 */
public enum ClipFunction {
    ;
    private static final int MAX_SHAPE_DEPTH = 250;
    private static final int MASK_COLOR = 0xFFFFFFFF;
    private static final Deque<Layer> STACK = new ArrayDeque<>();
    private static final double[] POINTS = new double[512];
    private static boolean warnedShapeUnsupported;
    private static boolean warnedShapeDepth;

    public static boolean pushRaw(float x, float y, float width, float height) {
        return pushRect(x, y, width, height);
    }

    public static boolean pushRect(double x, double y, double width, double height) {
        boolean pushed = ScissorFunction.pushRaw((float) x, (float) y, (float) width, (float) height);
        if (pushed) {
            STACK.push(Layer.rect());
        }
        return pushed;
    }

    public static boolean pushRoundedRect(double x, double y, double width, double height, double radius) {
        return push(UiShape.roundedRect(x, y, width, height, radius));
    }

    public static boolean pushRoundedRect(double x, double y, double width, double height,
                                          double topLeft, double topRight, double bottomRight, double bottomLeft) {
        return push(UiShape.roundedRect(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft));
    }

    public static boolean pushChamferedRect(double x, double y, double width, double height, double chamfer) {
        return push(UiShape.chamferedRect(x, y, width, height, chamfer, chamfer, chamfer, chamfer));
    }

    public static boolean pushCircle(double cx, double cy, double radius) {
        return push(UiShape.circle(cx, cy, radius));
    }

    public static boolean push(UiShape shape) {
        if (shape == null) return false;
        UiRect bounds = shape.bounds();
        if (bounds == null || bounds.empty()) return false;

        if (isPlainRect(shape)) {
            return pushRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        }

        if (activeShapeDepth() >= MAX_SHAPE_DEPTH) {
            warnShapeDepth();
            return false;
        }

        Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
        // A clipped liquid-glass consumer must refresh the shared blur before this
        // shape installs its bounds scissor/stencil. The refresh remains on the normal
        // shared Kawase chain and therefore never needs a clip-state bypass.
        Renderer2D.prepareLiquidGlassBlurBeforeShapeClipIfRequested();
        ShapeClipBackend clip = clipBackend();
        if (!clip.supported()) {
            warnShapeUnsupported(shape, clip, "backend unsupported");
            return false;
        }

        boolean scissor = ScissorFunction.pushRaw(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        if (!scissor) return false;

        int parent = currentShapeReference();
        int reference = parent + 1;
        boolean clear = activeShapeDepth() == 0;
        String attachmentReason = "ClipFunction.push kind=" + shape.kind() + " bounds=" + bounds;

        if (Renderer2D.isDeferredExtractRecording()) {
            Renderer2D.deferRenderThreadAction(() -> {
                ShapeClipBackend renderClip = clipBackend();
                renderClip.requireRenderPassAttachment(attachmentReason);
                if (clear) {
                    renderClip.requestClear("first shape layer");
                }
                renderClip.beginWrite(parent, reference);
                renderMaskShape(shape);
                renderClip.beginTest(reference);
                renderStencilDebugProbe(shape, reference, false);
            });
        } else {
            clip.requireRenderPassAttachment(attachmentReason);
            if (clear) {
                clip.requestClear("first shape layer");
            }
            clip.beginWrite(parent, reference);
            renderMaskShape(shape);
            clip.beginTest(reference);
            renderStencilDebugProbe(shape, reference, false);
        }

        STACK.push(Layer.shape(shape, reference, parent));
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
        if (STACK.isEmpty()) return;
        Layer layer = STACK.pop();
        if (!layer.shape) {
            ScissorFunction.pop();
            applyPreviousShapeTest();
            return;
        }

        Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
        int previousReference = currentShapeReference();
        if (Renderer2D.isDeferredExtractRecording()) {
            Renderer2D.deferRenderThreadAction(() -> {
                renderStencilDebugProbe(layer.shapeValue, layer.reference, true);
                Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
                ShapeClipBackend renderClip = clipBackend();
                renderClip.beginRestore(layer.reference, layer.parentReference);
                renderMaskShape(layer.shapeValue);
                if (previousReference > 0) {
                    renderClip.beginTest(previousReference);
                } else {
                    renderClip.disable();
                }
            });
            ScissorFunction.pop();
            return;
        }

        ShapeClipBackend clip = clipBackend();
        renderStencilDebugProbe(layer.shapeValue, layer.reference, true);
        Renderer2D.flushBatch(Renderer2D.FlushReason.SCISSOR);
        clip.beginRestore(layer.reference, layer.parentReference);
        renderMaskShape(layer.shapeValue);
        ScissorFunction.pop();
        applyPreviousShapeTest();
    }

    public static int depth() {
        return STACK.size();
    }

    public static boolean isShapeClipActive() {
        return currentShapeReference() > 0;
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
                reason + "|" + shape.kind() + "|" + bounds + "|" + activeShapeDepth() + "|" + backend,
                "ClipFunction: shape push rejected: %s. kind=%s bounds=%s depth=%d backend=%s",
                reason,
                shape.kind(),
                bounds,
                activeShapeDepth(),
                backend
        );
    }

    private static void warnShapeDepth() {
        if (warnedShapeDepth) return;
        warnedShapeDepth = true;
        DebugLog.warn("ClipFunction: shape clip stack exceeded %d layers. Clip push rejected.", MAX_SHAPE_DEPTH);
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
        if (!DebugLog.isStencilDebugEnabled()) return;
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
        for (Layer layer : STACK) {
            if (layer.shape) return layer.reference;
        }
        return 0;
    }

    private static int activeShapeDepth() {
        int depth = 0;
        for (Layer layer : STACK) {
            if (layer.shape) depth++;
        }
        return depth;
    }

    private static boolean isPlainRect(UiShape shape) {
        return shape.kind() == UiShapeKind.RECT && shape.cornerMode() == UiCornerMode.NONE;
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

    private record Layer(boolean shape, UiShape shapeValue, int reference, int parentReference) {
        static Layer rect() {
            return new Layer(false, null, 0, 0);
        }

        static Layer shape(UiShape shape, int reference, int parentReference) {
            return new Layer(true, shape, reference, parentReference);
        }
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
