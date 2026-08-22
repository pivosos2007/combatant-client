/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import java.util.Arrays;

/**
 * Declarative panel primitive used by the fullscreen UI.
 *
 * <p>The builder deliberately separates authoring from lowering: callers choose
 * a reusable preset and only override the corners/sides which are exceptional.
 * The result is a normalized polygon which can be rendered by the analytic GPU
 * path when it is convex and small enough, or by the regular polygon fallback.</p>
 */
public final class UiPrimitive {
    public static final int MAX_SHADER_VERTICES = 8;

    private final UiRect bounds;
    private final double[] points;
    private final int pointCount;
    private final float rounding;
    private final boolean convex;

    private UiPrimitive(double[] points, int pointCount, float rounding) {
        this.pointCount = Math.max(0, Math.min(pointCount, points != null ? points.length / 2 : 0));
        this.points = this.pointCount == 0 ? new double[0] : Arrays.copyOf(points, this.pointCount * 2);
        this.bounds = computeBounds(this.points, this.pointCount);
        this.rounding = Float.isFinite(rounding) ? Math.max(0.0f, rounding) : 0.0f;
        this.convex = isConvexClockwise(this.points, this.pointCount);
    }

    public static Builder builder(double x, double y, double width, double height) {
        return new Builder(UiRect.of(x, y, width, height));
    }

    public static Builder builder(UiRect bounds) {
        return new Builder(bounds != null ? bounds : UiRect.of(0, 0, 0, 0));
    }

    public UiRect bounds() {
        return bounds;
    }

    public double[] points() {
        return Arrays.copyOf(points, points.length);
    }

    public int pointCount() {
        return pointCount;
    }

    public float rounding() {
        return rounding;
    }

    public boolean isConvex() {
        return convex;
    }

    public boolean shaderEligible() {
        return convex && pointCount >= 3 && pointCount <= MAX_SHADER_VERTICES;
    }

    /** Returns point coordinates relative to the primitive's final bounds. */
    public float localX(int index) {
        if (pointCount == 0) return 0.0f;
        return (float) (points[normalizedIndex(index) * 2] - bounds.x());
    }

    public float localY(int index) {
        if (pointCount == 0) return 0.0f;
        return (float) (points[normalizedIndex(index) * 2 + 1] - bounds.y());
    }

    private int normalizedIndex(int index) {
        if (pointCount == 0) return 0;
        return Math.floorMod(index, pointCount);
    }

    public enum Preset {
        RECT,
        CHAMFERED,
        HEXAGON,
        TRAPEZOID_LEFT,
        TRAPEZOID_RIGHT,
        PARALLELOGRAM_LEFT,
        PARALLELOGRAM_RIGHT,
        DIRECTIONAL_LEFT,
        DIRECTIONAL_RIGHT,
        NOTCHED_TOP,
        STEPPED_LEFT,
        STEPPED_RIGHT
    }

