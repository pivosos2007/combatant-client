/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer;

/**
 * Screen-space UI warp. Coordinates stay in the same 2D HUD space; only emitted vertices are remapped.
 */
public final class RenderWarp {
    public static final RenderWarp IDENTITY = new RenderWarp(
            false,
            0.0f, 0.0f, 1.0f, 1.0f,
            0.0f, 0.0f,
            0.0f, 0.0f,
            0.0f, 0.0f,
            0.0f, 0.0f,
            1.0f
    );

    private final boolean active;
    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final float tlX;
    private final float tlY;
    private final float trX;
    private final float trY;
    private final float brX;
    private final float brY;
    private final float blX;
    private final float blY;
    private final float strength;
    private final float dstTlX;
    private final float dstTlY;
    private final float dstTrX;
    private final float dstTrY;
    private final float dstBrX;
    private final float dstBrY;
    private final float dstBlX;
    private final float dstBlY;
    private final double h00;
    private final double h01;
    private final double h02;
    private final double h10;
    private final double h11;
    private final double h12;
    private final double h20;
    private final double h21;

    private RenderWarp(boolean active,
                       float x,
                       float y,
                       float width,
                       float height,
                       float tlX,
                       float tlY,
                       float trX,
                       float trY,
                       float brX,
                       float brY,
                       float blX,
                       float blY,
                       float strength) {
        this.active = active;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.tlX = tlX;
        this.tlY = tlY;
        this.trX = trX;
        this.trY = trY;
        this.brX = brX;
        this.brY = brY;
        this.blX = blX;
        this.blY = blY;
        this.strength = strength;
        this.dstTlX = x + tlX * strength;
        this.dstTlY = y + tlY * strength;
        this.dstTrX = x + width + trX * strength;
        this.dstTrY = y + trY * strength;
        this.dstBrX = x + width + brX * strength;
        this.dstBrY = y + height + brY * strength;
        this.dstBlX = x + blX * strength;
        this.dstBlY = y + height + blY * strength;

        double[] matrix = computeUnitSquareHomography(
                dstTlX, dstTlY,
                dstTrX, dstTrY,
                dstBrX, dstBrY,
                dstBlX, dstBlY
        );
        this.h00 = matrix[0];
        this.h01 = matrix[1];
        this.h02 = matrix[2];
        this.h10 = matrix[3];
        this.h11 = matrix[4];
        this.h12 = matrix[5];
        this.h20 = matrix[6];
        this.h21 = matrix[7];
    }

    public static RenderWarp corners(float x,
                                     float y,
                                     float width,
                                     float height,
                                     float tlX,
                                     float tlY,
                                     float trX,
                                     float trY,
                                     float brX,
                                     float brY,
                                     float blX,
                                     float blY) {
        return corners(x, y, width, height, tlX, tlY, trX, trY, brX, brY, blX, blY, 1.0f);
    }

    public static RenderWarp corners(float x,
                                     float y,
                                     float width,
                                     float height,
                                     float tlX,
                                     float tlY,
                                     float trX,
                                     float trY,
                                     float brX,
                                     float brY,
                                     float blX,
                                     float blY,
                                     float strength) {
        if (Math.abs(width) <= 0.0001f || Math.abs(height) <= 0.0001f || Math.abs(strength) <= 0.0001f) {
            return IDENTITY;
        }
        return new RenderWarp(true, x, y, width, height,
                tlX, tlY, trX, trY, brX, brY, blX, blY, clampStrength(strength));
    }

    public static RenderWarp tilt(float x,
                                  float y,
                                  float width,
                                  float height,
                                  float strength,
                                  float hover,
                                  float mouseX,
                                  float mouseY) {
        if (Math.abs(width) <= 0.0001f || Math.abs(height) <= 0.0001f) {
            return IDENTITY;
        }

        float cx = x + width * 0.5f;
        float cy = y + height * 0.5f;
        float dx = clamp((mouseX - cx) / width, -1.0f, 1.0f);
        float dy = clamp((mouseY - cy) / height, -1.0f, 1.0f);
        float amount = Math.max(0.0f, hover) * strength;
        if (Math.abs(amount) <= 0.0001f) {
            return IDENTITY;
        }

        return angles(x, y, width, height, -dx * amount, dy * amount, 0.0f,
                3.5f, 1.0f, 1.0f + Math.max(0.0f, hover) * 0.025f);
    }

    public static RenderWarp angles(float x,
                                    float y,
                                    float width,
                                    float height,
                                    float yawDeg,
                                    float pitchDeg,
                                    float rollDeg) {
        return angles(x, y, width, height, yawDeg, pitchDeg, rollDeg, 3.5f, 1.0f, 1.0f);
    }

