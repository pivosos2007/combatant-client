/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.mixins.accessors.ClientLevelAccessor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.color.ColorUtils;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.PostProcessUniforms;
import combatant.client.util.time.Timer;
import combatant.client.util.sound.SoundAsset;
import combatant.client.util.sound.SoundCatalog;
import combatant.client.util.sound.SoundKey;
import combatant.client.util.sound.SoundOptions;

import java.awt.*;
import java.util.*;
import java.util.List;

//todo Description
@ModuleInfo(id = "killeffect", displayName = "KillEffect", category = ModuleCategory.VISUALS)
public class KillEffect extends Module implements PostProcessPass {
    private static final int MAX_EMBERS = 26;
    private static final int MIN_EMBERS = 16;
    private static final int LIGHTNING_POINTS = 200;
    private static final double LIGHTNING_STEP_Y = 0.25;
    private static final float LIGHTNING_STEP_SPREAD = 0.25f;
    private static final long LIGHTNING_APPEAR_MS = 1200L;
    private static final long LIGHTNING_FADE_MS = 250L;
    private static final long LIGHTNING_LIFE_MS = LIGHTNING_APPEAR_MS + LIGHTNING_FADE_MS;
    private static final float LIGHTNING_MIN_SIZE = 0.26f;
    private static final float LIGHTNING_MAX_SIZE = 0.78f;
    private static final float LIGHTNING_ALPHA = 0.40f;
    private static final long KILL_BLUR_ATTACK_MS = 55L;
    private static final long KILL_BLUR_DURATION_MS = 520L;
    private static final String SETTING_MODE = "mode";
    private static final String SETTING_Y_SPEED = "y_speed";
    private static final String SETTING_PLAY_SOUND = "play_sound";
    private static final String SETTING_SOUND_VOLUME = "sound_volume";
    private static final String SETTING_COLOR = "color";
    private static final String SETTING_MOBS = "mobs";
    private static final String SETTING_KILL_BLUR = "kill_blur";
    private static final long EMBER_GROUND_EXTRA_MS = 0;
    private final Minecraft mc = Minecraft.getInstance();
    private final Random random = new Random();
    private final ModeValue mode =
            modeSetting("killEffectMode", SETTING_MODE, "Orthodox", "Orthodox", "FallingLava", "EmbersArc", "Lightning");
    private final NumberValue<Integer> ySpeed =
            visibleWhen(num("killEffectYSpeed", SETTING_Y_SPEED, 0, -10, 10), () -> "Orthodox".equals(mode.get()));
    private final BooleanValue playSound =
            visibleWhen(bool("killEffectPlaySound", SETTING_PLAY_SOUND, true), () -> "Orthodox".equals(mode.get()));
    private final NumberValue<Double> soundVolume =
            visibleWhen(num("killEffectSoundVolume", SETTING_SOUND_VOLUME, 1.0, 0.0, 2.0),
                    () -> "Orthodox".equals(mode.get()) && playSound.get());
    private final RGBAColorValue color =
            visibleWhen(color("killEffectColor", SETTING_COLOR, "#FFFF9600"),
                    () -> "Orthodox".equals(mode.get()) || "Lightning".equals(mode.get()));
    private final BooleanValue mobs = bool("killEffectMobs", SETTING_MOBS, false);
    private final BooleanValue killBlur = bool("killEffectKillBlur", SETTING_KILL_BLUR, true);
    private final Map<Integer, Long> handled = new HashMap<>();
    private final List<OrthodoxMark> orthodoxMarks = new ArrayList<>();
    private final List<Ember> embers = new ArrayList<>();
    private final List<FlashRing> flashes = new ArrayList<>();
    private final List<LightningStrike> lightningStrikes = new ArrayList<>();
    private long killBlurStartedMs;