    public enum Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT
    }

    public enum Side {
        TOP,
        RIGHT,
        BOTTOM,
        LEFT
    }

    public static final class Builder {
        private static final int PATH_CAPACITY = 64;

        private final UiRect layoutBounds;
        private Preset preset = Preset.RECT;
        private float cut = Float.NaN;
        private float rounding;
        private final UiCornerSpec[] corners = {
                UiCornerSpec.SQUARE, UiCornerSpec.SQUARE, UiCornerSpec.SQUARE, UiCornerSpec.SQUARE
        };
        private final UiEdgeSpec[] edges = {
                UiEdgeSpec.STRAIGHT, UiEdgeSpec.STRAIGHT, UiEdgeSpec.STRAIGHT, UiEdgeSpec.STRAIGHT
        };
        private final boolean[] cornerOverride = new boolean[4];
        private final boolean[] edgeOverride = new boolean[4];
        private final double[] cornerOffsetX = new double[4];
        private final double[] cornerOffsetY = new double[4];
        private double[] customPoints;
        private int customPointCount;
        private final double[] vertexOffsetX = new double[PATH_CAPACITY];
        private final double[] vertexOffsetY = new double[PATH_CAPACITY];

        private Builder(UiRect bounds) {
            this.layoutBounds = bounds;
        }

        public Builder preset(Preset preset) {
            this.preset = preset != null ? preset : Preset.RECT;
            this.customPoints = null;
            this.customPointCount = 0;
            return this;
        }

        /** Main cut/inset size used by the selected preset. */
        public Builder cut(double cut) {
            this.cut = finitePositive(cut);
            return this;
        }

        /**
         * Geometric edge/corner softness in logical pixels. The analytic shader
         * applies it to the complete primitive rather than requiring every corner
         * to carry another radius value.
         */
        public Builder rounding(double rounding) {
            this.rounding = finitePositive(rounding);
            return this;
        }

        public Builder allCorners(UiCornerSpec spec) {
            UiCornerSpec safe = spec != null ? spec : UiCornerSpec.SQUARE;
            for (Corner corner : Corner.values()) corner(corner, safe);
            return this;
        }

        public Builder corner(Corner corner, UiCornerSpec spec) {
            int i = safeCorner(corner).ordinal();
            corners[i] = spec != null ? spec : UiCornerSpec.SQUARE;
            cornerOverride[i] = true;
            return this;
        }

        /** Sets the two corners touching a side in clockwise path order. */
        public Builder sideCorners(Side side, UiCornerSpec start, UiCornerSpec end) {
            return switch (safeSide(side)) {
                case TOP -> corner(Corner.TOP_LEFT, start).corner(Corner.TOP_RIGHT, end);
                case RIGHT -> corner(Corner.TOP_RIGHT, start).corner(Corner.BOTTOM_RIGHT, end);
                case BOTTOM -> corner(Corner.BOTTOM_RIGHT, start).corner(Corner.BOTTOM_LEFT, end);
                case LEFT -> corner(Corner.BOTTOM_LEFT, start).corner(Corner.TOP_LEFT, end);
            };
        }

        public Builder side(Side side, UiEdgeSpec spec) {
            int i = safeSide(side).ordinal();
            edges[i] = spec != null ? spec : UiEdgeSpec.STRAIGHT;
            edgeOverride[i] = true;
            return this;
        }

        /** Moves one semantic panel corner before the final polygon is lowered. */
        public Builder cornerOffset(Corner corner, double dx, double dy) {
            int i = safeCorner(corner).ordinal();
            cornerOffsetX[i] = finite(dx);
            cornerOffsetY[i] = finite(dy);
            return this;
        }

        /** Moves both semantic corners belonging to a side. */
        public Builder sideOffset(Side side, double dx, double dy) {
            return switch (safeSide(side)) {
                case TOP -> addCornerOffset(Corner.TOP_LEFT, dx, dy).addCornerOffset(Corner.TOP_RIGHT, dx, dy);
                case RIGHT -> addCornerOffset(Corner.TOP_RIGHT, dx, dy).addCornerOffset(Corner.BOTTOM_RIGHT, dx, dy);
                case BOTTOM -> addCornerOffset(Corner.BOTTOM_RIGHT, dx, dy).addCornerOffset(Corner.BOTTOM_LEFT, dx, dy);
                case LEFT -> addCornerOffset(Corner.BOTTOM_LEFT, dx, dy).addCornerOffset(Corner.TOP_LEFT, dx, dy);
            };
        }

        private Builder addCornerOffset(Corner corner, double dx, double dy) {
            int i = corner.ordinal();
            cornerOffsetX[i] += finite(dx);
            cornerOffsetY[i] += finite(dy);
            return this;
        }

        /** Fine adjustment after a preset has emitted its clockwise vertices. */
        public Builder vertexOffset(int clockwiseIndex, double dx, double dy) {
            if (clockwiseIndex < 0 || clockwiseIndex >= PATH_CAPACITY) {
                throw new IllegalArgumentException("Primitive vertex index must be in [0, " + (PATH_CAPACITY - 1) + "]");
            }
            vertexOffsetX[clockwiseIndex] = finite(dx);
            vertexOffsetY[clockwiseIndex] = finite(dy);
            return this;
        }

        /**
         * Supplies normalized clockwise points in the 0..1 layout-bounds space.
         * Convex polygons with at most eight points use the analytic shader.
         */
        public Builder customConvex(double... normalizedPoints) {
            if (normalizedPoints == null || normalizedPoints.length < 6 || (normalizedPoints.length & 1) != 0) {
                throw new IllegalArgumentException("A custom primitive requires at least three x/y pairs");
            }
            if (normalizedPoints.length / 2 > PATH_CAPACITY) {
                throw new IllegalArgumentException("A custom primitive supports at most " + PATH_CAPACITY + " points");
            }
            this.customPoints = Arrays.copyOf(normalizedPoints, normalizedPoints.length);
            this.customPointCount = normalizedPoints.length / 2;
            return this;
        }

        public UiPrimitive build() {
            if (!isBoxPreset() && hasOverrides()) {
                throw new IllegalStateException("Corner/edge overrides require a box-based primitive preset; "
                        + "use cornerOffset/sideOffset/vertexOffset for " + preset);
            }
            double[] path = new double[PATH_CAPACITY * 2];
            int count = emitBase(path);
            if (count < 3) return new UiPrimitive(path, count, rounding);
            applyBilinearCornerWarp(path, count);
            for (int i = 0; i < count; i++) {
                path[i * 2] += vertexOffsetX[i];
                path[i * 2 + 1] += vertexOffsetY[i];
            }
            count = removeDuplicateClosingPoint(path, count);
            ensureClockwise(path, count);
            return new UiPrimitive(path, count, rounding);
        }

        private int emitBase(double[] path) {
            if (customPoints != null) {
                int count = Math.min(customPointCount, PATH_CAPACITY);
                for (int i = 0; i < count; i++) {
                    path[i * 2] = layoutBounds.x() + customPoints[i * 2] * layoutBounds.width();
                    path[i * 2 + 1] = layoutBounds.y() + customPoints[i * 2 + 1] * layoutBounds.height();
                }
                return count;
            }

            if (usesBoxLowering()) {
                UiCornerSpec[] resolvedCorners = presetCorners();
                UiEdgeSpec[] resolvedEdges = presetEdges();
                UiBoxShape box = UiBoxShape.rect(layoutBounds.x(), layoutBounds.y(), layoutBounds.width(), layoutBounds.height())
                        .corners(resolvedCorners[0], resolvedCorners[1], resolvedCorners[2], resolvedCorners[3])
                        .edges(resolvedEdges[0], resolvedEdges[1], resolvedEdges[2], resolvedEdges[3])
                        .build();
                return UiBoxPathBuilder.write(box, path, PATH_CAPACITY);
            }

            double[] normalized = presetPolygon(preset, resolvedCutX(), resolvedCutY());
            int count = Math.min(normalized.length / 2, PATH_CAPACITY);
            for (int i = 0; i < count; i++) {
                path[i * 2] = layoutBounds.x() + normalized[i * 2] * layoutBounds.width();
                path[i * 2 + 1] = layoutBounds.y() + normalized[i * 2 + 1] * layoutBounds.height();
            }
            return count;
        }

        private boolean usesBoxLowering() {
            return isBoxPreset();
        }

        private boolean isBoxPreset() {
            return preset == Preset.RECT || preset == Preset.CHAMFERED
                    || preset == Preset.NOTCHED_TOP || preset == Preset.STEPPED_LEFT || preset == Preset.STEPPED_RIGHT;
        }

        private boolean hasOverrides() {
            for (boolean value : cornerOverride) if (value) return true;
            for (boolean value : edgeOverride) if (value) return true;
            return false;
        }

        private UiCornerSpec[] presetCorners() {
            float c = resolvedCutPixels();
            UiCornerSpec base = preset == Preset.CHAMFERED ? UiCornerSpec.chamfered(c) : UiCornerSpec.SQUARE;
            UiCornerSpec[] result = {base, base, base, base};
            for (int i = 0; i < 4; i++) if (cornerOverride[i]) result[i] = corners[i];
            return result;
        }

        private UiEdgeSpec[] presetEdges() {
            float c = resolvedCutPixels();
            UiEdgeSpec[] result = {
                    UiEdgeSpec.STRAIGHT, UiEdgeSpec.STRAIGHT, UiEdgeSpec.STRAIGHT, UiEdgeSpec.STRAIGHT
            };
            switch (preset) {
                case NOTCHED_TOP -> result[Side.TOP.ordinal()] = UiEdgeSpec.notchedCenter(c * 2.0, c);
                case STEPPED_LEFT -> result[Side.LEFT.ordinal()] = UiEdgeSpec.notchedCenter(c * 2.0, c);
                case STEPPED_RIGHT -> result[Side.RIGHT.ordinal()] = UiEdgeSpec.notchedCenter(c * 2.0, c);
                default -> { }
            }
            for (int i = 0; i < 4; i++) if (edgeOverride[i]) result[i] = edges[i];
            return result;
        }

        private float resolvedCutPixels() {
            if (Float.isFinite(cut)) return cut;
            return (float) Math.max(2.0, Math.min(layoutBounds.width(), layoutBounds.height()) * 0.16);
        }

        private double resolvedCutX() {
            return Math.min(0.45, resolvedCutPixels() / Math.max(1.0, layoutBounds.width()));
        }

        private double resolvedCutY() {
            return Math.min(0.45, resolvedCutPixels() / Math.max(1.0, layoutBounds.height()));
        }

        private static double[] presetPolygon(Preset preset, double cutX, double cutY) {
            double cx = Math.max(0.0, Math.min(0.45, cutX));
            double cy = Math.max(0.0, Math.min(0.45, cutY));
            return switch (preset) {
                case HEXAGON -> new double[]{cx, 0, 1 - cx, 0, 1, 0.5, 1 - cx, 1, cx, 1, 0, 0.5};
                case TRAPEZOID_LEFT -> new double[]{cx, 0, 1, 0, 1, 1, 0, 1};
                case TRAPEZOID_RIGHT -> new double[]{0, 0, 1 - cx, 0, 1, 1, 0, 1};
                case PARALLELOGRAM_LEFT -> new double[]{cx, 0, 1, 0, 1 - cx, 1, 0, 1};
                case PARALLELOGRAM_RIGHT -> new double[]{0, 0, 1 - cx, 0, 1, 1, cx, 1};
                case DIRECTIONAL_LEFT -> new double[]{cx, 0, 1, 0, 1, 1 - cy, 1 - cx, 1, 0, 1, 0, cy};
                case DIRECTIONAL_RIGHT -> new double[]{0, 0, 1 - cx, 0, 1, cy, 1, 1, cx, 1, 0, 1 - cy};
                default -> new double[]{0, 0, 1, 0, 1, 1, 0, 1};
            };
        }

        private void applyBilinearCornerWarp(double[] path, int count) {
            double width = Math.max(0.0001, layoutBounds.width());
            double height = Math.max(0.0001, layoutBounds.height());
            for (int i = 0; i < count; i++) {
                double u = clamp01((path[i * 2] - layoutBounds.x()) / width);
                double v = clamp01((path[i * 2 + 1] - layoutBounds.y()) / height);
                double topX = lerp(cornerOffsetX[0], cornerOffsetX[1], u);
                double bottomX = lerp(cornerOffsetX[3], cornerOffsetX[2], u);
                double topY = lerp(cornerOffsetY[0], cornerOffsetY[1], u);
                double bottomY = lerp(cornerOffsetY[3], cornerOffsetY[2], u);
                path[i * 2] += lerp(topX, bottomX, v);
                path[i * 2 + 1] += lerp(topY, bottomY, v);
            }
        }
    }

    private static UiRect computeBounds(double[] points, int pointCount) {
        if (pointCount <= 0) return UiRect.of(0, 0, 0, 0);
        double minX = points[0], maxX = points[0], minY = points[1], maxY = points[1];
        for (int i = 1; i < pointCount; i++) {
            double x = points[i * 2];
            double y = points[i * 2 + 1];
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        return UiRect.of(minX, minY, maxX - minX, maxY - minY);
    }

    private static boolean isConvexClockwise(double[] points, int count) {
        if (count < 3) return false;
        double sign = 0.0;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            int k = (i + 2) % count;
            double ax = points[j * 2] - points[i * 2];
            double ay = points[j * 2 + 1] - points[i * 2 + 1];
            double bx = points[k * 2] - points[j * 2];
            double by = points[k * 2 + 1] - points[j * 2 + 1];
            double cross = ax * by - ay * bx;
            if (Math.abs(cross) <= 1.0e-7) continue;
            if (sign == 0.0) sign = Math.signum(cross);
            else if (Math.signum(cross) != sign) return false;
        }
        return sign > 0.0;
    }

    private static void ensureClockwise(double[] points, int count) {
        if (signedArea(points, count) >= 0.0) return;
        for (int i = 0; i < count / 2; i++) {
            int j = count - 1 - i;
            double x = points[i * 2];
            double y = points[i * 2 + 1];
            points[i * 2] = points[j * 2];
            points[i * 2 + 1] = points[j * 2 + 1];
            points[j * 2] = x;
            points[j * 2 + 1] = y;
        }
    }

    private static double signedArea(double[] points, int count) {
        double area = 0.0;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1];
        }
        return area * 0.5;
    }

    private static int removeDuplicateClosingPoint(double[] points, int count) {
        if (count < 2) return count;
        double dx = points[0] - points[(count - 1) * 2];
        double dy = points[1] - points[(count - 1) * 2 + 1];
        return dx * dx + dy * dy <= 1.0e-10 ? count - 1 : count;
    }

    private static Corner safeCorner(Corner corner) {
        return corner != null ? corner : Corner.TOP_LEFT;
    }

    private static Side safeSide(Side side) {
        return side != null ? side : Side.TOP;
    }

    private static float finitePositive(double value) {
        return Double.isFinite(value) ? (float) Math.max(0.0, value) : 0.0f;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
