/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import java.util.Arrays;

/**
 * Supports up to four circle sources, two rounded boxes, or two squircle boxes in a single compound SDF.
 */
public final class UiCompoundSdf {
    public static final int MAX_CIRCLES = 4;

    public enum Mode {
        ISLAND_BLOB,
        SMOOTH_BOX_UNION,
        SMOOTH_SQUIRCLE_UNION
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
    private final float firstSquircleExponent;
    private final float secondSquircleExponent;

    private UiCompoundSdf(Mode mode,
                          UiRect bounds,
                          float smoothing,
                          Circle[] circles,
                          UiRect firstBox,
                          UiRect secondBox,
                          float firstRadius,
                          float secondRadius,
                          float firstSquircleExponent,
                          float secondSquircleExponent) {
        this.mode = mode;
        this.bounds = bounds;
        this.smoothing = Math.max(0.0f, smoothing);
        this.circles = circles != null ? circles : new Circle[0];
        this.firstBox = firstBox;
        this.secondBox = secondBox;
        this.firstRadius = Math.max(0.0f, firstRadius);
        this.secondRadius = Math.max(0.0f, secondRadius);
        this.firstSquircleExponent = normalizeSquircleExponent(firstSquircleExponent);
        this.secondSquircleExponent = normalizeSquircleExponent(secondSquircleExponent);
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
        return new UiCompoundSdf(Mode.ISLAND_BLOB, bounds, smooth, copy, null, null,
                0f, 0f, 4f, 4f);
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
                (float) secondRadius,
                4f,
                4f
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

    /**
     * Smooth union of two superellipse/squircle boxes. This keeps the authored squircle
     * silhouette on both lobes while allowing the SDF field between them to form a real
     * metaball-like neck instead of falling back to circles or ordinary rounded boxes.
     */
    public static UiCompoundSdf smoothSquircleUnion(UiRect first,
                                                     double firstExponent,
                                                     UiRect second,
                                                     double secondExponent,
                                                     double smoothing) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Smooth squircle union requires two boxes");
        }
        float smooth = (float) Math.max(0.0, smoothing);
        UiRect bounds = unionBounds(first, second, smooth);
        return new UiCompoundSdf(
                Mode.SMOOTH_SQUIRCLE_UNION,
                bounds,
                smooth,
                null,
                first,
                second,
                0f,
                0f,
                normalizeSquircleExponent((float) firstExponent),
                normalizeSquircleExponent((float) secondExponent)
        );
    }

    public static UiCompoundSdf smoothSquircleUnion(double ax, double ay, double aw, double ah, double firstExponent,
                                                     double bx, double by, double bw, double bh, double secondExponent,
                                                     double smoothing) {
        return smoothSquircleUnion(
                UiRect.of(ax, ay, aw, ah), firstExponent,
                UiRect.of(bx, by, bw, bh), secondExponent,
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

    public float firstSquircleExponent() {
        return firstSquircleExponent;
    }

    public float secondSquircleExponent() {
        return secondSquircleExponent;
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

    private static float normalizeSquircleExponent(float exponent) {
        if (!Float.isFinite(exponent)) return 4.0f;
        return Math.max(2.0f, Math.min(16.0f, exponent));
    }
}
