/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.area;

import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Renderer3D implementation of WorldAreaPreviewDescriptor for the current renderer. */
public final class CurrentWorldAreaPreviewRenderer {
    private static final double TAU = Math.PI * 2.0;

    private CurrentWorldAreaPreviewRenderer() {
    }

    public static void render(Renderer3D renderer, WorldAreaPreviewDescriptor descriptor) {
        if (renderer == null || descriptor == null) return;

        Renderer3D.DepthMode depthMode = descriptor.depthTest()
                ? Renderer3D.DepthMode.PRE_DEPTH
                : Renderer3D.DepthMode.MAIN;
        MeshBuilder fill = descriptor.fillOpacity() > 0.0f
                ? renderer.batch(
                descriptor.depthTest() ? CombatantRenderPipelines.WORLD_COLORED_DEPTH : CombatantRenderPipelines.WORLD_COLORED,
                depthMode
        )
                : null;
        MeshBuilder stroke = descriptor.strokeOpacity() > 0.0f
                ? renderer.batch(
                descriptor.depthTest() ? CombatantRenderPipelines.WORLD_COLORED_LINES_DEPTH : CombatantRenderPipelines.WORLD_COLORED_LINES,
                depthMode
        )
                : null;
        if (fill == null && stroke == null) return;

        int fillColor = applyOpacity(descriptor.fillColor(), descriptor.fillOpacity());
        int strokeColor = applyOpacity(descriptor.strokeColor(), descriptor.strokeOpacity());
        switch (descriptor.shape()) {
            case CIRCLE -> renderDisc(fill, stroke, descriptor.center(), 0.0f, descriptor.radius(), 0.0f, (float) TAU,
                    descriptor.segments(), fillColor, strokeColor, false);
            case RING -> renderDisc(fill, stroke, descriptor.center(), descriptor.innerRadius(), descriptor.radius(),
                    0.0f, (float) TAU, descriptor.segments(), fillColor, strokeColor, true);
            case ARC_SECTOR -> renderDisc(fill, stroke, descriptor.center(), descriptor.innerRadius(), descriptor.radius(),
                    descriptor.startAngleRadians(), descriptor.endAngleRadians(), descriptor.segments(), fillColor, strokeColor, true);
            case DOME -> renderSphere(fill, stroke, descriptor.center(), descriptor.radius(), Math.max(descriptor.height(), descriptor.radius()),
                    descriptor.segments(), fillColor, strokeColor, true);
            case SPHERE -> renderSphere(fill, stroke, descriptor.center(), descriptor.radius(),
                    descriptor.height() > 0.0f ? descriptor.height() : descriptor.radius(),
                    descriptor.segments(), fillColor, strokeColor, false);
            case BOX -> renderBox(fill, stroke, descriptor.center(), descriptor.halfExtents(), fillColor, strokeColor);
            case POLYGON -> renderPolygon(fill, stroke, descriptor.center(), descriptor.polygonPoints(), fillColor, strokeColor);
        }
    }

