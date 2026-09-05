/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import combatant.client.render.engine.uniform.MeshBuilder;

/**
 * CPU path compiler for the dedicated UI path pipeline.
 *
 * <p>The compiler keeps the semantic curve continuous: cubic Beziers and Catmull-Rom splines are
 * adaptively flattened in screen/logical space, then one stroke mesh is produced with explicit
 * joins/caps. The path shader only resolves the narrow edge fringe, so ordinary path interiors do
 * not depend on polygon MSAA.</p>
 */
public final class UiPathRenderer {
    private static final int MAX_POINTS = 2048;
    private static final int MAX_SEGMENTS = MAX_POINTS;
    private static final int MAX_SUBDIVISION_DEPTH = 10;
    private static final double FLATNESS_PX = 0.24;
    private static final double EPSILON = 1.0e-5;
    private static final double MITER_LIMIT = 4.0;
    private static final double EDGE_FRINGE = 0.85;

    private final double[] resolved = new double[MAX_POINTS * 2];
    private int resolvedCount;

    private final double[] dirX = new double[MAX_SEGMENTS];
    private final double[] dirY = new double[MAX_SEGMENTS];
    private final double[] normX = new double[MAX_SEGMENTS];
    private final double[] normY = new double[MAX_SEGMENTS];
    private final double[] cumulative = new double[MAX_POINTS + 1];
    private final int[] areaTop = new int[MAX_POINTS];
    private final int[] areaBottom = new int[MAX_POINTS];
    private final int[] areaFringe = new int[MAX_POINTS];

    private final double[] startLeftX = new double[MAX_SEGMENTS];
    private final double[] startLeftY = new double[MAX_SEGMENTS];
    private final double[] startRightX = new double[MAX_SEGMENTS];
    private final double[] startRightY = new double[MAX_SEGMENTS];
    private final double[] endLeftX = new double[MAX_SEGMENTS];
    private final double[] endLeftY = new double[MAX_SEGMENTS];
    private final double[] endRightX = new double[MAX_SEGMENTS];
    private final double[] endRightY = new double[MAX_SEGMENTS];

    private long cachedSplineHash = Long.MIN_VALUE;
    private int cachedSplineInputCount = -1;
    private boolean cachedSplineClosed;
    private int cachedSplineResolvedCount;

    public double[] resolvedPoints() {
        return resolved;
    }

    public int resolvedCount() {
        return resolvedCount;
    }

    public int resolvePolyline(double[] points, int pointCount, boolean closed) {
        resolvedCount = cleanCopy(points, pointCount, closed);
        return resolvedCount;
    }

    public int resolveBezier(double x0, double y0,
                             double x1, double y1,
                             double x2, double y2,
                             double x3, double y3) {
        resolvedCount = 0;
        appendResolved(x0, y0);
        flattenCubic(x0, y0, x1, y1, x2, y2, x3, y3, 0, false,
                Math.min(y0, y3), Math.max(y0, y3), false);
        return resolvedCount;
    }

    /**
     * Resolves a real Catmull-Rom spline. The last result is cached by input content so area/glow/
     * main-stroke submissions of the same graph reuse exactly one flattened curve geometry.
     */
    public int resolveSpline(double[] points, int pointCount, boolean closed) {
        int safeCount = Math.min(Math.max(pointCount, 0), points != null ? points.length / 2 : 0);
        if (safeCount < 2) {
            resolvedCount = 0;
            return 0;
        }

        long hash = splineHash(points, safeCount, closed);
        if (hash == cachedSplineHash && safeCount == cachedSplineInputCount && closed == cachedSplineClosed) {
            resolvedCount = cachedSplineResolvedCount;
            return resolvedCount;
        }

        if (safeCount == 2 && !closed) {
            resolvedCount = cleanCopy(points, safeCount, false);
            rememberSpline(hash, safeCount, closed);
            return resolvedCount;
        }

        resolvedCount = 0;
        appendResolved(points[0], points[1]);
        int segmentCount = closed ? safeCount : safeCount - 1;
        for (int segment = 0; segment < segmentCount && resolvedCount < MAX_POINTS; segment++) {
            int i1 = segment;
            int i2 = (segment + 1) % safeCount;
            int i0 = closed ? wrap(segment - 1, safeCount) : Math.max(0, segment - 1);
            int i3 = closed ? wrap(segment + 2, safeCount) : Math.min(safeCount - 1, segment + 2);

            double p0x = points[i0 * 2];
            double p0y = points[i0 * 2 + 1];
            double p1x = points[i1 * 2];
            double p1y = points[i1 * 2 + 1];
            double p2x = points[i2 * 2];
            double p2y = points[i2 * 2 + 1];
            double p3x = points[i3 * 2];
            double p3y = points[i3 * 2 + 1];

            // Uniform Catmull-Rom converted to cubic Bezier. Clamp the adaptive samples between
            // adjacent control values on x-monotonic graphs to prevent spline overshoot/crawling.
            double c1x = p1x + (p2x - p0x) / 6.0;
            double c1y = p1y + (p2y - p0y) / 6.0;
            double c2x = p2x - (p3x - p1x) / 6.0;
            double c2y = p2y - (p3y - p1y) / 6.0;
            boolean xMonotonic = !closed && p2x >= p1x;
            if (xMonotonic) {
                // Streaming graphs/waveforms keep a stable topology while Y animates: subdivision
                // count derives from the (normally stable) horizontal span, not instantaneous
                // curvature. This prevents pixel crawling caused by adaptive topology flapping.
                appendStableMonotonicCubic(p1x, p1y, c1x, c1y, c2x, c2y, p2x, p2y,
                        Math.min(p1y, p2y), Math.max(p1y, p2y));
            } else {
                flattenCubic(p1x, p1y, c1x, c1y, c2x, c2y, p2x, p2y, 0,
                        false, Math.min(p1y, p2y), Math.max(p1y, p2y), true);
            }
        }

        if (closed && resolvedCount > 1) {
            int last = (resolvedCount - 1) * 2;
            if (distanceSq(resolved[last], resolved[last + 1], resolved[0], resolved[1]) <= EPSILON * EPSILON) {
                resolvedCount--;
            }
        }
        rememberSpline(hash, safeCount, closed);
        return resolvedCount;
    }

