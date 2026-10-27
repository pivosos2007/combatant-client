/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer;

import combatant.client.render.engine.pipeline.WorldRenderPipelines;
import combatant.client.render.engine.uniform.MeshBuilder;
import net.minecraft.world.phys.AABB;

/**
 * Emits boxes for the animated {@code world_glow_surface} shader: a pixel-width rim, a band that
 * climbs the faces, fresnel and a faint sheen. Callers keep their own fill colours; this class only
 * packs the payload the shader expects (see the vertex shader header for the layout).
 */
public final class GlowSurfaces {
    private static final long TIME_WRAP_MS = 1_000_000L;

    private GlowSurfaces() {
    }

    /** Seconds on a wrapped clock; small enough to keep float precision for the shader sin() terms. */
    public static float time() {
        return (System.currentTimeMillis() % TIME_WRAP_MS) / 1000.0f;
    }

    /** Stable 0..1 phase per position so neighbouring boxes do not sweep in lockstep. */
    public static float phaseFor(double x, double y, double z) {
        int h = (int) Math.floor(x) * 73856093 ^ (int) Math.floor(y) * 19349663 ^ (int) Math.floor(z) * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0f;
    }

    /**
     * @param bottomArgb fill colour at the bottom edge (alpha = fill alpha)
     * @param topArgb    fill colour at the top edge
     * @param rimAlpha   0..1 strength of rim, halo and scan band
     * @param scan       0..1 strength of the climbing band (0 disables it)
     * @param fadeTop    true fades rim/band toward the top (hole style), false keeps them uniform
     * @param caps       true draws top and bottom faces too
     * @param depth      true tests against the pre-translucent world depth
     */
    public static void box(Renderer3D renderer, AABB box,
                           int bottomArgb, int topArgb,
                           float rimAlpha, float scan,
                           boolean fadeTop, boolean caps, boolean depth) {
        MeshBuilder mesh = renderer.batch(
                depth ? WorldRenderPipelines.WORLD_GLOW_SURFACE_DEPTH : WorldRenderPipelines.WORLD_GLOW_SURFACE,
                depth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.MAIN
        );
        if (mesh == null) {
            return;
        }

        float time = time();
        float phase = phaseFor(box.minX, box.minY, box.minZ);
        float bottomFade = 1.0f;
        float topFade = fadeTop ? 0.0f : 1.0f;

        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

        // Walls: v = 0 at the bottom, 1 at the top so the shader's "climb" direction matches world up.
        wall(mesh, time, phase, scan, rimAlpha, x1, z2, x2, z2, y1, y2, 0, 0, 1, bottomArgb, topArgb, bottomFade, topFade);
        wall(mesh, time, phase, scan, rimAlpha, x2, z1, x1, z1, y1, y2, 0, 0, -1, bottomArgb, topArgb, bottomFade, topFade);
        wall(mesh, time, phase, scan, rimAlpha, x2, z2, x2, z1, y1, y2, 1, 0, 0, bottomArgb, topArgb, bottomFade, topFade);
        wall(mesh, time, phase, scan, rimAlpha, x1, z1, x1, z2, y1, y2, -1, 0, 0, bottomArgb, topArgb, bottomFade, topFade);

        if (caps) {
            cap(mesh, time, phase, scan, rimAlpha, x1, z1, x2, z2, y1, -1, bottomArgb, bottomFade);
            cap(mesh, time, phase, scan, rimAlpha, x1, z1, x2, z2, y2, 1, topArgb, topFade);
        }
    }

    private static void wall(MeshBuilder mesh, float time, float phase, float scan, float rim,
                             double ax, double az, double bx, double bz,
                             double yLow, double yHigh,
                             float nx, float ny, float nz,
                             int lowArgb, int highArgb, float lowFade, float highFade) {
        mesh.ensureQuadCapacity();
        int i1 = vertex(mesh, time, phase, scan, rim, ax, yLow, az, 0, 0, nx, ny, nz, lowArgb, lowFade);
        int i2 = vertex(mesh, time, phase, scan, rim, bx, yLow, bz, 1, 0, nx, ny, nz, lowArgb, lowFade);
        int i3 = vertex(mesh, time, phase, scan, rim, bx, yHigh, bz, 1, 1, nx, ny, nz, highArgb, highFade);
        int i4 = vertex(mesh, time, phase, scan, rim, ax, yHigh, az, 0, 1, nx, ny, nz, highArgb, highFade);
        mesh.quad(i1, i2, i3, i4);
    }

    private static void cap(MeshBuilder mesh, float time, float phase, float scan, float rim,
                            double x1, double z1, double x2, double z2, double y,
                            float ny, int argb, float fade) {
        mesh.ensureQuadCapacity();
        int i1 = vertex(mesh, time, phase, scan, rim, x1, y, z1, 0, 0, 0, ny, 0, argb, fade);
        int i2 = vertex(mesh, time, phase, scan, rim, x2, y, z1, 1, 0, 0, ny, 0, argb, fade);
        int i3 = vertex(mesh, time, phase, scan, rim, x2, y, z2, 1, 1, 0, ny, 0, argb, fade);
        int i4 = vertex(mesh, time, phase, scan, rim, x1, y, z2, 0, 1, 0, ny, 0, argb, fade);
        mesh.quad(i1, i2, i3, i4);
    }

    private static int vertex(MeshBuilder mesh, float time, float phase, float scan, float rim,
                              double x, double y, double z,
                              float u, float v,
                              float nx, float ny, float nz,
                              int argb, float fade) {
        return mesh.vec3(x, y, z)
                .raw2(u, v)
                .colorArgb(argb)
                .vec4(nx, ny, nz, fade)
                .vec4(time, phase, scan, rim)
                .next();
    }
}
