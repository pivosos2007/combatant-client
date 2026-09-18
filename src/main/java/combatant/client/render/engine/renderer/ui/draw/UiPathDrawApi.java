/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import combatant.client.render.engine.renderer.ui.DrawBatch;
import combatant.client.render.engine.renderer.ui.OrderedUiBatcher;
import combatant.client.render.engine.renderer.ui.UiBatchType;
import combatant.client.render.engine.renderer.ui.UiRenderDispatcher;
import combatant.client.render.engine.uniform.MeshBuilder;

/**
 * Typed lowering backend for UI paths.
 *
 * <p>All layers belonging to one logical path share one flattened geometry. This is important for
 * streaming charts: an area fill, glow and primary stroke stay pixel-identical and do not pay for
 * three independent spline resolves.</p>
 */
public final class UiPathDrawApi {
    private final UiPathRenderer renderer = new UiPathRenderer();

    public void draw(OrderedUiBatcher batcher,
                     double alpha,
                     double[] points,
                     int pointCount,
                     UiPathCurve curve,
                     boolean closed,
                     UiPathAreaFill area,
                     UiPathStrokeLayer glow,
                     UiPathStrokeLayer stroke) {
        if (batcher == null || points == null || pointCount < 2) return;
        UiPathCurve safeCurve = curve != null ? curve : UiPathCurve.LINEAR;
        int count = switch (safeCurve) {
            case LINEAR -> renderer.resolvePolyline(points, pointCount, closed);
            case SPLINE -> renderer.resolveSpline(points, pointCount, closed);
        };
        appendResolved(batcher, alpha, count, closed, area, glow, stroke);
    }

    public void drawBezier(OrderedUiBatcher batcher,
                           double alpha,
                           double x0, double y0,
                           double x1, double y1,
                           double x2, double y2,
                           double x3, double y3,
                           UiPathStrokeLayer stroke) {
        if (batcher == null || stroke == null || !stroke.enabled()) return;
        int count = renderer.resolveBezier(x0, y0, x1, y1, x2, y2, x3, y3);
        appendResolved(batcher, alpha, count, false, null, null, stroke);
    }

    public void drawLinearGradientStroke(OrderedUiBatcher batcher,
                                         double alpha,
                                         double[] points,
                                         int pointCount,
                                         boolean closed,
                                         double thickness,
                                         UiPathCap cap,
                                         UiPathJoin join,
                                         double originX,
                                         double originY,
                                         double width,
                                         double height,
                                         int startArgb,
                                         int endArgb,
                                         float angleDeg,
                                         float offsetPx) {
        if (batcher == null || points == null || pointCount < 2 || thickness <= 0.0) return;
        int count = renderer.resolvePolyline(points, pointCount, closed);
        if (count < 2) return;
        boolean auto = UiRenderDispatcher.beginAutoBatch();
        try {
            DrawBatch batch = batcher.getOrCreate(UiBatchType.PATH, null, null);
            if (batch == null) return;
            MeshBuilder mesh = batch.mesh;
            mesh.alpha = alpha;
            renderer.appendStrokeLinearGradient(mesh, count, closed, thickness,
                    cap != null ? cap : UiPathCap.BUTT,
                    join != null ? join : UiPathJoin.MITER,
                    originX, originY, width, height, startArgb, endArgb, angleDeg, offsetPx);
        } finally {
            UiRenderDispatcher.endAutoBatch(auto);
        }
    }

    private void appendResolved(OrderedUiBatcher batcher,
                                double alpha,
                                int pointCount,
                                boolean closed,
                                UiPathAreaFill area,
                                UiPathStrokeLayer glow,
                                UiPathStrokeLayer stroke) {
        if (pointCount < 2) return;
        boolean hasArea = area != null && area.enabled() && !closed;
        boolean hasGlow = glow != null && glow.enabled();
        boolean hasStroke = stroke != null && stroke.enabled();
        if (!hasArea && !hasGlow && !hasStroke) return;

        boolean auto = UiRenderDispatcher.beginAutoBatch();
        try {
            DrawBatch batch = batcher.getOrCreate(UiBatchType.PATH, null, null);
            if (batch == null) return;
            MeshBuilder mesh = batch.mesh;
            mesh.alpha = alpha;

            if (hasArea) {
                renderer.appendAreaToBaseline(mesh, pointCount, area.baseline(),
                        area.topStartArgb(), area.topEndArgb(), area.bottomStartArgb(), area.bottomEndArgb());
            }
            if (hasGlow) {
                renderer.appendStroke(mesh, pointCount, closed, glow.width(), glow.cap(), glow.join(),
                        glow.startArgb(), glow.endArgb());
            }
            if (hasStroke) {
                renderer.appendStroke(mesh, pointCount, closed, stroke.width(), stroke.cap(), stroke.join(),
                        stroke.startArgb(), stroke.endArgb());
            }
        } finally {
            UiRenderDispatcher.endAutoBatch(auto);
        }
    }
}