    public void appendStroke(MeshBuilder mesh,
                             int pointCount,
                             boolean closed,
                             double thickness,
                             UiPathCap cap,
                             UiPathJoin join,
                             int startArgb,
                             int endArgb) {
        if (mesh == null || pointCount < 2 || thickness <= 0.0) return;
        int count = Math.min(pointCount, resolvedCount);
        if (count < 2) return;

        int segments = closed ? count : count - 1;
        if (segments <= 0 || segments > MAX_SEGMENTS) return;
        double half = Math.max(0.0001, thickness * 0.5);
        UiPathCap safeCap = cap != null ? cap : UiPathCap.BUTT;
        UiPathJoin safeJoin = join != null ? join : UiPathJoin.MITER;

        if (!prepareSegments(count, closed, half, safeCap, safeJoin)) return;
        computeCumulative(count, closed);
        double totalLength = Math.max(EPSILON, cumulative[closed ? count : count - 1]);

        // Conservative reserve. Adaptive round joins/caps may add more vertices, but MeshBuilder
        // grows as needed and this reserve keeps the common spline/polyline path allocation-free.
        mesh.ensureCapacity(segments * 24 + 96, segments * 36 + 192);

        for (int segment = 0; segment < segments; segment++) {
            int nextPoint = (segment + 1) % count;
            double u0 = cumulative[segment] / totalLength;
            double u1 = (segment == segments - 1 && closed) ? 1.0 : cumulative[nextPoint] / totalLength;
            int c0 = mixArgb(startArgb, endArgb, u0);
            int c1 = mixArgb(startArgb, endArgb, u1);

            appendCoreQuad(mesh,
                    startLeftX[segment], startLeftY[segment], startRightX[segment], startRightY[segment],
                    endLeftX[segment], endLeftY[segment], endRightX[segment], endRightY[segment], c0, c1);
            appendSideFringe(mesh, segment, true, c0, c1);
            appendSideFringe(mesh, segment, false, c0, c1);
        }

        if (closed) {
            for (int point = 0; point < count; point++) {
                int prev = wrap(point - 1, segments);
                int next = point % segments;
                appendJoin(mesh, point, prev, next, safeJoin, half,
                        mixArgb(startArgb, endArgb, cumulative[point] / totalLength));
            }
        } else {
            for (int point = 1; point < count - 1; point++) {
                appendJoin(mesh, point, point - 1, point, safeJoin, half,
                        mixArgb(startArgb, endArgb, cumulative[point] / totalLength));
            }
            appendCap(mesh, 0, true, safeCap, half, startArgb);
            appendCap(mesh, count - 1, false, safeCap, half, endArgb);
        }
    }

