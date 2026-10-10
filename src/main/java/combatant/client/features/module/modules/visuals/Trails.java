/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.config.values.*;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.mixins.accessors.PersistentProjectileEntityAccessor;
import combatant.client.render.effects.particle.ParticleSimulationProfile;
import combatant.client.render.effects.trail.TrailRibbonCurve;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.math.RenderMath;
import combatant.client.render.engine.rig.core.RigDefinition;
import combatant.client.render.engine.rig.core.RigInstance;
import combatant.client.render.engine.rig.core.RigTransform;
import combatant.client.render.engine.rig.deform.RigDeformFlags;
import combatant.client.render.engine.rig.deform.RigRibbonDefinition;
import combatant.client.render.engine.rig.mesh.RigVertexEncoding;
import combatant.client.render.engine.rig.shader.RigBonesUniforms;
import combatant.client.render.engine.rig.shader.RigDeformUniforms;
import combatant.client.render.engine.rig.shader.RigRibbonUniforms;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.helpers.Particle3D;
import combatant.client.render.helpers.ParticleTextureMode;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;
import java.util.*;
import java.util.List;

@ModuleInfo(id = "trails", displayName = "Trails", category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.COSMETIC,
        description = "module.trails.description")
public class Trails extends Module {
    private static final String SETTING_ONLY_SELF = "only_self";
    private static final String SETTING_HIDE_FIRST_PERSON = "hide_first_person";
    private static final String SETTING_DEPTH_TEST = "depth_test";
    private static final String SETTING_TRAIL_MODE = "trail_mode";
    private static final String SETTING_LIFE_TIME_TICKS = "life_time_ticks";
    private static final String SETTING_SCALE = "scale";
    private static final String SETTING_TRAIL_DOWN = "trail_down";
    private static final String SETTING_TRAIL_HEIGHT = "trail_height";
    private static final String SETTING_FX_SPEED = "fx_speed";
    private static final String SETTING_COLOR = "color";
    private static final String SETTING_COLOR_MODE = "color_mode";
    private static final String SETTING_COLOR_SPEED = "color_speed";
    private static final String SETTING_COLOR_PHASE = "color_phase";
    private static final String SETTING_COLOR_LENGTH_SPREAD = "color_length_spread";
    private static final String SETTING_COLOR_AGE_SPREAD = "color_age_spread";
    private static final String SETTING_COLOR_2 = "color2";
    private static final String SETTING_PARTICLES_ENABLED = "particles_enabled";
    private static final String SETTING_PARTICLES_HIDE_FIRST_PERSON = "particles_hide_first_person";
    private static final String SETTING_PARTICLE_TRIGGERS = "particle_triggers";
    private static final String SETTING_PARTICLE_TEXTURE = "particle_texture";
    private static final String SETTING_PARTICLE_RANDOM_COLOR = "particle_random_color";
    private static final String SETTING_PARTICLE_SIZE = "particle_size";
    private static final String SETTING_PARTICLE_LIFE = "particle_life_ms";
    private static final String SETTING_PARTICLE_WALK_COUNT = "particle_walk_count";
    private static final String SETTING_PARTICLE_JUMP_COUNT = "particle_jump_count";
    private static final String SETTING_PARTICLE_PROJECTILE_COUNT = "particle_projectile_count";

    private static final ParticleSimulationProfile PARTICLE_SIMULATION =
            ParticleSimulationProfile.dragGravity(0.96f, 0.96f, 0.01f);
    private static final int PARTICLE_MAX = 600;
    private static final int PROJECTILE_SCAN_RADIUS = 48;
    private static final int PROJECTILE_MAX_SPAWN = 140;

    private static final float HISTORY_MIN_SPACING = 0.045f;
    private static final float HISTORY_RESAMPLE_SPACING = 0.13f;
    private static final float HISTORY_TELEPORT_DISTANCE = 3.25f;
    private static final int HISTORY_MAX_POINTS = 224;
    private static final float SPEED_RESPONSE = 0.34f;
    private static final int RIBBON_DEFORM_ID = 0;
    private static final int RIBBON_SAMPLES = 16;
    private static final int RIBBON_LONGITUDINAL_STEPS_MAX = 52;
    private static final int FIREFLY_MAX_STAMPS = 96;
    private static final float FIREFLY_MIN_SPACING = 0.12f;
    private static final int RIBBON_META = RigVertexEncoding.packDeformMeta(RIBBON_DEFORM_ID, RigDeformFlags.RIBBON);
    private static final RigDefinition TRAIL_RIG_DEFINITION = buildTrailRigDefinition();

