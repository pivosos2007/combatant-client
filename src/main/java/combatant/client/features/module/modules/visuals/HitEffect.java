/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.mixininterface.ILocalPlayer;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.effects.surface.CurrentSurfaceWaveRenderer;
import combatant.client.render.effects.surface.SurfaceWaveDescriptor;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.helpers.Particle3D;
import combatant.client.render.helpers.ParticleTextureMode;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

//todo Description
@ModuleInfo(id = "hiteffect", displayName = "HitEffect", category = ModuleCategory.VISUALS)
public class HitEffect extends Module {

    private static final String SETTING_EFFECT_MODE = "effect_mode";
    private static final String SETTING_ONLY_PLAYERS = "only_players";
    private static final String SETTING_DEPTH_TEST = "depth_test";
    private static final String SETTING_COLOR_MODE = "color_mode";
    private static final String SETTING_COLOR_SPEED = "color_speed";
    private static final String SETTING_COLOR = "color";
    private static final String SETTING_COLOR2 = "color2";
    private static final String SETTING_WAVE_RADIUS = "wave_radius";
    private static final String SETTING_WAVE_DURATION = "wave_duration";
    private static final String SETTING_HIT_PARTICLE_COUNT = "hit_particle_count";
    private static final String SETTING_HIT_PARTICLE_SIZE = "hit_particle_size";
    private static final String SETTING_HIT_PARTICLE_LIFE = "hit_particle_life_ms";
    private static final String SETTING_HIT_PARTICLE_TEXTURE = "hit_particle_texture";
    private static final String SETTING_HIT_PARTICLE_RANDOM_COLOR = "hit_particle_random_color";

    private static final float CHARGED_THRESHOLD = 0.9f;

    private static final float WAVE_WIDTH = 3.15f;
    private static final float WAVE_FILL_TRAIL = 4.25f;
    private static final float WAVE_FILL_ALPHA = 0.18f;
    private static final float WAVE_TRAIL_ALPHA = 0.055f;
    private static final int WAVE_MAX_PER_FRAME = 400;
    private static final float WAVE_MIN_ALPHA = 0.012f;

    private static final float HIT_PARTICLE_GRAVITY_MIN = 0.006f;
    private static final float HIT_PARTICLE_GRAVITY_MAX = 0.012f;
    private static final float HIT_PARTICLE_DRAG_MIN = 0.90f;
    private static final float HIT_PARTICLE_DRAG_MAX = 0.94f;
    private static final int HIT_PARTICLE_MAX = 320;
    private static final int HIT_PARTICLE_SPAWN_CAP_PER_TICK = 48;
    private static final float HIT_PARTICLE_GLOW_ALPHA = 0.20f;
    private static final float HIT_PARTICLE_GLOW_SCALE = 1.45f;

    private final Minecraft mc = Minecraft.getInstance();
    private final Random random = new Random();

    private final EnumValue<EffectMode> effectMode =
            enumSetting("hitEffectMode", SETTING_EFFECT_MODE, EffectMode.PARTICLES, EffectMode.values());
    private final ModeValue colorMode =
            modeSetting("hitEffectColorMode", SETTING_COLOR_MODE, "Theme",
                    "Static", "Rainbow", "LightRainbow", "Sky", "Fade", "DoubleColor", "Analogous", "Theme");
    private final NumberValue<Integer> colorSpeed =
            num("hitEffectColorSpeed", SETTING_COLOR_SPEED, 18, 2, 54);
    private final RGBAColorValue colorValue = color("hitEffectColor", SETTING_COLOR, "#FF8ED4FF");
    private final RGBAColorValue colorValue2 =
            visibleWhen(color("hitEffectColor2", SETTING_COLOR2, "#FFFF7D9A"), this::usesSecondaryColor);
    private final BooleanValue depthTest = bool("hitEffectDepthTest", SETTING_DEPTH_TEST, true);
    private final BooleanValue onlyPlayers = bool("hitEffectOnlyPlayers", SETTING_ONLY_PLAYERS, true);

    private final NumberValue<Integer> waveRadius =
            visibleWhen(num("hitEffectWaveRadius", SETTING_WAVE_RADIUS, 12, 3, 32), this::isWaveMode);
    private final NumberValue<Integer> waveDurationMs =
            visibleWhen(num("hitEffectWaveDuration", SETTING_WAVE_DURATION, 1500, 200, 6000), this::isWaveMode);

