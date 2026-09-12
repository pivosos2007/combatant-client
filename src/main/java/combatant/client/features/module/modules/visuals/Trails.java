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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import combatant.client.config.values.*;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.mixininterface.IEntity;
import combatant.client.mixins.accessors.PersistentProjectileEntityAccessor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.effects.particle.ParticleSimulationProfile;
import combatant.client.render.helpers.Particle3D;
import combatant.client.render.helpers.ParticleTextureMode;
import combatant.client.render.helpers.TrailPoint;

import java.awt.*;
import java.util.*;
import java.util.List;

//todo Description
@ModuleInfo(id = "trails", displayName = "Trails", category = ModuleCategory.VISUALS)
public class Trails extends Module {
    private static final String SETTING_ONLY_SELF = "only_self";
    private static final String SETTING_HIDE_FIRST_PERSON = "hide_first_person";
    private static final String SETTING_DEPTH_TEST = "depth_test";
    private static final String SETTING_TRAIL_MODE = "trail_mode";
    private static final String SETTING_LIFE_TIME_TICKS = "life_time_ticks";
    private static final String SETTING_SCALE = "scale";
    private static final String SETTING_TRAIL_DOWN = "trail_down";
    private static final String SETTING_TRAIL_HEIGHT = "trail_height";
    private static final String SETTING_TRAIL_STROKE = "trail_stroke";
    private static final String SETTING_TRAIL_STROKE_WIDTH = "trail_stroke_width";
    private static final String SETTING_TRAIL_STROKE_ALPHA = "trail_stroke_alpha";
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
    private static final String SETTING_CONNECTED_PATH = "connected_path";
    private static final String SETTING_CONNECT_STEP = "connected_step";
    private static final ParticleSimulationProfile PARTICLE_SIMULATION =
            ParticleSimulationProfile.dragGravity(0.96f, 0.96f, 0.01f);
    private static final int PARTICLE_MAX = 600;
    private static final int PROJECTILE_SCAN_RADIUS = 48;
    private static final int PROJECTILE_MAX_SPAWN = 140;
    private static final int CONNECT_MAX_STEPS = 6;
    private final Minecraft mc = Minecraft.getInstance();
    private final Random random = new Random();
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
            visibleWhen(num("trailsScale", SETTING_SCALE, 0.6f, 0.2f, 1.2f), this::isTailMode);
    private final NumberValue<Float> trailDown =
            visibleWhen(num("trailsTrailDown", SETTING_TRAIL_DOWN, 0.5f, 0.0f, 2.0f), this::isTrailDownVisible);
    private final NumberValue<Float> trailHeight =
            visibleWhen(num("trailsTrailHeight", SETTING_TRAIL_HEIGHT, 1.3f, 0.1f, 2.0f), this::isTrailDownVisible);
    private final BooleanValue trailStroke =
            visibleWhen(bool("trailsTrailStroke", SETTING_TRAIL_STROKE, true), this::isTrailMode);
    private final NumberValue<Float> trailStrokeWidth =
            visibleWhen(num("trailsTrailStrokeWidth", SETTING_TRAIL_STROKE_WIDTH, 2.0f, 0.5f, 8.0f),
                    () -> isTrailMode() && trailStroke.get());
    private final NumberValue<Float> trailStrokeAlpha =
            visibleWhen(num("trailsTrailStrokeAlpha", SETTING_TRAIL_STROKE_ALPHA, 0.75f, 0.05f, 1.0f),
                    () -> isTrailMode() && trailStroke.get());
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
    private final BooleanValue connectedPath =
            visibleWhen(bool("trailsConnectedPath", SETTING_CONNECTED_PATH, true), this::isTailMode);
    private final NumberValue<Float> connectStep =
            visibleWhen(num("trailsConnectedStep", SETTING_CONNECT_STEP, 0.35f, 0.1f, 1.0f),
                    () -> isTailMode() && connectedPath.get());
    private final Map<UUID, Boolean> groundState = new HashMap<>();
    private final List<Particle3D> extraParticles = new ArrayList<>();
    private final List<Particle3D>[] particleBuckets = createBuckets();

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

