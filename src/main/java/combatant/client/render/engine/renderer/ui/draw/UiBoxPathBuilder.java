/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/**
 * CPU fallback path builder for flexible boxes until the RHI UI shape lowering is fully native.
 */
public enum UiBoxPathBuilder {
    ;

    public static int write(UiBoxShape box, double[] out, int maxPoints) {
        if (box == null || out == null || maxPoints <= 0) return 0;
        UiRect r = box.bounds();
        double x = r.x();
        double y = r.y();
        double w = Math.max(0.0, r.width());
        double h = Math.max(0.0, r.height());
        if (w <= 0.0 || h <= 0.0) return 0;
        if (box.isSquircle()) {
            return writeSquircle(r, box.squircleExponent(), out, maxPoints);
        }
        double x2 = x + w;
        double y2 = y + h;
        UiCornerSpec tl = clampCorner(box.topLeft(), w, h);
        UiCornerSpec tr = clampCorner(box.topRight(), w, h);
        UiCornerSpec br = clampCorner(box.bottomRight(), w, h);
        UiCornerSpec bl = clampCorner(box.bottomLeft(), w, h);
        int[] count = {0};

        add(out, maxPoints, count, x + tl.extentX(), y);
        addHorizontalEdge(out, maxPoints, count, box.top(), x + tl.extentX(), x2 - tr.extentX(), y, true, w);
        addCorner(out, maxPoints, count, tr, x2, y, Corner.TOP_RIGHT);
        addVerticalEdge(out, maxPoints, count, box.right(), y + tr.extentY(), y2 - br.extentY(), x2, true, h);
        addCorner(out, maxPoints, count, br, x2, y2, Corner.BOTTOM_RIGHT);
        addHorizontalEdge(out, maxPoints, count, box.bottom(), x2 - br.extentX(), x + bl.extentX(), y2, false, w);
        addCorner(out, maxPoints, count, bl, x, y2, Corner.BOTTOM_LEFT);
        addVerticalEdge(out, maxPoints, count, box.left(), y2 - bl.extentY(), y + tl.extentY(), x, false, h);
        addCorner(out, maxPoints, count, tl, x, y, Corner.TOP_LEFT);
        return count[0];
    }

    /**
     * Samples the complete superellipse, not four rounded-corner arcs. Each
     * quarter is recursively subdivided until the curve-to-chord error is below
     * a logical-pixel tolerance, subject to the caller's point budget.
     */
    private static int writeSquircle(UiRect rect, double exponent, double[] out, int maxPoints) {
        double width = Math.max(0.0, rect.width());
        double height = Math.max(0.0, rect.height());
        if (width <= 0.0 || height <= 0.0 || maxPoints < 4) return 0;

        double cx = rect.x() + width * 0.5;
        double cy = rect.y() + height * 0.5;
        double a = width * 0.5;
        double b = height * 0.5;
        double power = Math.max(2.0, Math.min(16.0, exponent));
        double tolerance = Math.max(0.12, Math.min(0.35, Math.min(width, height) / 160.0));
        int[] count = {0};

        double start = -Math.PI * 0.5;
        double[] p0 = superellipsePoint(cx, cy, a, b, power, start);
        add(out, maxPoints, count, p0[0], p0[1]);
        for (int quarter = 0; quarter < 4 && count[0] < maxPoints; quarter++) {
            double t0 = start + quarter * Math.PI * 0.5;
            double t1 = t0 + Math.PI * 0.5;
            double[] from = superellipsePoint(cx, cy, a, b, power, t0);
            double[] to = superellipsePoint(cx, cy, a, b, power, t1);
            appendSquircleSegment(out, maxPoints, count, cx, cy, a, b, power,
                    t0, from[0], from[1], t1, to[0], to[1], tolerance, 0);
        }
        return count[0];
    }

    private static void appendSquircleSegment(double[] out, int maxPoints, int[] count,
                                               double cx, double cy, double a, double b, double power,
                                               double t0, double x0, double y0,
                                               double t1, double x1, double y1,
                                               double tolerance, int depth) {
        if (count[0] >= maxPoints) return;
        double tm = (t0 + t1) * 0.5;
        double[] mid = superellipsePoint(cx, cy, a, b, power, tm);
        double error = pointSegmentDistance(mid[0], mid[1], x0, y0, x1, y1);
        if (depth < 10 && error > tolerance && count[0] + 1 < maxPoints) {
            appendSquircleSegment(out, maxPoints, count, cx, cy, a, b, power,
                    t0, x0, y0, tm, mid[0], mid[1], tolerance, depth + 1);
            appendSquircleSegment(out, maxPoints, count, cx, cy, a, b, power,
                    tm, mid[0], mid[1], t1, x1, y1, tolerance, depth + 1);
            return;
        }
        add(out, maxPoints, count, x1, y1);
    }

