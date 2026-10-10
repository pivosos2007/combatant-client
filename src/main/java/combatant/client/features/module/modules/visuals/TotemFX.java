/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.effects.CurrentTransientEffectBackend;
import combatant.client.render.effects.EffectBudget;
import combatant.client.render.effects.kernels.TransientAttackKernels;
import combatant.client.render.effects.mask.WorldPostProcessMasks;
import combatant.client.render.effects.surface.CurrentSurfaceWaveRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@ModuleInfo(id = "totemfx", displayName = "TotemFX", category = ModuleCategory.VISUALS,
        subcategory = ModuleSubcategory.COSMETIC, description = "module.totemfx.description")
public class TotemFX extends Module {
    private static final String SETTING_SELF_POP = "self_pop";
    private static final String SETTING_ATTACKED_POP = "attacked_pop";
    private static final String SETTING_BURST_SCALE = "burst_scale";
    private static final String SETTING_DURATION = "duration";
    private static final String SETTING_DENSITY = "density";
    private static final String SETTING_DEPTH_TEST = "depth_test";

    private static final long ATTACK_ATTRIBUTION_WINDOW_MS = 1_250L;
    private static final long ATTACK_HISTORY_RETENTION_MS = 2_500L;
    private static final int MAX_ACTIVE_BURSTS = 24;

    private static final int TOTEM_GOLD = 0xFFFFD36A;
    private static final int TOTEM_MINT = 0xFF68FFD5;
    private static final int IMPACT_CORAL = 0xFFFF667A;
    private static final int IMPACT_HOT = 0xFFFFA24F;

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue selfPop = bool("totemFxSelfPop", SETTING_SELF_POP, true);
    private final BooleanValue attackedPop = bool("totemFxAttackedPop", SETTING_ATTACKED_POP, true);
    private final NumberValue<Float> burstScale = num("totemFxBurstScale", SETTING_BURST_SCALE, 1.0f, 0.45f, 2.5f);
    private final NumberValue<Integer> durationMs = num("totemFxDuration", SETTING_DURATION, 700, 200, 5000);
    private final NumberValue<Float> density = num("totemFxDensity", SETTING_DENSITY, 1.0f, 0.45f, 2.0f);
    private final BooleanValue depthTest = bool("totemFxDepthTest", SETTING_DEPTH_TEST, true);

    private final CurrentTransientEffectBackend worldEffects =
            new CurrentTransientEffectBackend(new EffectBudget(128, 48, Integer.MIN_VALUE));
    private final Map<UUID, Long> recentLocalAttacks = new HashMap<>();
    private final List<BurstVisual> activeBursts = new ArrayList<>();
    private long nextEffectId = 1L;

    {
        TransientAttackKernels.register(worldEffects);
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (!isEnabled() || event == null || event.getPlayer() != mc.player) return;
        if (!(event.getTarget() instanceof Player player) || player == mc.player) return;

        long now = System.currentTimeMillis();
        recentLocalAttacks.put(player.getUUID(), now);
        pruneAttackHistory(now);
    }

    public void onTotemPop() {
        onLocalTotemPop();
    }

    public void onLocalTotemPop() {
        if (!isEnabled() || !selfPop.get() || mc.player == null || mc.level == null) return;
        spawnBurst(mc.player, BurstKind.SELF, System.currentTimeMillis());
    }

    public void onOpponentTotemPop(Player player) {
        if (!isEnabled() || !attackedPop.get() || player == null || mc.player == null || mc.level == null) return;
        if (player == mc.player) return;

        long now = System.currentTimeMillis();
        Long attackedAt = recentLocalAttacks.get(player.getUUID());
        if (attackedAt == null || now - attackedAt < 0L || now - attackedAt > ATTACK_ATTRIBUTION_WINDOW_MS) {
            pruneAttackHistory(now);
            return;
        }

        recentLocalAttacks.remove(player.getUUID());
        spawnBurst(player, BurstKind.ATTACKED, now);
        pruneAttackHistory(now);
    }