    public void appendStrokeLinearGradient(MeshBuilder mesh,
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
        if (mesh == null || pointCount < 2 || thickness <= 0.0) return;
        int count = Math.min(pointCount, resolvedCount);
        if (count < 2) return;
        int segments = closed ? count : count - 1;
        double half = Math.max(0.0001, thickness * 0.5);
        UiPathCap safeCap = cap != null ? cap : UiPathCap.BUTT;
        UiPathJoin safeJoin = join != null ? join : UiPathJoin.MITER;
        if (!prepareSegments(count, closed, half, safeCap, safeJoin)) return;
        mesh.ensureCapacity(segments * 24 + 96, segments * 36 + 192);

        for (int segment = 0; segment < segments; segment++) {
            int p0 = segment;
            int p1 = (segment + 1) % count;
            int c0 = linearGradientColorAt(resolved[p0 * 2] - originX, resolved[p0 * 2 + 1] - originY,
                    width, height, startArgb, endArgb, angleDeg, offsetPx);
            int c1 = linearGradientColorAt(resolved[p1 * 2] - originX, resolved[p1 * 2 + 1] - originY,
                    width, height, startArgb, endArgb, angleDeg, offsetPx);
            appendCoreQuad(mesh,
                    startLeftX[segment], startLeftY[segment], startRightX[segment], startRightY[segment],
                    endLeftX[segment], endLeftY[segment], endRightX[segment], endRightY[segment], c0, c1);
            appendSideFringe(mesh, segment, true, c0, c1);
            appendSideFringe(mesh, segment, false, c0, c1);
        }
        if (closed) {
            for (int point = 0; point < count; point++) {
                int c = linearGradientColorAt(resolved[point * 2] - originX, resolved[point * 2 + 1] - originY,
                        width, height, startArgb, endArgb, angleDeg, offsetPx);
                appendJoin(mesh, point, wrap(point - 1, segments), point % segments, safeJoin, half, c);
            }
        } else {
            for (int point = 1; point < count - 1; point++) {
                int c = linearGradientColorAt(resolved[point * 2] - originX, resolved[point * 2 + 1] - originY,
                        width, height, startArgb, endArgb, angleDeg, offsetPx);
                appendJoin(mesh, point, point - 1, point, safeJoin, half, c);
            }
            int c0 = linearGradientColorAt(resolved[0] - originX, resolved[1] - originY,
                    width, height, startArgb, endArgb, angleDeg, offsetPx);
            int last = count - 1;
            int c1 = linearGradientColorAt(resolved[last * 2] - originX, resolved[last * 2 + 1] - originY,
                    width, height, startArgb, endArgb, angleDeg, offsetPx);
            appendCap(mesh, 0, true, safeCap, half, c0);
            appendCap(mesh, last, false, safeCap, half, c1);
        }
    }

    /** Continuous x-monotonic area strip using the exact cached spline top boundary. */
    public void appendAreaToBaseline(MeshBuilder mesh,
                                     int pointCount,
                                     double baseline,
                                     int topStart,
                                     int topEnd,
                                     int bottomStart,
                                     int bottomEnd) {
        if (mesh == null || pointCount < 2) return;
        int count = Math.min(pointCount, resolvedCount);
        if (count < 2) return;

        // A graph that lies entirely on its baseline has zero fill area. Emitting a top strip and
        // AA fringe for it only creates degenerate triangles; keep the stroke path independent.
        boolean hasArea = false;
        for (int i = 0; i < count; i++) {
            double y = resolved[i * 2 + 1];
            if (Double.isFinite(y) && y < baseline - EPSILON) {
                hasArea = true;
                break;
            }
        }
        if (!hasArea) return;

        computeCumulative(count, false);
        double total = Math.max(EPSILON, cumulative[count - 1]);
        mesh.ensureCapacity(count * 3, Math.max(0, count - 1) * 12);

        for (int i = 0; i < count; i++) {
            double x = resolved[i * 2];
            double y = Math.min(baseline, resolved[i * 2 + 1]);
            double u = cumulative[i] / total;
            int topColor = mixArgb(topStart, topEnd, u);
            int bottomColor = mixArgb(bottomStart, bottomEnd, u);
            int prev = Math.max(0, i - 1);
            int next = Math.min(count - 1, i + 1);
            double tx = resolved[next * 2] - resolved[prev * 2];
            double ty = resolved[next * 2 + 1] - resolved[prev * 2 + 1];
            double tangentLength = Math.max(EPSILON, Math.hypot(tx, ty));
            // Screen Y grows downward; (ty, -tx) is the outward/upward normal for the
            // x-monotonic graph top boundary. This keeps the AA fringe width stable on slopes.
            double outwardX = ty / tangentLength;
            double outwardY = -tx / tangentLength;
            areaTop[i] = appendPathVertex(mesh, x, y, topColor, 0.0, EDGE_FRINGE, u);
            areaBottom[i] = appendPathVertex(mesh, x, baseline, bottomColor, -EDGE_FRINGE, EDGE_FRINGE, u);
            areaFringe[i] = appendPathVertex(mesh, x + outwardX * EDGE_FRINGE, y + outwardY * EDGE_FRINGE,
                    topColor, EDGE_FRINGE, EDGE_FRINGE, u);
        }
        for (int i = 0; i < count - 1; i++) {
            // Main area strip and a narrow top AA fringe. The exact same top vertices are later used
            // by the glow/main stroke because resolveSpline() returns the cached flattened curve.
            mesh.quad(areaTop[i], areaBottom[i], areaBottom[i + 1], areaTop[i + 1]);
            mesh.quad(areaFringe[i], areaTop[i], areaTop[i + 1], areaFringe[i + 1]);
        }
    }

