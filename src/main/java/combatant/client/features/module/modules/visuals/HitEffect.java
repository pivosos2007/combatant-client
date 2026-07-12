/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.config.values.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import combatant.client.config.values.*;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.mixininterface.ILocalPlayer;
import combatant.client.mixininterface.IPlayerAttackCooldown;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.helpers.Particle3D;
import combatant.client.render.helpers.ParticleTextureMode;
import combatant.client.util.time.Timer;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

//todo Description
@ModuleInfo(id = "hiteffect", displayName = "HitEffect", category = ModuleCategory.VISUALS)
public class HitEffect extends Module {

    private static final String SETTING_EFFECT_MODE = "effect_mode";
    private static final String SETTING_LIFE_TIME = "life_time";
    private static final String SETTING_SCALE = "scale";
    private static final String SETTING_ONLY_PLAYERS = "only_players";
    private static final String SETTING_DEPTH_TEST = "depth_test";
    private static final String SETTING_STARS = "stars";
    private static final String SETTING_STARS_COLOR = "stars_color";
    private static final String SETTING_CRIT_STARS_COLOR = "crit_stars_color";
    private static final String SETTING_COLOR_MODE = "color_mode";
    private static final String SETTING_COLOR_SPEED = "color_speed";
    private static final String SETTING_COLOR = "color";
    private static final String SETTING_COLOR2 = "color2";
    private static final String SETTING_WAVE_RADIUS = "wave_radius";
    private static final String SETTING_WAVE_DURATION = "wave_duration";
    private static final String SETTING_HIT_PARTICLES = "hit_particles";
    private static final String SETTING_HIT_PARTICLE_COUNT = "hit_particle_count";
    private static final String SETTING_HIT_PARTICLE_SIZE = "hit_particle_size";
    private static final String SETTING_HIT_PARTICLE_LIFE = "hit_particle_life_ms";
    private static final String SETTING_HIT_PARTICLE_TEXTURE = "hit_particle_texture";
    private static final String SETTING_HIT_PARTICLE_RANDOM_COLOR = "hit_particle_random_color";
    private static final int NORMAL_STARS = 12;
    private static final int CRIT_STARS = 18;
    private static final float CHARGED_THRESHOLD = 0.9f;
    private static final float WAVE_WIDTH = 2.5f;
    private static final int WAVE_MAX_PER_FRAME = 400;
    private static final float WAVE_MIN_ALPHA = 0.02f;
    private static final double WAVE_OUTLINE_EPS = 0.002;
    private static final float WAVE_LINE_WIDTH_BASE = 0.8f;
    private static final float WAVE_LINE_WIDTH_BOOST = 0.6f;
    private static final float HIT_PARTICLE_GRAVITY = 0.02f;
    private static final float HIT_PARTICLE_DRAG = 0.94f;
    private static final int HIT_PARTICLE_MAX = 320;
    private static final int HIT_PARTICLE_SPAWN_CAP_PER_TICK = 32;
    private final Minecraft mc = Minecraft.getInstance();
    private final Random random = new Random();
    private final EnumValue<EffectMode> effectMode =
            enumSetting("hitBubblesEffectMode", SETTING_EFFECT_MODE, EffectMode.BUBBLES, EffectMode.values());
    private final NumberValue<Integer> lifeTime =
            visibleWhen(num("hitBubblesLifeTime", SETTING_LIFE_TIME, 30, 1, 150), this::isBubblesMode);
    private final NumberValue<Float> bubbleScale =
            visibleWhen(num("hitBubblesScale", SETTING_SCALE, 1.0f, 0.4f, 2.0f), this::isBubblesMode);
    private final ModeValue colorMode =
            modeSetting("hitBubblesColorMode", SETTING_COLOR_MODE, "Static",
                    "Static", "Rainbow", "LightRainbow", "Sky", "Fade", "DoubleColor", "Analogous", "Theme");
    private final NumberValue<Integer> colorSpeed =
            num("hitBubblesColorSpeed", SETTING_COLOR_SPEED, 18, 2, 54);
    private final RGBAColorValue colorValue = color("hitBubblesColor", SETTING_COLOR, "#FF55FFFF");
    private final RGBAColorValue colorValue2 =
            visibleWhen(color("hitBubblesColor2", SETTING_COLOR2, "#FFFF5555"), this::usesSecondaryColor);
    private final BooleanValue depthTest =
            bool("hitBubblesDepthTest", SETTING_DEPTH_TEST, true);
    private final BooleanValue onlyPlayers =
            bool("hitBubblesOnlyPlayers", SETTING_ONLY_PLAYERS, true);
    private final BooleanValue starsEnabled =
            visibleWhen(bool("hitBubblesStarsEnabled", SETTING_STARS, true), this::isBubblesMode);
    private final RGBAColorValue starsColor =
            visibleWhen(color("hitBubblesStarsColor", SETTING_STARS_COLOR, "#FFFFE16B"),
                    () -> isBubblesMode() && starsEnabled.get());
    private final RGBAColorValue critStarsColor =
            visibleWhen(color("hitBubblesCritStarsColor", SETTING_CRIT_STARS_COLOR, "#FFFF3A2E"),
                    () -> isBubblesMode() && starsEnabled.get());
    private final NumberValue<Integer> waveRadius =
            visibleWhen(num("hitBubblesWaveRadius", SETTING_WAVE_RADIUS, 12, 3, 32), this::isWaveMode);
    private final NumberValue<Integer> waveDurationMs =
            visibleWhen(num("hitBubblesWaveDuration", SETTING_WAVE_DURATION, 1500, 200, 6000), this::isWaveMode);
    private final BooleanValue hitParticlesEnabled =
            bool("hitEffectParticlesEnabled", SETTING_HIT_PARTICLES, false);
    private final NumberValue<Integer> hitParticleCount =
            visibleWhen(num("hitEffectParticleCount", SETTING_HIT_PARTICLE_COUNT, 15, 1, 80), hitParticlesEnabled::get);
    private final NumberValue<Float> hitParticleSize =
            visibleWhen(num("hitEffectParticleSize", SETTING_HIT_PARTICLE_SIZE, 0.12f, 0.04f, 0.4f), hitParticlesEnabled::get);
    private final NumberValue<Integer> hitParticleLifeMs =
            visibleWhen(num("hitEffectParticleLifeMs", SETTING_HIT_PARTICLE_LIFE, 2200, 200, 6000), hitParticlesEnabled::get);
    private final EnumValue<ParticleTextureMode> hitParticleTexture =
            visibleWhen(enumSetting("hitEffectParticleTexture", SETTING_HIT_PARTICLE_TEXTURE, ParticleTextureMode.BLOOM, ParticleTextureMode.values()),
                    hitParticlesEnabled::get);
    private final BooleanValue hitParticleRandomColor =
            visibleWhen(bool("hitEffectParticleRandomColor", SETTING_HIT_PARTICLE_RANDOM_COLOR, false), hitParticlesEnabled::get);
    private final List<HitBubble> bubbles = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<WaveEffect> waves = new ArrayList<>();
    private final List<Particle3D> hitParticles = new ArrayList<>();
    private final List<Particle3D>[] hitParticleBuckets = createBuckets();
    private int lastHitParticleTick = Integer.MIN_VALUE;
    private int spawnedHitParticlesThisTick = 0;

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