    private final Minecraft mc = Minecraft.getInstance();
    private final Random random = new Random();
    private final RigInstance trailRig = createTrailRig();

    private final BooleanValue onlySelf = bool("trailsOnlySelf", SETTING_ONLY_SELF, false);
    private final BooleanValue hideFirstPerson = bool("trailsHideFirstPerson", SETTING_HIDE_FIRST_PERSON, true);
    private final BooleanValue depthTest = bool("trailsDepthTest", SETTING_DEPTH_TEST, true);
    private final ModeValue trailMode =
            modeSetting("trailsTrailMode", SETTING_TRAIL_MODE, "Tail", "Tail", "Trail");
    private final RGBAColorValue colorValue = color("trailsColor", SETTING_COLOR, "#8800FF00");
    private final ModeValue colorMode =
            modeSetting("trailsColorMode", SETTING_COLOR_MODE, "Static",
                    "Static", "Rainbow", "LightRainbow", "Sky", "Fade", "DoubleColor", "Analogous", "Theme");
    private final RGBAColorValue colorValue2 =
            visibleWhen(color("trailsColor2", SETTING_COLOR_2, "#FF55FFFF"), this::usesSecondaryColor);
    private final NumberValue<Integer> colorSpeed =
            num("trailsColorSpeed", SETTING_COLOR_SPEED, 18, 2, 54);
    private final NumberValue<Integer> lifeTimeTicks =
            num("trailsLifeTimeTicks", SETTING_LIFE_TIME_TICKS, 10, 1, 40);
    private final NumberValue<Float> trailScale =
            visibleWhen(num("trailsScale", SETTING_SCALE, 0.95f, 0.25f, 2.4f), this::isTailMode);
    private final NumberValue<Float> trailDown =
            visibleWhen(num("trailsTrailDown", SETTING_TRAIL_DOWN, 0.5f, 0.0f, 2.0f), this::isTrailMode);
    private final NumberValue<Float> trailHeight =
            visibleWhen(num("trailsTrailHeight", SETTING_TRAIL_HEIGHT, 1.45f, 0.2f, 3.2f), this::isTrailMode);
    private final NumberValue<Float> fxSpeed =
            num("trailsFxSpeed", SETTING_FX_SPEED, 1.0f, 0.2f, 3.0f);

    private final NumberValue<Integer> colorPhase =
            num("trailsColorPhase", SETTING_COLOR_PHASE, 0, 0, 360);
    private final NumberValue<Integer> colorLengthSpread =
            num("trailsColorLengthSpread", SETTING_COLOR_LENGTH_SPREAD, 180, -720, 720);
    private final NumberValue<Integer> colorAgeSpread =
            num("trailsColorAgeSpread", SETTING_COLOR_AGE_SPREAD, 90, -720, 720);

    private final BooleanValue particlesEnabled =
            bool("trailsParticlesEnabled", SETTING_PARTICLES_ENABLED, false);
    private final BooleanValue particlesHideFirstPerson =
            visibleWhen(bool("trailsParticlesHideFirstPerson", SETTING_PARTICLES_HIDE_FIRST_PERSON, false), particlesEnabled::get);
    private final BooleanMapValue particleTriggers = visibleWhen(group("trailsParticleTriggers", SETTING_PARTICLE_TRIGGERS, Map.of(
            "walk", true,
            "jump", true,
            "projectile", true
    )), particlesEnabled::get);
    private final NumberValue<Integer> particleWalkCount =
            visibleWhen(num("trailsParticleWalkCount", SETTING_PARTICLE_WALK_COUNT, 3, 1, 8),
                    () -> particlesEnabled.get() && particleTriggers.get("walk"));
    private final NumberValue<Integer> particleJumpCount =
            visibleWhen(num("trailsParticleJumpCount", SETTING_PARTICLE_JUMP_COUNT, 12, 1, 30),
                    () -> particlesEnabled.get() && particleTriggers.get("jump"));
    private final NumberValue<Integer> particleProjectileCount =
            visibleWhen(num("trailsParticleProjectileCount", SETTING_PARTICLE_PROJECTILE_COUNT, 3, 1, 10),
                    () -> particlesEnabled.get() && particleTriggers.get("projectile"));
    private final EnumValue<ParticleTextureMode> particleTexture =
            visibleWhen(enumSetting("trailsParticleTexture", SETTING_PARTICLE_TEXTURE, ParticleTextureMode.BLOOM, ParticleTextureMode.values()),
                    particlesEnabled::get);
    private final BooleanValue particleRandomColor =
            visibleWhen(bool("trailsParticleRandomColor", SETTING_PARTICLE_RANDOM_COLOR, false), particlesEnabled::get);
    private final NumberValue<Float> particleSize =
            visibleWhen(num("trailsParticleSize", SETTING_PARTICLE_SIZE, 0.12f, 0.04f, 0.4f), particlesEnabled::get);
    private final NumberValue<Integer> particleLifeMs =
            visibleWhen(num("trailsParticleLifeMs", SETTING_PARTICLE_LIFE, 1600, 200, 5000), particlesEnabled::get);