    private boolean prepareSegments(int count, boolean closed, double half, UiPathCap cap, UiPathJoin join) {
        int segments = closed ? count : count - 1;
        for (int segment = 0; segment < segments; segment++) {
            int p0 = segment;
            int p1 = (segment + 1) % count;
            double x0 = resolved[p0 * 2];
            double y0 = resolved[p0 * 2 + 1];
            double x1 = resolved[p1 * 2];
            double y1 = resolved[p1 * 2 + 1];
            double dx = x1 - x0;
            double dy = y1 - y0;
            double length = Math.hypot(dx, dy);
            if (length <= EPSILON) return false;
            dx /= length;
            dy /= length;
            dirX[segment] = dx;
            dirY[segment] = dy;
            normX[segment] = -dy;
            normY[segment] = dx;

            double startShift = (!closed && segment == 0 && cap == UiPathCap.SQUARE) ? -half : 0.0;
            double endShift = (!closed && segment == segments - 1 && cap == UiPathCap.SQUARE) ? half : 0.0;
            double sx = x0 + dx * startShift;
            double sy = y0 + dy * startShift;
            double ex = x1 + dx * endShift;
            double ey = y1 + dy * endShift;
            startLeftX[segment] = sx + normX[segment] * half;
            startLeftY[segment] = sy + normY[segment] * half;
            startRightX[segment] = sx - normX[segment] * half;
            startRightY[segment] = sy - normY[segment] * half;
            endLeftX[segment] = ex + normX[segment] * half;
            endLeftY[segment] = ey + normY[segment] * half;
            endRightX[segment] = ex - normX[segment] * half;
            endRightY[segment] = ey - normY[segment] * half;
        }

        if (closed) {
            for (int point = 0; point < count; point++) {
                adjustJoinGeometry(point, wrap(point - 1, segments), point % segments, join, half);
            }
        } else {
            for (int point = 1; point < count - 1; point++) {
                adjustJoinGeometry(point, point - 1, point, join, half);
            }
        }
        return true;
    }

    private void adjustJoinGeometry(int point, int prev, int next, UiPathJoin join, double half) {
        double px = resolved[point * 2];
        double py = resolved[point * 2 + 1];
        double cross = dirX[prev] * dirY[next] - dirY[prev] * dirX[next];
        double dot = dirX[prev] * dirX[next] + dirY[prev] * dirY[next];
        if (Math.abs(cross) <= 1.0e-4 && dot > 0.0) {
            double lx = (endLeftX[prev] + startLeftX[next]) * 0.5;
            double ly = (endLeftY[prev] + startLeftY[next]) * 0.5;
            double rx = (endRightX[prev] + startRightX[next]) * 0.5;
            double ry = (endRightY[prev] + startRightY[next]) * 0.5;
            endLeftX[prev] = startLeftX[next] = lx;
            endLeftY[prev] = startLeftY[next] = ly;
            endRightX[prev] = startRightX[next] = rx;
            endRightY[prev] = startRightY[next] = ry;
            return;
        }

        double leftAx = px + normX[prev] * half;
        double leftAy = py + normY[prev] * half;
        double leftT = lineIntersectionParameter(leftAx, leftAy, dirX[prev], dirY[prev],
                px + normX[next] * half, py + normY[next] * half, dirX[next], dirY[next]);
        boolean hasLeft = Double.isFinite(leftT);
        double leftX = hasLeft ? leftAx + dirX[prev] * leftT : 0.0;
        double leftY = hasLeft ? leftAy + dirY[prev] * leftT : 0.0;

        double rightAx = px - normX[prev] * half;
        double rightAy = py - normY[prev] * half;
        double rightT = lineIntersectionParameter(rightAx, rightAy, dirX[prev], dirY[prev],
                px - normX[next] * half, py - normY[next] * half, dirX[next], dirY[next]);
        boolean hasRight = Double.isFinite(rightT);
        double rightX = hasRight ? rightAx + dirX[prev] * rightT : 0.0;
        double rightY = hasRight ? rightAy + dirY[prev] * rightT : 0.0;

        if (join == UiPathJoin.MITER && hasLeft && hasRight
                && distance(px, py, leftX, leftY) <= half * MITER_LIMIT
                && distance(px, py, rightX, rightY) <= half * MITER_LIMIT) {
            endLeftX[prev] = startLeftX[next] = leftX;
            endLeftY[prev] = startLeftY[next] = leftY;
            endRightX[prev] = startRightX[next] = rightX;
            endRightY[prev] = startRightY[next] = rightY;
            return;
        }

        // Bevel/round join: share only the concave (inner) boundary. The convex side remains as
        // two radius endpoints and is filled exactly once by appendJoin(), avoiding alpha overlap.
        if (cross > 0.0) {
            if (hasLeft) {
                endLeftX[prev] = startLeftX[next] = leftX;
                endLeftY[prev] = startLeftY[next] = leftY;
            }
        } else if (hasRight) {
            endRightX[prev] = startRightX[next] = rightX;
            endRightY[prev] = startRightY[next] = rightY;
        }
    }

