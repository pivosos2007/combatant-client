/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import java.util.Arrays;

public final class UiShape {
    private final UiShapeKind kind;
    private final UiRect bounds;
    private final UiCornerMode cornerMode;
    private final UiCornerRadii roundedRadii;
    private final UiChamferRadii chamferRadii;
    private final float radius;
    private final float startAngleDeg;
    private final float endAngleDeg;
    private final double[] points;
    private final int pointCount;
    private final boolean closed;
    private final UiBoxShape box;

    private UiShape(UiShapeKind kind,
                    UiRect bounds,
                    UiCornerMode cornerMode,
                    UiCornerRadii roundedRadii,
                    UiChamferRadii chamferRadii,
                    float radius,
                    float startAngleDeg,
                    float endAngleDeg,
                    double[] points,
                    int pointCount,
                    boolean closed,
                    UiBoxShape box) {
        this.kind = kind;
        this.bounds = bounds;
        this.cornerMode = cornerMode;
        this.roundedRadii = roundedRadii;
        this.chamferRadii = chamferRadii;
        this.radius = radius;
        this.startAngleDeg = startAngleDeg;
        this.endAngleDeg = endAngleDeg;
        this.points = points;
        this.pointCount = pointCount;
        this.closed = closed;
        this.box = box;
    }

    public static UiShape rect(double x, double y, double width, double height) {
        return new UiShape(UiShapeKind.RECT, UiRect.of(x, y, width, height), UiCornerMode.NONE,
                UiCornerRadii.ZERO, UiChamferRadii.ZERO, 0f, 0f, 0f, null, 0, true, null);
    }

    public static UiShape box(UiBoxShape box) {
        UiBoxShape safe = box != null ? box : UiBoxShape.square(0, 0, 0, 0);
        return new UiShape(UiShapeKind.FLEXIBLE_BOX, safe.bounds(), UiCornerMode.CUSTOM_POLYGON,
                UiCornerRadii.ZERO, UiChamferRadii.ZERO, 0f, 0f, 0f, null, 0, true, safe);
    }

    public static UiShape roundedRect(double x, double y, double width, double height,
                                      double tl, double tr, double br, double bl) {
        return new UiShape(UiShapeKind.RECT, UiRect.of(x, y, width, height), UiCornerMode.ROUNDED,
                UiCornerRadii.of(tl, tr, br, bl), UiChamferRadii.ZERO, 0f, 0f, 0f, null, 0, true, null);
    }

    public static UiShape roundedRect(double x, double y, double width, double height, double radius) {
        return roundedRect(x, y, width, height, radius, radius, radius, radius);
    }

    public static UiShape chamferedRect(double x, double y, double width, double height,
                                        double tl, double tr, double br, double bl) {
        return new UiShape(UiShapeKind.RECT, UiRect.of(x, y, width, height), UiCornerMode.CHAMFERED,
                UiCornerRadii.ZERO, UiChamferRadii.of(tl, tr, br, bl), 0f, 0f, 0f, null, 0, true, null);
    }

    public static UiShape chamferedRectAxes(double x, double y, double width, double height,
                                            double tlX, double tlY, double trX, double trY,
                                            double brX, double brY, double blX, double blY) {
        return new UiShape(UiShapeKind.RECT, UiRect.of(x, y, width, height), UiCornerMode.CHAMFERED,
                UiCornerRadii.ZERO, UiChamferRadii.axes(tlX, tlY, trX, trY, brX, brY, blX, blY), 0f, 0f, 0f, null, 0, true, null);
    }

    public static UiShape circle(double cx, double cy, double radius) {
        double r = Math.max(0.0, radius);
        return new UiShape(UiShapeKind.CIRCLE, UiRect.of(cx - r, cy - r, r * 2.0, r * 2.0),
                UiCornerMode.NONE, UiCornerRadii.ZERO, UiChamferRadii.ZERO,
                (float) r, 0f, 360f, null, 0, true, null);
    }

    public static UiShape arc(double cx, double cy, double radius,
                              double startAngleDeg, double endAngleDeg) {
        double r = Math.max(0.0, radius);
        return new UiShape(UiShapeKind.ARC, UiRect.of(cx - r, cy - r, r * 2.0, r * 2.0),
                UiCornerMode.NONE, UiCornerRadii.ZERO, UiChamferRadii.ZERO,
                (float) r, (float) startAngleDeg, (float) endAngleDeg, null, 0, false, null);
    }

    public static UiShape polyline(double[] points, int pointCount, boolean closed) {
        int safeCount = Math.max(0, Math.min(pointCount, points != null ? points.length / 2 : 0));
        double[] copy = safeCount == 0 ? new double[0] : Arrays.copyOf(points, safeCount * 2);
        return new UiShape(closed ? UiShapeKind.POLYGON : UiShapeKind.POLYLINE, computeBounds(copy, safeCount),
                UiCornerMode.CUSTOM_POLYGON, UiCornerRadii.ZERO, UiChamferRadii.ZERO,
                0f, 0f, 0f, copy, safeCount, closed, null);
    }

    public static UiShape bezier(double[] points, int pointCount) {
        int safeCount = Math.max(0, Math.min(pointCount, points != null ? points.length / 2 : 0));
        double[] copy = safeCount == 0 ? new double[0] : Arrays.copyOf(points, safeCount * 2);
        return new UiShape(UiShapeKind.BEZIER, computeBounds(copy, safeCount), UiCornerMode.CUSTOM_POLYGON,
                UiCornerRadii.ZERO, UiChamferRadii.ZERO, 0f, 0f, 0f, copy, safeCount, false, null);
    }

    private static UiRect computeBounds(double[] points, int pointCount) {
        if (pointCount <= 0) return new UiRect(0f, 0f, 0f, 0f);
        double minX = points[0], maxX = points[0], minY = points[1], maxY = points[1];
        for (int i = 1; i < pointCount; i++) {
            double x = points[i * 2];
            double y = points[i * 2 + 1];
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        return UiRect.of(minX, minY, maxX - minX, maxY - minY);
    }

    public UiShapeKind kind() {
        return kind;
    }

    public UiRect bounds() {
        return bounds;
    }

    public UiCornerMode cornerMode() {
        return cornerMode;
    }

    public UiCornerRadii roundedRadii() {
        return roundedRadii;
    }

    public UiChamferRadii chamferRadii() {
        return chamferRadii;
    }

    public float radius() {
        return radius;
    }

    public float startAngleDeg() {
        return startAngleDeg;
    }

    public float endAngleDeg() {
        return endAngleDeg;
    }

    public double[] points() {
        return points == null ? null : Arrays.copyOf(points, pointCount * 2);
    }

    public int pointCount() {
        return pointCount;
    }

    public boolean closed() {
        return closed;
    }

    public UiBoxShape box() {
        return box;
    }
}
