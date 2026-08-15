/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/**
 * Flexible box primitive: every corner and every edge can have independent
 * geometry. This is the authoring model for Renderer2D and the JS UI runtime;
 * legacy rounded/chamfered/notched methods should normalize into this object.
 */
public final class UiBoxShape {
    private final UiRect bounds;
    private final UiBoxForm form;
    private final float squircleExponent;
    private final UiCornerSpec topLeft;
    private final UiCornerSpec topRight;
    private final UiCornerSpec bottomRight;
    private final UiCornerSpec bottomLeft;
    private final UiEdgeSpec top;
    private final UiEdgeSpec right;
    private final UiEdgeSpec bottom;
    private final UiEdgeSpec left;

    private UiBoxShape(UiRect bounds,
                       UiBoxForm form,
                       float squircleExponent,
                       UiCornerSpec topLeft,
                       UiCornerSpec topRight,
                       UiCornerSpec bottomRight,
                       UiCornerSpec bottomLeft,
                       UiEdgeSpec top,
                       UiEdgeSpec right,
                       UiEdgeSpec bottom,
                       UiEdgeSpec left) {
        this.bounds = bounds != null ? bounds : UiRect.of(0, 0, 0, 0);
        this.form = form != null ? form : UiBoxForm.RECT;
        this.squircleExponent = normalizeSquircleExponent(squircleExponent);
        this.topLeft = topLeft != null ? topLeft : UiCornerSpec.SQUARE;
        this.topRight = topRight != null ? topRight : UiCornerSpec.SQUARE;
        this.bottomRight = bottomRight != null ? bottomRight : UiCornerSpec.SQUARE;
        this.bottomLeft = bottomLeft != null ? bottomLeft : UiCornerSpec.SQUARE;
        this.top = top != null ? top : UiEdgeSpec.STRAIGHT;
        this.right = right != null ? right : UiEdgeSpec.STRAIGHT;
        this.bottom = bottom != null ? bottom : UiEdgeSpec.STRAIGHT;
        this.left = left != null ? left : UiEdgeSpec.STRAIGHT;
    }

    public static Builder rect(double x, double y, double width, double height) {
        return new Builder(UiRect.of(x, y, width, height));
    }

    public static UiBoxShape square(double x, double y, double width, double height) {
        return rect(x, y, width, height).build();
    }

    public static UiBoxShape rounded(double x, double y, double width, double height, double radius) {
        return rect(x, y, width, height).allCorners(UiCornerSpec.rounded(radius, radius)).build();
    }

    public static UiBoxShape squircle(double x, double y, double width, double height) {
        return squircle(x, y, width, height, UiSquircleProfile.STANDARD);
    }

    public static UiBoxShape squircle(double x, double y, double width, double height, UiSquircleProfile profile) {
        return rect(x, y, width, height).squircle(profile).build();
    }

    public static UiBoxShape squircle(double x, double y, double width, double height, double exponent) {
        return rect(x, y, width, height).squircle(exponent).build();
    }

    public static UiBoxShape chamfered(double x, double y, double width, double height, double cut) {
        return rect(x, y, width, height).allCorners(UiCornerSpec.chamfered(cut)).build();
    }

    public UiRect bounds() {
        return bounds;
    }

    public UiBoxForm form() {
        return form;
    }

    public boolean isSquircle() {
        return form == UiBoxForm.SQUIRCLE;
    }

    public float squircleExponent() {
        return squircleExponent;
    }

    public UiCornerSpec topLeft() {
        return topLeft;
    }

    public UiCornerSpec topRight() {
        return topRight;
    }

    public UiCornerSpec bottomRight() {
        return bottomRight;
    }

    public UiCornerSpec bottomLeft() {
        return bottomLeft;
    }

    public UiEdgeSpec top() {
        return top;
    }

    public UiEdgeSpec right() {
        return right;
    }

    public UiEdgeSpec bottom() {
        return bottom;
    }

    public UiEdgeSpec left() {
        return left;
    }

    public boolean hasOnlyStraightEdges() {
        return top.isStraight() && right.isStraight() && bottom.isStraight() && left.isStraight();
    }

    public boolean hasUniformCornerKind(UiCornerKind kind) {
        return topLeft.kind() == kind && topRight.kind() == kind && bottomRight.kind() == kind && bottomLeft.kind() == kind;
    }

    public UiCornerSpec corner(int clockwiseIndex) {
        return switch (clockwiseIndex & 3) {
            case 0 -> topLeft;
            case 1 -> topRight;
            case 2 -> bottomRight;
            default -> bottomLeft;
        };
    }

    public UiEdgeSpec edge(int clockwiseIndex) {
        return switch (clockwiseIndex & 3) {
            case 0 -> top;
            case 1 -> right;
            case 2 -> bottom;
            default -> left;
        };
    }