    public static RenderWarp perspective(float x,
                                         float y,
                                         float width,
                                         float height,
                                         float yawDeg,
                                         float pitchDeg,
                                         float rollDeg,
                                         float depth,
                                         float perspective,
                                         float scale) {
        return angles(x, y, width, height, yawDeg, pitchDeg, rollDeg, depth, perspective, scale);
    }

    public static RenderWarp angles(float x,
                                    float y,
                                    float width,
                                    float height,
                                    float yawDeg,
                                    float pitchDeg,
                                    float rollDeg,
                                    float depth,
                                    float perspective,
                                    float scale) {
        if (Math.abs(width) <= 0.0001f || Math.abs(height) <= 0.0001f) {
            return IDENTITY;
        }
        if (Math.abs(yawDeg) <= 0.0001f
                && Math.abs(pitchDeg) <= 0.0001f
                && Math.abs(rollDeg) <= 0.0001f
                && Math.abs(scale - 1.0f) <= 0.0001f) {
            return IDENTITY;
        }

        float cx = x + width * 0.5f;
        float cy = y + height * 0.5f;
        float cameraDistance = Math.max(1.0f, Math.max(Math.abs(width), Math.abs(height)) * Math.max(0.75f, depth));
        float persp = Math.max(0.0f, perspective);
        float s = Math.max(0.05f, scale);

        float[] tl = projectCorner(-width * 0.5f, -height * 0.5f, yawDeg, pitchDeg, rollDeg, cameraDistance, persp, s, cx, cy);
        float[] tr = projectCorner(width * 0.5f, -height * 0.5f, yawDeg, pitchDeg, rollDeg, cameraDistance, persp, s, cx, cy);
        float[] br = projectCorner(width * 0.5f, height * 0.5f, yawDeg, pitchDeg, rollDeg, cameraDistance, persp, s, cx, cy);
        float[] bl = projectCorner(-width * 0.5f, height * 0.5f, yawDeg, pitchDeg, rollDeg, cameraDistance, persp, s, cx, cy);

        return corners(
                x, y, width, height,
                tl[0] - x, tl[1] - y,
                tr[0] - (x + width), tr[1] - y,
                br[0] - (x + width), br[1] - (y + height),
                bl[0] - x, bl[1] - (y + height),
                1.0f
        );
    }

    private static double[] computeUnitSquareHomography(float x0, float y0,
                                                        float x1, float y1,
                                                        float x2, float y2,
                                                        float x3, float y3) {
        double dx1 = x1 - x2;
        double dy1 = y1 - y2;
        double dx2 = x3 - x2;
        double dy2 = y3 - y2;
        double dx3 = x0 - x1 + x2 - x3;
        double dy3 = y0 - y1 + y2 - y3;
        double det = dx1 * dy2 - dx2 * dy1;

        double g = 0.0;
        double h = 0.0;
        if (Math.abs(det) > 0.000001) {
            g = (dx3 * dy2 - dx2 * dy3) / det;
            h = (dx1 * dy3 - dx3 * dy1) / det;
        }

        double a = x1 - x0 + g * x1;
        double b = x3 - x0 + h * x3;
        double c = x0;
        double d = y1 - y0 + g * y1;
        double e = y3 - y0 + h * y3;
        double f = y0;
        return new double[]{a, b, c, d, e, f, g, h};
    }