    private final NumberValue<Integer> hitParticleCount =
            visibleWhen(num("hitEffectParticleCount", SETTING_HIT_PARTICLE_COUNT, 18, 1, 80), this::isParticleMode);
    private final NumberValue<Float> hitParticleSize =
            visibleWhen(num("hitEffectParticleSize", SETTING_HIT_PARTICLE_SIZE, 0.105f, 0.04f, 0.4f), this::isParticleMode);
    private final NumberValue<Integer> hitParticleLifeMs =
            visibleWhen(num("hitEffectParticleLifeMs", SETTING_HIT_PARTICLE_LIFE, 1250, 250, 4500), this::isParticleMode);
    private final EnumValue<ParticleTextureMode> hitParticleTexture =
            visibleWhen(enumSetting("hitEffectParticleTexture", SETTING_HIT_PARTICLE_TEXTURE,
                    ParticleTextureMode.RANDOM, ParticleTextureMode.values()), this::isParticleMode);
    private final BooleanValue hitParticleRandomColor =
            visibleWhen(bool("hitEffectParticleRandomColor", SETTING_HIT_PARTICLE_RANDOM_COLOR, false), this::isParticleMode);

    private final List<SurfaceWaveDescriptor> waves = new ArrayList<>();
    private final List<Particle3D> hitParticles = new ArrayList<>();
    private final List<Particle3D>[] hitParticleBuckets = createBuckets();
    private int lastHitParticleTick = Integer.MIN_VALUE;
    private int spawnedHitParticlesThisTick;

