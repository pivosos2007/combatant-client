/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

import combatant.client.render.engine.uniform.MeshBuilder;

/**
 * Low-level mesh tessellation, gradient and path geometry used by Renderer2D.
 * Kept outside the public drawing facade so API methods remain easy to navigate.
 */
public final class UiMeshGeometry {
    private UiMeshGeometry() {
    }

    public static void computeLinearGradientColors(float width, float height,
                                                    int startArgb, int endArgb,
                                                    float angleDeg, float offsetPx,
                                                    int[] out) {
        float angle = (float) Math.toRadians(angleDeg);
        float dirX = (float) Math.cos(angle);
        float dirY = (float) Math.sin(angle);
    
        float p0 = 0.0f;
        float p1 = width * dirX;
        float p2 = height * dirY;
        float p3 = width * dirX + height * dirY;
    
        float minProj = Math.min(Math.min(p0, p1), Math.min(p2, p3));
        float maxProj = Math.max(Math.max(p0, p1), Math.max(p2, p3));
        float range = Math.max(0.0001f, maxProj - minProj);
    
        float t0 = clamp01((p0 + offsetPx - minProj) / range);
        float t1 = clamp01((p1 + offsetPx - minProj) / range);
        float t2 = clamp01((p3 + offsetPx - minProj) / range);
        float t3 = clamp01((p2 + offsetPx - minProj) / range);
    
        out[0] = mixArgb(startArgb, endArgb, t0);
        out[1] = mixArgb(startArgb, endArgb, t1);
        out[2] = mixArgb(startArgb, endArgb, t2);
        out[3] = mixArgb(startArgb, endArgb, t3);
    }

    private static int linearGradientColorAt(double x, double y, double width, double height,
                                             int startArgb, int endArgb, float angleDeg, float offsetPx) {
        float angle = (float) Math.toRadians(angleDeg);
        float dirX = (float) Math.cos(angle);
        float dirY = (float) Math.sin(angle);
    
        float w = (float) width;
        float h = (float) height;
        float p0 = 0.0f;
        float p1 = w * dirX;
        float p2 = h * dirY;
        float p3 = w * dirX + h * dirY;
        float minProj = Math.min(Math.min(p0, p1), Math.min(p2, p3));
        float maxProj = Math.max(Math.max(p0, p1), Math.max(p2, p3));
        float range = Math.max(0.0001f, maxProj - minProj);
        float p = (float) x * dirX + (float) y * dirY;
        return mixArgb(startArgb, endArgb, clamp01((p + offsetPx - minProj) / range));
    }