    private static void renderDisc(MeshBuilder fill,
                                   MeshBuilder stroke,
                                   Vec3 center,
                                   float innerRadius,
                                   float outerRadius,
                                   float startAngle,
                                   float endAngle,
                                   int segments,
                                   int fillColor,
                                   int strokeColor,
                                   boolean includeInnerStroke) {
        if (outerRadius <= 0.0f) return;
        float span = normalizeSpan(startAngle, endAngle);
        int segs = Math.max(2, Math.round(segments * Math.min(1.0f, span / (float) TAU)));
        boolean closed = span >= TAU - 1.0e-4f;

        Vec3 firstOuter = null;
        Vec3 firstInner = null;
        Vec3 prevOuter = null;
        Vec3 prevInner = null;
        for (int i = 0; i <= segs; i++) {
            float t = i / (float) segs;
            double angle = startAngle + span * t;
            Vec3 outer = center.add(Math.cos(angle) * outerRadius, 0.0, Math.sin(angle) * outerRadius);
            Vec3 inner = innerRadius > 0.0f
                    ? center.add(Math.cos(angle) * innerRadius, 0.0, Math.sin(angle) * innerRadius)
                    : center;
            if (i == 0) {
                firstOuter = outer;
                firstInner = inner;
            }
            if (prevOuter != null) {
                if (fill != null) {
                    if (innerRadius > 0.0f) {
                        addQuad(fill, prevInner, prevOuter, outer, inner, fillColor);
                    } else {
                        addTriangle(fill, center, prevOuter, outer, fillColor);
                    }
                }
                if (stroke != null) {
                    addLine(stroke, prevOuter, outer, strokeColor);
                    if (includeInnerStroke && innerRadius > 0.0f) {
                        addLine(stroke, prevInner, inner, strokeColor);
                    }
                }
            }
            prevOuter = outer;
            prevInner = inner;
        }

        if (stroke != null && !closed && firstOuter != null && prevOuter != null) {
            addLine(stroke, firstOuter, firstInner, strokeColor);
            addLine(stroke, prevOuter, prevInner, strokeColor);
        }
    }

    private static void renderSphere(MeshBuilder fill,
                                     MeshBuilder stroke,
                                     Vec3 center,
                                     float radius,
                                     float verticalRadius,
                                     int segments,
                                     int fillColor,
                                     int strokeColor,
                                     boolean hemisphereOnly) {
        if (radius <= 0.0f || verticalRadius <= 0.0f) return;
        int lonSegments = Mth.clamp(segments, 8, 96);
        int latSegments = Math.max(4, lonSegments / 2);
        float minPhi = hemisphereOnly ? 0.0f : (float) (-Math.PI * 0.5);
        float maxPhi = (float) (Math.PI * 0.5);

        for (int lat = 0; lat < latSegments; lat++) {
            float t0 = lat / (float) latSegments;
            float t1 = (lat + 1) / (float) latSegments;
            double phi0 = minPhi + (maxPhi - minPhi) * t0;
            double phi1 = minPhi + (maxPhi - minPhi) * t1;
            for (int lon = 0; lon < lonSegments; lon++) {
                double a0 = TAU * lon / lonSegments;
                double a1 = TAU * (lon + 1) / lonSegments;
                Vec3 p00 = spherePoint(center, radius, verticalRadius, phi0, a0);
                Vec3 p01 = spherePoint(center, radius, verticalRadius, phi0, a1);
                Vec3 p10 = spherePoint(center, radius, verticalRadius, phi1, a0);
                Vec3 p11 = spherePoint(center, radius, verticalRadius, phi1, a1);
                if (fill != null) {
                    addQuad(fill, p00, p01, p11, p10, fillColor);
                }
                if (stroke != null && (lat % Math.max(1, latSegments / 5) == 0 || lon % Math.max(1, lonSegments / 8) == 0)) {
                    addLine(stroke, p00, p01, strokeColor);
                    addLine(stroke, p00, p10, strokeColor);
                }
            }
        }
        if (stroke != null && hemisphereOnly) {
            renderDisc(null, stroke, center, 0.0f, radius, 0.0f, (float) TAU, lonSegments, 0, strokeColor, false);
        }
    }

    private static Vec3 spherePoint(Vec3 center,
                                    float radius,
                                    float verticalRadius,
                                    double phi,
                                    double theta) {
        double cosPhi = Math.cos(phi);
        return center.add(
                Math.cos(theta) * cosPhi * radius,
                Math.sin(phi) * verticalRadius,
                Math.sin(theta) * cosPhi * radius
        );
    }

