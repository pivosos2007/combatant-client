/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/**
 * Geometry of one box corner. Rounded and chamfered corners intentionally share
 * the same x/y extents so one shape can freely mix rounded, cut and square corners.
 */
public record UiCornerSpec(UiCornerKind kind,
                           float radiusX,
                           float radiusY,
                           float cutX,
                           float cutY) {
    public static final UiCornerSpec SQUARE = new UiCornerSpec(UiCornerKind.SQUARE, 0f, 0f, 0f, 0f);

    public UiCornerSpec {
        kind = kind != null ? kind : UiCornerKind.SQUARE;
        radiusX = Math.max(0f, radiusX);
        radiusY = Math.max(0f, radiusY);
        cutX = Math.max(0f, cutX);
        cutY = Math.max(0f, cutY);
    }

    public static UiCornerSpec square() {
        return SQUARE;
    }

    public static UiCornerSpec rounded(double radius) {
        return rounded(radius, radius);
    }

    public static UiCornerSpec rounded(double radiusX, double radiusY) {
        return new UiCornerSpec(UiCornerKind.ROUNDED, (float) radiusX, (float) radiusY, 0f, 0f);
    }

    public static UiCornerSpec concaveRounded(double radius) {
        return new UiCornerSpec(UiCornerKind.CONCAVE_ROUNDED, (float) radius, (float) radius, 0f, 0f);
    }

    public static UiCornerSpec chamfered(double cut) {
        return chamfered(cut, cut);
    }

    public static UiCornerSpec chamfered(double cutX, double cutY) {
        return new UiCornerSpec(UiCornerKind.CHAMFERED, 0f, 0f, (float) cutX, (float) cutY);
    }

    public static UiCornerSpec notched(double width, double depth) {
        return new UiCornerSpec(UiCornerKind.NOTCHED, 0f, 0f, (float) width, (float) depth);
    }

    public float extentX() {
        return Math.max(radiusX, cutX);
    }

    public float extentY() {
        return Math.max(radiusY, cutY);
    }

    public boolean isSquare() {
        return kind == UiCornerKind.SQUARE && extentX() <= 0.0001f && extentY() <= 0.0001f;
    }
}
