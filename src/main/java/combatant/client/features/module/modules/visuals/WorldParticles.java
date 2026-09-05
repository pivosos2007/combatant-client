/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.*;
import combatant.client.features.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.color.ColorBlendMode;
import combatant.client.render.engine.color.ColorEffects;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.TextureTintUniforms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

//todo Description
@ModuleInfo(
        id = "worldparticles",
        displayName = "WorldParticles",
        category = ModuleCategory.VISUALS
)
public class WorldParticles extends Module {
    private static final float SPRITE_SIZE_MULTIPLIER = 4.0f;
    private static final float SPRITE_ALPHA_MULTIPLIER = 0.40f;
    private static final float DIAGONAL_ALPHA_MULTIPLIER = 0.40f;
    private static final float OUTLINE_ALPHA_MULTIPLIER = 205.0f / 255.0f;
    private static final float QUAD_SWAY_MULTIPLIER = 0.22f;
    private static final float QUAD_BREATHE_MULTIPLIER = 0.10f;
    private static final float QUAD_ROLL_SPEED = 0.32f;
    private static final float BUBBLE_FILL_SIZE_MULTIPLIER = 3.15f;
    private static final float BUBBLE_GLOW_SIZE_MULTIPLIER = 5.10f;
    private static final float BUBBLE_CULL_EXTENT_MULTIPLIER = BUBBLE_GLOW_SIZE_MULTIPLIER * 1.56f;
    private static final float BUBBLE_HIGHLIGHT_SIZE_MULTIPLIER = 0.34f;
    private static final float BUBBLE_SECONDARY_HIGHLIGHT_SIZE_MULTIPLIER = 0.20f;
    private static final float BUBBLE_FILL_ALPHA_MULTIPLIER = 0.34f;
    private static final float BUBBLE_GLOW_ALPHA_MULTIPLIER = 0.13f;
    private static final float BUBBLE_DEPTH_ALPHA_MULTIPLIER = 0.22f;
    private static final float BUBBLE_HIGHLIGHT_ALPHA_MULTIPLIER = 0.74f;
    private static final int BUBBLE_SPHERE_STACKS = 5;
    private static final int BUBBLE_SPHERE_SLICES = 12;
    private static final int BUBBLE_SPHERE_ROW_SIZE = BUBBLE_SPHERE_SLICES + 1;
    private static final int BUBBLE_SPHERE_SAMPLE_COUNT = (BUBBLE_SPHERE_STACKS + 1) * BUBBLE_SPHERE_ROW_SIZE;
    private static final int BUBBLE_SPHERE_INTERIOR_STACKS = BUBBLE_SPHERE_STACKS - 1;
    private static final int BUBBLE_SPHERE_MESH_VERTEX_COUNT = 2 + BUBBLE_SPHERE_INTERIOR_STACKS * BUBBLE_SPHERE_SLICES;
    private static final int BUBBLE_SPHERE_MESH_INDEX_COUNT = 6 * BUBBLE_SPHERE_SLICES * BUBBLE_SPHERE_INTERIOR_STACKS;
    private static final float[] BUBBLE_SPHERE_X = new float[BUBBLE_SPHERE_SAMPLE_COUNT];
    private static final float[] BUBBLE_SPHERE_Y = new float[BUBBLE_SPHERE_SAMPLE_COUNT];
    private static final float[] BUBBLE_SPHERE_Z = new float[BUBBLE_SPHERE_SAMPLE_COUNT];
    private static final Vector3f BUBBLE_LIGHT = new Vector3f(-0.36f, 0.84f, -0.40f).normalize();
    private static final float FUNNEL_EYE_SIZE_MULTIPLIER = 4.35f;
    private static final float FUNNEL_DISTORTION_SIZE_MULTIPLIER = 5.25f;
    private static final float FUNNEL_DISTORTION_OUTER_SIZE_MULTIPLIER = 6.85f;
    private static final float FUNNEL_DISTORTION_INNER_SIZE_MULTIPLIER = 3.95f;
    private static final float FUNNEL_EYE_ALPHA_MULTIPLIER = 0.72f;
    private static final float FUNNEL_DISTORTION_ALPHA_MULTIPLIER = 0.16f;
    private static final float FUNNEL_DISTORTION_OUTER_ALPHA_MULTIPLIER = 0.085f;
    private static final float FUNNEL_DISTORTION_INNER_ALPHA_MULTIPLIER = 0.115f;
    private static final float FUNNEL_RIM_ALPHA_MULTIPLIER = 0.22f;
    private static final float FUNNEL_GHOST_ALPHA_MULTIPLIER = 0.18f;
    private static final float FUNNEL_FLASH_ALPHA_MULTIPLIER = 0.34f;
    private static final float FUNNEL_LEAK_ALPHA_MULTIPLIER = 0.32f;
    private static final float MOTION_SCALE = 0.04f;
    private static final float DRAG = 0.98f;
    private static final long ALPHA_ANIM_MS = 1000L;
    private static final int[][] CUBE_EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final int[][] BODY_DIAGONALS = {
            {0, 6}, {1, 7}, {2, 4}, {3, 5}
    };