    {
        PostProcessManager.register(this);
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

    private static void addGroundQuad(MeshBuilder mesh, Vec3 center, float size, float angleDeg, int argb) {
        double radians = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        Vec3 right = new Vec3(cos, 0.0, -sin);
        Vec3 forward = new Vec3(sin, 0.0, cos);

        Vec3 p1 = center.add(right.scale(-size)).add(forward.scale(size));
        Vec3 p2 = center.add(right.scale(size)).add(forward.scale(size));
        Vec3 p3 = center.add(right.scale(size)).add(forward.scale(-size));
        Vec3 p4 = center.add(right.scale(-size)).add(forward.scale(-size));

        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(p1.x, p1.y, p1.z).vec2(0, 1).color(new RenderColor(argb)).next();
        int i2 = mesh.vec3(p2.x, p2.y, p2.z).vec2(1, 1).color(new RenderColor(argb)).next();
        int i3 = mesh.vec3(p3.x, p3.y, p3.z).vec2(1, 0).color(new RenderColor(argb)).next();
        int i4 = mesh.vec3(p4.x, p4.y, p4.z).vec2(0, 0).color(new RenderColor(argb)).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static int withAlpha(int argb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static float smoothProgress(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        return clamped * clamped * (3f - 2f * clamped);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public boolean isActive() {
        return isEnabled() && killBlur.get() && mc.player != null && mc.level != null
                && getKillBlurStrength(System.currentTimeMillis()) > 0.001f;
    }

    @Override
    public int getPriority() {
        return 15;
    }

    @Override
    public Phase getPhase() {
        return Phase.POST_HAND;
    }

    @Override
    public boolean render(GpuTextureView src, GpuTextureView dst, float tickDelta) {
        if (!isEnabled() || !killBlur.get() || src == null || dst == null) return false;

        float strength = getKillBlurStrength(System.currentTimeMillis());
        if (strength <= 0.001f) return false;

        PostProcessUniforms.update(strength, 0.0f, 0.0f, 0.0f);
        FullScreenRenderer.begin("Combatant Kill Blur")
                .attachment(dst)
                .pipeline(CombatantRenderPipelines.KILL_BLUR)
                .uniform("PostProcess", PostProcessUniforms.get())
                .sampler("u_Texture", src, PostProcessManager.getSampler())
                .end();
        return true;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        handled.entrySet().removeIf(e -> now - e.getValue() > 6000);

        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(128), e -> true)) {
            if (entity == mc.player) continue;
            if (!mobs.get() && !(entity instanceof Player)) continue;
            if (entity.isAlive() || entity.getHealth() > 0) continue;

            int id = entity.getId();
            if (handled.containsKey(id)) continue;
            handled.put(id, now);
            if (killBlur.get()) {
                killBlurStartedMs = now;
            }

            Vec3 pos = entity.position();

            if ("Orthodox".equals(mode.get())) {
                orthodoxMarks.add(new OrthodoxMark(pos, now));
                if (playSound.get()) {
                    KillSound.ORTHODOX.play(SoundOptions.at(pos).withGain(soundVolume.get()));
                }
            } else if ("FallingLava".equals(mode.get())) {
                spawnFallingLava(entity);
            } else if ("EmbersArc".equals(mode.get())) {
                spawnEmbers(pos);
            } else if ("Lightning".equals(mode.get())) {
                lightningStrikes.add(new LightningStrike(pos, color.getArgb(), now));
            }
        }

        if (!embers.isEmpty()) {
            embers.removeIf(e -> e.update(mc.level));
        }

        if (!flashes.isEmpty()) {
            flashes.removeIf(f -> f.timer.passedMs(f.lifeMs));
        }

        if (!lightningStrikes.isEmpty()) {
            lightningStrikes.removeIf(LightningStrike::isExpired);
        }

        if (!orthodoxMarks.isEmpty()) {
            orthodoxMarks.removeIf(m -> now - m.createdAtMs > 3000);
        }
    }

    @SoundCatalog(namespace = "combatant", root = "sounds/misc", idPrefix = "kill_effect")
    private enum KillSound implements SoundKey {
        @SoundAsset("orthodox.wav")
        ORTHODOX
    }

    @Override
    public void onDisable() {
        handled.clear();
        orthodoxMarks.clear();
        embers.clear();
        flashes.clear();
        lightningStrikes.clear();
        killBlurStartedMs = 0L;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        if ("Orthodox".equals(mode.get())) {
            renderOrthodox(renderer);
        } else if ("Lightning".equals(mode.get())) {
            renderLightning(renderer);
        } else if ("EmbersArc".equals(mode.get())) {
            renderEmbers(renderer, tickDelta);
            renderFlashRings(renderer);
        }
    }

    private void renderOrthodox(Renderer3D renderer) {
        int argb = color.getArgb();
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        int a = (argb >> 24) & 255;

        double offset = ySpeed.get() / 100.0;

        for (OrthodoxMark mark : orthodoxMarks) {
            Vec3 pos = mark.pos;
            renderer.line(pos.x, pos.y + offset, pos.z,
                    pos.x, pos.y + 3.0 + offset, pos.z, r, g, b, a);
            renderer.line(pos.x + 1.0, pos.y + 2.3 + offset, pos.z,
                    pos.x - 1.0, pos.y + 2.3 + offset, pos.z, r, g, b, a);
            renderer.line(pos.x + 0.5, pos.y + 1.2 + offset, pos.z,
                    pos.x - 0.5, pos.y + 0.8 + offset, pos.z, r, g, b, a);
        }
    }

    private void renderEmbers(Renderer3D renderer, float tickDelta) {
        if (embers.isEmpty()) return;

        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE,
                TextureStorage.FIRE_FLY,
                Renderer3D.DepthMode.MAIN
        );
        if (mesh == null) return;

        Quaternionf camRot = RenderState.cameraRotation;

        for (int i = 0; i < embers.size(); i++) {
            Ember e = embers.get(i);
            Vec3 pos = e.interpolate(tickDelta);

            float lifeT = e.getLifeProgress();
            Color base = new Color(255, 20, 0, 255);
            Color hot = new Color(255, 220, 80, 255);
            Color col = ColorUtils.interpolateColorC(base, hot, 1f - lifeT);

            int alpha = (int) (255f * (1f - lifeT));
            int argb = withAlpha(col.getRGB(), alpha);

            float size = e.size * (1f - lifeT);
            if (size <= 0.01f) continue;

            addBillboardQuad(mesh, pos.x, pos.y, pos.z, size, camRot, argb);
        }

    }

    private void renderFlashRings(Renderer3D renderer) {
        if (flashes.isEmpty()) return;

        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE,
                TextureStorage.DEFAULT_CIRCLE,
                Renderer3D.DepthMode.MAIN
        );
        if (mesh == null) return;

        for (FlashRing ring : flashes) {
            float t = Math.min(1f, ring.timer.getPassedTimeMs() / (float) ring.lifeMs);
            float size = 0.4f + t * 1.8f;
            float alpha = (float) Math.pow(1f - t, 1.6);

            Color base = new Color(255, 60, 0, 255);
            Color hot = new Color(255, 220, 120, 255);
            Color col = ColorUtils.interpolateColorC(hot, base, t);
            int argb = withAlpha(col.getRGB(), (int) (255f * alpha));

            float angle = t * 180f;
            addGroundQuad(mesh, ring.pos, size, angle, argb);
        }

    }

