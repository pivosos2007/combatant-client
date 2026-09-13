/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import java.util.Arrays;

/**
 * Small backend-neutral 2D implicit/compound-SDF descriptor.
 *
 * <p>UI backend evaluates either up to four
 * circle sources (IslandBlob/metaball-like smooth union) or a smooth union of two rounded
 * boxes in one analytic quad. Larger fields belong to a future field backend, not to a
 * growing list of special UI shaders.</p>
 */
public final class UiCompoundSdf {
    public static final int MAX_CIRCLES = 4;

    public enum Mode {
        ISLAND_BLOB,
        SMOOTH_BOX_UNION
    }

    public record Circle(double centerX, double centerY, double radius) {
        public Circle {
            radius = Math.max(0.0, radius);
        }
    }

    private final Mode mode;
    private final UiRect bounds;
    private final float smoothing;
    private final Circle[] circles;
    private final UiRect firstBox;
    private final UiRect secondBox;
    private final float firstRadius;
    private final float secondRadius;

    private UiCompoundSdf(Mode mode,
                          UiRect bounds,
                          float smoothing,
                          Circle[] circles,
                          UiRect firstBox,
                          UiRect secondBox,
                          float firstRadius,
                          float secondRadius) {
        this.mode = mode;
        this.bounds = bounds;
        this.smoothing = Math.max(0.0f, smoothing);
        this.circles = circles != null ? circles : new Circle[0];
        this.firstBox = firstBox;
        this.secondBox = secondBox;
        this.firstRadius = Math.max(0.0f, firstRadius);
        this.secondRadius = Math.max(0.0f, secondRadius);
    }

    public static Circle circle(double centerX, double centerY, double radius) {
        return new Circle(centerX, centerY, radius);
    }

    public static UiCompoundSdf islandBlob(double smoothing, Circle... sources) {
        if (sources == null || sources.length == 0) {
            throw new IllegalArgumentException("IslandBlob requires at least one circle source");
        }
        int count = Math.min(MAX_CIRCLES, sources.length);
        Circle[] copy = new Circle[count];
        for (int i = 0; i < count; i++) {
            Circle source = sources[i];
            if (source == null) throw new IllegalArgumentException("IslandBlob circle source cannot be null");
            copy[i] = source;
        }
        float smooth = (float) Math.max(0.0, smoothing);
        UiRect bounds = circleBounds(copy, smooth);
        return new UiCompoundSdf(Mode.ISLAND_BLOB, bounds, smooth, copy, null, null, 0f, 0f);
    }

    public static UiCompoundSdf smoothBoxUnion(UiRect first,
                                                double firstRadius,
                                                UiRect second,
                                                double secondRadius,
                                                double smoothing) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Smooth box union requires two boxes");
        }
        float smooth = (float) Math.max(0.0, smoothing);
        UiRect bounds = unionBounds(first, second, smooth);
        return new UiCompoundSdf(
                Mode.SMOOTH_BOX_UNION,
                bounds,
                smooth,
                null,
                first,
                second,
                (float) firstRadius,
                (float) secondRadius
        );
    }

    public static UiCompoundSdf smoothBoxUnion(double ax, double ay, double aw, double ah, double firstRadius,
                                                double bx, double by, double bw, double bh, double secondRadius,
                                                double smoothing) {
        return smoothBoxUnion(
                UiRect.of(ax, ay, aw, ah), firstRadius,
                UiRect.of(bx, by, bw, bh), secondRadius,
                smoothing
        );
    }

    public Mode mode() {
        return mode;
    }

    public UiRect bounds() {
        return bounds;
    }

    public float smoothing() {
        return smoothing;
    }

    public int circleCount() {
        return circles.length;
    }

    public Circle circle(int index) {
        return circles[index];
    }

    public Circle[] circles() {
        return Arrays.copyOf(circles, circles.length);
    }

    public UiRect firstBox() {
        return firstBox;
    }

    public UiRect secondBox() {
        return secondBox;
    }

    public float firstRadius() {
        return firstRadius;
    }

    public float secondRadius() {
        return secondRadius;
    }

    private static UiRect circleBounds(Circle[] circles, float smoothing) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double pad = smoothing;
        for (Circle circle : circles) {
            minX = Math.min(minX, circle.centerX() - circle.radius() - pad);
            minY = Math.min(minY, circle.centerY() - circle.radius() - pad);
            maxX = Math.max(maxX, circle.centerX() + circle.radius() + pad);
            maxY = Math.max(maxY, circle.centerY() + circle.radius() + pad);
        }
        return UiRect.of(minX, minY, Math.max(0.0, maxX - minX), Math.max(0.0, maxY - minY));
    }

    private static UiRect unionBounds(UiRect a, UiRect b, float smoothing) {
        double minX = Math.min(a.x(), b.x()) - smoothing;
        double minY = Math.min(a.y(), b.y()) - smoothing;
        double maxX = Math.max(a.x() + a.width(), b.x() + b.width()) + smoothing;
        double maxY = Math.max(a.y() + a.height(), b.y() + b.height()) + smoothing;
        return UiRect.of(minX, minY, Math.max(0.0, maxX - minX), Math.max(0.0, maxY - minY));
    }
}