    private final Map<UUID, Boolean> groundState = new HashMap<>();
    private final Map<UUID, TrailHistory> histories = new HashMap<>();
    private final List<Particle3D> extraParticles = new ArrayList<>();
    private final List<Particle3D>[] particleBuckets = createBuckets();

    private static RigDefinition buildTrailRigDefinition() {
        RigDefinition.Builder builder = RigDefinition.builder();
        builder.bone("root", -1, RigTransform.identity());
        return builder.build();
    }

    private static RigInstance createTrailRig() {
        RigInstance instance = new RigInstance(TRAIL_RIG_DEFINITION);
        instance.ribbon().define(new RigRibbonDefinition(
                RIBBON_DEFORM_ID,
                RIBBON_SAMPLES,
                new Vector3f(1.0f, 0.0f, 0.0f),
                new Vector3f(0.0f, 1.0f, 0.0f),
                new Vector3f(0.0f, 0.0f, 1.0f)
        ));
        return instance;
    }

    @SuppressWarnings("unchecked")
    private static List<Particle3D>[] createBuckets() {
        List<Particle3D>[] buckets = new List[TextureStorage.RANDOM_PARTICLES.length];
        for (int i = 0; i < buckets.length; i++) buckets[i] = new ArrayList<>();
        return buckets;
    }

    private static void clearBuckets(List<Particle3D>[] buckets) {
        for (List<Particle3D> bucket : buckets) bucket.clear();
    }

    private static void addBillboardQuad(MeshBuilder mesh, double cx, double cy, double cz,
                                         Vector3f right, Vector3f up, int argb) {
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

    private static void addBillboardQuad(MeshBuilder mesh, double cx, double cy, double cz,
                                         float size, Quaternionf camRot, int argb) {
        Vector3f right = new Vector3f(1, 0, 0).rotate(camRot).mul(size);
        Vector3f up = new Vector3f(0, 1, 0).rotate(camRot).mul(size);
        addBillboardQuad(mesh, cx, cy, cz, right, up, argb);
    }

    private static int withAlpha(int argb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        int colorIndex = mc.player.tickCount % 360;
        boolean particlesOn = particlesEnabled.get();
        boolean triggerWalk = particlesOn && particleTriggers.get("walk");
        boolean triggerJump = particlesOn && particleTriggers.get("jump");
        boolean triggerProjectile = particlesOn && particleTriggers.get("projectile");

        if (!extraParticles.isEmpty()) extraParticles.removeIf(Particle3D::update);
        Set<UUID> active = new HashSet<>();

        for (Player player : mc.level.players()) {
            if (onlySelf.get() && player != mc.player) continue;
            UUID id = player.getUUID();
            active.add(id);

            TrailHistory history = histories.computeIfAbsent(id, ignored -> new TrailHistory());
            boolean moved = captureHistory(history, player.position(), player.tickCount, getColorArgb(colorIndex));

            boolean skipParticlesFirstPerson = particlesHideFirstPerson.get()
                    && player == mc.player
                    && mc.options.getCameraType().isFirstPerson();
            if (particlesOn && !skipParticlesFirstPerson) {
                if (triggerWalk && moved) spawnWalkParticles(player, colorIndex);

                boolean prevGround = groundState.getOrDefault(id, player.onGround());
                boolean nowGround = player.onGround();
                if (triggerJump && prevGround && !nowGround) spawnJumpParticles(player, colorIndex);
                groundState.put(id, nowGround);
            }
        }

        if (particlesOn && triggerProjectile) spawnProjectileParticles(colorIndex);
        groundState.keySet().removeIf(id -> !active.contains(id));
        histories.keySet().removeIf(id -> !active.contains(id));
    }

    private boolean captureHistory(TrailHistory history, Vec3 current, int tick, int color) {
        history.currentTick = tick;
        if (history.lastTickPosition == null) {
            history.lastTickPosition = current;
            history.samples.add(new TrailSample(current, color, tick, 0.0f));
            return false;
        }

        Vec3 previous = history.lastTickPosition;
        double distance = previous.distanceTo(current);
        if (distance > HISTORY_TELEPORT_DISTANCE) {
            history.reset(current, tick, color);
            return false;
        }

        double horizontalDistance = horizontalDistanceBetween(previous, current);
        float speed = (float) horizontalDistance;
        history.smoothedSpeed += (speed - history.smoothedSpeed) * SPEED_RESPONSE;

        boolean moved = horizontalDistance >= HISTORY_MIN_SPACING;
        if (moved) {
            appendHistorySample(history, current, color, tick, speed);
            history.lastMovedTick = tick;
        }
        history.lastTickPosition = current;
        float retention = lifeTimeTicks.get() + 1.25f;
        history.samples.removeIf(sample -> tick - sample.bornTime() > retention);
        return moved;
    }

    private void appendHistorySample(TrailHistory history, Vec3 sample, int color, int tick, float speed) {
        if (history.samples.isEmpty()) {
            history.samples.add(new TrailSample(sample, color, tick, speed));
            return;
        }

        Vec3 last = history.samples.get(history.samples.size() - 1).position();
        double distance = horizontalDistanceBetween(last, sample);
        if (distance < HISTORY_MIN_SPACING) return;

        int steps = Math.max(1, Math.min(16, (int) Math.ceil(distance / HISTORY_RESAMPLE_SPACING)));
        float startTime = tick - 1.0f;
        float endTime = tick;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            float bornTime = Mth.lerp((float) t, startTime, endTime);
            history.samples.add(new TrailSample(last.lerp(sample, t), color, bornTime, speed));
        }
        while (history.samples.size() > HISTORY_MAX_POINTS) history.samples.remove(0);
    }

