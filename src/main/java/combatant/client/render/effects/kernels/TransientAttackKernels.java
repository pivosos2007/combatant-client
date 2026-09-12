/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.kernels;

import combatant.client.render.effects.CurrentTransientEffectBackend;
import combatant.client.render.effects.EffectDomain;
import combatant.client.render.effects.EffectMaterial;
import combatant.client.render.effects.EffectParameters;
import combatant.client.render.effects.TransientEffectDescriptor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Reusable current-renderer kernels for short combat/transient events. */
public final class TransientAttackKernels {
    public static final String PLASMA_PINCH = "combatant:plasma_pinch";
    public static final String SPARKS = "combatant:attack_sparks";
    public static final String SLASH = "combatant:attack_slash";

    private static final double TAU = Math.PI * 2.0;

    private TransientAttackKernels() {
    }

    public static void register(CurrentTransientEffectBackend backend) {
        if (backend == null) return;
        backend.registerRenderer(PLASMA_PINCH, TransientAttackKernels::renderPlasma);
        backend.registerRenderer(SPARKS, TransientAttackKernels::renderSparks);
        backend.registerRenderer(SLASH, TransientAttackKernels::renderSlash);
    }

    public static TransientEffectDescriptor plasma(
            long id, Vec3 position, long nowMs, long lifetimeMs, long seed,
            int sourceEntityId, int primaryColor, int secondaryColor,
            float radius, float intensity, int priority
    ) {
        return new TransientEffectDescriptor(
                id, PLASMA_PINCH, EffectDomain.BILLBOARD, position, nowMs, lifetimeMs,
                sourceEntityId, seed, 0, priority,
                material(primaryColor, secondaryColor, intensity),
                new EffectParameters(Math.max(0.05f, radius), Math.max(0.0f, intensity), 18.0f, 1.0f,
                        0, 0, 0, 0)
        );
    }

    public static TransientEffectDescriptor sparks(
            long id, Vec3 position, long nowMs, long lifetimeMs, long seed,
            int sourceEntityId, int primaryColor, int secondaryColor,
            float radius, int rayCount, float intensity, int priority
    ) {
        return new TransientEffectDescriptor(
                id, SPARKS, EffectDomain.BILLBOARD, position, nowMs, lifetimeMs,
                sourceEntityId, seed, 0, priority,
                material(primaryColor, secondaryColor, intensity),
                new EffectParameters(Math.max(0.05f, radius), Math.max(3, rayCount),
                        Math.max(0.0f, intensity), 0.12f, 0, 0, 0, 0)
        );
    }

    public static TransientEffectDescriptor slash(
            long id, Vec3 position, long nowMs, long lifetimeMs, long seed,
            int sourceEntityId, int primaryColor, int secondaryColor,
            float radius, float arcRadians, float tiltRadians, float intensity, int priority
    ) {
        return new TransientEffectDescriptor(
                id, SLASH, EffectDomain.RIBBON, position, nowMs, lifetimeMs,
                sourceEntityId, seed, 0, priority,
                material(primaryColor, secondaryColor, intensity),
                new EffectParameters(Math.max(0.05f, radius), Math.max(0.15f, arcRadians),
                        tiltRadians, Math.max(0.0f, intensity), 22.0f, 0, 0, 0)
        );
    }

    private static EffectMaterial material(int primary, int secondary, float intensity) {
        return new EffectMaterial(
                primary,
                secondary,
                1.0f,
                Math.max(0.0f, intensity),
                EffectMaterial.DepthPolicy.MAIN,
                EffectMaterial.BlendPolicy.ADDITIVE
        );
    }

    private static void renderPlasma(Renderer3D renderer,
                                     TransientEffectDescriptor effect,
                                     float tickDelta,
                                     long nowMs) {
        float t = effect.progress(nowMs);
        float fade = 1.0f - smooth(t);
        if (fade <= 0.002f) return;

        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE,
                TextureStorage.FIRE_FLY,
                Renderer3D.DepthMode.MAIN
        );
        if (mesh == null) return;

        float radius = effect.parameters().p0();
        float intensity = effect.parameters().p1();
        int count = Mth.clamp(Math.round(effect.parameters().p2()), 8, 48);
        Quaternionf camera = RenderState.cameraRotation;

        float coreSize = radius * (0.18f + t * 0.45f);
        addBillboard(mesh, effect.position(), coreSize, camera,
                withAlpha(effect.material().secondaryColor(), Math.round(255.0f * fade)));

