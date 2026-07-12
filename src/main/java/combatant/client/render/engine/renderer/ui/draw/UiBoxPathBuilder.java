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
        if (c.kind() == UiCornerKind.ROUNDED || c.kind() == UiCornerKind.CONCAVE_ROUNDED) {
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
        if (c.kind() == UiCornerKind.CHAMFERED || c.kind() == UiCornerKind.NOTCHED) {
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
        if (edge == null || edge.isStraight() || edge.kind() != UiEdgeKind.NOTCHED) {
            add(out, max, count, endX, y);
            return;
        }
        double left = Math.min(startX, endX);
        double right = Math.max(startX, endX);
        double width = Math.max(0.0, edge.size());
        double center = edge.offset() < 0.0f ? left + (right - left) * 0.5 : left + Math.min(Math.max(0.0, edge.offset()), fullWidth);
        double a = Math.max(left, center - width * 0.5);
        double b = Math.min(right, center + width * 0.5);
        double depth = edge.depth();
        if (forward) {
            add(out, max, count, a, y);
            add(out, max, count, a, y + depth);
            add(out, max, count, b, y + depth);
            add(out, max, count, b, y);
            add(out, max, count, endX, y);
        } else {
            add(out, max, count, b, y);
            add(out, max, count, b, y - depth);
            add(out, max, count, a, y - depth);
            add(out, max, count, a, y);
            add(out, max, count, endX, y);
        }
    }

    private static void addVerticalEdge(double[] out, int max, int[] count, UiEdgeSpec edge,
                                        double startY, double endY, double x, boolean forward, double fullHeight) {
        if (edge == null || edge.isStraight() || edge.kind() != UiEdgeKind.NOTCHED) {
            add(out, max, count, x, endY);
            return;
        }
        double top = Math.min(startY, endY);
        double bottom = Math.max(startY, endY);
        double height = Math.max(0.0, edge.size());
        double center = edge.offset() < 0.0f ? top + (bottom - top) * 0.5 : top + Math.min(Math.max(0.0, edge.offset()), fullHeight);
        double a = Math.max(top, center - height * 0.5);
        double b = Math.min(bottom, center + height * 0.5);
        double depth = edge.depth();
        if (forward) {
            add(out, max, count, x, a);
            add(out, max, count, x - depth, a);
            add(out, max, count, x - depth, b);
            add(out, max, count, x, b);
            add(out, max, count, x, endY);
        } else {
            add(out, max, count, x, b);
            add(out, max, count, x + depth, b);
            add(out, max, count, x + depth, a);
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