    @Override
    public void onDisable() {
        histories.clear();
        extraParticles.clear();
        groundState.clear();
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        if (particlesEnabled.get() && !extraParticles.isEmpty()) renderExtraParticles(renderer, tickDelta);

        boolean useDepth = depthTest.get();
        if (isTailMode()) {
            renderClassicFireflyTail(renderer, tickDelta, useDepth);
            return;
        }

        RenderPipeline pipeline = useDepth
                ? CombatantRenderPipelines.RIG_TRAIL_CLASSIC_DEPTH
                : CombatantRenderPipelines.RIG_TRAIL_CLASSIC;
        Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.NONE;
        int phaseBase = currentColorPhase(tickDelta);
        float time = (mc.player.tickCount - 1 + tickDelta) * 0.05f * fxSpeed.get();

        for (Player player : mc.level.players()) {
            if (!shouldRenderPlayer(player)) continue;
            TrailHistory history = histories.get(player.getUUID());
            if (history == null) continue;
            List<Vec3> path = buildWorldPath(player, history, tickDelta);
            if (path.size() < 2) continue;
            float cullPadding = Math.max(0.25f, trailHeight.get() * 0.72f + Math.abs(trailDown.get()));
            if (!pathVisible(path, cullPadding)) continue;
            renderRiggedTrail(renderer, pipeline, depthMode, history, path, tickDelta, phaseBase, time);
        }
    }

