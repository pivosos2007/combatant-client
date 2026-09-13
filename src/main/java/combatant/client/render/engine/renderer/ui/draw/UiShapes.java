/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/**
 * Convenience factory for the normalized Renderer2D shape model.
 */
public enum UiShapes {
    ;

    public static UiShape rect(double x, double y, double width, double height) {
        return UiShape.rect(x, y, width, height);
    }

    public static UiShape box(UiBoxShape box) {
        return UiShape.box(box);
    }

    public static UiBoxShape.Builder flexibleBox(double x, double y, double width, double height) {
        return UiBoxShape.rect(x, y, width, height);
    }

    public static UiPrimitive.Builder primitive(double x, double y, double width, double height) {
        return UiPrimitive.builder(x, y, width, height);
    }

    public static UiPrimitive primitive(double x, double y, double width, double height,
                                        UiPrimitive.Preset preset, double cut, double rounding) {
        return UiPrimitive.builder(x, y, width, height)
                .preset(preset)
                .cut(cut)
                .rounding(rounding)
                .build();
    }

    public static UiCompoundSdf islandBlob(double smoothing, UiCompoundSdf.Circle... circles) {
        return UiCompoundSdf.islandBlob(smoothing, circles);
    }

    public static UiCompoundSdf smoothBoxUnion(UiRect first, double firstRadius,
                                                UiRect second, double secondRadius,
                                                double smoothing) {
        return UiCompoundSdf.smoothBoxUnion(first, firstRadius, second, secondRadius, smoothing);
    }

    public static UiShape rounded(double x, double y, double width, double height, double radius) {
        return UiShape.box(UiBoxShape.rounded(x, y, width, height, radius));
    }

    public static UiShape rounded(double x, double y, double width, double height,
                                  double tl, double tr, double br, double bl) {
        UiBoxShape box = UiBoxShape.rect(x, y, width, height)
                .corners(UiCornerSpec.rounded(tl), UiCornerSpec.rounded(tr), UiCornerSpec.rounded(br), UiCornerSpec.rounded(bl))
                .build();
        return UiShape.box(box);
    }

    public static UiShape chamfered(double x, double y, double width, double height, double chamfer) {
        return UiShape.box(UiBoxShape.chamfered(x, y, width, height, chamfer));
    }

    public static UiShape squircle(double x, double y, double width, double height) {
        return UiShape.box(UiBoxShape.squircle(x, y, width, height));
    }

    public static UiShape squircle(double x, double y, double width, double height, UiSquircleProfile profile) {
        return UiShape.box(UiBoxShape.squircle(x, y, width, height, profile));
    }

    public static UiShape squircle(double x, double y, double width, double height, double exponent) {
        return UiShape.box(UiBoxShape.squircle(x, y, width, height, exponent));
    }

    public static UiShape chamfered(double x, double y, double width, double height,
                                    double tl, double tr, double br, double bl) {
        UiBoxShape box = UiBoxShape.rect(x, y, width, height)
                .corners(UiCornerSpec.chamfered(tl), UiCornerSpec.chamfered(tr), UiCornerSpec.chamfered(br), UiCornerSpec.chamfered(bl))
                .build();
        return UiShape.box(box);
    }

    public static UiShape mixedBox(double x, double y, double width, double height,
                                   UiCornerSpec tl, UiCornerSpec tr, UiCornerSpec br, UiCornerSpec bl) {
        return UiShape.box(UiBoxShape.rect(x, y, width, height).corners(tl, tr, br, bl).build());
    }

    public static UiShape circle(double cx, double cy, double radius) {
        return UiShape.circle(cx, cy, radius);
    }

    public static UiShape polyline(double[] points, int count, boolean closed) {
        return UiShape.polyline(points, count, closed);
    }
}