    private void appendJoin(MeshBuilder mesh, int point, int prev, int next, UiPathJoin join, double half, int argb) {
        double cross = dirX[prev] * dirY[next] - dirY[prev] * dirX[next];
        double dot = dirX[prev] * dirX[next] + dirY[prev] * dirY[next];
        if (Math.abs(cross) <= 1.0e-4 && dot > 0.0) return;

        double px = resolved[point * 2];
        double py = resolved[point * 2 + 1];
        boolean leftInner = cross > 0.0;
        double innerX = leftInner ? endLeftX[prev] : endRightX[prev];
        double innerY = leftInner ? endLeftY[prev] : endRightY[prev];
        double outerPrevX = leftInner ? endRightX[prev] : endLeftX[prev];
        double outerPrevY = leftInner ? endRightY[prev] : endLeftY[prev];
        double outerNextX = leftInner ? startRightX[next] : startLeftX[next];
        double outerNextY = leftInner ? startRightY[next] : startLeftY[next];

        if (join == UiPathJoin.MITER) {
            // prepareSegments() already produced a miter when it is inside the limit. If it fell
            // back to bevel geometry, fill the remaining outer wedge as bevel.
            if (distanceSq(outerPrevX, outerPrevY, outerNextX, outerNextY) <= EPSILON * EPSILON) return;
        }

        if (join == UiPathJoin.ROUND) {
            double a0 = Math.atan2(outerPrevY - py, outerPrevX - px);
            double a1 = Math.atan2(outerNextY - py, outerNextX - px);
            double sweep = shortestSweep(a0, a1);
            if (cross > 0.0 && sweep < 0.0) sweep += Math.PI * 2.0;
            if (cross < 0.0 && sweep > 0.0) sweep -= Math.PI * 2.0;
            if (Math.abs(sweep) > Math.PI) sweep = Math.copySign(Math.PI, sweep);
            int steps = arcSteps(half, Math.abs(sweep));
            int inner = appendPathVertex(mesh, innerX, innerY, argb, -EDGE_FRINGE, EDGE_FRINGE, 0.0);
            double prevX = outerPrevX;
            double prevY = outerPrevY;
            int prevVertex = appendPathVertex(mesh, prevX, prevY, argb, 0.0, EDGE_FRINGE, 0.0);
            for (int step = 1; step <= steps; step++) {
                double angle = a0 + sweep * step / steps;
                double x = px + Math.cos(angle) * half;
                double y = py + Math.sin(angle) * half;
                int vertex = appendPathVertex(mesh, x, y, argb, 0.0, EDGE_FRINGE, 0.0);
                mesh.triangle(inner, prevVertex, vertex);
                prevVertex = vertex;
            }
            appendRoundFringe(mesh, px, py, half, a0, sweep, argb);
        } else {
            int inner = appendPathVertex(mesh, innerX, innerY, argb, -EDGE_FRINGE, EDGE_FRINGE, 0.0);
            int a = appendPathVertex(mesh, outerPrevX, outerPrevY, argb, 0.0, EDGE_FRINGE, 0.0);
            int b = appendPathVertex(mesh, outerNextX, outerNextY, argb, 0.0, EDGE_FRINGE, 0.0);
            mesh.triangle(inner, a, b);
            appendBevelFringe(mesh, px, py, outerPrevX, outerPrevY, outerNextX, outerNextY, argb);
        }
    }

    private void appendCap(MeshBuilder mesh, int point, boolean start, UiPathCap cap, double half, int argb) {
        if (cap == UiPathCap.SQUARE) {
            appendButtCapFringe(mesh, point, start, half, argb, true);
            return;
        }
        if (cap == UiPathCap.BUTT) {
            appendButtCapFringe(mesh, point, start, half, argb, false);
            return;
        }

        int segment = start ? 0 : Math.max(0, point - 1);
        double px = resolved[point * 2];
        double py = resolved[point * 2 + 1];
        double directionAngle = Math.atan2(dirY[segment], dirX[segment]);
        double a0 = start ? directionAngle + Math.PI * 0.5 : directionAngle - Math.PI * 0.5;
        double sweep = Math.PI;
        int steps = arcSteps(half, Math.PI);
        int center = appendPathVertex(mesh, px, py, argb, -EDGE_FRINGE, EDGE_FRINGE, 0.0);
        int previous = -1;
        for (int step = 0; step <= steps; step++) {
            double angle = a0 + sweep * step / steps;
            double x = px + Math.cos(angle) * half;
            double y = py + Math.sin(angle) * half;
            int vertex = appendPathVertex(mesh, x, y, argb, 0.0, EDGE_FRINGE, 0.0);
            if (previous >= 0) mesh.triangle(center, previous, vertex);
            previous = vertex;
        }
        appendRoundFringe(mesh, px, py, half, a0, sweep, argb);
    }