    private static float[] projectCorner(float localX,
                                         float localY,
                                         float yawDeg,
                                         float pitchDeg,
                                         float rollDeg,
                                         float cameraDistance,
                                         float perspective,
                                         float scale,
                                         float centerX,
                                         float centerY) {
        float yaw = (float) Math.toRadians(clamp(yawDeg, -85.0f, 85.0f));
        float pitch = (float) Math.toRadians(clamp(pitchDeg, -85.0f, 85.0f));
        float roll = (float) Math.toRadians(rollDeg);

        float x0 = localX;
        float y0 = localY;
        float z0 = 0.0f;

        float cosX = (float) Math.cos(pitch);
        float sinX = (float) Math.sin(pitch);
        float y1 = y0 * cosX - z0 * sinX;
        float z1 = y0 * sinX + z0 * cosX;
        float x1 = x0;

        float cosY = (float) Math.cos(yaw);
        float sinY = (float) Math.sin(yaw);
        float x2 = x1 * cosY + z1 * sinY;
        float z2 = -x1 * sinY + z1 * cosY;
        float y2 = y1;

        float cosZ = (float) Math.cos(roll);
        float sinZ = (float) Math.sin(roll);
        float x3 = x2 * cosZ - y2 * sinZ;
        float y3 = x2 * sinZ + y2 * cosZ;
        float z3 = z2;

        float denom = Math.max(cameraDistance * 0.15f, cameraDistance + z3 * perspective);
        float p = cameraDistance / denom;
        return new float[]{centerX + x3 * p * scale, centerY + y3 * p * scale};
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clampStrength(float value) {
        return clamp(value, -4.0f, 4.0f);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public boolean active() {
        return active;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public RenderWarp withStrength(float multiplier) {
        if (!active) return this;
        return corners(x, y, width, height, tlX, tlY, trX, trY, brX, brY, blX, blY, strength * multiplier);
    }

    public double mapX(double px, double py) {
        if (!active) return px;
        return map(px, py, true);
    }

    public double mapY(double px, double py) {
        if (!active) return py;
        return map(px, py, false);
    }

    public double interpolationInvW(double px, double py) {
        if (!active) return 1.0;
        double u = (px - x) / width;
        double v = (py - y) / height;
        double denom = h20 * u + h21 * v + 1.0;
        if (Math.abs(denom) <= 0.000001) return 1.0;
        return 1.0 / denom;
    }

    public void map(double px, double py, double[] out) {
        if (out == null || out.length < 2) return;
        if (!active) {
            out[0] = px;
            out[1] = py;
            return;
        }
        mapProjective(px, py, out);
    }

    /**
     * Maps an axis-aligned source rectangle and returns the conservative axis-aligned bounds of
     * its warped quadrilateral as {@code [x, y, width, height]}.
     */
    public void mapBounds(double sourceX, double sourceY, double sourceWidth, double sourceHeight, double[] out) {
        if (out == null || out.length < 4) return;
        if (!active) {
            out[0] = sourceX;
            out[1] = sourceY;
            out[2] = sourceWidth;
            out[3] = sourceHeight;
            return;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            double px = (corner == 1 || corner == 2) ? sourceX + sourceWidth : sourceX;
            double py = corner >= 2 ? sourceY + sourceHeight : sourceY;
            double u = (px - x) / width;
            double v = (py - y) / height;
            double denom = h20 * u + h21 * v + 1.0;
            double mappedX = px;
            double mappedY = py;
            if (Math.abs(denom) > 0.000001) {
                mappedX = (h00 * u + h01 * v + h02) / denom;
                mappedY = (h10 * u + h11 * v + h12) / denom;
            }
            minX = Math.min(minX, mappedX);
            minY = Math.min(minY, mappedY);
            maxX = Math.max(maxX, mappedX);
            maxY = Math.max(maxY, mappedY);
        }
        out[0] = minX;
        out[1] = minY;
        out[2] = Math.max(0.0, maxX - minX);
        out[3] = Math.max(0.0, maxY - minY);
    }

    public void originalCorner(int corner, double[] out) {
        if (out == null || out.length < 2) return;
        switch (corner) {
            case 0 -> {
                out[0] = x;
                out[1] = y;
            }
            case 1 -> {
                out[0] = x + width;
                out[1] = y;
            }
            case 2 -> {
                out[0] = x + width;
                out[1] = y + height;
            }
            case 3 -> {
                out[0] = x;
                out[1] = y + height;
            }
            default -> {
                out[0] = x;
                out[1] = y;
            }
        }
    }

    public void warpedCorner(int corner, double[] out) {
        if (out == null || out.length < 2) return;
        switch (corner) {
            case 0 -> {
                out[0] = dstTlX;
                out[1] = dstTlY;
            }
            case 1 -> {
                out[0] = dstTrX;
                out[1] = dstTrY;
            }
            case 2 -> {
                out[0] = dstBrX;
                out[1] = dstBrY;
            }
            case 3 -> {
                out[0] = dstBlX;
                out[1] = dstBlY;
            }
            default -> {
                out[0] = dstTlX;
                out[1] = dstTlY;
            }
        }
    }

    private double map(double px, double py, boolean xAxis) {
        double[] out = new double[2];
        mapProjective(px, py, out);
        return xAxis ? out[0] : out[1];
    }

    private void mapProjective(double px, double py, double[] out) {
        double u = (px - x) / width;
        double v = (py - y) / height;
        double denom = h20 * u + h21 * v + 1.0;
        if (Math.abs(denom) <= 0.000001) {
            out[0] = px;
            out[1] = py;
            return;
        }
        out[0] = (h00 * u + h01 * v + h02) / denom;
        out[1] = (h10 * u + h11 * v + h12) / denom;
    }
}