    private static int applyOpacity(int argb, float opacity) {
        opacity = Math.min(1f, Math.max(0f, opacity));
        int a = (argb >>> 24) & 0xFF;
        int na = Mth.clamp((int) (a * opacity), 0, 255);
        return (na << 24) | (argb & 0x00FFFFFF);
    }

    private static void addOrientedQuad(MeshBuilder mesh, Vec3 center, float yaw, float pitch,
                                        float spinDeg, float scale, int c1, int c2, int c3, int c4) {
        Quaternionf rot = new Quaternionf()
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(pitch));

        Vector3f forward = new Vector3f(0, 0, 1).rotate(rot);
        Vector3f right = new Vector3f(1, 0, 0).rotate(rot);
        Vector3f up = new Vector3f(0, 1, 0).rotate(rot);

        Quaternionf spin = new Quaternionf().fromAxisAngleRad(forward, (float) Math.toRadians(spinDeg));
        right.rotate(spin);
        up.rotate(spin);

        right.mul(scale / 2f);
        up.mul(scale / 2f);

        double p1x = center.x - right.x() - up.x();
        double p1y = center.y - right.y() - up.y();
        double p1z = center.z - right.z() - up.z();
        double p2x = center.x + right.x() - up.x();
        double p2y = center.y + right.y() - up.y();
        double p2z = center.z + right.z() - up.z();
        double p3x = center.x + right.x() + up.x();
        double p3y = center.y + right.y() + up.y();
        double p3z = center.z + right.z() + up.z();
        double p4x = center.x - right.x() + up.x();
        double p4y = center.y - right.y() + up.y();
        double p4z = center.z - right.z() + up.z();

        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(p1x, p1y, p1z).vec2(0, 1).color(new RenderColor(c1)).next();
        int i2 = mesh.vec3(p2x, p2y, p2z).vec2(1, 1).color(new RenderColor(c2)).next();
        int i3 = mesh.vec3(p3x, p3y, p3z).vec2(1, 0).color(new RenderColor(c3)).next();
        int i4 = mesh.vec3(p4x, p4y, p4z).vec2(0, 0).color(new RenderColor(c4)).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void addBillboardQuad(MeshBuilder mesh, double cx, double cy, double cz,
                                         float size, Quaternionf camRot, int argb) {
        Vector3f right = new Vector3f(1, 0, 0).rotate(camRot).mul(size);
        Vector3f up = new Vector3f(0, 1, 0).rotate(camRot).mul(size);

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
        int i1 = mesh.vec3(p1x, p1y, p1z).vec2(0, 1).color(new RenderColor(argb)).next();
        int i2 = mesh.vec3(p2x, p2y, p2z).vec2(1, 1).color(new RenderColor(argb)).next();
        int i3 = mesh.vec3(p3x, p3y, p3z).vec2(1, 0).color(new RenderColor(argb)).next();
        int i4 = mesh.vec3(p4x, p4y, p4z).vec2(0, 0).color(new RenderColor(argb)).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void addShapeOutline(MeshBuilder mesh, BlockPos pos, VoxelShape shape, int argb, double eps) {
        if (mesh == null || shape == null) return;

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        VoxelShape offset = shape.move(pos.getX(), pos.getY(), pos.getZ());
        offset.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.5;
            double cz = pos.getZ() + 0.5;

            double sx1 = Math.signum(x1 - cx);
            double sy1 = Math.signum(y1 - cy);
            double sz1 = Math.signum(z1 - cz);
            double sx2 = Math.signum(x2 - cx);
            double sy2 = Math.signum(y2 - cy);
            double sz2 = Math.signum(z2 - cz);

            double ax1 = x1 + sx1 * eps;
            double ay1 = y1 + sy1 * eps;
            double az1 = z1 + sz1 * eps;
            double ax2 = x2 + sx2 * eps;
            double ay2 = y2 + sy2 * eps;
            double az2 = z2 + sz2 * eps;

            mesh.ensureLineCapacity();
            int i1 = mesh.vec3(ax1, ay1, az1).color(r, g, b, a).next();
            int i2 = mesh.vec3(ax2, ay2, az2).color(r, g, b, a).next();
            mesh.line(i1, i2);
        });
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onDisable() {
        bubbles.clear();
        stars.clear();
        waves.clear();
        hitParticles.clear();
        lastHitParticleTick = Integer.MIN_VALUE;
        spawnedHitParticlesThisTick = 0;
    }