    private void appendButtCapFringe(MeshBuilder mesh, int point, boolean start, double half, int argb, boolean square) {
        int segment = start ? 0 : Math.max(0, point - 1);
        double sign = start ? -1.0 : 1.0;
        double px = resolved[point * 2];
        double py = resolved[point * 2 + 1];
        if (square) {
            px += dirX[segment] * sign * half;
            py += dirY[segment] * sign * half;
        }
        double lx = px + normX[segment] * half;
        double ly = py + normY[segment] * half;
        double rx = px - normX[segment] * half;
        double ry = py - normY[segment] * half;
        double ox = dirX[segment] * sign * EDGE_FRINGE;
        double oy = dirY[segment] * sign * EDGE_FRINGE;
        int l = appendPathVertex(mesh, lx, ly, argb, 0.0, EDGE_FRINGE, 0.0);
        int r = appendPathVertex(mesh, rx, ry, argb, 0.0, EDGE_FRINGE, 0.0);
        int ro = appendPathVertex(mesh, rx + ox, ry + oy, argb, EDGE_FRINGE, EDGE_FRINGE, 0.0);
        int lo = appendPathVertex(mesh, lx + ox, ly + oy, argb, EDGE_FRINGE, EDGE_FRINGE, 0.0);
        mesh.quad(l, r, ro, lo);
    }

    private void appendCoreQuad(MeshBuilder mesh,
                                double slx, double sly, double srx, double sry,
                                double elx, double ely, double erx, double ery,
                                int startArgb, int endArgb) {
        int sl = appendPathVertex(mesh, slx, sly, startArgb, 0.0, EDGE_FRINGE, 0.0);
        int sr = appendPathVertex(mesh, srx, sry, startArgb, 0.0, EDGE_FRINGE, 0.0);
        int er = appendPathVertex(mesh, erx, ery, endArgb, 0.0, EDGE_FRINGE, 1.0);
        int el = appendPathVertex(mesh, elx, ely, endArgb, 0.0, EDGE_FRINGE, 1.0);
        mesh.quad(sl, sr, er, el);
    }

    private void appendSideFringe(MeshBuilder mesh, int segment, boolean left, int startArgb, int endArgb) {
        double nx = normX[segment] * (left ? 1.0 : -1.0);
        double ny = normY[segment] * (left ? 1.0 : -1.0);
        double sx = left ? startLeftX[segment] : startRightX[segment];
        double sy = left ? startLeftY[segment] : startRightY[segment];
        double ex = left ? endLeftX[segment] : endRightX[segment];
        double ey = left ? endLeftY[segment] : endRightY[segment];
        int s = appendPathVertex(mesh, sx, sy, startArgb, 0.0, EDGE_FRINGE, 0.0);
        int e = appendPathVertex(mesh, ex, ey, endArgb, 0.0, EDGE_FRINGE, 1.0);
        int eo = appendPathVertex(mesh, ex + nx * EDGE_FRINGE, ey + ny * EDGE_FRINGE,
                endArgb, EDGE_FRINGE, EDGE_FRINGE, 1.0);
        int so = appendPathVertex(mesh, sx + nx * EDGE_FRINGE, sy + ny * EDGE_FRINGE,
                startArgb, EDGE_FRINGE, EDGE_FRINGE, 0.0);
        if (left) mesh.quad(so, s, e, eo);
        else mesh.quad(s, so, eo, e);
    }

    private void appendRoundFringe(MeshBuilder mesh,
                                   double cx, double cy,
                                   double radius,
                                   double startAngle,
                                   double sweep,
                                   int argb) {
        int steps = arcSteps(radius + EDGE_FRINGE, Math.abs(sweep));
        int prevInner = -1;
        int prevOuter = -1;
        for (int step = 0; step <= steps; step++) {
            double angle = startAngle + sweep * step / steps;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int inner = appendPathVertex(mesh, cx + cos * radius, cy + sin * radius,
                    argb, 0.0, EDGE_FRINGE, 0.0);
            int outer = appendPathVertex(mesh, cx + cos * (radius + EDGE_FRINGE), cy + sin * (radius + EDGE_FRINGE),
                    argb, EDGE_FRINGE, EDGE_FRINGE, 0.0);
            if (prevInner >= 0) mesh.quad(prevInner, inner, outer, prevOuter);
            prevInner = inner;
            prevOuter = outer;
        }
    }