    private static double[] superellipsePoint(double cx, double cy, double a, double b,
                                               double exponent, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double parameterPower = 2.0 / exponent;
        double x = cx + a * Math.copySign(Math.pow(Math.abs(cos), parameterPower), cos);
        double y = cy + b * Math.copySign(Math.pow(Math.abs(sin), parameterPower), sin);
        return new double[]{x, y};
    }

    private static double pointSegmentDistance(double px, double py,
                                               double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len2 = dx * dx + dy * dy;
        if (len2 <= 1.0e-12) return Math.hypot(px - x0, py - y0);
        double t = Math.max(0.0, Math.min(1.0, ((px - x0) * dx + (py - y0) * dy) / len2));
        return Math.hypot(px - (x0 + dx * t), py - (y0 + dy * t));
    }

    private static UiCornerSpec clampCorner(UiCornerSpec c, double width, double height) {
        if (c == null) return UiCornerSpec.SQUARE;
        float maxX = (float) Math.max(0.0, width * 0.5);
        float maxY = (float) Math.max(0.0, height * 0.5);
        return new UiCornerSpec(c.kind(), Math.min(c.radiusX(), maxX), Math.min(c.radiusY(), maxY),
                Math.min(c.cutX(), maxX), Math.min(c.cutY(), maxY));
    }

    private static void addCorner(double[] out, int max, int[] count, UiCornerSpec c, double x, double y, Corner corner) {
        double ex = c.extentX();
        double ey = c.extentY();
        if (c.kind() == UiCornerKind.ROUNDED) {
            double cx = switch (corner) {
                case TOP_LEFT, BOTTOM_LEFT -> x + ex;
                case TOP_RIGHT, BOTTOM_RIGHT -> x - ex;
            };
            double cy = switch (corner) {
                case TOP_LEFT, TOP_RIGHT -> y + ey;
                case BOTTOM_LEFT, BOTTOM_RIGHT -> y - ey;
            };
            double a0 = switch (corner) {
                case TOP_RIGHT -> -90.0;
                case BOTTOM_RIGHT -> 0.0;
                case BOTTOM_LEFT -> 90.0;
                case TOP_LEFT -> 180.0;
            };
            int segments = Math.max(3, Math.min(12, (int) Math.ceil(Math.max(ex, ey) / 4.0)));
            for (int i = 1; i <= segments; i++) {
                double t = i / (double) segments;
                double a = Math.toRadians(a0 + t * 90.0);
                add(out, max, count, cx + Math.cos(a) * ex, cy + Math.sin(a) * ey);
            }
            return;
        }
        if (c.kind() == UiCornerKind.CONCAVE_ROUNDED) {
            double a0 = switch (corner) {
                case TOP_RIGHT -> 180.0;
                case BOTTOM_RIGHT -> -90.0;
                case BOTTOM_LEFT -> 0.0;
                case TOP_LEFT -> 90.0;
            };
            int segments = Math.max(3, Math.min(12, (int) Math.ceil(Math.max(ex, ey) / 4.0)));
            for (int i = 1; i <= segments; i++) {
                double t = i / (double) segments;
                double a = Math.toRadians(a0 - t * 90.0);
                add(out, max, count, x + Math.cos(a) * ex, y + Math.sin(a) * ey);
            }
            return;
        }
        if (c.kind() == UiCornerKind.NOTCHED) {
            switch (corner) {
                case TOP_RIGHT -> {
                    add(out, max, count, x - ex, y + ey);
                    add(out, max, count, x, y + ey);
                }
                case BOTTOM_RIGHT -> {
                    add(out, max, count, x - ex, y - ey);
                    add(out, max, count, x - ex, y);
                }
                case BOTTOM_LEFT -> {
                    add(out, max, count, x + ex, y - ey);
                    add(out, max, count, x, y - ey);
                }
                case TOP_LEFT -> {
                    add(out, max, count, x + ex, y + ey);
                    add(out, max, count, x + ex, y);
                }
            }
            return;
        }
        if (c.kind() == UiCornerKind.CHAMFERED) {
            switch (corner) {
                case TOP_RIGHT -> add(out, max, count, x, y + ey);
                case BOTTOM_RIGHT -> add(out, max, count, x - ex, y);
                case BOTTOM_LEFT -> add(out, max, count, x, y - ey);
                case TOP_LEFT -> add(out, max, count, x + ex, y);
            }
            return;
        }
        add(out, max, count, x, y);
    }