    private static void addVerticalQuad(MeshBuilder mesh, Vec3 p0, Vec3 p1,
                                        float down, float height, int argb0, int argb1) {
        double x1 = p0.x;
        double z1 = p0.z;
        double y1 = p0.y + down;
        double y2 = p0.y + down + height;

        double x2 = p1.x;
        double z2 = p1.z;
        double y3 = p1.y + down + height;
        double y4 = p1.y + down;

        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(x1, y1, z1).color(new RenderColor(argb0)).next();
        int i2 = mesh.vec3(x1, y2, z1).color(new RenderColor(argb0)).next();
        int i3 = mesh.vec3(x2, y3, z2).color(new RenderColor(argb1)).next();
        int i4 = mesh.vec3(x2, y4, z2).color(new RenderColor(argb1)).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void addLine(MeshBuilder mesh, double x0, double y0, double z0,
                                double x1, double y1, double z1, int argb0, int argb1) {
        mesh.ensureLineCapacity();
        int i1 = mesh.vec3(x0, y0, z0).color(new RenderColor(argb0)).next();
        int i2 = mesh.vec3(x1, y1, z1).color(new RenderColor(argb1)).next();
        mesh.line(i1, i2);
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

        if (!extraParticles.isEmpty()) {
            extraParticles.removeIf(Particle3D::update);
        }

        java.util.Set<UUID> active = new java.util.HashSet<>();

        for (Player player : mc.level.players()) {
            if (onlySelf.get() && player != mc.player) continue;
            active.add(player.getUUID());

            List<TrailPoint> trails = ((IEntity) player).combatant$getTrails();
            Vec3 last = ((IEntity) player).get$InstantRenderPos();

            boolean skipSelfInFirstPerson = hideFirstPerson.get()
                    && player == mc.player
                    && mc.options.getCameraType().isFirstPerson();
            boolean moved = player.getX() != last.x || player.getZ() != last.z;
            if (!skipSelfInFirstPerson && moved) {
                int argb = getColorArgb(colorIndex);
                trails.add(new TrailPoint(
                        last,
                        player.position(),
                        argb,
                        lifeTimeTicks.get()
                ));
            }

            trails.removeIf(TrailPoint::update);

            boolean skipParticlesFirstPerson = particlesHideFirstPerson.get()
                    && player == mc.player
                    && mc.options.getCameraType().isFirstPerson();
            if (particlesOn && !skipParticlesFirstPerson) {
                if (triggerWalk && moved) {
                    spawnWalkParticles(player, colorIndex);
                }

                boolean prevGround = groundState.getOrDefault(player.getUUID(), player.onGround());
                boolean nowGround = player.onGround();
                if (triggerJump && prevGround && !nowGround) {
                    spawnJumpParticles(player, colorIndex);
                }
                groundState.put(player.getUUID(), nowGround);
            }
        }

        if (particlesOn && triggerProjectile) {
            spawnProjectileParticles(colorIndex);
        }

        groundState.keySet().removeIf(id -> !active.contains(id));
    }

    @Override
    public void onDisable() {
        if (mc.level == null) return;
        for (Player player : mc.level.players()) {
            ((IEntity) player).combatant$getTrails().clear();
        }
        extraParticles.clear();
        groundState.clear();
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        boolean useDepth = depthTest.get();
        if (particlesEnabled.get() && !extraParticles.isEmpty()) {
            renderExtraParticles(renderer, tickDelta);
        }

        if (isTrailMode()) {
            renderRibbonTrails(renderer, tickDelta, useDepth);
            return;
        }

        RenderPipeline pipeline = useDepth
                ? CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE
                : CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE;
        Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.MAIN;
        MeshBuilder mesh = renderer.batchTextured(pipeline, TextureStorage.FIRE_FLY, depthMode);
        if (mesh == null) return;

        Quaternionf camRot = RenderState.cameraRotation;
        float scale = trailScale.get();
        Vector3f right = new Vector3f(1, 0, 0).rotate(camRot).mul(scale);
        Vector3f up = new Vector3f(0, 1, 0).rotate(camRot).mul(scale);
        boolean connect = connectedPath.get();
        float step = Math.max(0.05f, connectStep.get());
        int phaseBase = currentColorPhase(tickDelta);

        for (Player player : mc.level.players()) {
            if (onlySelf.get() && player != mc.player) continue;
            if (hideFirstPerson.get()
                    && player == mc.player
                    && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }

            List<TrailPoint> trails = ((IEntity) player).combatant$getTrails();
            int size = trails.size();
            if (size == 0) continue;

            for (int i = 0; i < size; i++) {
                TrailPoint ctx = trails.get(i);

                int col = trailPointColor(ctx, i, size, tickDelta, phaseBase);
                float anim = ctx.animation(tickDelta);
                int alpha = (int) (((col >>> 24) & 0xFF) * anim);
                int argb = withAlpha(col, alpha);

                if (connect) {
                    Vec3 from = ctx.from();
                    Vec3 to = ctx.to();
                    double dist = from.distanceTo(to);
                    int steps = Math.min(CONNECT_MAX_STEPS, (int) Math.ceil(dist / step));
                    if (steps <= 1) {
                        Vec3 pos = ctx.interpolate(tickDelta);
                        addBillboardQuad(mesh, pos.x, pos.y + 0.9, pos.z, right, up, argb);
                    } else {
                        for (int s = 0; s <= steps; s++) {
                            float t = s / (float) steps;
                            Vec3 pos = from.lerp(to, t);
                            addBillboardQuad(mesh, pos.x, pos.y + 0.9, pos.z, right, up, argb);
                        }
                    }
                } else {
                    Vec3 pos = ctx.interpolate(tickDelta);
                    addBillboardQuad(mesh, pos.x, pos.y + 0.9, pos.z, right, up, argb);
                }
            }
        }

    }

    private void renderRibbonTrails(Renderer3D renderer, float tickDelta, boolean useDepth) {
        RenderPipeline pipeline = useDepth
                ? CombatantRenderPipelines.WORLD_COLORED_LIQUID_IGNORE
                : CombatantRenderPipelines.WORLD_COLORED;
        Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.MAIN;
        MeshBuilder mesh = renderer.batch(pipeline, depthMode);
        if (mesh == null) return;

        MeshBuilder strokeMesh = null;
        if (trailStroke.get()) {
            float previousLineWidth = RenderState.lineWidth;
            try {
                RenderState.lineWidth = Math.max(0.5f, trailStrokeWidth.get());
                strokeMesh = renderer.batch(
                        useDepth
                                ? CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_IGNORE
                                : CombatantRenderPipelines.WORLD_COLORED_LINES,
                        depthMode
                );
            } finally {
                RenderState.lineWidth = previousLineWidth;
            }
        }

        float down = trailDown.get();
        float height = trailHeight.get();
        int phaseBase = currentColorPhase(tickDelta);

        for (Player player : mc.level.players()) {
            if (onlySelf.get() && player != mc.player) continue;
            if (hideFirstPerson.get()
                    && player == mc.player
                    && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }

            List<TrailPoint> trails = ((IEntity) player).combatant$getTrails();
            int size = trails.size();
            if (size < 2) continue;

            for (int i = 1; i < size; i++) {
                TrailPoint prev = trails.get(i - 1);
                TrailPoint cur = trails.get(i);

                Vec3 p0 = prev.interpolate(tickDelta);
                Vec3 p1 = cur.interpolate(tickDelta);

                int col0 = trailPointColor(prev, i - 1, size, tickDelta, phaseBase);
                int col1 = trailPointColor(cur, i, size, tickDelta, phaseBase);

                int alpha0 = (int) (((col0 >>> 24) & 0xFF) * prev.animation(tickDelta));
                int alpha1 = (int) (((col1 >>> 24) & 0xFF) * cur.animation(tickDelta));

                int argb0 = withAlpha(col0, alpha0);
                int argb1 = withAlpha(col1, alpha1);
                addVerticalQuad(mesh, p0, p1, down, height, argb0, argb1);
                if (strokeMesh != null) {
                    int stroke0 = AnimatedRenderColors.scaleAlpha(argb0, trailStrokeAlpha.get());
                    int stroke1 = AnimatedRenderColors.scaleAlpha(argb1, trailStrokeAlpha.get());
                    addLine(strokeMesh, p0.x, p0.y + down, p0.z, p1.x, p1.y + down, p1.z, stroke0, stroke1);
                    addLine(strokeMesh, p0.x, p0.y + down + height, p0.z, p1.x, p1.y + down + height, p1.z, stroke0, stroke1);
                }
            }
        }
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
        Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.MAIN;
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
        return "Tail".equals(trailMode.get());
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

    private int trailPointColor(TrailPoint point, int index, int size, float tickDelta, int phaseBase) {
        float position = size <= 1 ? 0.0f : index / (float) (size - 1);
        float age = 1.0f - point.animation(tickDelta);
        int phase = phaseBase
                + Math.round(position * colorLengthSpread.get())
                + Math.round(age * colorAgeSpread.get());

        int animated = getColorArgb(phase);
        if (animatedColorMode() == AnimatedRenderColors.Mode.STATIC) {
            animated = point.color();
        }

        float lift = (float) Math.pow(1.0f - position, 2.0f) * 0.45f;
        return AnimatedRenderColors.mixArgb(
                animated,
                (animated & 0xFF000000) | 0x00FFFFFF,
                lift
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

    private record ParticleTexture(net.minecraft.resources.Identifier texture, int index) {
    }

    // textured rendering handled by Renderer3D batching
}