    private void renderLightning(Renderer3D renderer) {
        if (lightningStrikes.isEmpty()) return;

        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE,
                TextureStorage.FIRE_FLY,
                Renderer3D.DepthMode.MAIN
        );
        if (mesh == null) return;

        Quaternionf camRot = RenderState.cameraRotation;

        for (LightningStrike strike : lightningStrikes) {
            float alpha = strike.getAlpha();
            if (alpha <= 0.01f) continue;

            int colorArgb = withAlpha(strike.colorArgb, (int) (255f * alpha * LIGHTNING_ALPHA));
            int pointCount = Math.max(1, strike.points.size() - 1);

            for (int i = 0; i < strike.points.size(); i++) {
                float progress = i / (float) pointCount;
                float size = LIGHTNING_MIN_SIZE + (LIGHTNING_MAX_SIZE - LIGHTNING_MIN_SIZE) * progress;
                Vec3 point = strike.points.get(i);
                addBillboardQuad(mesh, point.x, point.y, point.z, size, camRot, colorArgb);
            }
        }
    }

    private void spawnEmbers(Vec3 pos) {
        int count = MIN_EMBERS + random.nextInt(MAX_EMBERS - MIN_EMBERS + 1);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.18 + random.nextDouble() * 0.18;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            double vy = 0.28 + random.nextDouble() * 0.20;

            long life = 4000 + random.nextInt(401);
            float size = 0.24f + random.nextFloat() * 0.18f;
            embers.add(new Ember(pos, new Vec3(vx, vy, vz), life, size));
        }
        flashes.add(new FlashRing(pos, 240));
    }

    private void spawnFallingLava(LivingEntity entity) {
        if (mc.level == null) return;
        int height = (int) (entity.getBbHeight() * 10);
        int width = (int) (entity.getBbWidth() * 10);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                for (int k = 0; k < width; k++) {
                    ((ClientLevelAccessor) mc.level).invokeAddParticle(
                            ParticleTypes.FALLING_LAVA,
                            false,
                            true,
                            entity.getX() + j * 0.1,
                            entity.getY() + i * 0.1,
                            entity.getZ() + k * 0.1,
                            0, 0, 0
                    );
                }
            }
        }
    }

    private float getKillBlurStrength(long nowMs) {
        if (killBlurStartedMs <= 0L) return 0.0f;
        long age = nowMs - killBlurStartedMs;
        if (age < 0L || age >= KILL_BLUR_DURATION_MS) return 0.0f;

        if (age < KILL_BLUR_ATTACK_MS) {
            return smoothProgress(age / (float) KILL_BLUR_ATTACK_MS);
        }

        float release = (age - KILL_BLUR_ATTACK_MS)
                / (float) Math.max(1L, KILL_BLUR_DURATION_MS - KILL_BLUR_ATTACK_MS);
        float eased = 1.0f - smoothProgress(release);
        return (float) Math.pow(Math.max(0.0f, eased), 1.25);
    }

    private float randomRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    // textured rendering handled by Renderer3D batching

    private record OrthodoxMark(Vec3 pos, long createdAtMs) {
    }

    private static class Ember {
        private final Timer timer;
        private final long lifeMs;
        private final float size;
        private Vec3 pos;
        private Vec3 prevPos;
        private Vec3 velocity;
        private boolean grounded;
        private long groundExtraMs;

        private Ember(Vec3 pos, Vec3 velocity, long lifeMs, float size) {
            this.pos = pos;
            this.prevPos = pos;
            this.velocity = velocity;
            this.timer = new Timer();
            this.lifeMs = lifeMs;
            this.size = size;
        }

        private boolean update(ClientLevel world) {
            prevPos = pos;
            pos = pos.add(velocity);

            if (!grounded && world != null) {
                var blockPos = net.minecraft.core.BlockPos.containing(pos.x, pos.y - 0.02, pos.z);
                boolean solid = !world.getBlockState(blockPos).isAir();
                if (solid) {
                    grounded = true;
                    velocity = new Vec3(0.0, 0.0, 0.0);
                    groundExtraMs = EMBER_GROUND_EXTRA_MS;
                    pos = new Vec3(pos.x, blockPos.getY() + 1.001, pos.z);
                }
            }

            if (!grounded) {
                velocity = velocity.multiply(0.97, 0.97, 0.97).add(0.0, -0.055, 0.0);
            }

            long totalLife = lifeMs + groundExtraMs;
            return timer.passedMs(totalLife);
        }

        private Vec3 interpolate(float tickDelta) {
            return prevPos.lerp(pos, tickDelta);
        }

        private float getLifeProgress() {
            long totalLife = lifeMs + groundExtraMs;
            return Math.min(1f, timer.getPassedTimeMs() / (float) totalLife);
        }
    }

    private static class FlashRing {
        private final Vec3 pos;
        private final Timer timer;
        private final long lifeMs;

        private FlashRing(Vec3 pos, long lifeMs) {
            this.pos = pos;
            this.timer = new Timer();
            this.lifeMs = lifeMs;
        }
    }

    private class LightningStrike {
        private final List<Vec3> points = new ArrayList<>(LIGHTNING_POINTS);
        private final int colorArgb;
        private final long createdAtMs;

        private LightningStrike(Vec3 startPos, int colorArgb, long createdAtMs) {
            this.colorArgb = colorArgb;
            this.createdAtMs = createdAtMs;

            Vec3 current = startPos;
            for (int i = 0; i < LIGHTNING_POINTS; i++) {
                current = current.add(
                        randomRange(-LIGHTNING_STEP_SPREAD, LIGHTNING_STEP_SPREAD),
                        LIGHTNING_STEP_Y,
                        randomRange(-LIGHTNING_STEP_SPREAD, LIGHTNING_STEP_SPREAD)
                );
                points.add(current);
            }
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - createdAtMs > LIGHTNING_LIFE_MS;
        }

        private float getAlpha() {
            long age = System.currentTimeMillis() - createdAtMs;
            float appear = smoothProgress(Math.min(1f, age / (float) LIGHTNING_APPEAR_MS));
            float fade = 1f;
            if (age > LIGHTNING_APPEAR_MS) {
                float fadeProgress = Math.min(1f, (age - LIGHTNING_APPEAR_MS) / (float) LIGHTNING_FADE_MS);
                fade = 1f - smoothProgress(fadeProgress);
            }
            return Math.max(0f, Math.min(1f, appear * fade));
        }
    }
}