    private static void addHorizontalEdge(double[] out, int max, int[] count, UiEdgeSpec edge,
                                          double startX, double endX, double y, boolean forward, double fullWidth) {
        if (edge == null || edge.isStraight()) {
            add(out, max, count, endX, y);
            return;
        }
        if (edge.kind() == UiEdgeKind.INSET) {
            double direction = forward ? 1.0 : -1.0;
            add(out, max, count, startX, y + edge.depth() * direction);
            add(out, max, count, endX, y + edge.depth() * direction);
            add(out, max, count, endX, y);
            return;
        }
        if (edge.kind() != UiEdgeKind.NOTCHED && edge.kind() != UiEdgeKind.CUT
                && edge.kind() != UiEdgeKind.PROTRUSION) {
            add(out, max, count, endX, y);
            return;
        }
        double left = Math.min(startX, endX);
        double right = Math.max(startX, endX);
        double width = Math.max(0.0, edge.size());
        double center = edge.offset() < 0.0f ? left + (right - left) * 0.5 : left + Math.min(Math.max(0.0, edge.offset()), fullWidth);
        double a = Math.max(left, center - width * 0.5);
        double b = Math.min(right, center + width * 0.5);
        double depth = edge.kind() == UiEdgeKind.PROTRUSION ? -edge.depth() : edge.depth();
        if (forward) {
            add(out, max, count, a, y);
            if (edge.kind() == UiEdgeKind.CUT) {
                add(out, max, count, (a + b) * 0.5, y + depth);
            } else {
                add(out, max, count, a, y + depth);
                add(out, max, count, b, y + depth);
            }
            add(out, max, count, b, y);
            add(out, max, count, endX, y);
        } else {
            add(out, max, count, b, y);
            if (edge.kind() == UiEdgeKind.CUT) {
                add(out, max, count, (a + b) * 0.5, y - depth);
            } else {
                add(out, max, count, b, y - depth);
                add(out, max, count, a, y - depth);
            }
            add(out, max, count, a, y);
            add(out, max, count, endX, y);
        }
    }

    private static void addVerticalEdge(double[] out, int max, int[] count, UiEdgeSpec edge,
                                        double startY, double endY, double x, boolean forward, double fullHeight) {
        if (edge == null || edge.isStraight()) {
            add(out, max, count, x, endY);
            return;
        }
        if (edge.kind() == UiEdgeKind.INSET) {
            double direction = forward ? -1.0 : 1.0;
            add(out, max, count, x + edge.depth() * direction, startY);
            add(out, max, count, x + edge.depth() * direction, endY);
            add(out, max, count, x, endY);
            return;
        }
        if (edge.kind() != UiEdgeKind.NOTCHED && edge.kind() != UiEdgeKind.CUT
                && edge.kind() != UiEdgeKind.PROTRUSION) {
            add(out, max, count, x, endY);
            return;
        }
        double top = Math.min(startY, endY);
        double bottom = Math.max(startY, endY);
        double height = Math.max(0.0, edge.size());
        double center = edge.offset() < 0.0f ? top + (bottom - top) * 0.5 : top + Math.min(Math.max(0.0, edge.offset()), fullHeight);
        double a = Math.max(top, center - height * 0.5);
        double b = Math.min(bottom, center + height * 0.5);
        double depth = edge.kind() == UiEdgeKind.PROTRUSION ? -edge.depth() : edge.depth();
        if (forward) {
            add(out, max, count, x, a);
            if (edge.kind() == UiEdgeKind.CUT) {
                add(out, max, count, x - depth, (a + b) * 0.5);
            } else {
                add(out, max, count, x - depth, a);
                add(out, max, count, x - depth, b);
            }
            add(out, max, count, x, b);
            add(out, max, count, x, endY);
        } else {
            add(out, max, count, x, b);
            if (edge.kind() == UiEdgeKind.CUT) {
                add(out, max, count, x + depth, (a + b) * 0.5);
            } else {
                add(out, max, count, x + depth, b);
                add(out, max, count, x + depth, a);
            }
            add(out, max, count, x, a);
            add(out, max, count, x, endY);
        }
    }

    private static void add(double[] out, int max, int[] count, double x, double y) {
        if (count[0] > 0) {
            double px = out[(count[0] - 1) * 2];
            double py = out[(count[0] - 1) * 2 + 1];
            if (Math.abs(px - x) < 0.0001 && Math.abs(py - y) < 0.0001) return;
        }
        if (count[0] >= max) return;
        out[count[0] * 2] = x;
        out[count[0] * 2 + 1] = y;
        count[0]++;
    }

    private enum Corner {TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT}
}