    public void handleHit(Entity target) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (!(target instanceof LivingEntity)) return;
        if (onlyPlayers.get() && !(target instanceof Player)) return;

        if (effectMode.get() == EffectMode.WAVE) {
            Vec3 pos = target.position();
            BlockPos basePos = BlockPos.containing(pos.x, pos.y - 0.1, pos.z);
            addWave(basePos);
        } else {
            ILocalPlayer access = (ILocalPlayer) mc.player;
            float yaw = access.combatant$getLastYaw();
            float pitch = access.combatant$getLastPitch();

            double distance = mc.player.distanceTo(target) + 1.0;
            Vec3 point = getRtxPoint(target, yaw, pitch, distance);
            if (point == null) point = target.position();

            bubbles.add(new HitBubble(point, -yaw, pitch, new Timer()));

            if (starsEnabled.get() && mc.player instanceof IPlayerAttackCooldown cooldownAccess) {
                float cooldown = cooldownAccess.combatant$getAttackCooldownProgress(0.0f);
                if (cooldown >= CHARGED_THRESHOLD) {
                    boolean crit = isCritical(mc.player, cooldown);
                    spawnStars(point, crit);
                }
            }
        }

        if (hitParticlesEnabled.get()) {
            spawnHitParticles((LivingEntity) target);
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        if (effectMode.get() != EffectMode.BUBBLES || !starsEnabled.get()) {
            stars.clear();
        } else if (!stars.isEmpty()) {
            stars.removeIf(Star::update);
        }

        if (!hitParticles.isEmpty()) {
            hitParticles.removeIf(Particle3D::update);
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        if (hitParticlesEnabled.get() && !hitParticles.isEmpty()) {
            renderHitParticles(renderer, tickDelta);
        }

        if (effectMode.get() == EffectMode.WAVE) {
            renderWaveEffect(renderer);
            return;
        }

        renderBubbleEffect(renderer, tickDelta);
    }

    private void renderBubbleEffect(Renderer3D renderer, float tickDelta) {
        boolean useDepth = depthTest.get();
        RenderPipeline pipeline = useDepth
                ? CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE
                : CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE;
        Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.MAIN;

        if (!bubbles.isEmpty()) {
            MeshBuilder mesh = renderer.batchTextured(pipeline, TextureStorage.BUBBLE, depthMode);
            if (mesh == null) return;

            for (HitBubble b : bubbles) {
                float lifeMs = b.life.getPassedTimeMs();
                float lifeTotal = Math.max(1f, lifeTime.get() * 50f);
                float angle = -lifeMs / 4f;
                float factor = Math.min(1f, lifeMs / lifeTotal);
                float scale = factor * 2f * bubbleScale.get();
                if (scale <= 0.01f) continue;

                int c1 = applyOpacity(getColorArgb(270), 1f - factor);
                int c2 = applyOpacity(getColorArgb(0), 1f - factor);
                int c3 = applyOpacity(getColorArgb(180), 1f - factor);
                int c4 = applyOpacity(getColorArgb(90), 1f - factor);

                addOrientedQuad(mesh, b.pos, b.yaw, b.pitch, angle, scale, c1, c2, c3, c4);
            }

            bubbles.removeIf(b -> b.life.passedMs(lifeTime.get() * 50L));
        }

        if (starsEnabled.get() && !stars.isEmpty()) {
            MeshBuilder mesh = renderer.batchTextured(pipeline, TextureStorage.PARTICLE_STARS, depthMode);
            if (mesh == null) return;

            Quaternionf camRot = RenderState.cameraRotation;

            for (Star s : stars) {
                Vec3 pos = s.interpolate(tickDelta);
                float t = s.getLifeProgress();
                float scale = s.size * (1f - t);
                if (scale <= 0.01f) continue;

                int argb = applyOpacity(s.color, 1f - t);
                addBillboardQuad(mesh, pos.x, pos.y, pos.z, scale, camRot, argb);
            }

        }
    }

    private void renderWaveEffect(Renderer3D renderer) {
        if (waves.isEmpty()) return;

        Iterator<WaveEffect> iterator = waves.iterator();
        while (iterator.hasNext()) {
            WaveEffect wave = iterator.next();
            if (wave.isExpired()) {
                iterator.remove();
                continue;
            }
            wave.render(renderer);
        }
    }

    private void addWave(BlockPos pos) {
        if (mc.level != null) {
            waves.add(new WaveEffect(pos, System.currentTimeMillis()));
        }
    }

    private void spawnHitParticles(LivingEntity target) {
        int count = reserveHitParticleBudget(Math.max(0, hitParticleCount.get()));
        if (count <= 0) return;

        int baseColor = getColorArgb(target.getId() % 360);
        float size = hitParticleSize.get();
        long life = hitParticleLifeMs.get();
        long fadeIn = Math.min(250L, life / 4L);
        long fadeOut = Math.min(900L, life / 2L);

        for (int i = 0; i < count; i++) {
            if (hitParticles.size() >= HIT_PARTICLE_MAX) {
                hitParticles.remove(0);
            }

            double px = target.getX() + random.nextDouble() * 0.6 - 0.3;
            double py = target.getY() + random.nextDouble() * Math.max(0.2, target.getBbHeight());
            double pz = target.getZ() + random.nextDouble() * 0.6 - 0.3;

            double motion = 0.06;
            Vec3 vel = new Vec3(
                    random.nextDouble() * motion * 2 - motion,
                    random.nextDouble() * 0.04 - 0.02,
                    random.nextDouble() * motion * 2 - motion
            );

            int color = hitParticleRandomColor.get() ? randomColor() : baseColor;
            ParticleTexture tex = resolveParticleTexture();

            hitParticles.add(new Particle3D(
                    new Vec3(px, py, pz),
                    vel,
                    life,
                    fadeIn,
                    fadeOut,
                    size,
                    color,
                    tex.texture(),
                    tex.index(),
                    HIT_PARTICLE_DRAG,
                    HIT_PARTICLE_GRAVITY,
                    (random.nextFloat() - 0.5f) * 8f
            ));
        }
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

    private int randomColor() {
        float h = random.nextFloat();
        float s = 0.7f + random.nextFloat() * 0.3f;
        float v = 0.8f + random.nextFloat() * 0.2f;
        return Color.HSBtoRGB(h, s, v) | 0xFF000000;
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
            for (Particle3D p : hitParticles) {
                renderParticle(mesh, p, tickDelta, camRot);
            }
            return;
        }

        clearBuckets(hitParticleBuckets);
        for (Particle3D p : hitParticles) {
            int idx = p.textureIndex();
            if (idx >= 0 && idx < hitParticleBuckets.length) {
                hitParticleBuckets[idx].add(p);
            }
        }

        for (int i = 0; i < hitParticleBuckets.length; i++) {
            List<Particle3D> bucket = hitParticleBuckets[i];
            if (bucket.isEmpty()) continue;
            MeshBuilder mesh = renderer.batchTextured(pipeline, TextureStorage.RANDOM_PARTICLES[i], depthMode);
            if (mesh == null) continue;
            for (Particle3D p : bucket) {
                renderParticle(mesh, p, tickDelta, camRot);
            }
        }
    }

    private void renderParticle(MeshBuilder mesh, Particle3D particle, float tickDelta, Quaternionf camRot) {
        Vec3 pos = particle.interpolate(tickDelta);
        float alpha = particle.alpha();
        if (alpha <= 0.01f) return;
        int argb = applyOpacity(particle.color(), alpha);
        addBillboardQuad(mesh, pos.x, pos.y, pos.z, particle.size(), camRot, argb);
    }

    private ParticleTexture resolveParticleTexture() {
        if (hitParticleTexture.get() == ParticleTextureMode.BLOOM) {
            return new ParticleTexture(TextureStorage.FIRE_FLY, -1);
        }
        int idx = random.nextInt(TextureStorage.RANDOM_PARTICLES.length);
        return new ParticleTexture(TextureStorage.RANDOM_PARTICLES[idx], idx);
    }

    private boolean isBubblesMode() {
        return effectMode.get() == EffectMode.BUBBLES;
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

    private boolean isCritical(Player attacker, float cooldown) {
        if (cooldown < CHARGED_THRESHOLD) return false;
        if (attacker.onGround()) return false;
        if (attacker.onClimbable()) return false;
        if (attacker.isInWater()) return false;
        if (attacker.isPassenger()) return false;
        return attacker.fallDistance > 0.0f;
    }

    private void spawnStars(Vec3 pos, boolean crit) {
        int count = crit ? CRIT_STARS : NORMAL_STARS;
        int color = crit ? critStarsColor.getArgb() : starsColor.getArgb();

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.14 + random.nextDouble() * 0.14;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = 0.12 + random.nextDouble() * 0.12;

            long life = 500 + random.nextInt(250);
            float size = 0.18f + random.nextFloat() * 0.12f;

            stars.add(new Star(pos, new Vec3(vx, vy, vz), life, size, color));
        }
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

    public enum EffectMode {
        BUBBLES,
        WAVE
    }

    private record ParticleTexture(Identifier texture, int index) {
    }

    private record HitBubble(Vec3 pos, float yaw, float pitch, Timer life) {
    }

    // textured rendering handled by Renderer3D batching

    private static class Star {
        private final Timer timer;
        private final long lifeMs;
        private final float size;
        private final int color;
        private Vec3 pos;
        private Vec3 prevPos;
        private Vec3 velocity;

        private Star(Vec3 pos, Vec3 velocity, long lifeMs, float size, int color) {
            this.pos = pos;
            this.prevPos = pos;
            this.velocity = velocity;
            this.timer = new Timer();
            this.lifeMs = lifeMs;
            this.size = size;
            this.color = color;
        }

        private boolean update() {
            prevPos = pos;
            pos = pos.add(velocity);
            velocity = velocity.multiply(0.94, 0.94, 0.94).add(0.0, -0.03, 0.0);
            return timer.passedMs(lifeMs);
        }

        private Vec3 interpolate(float tickDelta) {
            return prevPos.lerp(pos, tickDelta);
        }

        private float getLifeProgress() {
            return Math.min(1f, timer.getPassedTimeMs() / (float) lifeMs);
        }
    }

    private class WaveEffect {
        private final BlockPos centerPos;
        private final long startTime;

        private WaveEffect(BlockPos centerPos, long startTime) {
            this.centerPos = centerPos;
            this.startTime = startTime;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - startTime > Math.max(1L, waveDurationMs.get());
        }

        private void render(Renderer3D renderer) {
            if (mc.level == null) return;
            if (renderer == null) return;

            long durationMs = Math.max(1L, waveDurationMs.get());
            int maxRadius = Math.max(1, waveRadius.get());
            long elapsed = System.currentTimeMillis() - startTime;
            float progress = Mth.clamp((float) elapsed / durationMs, 0f, 1f);

            float currentRadius = progress * maxRadius;
            float globalAlpha = (float) Math.pow(1.0f - progress, 0.6);

            float minRadSq = (currentRadius - WAVE_WIDTH) * (currentRadius - WAVE_WIDTH);
            float maxRadSq = (currentRadius + 0.5f) * (currentRadius + 0.5f);

            float prevWidth = RenderState.lineWidth;
            RenderState.lineWidth = Math.max(0.5f, WAVE_LINE_WIDTH_BASE + globalAlpha * WAVE_LINE_WIDTH_BOOST);
            try {
                boolean useDepth = depthTest.get();
                RenderPipeline pipeline = useDepth
                        ? CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_IGNORE
                        : CombatantRenderPipelines.WORLD_COLORED_LINES;
                Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.MAIN;
                MeshBuilder mesh = renderer.batch(pipeline, depthMode);
                if (mesh == null) return;

                int rendered = 0;

                for (int x = -maxRadius; x <= maxRadius; x++) {
                    for (int z = -maxRadius; z <= maxRadius; z++) {
                        if (rendered >= WAVE_MAX_PER_FRAME) return;

                        float distSq = x * x + z * z;
                        if (distSq < minRadSq || distSq > maxRadSq) continue;

                        BlockPos checkPos = centerPos.offset(x, 0, z);
                        BlockPos renderPos = findSurface(checkPos);
                        if (renderPos == null) continue;

                        BlockState state = mc.level.getBlockState(renderPos);
                        VoxelShape shape = state.getShape(mc.level, renderPos);
                        if (shape.isEmpty()) continue;

                        rendered++;

                        float distance = (float) Math.sqrt(distSq);
                        float localAlpha = 1.0f - Math.abs(distance - currentRadius) / WAVE_WIDTH;
                        localAlpha = Math.max(0f, Math.min(1f, localAlpha)) * globalAlpha;
                        if (localAlpha <= WAVE_MIN_ALPHA) continue;

                        int colorIndex = (int) (Math.toDegrees(Math.atan2(z, x)) + 180.0);
                        int color = applyOpacity(getColorArgb(colorIndex), localAlpha);

                        addShapeOutline(mesh, renderPos, shape, color, WAVE_OUTLINE_EPS);
                    }
                }
            } finally {
                RenderState.lineWidth = prevWidth;
            }
        }

        private BlockPos findSurface(BlockPos pos) {
            for (int y = 2; y >= -4; y--) {
                BlockPos p = pos.above(y);
                if (!mc.level.getBlockState(p).isAir() && mc.level.getBlockState(p.above()).isAir()) {
                    return p;
                }
            }
            return null;
        }
    }
}