    private void renderClassicFireflyTail(Renderer3D renderer, float tickDelta, boolean useDepth) {
        RenderPipeline pipeline = useDepth
                ? CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_DEPTH
                : CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE;
        Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.NONE;
        MeshBuilder mesh = renderer.batchTextured(pipeline, TextureStorage.FIRE_FLY, depthMode);
        if (mesh == null) return;

        Quaternionf camRot = RenderState.cameraRotation;
        int phaseBase = currentColorPhase(tickDelta);
        float baseScale = trailScale.get();

        for (Player player : mc.level.players()) {
            if (!shouldRenderPlayer(player)) continue;
            TrailHistory history = histories.get(player.getUUID());
            if (history == null) continue;
            List<Vec3> path = buildWorldPath(player, history, tickDelta);
            if (path.size() < 2) continue;
            if (!pathVisible(path, Math.max(0.25f, trailScale.get() * 1.35f))) continue;

            ArrayList<Vector3f> points = new ArrayList<>(path.size());
            for (Vec3 p : path) points.add(new Vector3f((float) p.x, (float) p.y, (float) p.z));

            TrailRibbonCurve curve;
            try {
                curve = new TrailRibbonCurve(points);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            Vec3 camera = RenderState.cameraPos;
            double cameraDistance = camera != null ? camera.distanceTo(player.position()) : 0.0;
            float distanceSpacing = (float) Math.max(0.0, (cameraDistance - 18.0) * 0.0045);
            float spacing = Math.max(FIREFLY_MIN_SPACING, Math.max(baseScale * 0.24f, distanceSpacing));
            int stamps = Math.max(2, Math.min(FIREFLY_MAX_STAMPS, (int) Math.ceil(curve.totalLength() / spacing) + 1));
            Vector3f sampled = new Vector3f();
            float speedResponse = Math.min(1.0f, history.smoothedSpeed / SPEED_RESPONSE);
            float lengthFade = smoothstep(0.04f, 0.55f, curve.totalLength());

            for (int i = 0; i < stamps; i++) {
                float u = stamps <= 1 ? 1.0f : i / (float) (stamps - 1);
                curve.sample(u, sampled);
                int color = samplePathColor(history, u, tickDelta, phaseBase);
                float envelope = smoothPathEnvelope(u);
                if (envelope <= 0.004f) continue;

                float breathing = 0.92f + 0.08f * (float) Math.sin(u * 21.0f - (mc.player.tickCount + tickDelta) * 0.19f);
                float headGain = 0.92f + 0.18f * smoothstep(0.62f, 1.0f, u);
                float size = baseScale * breathing * headGain * (0.82f + speedResponse * 0.12f);

                int core = AnimatedRenderColors.scaleAlpha(color, 0.72f * envelope * lengthFade);
                addBillboardQuad(mesh, sampled.x, sampled.y + 0.9f, sampled.z, size * 0.72f, camRot, core);
            }
        }
    }

    private boolean shouldRenderPlayer(Player player) {
        if (onlySelf.get() && player != mc.player) return false;
        return !(hideFirstPerson.get()
                && player == mc.player
                && mc.options.getCameraType().isFirstPerson());
    }

    private List<Vec3> buildWorldPath(Player player, TrailHistory history, float tickDelta) {
        ArrayList<Vec3> out = new ArrayList<>(history.samples.size() + 1);
        float renderTick = player.tickCount - 1.0f + tickDelta;
        float cutoff = renderTick - lifeTimeTicks.get();
        Vec3 last = null;
        TrailSample previousSample = null;
        for (TrailSample sample : history.samples) {
            Vec3 p = sample.position();
            if (!finite(p)) continue;
            if (sample.bornTime() > renderTick + 1.0e-4f) break;
            if (sample.bornTime() < cutoff) {
                previousSample = sample;
                continue;
            }
            if (out.isEmpty() && previousSample != null && previousSample.bornTime() < cutoff) {
                float span = sample.bornTime() - previousSample.bornTime();
                float blend = span > 1.0e-5f
                        ? Mth.clamp((cutoff - previousSample.bornTime()) / span, 0.0f, 1.0f)
                        : 1.0f;
                Vec3 clippedTail = previousSample.position().lerp(p, blend);
                if (finite(clippedTail)) {
                    out.add(clippedTail);
                    last = clippedTail;
                }
            }
            if (last != null && last.distanceToSqr(p) > HISTORY_TELEPORT_DISTANCE * HISTORY_TELEPORT_DISTANCE) {
                out.clear();
                last = null;
            }
            if (last == null || last.distanceToSqr(p) > 1.0e-8) {
                out.add(p);
                last = p;
            }
            previousSample = sample;
        }

        Vec3 head = RenderMath.getLerpedPos(player, tickDelta);
        if (finite(head)) {
            if (last != null && last.distanceToSqr(head) > HISTORY_TELEPORT_DISTANCE * HISTORY_TELEPORT_DISTANCE) {
                out.clear();
            }
            if (out.isEmpty() || out.get(out.size() - 1).distanceToSqr(head) > 1.0e-8) out.add(head);
        }
        return out;
    }

    private static boolean finite(Vec3 point) {
        return point != null
                && Double.isFinite(point.x)
                && Double.isFinite(point.y)
                && Double.isFinite(point.z);
    }

    private void renderRiggedTrail(Renderer3D renderer,
                                   RenderPipeline pipeline,
                                   Renderer3D.DepthMode depthMode,
                                   TrailHistory history,
                                   List<Vec3> worldPath,
                                   float tickDelta,
                                   int phaseBase,
                                   float time) {
        Vec3 camera = RenderState.cameraPos;
        if (camera == null) return;

        ArrayList<Vector3f> curvePoints = new ArrayList<>(worldPath.size());
        for (Vec3 p : worldPath) {
            curvePoints.add(new Vector3f(
                    (float) (p.x - camera.x),
                    (float) p.y,
                    (float) (p.z - camera.z)
            ));
        }

        TrailRibbonCurve curve;
        try {
            curve = new TrailRibbonCurve(curvePoints, isTrailMode());
        } catch (IllegalArgumentException ignored) {
            return;
        }

        int longitudinalSteps = ribbonSteps(curve.totalLength());
        trailRig.ribbon().update(RIBBON_DEFORM_ID, curve);
        GpuBufferSlice bones = RigBonesUniforms.upload(trailRig);
        GpuBufferSlice deform = RigDeformUniforms.upload(trailRig.deform());
        GpuBufferSlice ribbon = RigRibbonUniforms.upload(trailRig.ribbon());
        Renderer3D.BatchBindings bindings = Renderer3D.BatchBindings.none()
                .withUniform(RigBonesUniforms.BLOCK_NAME, bones)
                .withUniform(RigDeformUniforms.BLOCK_NAME, deform)
                .withUniform(RigRibbonUniforms.BLOCK_NAME, ribbon);
        MeshBuilder mesh = renderer.batch(pipeline, depthMode, bindings);
        if (mesh == null) return;

        float center = trailDown.get() + trailHeight.get() * 0.5f;
        float halfHeight = trailHeight.get() * 0.5f;
        emitClassicRigSheet(mesh, history, tickDelta, phaseBase, time, center, halfHeight,
                longitudinalSteps, smoothstep(0.04f, 0.55f, curve.totalLength()));
    }

    private void emitClassicRigSheet(MeshBuilder mesh,
                                     TrailHistory history,
                                     float tickDelta,
                                     int phaseBase,
                                     float time,
                                     float centerLateral,
                                     float halfHeight,
                                     int longitudinalSteps,
                                     float lengthFade) {
        for (int i = 0; i < longitudinalSteps; i++) {
            float u0 = i / (float) longitudinalSteps;
            float u1 = (i + 1) / (float) longitudinalSteps;
            int color0 = AnimatedRenderColors.scaleAlpha(samplePathColor(history, u0, tickDelta, phaseBase), lengthFade);
            int color1 = AnimatedRenderColors.scaleAlpha(samplePathColor(history, u1, tickDelta, phaseBase), lengthFade);

            mesh.ensureQuadCapacity();
            int v0 = rigVertex(mesh, u0, 0.0f, centerLateral - halfHeight, 0.0f, 0.0f, 1.0f, color0, time);
            int v1 = rigVertex(mesh, u0, 1.0f, centerLateral + halfHeight, 0.0f, 0.0f, 1.0f, color0, time);
            int v2 = rigVertex(mesh, u1, 1.0f, centerLateral + halfHeight, 0.0f, 0.0f, 1.0f, color1, time);
            int v3 = rigVertex(mesh, u1, 0.0f, centerLateral - halfHeight, 0.0f, 0.0f, 1.0f, color1, time);
            mesh.quad(v0, v1, v2, v3);
        }
    }

    private static double horizontalDistanceBetween(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - a) / Math.max(1.0e-6f, b - a)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static int rigVertex(MeshBuilder mesh,
                                 float u,
                                 float v,
                                 float lateral,
                                 float depth,
                                 float normalY,
                                 float normalZ,
                                 int argb,
                                 float time) {
        return mesh.vec3(0.0f, 0.0f, 0.0f)
                .raw2(u, v)
                .vec3(0.0f, normalY, normalZ)
                .colorArgb(argb)
                .u8x4(0, 0, 0, 0)
                .unorm8x4(1.0f, 0.0f, 0.0f, 0.0f)
                .vec4(u, lateral, depth, time)
                .uint(RIBBON_META)
                .next();
    }

    private static int ribbonSteps(float length) {
        if (!Float.isFinite(length) || length <= 0.0f) return 12;
        return Math.max(12, Math.min(RIBBON_LONGITUDINAL_STEPS_MAX, (int) Math.ceil(length / 0.17f)));
    }

    private static boolean pathVisible(List<Vec3> path, float padding) {
        if (path == null || path.isEmpty()) return false;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 p : path) {
            if (p == null) continue;
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        if (!Double.isFinite(minX)) return false;
        double pad = Math.max(0.05, padding);
        AABB bounds = new AABB(minX - pad, minY - pad, minZ - pad, maxX + pad, maxY + pad, maxZ + pad);
        return Renderer3D.Culling.isInFrustum(bounds);
    }

    private int samplePathColor(TrailHistory history, float u, float tickDelta, int phaseBase) {
        float t = Math.max(0.0f, Math.min(1.0f, u));
        int phase = phaseBase
                + Math.round(t * colorLengthSpread.get())
                + Math.round((1.0f - t) * colorAgeSpread.get());
        int animated = getColorArgb(phase);

        if (!history.samples.isEmpty()) {
            int index = Math.min(history.samples.size() - 1, Math.max(0, Math.round(t * (history.samples.size() - 1))));
            TrailSample sample = history.samples.get(index);
            if (animatedColorMode() == AnimatedRenderColors.Mode.STATIC) {
                animated = sample.color();
            }
        }

        float lift = (1.0f - t) * (1.0f - t) * 0.24f;
        int mixed = AnimatedRenderColors.mixArgb(animated, (animated & 0xFF000000) | 0x00FFFFFF, lift);
        float life = lifeTimeTicks.get();
        float renderTick = history.currentTick - 1.0f + tickDelta;
        float remaining = life - Math.max(0.0f, renderTick - history.lastMovedTick);
        float terminalFade = smoothstep(0.0f, Math.min(5.0f, life * 0.45f), remaining);
        return AnimatedRenderColors.scaleAlpha(mixed, smoothPathEnvelope(t) * terminalFade);
    }

    private static float smoothPathEnvelope(float u) {
        float t = Math.max(0.0f, Math.min(1.0f, u));
        float tail = smoothstep(0.0f, 0.20f, t);
        float head = 0.88f + 0.12f * smoothstep(0.62f, 1.0f, t);
        return tail * head;
    }

    private void spawnWalkParticles(Player player, int colorIndex) {
        int count = particleWalkCount.get();
        if (count <= 0) return;
        for (int i = 0; i < count; i++) {
            spawnParticleAround(player, colorIndex, 0.5, 0.4, 0.1);
        }
    }

    private void spawnJumpParticles(Player player, int colorIndex) {
        int count = particleJumpCount.get();
        if (count <= 0) return;
        for (int i = 0; i < count; i++) {
            spawnParticleAround(player, colorIndex, 0.4, 0.1, 0.2);
        }
    }

    private void spawnProjectileParticles(int colorIndex) {
        if (mc.player == null) return;
        int count = particleProjectileCount.get();
        if (count <= 0) return;

        int spawned = 0;
        var box = mc.player.getBoundingBox().inflate(PROJECTILE_SCAN_RADIUS);
        List<Projectile> projectiles = mc.level.getEntitiesOfClass(
                Projectile.class,
                box,
                e -> true
        );

        for (Projectile p : projectiles) {
            if (p instanceof AbstractArrow persistent && isInGround(persistent)) {
                continue;
            }
            if (spawned >= PROJECTILE_MAX_SPAWN) break;
            for (int i = 0; i < count && spawned < PROJECTILE_MAX_SPAWN; i++) {
                spawnParticleAt(
                        p.getX() + random.nextDouble() * 0.6 - 0.3,
                        p.getY() + random.nextDouble() * Math.max(0.2, p.getBbHeight()),
                        p.getZ() + random.nextDouble() * 0.6 - 0.3,
                        colorIndex
                );
                spawned++;
            }
        }
    }

    private boolean isInGround(AbstractArrow projectile) {
        if (projectile == null) return false;
        try {
            return ((PersistentProjectileEntityAccessor) projectile).combatant$isInGround();
        } catch (Throwable t) {
            return false;
        }
    }

    private void spawnParticleAround(Player player, int colorIndex, double radius, double height, double motionScale) {
        double px = player.getX() + random.nextDouble() * radius - radius * 0.5;
        double py = player.getY() + random.nextDouble() * Math.max(0.1, height);
        double pz = player.getZ() + random.nextDouble() * radius - radius * 0.5;
        spawnParticleAt(px, py, pz, colorIndex, motionScale);
    }

    private void spawnParticleAt(double x, double y, double z, int colorIndex) {
        spawnParticleAt(x, y, z, colorIndex, 0.12);
    }

    private void spawnParticleAt(double x, double y, double z, int colorIndex, double motionScale) {
        if (extraParticles.size() >= PARTICLE_MAX) {
            extraParticles.remove(0);
        }

        double motion = 0.06 * motionScale;
        Vec3 vel = new Vec3(
                random.nextDouble() * motion * 2 - motion,
                random.nextDouble() * 0.03 - 0.015,
                random.nextDouble() * motion * 2 - motion
        );

        int color = particleRandomColor.get() ? randomColor() : getColorArgb(colorIndex);
        ParticleTexture tex = resolveParticleTexture();
        long life = particleLifeMs.get();
        long fadeIn = Math.min(200L, life / 4L);
        long fadeOut = Math.min(700L, life / 2L);

        extraParticles.add(new Particle3D(
                new Vec3(x, y, z),
                vel,
                life,
                fadeIn,
                fadeOut,
                particleSize.get(),
                color,
                tex.texture(),
                tex.index(),
                PARTICLE_SIMULATION.linearDrag(),
                PARTICLE_SIMULATION.gravity(),
                (random.nextFloat() - 0.5f) * 8f
        ));
    }

    private void renderExtraParticles(Renderer3D renderer, float tickDelta) {
        boolean useDepth = depthTest.get();
        RenderPipeline pipeline = useDepth
                ? CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE
                : CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE;
        Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.NONE;
        Quaternionf camRot = RenderState.cameraRotation;

        if (particleTexture.get() == ParticleTextureMode.BLOOM) {
            MeshBuilder mesh = renderer.batchTextured(pipeline, TextureStorage.FIRE_FLY, depthMode);
            if (mesh == null) return;
            for (Particle3D p : extraParticles) {
                renderParticle(mesh, p, tickDelta, camRot);
            }
            return;
        }

        clearBuckets(particleBuckets);
        for (Particle3D p : extraParticles) {
            int idx = p.textureIndex();
            if (idx >= 0 && idx < particleBuckets.length) {
                particleBuckets[idx].add(p);
            }
        }

        for (int i = 0; i < particleBuckets.length; i++) {
            List<Particle3D> bucket = particleBuckets[i];
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
        int argb = withAlpha(particle.color(), (int) (((particle.color() >>> 24) & 0xFF) * alpha));
        addBillboardQuad(mesh, pos.x, pos.y, pos.z, particle.size(), camRot, argb);
    }

    private int randomColor() {
        float h = random.nextFloat();
        float s = 0.7f + random.nextFloat() * 0.3f;
        float v = 0.8f + random.nextFloat() * 0.2f;
        return Color.HSBtoRGB(h, s, v) | 0xFF000000;
    }

    private ParticleTexture resolveParticleTexture() {
        if (particleTexture.get() == ParticleTextureMode.BLOOM) {
            return new ParticleTexture(TextureStorage.FIRE_FLY, -1);
        }
        int idx = random.nextInt(TextureStorage.RANDOM_PARTICLES.length);
        return new ParticleTexture(TextureStorage.RANDOM_PARTICLES[idx], idx);
    }

    private boolean usesSecondaryColor() {
        return AnimatedRenderColors.usesSecondary(animatedColorMode());
    }

    private boolean isTailMode() {
        return !"Trail".equals(trailMode.get());
    }

    private boolean isTrailMode() {
        return "Trail".equals(trailMode.get());
    }

    private boolean isTrailDownVisible() {
        return isTrailMode();
    }

    private int getColorArgb(int count) {
        return AnimatedRenderColors.resolve(
                animatedColorMode(),
                colorSpeed.get(),
                count,
                colorValue.getArgb(),
                colorValue2.getArgb(),
                true
        );
    }

    private int currentColorPhase(float tickDelta) {
        if (mc.player == null) return colorPhase.get();
        return Math.round((mc.player.tickCount + tickDelta) * 4.0f) + colorPhase.get();
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

    private static final class TrailHistory {
        private final List<TrailSample> samples = new ArrayList<>();
        private Vec3 lastTickPosition;
        private float smoothedSpeed;
        private float lastMovedTick;
        private float currentTick;

        private void reset(Vec3 position, int tick, int color) {
            samples.clear();
            samples.add(new TrailSample(position, color, tick, 0.0f));
            lastTickPosition = position;
            smoothedSpeed = 0.0f;
            lastMovedTick = tick;
            currentTick = tick;
        }
    }

    private record TrailSample(Vec3 position, int color, float bornTime, float speed) {
    }

    private record ParticleTexture(net.minecraft.resources.Identifier texture, int index) {
    }

    // textured rendering handled by Renderer3D batching
}