    public static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }

    public static float packLiquidGlassPayload(float distortPx, float squirclePower, float blurAlpha) {
        // This argument has historically meant rounded-corner smoothness. Keep
        // that API stable; whole-box squircles must opt in through the boolean overload.
        return packLiquidGlassPayload(distortPx, squirclePower, blurAlpha, false);
    }

    public static float packLiquidGlassPayload(float distortPx, float squirclePower, float blurAlpha,
                                               boolean squircle) {
        float distort = Math.max(0.0f, Math.min(0.35f, distortPx));
        float smoothness = squirclePower < 1.5f
                ? 2.0f
                : Math.max(2.0f, Math.min(16.0f, squirclePower));
        float blurBucket = Math.round(clamp01(blurAlpha) * 100.0f);
        if (squircle) {
            float exponentBucket = Math.round(smoothness * 100.0f);
            return 200_000.0f + exponentBucket * 128.0f + blurBucket + distort;
        }
        return smoothness * 10000.0f + blurBucket + distort;
    }

    public static float clampRoundedRadius(float radius, double width, double height) {
        float maxRadius = maxRoundedRadius(width, height);
        if (radius < 0.0f) return 0.0f;
        return Math.min(radius, maxRadius);
    }

    public static float clampChamfer(double chamfer, double width, double height) {
        float max = maxRoundedRadius(width, height);
        if (chamfer < 0.0) return 0.0f;
        return (float) Math.min(chamfer, max);
    }

    public static float clampChamferAxis(double chamfer, double length) {
        double max = Math.abs(length);
        if (chamfer < 0.0 || max <= 0.0) return 0.0f;
        return (float) Math.min(chamfer, max);
    }

    private static float maxRoundedRadius(double width, double height) {
        double w = Math.abs(width);
        double h = Math.abs(height);
        if (w <= 0.0 || h <= 0.0) {
            return 0.0f;
        }
        return (float) (Math.min(w, h) * 0.5);
    }

    public static void normalizeCornerRadii(double width, double height,
                                             float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                             float[] out) {
        if (out == null || out.length < 4) {
            throw new IllegalArgumentException("Output array must have length >= 4");
        }
    
        float w = (float) Math.abs(width);
        float h = (float) Math.abs(height);
        if (w <= 0.0f || h <= 0.0f) {
            out[0] = 0.0f;
            out[1] = 0.0f;
            out[2] = 0.0f;
            out[3] = 0.0f;
            return;
        }
    
        float maxRadius = Math.min(w, h) * 0.5f;
        float tl = Math.max(0.0f, Math.min(radiusTL, maxRadius));
        float tr = Math.max(0.0f, Math.min(radiusTR, maxRadius));
        float br = Math.max(0.0f, Math.min(radiusBR, maxRadius));
        float bl = Math.max(0.0f, Math.min(radiusBL, maxRadius));
    
        float scale = 1.0f;
        float top = tl + tr;
        if (top > w && top > 0.0f) {
            scale = Math.min(scale, w / top);
        }
        float bottom = bl + br;
        if (bottom > w && bottom > 0.0f) {
            scale = Math.min(scale, w / bottom);
        }
        float left = tl + bl;
        if (left > h && left > 0.0f) {
            scale = Math.min(scale, h / left);
        }
        float right = tr + br;
        if (right > h && right > 0.0f) {
            scale = Math.min(scale, h / right);
        }
    
        if (scale < 1.0f) {
            tl *= scale;
            tr *= scale;
            br *= scale;
            bl *= scale;
        }
    
        out[0] = tl;
        out[1] = tr;
        out[2] = br;
        out[3] = bl;
    }

    public static void normalizeChamfers(double width, double height,
                                          float chamferTL, float chamferTR, float chamferBR, float chamferBL,
                                          float[] out) {
        if (out == null || out.length < 4) {
            throw new IllegalArgumentException("Output array must have length >= 4");
        }
    
        float w = (float) Math.abs(width);
        float h = (float) Math.abs(height);
        if (w <= 0.0f || h <= 0.0f) {
            out[0] = 0.0f;
            out[1] = 0.0f;
            out[2] = 0.0f;
            out[3] = 0.0f;
            return;
        }
    
        float max = Math.min(w, h);
        float tl = Math.max(0.0f, Math.min(chamferTL, max));
        float tr = Math.max(0.0f, Math.min(chamferTR, max));
        float br = Math.max(0.0f, Math.min(chamferBR, max));
        float bl = Math.max(0.0f, Math.min(chamferBL, max));
    
        float scale = 1.0f;
        float top = tl + tr;
        if (top > w && top > 0.0f) {
            scale = Math.min(scale, w / top);
        }
        float bottom = bl + br;
        if (bottom > w && bottom > 0.0f) {
            scale = Math.min(scale, w / bottom);
        }
        float left = tl + bl;
        if (left > h && left > 0.0f) {
            scale = Math.min(scale, h / left);
        }
        float right = tr + br;
        if (right > h && right > 0.0f) {
            scale = Math.min(scale, h / right);
        }
    
        if (scale < 1.0f) {
            tl *= scale;
            tr *= scale;
            br *= scale;
            bl *= scale;
        }
    
        out[0] = tl;
        out[1] = tr;
        out[2] = br;
        out[3] = bl;
    }

    public static float normalizeDegrees(float angleDeg) {
        float out = angleDeg % 360.0f;
        return out < 0.0f ? out + 360.0f : out;
    }

    public static void appendColoredQuad(MeshBuilder mesh,
                                          double x, double y, double width, double height,
                                          int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        mesh.ensureQuadCapacity();
    
        int tlA = (cTopLeft >>> 24) & 0xFF;
        int tlR = (cTopLeft >>> 16) & 0xFF;
        int tlG = (cTopLeft >>> 8) & 0xFF;
        int tlB = cTopLeft & 0xFF;
        int trA = (cTopRight >>> 24) & 0xFF;
        int trR = (cTopRight >>> 16) & 0xFF;
        int trG = (cTopRight >>> 8) & 0xFF;
        int trB = cTopRight & 0xFF;
        int brA = (cBottomRight >>> 24) & 0xFF;
        int brR = (cBottomRight >>> 16) & 0xFF;
        int brG = (cBottomRight >>> 8) & 0xFF;
        int brB = cBottomRight & 0xFF;
        int blA = (cBottomLeft >>> 24) & 0xFF;
        int blR = (cBottomLeft >>> 16) & 0xFF;
        int blG = (cBottomLeft >>> 8) & 0xFF;
        int blB = cBottomLeft & 0xFF;
    
        int i1 = mesh.vec2(x, y).color(tlR, tlG, tlB, tlA).next();
        int i2 = mesh.vec2(x, y + height).color(blR, blG, blB, blA).next();
        int i3 = mesh.vec2(x + width, y + height).color(brR, brG, brB, brA).next();
        int i4 = mesh.vec2(x + width, y).color(trR, trG, trB, trA).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void emitUiPolygonTriangle(MeshBuilder mesh, int a, int b, int c, boolean reverseForUiCull) {
        if (reverseForUiCull) {
            mesh.triangle(a, c, b);
        } else {
            mesh.triangle(a, b, c);
        }
    }

    public static void appendPolygon(MeshBuilder mesh, double[] points, int pointCount,
                                      int[] indices, int[] vertexIds, int argb) {
        int safeCount = Math.min(pointCount, Math.min(points.length / 2, Math.min(indices.length, vertexIds.length)));
        int triangles = safeCount - 2;
        if (triangles <= 0) return;
        mesh.ensureCapacity(safeCount, triangles * 3);
    
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
    
        for (int i = 0; i < safeCount; i++) {
            indices[i] = i;
            vertexIds[i] = mesh.vec2(points[i * 2], points[i * 2 + 1]).color(r, g, b, a).next();
        }
    
        double area = polygonArea(points, safeCount);
        boolean reverseForUiCull = area >= 0.0;
        if (isConvex(points, safeCount)) {
            for (int i = 2; i < safeCount; i++) {
                emitUiPolygonTriangle(mesh, vertexIds[0], vertexIds[i - 1], vertexIds[i], reverseForUiCull);
            }
            return;
        }
    
        int remaining = safeCount;
        double winding = area >= 0.0 ? 1.0 : -1.0;
        int guard = safeCount * safeCount;
        while (remaining > 2 && guard-- > 0) {
            boolean clipped = false;
            for (int i = 0; i < remaining; i++) {
                int prev = i == 0 ? remaining - 1 : i - 1;
                int next = i + 1 == remaining ? 0 : i + 1;
                if (!isEar(points, indices, remaining, prev, i, next, winding)) continue;
                emitUiPolygonTriangle(mesh, vertexIds[indices[prev]], vertexIds[indices[i]], vertexIds[indices[next]], reverseForUiCull);
                for (int j = i; j < remaining - 1; j++) {
                    indices[j] = indices[j + 1];
                }
                remaining--;
                clipped = true;
                break;
            }
            if (!clipped) {
                for (int i = 2; i < remaining; i++) {
                    emitUiPolygonTriangle(mesh, vertexIds[indices[0]], vertexIds[indices[i - 1]], vertexIds[indices[i]], reverseForUiCull);
                }
                return;
            }
        }
    }

    public static void appendPolygonGradient(MeshBuilder mesh, double[] points, int pointCount,
                                              int[] indices, int[] vertexIds,
                                              double x, double y, double width, double height,
                                              int startArgb, int endArgb, float angleDeg, float offsetPx) {
        int safeCount = Math.min(pointCount, Math.min(points.length / 2, Math.min(indices.length, vertexIds.length)));
        int triangles = safeCount - 2;
        if (triangles <= 0) return;
        mesh.ensureCapacity(safeCount, triangles * 3);
    
        for (int i = 0; i < safeCount; i++) {
            indices[i] = i;
            int argb = linearGradientColorAt(
                    points[i * 2] - x,
                    points[i * 2 + 1] - y,
                    width,
                    height,
                    startArgb,
                    endArgb,
                    angleDeg,
                    offsetPx
            );
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            vertexIds[i] = mesh.vec2(points[i * 2], points[i * 2 + 1]).color(r, g, b, a).next();
        }
    
        double area = polygonArea(points, safeCount);
        boolean reverseForUiCull = area >= 0.0;
        if (isConvex(points, safeCount)) {
            for (int i = 2; i < safeCount; i++) {
                emitUiPolygonTriangle(mesh, vertexIds[0], vertexIds[i - 1], vertexIds[i], reverseForUiCull);
            }
            return;
        }
    
        int remaining = safeCount;
        double winding = area >= 0.0 ? 1.0 : -1.0;
        int guard = safeCount * safeCount;
        while (remaining > 2 && guard-- > 0) {
            boolean clipped = false;
            for (int i = 0; i < remaining; i++) {
                int prev = i == 0 ? remaining - 1 : i - 1;
                int next = i + 1 == remaining ? 0 : i + 1;
                if (!isEar(points, indices, remaining, prev, i, next, winding)) continue;
                emitUiPolygonTriangle(mesh, vertexIds[indices[prev]], vertexIds[indices[i]], vertexIds[indices[next]], reverseForUiCull);
                for (int j = i; j < remaining - 1; j++) {
                    indices[j] = indices[j + 1];
                }
                remaining--;
                clipped = true;
                break;
            }
            if (!clipped) {
                for (int i = 2; i < remaining; i++) {
                    emitUiPolygonTriangle(mesh, vertexIds[indices[0]], vertexIds[indices[i - 1]], vertexIds[indices[i]], reverseForUiCull);
                }
                return;
            }
        }
    }

    public static void appendPolyline(MeshBuilder mesh, double[] points, int pointCount,
                                       double thickness, boolean closed, int argb, boolean roundCapsAndJoins) {
        int safeCount = Math.min(pointCount, points.length / 2);
        int segments = closed ? safeCount : safeCount - 1;
        if (segments <= 0) return;
        mesh.ensureCapacity(segments * 4, segments * 6);
        for (int i = 0; i < segments; i++) {
            int next = i + 1;
            if (next >= safeCount) next = 0;
            appendThickSegment(
                    mesh,
                    points[i * 2],
                    points[i * 2 + 1],
                    points[next * 2],
                    points[next * 2 + 1],
                    thickness,
                    argb
            );
        }
        if (!roundCapsAndJoins) return;
        double radius = thickness * 0.5;
        if (closed) {
            for (int i = 0; i < safeCount; i++) {
                appendDisk(mesh, points[i * 2], points[i * 2 + 1], radius, 10, argb);
            }
            return;
        }
        appendDisk(mesh, points[0], points[1], radius, 10, argb);
        for (int i = 1; i < safeCount - 1; i++) {
            appendDisk(mesh, points[i * 2], points[i * 2 + 1], radius, 10, argb);
        }
        appendDisk(mesh, points[(safeCount - 1) * 2], points[(safeCount - 1) * 2 + 1], radius, 10, argb);
    }

    public static void appendPolylineGradient(MeshBuilder mesh, double[] points, int pointCount,
                                               double thickness, boolean closed,
                                               int startArgb, int endArgb,
                                               boolean roundCapsAndJoins) {
        int safeCount = Math.min(pointCount, points.length / 2);
        int segments = closed ? safeCount : safeCount - 1;
        if (segments <= 0) return;
        mesh.ensureCapacity(segments * 4, segments * 6);
        for (int i = 0; i < segments; i++) {
            int next = i + 1;
            if (next >= safeCount) next = 0;
            float t0 = segments <= 1 ? 0.0f : (float) i / (float) segments;
            float t1 = segments <= 1 ? 1.0f : (float) (i + 1) / (float) segments;
            appendThickSegmentGradient(
                    mesh,
                    points[i * 2],
                    points[i * 2 + 1],
                    points[next * 2],
                    points[next * 2 + 1],
                    thickness,
                    mixArgb(startArgb, endArgb, clamp01(t0)),
                    mixArgb(startArgb, endArgb, clamp01(t1))
            );
        }
        if (!roundCapsAndJoins) return;
        double radius = thickness * 0.5;
        for (int i = 0; i < safeCount; i++) {
            float t = safeCount <= 1 ? 0.0f : (float) i / (float) (safeCount - 1);
            appendDisk(mesh, points[i * 2], points[i * 2 + 1], radius, 10, mixArgb(startArgb, endArgb, clamp01(t)));
        }
    }

    public static void appendPolylineLinearGradient(MeshBuilder mesh, double[] points, int pointCount,
                                                     double thickness, boolean closed,
                                                     double x, double y, double width, double height,
                                                     int startArgb, int endArgb, float angleDeg, float offsetPx,
                                                     boolean roundCapsAndJoins) {
        int safeCount = Math.min(pointCount, points.length / 2);
        int segments = closed ? safeCount : safeCount - 1;
        if (segments <= 0) return;
        mesh.ensureCapacity(segments * 4, segments * 6);
        for (int i = 0; i < segments; i++) {
            int next = i + 1;
            if (next >= safeCount) next = 0;
            int c0 = linearGradientColorAt(points[i * 2] - x, points[i * 2 + 1] - y,
                    width, height, startArgb, endArgb, angleDeg, offsetPx);
            int c1 = linearGradientColorAt(points[next * 2] - x, points[next * 2 + 1] - y,
                    width, height, startArgb, endArgb, angleDeg, offsetPx);
            appendThickSegmentGradient(
                    mesh,
                    points[i * 2],
                    points[i * 2 + 1],
                    points[next * 2],
                    points[next * 2 + 1],
                    thickness,
                    c0,
                    c1
            );
        }
        if (!roundCapsAndJoins) return;
        double radius = thickness * 0.5;
        for (int i = 0; i < safeCount; i++) {
            int c = linearGradientColorAt(points[i * 2] - x, points[i * 2 + 1] - y,
                    width, height, startArgb, endArgb, angleDeg, offsetPx);
            appendDisk(mesh, points[i * 2], points[i * 2 + 1], radius, 10, c);
        }
    }

    private static void appendThickSegment(MeshBuilder mesh,
                                           double x1,
                                           double y1,
                                           double x2,
                                           double y2,
                                           double thickness,
                                           int argb) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len <= 0.0001) return;
        double half = thickness * 0.5;
        double nx = -dy / len * half;
        double ny = dx / len * half;
    
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
    
        int i1 = mesh.vec2(x1 - nx, y1 - ny).color(r, g, b, a).next();
        int i2 = mesh.vec2(x1 + nx, y1 + ny).color(r, g, b, a).next();
        int i3 = mesh.vec2(x2 + nx, y2 + ny).color(r, g, b, a).next();
        int i4 = mesh.vec2(x2 - nx, y2 - ny).color(r, g, b, a).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void appendThickSegmentGradient(MeshBuilder mesh,
                                                   double x1,
                                                   double y1,
                                                   double x2,
                                                   double y2,
                                                   double thickness,
                                                   int startArgb,
                                                   int endArgb) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len <= 0.0001) return;
        double half = thickness * 0.5;
        double nx = -dy / len * half;
        double ny = dx / len * half;
    
        int sa = (startArgb >>> 24) & 0xFF;
        int sr = (startArgb >>> 16) & 0xFF;
        int sg = (startArgb >>> 8) & 0xFF;
        int sb = startArgb & 0xFF;
        int ea = (endArgb >>> 24) & 0xFF;
        int er = (endArgb >>> 16) & 0xFF;
        int eg = (endArgb >>> 8) & 0xFF;
        int eb = endArgb & 0xFF;
    
        int i1 = mesh.vec2(x1 - nx, y1 - ny).color(sr, sg, sb, sa).next();
        int i2 = mesh.vec2(x1 + nx, y1 + ny).color(sr, sg, sb, sa).next();
        int i3 = mesh.vec2(x2 + nx, y2 + ny).color(er, eg, eb, ea).next();
        int i4 = mesh.vec2(x2 - nx, y2 - ny).color(er, eg, eb, ea).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void appendDisk(MeshBuilder mesh,
                                   double cx,
                                   double cy,
                                   double radius,
                                   int segments,
                                   int argb) {
        if (radius <= 0.0) return;
        int safeSegments = Math.max(6, Math.min(24, segments));
        mesh.ensureCapacity(safeSegments + 1, safeSegments * 3);
    
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
    
        int center = mesh.vec2(cx, cy).color(r, g, b, a).next();
        int first = -1;
        int prev = -1;
        for (int i = 0; i < safeSegments; i++) {
            double angle = Math.PI * 2.0 * i / safeSegments;
            int vertex = mesh.vec2(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius)
                    .color(r, g, b, a)
                    .next();
            if (first < 0) {
                first = vertex;
            } else {
                mesh.triangle(center, prev, vertex);
            }
            prev = vertex;
        }
        if (first >= 0 && prev >= 0) {
            mesh.triangle(center, prev, first);
        }
    }

    public static int buildRoundedProgressRect(double[] out,
                                                double[] work,
                                                double x,
                                                double y,
                                                double width,
                                                double height,
                                                double radius,
                                                double clipX,
                                                boolean fromRight) {
        int sourceCount = buildRoundedRectPolygon(work, x, y, width, height, radius);
        if (sourceCount < 3) return 0;
        return clipPolygonVertical(work, sourceCount, out, clipX, fromRight);
    }

    public static int buildRoundedRectPolygon(double[] out,
                                               double x,
                                               double y,
                                               double width,
                                               double height,
                                               double radius) {
        double w = Math.max(0.0, width);
        double h = Math.max(0.0, height);
        if (w <= 0.0 || h <= 0.0) return 0;
        double x2 = x + w;
        double y2 = y + h;
        double r = Math.max(0.0, Math.min(Math.min(w, h) * 0.5, radius));
        int[] count = {0};
        if (r <= 0.001) {
            add(out, out.length / 2, count, x, y);
            add(out, out.length / 2, count, x2, y);
            add(out, out.length / 2, count, x2, y2);
            add(out, out.length / 2, count, x, y2);
            return count[0];
        }
    
        int segments = Math.max(4, Math.min(10, (int) Math.ceil(r * 1.35)));
        add(out, out.length / 2, count, x + r, y);
        add(out, out.length / 2, count, x2 - r, y);
        addArc(out, out.length / 2, count, x2 - r, y + r, r, -90.0, 0.0, segments);
        add(out, out.length / 2, count, x2, y2 - r);
        addArc(out, out.length / 2, count, x2 - r, y2 - r, r, 0.0, 90.0, segments);
        add(out, out.length / 2, count, x + r, y2);
        addArc(out, out.length / 2, count, x + r, y2 - r, r, 90.0, 180.0, segments);
        add(out, out.length / 2, count, x, y + r);
        addArc(out, out.length / 2, count, x + r, y + r, r, 180.0, 270.0, segments);
        return removeClosingDuplicate(out, count[0]);
    }

    private static int removeClosingDuplicate(double[] points, int count) {
        if (points == null || count < 2) return count;
        double firstX = points[0];
        double firstY = points[1];
        double lastX = points[(count - 1) * 2];
        double lastY = points[(count - 1) * 2 + 1];
        if (Math.abs(firstX - lastX) < 0.0001 && Math.abs(firstY - lastY) < 0.0001) {
            return count - 1;
        }
        return count;
    }

    private static void add(double[] out, int max, int[] count, double x, double y) {
        if (out == null || count == null || count.length == 0) return;
        int index = count[0];
        if (index < 0 || index >= max) return;
        int base = index * 2;
        if (base + 1 >= out.length) return;
        out[base] = x;
        out[base + 1] = y;
        count[0] = index + 1;
    }

    private static void addArc(double[] out,
                               int max,
                               int[] count,
                               double cx,
                               double cy,
                               double radius,
                               double startDeg,
                               double endDeg,
                               int segments) {
        int safeSegments = Math.max(1, segments);
        for (int i = 1; i <= safeSegments; i++) {
            double t = i / (double) safeSegments;
            double a = Math.toRadians(startDeg + (endDeg - startDeg) * t);
            add(out, max, count, cx + Math.cos(a) * radius, cy + Math.sin(a) * radius);
        }
    }

    private static int clipPolygonVertical(double[] source,
                                           int sourceCount,
                                           double[] out,
                                           double clipX,
                                           boolean keepRight) {
        int maxOut = out.length / 2;
        int[] count = {0};
        if (source == null || out == null || sourceCount < 3 || maxOut <= 0) return 0;
    
        for (int i = 0; i < sourceCount; i++) {
            int prev = i == 0 ? sourceCount - 1 : i - 1;
            double sx = source[prev * 2];
            double sy = source[prev * 2 + 1];
            double ex = source[i * 2];
            double ey = source[i * 2 + 1];
            boolean sInside = keepRight ? sx >= clipX - 0.0001 : sx <= clipX + 0.0001;
            boolean eInside = keepRight ? ex >= clipX - 0.0001 : ex <= clipX + 0.0001;
    
            if (sInside && eInside) {
                add(out, maxOut, count, ex, ey);
            } else if (sInside) {
                addVerticalIntersection(out, maxOut, count, sx, sy, ex, ey, clipX);
            } else if (eInside) {
                addVerticalIntersection(out, maxOut, count, sx, sy, ex, ey, clipX);
                add(out, maxOut, count, ex, ey);
            }
        }
        return count[0];
    }

    private static void addVerticalIntersection(double[] out,
                                                int max,
                                                int[] count,
                                                double sx,
                                                double sy,
                                                double ex,
                                                double ey,
                                                double clipX) {
        double dx = ex - sx;
        if (Math.abs(dx) <= 0.000001) {
            add(out, max, count, clipX, ey);
            return;
        }
        double t = (clipX - sx) / dx;
        t = Math.max(0.0, Math.min(1.0, t));
        add(out, max, count, clipX, sy + (ey - sy) * t);
    }

    public static void roundedRectAnchor(double[] out,
                                          int offset,
                                          double x,
                                          double y,
                                          double width,
                                          double height,
                                          double radius,
                                          double targetX,
                                          double targetY) {
        double cx = x + width * 0.5;
        double cy = y + height * 0.5;
        double halfW = Math.abs(width) * 0.5;
        double halfH = Math.abs(height) * 0.5;
        if (halfW <= 0.0 || halfH <= 0.0) {
            out[offset] = cx;
            out[offset + 1] = cy;
            return;
        }
    
        double dx = targetX - cx;
        double dy = targetY - cy;
        if (Math.abs(dx) <= 0.0001 && Math.abs(dy) <= 0.0001) {
            out[offset] = cx;
            out[offset + 1] = cy;
            return;
        }
    
        double r = Math.max(0.0, Math.min(Math.min(halfW, halfH), radius));
        double innerW = halfW - r;
        double innerH = halfH - r;
        double bestT = Double.POSITIVE_INFINITY;
    
        if (Math.abs(dx) > 0.0001) {
            double tx = (dx > 0.0 ? halfW : -halfW) / dx;
            double yy = dy * tx;
            if (tx > 0.0 && Math.abs(yy) <= innerH + 0.0001) {
                bestT = Math.min(bestT, tx);
            }
        }
        if (Math.abs(dy) > 0.0001) {
            double ty = (dy > 0.0 ? halfH : -halfH) / dy;
            double xx = dx * ty;
            if (ty > 0.0 && Math.abs(xx) <= innerW + 0.0001) {
                bestT = Math.min(bestT, ty);
            }
        }
        if (r > 0.0) {
            double circleX = dx >= 0.0 ? innerW : -innerW;
            double circleY = dy >= 0.0 ? innerH : -innerH;
            double a = dx * dx + dy * dy;
            double b = -2.0 * (dx * circleX + dy * circleY);
            double c = circleX * circleX + circleY * circleY - r * r;
            double discriminant = b * b - 4.0 * a * c;
            if (a > 0.0001 && discriminant >= 0.0) {
                double sqrt = Math.sqrt(discriminant);
                double t0 = (-b - sqrt) / (2.0 * a);
                double t1 = (-b + sqrt) / (2.0 * a);
                if (t0 > 0.0) bestT = Math.min(bestT, t0);
                if (t1 > 0.0) bestT = Math.min(bestT, t1);
            }
        }
        if (!Double.isFinite(bestT)) {
            double tx = Math.abs(dx) > 0.0001 ? halfW / Math.abs(dx) : Double.POSITIVE_INFINITY;
            double ty = Math.abs(dy) > 0.0001 ? halfH / Math.abs(dy) : Double.POSITIVE_INFINITY;
            bestT = Math.min(tx, ty);
        }
    
        out[offset] = cx + dx * bestT;
        out[offset + 1] = cy + dy * bestT;
    }

    private static int buildChamferedRect(double[] out,
                                          double x,
                                          double y,
                                          double width,
                                          double height,
                                          double chamfer) {
        double c = Math.max(0.0, Math.min(Math.min(Math.abs(width), Math.abs(height)) * 0.5, chamfer));
        double x2 = x + width;
        double y2 = y + height;
        out[0] = x + c;
        out[1] = y;
        out[2] = x2 - c;
        out[3] = y;
        out[4] = x2;
        out[5] = y + c;
        out[6] = x2;
        out[7] = y2 - c;
        out[8] = x2 - c;
        out[9] = y2;
        out[10] = x + c;
        out[11] = y2;
        out[12] = x;
        out[13] = y2 - c;
        out[14] = x;
        out[15] = y + c;
        return 8;
    }

    public static int buildNotchedRect(double[] out,
                                        double x,
                                        double y,
                                        double width,
                                        double height,
                                        double notchWidth,
                                        double notchDepth) {
        double nw = Math.max(0.0, Math.min(Math.abs(width) * 0.5, notchWidth));
        double nd = Math.max(0.0, Math.min(Math.abs(height) * 0.5, notchDepth));
        double x2 = x + width;
        double y2 = y + height;
        double cx = x + width * 0.5;
        out[0] = x;
        out[1] = y;
        out[2] = cx - nw;
        out[3] = y;
        out[4] = cx - nw;
        out[5] = y + nd;
        out[6] = cx + nw;
        out[7] = y + nd;
        out[8] = cx + nw;
        out[9] = y;
        out[10] = x2;
        out[11] = y;
        out[12] = x2;
        out[13] = y2;
        out[14] = x;
        out[15] = y2;
        return 8;
    }

    public static int buildBezier(double[] out,
                                   double x1,
                                   double y1,
                                   double cx1,
                                   double cy1,
                                   double cx2,
                                   double cy2,
                                   double x2,
                                   double y2,
                                   int segments) {
        int count = Math.max(2, Math.min(32, segments + 1));
        for (int i = 0; i < count; i++) {
            double t = (double) i / (count - 1);
            double u = 1.0 - t;
            double x = u * u * u * x1
                    + 3.0 * u * u * t * cx1
                    + 3.0 * u * t * t * cx2
                    + t * t * t * x2;
            double y = u * u * u * y1
                    + 3.0 * u * u * t * cy1
                    + 3.0 * u * t * t * cy2
                    + t * t * t * y2;
            out[i * 2] = x;
            out[i * 2 + 1] = y;
        }
        return count;
    }

    private static boolean isConvex(double[] points, int count) {
        double sign = 0.0;
        for (int i = 0; i < count; i++) {
            int i0 = i;
            int i1 = (i + 1) % count;
            int i2 = (i + 2) % count;
            double cross = cross(points, i0, i1, i2);
            if (Math.abs(cross) <= 0.0001) continue;
            if (sign == 0.0) {
                sign = Math.signum(cross);
            } else if (Math.signum(cross) != sign) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEar(double[] points, int[] vertexIds, int remaining, int prev, int i, int next, double winding) {
        int p = vertexIds[prev];
        int c = vertexIds[i];
        int n = vertexIds[next];
        if (crossByVertex(points, p, c, n) * winding <= 0.0001) return false;
        double ax = points[(p) * 2];
        double ay = points[(p) * 2 + 1];
        double bx = points[(c) * 2];
        double by = points[(c) * 2 + 1];
        double cx = points[(n) * 2];
        double cy = points[(n) * 2 + 1];
        for (int j = 0; j < remaining; j++) {
            if (j == prev || j == i || j == next) continue;
            int v = vertexIds[j];
            if (pointInTriangle(points[v * 2], points[v * 2 + 1], ax, ay, bx, by, cx, cy, winding)) {
                return false;
            }
        }
        return true;
    }

    private static double cross(double[] points, int i0, int i1, int i2) {
        double ax = points[i0 * 2];
        double ay = points[i0 * 2 + 1];
        double bx = points[i1 * 2];
        double by = points[i1 * 2 + 1];
        double cx = points[i2 * 2];
        double cy = points[i2 * 2 + 1];
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    private static double crossByVertex(double[] points, int i0, int i1, int i2) {
        return cross(points, i0, i1, i2);
    }

    private static double polygonArea(double[] points, int count) {
        double area = 0.0;
        for (int i = 0; i < count; i++) {
            int j = i + 1 == count ? 0 : i + 1;
            area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1];
        }
        return area * 0.5;
    }

    private static boolean pointInTriangle(double px, double py,
                                           double ax, double ay,
                                           double bx, double by,
                                           double cx, double cy,
                                           double winding) {
        double c1 = (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        double c2 = (cx - bx) * (py - by) - (cy - by) * (px - bx);
        double c3 = (ax - cx) * (py - cy) - (ay - cy) * (px - cx);
        return c1 * winding >= 0.0 && c2 * winding >= 0.0 && c3 * winding >= 0.0;
    }

    public static int mixArgb(int start, int end, float t) {
        int sa = (start >>> 24) & 0xFF;
        int sr = (start >>> 16) & 0xFF;
        int sg = (start >>> 8) & 0xFF;
        int sb = start & 0xFF;
    
        int ea = (end >>> 24) & 0xFF;
        int er = (end >>> 16) & 0xFF;
        int eg = (end >>> 8) & 0xFF;
        int eb = end & 0xFF;
    
        int a = (int) (sa + (ea - sa) * t + 0.5f);
        int r = (int) (sr + (er - sr) * t + 0.5f);
        int g = (int) (sg + (eg - sg) * t + 0.5f);
        int b = (int) (sb + (eb - sb) * t + 0.5f);
    
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
