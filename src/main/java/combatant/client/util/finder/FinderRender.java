/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.finder;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;

/**
 * World markers shared by the finder modules: chunk plates, block boxes and tracers.
 *
 * <p>Everything is drawn with depth testing off. Finder hits are almost always behind
 * terrain, which is the whole reason to render them.</p>
 */
public final class FinderRender {

    private static final double PLATE_THICKNESS = 0.06;
    // Tracers start a little in front of the camera, like the Tracers module, so they read as
    // coming from the crosshair instead of from inside the player's head.
    private static final double TRACER_START_DISTANCE = 10.0;

    private final MeshBuilder tris;
    private final MeshBuilder lines;

    private FinderRender(MeshBuilder tris, MeshBuilder lines) {
        this.tris = tris;
        this.lines = lines;
    }

    public static FinderRender begin(Renderer3D renderer, float lineWidth) {
        MeshBuilder tris = renderer.batch(CombatantRenderPipelines.WORLD_COLORED, Renderer3D.DepthMode.NONE);
        float previous = RenderState.lineWidth;
        try {
            RenderState.lineWidth = Math.max(0.5f, lineWidth);
            MeshBuilder lines = renderer.batch(CombatantRenderPipelines.WORLD_COLORED_LINES, Renderer3D.DepthMode.NONE);
            return new FinderRender(tris, lines);
        } finally {
            RenderState.lineWidth = previous;
        }
    }

    /** A thin horizontal slab covering one chunk at the given height. */
    public void chunkPlate(int chunkX, int chunkZ, double y, int fillArgb, int lineArgb) {
        double minX = chunkX << 4;
        double minZ = chunkZ << 4;
        box(new AABB(minX, y, minZ, minX + 16.0, y + PLATE_THICKNESS, minZ + 16.0), fillArgb, lineArgb);
    }

    public void blockBox(int x, int y, int z, int fillArgb, int lineArgb) {
        box(new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0), fillArgb, lineArgb);
    }

    public void box(AABB box, int fillArgb, int lineArgb) {
        if (alpha(fillArgb) > 0) filledBox(box, fillArgb);
        if (alpha(lineArgb) > 0) outlineBox(box, lineArgb);
    }

    public void tracer(Vec3 target, int argb) {
        Vec3 start = tracerStart();
        if (start == null) return;
        line(start.x, start.y, start.z, target.x, target.y, target.z, argb);
    }

    /** Horizontal circle, e.g. a spawner's activation range. */
    public void ring(Vec3 center, double radius, int argb) {
        // Enough segments that a 16-block ring does not look polygonal up close.
        int segments = Math.max(24, (int) Math.ceil(radius * 4.0));
        double previousX = center.x + radius;
        double previousZ = center.z;
        for (int i = 1; i <= segments; i++) {
            double angle = (Math.PI * 2.0 * i) / segments;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            line(previousX, center.y, previousZ, x, center.y, z, argb);
            previousX = x;
            previousZ = z;
        }
    }

    public void verticalRing(Vec3 center, double radius, boolean alongX, int argb) {
        if (radius <= 0.0 || alpha(argb) == 0) return;
        int segments = Math.max(32, Math.min(128, (int) Math.ceil(radius * 5.0)));
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0 * i / segments;
            double b = Math.PI * 2.0 * (i + 1) / segments;
            if (alongX) {
                line(center.x + Math.cos(a) * radius, center.y + Math.sin(a) * radius, center.z,
                        center.x + Math.cos(b) * radius, center.y + Math.sin(b) * radius, center.z, argb);
            } else {
                line(center.x, center.y + Math.sin(a) * radius, center.z + Math.cos(a) * radius,
                        center.x, center.y + Math.sin(b) * radius, center.z + Math.cos(b) * radius, argb);
            }
        }
    }

    /** Vertical line through a point, useful to spot markers from far away. */
    public void beam(double x, double z, double fromY, double toY, int argb) {
        line(x, fromY, z, x, toY, z, argb);
    }

    private static Vec3 tracerStart() {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        if (camera == null) return null;
        Vector3fc forward = camera.forwardVector();
        return camera.position().add(
                forward.x() * TRACER_START_DISTANCE,
                forward.y() * TRACER_START_DISTANCE,
                forward.z() * TRACER_START_DISTANCE);
    }

    private void filledBox(AABB b, int argb) {
        int a = alpha(argb), r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, bl = argb & 0xFF;
        quad(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, r, g, bl, a);
        quad(b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ, r, g, bl, a);
        quad(b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, r, g, bl, a);
        quad(b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.minY, b.minZ, r, g, bl, a);
        quad(b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ, r, g, bl, a);
        quad(b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, r, g, bl, a);
    }

    private void outlineBox(AABB b, int argb) {
        line(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, argb);
        line(b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, argb);
        line(b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, argb);
        line(b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ, argb);
        line(b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, argb);
        line(b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, argb);
        line(b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, argb);
        line(b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, argb);
        line(b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, argb);
        line(b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, argb);
        line(b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, argb);
        line(b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, argb);
    }

    private void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        if (lines == null) return;
        int a = alpha(argb), r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        lines.ensureLineCapacity();
        int i1 = lines.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = lines.vec3(x2, y2, z2).color(r, g, b, a).next();
        lines.line(i1, i2);
    }

    private void quad(double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      double x3, double y3, double z3,
                      double x4, double y4, double z4,
                      int r, int g, int b, int a) {
        if (tris == null) return;
        tris.ensureQuadCapacity();
        int i1 = tris.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = tris.vec3(x2, y2, z2).color(r, g, b, a).next();
        int i3 = tris.vec3(x3, y3, z3).color(r, g, b, a).next();
        int i4 = tris.vec3(x4, y4, z4).color(r, g, b, a).next();
        tris.quad(i1, i2, i3, i4);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }
}