        double phase = unit(effect.seed(), 0) * TAU + t * 4.2;
        float shellRadius = radius * (0.16f + smooth(t) * 0.95f);
        for (int i = 0; i < count; i++) {
            double u = (i + 0.5) / count;
            double a = TAU * u + phase;
            double wobble = Math.sin(a * 3.0 + t * 8.0 + unit(effect.seed(), i + 1) * TAU) * radius * 0.11;
            Vec3 p = effect.position().add(
                    Math.cos(a) * (shellRadius + wobble),
                    Math.sin(a * 2.0 + phase) * radius * 0.16,
                    Math.sin(a) * (shellRadius + wobble)
            );
            float local = 0.55f + 0.45f * (float) Math.sin(a * 2.0 + 1.7);
            float size = radius * (0.035f + 0.035f * local) * (0.75f + intensity * 0.25f);
            int color = lerpColor(effect.material().primaryColor(), effect.material().secondaryColor(), local);
            addBillboard(mesh, p, size, camera, withAlpha(color, Math.round(210.0f * fade)));
        }
    }

    private static void renderSparks(Renderer3D renderer,
                                     TransientEffectDescriptor effect,
                                     float tickDelta,
                                     long nowMs) {
        float t = effect.progress(nowMs);
        float fade = 1.0f - smooth(t);
        if (fade <= 0.002f) return;

        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE,
                TextureStorage.FIRE_FLY,
                Renderer3D.DepthMode.MAIN
        );
        if (mesh == null) return;

        float radius = effect.parameters().p0();
        int rays = Mth.clamp(Math.round(effect.parameters().p1()), 3, 64);
        float intensity = effect.parameters().p2();
        float baseSize = Math.max(0.02f, effect.parameters().p3());
        Quaternionf camera = RenderState.cameraRotation;

        for (int ray = 0; ray < rays; ray++) {
            double azimuth = TAU * unit(effect.seed(), ray * 3 + 1);
            double elevation = (unit(effect.seed(), ray * 3 + 2) - 0.35) * 1.2;
            double cosEl = Math.cos(elevation);
            Vec3 direction = new Vec3(Math.cos(azimuth) * cosEl, Math.sin(elevation), Math.sin(azimuth) * cosEl);
            float rayScale = 0.55f + 0.65f * (float) unit(effect.seed(), ray * 3 + 3);
            double headDistance = radius * rayScale * smooth(t);
            for (int trail = 0; trail < 4; trail++) {
                float trailT = trail / 3.0f;
                double distance = Math.max(0.0, headDistance - radius * 0.15 * trailT);
                Vec3 p = effect.position().add(direction.scale(distance));
                float alpha = fade * (1.0f - trailT * 0.72f);
                float size = baseSize * (0.85f + intensity * 0.18f) * (1.0f - trailT * 0.45f);
                int color = lerpColor(effect.material().secondaryColor(), effect.material().primaryColor(), trailT);
                addBillboard(mesh, p, size, camera, withAlpha(color, Math.round(alpha * 255.0f)));
            }
        }
    }

    private static void renderSlash(Renderer3D renderer,
                                    TransientEffectDescriptor effect,
                                    float tickDelta,
                                    long nowMs) {
        float t = effect.progress(nowMs);
        float fade = 1.0f - smooth(t);
        if (fade <= 0.002f) return;

        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE,
                TextureStorage.FIRE_FLY,
                Renderer3D.DepthMode.MAIN
        );
        if (mesh == null) return;

        float radius = effect.parameters().p0();
        float arc = effect.parameters().p1();
        float tilt = effect.parameters().p2();
        float intensity = effect.parameters().p3();
        int count = Mth.clamp(Math.round(effect.parameters().p4()), 8, 48);
        Quaternionf camera = RenderState.cameraRotation;
        double base = unit(effect.seed(), 1) * TAU - arc * 0.5;
        double cosTilt = Math.cos(tilt);
        double sinTilt = Math.sin(tilt);

        for (int i = 0; i < count; i++) {
            float u = count <= 1 ? 0.5f : i / (float) (count - 1);
            double a = base + arc * u;
            double x = Math.cos(a) * radius;
            double y = Math.sin(a) * radius * 0.55;
            double z = y * sinTilt;
            y *= cosTilt;
            Vec3 p = effect.position().add(x, y, z);
            float center = 1.0f - Math.abs(u * 2.0f - 1.0f);
            float reveal = Mth.clamp((t * 1.35f - u) * 4.0f, 0.0f, 1.0f);
            float alpha = fade * reveal * (0.35f + center * 0.65f);
            float size = radius * 0.045f * (0.7f + intensity * 0.25f) * (0.7f + center * 0.5f);
            int color = lerpColor(effect.material().primaryColor(), effect.material().secondaryColor(), center);
            addBillboard(mesh, p, size, camera, withAlpha(color, Math.round(alpha * 255.0f)));
        }
    }

    private static void addBillboard(MeshBuilder mesh, Vec3 center, float size, Quaternionf camera, int argb) {
        Vector3f right = new Vector3f(1, 0, 0).rotate(camera).mul(size);
        Vector3f up = new Vector3f(0, 1, 0).rotate(camera).mul(size);
        Vec3 p1 = center.add(-right.x - up.x, -right.y - up.y, -right.z - up.z);
        Vec3 p2 = center.add(right.x - up.x, right.y - up.y, right.z - up.z);
        Vec3 p3 = center.add(right.x + up.x, right.y + up.y, right.z + up.z);
        Vec3 p4 = center.add(-right.x + up.x, -right.y + up.y, -right.z + up.z);
        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(p1.x, p1.y, p1.z).vec2(0, 1).colorArgb(argb).next();
        int i2 = mesh.vec3(p2.x, p2.y, p2.z).vec2(1, 1).colorArgb(argb).next();
        int i3 = mesh.vec3(p3.x, p3.y, p3.z).vec2(1, 0).colorArgb(argb).next();
        int i4 = mesh.vec3(p4.x, p4.y, p4.z).vec2(0, 0).colorArgb(argb).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int ar = (a >>> 16) & 255, ag = (a >>> 8) & 255, ab = a & 255;
        int br = (b >>> 16) & 255, bg = (b >>> 8) & 255, bb = b & 255;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static double unit(long seed, int lane) {
        long z = seed + 0x9E3779B97F4A7C15L * (lane + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