    static {
        int vertex = 0;
        for (int stack = 0; stack <= BUBBLE_SPHERE_STACKS; stack++) {
            float theta = (float) Math.PI * stack / (float) BUBBLE_SPHERE_STACKS;
            float sinTheta = (float) Math.sin(theta);
            float y = (float) Math.cos(theta);
            for (int slice = 0; slice <= BUBBLE_SPHERE_SLICES; slice++) {
                float phi = (float) (Math.PI * 2.0) * slice / (float) BUBBLE_SPHERE_SLICES;
                BUBBLE_SPHERE_X[vertex] = (float) Math.cos(phi) * sinTheta;
                BUBBLE_SPHERE_Y[vertex] = y;
                BUBBLE_SPHERE_Z[vertex] = (float) Math.sin(phi) * sinTheta;
                vertex++;
            }
        }
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode =
            enumSetting("mode", "mode", Mode.QUADS);
    private final BooleanValue depthTest =
            boolCommon("depth_test", CommonSettingSchemas.RENDER_DEPTH_TEST, true);
    private final RGBAColorValue color =
            common(color("color", "#FF8ED4FF"), CommonSettingSchemas.RENDER_PRIMARY_COLOR.commonI18nKey());
    private final ModeValue colorMode =
            modeSetting("worldParticlesColorMode", "color_mode", "Theme",
                    "Static", "Rainbow", "LightRainbow", "Sky", "Fade", "DoubleColor", "Analogous", "Theme");
    private final NumberValue<Integer> colorSpeed =
            num("worldParticlesColorSpeed", "color_speed", 18, 2, 54);
    private final NumberValue<Integer> colorPhase =
            num("worldParticlesColorPhase", "color_phase", 0, 0, 360);
    private final NumberValue<Integer> colorSpread =
            num("worldParticlesColorSpread", "color_spread", 90, -720, 720);
    private final RGBAColorValue color2 =
            visibleWhen(color("worldParticlesColor2", "color2", "#FF55FFFF"), this::usesSecondaryColor);
    private final NumberValue<Integer> maxParticles =
            num("max_particles", 100, 1, 400);
    private final NumberValue<Integer> spawnPerTick =
            num("spawn_per_tick", 1, 1, 20);
    private final NumberValue<Double> radius =
            num("radius", 20.0, 1.0, 64.0);
    private final NumberValue<Double> height =
            num("height", 5.0, 0.5, 16.0);
    private final NumberValue<Integer> lifeMinMs =
            num("life_min_ms", 1500, 100, 10000);
    private final NumberValue<Integer> lifeMaxMs =
            num("life_max_ms", 4500, 100, 15000);
    private final NumberValue<Double> sizeMin =
            num("size_min", 0.10, 0.02, 1.0);
    private final NumberValue<Double> sizeMax =
            num("size_max", 0.30, 0.02, 1.5);
    private final NumberValue<Double> lineWidth =
            visibleWhen(num("line_width", 1.0, 0.5, 4.0), () -> !isFunnelMode() && !isBubbleMode());
    private final EnumValue<ColorBlendMode> funnelTintMode =
            visibleWhen(enumSetting("funnel_tint_mode", "funnel_tint_mode", ColorBlendMode.ORIGINAL), this::isFunnelMode);
    private final BooleanValue funnelTintSyncTheme =
            visibleWhen(bool("funnel_tint_sync_theme", "funnel_tint_sync_theme", true), this::isFunnelTintControlsVisible);
    private final RGBColorValue funnelTintColor =
            visibleWhen(colorNoAlpha("funnel_tint_color", "funnel_tint_color", "#78D7FF"), this::isManualFunnelTintVisible);
    private final NumberValue<Float> funnelTintStrength =
            visibleWhen(num("funnel_tint_strength", "funnel_tint_strength", 0.45f, 0.0f, 1.0f), this::isFunnelTintControlsVisible);
    private final List<Particle> particles = new ArrayList<>();
    private final Quaternionf bubbleSphereRotation = new Quaternionf();
    private final Vector3f bubbleSphereNormal = new Vector3f();

    private static double randomDoubleClosed(ThreadLocalRandom random, double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return 0.02;
        }
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        if (high <= low) {
            return low;
        }
        return random.nextDouble(low, high);
    }

    private static long randomLongClosed(ThreadLocalRandom random, long min, long max) {
        long low = Math.min(min, max);
        long high = Math.max(min, max);
        if (high <= low) {
            return low;
        }
        if (high == Long.MAX_VALUE) {
            return random.nextLong(low, Long.MAX_VALUE) + 1L;
        }
        return random.nextLong(low, high + 1L);
    }

