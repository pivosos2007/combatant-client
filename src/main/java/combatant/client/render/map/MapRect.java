/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

/** Immutable axis-aligned rectangle used by map layout and hit testing. */
public record MapRect(double x, double y, double width, double height) {
    public MapRect {
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("Map rectangle values must be finite.");
        }
        if (width < 0.0 || height < 0.0) {
            throw new IllegalArgumentException("Map rectangle size cannot be negative.");
        }
    }

    public double maxX() {
        return x + width;
    }

    public double maxY() {
        return y + height;
    }

    public double centerX() {
        return x + width * 0.5;
    }

    public double centerY() {
        return y + height * 0.5;
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX <= maxX() && pointY >= y && pointY <= maxY();
    }

    public boolean intersects(MapRect other) {
        return other != null
                && maxX() >= other.x
                && other.maxX() >= x
                && maxY() >= other.y
                && other.maxY() >= y;
    }

    public MapRect inset(double amount) {
        double inset = Math.max(0.0, amount);
        return new MapRect(
                x + inset,
                y + inset,
                Math.max(0.0, width - inset * 2.0),
                Math.max(0.0, height - inset * 2.0)
        );
    }
}
