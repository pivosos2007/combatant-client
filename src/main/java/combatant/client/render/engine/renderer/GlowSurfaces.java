/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer;

import combatant.client.config.values.EnumValue;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.WorldRenderPipelines;
import combatant.client.render.engine.uniform.MeshBuilder;
import net.minecraft.world.phys.AABB;

/**
 * Reusable world glow surface. All effects share the original Fresnel, screen-space rim and halo
 * shader; presets change only the visual modulation. The vertex payload and pipelines are unchanged.
 *
 * <p>One {@link Batch} writes surfaces and their edge ribbons into the same Renderer3D mesh batch.
 * Neither presets nor custom GLSL effects create additional framebuffer passes or draw calls.</p>
 */
public final class GlowSurfaces {
    private static final long TIME_WRAP_MS = 1_000_000L;

    private GlowSurfaces() { }

    /** Values also form the stable GLSL ABI (world_glow_animations.glsl). Do not renumber. */
    public enum Animation implements EnumValue.IdProvider {
        STATIC("static", 0),
        PULSE("pulse", 1),
        BREATHE("breathe", 2),
        FLOW("flow", 3),
        RIPPLE("ripple", 4),
        SWEEP("sweep", 5),
        CUSTOM_1("custom_1", 8),
        CUSTOM_2("custom_2", 9),
        CUSTOM_3("custom_3", 10),
        CUSTOM_4("custom_4", 11);

        private final String id;
        private final int shaderId;

        Animation(String id, int shaderId) {
            this.id = id;
            this.shaderId = shaderId;
        }

        @Override public String id() { return id; }
        public int shaderId() { return shaderId; }
    }

    /** Wrapped clock protects fragment-shader precision after extended play sessions. */
    public static float time() {
        return (System.currentTimeMillis() % TIME_WRAP_MS) / 1000.0f;
    }

    /** 0 inclusive, 1 exclusive: animation ID is packed into the integer part of this value. */
    public static float phaseFor(double x, double y, double z) {
        int h = (int) Math.floor(x) * 73856093 ^ (int) Math.floor(y) * 19349663 ^ (int) Math.floor(z) * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65536.0f;
    }

    /**
     * Starts one batched glow submission. Null means the renderer is not accepting geometry.
     * The animation is evaluated only on the GPU; time is shared between all enclosed faces/edges.
     */
    public static Batch begin(Renderer3D renderer, boolean depth, Animation animation, float speed, float accent) {
        MeshBuilder mesh = renderer.batch(
                depth ? WorldRenderPipelines.WORLD_GLOW_SURFACE_DEPTH : WorldRenderPipelines.WORLD_GLOW_SURFACE,
                depth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.MAIN);
        if (mesh == null) return null;
        Animation resolved = animation == null ? Animation.STATIC : animation;
        float seconds = resolved == Animation.STATIC ? 0.0f : time() * Math.max(0.0f, speed);
        return new Batch(mesh, seconds, resolved.shaderId(), clamp(accent, 0.0f, 1.0f));
    }

    /** New public entry point for existing ESP modules and add-ons. */
    public static void box(Renderer3D renderer, AABB box, int bottomArgb, int topArgb,
                           float rimAlpha, Animation animation, float speed, float accent,
                           boolean fadeTop, boolean caps, boolean depth) {
        Batch batch = begin(renderer, depth, animation, speed, accent);
        if (batch != null) batch.box(box, bottomArgb, topArgb, rimAlpha, fadeTop, caps);
    }