    private void appendBevelFringe(MeshBuilder mesh,
                                   double px, double py,
                                   double ax, double ay,
                                   double bx, double by,
                                   int argb) {
        double mx = (ax + bx) * 0.5 - px;
        double my = (ay + by) * 0.5 - py;
        double length = Math.hypot(mx, my);
        if (length <= EPSILON) return;
        mx = mx / length * EDGE_FRINGE;
        my = my / length * EDGE_FRINGE;
        int a = appendPathVertex(mesh, ax, ay, argb, 0.0, EDGE_FRINGE, 0.0);
        int b = appendPathVertex(mesh, bx, by, argb, 0.0, EDGE_FRINGE, 0.0);
        int bo = appendPathVertex(mesh, bx + mx, by + my, argb, EDGE_FRINGE, EDGE_FRINGE, 0.0);
        int ao = appendPathVertex(mesh, ax + mx, ay + my, argb, EDGE_FRINGE, EDGE_FRINGE, 0.0);
        mesh.quad(a, b, bo, ao);
    }

    private void computeCumulative(int count, boolean closed) {
        cumulative[0] = 0.0;
        for (int i = 1; i < count; i++) {
            cumulative[i] = cumulative[i - 1] + distance(
                    resolved[(i - 1) * 2], resolved[(i - 1) * 2 + 1], resolved[i * 2], resolved[i * 2 + 1]);
        }
        if (closed) {
            cumulative[count] = cumulative[count - 1] + distance(
                    resolved[(count - 1) * 2], resolved[(count - 1) * 2 + 1], resolved[0], resolved[1]);
        }
    }

    private int cleanCopy(double[] points, int pointCount, boolean closed) {
        int safeCount = Math.min(Math.max(pointCount, 0), points != null ? points.length / 2 : 0);
        int out = 0;
        for (int i = 0; i < safeCount && out < MAX_POINTS; i++) {
            double x = points[i * 2];
            double y = points[i * 2 + 1];
            if (out > 0 && distanceSq(x, y, resolved[(out - 1) * 2], resolved[(out - 1) * 2 + 1]) <= EPSILON * EPSILON) {
                continue;
            }
            resolved[out * 2] = x;
            resolved[out * 2 + 1] = y;
            out++;
        }
        if (closed && out > 2 && distanceSq(resolved[0], resolved[1], resolved[(out - 1) * 2], resolved[(out - 1) * 2 + 1]) <= EPSILON * EPSILON) {
            out--;
        }
        return out;
    }

    private void appendStableMonotonicCubic(double x0, double y0,
                                             double x1, double y1,
                                             double x2, double y2,
                                             double x3, double y3,
                                             double minY, double maxY) {
        // Quantized screen-space density keeps topology deterministic for a stable graph X-grid.
        // Curvature still affects the curve itself; it no longer changes the number of triangles
        // every time an animated sample moves by a fraction of a pixel.
        double span = Math.max(0.0, x3 - x0);
        int steps = Math.max(2, Math.min(16, (int) Math.ceil(span / 4.0)));
        for (int step = 1; step <= steps && resolvedCount < MAX_POINTS; step++) {
            double t = step / (double) steps;
            double mt = 1.0 - t;
            double x = mt * mt * mt * x0
                    + 3.0 * mt * mt * t * x1
                    + 3.0 * mt * t * t * x2
                    + t * t * t * x3;
            double y = mt * mt * mt * y0
                    + 3.0 * mt * mt * t * y1
                    + 3.0 * mt * t * t * y2
                    + t * t * t * y3;
            appendResolved(clamp(x, x0, x3), clamp(y, minY, maxY));
        }
    }

    private void flattenCubic(double x0, double y0,
                              double x1, double y1,
                              double x2, double y2,
                              double x3, double y3,
                              int depth,
                              boolean clampX,
                              double minY,
                              double maxY,
                              boolean clampY) {
        if (resolvedCount >= MAX_POINTS) return;
        double flatness = Math.max(pointLineDistance(x1, y1, x0, y0, x3, y3),
                pointLineDistance(x2, y2, x0, y0, x3, y3));
        // Quantize the decision metric so tiny sub-pixel control-point motion does not constantly
        // cross a subdivision boundary. Adaptive quality remains curvature/scale driven.
        double stableFlatness = Math.rint(flatness * 8.0) * 0.125;
        if (depth >= MAX_SUBDIVISION_DEPTH || stableFlatness <= FLATNESS_PX) {
            double x = clampX ? clamp(x3, Math.min(x0, x3), Math.max(x0, x3)) : x3;
            double y = clampY ? clamp(y3, minY, maxY) : y3;
            appendResolved(x, y);
            return;
        }

        double x01 = (x0 + x1) * 0.5;
        double y01 = (y0 + y1) * 0.5;
        double x12 = (x1 + x2) * 0.5;
        double y12 = (y1 + y2) * 0.5;
        double x23 = (x2 + x3) * 0.5;
        double y23 = (y2 + y3) * 0.5;
        double x012 = (x01 + x12) * 0.5;
        double y012 = (y01 + y12) * 0.5;
        double x123 = (x12 + x23) * 0.5;
        double y123 = (y12 + y23) * 0.5;
        double x0123 = (x012 + x123) * 0.5;
        double y0123 = (y012 + y123) * 0.5;

        flattenCubic(x0, y0, x01, y01, x012, y012, x0123, y0123,
                depth + 1, clampX, minY, maxY, clampY);
        flattenCubic(x0123, y0123, x123, y123, x23, y23, x3, y3,
                depth + 1, clampX, minY, maxY, clampY);
    }