    @SuppressWarnings("unchecked")
    private static List<Particle3D>[] createBuckets() {
        List<Particle3D>[] buckets = new List[TextureStorage.RANDOM_PARTICLES.length];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new ArrayList<>();
        }
        return buckets;
    }

    private static void clearBuckets(List<Particle3D>[] buckets) {
        for (List<Particle3D> bucket : buckets) {
            bucket.clear();
        }
    }

    private static float smoothstep(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static int applyOpacity(int argb, float opacity) {
        opacity = Mth.clamp(opacity, 0.0f, 1.0f);
        int a = (argb >>> 24) & 0xFF;
        int na = Mth.clamp(Math.round(a * opacity), 0, 255);
        return (na << 24) | (argb & 0x00FFFFFF);
    }

    private static void addBillboardQuad(MeshBuilder mesh,
                                         double cx, double cy, double cz,
                                         float size,
                                         Quaternionf camRot,
                                         float rollRadians,
                                         int argb) {
        Vector3f baseRight = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camRot);
        Vector3f baseUp = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camRot);

        float cos = (float) Math.cos(rollRadians);
        float sin = (float) Math.sin(rollRadians);
        Vector3f right = new Vector3f(baseRight).mul(cos).fma(sin, baseUp).mul(size);
        Vector3f up = new Vector3f(baseUp).mul(cos).fma(-sin, baseRight).mul(size);

        double p1x = cx - right.x() - up.x();
        double p1y = cy - right.y() - up.y();
        double p1z = cz - right.z() - up.z();
        double p2x = cx + right.x() - up.x();
        double p2y = cy + right.y() - up.y();
        double p2z = cz + right.z() - up.z();
        double p3x = cx + right.x() + up.x();
        double p3y = cy + right.y() + up.y();
        double p3z = cz + right.z() + up.z();
        double p4x = cx - right.x() + up.x();
        double p4y = cy - right.y() + up.y();
        double p4z = cz - right.z() + up.z();

        mesh.ensureQuadCapacity();
        RenderColor color = new RenderColor(argb);
        int i1 = mesh.vec3(p1x, p1y, p1z).vec2(0, 1).color(color).next();
        int i2 = mesh.vec3(p2x, p2y, p2z).vec2(1, 1).color(color).next();
        int i3 = mesh.vec3(p3x, p3y, p3z).vec2(1, 0).color(color).next();
        int i4 = mesh.vec3(p4x, p4y, p4z).vec2(0, 0).color(color).next();
        mesh.quad(i1, i2, i3, i4);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onDisable() {
        waves.clear();
        hitParticles.clear();
        lastHitParticleTick = Integer.MIN_VALUE;
        spawnedHitParticlesThisTick = 0;
    }

    public void handleHit(Entity target) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (!(target instanceof LivingEntity living)) return;
        if (onlyPlayers.get() && !(target instanceof Player)) return;

        if (isWaveMode()) {
            Vec3 pos = target.position().add(0.0, -0.1, 0.0);
            addWave(pos);
            return;
        }

        Vec3 hitPoint = resolveHitPoint(target);
        boolean critical = mc.player instanceof Player attacker && isCritical(attacker);
        spawnHitParticles(living, hitPoint, critical);
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        if (!isParticleMode()) {
            hitParticles.clear();
            return;
        }

        if (!hitParticles.isEmpty()) {
            hitParticles.removeIf(Particle3D::update);
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        if (isWaveMode()) {
            renderWaveEffect(renderer);
            return;
        }

        if (!hitParticles.isEmpty()) {
            renderHitParticles(renderer, tickDelta);
        }
    }

    private void renderWaveEffect(Renderer3D renderer) {
        if (waves.isEmpty() || mc.level == null) return;

        long nowMs = System.currentTimeMillis();
        Iterator<SurfaceWaveDescriptor> iterator = waves.iterator();
        while (iterator.hasNext()) {
            SurfaceWaveDescriptor wave = iterator.next();
            if (wave.isExpired(nowMs)) {
                iterator.remove();
                continue;
            }
            CurrentSurfaceWaveRenderer.render(renderer, mc.level, wave, nowMs, this::getColorArgb);
        }
    }

    private void addWave(Vec3 pos) {
        if (mc.level == null || pos == null) return;
        waves.add(new SurfaceWaveDescriptor(
                pos,
                System.currentTimeMillis(),
                Math.max(1L, waveDurationMs.get()),
                Math.max(1, waveRadius.get()),
                WAVE_WIDTH,
                WAVE_FILL_TRAIL,
                WAVE_FILL_ALPHA,
                WAVE_TRAIL_ALPHA,
                WAVE_MAX_PER_FRAME,
                WAVE_MIN_ALPHA,
                depthTest.get()
        ));
    }

    private void spawnHitParticles(LivingEntity target, Vec3 hitPoint, boolean critical) {
        int requested = Math.max(0, hitParticleCount.get());
        if (critical) {
            requested = Math.max(requested, Math.round(requested * 1.35f));
        }

        int count = reserveHitParticleBudget(requested);
        if (count <= 0) return;

        Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.52, 0.0);
        Vec3 normal = hitPoint.subtract(center);
        if (normal.lengthSqr() < 1.0e-6) {
            normal = center.subtract(mc.player.getEyePosition());
        }
        if (normal.lengthSqr() < 1.0e-6) {
            normal = new Vec3(0.0, 1.0, 0.0);
        } else {
            normal = normal.normalize();
        }

        float configuredSize = hitParticleSize.get();
        long configuredLife = Math.max(250L, hitParticleLifeMs.get());

        for (int i = 0; i < count; i++) {
            if (hitParticles.size() >= HIT_PARTICLE_MAX) {
                hitParticles.remove(0);
            }

            Vec3 tangent = randomTangent(normal);
            double jitter = randomRange(0.010, 0.055);
            Vec3 pos = hitPoint
                    .add(tangent.scale(jitter))
                    .add(normal.scale(randomRange(-0.015, 0.025)));

            double normalSpeed = randomRange(0.075, critical ? 0.175 : 0.145);
            double spreadSpeed = randomRange(0.025, critical ? 0.105 : 0.085);
            Vec3 velocity = normal.scale(normalSpeed)
                    .add(tangent.scale(spreadSpeed))
                    .add(0.0, randomRange(0.018, critical ? 0.075 : 0.060), 0.0);

            long life = Math.max(250L, Math.round(configuredLife * randomRange(0.78, 1.18)));
            long fadeIn = Math.min(95L, Math.max(35L, life / 10L));
            long fadeOut = Math.min(620L, Math.max(220L, life / 2L));
            float size = configuredSize * (float) randomRange(0.78, critical ? 1.35 : 1.22);

            int phase = target.getId() * 31 + i * 29 + (critical ? 47 : 0);
            int baseColor = getColorArgb(phase);
            int color = hitParticleRandomColor.get() ? harmonizedRandomColor(baseColor) : baseColor;
            ParticleTexture tex = resolveParticleTexture();

            float drag = (float) randomRange(HIT_PARTICLE_DRAG_MIN, HIT_PARTICLE_DRAG_MAX);
            float gravity = (float) randomRange(HIT_PARTICLE_GRAVITY_MIN, HIT_PARTICLE_GRAVITY_MAX);
            float rotationSpeed = (float) randomRange(-14.0, 14.0);

            hitParticles.add(new Particle3D(
                    pos,
                    velocity,
                    life,
                    fadeIn,
                    fadeOut,
                    size,
                    color,
                    tex.texture(),
                    tex.index(),
                    drag,
                    gravity,
                    rotationSpeed
            ));
        }
    }

    private Vec3 randomTangent(Vec3 normal) {
        Vec3 sample = new Vec3(
                randomRange(-1.0, 1.0),
                randomRange(-0.65, 1.0),
                randomRange(-1.0, 1.0)
        );
        Vec3 tangent = sample.subtract(normal.scale(sample.dot(normal)));
        if (tangent.lengthSqr() < 1.0e-6) {
            tangent = Math.abs(normal.y) < 0.9
                    ? normal.cross(new Vec3(0.0, 1.0, 0.0))
                    : normal.cross(new Vec3(1.0, 0.0, 0.0));
        }
        return tangent.normalize();
    }

    private double randomRange(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private int reserveHitParticleBudget(int requested) {
        if (requested <= 0 || mc.player == null) {
            return 0;
        }

        int tick = mc.player.tickCount;
        if (tick != lastHitParticleTick) {
            lastHitParticleTick = tick;
            spawnedHitParticlesThisTick = 0;
        }

        int remaining = HIT_PARTICLE_SPAWN_CAP_PER_TICK - spawnedHitParticlesThisTick;
        if (remaining <= 0) {
            return 0;
        }

        int granted = Math.min(requested, remaining);
        spawnedHitParticlesThisTick += granted;
        return granted;
    }

    private int harmonizedRandomColor(int baseArgb) {
        int alpha = (baseArgb >>> 24) & 0xFF;
        int r = (baseArgb >>> 16) & 0xFF;
        int g = (baseArgb >>> 8) & 0xFF;
        int b = baseArgb & 0xFF;

        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float hue = hsb[0] + (random.nextFloat() - 0.5f) * 0.18f;
        hue -= (float) Math.floor(hue);
        float saturation = Mth.clamp(hsb[1] * (0.82f + random.nextFloat() * 0.28f), 0.35f, 1.0f);
        float brightness = Mth.clamp(hsb[2] * (0.92f + random.nextFloat() * 0.16f), 0.55f, 1.0f);
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
        return (alpha << 24) | rgb;
    }

    private void renderHitParticles(Renderer3D renderer, float tickDelta) {
        boolean useDepth = depthTest.get();
        RenderPipeline pipeline = useDepth
                ? CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE
                : CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE;
        Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.MAIN;
        Quaternionf camRot = RenderState.cameraRotation;

        if (hitParticleTexture.get() == ParticleTextureMode.BLOOM) {
            MeshBuilder mesh = renderer.batchTextured(pipeline, TextureStorage.FIRE_FLY, depthMode);
            if (mesh == null) return;
            for (Particle3D particle : hitParticles) {
                renderParticle(mesh, particle, tickDelta, camRot, 1.0f, 1.0f);
            }
            return;
        }

        MeshBuilder glowMesh = renderer.batchTextured(pipeline, TextureStorage.FIRE_FLY, depthMode);
        if (glowMesh != null) {
            for (Particle3D particle : hitParticles) {
                renderParticle(glowMesh, particle, tickDelta, camRot, HIT_PARTICLE_GLOW_ALPHA, HIT_PARTICLE_GLOW_SCALE);
            }
        }

        clearBuckets(hitParticleBuckets);
        for (Particle3D particle : hitParticles) {
            int idx = particle.textureIndex();
            if (idx >= 0 && idx < hitParticleBuckets.length) {
                hitParticleBuckets[idx].add(particle);
            }
        }

        for (int i = 0; i < hitParticleBuckets.length; i++) {
            List<Particle3D> bucket = hitParticleBuckets[i];
            if (bucket.isEmpty()) continue;
            MeshBuilder mesh = renderer.batchTextured(pipeline, TextureStorage.RANDOM_PARTICLES[i], depthMode);
            if (mesh == null) continue;
            for (Particle3D particle : bucket) {
                renderParticle(mesh, particle, tickDelta, camRot, 0.92f, 1.0f);
            }
        }
    }

    private void renderParticle(MeshBuilder mesh,
                                Particle3D particle,
                                float tickDelta,
                                Quaternionf camRot,
                                float alphaMultiplier,
                                float sizeMultiplier) {
        Vec3 pos = particle.interpolate(tickDelta);
        float alpha = particle.alpha() * alphaMultiplier;
        if (alpha <= 0.008f) return;

        float t = particle.lifeProgress();
        float appear = smoothstep(t / 0.14f);
        float disappear = 1.0f - smoothstep((t - 0.70f) / 0.30f);
        float scaleEnvelope = (0.54f + appear * 0.54f) * (0.30f + disappear * 0.70f);
        float size = particle.size() * sizeMultiplier * scaleEnvelope;
        if (size <= 0.003f) return;

        int argb = applyOpacity(particle.color(), alpha);
        float rollRadians = (float) Math.toRadians(particle.rotation());
        addBillboardQuad(mesh, pos.x, pos.y, pos.z, size, camRot, rollRadians, argb);
    }

    private ParticleTexture resolveParticleTexture() {
        if (hitParticleTexture.get() == ParticleTextureMode.BLOOM) {
            return new ParticleTexture(TextureStorage.FIRE_FLY, -1);
        }
        int idx = random.nextInt(TextureStorage.RANDOM_PARTICLES.length);
        return new ParticleTexture(TextureStorage.RANDOM_PARTICLES[idx], idx);
    }

    private boolean isParticleMode() {
        return effectMode.get() == EffectMode.PARTICLES;
    }

    private boolean isWaveMode() {
        return effectMode.get() == EffectMode.WAVE;
    }

    private boolean usesSecondaryColor() {
        return AnimatedRenderColors.usesSecondary(animatedColorMode());
    }

    private int getColorArgb(int count) {
        return AnimatedRenderColors.resolve(
                animatedColorMode(),
                colorSpeed.get(),
                count,
                colorValue.getArgb(),
                colorValue2.getArgb()
        );
    }

    private AnimatedRenderColors.Mode animatedColorMode() {
        return switch (colorMode.get()) {
            case "Rainbow" -> AnimatedRenderColors.Mode.RAINBOW;
            case "LightRainbow" -> AnimatedRenderColors.Mode.LIGHT_RAINBOW;
            case "Sky" -> AnimatedRenderColors.Mode.SKY;
            case "Fade" -> AnimatedRenderColors.Mode.FADE;
            case "DoubleColor" -> AnimatedRenderColors.Mode.DOUBLE_COLOR;
            case "Analogous" -> AnimatedRenderColors.Mode.ANALOGOUS;
            case "Theme" -> AnimatedRenderColors.Mode.THEME;
            default -> AnimatedRenderColors.Mode.STATIC;
        };
    }

    private boolean isCritical(Player attacker) {
        if (!(attacker instanceof combatant.client.mixininterface.IPlayerAttackCooldown cooldownAccess)) return false;
        float cooldown = cooldownAccess.combatant$getAttackCooldownProgress(0.0f);
        if (cooldown < CHARGED_THRESHOLD) return false;
        if (attacker.onGround()) return false;
        if (attacker.onClimbable()) return false;
        if (attacker.isInWater()) return false;
        if (attacker.isPassenger()) return false;
        return attacker.fallDistance > 0.0f;
    }

    private Vec3 resolveHitPoint(Entity target) {
        ILocalPlayer access = (ILocalPlayer) mc.player;
        float yaw = access.combatant$getLastYaw();
        float pitch = access.combatant$getLastPitch();
        double distance = mc.player.distanceTo(target) + 1.0;
        Vec3 point = getRtxPoint(target, yaw, pitch, distance);
        if (point != null) return point;
        return target.position().add(0.0, target.getBbHeight() * 0.52, 0.0);
    }

    private Vec3 getRtxPoint(Entity target, float yaw, float pitch, double distance) {
        if (mc.player == null) return null;

        Vec3 start = mc.player.position().add(0, mc.player.getEyeHeight(mc.player.getPose()), 0);
        Vec3 rot = getRotationVector(pitch, yaw);
        Vec3 end = start.add(rot.scale(distance));
        AABB box = mc.player.getBoundingBox().expandTowards(rot.scale(distance)).inflate(1.0, 1.0, 1.0);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                mc.player,
                start,
                end,
                box,
                e -> !e.isSpectator() && e.isPickable() && e == target,
                distance * distance
        );

        return hit != null ? hit.getLocation() : null;
    }

    private Vec3 getRotationVector(float pitch, float yaw) {
        float yawRad = -yaw * ((float) Math.PI / 180F);
        float pitchRad = pitch * ((float) Math.PI / 180F);
        float cosYaw = Mth.cos(yawRad);
        float sinYaw = Mth.sin(yawRad);
        float cosPitch = Mth.cos(pitchRad);
        float sinPitch = Mth.sin(pitchRad);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    public enum EffectMode implements EnumValue.IdProvider, EnumValue.AliasProvider {
        PARTICLES("particles", List.of("bubbles")),
        WAVE("wave", List.of());

        private final String id;
        private final List<String> aliases;

        EffectMode(String id, List<String> aliases) {
            this.id = id;
            this.aliases = aliases;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }
    }

    private record ParticleTexture(net.minecraft.resources.Identifier texture, int index) {
    }


}