    /**
     * Backward-compatible entry point. The old scan setting maps to the new selectable SWEEP preset;
     * passing 0 produces a static surface. Existing callers and compiled add-ons remain compatible.
     */
    public static void box(Renderer3D renderer, AABB box, int bottomArgb, int topArgb,
                           float rimAlpha, float scan, boolean fadeTop, boolean caps, boolean depth) {
        box(renderer, box, bottomArgb, topArgb, rimAlpha,
                scan > 0.001f ? Animation.SWEEP : Animation.STATIC,
                1.0f, clamp(scan, 0.0f, 1.0f), fadeTop, caps, depth);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static final class Batch {
        private final MeshBuilder mesh;
        private final float seconds;
        private final int animationId;
        private final float accent;

        private Batch(MeshBuilder mesh, float seconds, int animationId, float accent) {
            this.mesh = mesh;
            this.seconds = seconds;
            this.animationId = animationId;
            this.accent = accent;
        }

        public void box(AABB box, int bottomArgb, int topArgb,
                        float rimAlpha, boolean fadeTop, boolean caps) {
            float phase = phaseFor(box.minX, box.minY, box.minZ);
            float bottomFade = 1.0f;
            float topFade = fadeTop ? 0.0f : 1.0f;
            double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
            double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

            wall(phase, rimAlpha, x1, z2, x2, z2, y1, y2, 0, 0, 1,
                    bottomArgb, topArgb, bottomFade, topFade);
            wall(phase, rimAlpha, x2, z1, x1, z1, y1, y2, 0, 0, -1,
                    bottomArgb, topArgb, bottomFade, topFade);
            wall(phase, rimAlpha, x2, z2, x2, z1, y1, y2, 1, 0, 0,
                    bottomArgb, topArgb, bottomFade, topFade);
            wall(phase, rimAlpha, x1, z1, x1, z2, y1, y2, -1, 0, 0,
                    bottomArgb, topArgb, bottomFade, topFade);

            if (caps) {
                cap(phase, rimAlpha, x1, z1, x2, z2, y1, -1, bottomArgb, bottomFade);
                cap(phase, rimAlpha, x1, z1, x2, z2, y2, 1, topArgb, topFade);
            }
        }

        /**
         * Camera-facing ribbon with an analytic, antialiased bright core and soft halo in GLSL.
         * Same shader, vertex format and batch as box(); no native line-state changes.
         */
        public void edge(double x1, double y1, double z1, double x2, double y2, double z2,
                         int startArgb, int endArgb, float glow, float thickness) {
            double mx = (x1 + x2) * 0.5;
            double my = (y1 + y2) * 0.5;
            double mz = (z1 + z2) * 0.5;
            double ex = x2 - x1, ey = y2 - y1, ez = z2 - z1;
            double dx = RenderState.cameraPos.x - mx;
            double dy = RenderState.cameraPos.y - my;
            double dz = RenderState.cameraPos.z - mz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < 1.0e-10 || ex * ex + ey * ey + ez * ez < 1.0e-10) return;

            double sx = ey * dz - ez * dy;
            double sy = ez * dx - ex * dz;
            double sz = ex * dy - ey * dx;
            double sideSq = sx * sx + sy * sy + sz * sz;
            if (sideSq < 1.0e-10) return;

            double dist = Math.sqrt(distSq);
            double halfWidth = 0.003 + clamp(thickness, 0.5f, 4.0f) * (0.0025 + dist * 0.00018);
            double k = halfWidth / Math.sqrt(sideSq);
            sx *= k; sy *= k; sz *= k;
            // Bias toward camera to prevent z fighting on coplanar faces.
            double bx = dx / dist * 0.0025, by = dy / dist * 0.0025, bz = dz / dist * 0.0025;
            float phase = phaseFor(mx, my, mz);
            // Negative rim marks a ribbon. Encode line glow separately from the face rim strength.
            float ribbonMarker = -1.0f - clamp(glow, 0.0f, 4.0f);
            mesh.ensureQuadCapacity();
            int a = vertex(x1 - sx + bx, y1 - sy + by, z1 - sz + bz, 0, 0,
                    0, 0, 1, startArgb, 1, phase, ribbonMarker);
            int b = vertex(x1 + sx + bx, y1 + sy + by, z1 + sz + bz, 0, 1,
                    0, 0, 1, startArgb, 1, phase, ribbonMarker);
            int c = vertex(x2 + sx + bx, y2 + sy + by, z2 + sz + bz, 1, 1,
                    0, 0, 1, endArgb, 1, phase, ribbonMarker);
            int d = vertex(x2 - sx + bx, y2 - sy + by, z2 - sz + bz, 1, 0,
                    0, 0, 1, endArgb, 1, phase, ribbonMarker);
            mesh.quad(a, b, c, d);
        }

        private void wall(float phase, float rim, double ax, double az, double bx, double bz,
                          double low, double high, float nx, float ny, float nz,
                          int lowArgb, int highArgb, float lowFade, float highFade) {
            mesh.ensureQuadCapacity();
            int a = vertex(ax, low, az, 0, 0, nx, ny, nz, lowArgb, lowFade, phase, rim);
            int b = vertex(bx, low, bz, 1, 0, nx, ny, nz, lowArgb, lowFade, phase, rim);
            int c = vertex(bx, high, bz, 1, 1, nx, ny, nz, highArgb, highFade, phase, rim);
            int d = vertex(ax, high, az, 0, 1, nx, ny, nz, highArgb, highFade, phase, rim);
            mesh.quad(a, b, c, d);
        }

        private void cap(float phase, float rim, double x1, double z1, double x2, double z2,
                         double y, float ny, int argb, float fade) {
            mesh.ensureQuadCapacity();
            int a = vertex(x1, y, z1, 0, 0, 0, ny, 0, argb, fade, phase, rim);
            int b = vertex(x2, y, z1, 1, 0, 0, ny, 0, argb, fade, phase, rim);
            int c = vertex(x2, y, z2, 1, 1, 0, ny, 0, argb, fade, phase, rim);
            int d = vertex(x1, y, z2, 0, 1, 0, ny, 0, argb, fade, phase, rim);
            mesh.quad(a, b, c, d);
        }

        private int vertex(double x, double y, double z, float u, float v,
                           float nx, float ny, float nz, int color, float fade, float phase, float rim) {
            return mesh.vec3(x, y, z)
                    .raw2(u, v)
                    .colorArgb(color)
                    .vec4(nx, ny, nz, fade)
                    // Params2: seconds, shaderPresetId + phase, accent strength, signed rim/line glow.
                    .vec4(seconds, animationId + phase, accent, rim)
                    .next();
        }
    }
}