    private void appendResolved(double x, double y) {
        if (resolvedCount >= MAX_POINTS) return;
        if (resolvedCount > 0) {
            int previous = (resolvedCount - 1) * 2;
            if (distanceSq(x, y, resolved[previous], resolved[previous + 1]) <= EPSILON * EPSILON) return;
        }
        resolved[resolvedCount * 2] = x;
        resolved[resolvedCount * 2 + 1] = y;
        resolvedCount++;
    }

    private void rememberSpline(long hash, int inputCount, boolean closed) {
        cachedSplineHash = hash;
        cachedSplineInputCount = inputCount;
        cachedSplineClosed = closed;
        cachedSplineResolvedCount = resolvedCount;
    }

    private static long splineHash(double[] points, int count, boolean closed) {
        long h = 0xcbf29ce484222325L;
        h ^= count;
        h *= 0x100000001b3L;
        h ^= closed ? 1L : 0L;
        h *= 0x100000001b3L;
        for (int i = 0; i < count * 2; i++) {
            long bits = Double.doubleToLongBits(points[i]);
            h ^= bits;
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static int appendPathVertex(MeshBuilder mesh,
                                        double x, double y,
                                        int argb,
                                        double edgeDistance,
                                        double fringeWidth,
                                        double pathU) {
        return mesh.vec2(x, y)
                .color((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .vec4((float) edgeDistance, (float) fringeWidth, (float) pathU, 0f)
                .next();
    }

    private static int mixArgb(int startArgb, int endArgb, double t) {
        double k = clamp(t, 0.0, 1.0);
        int a = mix8((startArgb >>> 24) & 0xFF, (endArgb >>> 24) & 0xFF, k);
        int r = mix8((startArgb >>> 16) & 0xFF, (endArgb >>> 16) & 0xFF, k);
        int g = mix8((startArgb >>> 8) & 0xFF, (endArgb >>> 8) & 0xFF, k);
        int b = mix8(startArgb & 0xFF, endArgb & 0xFF, k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mix8(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static int linearGradientColorAt(double x, double y, double width, double height,
                                             int startArgb, int endArgb, float angleDeg, float offsetPx) {
        double angle = Math.toRadians(angleDeg);
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        double p0 = 0.0;
        double p1 = width * dx;
        double p2 = height * dy;
        double p3 = width * dx + height * dy;
        double min = Math.min(Math.min(p0, p1), Math.min(p2, p3));
        double max = Math.max(Math.max(p0, p1), Math.max(p2, p3));
        double range = Math.max(0.0001, max - min);
        double projection = x * dx + y * dy;
        return mixArgb(startArgb, endArgb, (projection + offsetPx - min) / range);
    }

    private static double lineIntersectionParameter(double ax, double ay, double adx, double ady,
                                                    double bx, double by, double bdx, double bdy) {
        double cross = adx * bdy - ady * bdx;
        if (Math.abs(cross) <= 1.0e-6) return Double.NaN;
        double qx = bx - ax;
        double qy = by - ay;
        return (qx * bdy - qy * bdx) / cross;
    }

    private static int arcSteps(double radius, double sweep) {
        double arcLength = Math.max(0.0, radius * sweep);
        return Math.max(2, Math.min(24, (int) Math.ceil(arcLength / 1.4)));
    }

    private static double shortestSweep(double a0, double a1) {
        double sweep = a1 - a0;
        while (sweep <= -Math.PI) sweep += Math.PI * 2.0;
        while (sweep > Math.PI) sweep -= Math.PI * 2.0;
        return sweep;
    }

    private static double pointLineDistance(double px, double py,
                                            double ax, double ay,
                                            double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq <= EPSILON * EPSILON) return Math.hypot(px - ax, py - ay);
        double cross = Math.abs((px - ax) * dy - (py - ay) * dx);
        return cross / Math.sqrt(lengthSq);
    }

    private static int wrap(int value, int size) {
        int result = value % size;
        return result < 0 ? result + size : result;
    }

    private static double distance(double x0, double y0, double x1, double y1) {
        return Math.hypot(x1 - x0, y1 - y0);
    }

    private static double distanceSq(double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        return dx * dx + dy * dy;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