    private static void renderBox(MeshBuilder fill,
                                  MeshBuilder stroke,
                                  Vec3 center,
                                  Vec3 half,
                                  int fillColor,
                                  int strokeColor) {
        double x0 = center.x - half.x;
        double x1 = center.x + half.x;
        double y0 = center.y - half.y;
        double y1 = center.y + half.y;
        double z0 = center.z - half.z;
        double z1 = center.z + half.z;
        Vec3 p000 = new Vec3(x0, y0, z0), p100 = new Vec3(x1, y0, z0);
        Vec3 p110 = new Vec3(x1, y1, z0), p010 = new Vec3(x0, y1, z0);
        Vec3 p001 = new Vec3(x0, y0, z1), p101 = new Vec3(x1, y0, z1);
        Vec3 p111 = new Vec3(x1, y1, z1), p011 = new Vec3(x0, y1, z1);
        if (fill != null) {
            addQuad(fill, p000, p100, p101, p001, fillColor);
            addQuad(fill, p010, p011, p111, p110, fillColor);
            addQuad(fill, p000, p010, p110, p100, fillColor);
            addQuad(fill, p001, p101, p111, p011, fillColor);
            addQuad(fill, p000, p001, p011, p010, fillColor);
            addQuad(fill, p100, p110, p111, p101, fillColor);
        }
        if (stroke != null) {
            Vec3[] p = {p000, p100, p110, p010, p001, p101, p111, p011};
            int[][] edges = {
                    {0, 1}, {1, 2}, {2, 3}, {3, 0},
                    {4, 5}, {5, 6}, {6, 7}, {7, 4},
                    {0, 4}, {1, 5}, {2, 6}, {3, 7}
            };
            for (int[] edge : edges) addLine(stroke, p[edge[0]], p[edge[1]], strokeColor);
        }
    }

    private static void renderPolygon(MeshBuilder fill,
                                      MeshBuilder stroke,
                                      Vec3 center,
                                      List<Vec3> points,
                                      int fillColor,
                                      int strokeColor) {
        if (points == null || points.size() < 2) return;
        Vec3 first = center.add(points.getFirst());
        Vec3 previous = first;
        for (int i = 1; i < points.size(); i++) {
            Vec3 current = center.add(points.get(i));
            if (fill != null && i >= 2) {
                addTriangle(fill, first, previous, current, fillColor);
            }
            if (stroke != null) {
                addLine(stroke, previous, current, strokeColor);
            }
            previous = current;
        }
        if (stroke != null) {
            addLine(stroke, previous, first, strokeColor);
        }
    }

    private static float normalizeSpan(float start, float end) {
        float span = end - start;
        while (span <= 0.0f) span += (float) TAU;
        return Math.min(span, (float) TAU);
    }

    private static int applyOpacity(int argb, float opacity) {
        int alpha = (argb >>> 24) & 0xFF;
        int out = Mth.clamp(Math.round(alpha * Mth.clamp(opacity, 0.0f, 1.0f)), 0, 255);
        return (out << 24) | (argb & 0x00FFFFFF);
    }

    private static void addTriangle(MeshBuilder mesh, Vec3 a, Vec3 b, Vec3 c, int argb) {
        mesh.ensureTriCapacity();
        int i0 = vertex(mesh, a, argb);
        int i1 = vertex(mesh, b, argb);
        int i2 = vertex(mesh, c, argb);
        mesh.triangle(i0, i1, i2);
    }

    private static void addQuad(MeshBuilder mesh, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        mesh.ensureQuadCapacity();
        int i0 = vertex(mesh, a, argb);
        int i1 = vertex(mesh, b, argb);
        int i2 = vertex(mesh, c, argb);
        int i3 = vertex(mesh, d, argb);
        mesh.quad(i0, i1, i2, i3);
    }

    private static void addLine(MeshBuilder mesh, Vec3 a, Vec3 b, int argb) {
        mesh.ensureLineCapacity();
        int i0 = vertex(mesh, a, argb);
        int i1 = vertex(mesh, b, argb);
        mesh.line(i0, i1);
    }

    private static int vertex(MeshBuilder mesh, Vec3 point, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return mesh.vec3(point.x, point.y, point.z).color(r, g, b, a).next();
    }
}