    public Builder toBuilder() {
        return new Builder(bounds)
                .form(form)
                .squircleExponent(squircleExponent)
                .topLeft(topLeft).topRight(topRight).bottomRight(bottomRight).bottomLeft(bottomLeft)
                .top(top).right(right).bottom(bottom).left(left);
    }

    public static final class Builder {
        private final UiRect bounds;
        private UiBoxForm form = UiBoxForm.RECT;
        private float squircleExponent = UiSquircleProfile.STANDARD.exponent();
        private UiCornerSpec topLeft = UiCornerSpec.SQUARE;
        private UiCornerSpec topRight = UiCornerSpec.SQUARE;
        private UiCornerSpec bottomRight = UiCornerSpec.SQUARE;
        private UiCornerSpec bottomLeft = UiCornerSpec.SQUARE;
        private UiEdgeSpec top = UiEdgeSpec.STRAIGHT;
        private UiEdgeSpec right = UiEdgeSpec.STRAIGHT;
        private UiEdgeSpec bottom = UiEdgeSpec.STRAIGHT;
        private UiEdgeSpec left = UiEdgeSpec.STRAIGHT;

        private Builder(UiRect bounds) {
            this.bounds = bounds;
        }

        public Builder form(UiBoxForm form) {
            this.form = form != null ? form : UiBoxForm.RECT;
            return this;
        }

        public Builder squircle(UiSquircleProfile profile) {
            UiSquircleProfile safe = profile != null ? profile : UiSquircleProfile.STANDARD;
            this.form = UiBoxForm.SQUIRCLE;
            this.squircleExponent = safe.exponent();
            return this;
        }

        public Builder squircle(double exponent) {
            this.form = UiBoxForm.SQUIRCLE;
            this.squircleExponent = normalizeSquircleExponent(exponent);
            return this;
        }

        private Builder squircleExponent(double exponent) {
            this.squircleExponent = normalizeSquircleExponent(exponent);
            return this;
        }

        public Builder allCorners(UiCornerSpec corner) {
            this.topLeft = this.topRight = this.bottomRight = this.bottomLeft = corner != null ? corner : UiCornerSpec.SQUARE;
            return this;
        }

        public Builder rounded(double radius) {
            return allCorners(UiCornerSpec.rounded(radius));
        }

        public Builder chamfered(double cut) {
            return allCorners(UiCornerSpec.chamfered(cut));
        }

        public Builder topLeft(UiCornerSpec topLeft) {
            this.topLeft = topLeft != null ? topLeft : UiCornerSpec.SQUARE;
            return this;
        }

        public Builder topRight(UiCornerSpec topRight) {
            this.topRight = topRight != null ? topRight : UiCornerSpec.SQUARE;
            return this;
        }

        public Builder bottomRight(UiCornerSpec bottomRight) {
            this.bottomRight = bottomRight != null ? bottomRight : UiCornerSpec.SQUARE;
            return this;
        }

        public Builder bottomLeft(UiCornerSpec bottomLeft) {
            this.bottomLeft = bottomLeft != null ? bottomLeft : UiCornerSpec.SQUARE;
            return this;
        }

        public Builder corners(UiCornerSpec topLeft, UiCornerSpec topRight, UiCornerSpec bottomRight, UiCornerSpec bottomLeft) {
            return topLeft(topLeft).topRight(topRight).bottomRight(bottomRight).bottomLeft(bottomLeft);
        }

        public Builder top(UiEdgeSpec top) {
            this.top = top != null ? top : UiEdgeSpec.STRAIGHT;
            return this;
        }

        public Builder right(UiEdgeSpec right) {
            this.right = right != null ? right : UiEdgeSpec.STRAIGHT;
            return this;
        }

        public Builder bottom(UiEdgeSpec bottom) {
            this.bottom = bottom != null ? bottom : UiEdgeSpec.STRAIGHT;
            return this;
        }

        public Builder left(UiEdgeSpec left) {
            this.left = left != null ? left : UiEdgeSpec.STRAIGHT;
            return this;
        }

        public Builder edges(UiEdgeSpec top, UiEdgeSpec right, UiEdgeSpec bottom, UiEdgeSpec left) {
            return top(top).right(right).bottom(bottom).left(left);
        }

        public UiBoxShape build() {
            return new UiBoxShape(bounds, form, squircleExponent,
                    topLeft, topRight, bottomRight, bottomLeft, top, right, bottom, left);
        }
    }

    private static float normalizeSquircleExponent(double exponent) {
        if (!Double.isFinite(exponent)) return UiSquircleProfile.STANDARD.exponent();
        return (float) Math.max(2.0, Math.min(16.0, exponent));
    }
}