    private void spawnBurst(Player player, BurstKind kind, long now) {
        float scale = burstScale.get();
        float densityMul = density.get();
        long life = Math.max(200L, durationMs.get());
        boolean useDepth = depthTest.get();
        Vec3 base = player.position().add(0.0, 0.035, 0.0);
        Vec3 center = player.position().add(0.0, player.getBbHeight() * (kind == BurstKind.SELF ? 0.52 : 0.55), 0.0);
        int sourceId = player.getId();
        long burstSeed = mixSeed(now, sourceId, kind == BurstKind.SELF ? 0xD1B54A32D192ED03L : 0x94D049BB133111EBL);
        if (activeBursts.size() >= MAX_ACTIVE_BURSTS) {
            activeBursts.remove(0);
        }
        activeBursts.add(new BurstVisual(center, base, now, life, burstSeed, scale, kind, useDepth));

        worldEffects.beginTick(mc.player != null ? mc.player.tickCount : now);
        if (kind == BurstKind.SELF) {
            worldEffects.spawn(TransientAttackKernels.radialBurst(
                    nextEffectId++, center, now, life,
                    mixSeed(now, sourceId, 0x51F15EEDL), sourceId,
                    TOTEM_GOLD, TOTEM_MINT,
                    2.15f * scale, Math.round(34.0f * densityMul), 1.08f, 0.26f, 120, useDepth
            ));
            worldEffects.spawn(TransientAttackKernels.sparks(
                    nextEffectId++, center, now, life,
                    mixSeed(now, sourceId, 0xBB67AE85L), sourceId,
                    TOTEM_MINT, TOTEM_GOLD,
                    3.05f * scale, Math.round(24.0f * densityMul), 1.02f, 105, useDepth
            ));
            return;
        }

        worldEffects.spawn(TransientAttackKernels.radialBurst(
                nextEffectId++, center, now, life,
                mixSeed(now, sourceId, 0xA77ACED1L), sourceId,
                IMPACT_CORAL, TOTEM_GOLD,
                2.65f * scale, Math.round(42.0f * densityMul), 1.24f, 0.30f, 130, useDepth
        ));
        worldEffects.spawn(TransientAttackKernels.sparks(
                nextEffectId++, center, now, life,
                mixSeed(now, sourceId, 0x9E3779B9L), sourceId,
                TOTEM_GOLD, IMPACT_CORAL,
                3.75f * scale, Math.round(30.0f * densityMul), 1.20f, 115, useDepth
        ));
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onPrepareWorldPostProcess(float tickDelta) {
        if (!isEnabled() || activeBursts.isEmpty() || mc.level == null || mc.player == null) return;
        renderBurstPipelines(System.currentTimeMillis());
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        pruneExpiredBursts(now);
        renderProceduralBursts(renderer, now);
        worldEffects.render(renderer, tickDelta, now);
        pruneAttackHistory(now);
    }

    @Override
    public void onDisable() {
        worldEffects.clear();
        activeBursts.clear();
        recentLocalAttacks.clear();
    }

    private void renderProceduralBursts(Renderer3D renderer, long now) {
        renderProceduralBursts(renderer, now, true);
        renderProceduralBursts(renderer, now, false);
    }

    private void renderProceduralBursts(Renderer3D renderer, long now, boolean useDepth) {
        int live = 0;
        for (BurstVisual burst : activeBursts) {
            long age = now - burst.spawnMs();
            if (burst.depthTest() == useDepth && age >= 0L && age < burst.lifetimeMs()) live++;
        }
        if (live == 0) return;

        var pipeline = useDepth
                ? CombatantRenderPipelines.WORLD_TOTEM_BURST_DEPTH
                : CombatantRenderPipelines.WORLD_TOTEM_BURST;
        MeshBuilder mesh = renderer.batch(
                pipeline,
                useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.NONE
        );
        if (mesh == null) return;

        final int maxLonSegments = 18;
        final int maxLatSegments = 10;
        final int maxQuadsPerSphere = maxLonSegments * maxLatSegments;
        mesh.ensureCapacity(live * maxQuadsPerSphere * 12, live * maxQuadsPerSphere * 18);

        Vec3 cameraPos = RenderState.cameraPos;
        for (BurstVisual burst : activeBursts) {
            if (burst.depthTest() != useDepth) continue;
            long age = now - burst.spawnMs();
            if (age < 0L || age >= burst.lifetimeMs()) continue;

            float t = Mth.clamp(age / (float) burst.lifetimeMs(), 0.0f, 1.0f);
            boolean offensive = burst.kind() == BurstKind.ATTACKED;
            float maxRadius = (offensive ? 3.10f : 2.42f) * burst.scale();
            if (!burstVisible(burst.center(), maxRadius)) continue;

            double cameraDistance = cameraPos != null ? cameraPos.distanceTo(burst.center()) : 0.0;
            int lonSegments = cameraDistance > 48.0 ? 12 : cameraDistance > 28.0 ? 14 : 18;
            int latSegments = cameraDistance > 48.0 ? 7 : cameraDistance > 28.0 ? 8 : 10;

            float expansion = 1.0f - (float) Math.pow(1.0f - t, offensive ? 2.75 : 2.35);
            float attack = smooth(Mth.clamp(t / 0.075f, 0.0f, 1.0f));
            float fade = 1.0f - smooth(Mth.clamp((t - (offensive ? 0.54f : 0.48f)) / (offensive ? 0.46f : 0.52f), 0.0f, 1.0f));
            float envelope = attack * fade;
            if (envelope <= 0.002f) continue;

            int primary = offensive ? IMPACT_CORAL : TOTEM_GOLD;
            float seed = seed01(burst.seed());

            float shellRadius = Math.max(0.12f * burst.scale(), maxRadius * (0.075f + expansion * 0.925f));
            boolean cameraInsideShell = cameraPos != null && cameraPos.distanceToSqr(burst.center()) < shellRadius * shellRadius;
            addProceduralSphere(
                    mesh, burst.center(), shellRadius,
                    primary, t, seed, offensive ? 1.0f : 0.0f,
                    envelope * (offensive ? 1.24f : 1.02f), 0.0f,
                    lonSegments, latSegments, cameraInsideShell
            );

            // Ground shockwave is authored as its own one-shot expanding pass, projected from the
            // burst centre onto the contact plane. That keeps the origin visually locked to the
            // detonation while avoiding the old torso/surface mismatch and multi-flash feel.
            Vec3 groundOrigin = new Vec3(burst.center().x, burst.base().y, burst.center().z);
            float waveProgress = smooth(Mth.clamp(t / (offensive ? 0.58f : 0.64f), 0.0f, 1.0f));
            float waveFade = 1.0f - smooth(Mth.clamp((t - (offensive ? 0.52f : 0.48f)) / (offensive ? 0.30f : 0.34f), 0.0f, 1.0f));
            float waveOpacity = attack * waveFade * (offensive ? 0.98f : 0.90f);
            if (waveOpacity > 0.003f) {
                float waveRadius = maxRadius * (offensive ? (0.18f + waveProgress * 1.08f) : (0.15f + waveProgress * 0.98f));
                CurrentSurfaceWaveRenderer.renderRadialInteraction(
                        renderer,
                        mc.level,
                        groundOrigin,
                        waveRadius,
                        offensive ? 0.16f * burst.scale() : 0.13f * burst.scale(),
                        offensive ? 0.22f * burst.scale() : 0.18f * burst.scale(),
                        waveOpacity,
                        waveProgress,
                        useDepth,
                        offensive ? 280 : 220,
                        angle -> burstPalette(angle, t, offensive),
                        offensive ? 2.0f : 1.0f
                );
            }

            float innerRimEnvelope = envelope
                    * smooth(Mth.clamp((t - 0.035f) / 0.11f, 0.0f, 1.0f))
                    * (1.0f - smooth(Mth.clamp((t - 0.48f) / 0.42f, 0.0f, 1.0f)));
            if (innerRimEnvelope > 0.003f) {
                float innerRadius = shellRadius * (offensive ? 0.80f : 0.83f);
                boolean cameraInsideInner = cameraPos != null
                        && cameraPos.distanceToSqr(burst.center()) < innerRadius * innerRadius;
                addProceduralSphere(
                        mesh, burst.center(), innerRadius,
                        primary, t, fract(seed + 0.213f), offensive ? 1.0f : 0.0f,
                        innerRimEnvelope * (offensive ? 0.92f : 0.78f), 2.0f,
                        lonSegments, latSegments, cameraInsideInner
                );
            }

            float coreFade = 1.0f - smooth(Mth.clamp((t - 0.02f) / (offensive ? 0.52f : 0.46f), 0.0f, 1.0f));
            float coreEnvelope = attack * coreFade;
            if (coreEnvelope > 0.003f) {
                float coreRadius = maxRadius * (0.11f + 0.19f * smooth(Mth.clamp(t / 0.52f, 0.0f, 1.0f)));
                boolean cameraInsideCore = cameraPos != null && cameraPos.distanceToSqr(burst.center()) < coreRadius * coreRadius;
                addProceduralSphere(
                        mesh, burst.center(), coreRadius,
                        primary, t, fract(seed + 0.371f), offensive ? 1.0f : 0.0f,
                        coreEnvelope * (offensive ? 1.08f : 0.88f), 1.0f,
                        lonSegments, latSegments, cameraInsideCore
                );
            }
        }
    }

    private static void addProceduralSphere(MeshBuilder mesh,
                                             Vec3 center,
                                             float radius,
                                             int primary,
                                             float progress,
                                             float seed,
                                             float profile,
                                             float intensity,
                                             float layer,
                                             int lonSegments,
                                             int latSegments,
                                             boolean cameraInside) {
        if (radius <= 0.0f || intensity <= 0.0f) return;
        int argb = 0xFF000000 | (primary & 0x00FFFFFF);
        final double halfPi = Math.PI * 0.5;
        final double tau = Math.PI * 2.0;

        for (int lat = 0; lat < latSegments; lat++) {
            double v0 = lat / (double) latSegments;
            double v1 = (lat + 1) / (double) latSegments;
            double phi0 = -halfPi + Math.PI * v0;
            double phi1 = -halfPi + Math.PI * v1;

            for (int lon = 0; lon < lonSegments; lon++) {
                double u0 = lon / (double) lonSegments;
                double u1 = (lon + 1) / (double) lonSegments;
                double theta0 = tau * u0;
                double theta1 = tau * u1;

                mesh.ensureQuadCapacity();
                int i00 = sphereVertex(mesh, center, radius, phi0, theta0, u0, v0, argb,
                        progress, seed, profile, intensity, layer);
                int i10 = sphereVertex(mesh, center, radius, phi1, theta0, u0, v1, argb,
                        progress, seed, profile, intensity, layer);
                int i11 = sphereVertex(mesh, center, radius, phi1, theta1, u1, v1, argb,
                        progress, seed, profile, intensity, layer);
                int i01 = sphereVertex(mesh, center, radius, phi0, theta1, u1, v0, argb,
                        progress, seed, profile, intensity, layer);

                if (cameraInside) mesh.quad(i00, i01, i11, i10);
                else mesh.quad(i00, i10, i11, i01);
            }
        }
    }

    private static int sphereVertex(MeshBuilder mesh,
                                    Vec3 center,
                                    float radius,
                                    double phi,
                                    double theta,
                                    double u,
                                    double v,
                                    int argb,
                                    float progress,
                                    float seed,
                                    float profile,
                                    float intensity,
                                    float layer) {
        double cosPhi = Math.cos(phi);
        float nx = (float) (Math.cos(theta) * cosPhi);
        float ny = (float) Math.sin(phi);
        float nz = (float) (Math.sin(theta) * cosPhi);
        Vec3 p = center.add(nx * radius, ny * radius, nz * radius);
        return mesh.vec3(p.x, p.y, p.z)
                .vec2(u, v)
                .colorArgb(argb)
                .vec4(progress, seed, profile, intensity)
                .vec4(nx, ny, nz, layer)
                .next();
    }

    private static float seed01(long seed) {
        return (float) ((seed >>> 40) * 0x1.0p-24);
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private void renderBurstPipelines(long now) {
        Iterator<BurstVisual> iterator = activeBursts.iterator();
        while (iterator.hasNext()) {
            BurstVisual burst = iterator.next();
            long age = now - burst.spawnMs();
            if (age < 0L) continue;
            if (age >= burst.lifetimeMs()) {
                iterator.remove();
                continue;
            }

            float t = Mth.clamp(age / (float) burst.lifetimeMs(), 0.0f, 1.0f);
            float expansion = 1.0f - (float) Math.pow(1.0f - t, burst.kind() == BurstKind.SELF ? 2.15 : 2.65);
            float fade = 1.0f - smooth(Mth.clamp((t - 0.28f) / 0.72f, 0.0f, 1.0f));
            float attack = smooth(Mth.clamp(t / 0.09f, 0.0f, 1.0f));
            float envelope = attack * fade;
            if (envelope <= 0.003f) continue;

            float maxRadius = (burst.kind() == BurstKind.SELF ? 2.55f : 3.35f) * burst.scale();
            if (!burstVisible(burst.center(), maxRadius)) continue;

            float seedPhase = (float) ((burst.seed() >>> 11) * 0x1.0p-53 * Math.PI * 2.0);
            float shockRadius = Math.max(0.12f, maxRadius * (0.10f + expansion * 0.90f));
            float shockStrength = (burst.kind() == BurstKind.SELF ? 0.44f : 0.70f)
                    * envelope * (0.78f + 0.22f * (float) Math.sin(t * 19.0f + seedPhase));

            float lensRadius = shockRadius * (burst.kind() == BurstKind.SELF ? 0.78f : 0.90f);
            WorldPostProcessMasks.distortionVolumeSphere(
                    burst.center(),
                    lensRadius,
                    seedPhase - t * (burst.kind() == BurstKind.SELF ? 0.46f : 0.82f),
                    shockStrength * (burst.kind() == BurstKind.SELF ? 0.82f : 1.00f),
                    burst.kind() == BurstKind.SELF ? -0.34f : 0.62f,
                    envelope * (burst.kind() == BurstKind.SELF ? 0.64f : 0.86f),
                    burst.kind() == BurstKind.SELF ? 0.82f : 0.92f,
                    burst.kind() == BurstKind.SELF ? 0.66f : 0.88f,
                    burst.kind() == BurstKind.SELF ? 0.28f : 0.44f,
                    burst.depthTest()
            );

            float coreEnvelope = (1.0f - smooth(Mth.clamp((t - 0.02f) / 0.48f, 0.0f, 1.0f))) * attack;
            if (coreEnvelope > 0.004f) {
                WorldPostProcessMasks.distortionVolumeSphere(
                        burst.center(),
                        Math.max(0.18f, lensRadius * (0.42f + t * 0.18f)),
                        -seedPhase + t * 1.35f,
                        (burst.kind() == BurstKind.SELF ? 0.42f : 0.66f) * coreEnvelope,
                        burst.kind() == BurstKind.SELF ? 0.48f : -0.72f,
                        coreEnvelope * (burst.kind() == BurstKind.SELF ? 0.54f : 0.76f),
                        1.0f, 0.26f, burst.kind() == BurstKind.SELF ? 0.58f : 0.78f,
                        burst.depthTest()
                );
            }
        }
    }

    private static boolean burstVisible(Vec3 center, float radius) {
        double extent = Math.max(0.25, radius);
        AABB box = new AABB(
                center.x - extent, center.y - extent, center.z - extent,
                center.x + extent, center.y + extent, center.z + extent
        );
        return Renderer3D.Culling.isInFrustum(box);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static int burstPalette(int angle, float progress, boolean offensive) {
        float radians = (float) Math.toRadians(angle);
        float phase = radians * (offensive ? 1.55f : 1.20f) - progress * (offensive ? 8.8f : 6.6f);
        float mix = 0.5f + 0.5f * (float) Math.sin(phase);
        int from = offensive ? IMPACT_CORAL : TOTEM_GOLD;
        int to = offensive ? IMPACT_HOT : TOTEM_MINT;
        return mixArgb(from, to, mix);
    }

    private static int mixArgb(int a, int b, float t) {
        float clamped = Mth.clamp(t, 0.0f, 1.0f);
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int outA = Math.round(aa + (ba - aa) * clamped);
        int outR = Math.round(ar + (br - ar) * clamped);
        int outG = Math.round(ag + (bg - ag) * clamped);
        int outB = Math.round(ab + (bb - ab) * clamped);
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private void pruneExpiredBursts(long now) {
        activeBursts.removeIf(burst -> {
            long age = now - burst.spawnMs();
            return age < -250L || age >= burst.lifetimeMs();
        });
    }

    private void pruneAttackHistory(long now) {
        Iterator<Map.Entry<UUID, Long>> iterator = recentLocalAttacks.entrySet().iterator();
        while (iterator.hasNext()) {
            long age = now - iterator.next().getValue();
            if (age < 0L || age > ATTACK_HISTORY_RETENTION_MS) iterator.remove();
        }
    }

    private static long mixSeed(long now, int sourceId, long salt) {
        long z = now ^ (((long) sourceId) << 32) ^ salt;
        z ^= z >>> 30;
        z *= 0xBF58476D1CE4E5B9L;
        z ^= z >>> 27;
        z *= 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private record BurstVisual(
            Vec3 center,
            Vec3 base,
            long spawnMs,
            long lifetimeMs,
            long seed,
            float scale,
            BurstKind kind,
            boolean depthTest
    ) {
    }

    private enum BurstKind {
        SELF,
        ATTACKED
    }
}