    private static Vec3 offsetInBillboardPlane(Vec3 pos, Quaternionf camRot, float angle, float distance) {
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camRot).mul((float) Math.cos(angle) * distance);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camRot).mul((float) Math.sin(angle) * distance);
        return pos.add(right.x() + up.x(), right.y() + up.y(), right.z() + up.z());
    }

    private static void addBillboardQuad(MeshBuilder mesh, double cx, double cy, double cz,
                                         float size, Quaternionf camRot, int argb) {
        addBillboardQuad(mesh, cx, cy, cz, size, camRot, 0.0f, argb);
    }

    private static void addBillboardQuad(MeshBuilder mesh, double cx, double cy, double cz,
                                         float size, Quaternionf camRot, float rollRadians, int argb) {
        addBillboardQuadGradient(mesh, cx, cy, cz, size, camRot, rollRadians, argb, argb, argb, argb);
    }

    private static void addBillboardQuadGradient(MeshBuilder mesh, double cx, double cy, double cz,
                                                 float size, Quaternionf camRot, float rollRadians,
                                                 int bottomLeftArgb,
                                                 int bottomRightArgb,
                                                 int topRightArgb,
                                                 int topLeftArgb) {
        Vector3f baseRight = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camRot);
        Vector3f baseUp = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camRot);
        addBillboardQuadGradient(mesh, cx, cy, cz, size, baseRight, baseUp, rollRadians,
                bottomLeftArgb, bottomRightArgb, topRightArgb, topLeftArgb);
    }

    private static void addBillboardQuadGradient(MeshBuilder mesh, double cx, double cy, double cz,
                                                 float size, Vector3f baseRight, Vector3f baseUp,
                                                 float rollRadians,
                                                 int bottomLeftArgb,
                                                 int bottomRightArgb,
                                                 int topRightArgb,
                                                 int topLeftArgb) {
        float cos = (float) Math.cos(rollRadians);
        float sin = (float) Math.sin(rollRadians);

        float rightX = (baseRight.x() * cos + baseUp.x() * sin) * size;
        float rightY = (baseRight.y() * cos + baseUp.y() * sin) * size;
        float rightZ = (baseRight.z() * cos + baseUp.z() * sin) * size;
        float upX = (baseUp.x() * cos - baseRight.x() * sin) * size;
        float upY = (baseUp.y() * cos - baseRight.y() * sin) * size;
        float upZ = (baseUp.z() * cos - baseRight.z() * sin) * size;

        double p1x = cx - rightX - upX;
        double p1y = cy - rightY - upY;
        double p1z = cz - rightZ - upZ;
        double p2x = cx + rightX - upX;
        double p2y = cy + rightY - upY;
        double p2z = cz + rightZ - upZ;
        double p3x = cx + rightX + upX;
        double p3y = cy + rightY + upY;
        double p3z = cz + rightZ + upZ;
        double p4x = cx - rightX + upX;
        double p4y = cy - rightY + upY;
        double p4z = cz - rightZ + upZ;

        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(p1x, p1y, p1z).vec2(0.0, 1.0).colorArgb(bottomLeftArgb).next();
        int i2 = mesh.vec3(p2x, p2y, p2z).vec2(1.0, 1.0).colorArgb(bottomRightArgb).next();
        int i3 = mesh.vec3(p3x, p3y, p3z).vec2(1.0, 0.0).colorArgb(topRightArgb).next();
        int i4 = mesh.vec3(p4x, p4y, p4z).vec2(0.0, 0.0).colorArgb(topLeftArgb).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void addBillboardRect(MeshBuilder mesh, double cx, double cy, double cz,
                                         float halfWidth, float halfHeight, Quaternionf camRot,
                                         float rollRadians, int argb) {
        Vector3f baseRight = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camRot);
        Vector3f baseUp = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camRot);
        float cos = (float) Math.cos(rollRadians);
        float sin = (float) Math.sin(rollRadians);

        Vector3f right = new Vector3f(baseRight).mul(cos).add(new Vector3f(baseUp).mul(sin)).mul(halfWidth);
        Vector3f up = new Vector3f(baseUp).mul(cos).sub(new Vector3f(baseRight).mul(sin)).mul(halfHeight);

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
        int i1 = mesh.vec3(p1x, p1y, p1z).vec2(0.0, 1.0).colorArgb(argb).next();
        int i2 = mesh.vec3(p2x, p2y, p2z).vec2(1.0, 1.0).colorArgb(argb).next();
        int i3 = mesh.vec3(p3x, p3y, p3z).vec2(1.0, 0.0).colorArgb(argb).next();
        int i4 = mesh.vec3(p4x, p4y, p4z).vec2(0.0, 0.0).colorArgb(argb).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void addCube(MeshBuilder mesh, Vec3 pos, Vec3 rotation, float size, int diagonalArgb, int outlineArgb) {
        Quaternionf quaternion = new Quaternionf().rotationXYZ((float) rotation.x, (float) rotation.y, (float) rotation.z);
        Vector3f[] corners = new Vector3f[]{
                rotatedCorner(-0.5f, -0.5f, -0.5f, quaternion, size, pos),
                rotatedCorner(0.5f, -0.5f, -0.5f, quaternion, size, pos),
                rotatedCorner(0.5f, 0.5f, -0.5f, quaternion, size, pos),
                rotatedCorner(-0.5f, 0.5f, -0.5f, quaternion, size, pos),
                rotatedCorner(-0.5f, -0.5f, 0.5f, quaternion, size, pos),
                rotatedCorner(0.5f, -0.5f, 0.5f, quaternion, size, pos),
                rotatedCorner(0.5f, 0.5f, 0.5f, quaternion, size, pos),
                rotatedCorner(-0.5f, 0.5f, 0.5f, quaternion, size, pos)
        };

        for (int[] edge : CUBE_EDGES) {
            addLine(mesh, corners[edge[0]], corners[edge[1]], outlineArgb);
        }
        for (int[] diagonal : BODY_DIAGONALS) {
            addLine(mesh, corners[diagonal[0]], corners[diagonal[1]], diagonalArgb);
        }
    }

    private static Vector3f rotatedCorner(float x, float y, float z, Quaternionf quaternion, float size, Vec3 pos) {
        return new Vector3f(x, y, z)
                .rotate(quaternion)
                .mul(size)
                .add((float) pos.x, (float) pos.y, (float) pos.z);
    }

    private static void addLine(MeshBuilder mesh, Vector3f from, Vector3f to, int argb) {
        addLineGradient(mesh, from, to, argb, argb);
    }

    private static void addLineGradient(MeshBuilder mesh, Vector3f from, Vector3f to, int fromArgb, int toArgb) {
        mesh.ensureLineCapacity();
        int i1 = mesh.vec3(from.x(), from.y(), from.z()).colorArgb(fromArgb).next();
        int i2 = mesh.vec3(to.x(), to.y(), to.z()).colorArgb(toArgb).next();
        mesh.line(i1, i2);
    }

    private static void addSphereShell(MeshBuilder mesh,
                                       Vec3 center,
                                       Quaternionf rotation,
                                       Vector3f normal,
                                       float radius,
                                       int baseColor,
                                       int brightColor,
                                       int shadowColor,
                                       float alpha) {
        int baseVertex = mesh.getVertexCount();
        mesh.ensureCapacity(BUBBLE_SPHERE_MESH_VERTEX_COUNT, BUBBLE_SPHERE_MESH_INDEX_COUNT);

        addSphereVertex(mesh, center, rotation, normal, radius, baseColor, brightColor, shadowColor, alpha, 0);
        for (int stack = 1; stack < BUBBLE_SPHERE_STACKS; stack++) {
            int sampleRow = stack * BUBBLE_SPHERE_ROW_SIZE;
            for (int slice = 0; slice < BUBBLE_SPHERE_SLICES; slice++) {
                addSphereVertex(mesh, center, rotation, normal, radius,
                        baseColor, brightColor, shadowColor, alpha, sampleRow + slice);
            }
        }
        addSphereVertex(mesh, center, rotation, normal, radius, baseColor, brightColor, shadowColor, alpha,
                BUBBLE_SPHERE_STACKS * BUBBLE_SPHERE_ROW_SIZE);

        int firstRing = baseVertex + 1;
        int bottom = firstRing + BUBBLE_SPHERE_INTERIOR_STACKS * BUBBLE_SPHERE_SLICES;
        for (int slice = 0; slice < BUBBLE_SPHERE_SLICES; slice++) {
            int next = (slice + 1) % BUBBLE_SPHERE_SLICES;
            mesh.triangle(baseVertex, firstRing + slice, firstRing + next);
        }
        for (int stack = 0; stack < BUBBLE_SPHERE_INTERIOR_STACKS - 1; stack++) {
            int row = firstRing + stack * BUBBLE_SPHERE_SLICES;
            int nextRow = row + BUBBLE_SPHERE_SLICES;
            for (int slice = 0; slice < BUBBLE_SPHERE_SLICES; slice++) {
                int next = (slice + 1) % BUBBLE_SPHERE_SLICES;
                int a = row + slice;
                int b = nextRow + slice;
                int c = nextRow + next;
                int d = row + next;
                mesh.triangle(a, b, c);
                mesh.triangle(c, d, a);
            }
        }
        int lastRing = bottom - BUBBLE_SPHERE_SLICES;
        for (int slice = 0; slice < BUBBLE_SPHERE_SLICES; slice++) {
            int next = (slice + 1) % BUBBLE_SPHERE_SLICES;
            mesh.triangle(bottom, lastRing + next, lastRing + slice);
        }
    }

    private static void addSphereVertex(MeshBuilder mesh,
                                        Vec3 center,
                                        Quaternionf rotation,
                                        Vector3f normal,
                                        float radius,
                                        int baseColor,
                                        int brightColor,
                                        int shadowColor,
                                        float alpha,
                                        int sample) {
        normal.set(BUBBLE_SPHERE_X[sample], BUBBLE_SPHERE_Y[sample], BUBBLE_SPHERE_Z[sample]).rotate(rotation);
        float px = (float) center.x + normal.x() * radius;
        float py = (float) center.y + normal.y() * radius;
        float pz = (float) center.z + normal.z() * radius;
        int vertexColor = sphereSurfaceColor(baseColor, brightColor, shadowColor, normal, alpha);
        mesh.vec3(px, py, pz).colorArgb(vertexColor).next();
    }

    private static int sphereSurfaceColor(int baseColor, int brightColor, int shadowColor,
                                          Vector3f normal, float alpha) {
        float lit = Mth.clamp(normal.dot(BUBBLE_LIGHT) * 0.5f + 0.5f, 0.0f, 1.0f);
        float rim = 1.0f - Math.abs(normal.z());
        float vertical = Mth.clamp(normal.y() * 0.5f + 0.5f, 0.0f, 1.0f);
        int midColor = AnimatedRenderColors.mixArgb(shadowColor, baseColor, 0.36f + vertical * 0.24f);
        int mixed = AnimatedRenderColors.mixArgb(midColor, brightColor, Mth.clamp(lit * 0.74f + rim * 0.24f, 0.0f, 1.0f));
        return multiplyAlpha(mixed, alpha * (0.22f + lit * 0.22f + rim * 0.16f));
    }

    private static int multiplyAlpha(int argb, float alphaMultiplier) {
        int alpha = (argb >>> 24) & 0xFF;
        int outAlpha = Mth.clamp(Math.round(alpha * alphaMultiplier), 0, 255);
        return (outAlpha << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onDisable() {
        particles.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        particles.removeIf(particle -> particle.isExpired(nowMs));
        for (Particle particle : particles) {
            particle.tick();
        }

        int targetCount = Math.max(1, maxParticles.get());
        int available = targetCount - particles.size();
        if (available <= 0) {
            return;
        }

        int spawnCount = Mth.clamp(spawnPerTick.get(), 0, available);
        for (int i = 0; i < spawnCount; i++) {
            Particle particle = createParticle();
            if (particle != null) {
                particles.add(particle);
            }
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || particles.isEmpty() || mc.player == null || mc.level == null) {
            return;
        }

        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.worldEffect("world_particles:" + mode.get())) {
            boolean useDepth = depthTest.get();
            Renderer3D.DepthMode depthMode = useDepth ? Renderer3D.DepthMode.PRE_DEPTH : Renderer3D.DepthMode.MAIN;
            if (isFunnelMode()) {
                renderFunnel(renderer, useDepth, depthMode, tickDelta);
                return;
            }

            boolean bubbleMode = isBubbleMode();
            MeshBuilder spriteMesh = renderer.batchTextured(
                    useDepth ? CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_LIQUID_IGNORE
                            : CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE,
                    TextureStorage.BLOOM,
                    depthMode
            );

            MeshBuilder lineMesh = null;
            if (!bubbleMode) {
                float prevLineWidth = RenderState.lineWidth;
                try {
                    RenderState.lineWidth = Math.max(0.5f, lineWidth.get().floatValue());
                    lineMesh = renderer.batch(
                            useDepth ? CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_IGNORE
                                    : CombatantRenderPipelines.WORLD_COLORED_LINES,
                            depthMode
                    );
                } finally {
                    RenderState.lineWidth = prevLineWidth;
                }
            }

            Quaternionf camRot = RenderState.cameraRotation;
            Vector3f bubbleRight = bubbleMode ? new Vector3f(1.0f, 0.0f, 0.0f).rotate(camRot) : null;
            Vector3f bubbleUp = bubbleMode ? new Vector3f(0.0f, 1.0f, 0.0f).rotate(camRot) : null;
            MeshBuilder bubbleShellMesh = bubbleMode
                    ? renderer.batch(
                            useDepth ? CombatantRenderPipelines.WORLD_COLORED_LIQUID_IGNORE
                                    : CombatantRenderPipelines.WORLD_COLORED,
                            depthMode
                    )
                    : null;

            int particleCount = particles.size();
            if (bubbleShellMesh != null) {
                bubbleShellMesh.ensureCapacity(
                        particleCount * BUBBLE_SPHERE_MESH_VERTEX_COUNT,
                        particleCount * BUBBLE_SPHERE_MESH_INDEX_COUNT
                );
            }
            if (spriteMesh != null) {
                spriteMesh.ensureCapacity(
                        bubbleMode ? particleCount * 20 : particleCount * 4,
                        bubbleMode ? particleCount * 30 : particleCount * 6
                );
            }
            if (lineMesh != null) {
                lineMesh.ensureCapacity(
                        particleCount * 32,
                        particleCount * 32
                );
            }

            long nowMs = System.currentTimeMillis();
            try (ProfilerPhase.Scope geometryScope = ProfilerPhase.scope(
                    bubbleMode ? "world_particles:bubble_geometry" : "world_particles:quad_geometry")) {
                for (int i = 0; i < particles.size(); i++) {
                    Particle particle = particles.get(i);
                    float alpha = particle.alpha(nowMs);
                    if (alpha <= 0.003f) {
                        continue;
                    }

                    Vec3 pos = particle.interpolatePos(tickDelta);
                    if (bubbleMode && !bubbleInFrustum(particle, pos)) {
                        continue;
                    }
                    int baseColor = colorForParticle(particle, i);

                    if (bubbleMode) {
                        renderAirBubble(bubbleShellMesh, spriteMesh, particle, pos,
                                bubbleRight, bubbleUp, alpha, baseColor, nowMs);
                        continue;
                    }

                    long ageMs = particle.ageMs(nowMs);
                    float ageSeconds = ageMs * 0.001f;
                    float pulse = AnimationUtility.smoothstep(
                            0.5f + 0.5f * (float) Math.sin(ageSeconds * 1.65f + particle.visualPhase));
                    float appear = AnimationUtility.easeOutCubic(ageMs / 420.0f);
                    float visualScale = (0.72f + 0.28f * appear)
                            * (1.0f - QUAD_BREATHE_MULTIPLIER * 0.5f + QUAD_BREATHE_MULTIPLIER * pulse);
                    float sway = particle.size * QUAD_SWAY_MULTIPLIER;
                    float orbit = ageSeconds * 0.54f + particle.visualPhase;
                    pos = pos.add(
                            Math.cos(orbit) * sway,
                            Math.sin(ageSeconds * 0.78f + particle.visualPhase) * sway * 0.32f,
                            Math.sin(orbit) * sway
                    );

                    if (spriteMesh != null) {
                        int spriteArgb = multiplyAlpha(baseColor, alpha * SPRITE_ALPHA_MULTIPLIER);
                        float roll = particle.visualPhase + ageSeconds * QUAD_ROLL_SPEED;
                        addBillboardQuad(spriteMesh, pos.x, pos.y, pos.z,
                                particle.size * SPRITE_SIZE_MULTIPLIER * visualScale, camRot, roll, spriteArgb);
                    }

                    if (lineMesh != null) {
                        Vec3 rotation = particle.interpolateRotation(tickDelta);
                        int diagonalArgb = multiplyAlpha(baseColor, alpha * DIAGONAL_ALPHA_MULTIPLIER);
                        int outlineArgb = multiplyAlpha(baseColor, alpha * OUTLINE_ALPHA_MULTIPLIER);
                        addCube(lineMesh, pos, rotation, particle.size * visualScale, diagonalArgb, outlineArgb);
                    }
                }
            }
        }
    }

    private void renderFunnel(Renderer3D renderer, boolean useDepth, Renderer3D.DepthMode depthMode, float tickDelta) {
        ColorBlendMode tintMode = funnelTintMode.get();
        TextureTintUniforms.update(
                funnelTintRgb(),
                tintMode == ColorBlendMode.ORIGINAL ? 0.0f : Mth.clamp(funnelTintStrength.get(), 0.0f, 1.0f),
                tintMode.ordinal()
        );

        MeshBuilder distortionMesh = tintedTexturedBatch(renderer, useDepth, depthMode, TextureStorage.FUNNEL_DISTORTION);
        MeshBuilder eyeMesh = tintedTexturedBatch(renderer, useDepth, depthMode, TextureStorage.FUNNEL_EYE);
        MeshBuilder bloomMesh = tintedTexturedBatch(renderer, useDepth, depthMode, TextureStorage.BLOOM);
        if (distortionMesh == null && eyeMesh == null && bloomMesh == null) {
            return;
        }

        int particleCount = particles.size();
        if (distortionMesh != null) {
            distortionMesh.ensureCapacity(particleCount * 12, particleCount * 18);
        }
        if (eyeMesh != null) {
            eyeMesh.ensureCapacity(particleCount * 16, particleCount * 24);
        }
        if (bloomMesh != null) {
            bloomMesh.ensureCapacity(particleCount * 20, particleCount * 30);
        }

        Quaternionf camRot = RenderState.cameraRotation;
        for (int i = 0; i < particles.size(); i++) {
            Particle particle = particles.get(i);
            float alpha = particle.funnelAlpha();
            if (alpha <= 0.003f) {
                continue;
            }

            Vec3 pos = particle.interpolatePos(tickDelta);
            int baseColor = colorForParticle(particle, i);
            float ageSeconds = particle.ageMs() / 1000.0f;
            float scale = particle.funnelScale();
            float pulse = 0.5f + 0.5f * (float) Math.sin(particle.ageMs() * 0.0048f + particle.visualPhase);
            float rimPulse = AnimationUtility.smoothstep(pulse);

            if (distortionMesh != null) {
                float outerRoll = particle.visualPhase * 0.63f - ageSeconds * particle.distortionSpin * 0.54f;
                int outerArgb = funnelColor(baseColor, 0xFFFFFF, alpha * FUNNEL_DISTORTION_OUTER_ALPHA_MULTIPLIER * (0.82f + rimPulse * 0.18f));
                addBillboardQuad(
                        distortionMesh,
                        pos.x, pos.y, pos.z,
                        particle.size * FUNNEL_DISTORTION_OUTER_SIZE_MULTIPLIER * scale * (1.0f + rimPulse * 0.055f),
                        camRot,
                        outerRoll,
                        outerArgb
                );

                float distortionRoll = particle.visualPhase + ageSeconds * particle.distortionSpin;
                int distortionArgb = funnelColor(baseColor, 0xFFFFFF, alpha * FUNNEL_DISTORTION_ALPHA_MULTIPLIER);
                addBillboardQuad(
                        distortionMesh,
                        pos.x, pos.y, pos.z,
                        particle.size * FUNNEL_DISTORTION_SIZE_MULTIPLIER * scale,
                        camRot,
                        distortionRoll,
                        distortionArgb
                );

                float innerRoll = -particle.visualPhase * 1.18f + ageSeconds * (0.18f - particle.distortionSpin * 0.35f);
                int innerArgb = funnelColor(baseColor, 0xFFFFFF, alpha * FUNNEL_DISTORTION_INNER_ALPHA_MULTIPLIER * (1.0f - rimPulse * 0.25f));
                addBillboardQuad(
                        distortionMesh,
                        pos.x, pos.y, pos.z,
                        particle.size * FUNNEL_DISTORTION_INNER_SIZE_MULTIPLIER * scale,
                        camRot,
                        innerRoll,
                        innerArgb
                );
            }

            if (eyeMesh != null) {
                float eyeRoll = -particle.visualPhase * 0.42f + ageSeconds * particle.eyeSpin;
                int rimArgb = funnelColor(baseColor, 0xFFC76A, alpha * FUNNEL_RIM_ALPHA_MULTIPLIER * (0.62f + rimPulse * 0.38f));
                addBillboardQuad(
                        eyeMesh,
                        pos.x, pos.y, pos.z,
                        particle.size * (FUNNEL_EYE_SIZE_MULTIPLIER + 0.48f) * scale * (1.0f + rimPulse * 0.045f),
                        camRot,
                        eyeRoll - 0.18f,
                        rimArgb
                );

                int ghostA = funnelColor(baseColor, 0x92E8FF, alpha * FUNNEL_GHOST_ALPHA_MULTIPLIER * (0.70f + rimPulse * 0.16f));
                addBillboardQuad(
                        eyeMesh,
                        pos.x, pos.y, pos.z,
                        particle.size * 3.74f * scale,
                        camRot,
                        eyeRoll + 0.74f,
                        ghostA
                );

                int ghostB = funnelColor(baseColor, 0xFF9D47, alpha * FUNNEL_GHOST_ALPHA_MULTIPLIER * 0.68f * (1.0f - rimPulse * 0.18f));
                addBillboardQuad(
                        eyeMesh,
                        pos.x, pos.y, pos.z,
                        particle.size * 4.88f * scale,
                        camRot,
                        eyeRoll - 1.06f,
                        ghostB
                );

                int eyeArgb = funnelColor(baseColor, 0xFFFFFF, alpha * FUNNEL_EYE_ALPHA_MULTIPLIER);
                addBillboardQuad(
                        eyeMesh,
                        pos.x, pos.y, pos.z,
                        particle.size * FUNNEL_EYE_SIZE_MULTIPLIER * scale,
                        camRot,
                        eyeRoll,
                        eyeArgb
                );
            }

            if (bloomMesh != null) {
                renderFunnelLeaks(bloomMesh, particle, pos, camRot, alpha, scale, baseColor, ageSeconds);

                float lifeT = particle.lifeProgress();
                float flashCenter = 0.34f + ColorEffects.hash01(particle.visualPhase + 5.73f) * 0.38f;
                float flash = 1.0f - AnimationUtility.smoothstep(Mth.clamp(Math.abs(lifeT - flashCenter) / 0.13f, 0.0f, 1.0f));
                if (flash > 0.002f) {
                    int flashArgb = funnelColor(baseColor, 0x7FE8FF, alpha * FUNNEL_FLASH_ALPHA_MULTIPLIER * flash);
                    addBillboardQuad(
                            bloomMesh,
                            pos.x, pos.y, pos.z,
                            particle.size * (1.45f + flash * 0.82f) * scale,
                            camRot,
                            particle.visualPhase * 0.21f,
                            flashArgb
                    );
                }
            }
        }
    }

    private void renderAirBubble(MeshBuilder shellMesh,
                                 MeshBuilder spriteMesh,
                                 Particle particle,
                                 Vec3 pos,
                                 Vector3f billboardRight,
                                 Vector3f billboardUp,
                                 float alpha,
                                 int baseColor,
                                 long nowMs) {
        long ageMs = particle.ageMs(nowMs);
        float ageSeconds = ageMs / 1000.0f;
        float lifeT = particle.lifeProgress(ageMs);
        float pulse = 0.5f + 0.5f * (float) Math.sin(ageSeconds * 3.1f + particle.visualPhase);
        float pop = 1.0f + AnimationUtility.smoothstep(pulse) * 0.09f;
        float radius = particle.size * BUBBLE_FILL_SIZE_MULTIPLIER * pop;
        float lightAngle = particle.visualPhase + 2.35f + ageSeconds * 0.16f;
        float depthAngle = lightAngle + (float) Math.PI;
        int brightColor = AnimatedRenderColors.mixArgb(baseColor, 0xFFFFFFFF, 0.72f);
        int softColor = AnimatedRenderColors.mixArgb(baseColor, 0xFFFFFFFF, 0.32f);
        int shadowColor = AnimatedRenderColors.mixArgb(baseColor, 0xFF000000, 0.42f);
        int sphereShadowColor = AnimatedRenderColors.mixArgb(baseColor, 0xFF000000, 0.38f);
        Quaternionf sphereRotation = bubbleSphereRotation.identity()
                .rotateY(particle.visualPhase + ageSeconds * 0.34f)
                .rotateX(particle.visualPhase * 0.47f + ageSeconds * 0.22f)
                .rotateZ(particle.visualPhase * -0.31f + ageSeconds * 0.16f);
        float sphereRadius = radius * 0.82f;

        if (shellMesh != null) {
            addSphereShell(shellMesh, pos, sphereRotation, bubbleSphereNormal, sphereRadius,
                    baseColor, brightColor, sphereShadowColor, alpha);
        }

        if (spriteMesh != null) {
            addBillboardQuadGradient(
                    spriteMesh,
                    pos.x, pos.y, pos.z,
                    particle.size * BUBBLE_GLOW_SIZE_MULTIPLIER * pop,
                    billboardRight, billboardUp,
                    particle.visualPhase * 0.22f + ageSeconds * 0.12f,
                    multiplyAlpha(shadowColor, alpha * BUBBLE_GLOW_ALPHA_MULTIPLIER * 0.58f),
                    multiplyAlpha(baseColor, alpha * BUBBLE_GLOW_ALPHA_MULTIPLIER * 0.88f),
                    multiplyAlpha(brightColor, alpha * BUBBLE_GLOW_ALPHA_MULTIPLIER * 1.32f),
                    multiplyAlpha(softColor, alpha * BUBBLE_GLOW_ALPHA_MULTIPLIER)
            );

            float depthDistance = radius * 0.22f;
            float depthRightScale = (float) Math.cos(depthAngle) * depthDistance;
            float depthUpScale = (float) Math.sin(depthAngle) * depthDistance;
            double depthX = pos.x + billboardRight.x() * depthRightScale + billboardUp.x() * depthUpScale;
            double depthY = pos.y + billboardRight.y() * depthRightScale + billboardUp.y() * depthUpScale;
            double depthZ = pos.z + billboardRight.z() * depthRightScale + billboardUp.z() * depthUpScale;
            float depthAlpha = alpha * BUBBLE_DEPTH_ALPHA_MULTIPLIER * (0.78f + pulse * 0.18f);
            addBillboardQuadGradient(
                    spriteMesh,
                    depthX, depthY, depthZ,
                    radius * 0.58f,
                    billboardRight, billboardUp,
                    particle.visualPhase * 0.31f,
                    multiplyAlpha(shadowColor, depthAlpha * 0.92f),
                    multiplyAlpha(baseColor, depthAlpha * 0.76f),
                    multiplyAlpha(softColor, depthAlpha * 0.42f),
                    multiplyAlpha(shadowColor, depthAlpha * 0.58f)
            );

            float fillAlpha = alpha * BUBBLE_FILL_ALPHA_MULTIPLIER;
            addBillboardQuadGradient(
                    spriteMesh,
                    pos.x, pos.y, pos.z,
                    radius,
                    billboardRight, billboardUp,
                    particle.visualPhase * -0.16f,
                    multiplyAlpha(shadowColor, fillAlpha * 0.92f),
                    multiplyAlpha(baseColor, fillAlpha * 1.06f),
                    multiplyAlpha(brightColor, fillAlpha * 0.98f),
                    multiplyAlpha(softColor, fillAlpha * 1.18f)
            );

            float highlightDistance = radius * 0.42f;
            float highlightRightScale = (float) Math.cos(lightAngle) * highlightDistance;
            float highlightUpScale = (float) Math.sin(lightAngle) * highlightDistance;
            double highlightX = pos.x + billboardRight.x() * highlightRightScale + billboardUp.x() * highlightUpScale;
            double highlightY = pos.y + billboardRight.y() * highlightRightScale + billboardUp.y() * highlightUpScale;
            double highlightZ = pos.z + billboardRight.z() * highlightRightScale + billboardUp.z() * highlightUpScale;
            float highlightAlpha = alpha * BUBBLE_HIGHLIGHT_ALPHA_MULTIPLIER * (1.0f - lifeT * 0.22f);
            addBillboardQuadGradient(
                    spriteMesh,
                    highlightX, highlightY, highlightZ,
                    particle.size * BUBBLE_HIGHLIGHT_SIZE_MULTIPLIER * (0.74f + pulse * 0.22f),
                    billboardRight, billboardUp,
                    particle.visualPhase,
                    multiplyAlpha(softColor, highlightAlpha * 0.46f),
                    multiplyAlpha(brightColor, highlightAlpha * 0.72f),
                    multiplyAlpha(0xFFFFFFFF, highlightAlpha),
                    multiplyAlpha(brightColor, highlightAlpha * 0.86f)
            );

            float secondaryAngle = lightAngle - 0.92f;
            float secondaryDistance = radius * 0.18f;
            float secondaryRightScale = (float) Math.cos(secondaryAngle) * secondaryDistance;
            float secondaryUpScale = (float) Math.sin(secondaryAngle) * secondaryDistance;
            double secondaryX = pos.x + billboardRight.x() * secondaryRightScale + billboardUp.x() * secondaryUpScale;
            double secondaryY = pos.y + billboardRight.y() * secondaryRightScale + billboardUp.y() * secondaryUpScale;
            double secondaryZ = pos.z + billboardRight.z() * secondaryRightScale + billboardUp.z() * secondaryUpScale;
            float secondaryHighlightAlpha = alpha * BUBBLE_HIGHLIGHT_ALPHA_MULTIPLIER * 0.42f * (0.84f + pulse * 0.16f);
            addBillboardQuadGradient(
                    spriteMesh,
                    secondaryX, secondaryY, secondaryZ,
                    particle.size * BUBBLE_SECONDARY_HIGHLIGHT_SIZE_MULTIPLIER,
                    billboardRight, billboardUp,
                    particle.visualPhase * -0.38f,
                    multiplyAlpha(baseColor, secondaryHighlightAlpha * 0.44f),
                    multiplyAlpha(brightColor, secondaryHighlightAlpha * 0.68f),
                    multiplyAlpha(0xFFFFFFFF, secondaryHighlightAlpha * 0.82f),
                    multiplyAlpha(softColor, secondaryHighlightAlpha * 0.58f)
            );
        }

    }

    private static boolean bubbleInFrustum(Particle particle, Vec3 pos) {
        if (RenderState.frustum == null) return true;
        // A billboard's axis-aligned extent may reach halfSize * sqrt(2). Include the 9% pulse
        // and a small numerical margin so culling cannot clip a visible glow at a frustum edge.
        double extent = particle.size * BUBBLE_CULL_EXTENT_MULTIPLIER;
        return Renderer3D.Culling.isInFrustum(new AABB(
                pos.x - extent, pos.y - extent, pos.z - extent,
                pos.x + extent, pos.y + extent, pos.z + extent
        ));
    }

    private MeshBuilder tintedTexturedBatch(Renderer3D renderer,
                                            boolean useDepth,
                                            Renderer3D.DepthMode depthMode,
                                            Identifier texture) {
        return renderer.batch(
                useDepth
                        ? CombatantRenderPipelines.WORLD_TEXTURED_TINT_ADDITIVE_LIQUID_IGNORE
                        : CombatantRenderPipelines.WORLD_TEXTURED_TINT_ADDITIVE,
                depthMode,
                Renderer3D.BatchBindings.none()
                        .withSampler("u_Texture", texture)
                        .withUniform("TextureTint", TextureTintUniforms.get())
        );
    }

    private Particle createParticle() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3 playerPos = mc.player.position();

        double range = Math.max(0.1, radius.get());
        double heightRange = Math.max(0.1, height.get());
        double px = playerPos.x + random.nextDouble(-range, range);
        double py = playerPos.y + random.nextDouble(0.0, heightRange);
        double pz = playerPos.z + random.nextDouble(-range, range);

        Vec3 pos = new Vec3(px, py, pz);
        boolean bubbleMode = isBubbleMode();
        Vec3 motion = bubbleMode
                ? new Vec3(
                        random.nextDouble(-0.42, 0.42) * MOTION_SCALE,
                        random.nextDouble(1.10, 2.55) * MOTION_SCALE,
                        random.nextDouble(-0.42, 0.42) * MOTION_SCALE
                )
                : new Vec3(
                        random.nextDouble(-1.0, 1.0) * MOTION_SCALE,
                        random.nextDouble(0.0, 2.0) * MOTION_SCALE,
                        random.nextDouble(-1.0, 1.0) * MOTION_SCALE
                );
        Vec3 rotationMotion = new Vec3(
                random.nextDouble(-1.0, 1.0) * MOTION_SCALE,
                random.nextDouble(-1.0, 1.0) * MOTION_SCALE,
                random.nextDouble(-1.0, 1.0) * MOTION_SCALE
        );

        int minLife = Math.max(1, Math.min(lifeMinMs.get(), lifeMaxMs.get()));
        int maxLife = Math.max(minLife, Math.max(lifeMinMs.get(), lifeMaxMs.get()));
        long life = randomLongClosed(random, minLife, maxLife);

        double minSize = Math.max(0.02, Math.min(sizeMin.get(), sizeMax.get()));
        double maxSize = Math.max(minSize, Math.max(sizeMin.get(), sizeMax.get()));
        float size = (float) randomDoubleClosed(random, minSize, maxSize);

        float visualPhase = (float) random.nextDouble(0.0, Math.PI * 2.0);
        float eyeSpin = (float) random.nextDouble(0.38, 0.72);
        float distortionSpin = (float) random.nextDouble(-0.28, -0.12);

        return new Particle(pos, motion, rotationMotion, life, size, visualPhase, eyeSpin, distortionSpin);
    }

    private void renderFunnelLeaks(MeshBuilder mesh,
                                   Particle particle,
                                   Vec3 pos,
                                   Quaternionf camRot,
                                   float alpha,
                                   float scale,
                                   int baseColor,
                                   float ageSeconds) {
        for (int i = 0; i < 4; i++) {
            float seed = ColorEffects.hash01(particle.visualPhase + i * 13.17f);
            float angle = particle.visualPhase + i * 1.5708f + ageSeconds * (0.28f + seed * 0.16f);
            float distance = particle.size * scale * (1.45f + seed * 1.10f);
            Vec3 leakPos = offsetInBillboardPlane(pos, camRot, angle, distance);

            float wave = 0.5f + 0.5f * (float) Math.sin(ageSeconds * (2.0f + seed * 0.9f) + particle.visualPhase + i);
            float leakAlpha = alpha * FUNNEL_LEAK_ALPHA_MULTIPLIER * (0.45f + AnimationUtility.smoothstep(wave) * 0.55f);
            int leakRgb = switch (i & 3) {
                case 0 -> 0xFF3A24;
                case 1 -> 0xA7FF2E;
                case 2 -> 0xFFD84E;
                default -> 0x55F7FF;
            };

            addBillboardRect(
                    mesh,
                    leakPos.x, leakPos.y, leakPos.z,
                    particle.size * scale * (0.055f + seed * 0.035f),
                    particle.size * scale * (0.42f + seed * 0.34f),
                    camRot,
                    angle - 1.5708f,
                    funnelColor(baseColor, leakRgb, leakAlpha)
            );
        }
    }

    private int funnelColor(int baseArgb, int sourceRgb, float alphaMultiplier) {
        return ColorEffects.argbWithBaseAlpha(baseArgb, sourceRgb, alphaMultiplier);
    }

    private int colorForParticle(Particle particle, int index) {
        int phase = colorPhase.get()
                + Math.round(index * colorSpread.get() / 90.0f)
                + Math.round(particle.visualPhase * 57.29578f);
        return AnimatedRenderColors.resolve(
                animatedColorMode(),
                colorSpeed.get(),
                phase,
                color.getArgb(),
                color2.getArgb(),
                true
        );
    }

    private boolean usesSecondaryColor() {
        return AnimatedRenderColors.usesSecondary(animatedColorMode());
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

    private int funnelTintRgb() {
        if (funnelTintSyncTheme.get()) {
            return Theme.theme().accent() & 0x00FFFFFF;
        }
        return funnelTintColor.getArgb() & 0x00FFFFFF;
    }

    private boolean isFunnelTintControlsVisible() {
        return isFunnelMode() && funnelTintMode.get() != ColorBlendMode.ORIGINAL;
    }

    private boolean isManualFunnelTintVisible() {
        return isFunnelTintControlsVisible() && !funnelTintSyncTheme.get();
    }

    private boolean isFunnelMode() {
        return mode.get() == Mode.FUNNEL;
    }

    private boolean isBubbleMode() {
        return mode.get() == Mode.AIR_BUBBLES;
    }

    private enum Mode implements EnumValue.AliasProvider {
        QUADS,
        AIR_BUBBLES,
        FUNNEL;

        @Override
        public List<String> aliases() {
            return switch (this) {
                case AIR_BUBBLES -> List.of("AirBubbles");
                case QUADS -> List.of("Quads");
                case FUNNEL -> List.of("Funnel");
            };
        }
    }

    private static final class Particle {
        private final long spawnMs;
        private final long lifeMs;
        private final float size;
        private final float visualPhase;
        private final float eyeSpin;
        private final float distortionSpin;
        private Vec3 prevPos;
        private Vec3 pos;
        private Vec3 prevRotation;
        private Vec3 rotation;
        private Vec3 motion;
        private Vec3 rotationMotion;

        private Particle(Vec3 pos, Vec3 motion, Vec3 rotationMotion, long lifeMs, float size,
                         float visualPhase, float eyeSpin, float distortionSpin) {
            this.prevPos = pos;
            this.pos = pos;
            this.prevRotation = Vec3.ZERO;
            this.rotation = Vec3.ZERO;
            this.motion = motion;
            this.rotationMotion = rotationMotion;
            this.spawnMs = System.currentTimeMillis();
            this.lifeMs = Math.max(1L, lifeMs);
            this.size = size;
            this.visualPhase = visualPhase;
            this.eyeSpin = eyeSpin;
            this.distortionSpin = distortionSpin;
        }

        private void tick() {
            prevPos = pos;
            prevRotation = rotation;
            pos = pos.add(motion);
            rotation = rotation.add(rotationMotion);
            motion = motion.scale(DRAG);
            rotationMotion = rotationMotion.scale(DRAG);
        }

        private float alpha() {
            return alpha(System.currentTimeMillis());
        }

        private float alpha(long nowMs) {
            long age = nowMs - spawnMs;
            if (age <= 0L) {
                return 0.0f;
            }
            if (age < ALPHA_ANIM_MS) {
                return AnimationUtility.smoothstep(age / (float) ALPHA_ANIM_MS);
            }
            if (age < lifeMs) {
                return 1.0f;
            }
            float fade = 1.0f - (age - lifeMs) / (float) ALPHA_ANIM_MS;
            return AnimationUtility.smoothstep(Mth.clamp(fade, 0.0f, 1.0f));
        }

        private float funnelAlpha() {
            long age = ageMs();
            if (age <= 0L) {
                return 0.0f;
            }

            float appear = AnimationUtility.easeOutCubic(age / 720.0f);
            float disappear = 1.0f;
            if (age > lifeMs) {
                disappear = 1.0f - AnimationUtility.easeInCubic((age - lifeMs) / (float) ALPHA_ANIM_MS);
            }

            float breath = 0.88f + 0.12f * (float) Math.sin(age * 0.0032f + visualPhase);
            return AnimationUtility.clamp01(appear * disappear * breath);
        }

        private float funnelScale() {
            long age = ageMs();
            float appear = AnimationUtility.easeOutCubic(age / 900.0f);
            float disappear = 1.0f;
            if (age > lifeMs) {
                disappear = 1.0f - AnimationUtility.smoothstep((age - lifeMs) / (float) ALPHA_ANIM_MS);
            }

            float open = AnimationUtility.lerp(0.68f, 1.04f, appear);
            float close = AnimationUtility.lerp(0.82f, 1.0f, disappear);
            return open * close;
        }

        private long ageMs() {
            return System.currentTimeMillis() - spawnMs;
        }

        private long ageMs(long nowMs) {
            return nowMs - spawnMs;
        }

        private float lifeProgress() {
            return AnimationUtility.clamp01(ageMs() / (float) Math.max(1L, lifeMs));
        }

        private float lifeProgress(long ageMs) {
            return AnimationUtility.clamp01(ageMs / (float) Math.max(1L, lifeMs));
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - spawnMs >= lifeMs + ALPHA_ANIM_MS;
        }

        private boolean isExpired(long nowMs) {
            return nowMs - spawnMs >= lifeMs + ALPHA_ANIM_MS;
        }

        private Vec3 interpolatePos(float tickDelta) {
            return prevPos.lerp(pos, tickDelta);
        }

        private Vec3 interpolateRotation(float tickDelta) {
            return prevRotation.lerp(rotation, tickDelta);
        }
    }
}
