/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.relations.CategoryService;
import combatant.client.mixininterface.IEntity;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.math.RenderMath;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.effects.lens.WorldTargetLenses;
import combatant.client.render.effects.orbit.WorldOrbitGravity;
import combatant.client.render.effects.mask.WorldPostProcessMasks;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.util.target.TargetingUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

@ModuleInfo(id = "targetesp", displayName = "TargetESP", aliases = {"target", "targetrender"}, category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.targetesp.description")
public class TargetESP extends Module {

    private static final String SETTING_MODE = "mode";
    private static final String SETTING_COLOR = "color";
    private static final String SETTING_COLOR_MODE = "color_mode";
    private static final String SETTING_COLOR_SPEED = "color_speed";
    private static final String SETTING_COLOR_PHASE = "color_phase";
    private static final String SETTING_COLOR_SPREAD = "color_spread";
    private static final String SETTING_COLOR2 = "color2";
    private static final String SETTING_USE_RELATION_COLORS = "use_relation_colors";
    private static final String SETTING_RELATION_COLOR_MIX = "relation_color_mix";
    private static final String SETTING_PLAYERS_ONLY = "players_only";
    private static final String SETTING_CROSSHAIR_TARGETS = "crosshair_targets";
    private static final String SETTING_ALPHA_ANIMATION_SPEED = "alpha_animation_speed";
    private static final String SETTING_ESP_LENGTH = "esp_length";
    private static final String SETTING_ESP_FACTOR = "esp_factor";
    private static final String SETTING_ESP_SHAKING = "esp_shaking";
    private static final String SETTING_ESP_AMPLITUDE = "esp_amplitude";
    private static final String SETTING_CAPTURE_SPIN_SPEED = "capture_spin_speed";
    private static final String SETTING_CAPTURE_HALO = "capture_halo";
    private static final String SETTING_CAPTURE_HALO_INTENSITY = "capture_halo_intensity";
    private static final String SETTING_CAPTURE_PULSE = "capture_pulse";
    private static final String SETTING_CAPTURE_PULSE_SPEED = "capture_pulse_speed";
    private static final String SETTING_CAPTURE_REFRACTION = "capture_refraction";
    private static final String SETTING_CAPTURE_REFRACTION_STRENGTH = "capture_refraction_strength";
    private static final String SETTING_CAPTURE_CHROMATIC = "capture_chromatic";
    private static final String SETTING_RING_THICKNESS = "ring_thickness";
    private static final String SETTING_RING_INTENSITY = "ring_intensity";
    private static final String SETTING_RING_SPEED = "ring_speed";
    private static final String SETTING_ORBIT_DENSITY = "layout_density";
    private static final String SETTING_ORBIT_SPEED = "layout_speed";
    private static final String SETTING_ORBIT_SCALE = "layout_radius_scale";
    private static final String SETTING_CRYSTAL_HIT_RED = "crystal_hit_red";
    private static final String SETTING_CRYSTAL_COUNT = "crystal_count";
    private static final String SETTING_CRYSTAL_SIZE = "crystal_size";
    private static final String SETTING_CRYSTAL_ORBIT_RADIUS = "crystal_orbit_radius";
    private static final String SETTING_DISTORTION = "distortion";
    private static final String SETTING_DISTORTION_STRENGTH = "distortion_strength";
    private static final String SETTING_DISTORTION_RADIUS = "distortion_radius";
    private static final String SETTING_DISTORTION_LENGTH = "distortion_length";
    private static final Identifier CAPTURE_MARK_TEXTURE = TextureStorage.CAPTURE;
    private static final float CRYSTAL_BASE_SIZE = 0.155f;
    private static final int CRYSTAL_SIDES = 8;
    private static final float ALPHA_TICK_DT = 1.0f / 20.0f;
    private static final float ALPHA_SNAP_EPSILON = 0.02f;
    private static float prevCircleStep;
    private static float circleStep;
    private static double captureValue = 1.0;
    private static double prevCaptureValue;
    private static double captureSpeed = 1.0;
    private static double captureSpeedScale = 1.0;
    private static boolean captureFlipSpeed;
    private final Minecraft mc = Minecraft.getInstance();
    private final ModeValue espMode = modeSetting(
            "targetEspMode",
            SETTING_MODE,
            "ghosts",
            "ghosts", "caustics", "ring", "capture_mark", "crystals", "orbit"
    );
    private final RGBAColorValue colorValue = color("targetEspColor", SETTING_COLOR, "#FFFF5555");
    private final ModeValue colorMode = visibleWhen(modeSetting(
            "targetEspColorMode",
            SETTING_COLOR_MODE,
            "Static",
            "Static", "Rainbow", "LightRainbow", "Sky", "Fade", "DoubleColor", "Analogous", "Theme"
    ), () -> true);
    private final NumberValue<Integer> colorSpeed =
            visibleWhen(num("targetEspColorSpeed", SETTING_COLOR_SPEED, 18, 2, 54), () -> true);
    private final NumberValue<Integer> colorPhase =
            num("targetEspColorPhase", SETTING_COLOR_PHASE, 0, 0, 360);
    private final NumberValue<Integer> colorSpread =
            num("targetEspColorSpread", SETTING_COLOR_SPREAD, 90, -720, 720);
    private final RGBAColorValue colorValue2 =
            visibleWhen(color("targetEspColor2", SETTING_COLOR2, "#FF55FFFF"), this::usesSecondaryColor);
    private final BooleanValue useRelationColors =
            boolCommon("targetEspUseRelationColors", SETTING_USE_RELATION_COLORS, CommonSettingSchemas.PLAYER_USE_RELATIONS, false);
    private final NumberValue<Float> relationColorMix =
            visibleWhen(num("targetEspRelationColorMix", SETTING_RELATION_COLOR_MIX, 1.0f, 0.0f, 1.0f), useRelationColors::get);
    private final BooleanValue playersOnly =
            bool("targetEspPlayersOnly", SETTING_PLAYERS_ONLY, false);
    private final BooleanValue crosshairTargets =
            bool("targetEspCrosshairTargets", SETTING_CROSSHAIR_TARGETS, true);
    private final NumberValue<Float> alphaAnimationSpeed =
            num("targetEspAlphaAnimationSpeed", SETTING_ALPHA_ANIMATION_SPEED, 8.0f, 2.0f, 10.0f);
    private final NumberValue<Integer> espLength =
            visibleWhen(num("targetEspLength", SETTING_ESP_LENGTH, 14, 1, 40), this::isGhostOrCausticsMode);
    private final NumberValue<Integer> espFactor =
            visibleWhen(num("targetEspFactor", SETTING_ESP_FACTOR, 8, 1, 20), this::isGhostOrCausticsMode);
    private final NumberValue<Float> espShaking =
            visibleWhen(num("targetEspShaking", SETTING_ESP_SHAKING, 1.8f, 1.5f, 10f), this::isGhostMode);
    private final NumberValue<Float> espAmplitude =
            visibleWhen(num("targetEspAmplitude", SETTING_ESP_AMPLITUDE, 3f, 0.1f, 8f), this::isGhostOrCausticsMode);
    private final NumberValue<Float> captureSpinSpeed =
            visibleWhen(num("targetEspCaptureSpinSpeed", SETTING_CAPTURE_SPIN_SPEED, 1.0f, 0.2f, 4.0f), this::isCaptureMarkMode);
    private final BooleanValue captureHalo =
            visibleWhen(bool("targetEspCaptureHalo", SETTING_CAPTURE_HALO, true), this::isCaptureMarkMode);
    private final NumberValue<Float> captureHaloIntensity =
            visibleWhen(num("targetEspCaptureHaloIntensity", SETTING_CAPTURE_HALO_INTENSITY, 0.52f, 0.05f, 1.25f),
                    () -> isCaptureMarkMode() && captureHalo.get());
    private final BooleanValue capturePulse =
            visibleWhen(bool("targetEspCapturePulse", SETTING_CAPTURE_PULSE, true), this::isCaptureMarkMode);
    private final NumberValue<Float> capturePulseSpeed =
            visibleWhen(num("targetEspCapturePulseSpeed", SETTING_CAPTURE_PULSE_SPEED, 1.0f, 0.2f, 3.0f),
                    () -> isCaptureMarkMode() && capturePulse.get());
    private final BooleanValue captureRefraction =
            visibleWhen(bool("targetEspCaptureRefraction", SETTING_CAPTURE_REFRACTION, true), this::isCaptureMarkMode);
    private final NumberValue<Float> captureRefractionStrength =
            visibleWhen(num("targetEspCaptureRefractionStrength", SETTING_CAPTURE_REFRACTION_STRENGTH, 0.075f, 0.0f, 0.22f),
                    () -> isCaptureMarkMode() && captureRefraction.get());
    private final BooleanValue captureChromatic =
            visibleWhen(bool("targetEspCaptureChromatic", SETTING_CAPTURE_CHROMATIC, true),
                    () -> isCaptureMarkMode() && captureRefraction.get());
    private final NumberValue<Float> ringThickness =
            visibleWhen(num("targetEspRingThickness", SETTING_RING_THICKNESS, 0.052f, 0.025f, 0.14f), this::isRingMode);
    private final NumberValue<Float> ringIntensity =
            visibleWhen(num("targetEspRingIntensity", SETTING_RING_INTENSITY, 0.86f, 0.3f, 1.8f), this::isRingMode);
    private final NumberValue<Float> ringSpeed =
            visibleWhen(num("targetEspRingSpeed", SETTING_RING_SPEED, 1.0f, 0.2f, 3.0f), this::isRingMode);
    private final NumberValue<Integer> orbitDensity =
            visibleWhen(num("targetEspLayoutDensity", SETTING_ORBIT_DENSITY, 180, 96, 256), this::isOrbitMode);
    private final NumberValue<Float> orbitSpeed =
            visibleWhen(num("targetEspLayoutSpeed", SETTING_ORBIT_SPEED, 1.0f, 0.15f, 3.0f), this::isOrbitMode);
    private final NumberValue<Float> orbitScale =
            visibleWhen(num("targetEspLayoutRadiusScale", SETTING_ORBIT_SCALE, 0.8f, 0.55f, 1.65f), this::isOrbitMode);
    private final BooleanValue crystalHitRed =
            visibleWhen(bool("targetEspCrystalHitRed", SETTING_CRYSTAL_HIT_RED, true), this::isCrystalsMode);
    private final NumberValue<Integer> crystalCount =
            visibleWhen(num("targetEspCrystalCount", SETTING_CRYSTAL_COUNT, 9, 1, 14), this::isCrystalsMode);
    private final NumberValue<Float> crystalSize =
            visibleWhen(num("targetEspCrystalSize", SETTING_CRYSTAL_SIZE, 1.0f, 0.35f, 2.5f), this::isCrystalsMode);
    private final NumberValue<Float> crystalOrbitRadius =
            visibleWhen(num("targetEspCrystalOrbitRadius", SETTING_CRYSTAL_ORBIT_RADIUS, 1.0f, 0.45f, 2.4f), this::isCrystalsMode);
    private final BooleanValue distortion =
            visibleWhen(bool("targetEspDistortion", SETTING_DISTORTION, true), this::isCrystalsMode);
    private final NumberValue<Float> distortionStrength =
            visibleWhen(num("targetEspDistortionStrength", SETTING_DISTORTION_STRENGTH, 0.145f, 0.01f, 0.36f),
                    () -> isCrystalsMode() && distortion.get());
    private final NumberValue<Float> distortionRadius =
            visibleWhen(num("targetEspDistortionRadius", SETTING_DISTORTION_RADIUS, 1.0f, 0.35f, 2.8f),
                    () -> isCrystalsMode() && distortion.get());
    private final NumberValue<Float> distortionLength =
            visibleWhen(num("targetEspDistortionLength", SETTING_DISTORTION_LENGTH, 1.0f, 0.35f, 2.8f),
                    () -> isCrystalsMode() && distortion.get());
    private final List<CrystalInstance> crystalList = new ArrayList<>();
    private LivingEntity target;
    private LivingEntity renderTarget;
    private float targetAlpha;
    private Entity lastCrystalTarget;
    private float crystalRotationAngle;
    private int crystalLayoutCount = -1;
    private float crystalLayoutRadius = Float.NaN;

    private static void tickTargetEsp() {
        prevCircleStep = circleStep;
        circleStep += 0.15f;

        prevCaptureValue = captureValue;
        captureValue += captureSpeed * captureSpeedScale;
        if (captureSpeed > 25.0) captureFlipSpeed = true;
        if (captureSpeed < -25.0) captureFlipSpeed = false;
        captureSpeed = captureFlipSpeed
                ? captureSpeed - 0.5 * captureSpeedScale
                : captureSpeed + 0.5 * captureSpeedScale;
    }

    private static void setCaptureSpeedScale(float scale) {
        if (scale <= 0f) {
            captureSpeedScale = 0.05;
        } else {
            captureSpeedScale = scale;
        }
    }

    private void renderTargetRing(Renderer3D renderer,
                                  Entity target,
                                  float tickDelta,
                                  IntFunction<Integer> palette) {
        if (renderer == null || target == null || palette == null) return;

        double cs = (prevCircleStep + (circleStep - prevCircleStep) * tickDelta) * ringSpeed.get();
        double previous = absSinAnimation(cs - 0.45f);
        double current = absSinAnimation(cs);
        Vec3 base = RenderMath.getLerpedPos(target, tickDelta);
        float previousY = (float) (base.y + previous * target.getBbHeight());
        float currentY = (float) (base.y + current * target.getBbHeight());
        float majorRadius = Math.max(0.34f, target.getBbWidth() * 0.88f);
        float minorRadius = Mth.clamp(ringThickness.get(), 0.025f, 0.14f);
        float intensity = Math.max(0.1f, ringIntensity.get());
        float scanSpeed = Math.max(0.05f, ringSpeed.get());
        float time = (target.tickCount - 1 + tickDelta) / 20.0f;
        int majorSegments = 112;
        int tubeSegments = 10;

        MeshBuilder mesh = renderer.batch(
                CombatantRenderPipelines.WORLD_TARGET_RING_DEPTH,
                Renderer3D.DepthMode.ENTITY_ONLY
        );
        if (mesh == null) return;

        appendTargetTorus(mesh, base, majorRadius, minorRadius, majorSegments, tubeSegments,
                previousY, currentY, time, 0.0f, scanSpeed, intensity, palette, 0, 1.0f);
        appendTargetTorus(mesh, base, majorRadius, minorRadius * 1.80f, majorSegments, tubeSegments,
                previousY, currentY, time, 1.0f, scanSpeed, intensity * 0.40f, palette, 18, 0.72f);
        appendTargetTorus(mesh, base, majorRadius * 1.025f, minorRadius * 0.82f, majorSegments, 8,
                previousY, previousY, time - 0.18f, 1.0f, scanSpeed, intensity * 0.30f, palette, 42, 0.46f);
    }

    private static void appendTargetTorus(MeshBuilder mesh,
                                          Vec3 base,
                                          float majorRadius,
                                          float minorRadius,
                                          int majorSegments,
                                          int tubeSegments,
                                          float previousY,
                                          float currentY,
                                          float time,
                                          float layer,
                                          float speed,
                                          float intensity,
                                          IntFunction<Integer> palette,
                                          int phaseOffset,
                                          float layerAlpha) {
        float middleY = (previousY + currentY) * 0.5f;
        float halfHeight = Math.abs(currentY - previousY) * 0.5f + minorRadius;
        float alphaDirection = Mth.clamp((currentY - previousY) / Math.max(0.12f, halfHeight * 1.5f), -1.0f, 1.0f);
        float maximumAlpha = 170.0f / 255.0f * layerAlpha;
        for (int i = 0; i < majorSegments; i++) {
            float u0 = i / (float) majorSegments;
            float u1 = (i + 1) / (float) majorSegments;
            float a0 = u0 * Mth.TWO_PI;
            float a1 = u1 * Mth.TWO_PI;
            int baseColor0 = palette.apply(Math.round(u0 * 360.0f) + phaseOffset);
            int baseColor1 = palette.apply(Math.round(u1 * 360.0f) + phaseOffset);

            for (int j = 0; j < tubeSegments; j++) {
                float v0 = j / (float) tubeSegments;
                float v1 = (j + 1) / (float) tubeSegments;
                float b0 = v0 * Mth.TWO_PI;
                float b1 = v1 * Mth.TWO_PI;
                float grad0 = 0.5f + 0.5f * alphaDirection * Mth.sin(b0);
                float grad1 = 0.5f + 0.5f * alphaDirection * Mth.sin(b1);
                int c00 = applyOpacity(baseColor0, maximumAlpha * grad0);
                int c10 = applyOpacity(baseColor1, maximumAlpha * grad0);
                int c11 = applyOpacity(baseColor1, maximumAlpha * grad1);
                int c01 = applyOpacity(baseColor0, maximumAlpha * grad1);

                Vector3f p00 = torusPoint(base, majorRadius, minorRadius, middleY, halfHeight, a0, b0);
                Vector3f p10 = torusPoint(base, majorRadius, minorRadius, middleY, halfHeight, a1, b0);
                Vector3f p11 = torusPoint(base, majorRadius, minorRadius, middleY, halfHeight, a1, b1);
                Vector3f p01 = torusPoint(base, majorRadius, minorRadius, middleY, halfHeight, a0, b1);
                Vector3f n00 = torusNormal(a0, b0, minorRadius / halfHeight);
                Vector3f n10 = torusNormal(a1, b0, minorRadius / halfHeight);
                Vector3f n11 = torusNormal(a1, b1, minorRadius / halfHeight);
                Vector3f n01 = torusNormal(a0, b1, minorRadius / halfHeight);

                mesh.ensureQuadCapacity();
                int q0 = addRingVertex(mesh, p00, u0, v0, c00, time, layer, speed, intensity, n00);
                int q1 = addRingVertex(mesh, p10, u1, v0, c10, time, layer, speed, intensity, n10);
                int q2 = addRingVertex(mesh, p11, u1, v1, c11, time, layer, speed, intensity, n11);
                int q3 = addRingVertex(mesh, p01, u0, v1, c01, time, layer, speed, intensity, n01);
                mesh.quad(q0, q1, q2, q3);
            }
        }
    }

    private static Vector3f torusPoint(Vec3 base,
                                        float majorRadius,
                                        float minorRadius,
                                        float middleY,
                                        float halfHeight,
                                        float a,
                                        float b) {
        float radial = majorRadius + minorRadius * Mth.cos(b);
        return new Vector3f(
                (float) base.x + Mth.cos(a) * radial,
                middleY + Mth.sin(b) * halfHeight,
                (float) base.z + Mth.sin(a) * radial
        );
    }

    private static Vector3f torusNormal(float a, float b, float verticalNormalScale) {
        return new Vector3f(
                Mth.cos(a) * Mth.cos(b),
                Mth.sin(b) * verticalNormalScale,
                Mth.sin(a) * Mth.cos(b)
        ).normalize();
    }

    private static int addRingVertex(MeshBuilder mesh,
                                     Vector3f position,
                                     float u,
                                     float v,
                                     int argb,
                                     float time,
                                     float layer,
                                     float speed,
                                     float intensity,
                                     Vector3f normal) {
        mesh.vec3(position.x, position.y, position.z)
                .vec2(u, v)
                .colorArgb(argb)
                .vec4(time, layer, speed, intensity)
                .vec4(normal.x, normal.y, normal.z, layer);
        return mesh.next();
    }


    private void renderClassicGhosts(Renderer3D renderer, Entity target, float tickDelta,
                                     IntFunction<Integer> palette) {
        if (renderer == null || target == null || palette == null) return;
        Renderer3D.DepthMode depthMode = Renderer3D.DepthMode.ENTITY_ONLY;
        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_DEPTH,
                TextureStorage.FIRE_FLY, depthMode);
        if (mesh == null) return;

        Vec3 base = RenderMath.getLerpedPos(target, tickDelta);
        float age = target.tickCount - 1.0f + tickDelta;
        int length = espLength.get();
        int factor = espFactor.get();
        float shaking = Math.max(0.01f, espShaking.get());
        float amplitude = espAmplitude.get();
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i <= length; i++) {
                double radians = Math.toRadians((((float) i / 1.5f + age) * factor + j * 120.0f) % (factor * 360.0f));
                double wobble = Math.sin(Math.toRadians(age * 2.5f + i * (j + 1)) * amplitude) / shaking;
                float offset = i / (float) length;
                int tint = applyOpacity(palette.apply((int) (180.0f * offset)), offset);
                float size = Math.max(0.24f * offset, 0.2f);
                addBillboardQuad(mesh,
                        base.x + Math.cos(radians) * target.getBbWidth(),
                        base.y + 1.0 + wobble,
                        base.z + Math.sin(radians) * target.getBbWidth(), size, tint);
            }
        }
    }

    private void renderCaustics(Renderer3D renderer,
                              int length, int factor, float amplitude,
                              Entity target, float tickDelta, IntFunction<Integer> palette) {
        if (renderer == null || target == null || palette == null || RenderState.cameraPos == null) return;

        Vec3 base = RenderMath.getLerpedPos(target, tickDelta);
        float height = Math.max(0.9f, target.getBbHeight());
        float radius = Math.max(0.34f, target.getBbWidth() * 0.82f);
        float time = (target.tickCount - 1 + tickDelta) / 20.0f;
        float intensity = Mth.clamp(0.78f + amplitude * 0.055f, 0.72f, 1.28f);

        Renderer3D.DepthMode depthMode = Renderer3D.DepthMode.ENTITY_ONLY;
        MeshBuilder mesh = renderer.batch(CombatantRenderPipelines.WORLD_TARGET_GHOST_DEPTH, depthMode);
        if (mesh == null) return;

        int blades = Mth.clamp(3 + factor / 4, 4, 7);
        int segments = Mth.clamp(16 + length * 2, 28, 72);
        for (int blade = 0; blade < blades; blade++) {
            float phase = blade / (float) blades * Mth.TWO_PI;
            Vector3f[] points = new Vector3f[segments + 1];
            float[] widths = new float[segments + 1];
            int[] colors = new int[segments + 1];
            for (int i = 0; i <= segments; i++) {
                float u = i / (float) segments;
                float fade = (float) Math.pow(Math.sin(Math.PI * u), 0.62);
                points[i] = ghostVeilPoint(base, radius, height, u, phase, time);
                widths[i] = radius * (0.14f + 0.10f * fade);
                colors[i] = palette.apply(blade * 37 + Math.round(u * 90.0f));
            }
            appendGhostRibbon(mesh, points, widths, colors, time, 0.0f, intensity, blade / (float) blades);
        }
    }

    private static Vector3f ghostVeilPoint(Vec3 base,
                                           float radius,
                                           float height,
                                           float u,
                                           float phase,
                                           float time) {
        float y = (float) base.y + 0.06f + u * height;
        float arch = Mth.sin(u * (float) Math.PI);
        float angle = phase + time * 0.28f + u * Mth.TWO_PI * 0.12f;
        float radial = radius * (0.76f + arch * 0.30f);
        return new Vector3f(
                (float) base.x + Mth.cos(angle) * radial,
                y,
                (float) base.z + Mth.sin(angle) * radial
        );
    }

    private static void appendGhostRibbon(MeshBuilder mesh,
                                          Vector3f[] points,
                                          float[] widths,
                                          int[] colors,
                                          float time,
                                          float motion,
                                          float intensity,
                                          float pathId) {
        int count = points.length;
        if (count < 2) return;
        Vector3f[] left = new Vector3f[count];
        Vector3f[] right = new Vector3f[count];
        Vector3f[] normals = new Vector3f[count];
        Vector3f previousSide = null;
        for (int i = 0; i < count; i++) {
            Vector3f before = points[Math.max(0, i - 1)];
            Vector3f after = points[Math.min(count - 1, i + 1)];
            Vector3f tangent = new Vector3f(after).sub(before);
            if (tangent.lengthSquared() < 1.0e-8f) tangent.set(0.0f, 1.0f, 0.0f); else tangent.normalize();
            Vector3f point = points[i];
            Vector3f view = new Vector3f(
                    (float) RenderState.cameraPos.x - point.x,
                    (float) RenderState.cameraPos.y - point.y,
                    (float) RenderState.cameraPos.z - point.z
            );
            if (view.lengthSquared() < 1.0e-8f) view.set(0.0f, 0.0f, 1.0f); else view.normalize();
            Vector3f side = new Vector3f(tangent).cross(view);
            if (side.lengthSquared() < 1.0e-8f) side.set(1.0f, 0.0f, 0.0f); else side.normalize();
            if (previousSide != null && side.dot(previousSide) < 0.0f) side.negate();
            previousSide = new Vector3f(side);
            Vector3f offset = new Vector3f(side).mul(widths[i]);
            left[i] = new Vector3f(point).sub(offset);
            right[i] = new Vector3f(point).add(offset);
            normals[i] = new Vector3f(side).cross(tangent).normalize();
        }

        for (int i = 0; i < count - 1; i++) {
            float u0 = i / (float) (count - 1);
            float u1 = (i + 1) / (float) (count - 1);
            mesh.ensureQuadCapacity();
            int v0 = addGhostVertex(mesh, left[i], u0, 0.0f, colors[i], time, 0.0f, motion, intensity, normals[i], pathId);
            int v1 = addGhostVertex(mesh, left[i + 1], u1, 0.0f, colors[i + 1], time, 0.0f, motion, intensity, normals[i + 1], pathId);
            int v2 = addGhostVertex(mesh, right[i + 1], u1, 1.0f, colors[i + 1], time, 0.0f, motion, intensity, normals[i + 1], pathId);
            int v3 = addGhostVertex(mesh, right[i], u0, 1.0f, colors[i], time, 0.0f, motion, intensity, normals[i], pathId);
            mesh.quad(v0, v1, v2, v3);
        }
    }

    private static int addGhostVertex(MeshBuilder mesh,
                                      Vector3f position,
                                      float u,
                                      float v,
                                      int argb,
                                      float time,
                                      float layer,
                                      float motion,
                                      float intensity,
                                      Vector3f normal,
                                      float pathId) {
        mesh.vec3(position.x, position.y, position.z)
                .vec2(u, v)
                .colorArgb(argb)
                .vec4(time, layer, motion, intensity)
                .vec4(normal.x, normal.y, normal.z, pathId);
        return mesh.next();
    }


    private void renderCaptureMark(Renderer3D renderer,
                                   Entity target,
                                   float tickDelta,
                                   IntFunction<Integer> palette) {
        if (renderer == null || target == null || palette == null) return;
        Vec3 pos = RenderMath.getLerpedPos(target, tickDelta);
        double cx = pos.x;
        double cy = pos.y + target.getEyeHeight(target.getPose()) / 2f;
        double cz = pos.z;
        float angle = interpolateFloat((float) prevCaptureValue, (float) captureValue, tickDelta);
        float time = (target.tickCount - 1 + tickDelta) / 20.0f;

        float pulse = capturePulse.get()
                ? 0.5f + 0.5f * Mth.sin(time * Math.max(0.05f, capturePulseSpeed.get()) * 3.64f)
                : 0.0f;
        float size = 0.75f * (capturePulse.get() ? 0.985f + pulse * 0.030f : 1.0f);
        float haloLift = captureHalo.get()
                ? Mth.clamp(captureHaloIntensity.get() * (0.055f + pulse * 0.018f), 0.0f, 0.16f)
                : 0.0f;
        float alphaGain = capturePulse.get() ? 0.90f + pulse * 0.10f : 1.0f;

        Renderer3D.DepthMode depthMode = Renderer3D.DepthMode.NONE;
        var capturePipeline = CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE;

        MeshBuilder markMesh = renderer.batchTextured(capturePipeline, CAPTURE_MARK_TEXTURE, depthMode);
        if (markMesh != null) {
            final float lift = haloLift;
            final float gain = alphaGain;
            IntFunction<Integer> markPalette = index -> {
                int c = palette.apply(index);
                if (lift > 0.0001f) {
                    c = AnimatedRenderColors.mixArgb(c, (c & 0xFF000000) | 0x00FFFFFF, lift);
                }
                return AnimatedRenderColors.scaleAlpha(c, gain);
            };
            addRotatedBillboard(markMesh, cx, cy, cz, size, angle, markPalette);
        }
    }

    private static void appendCrystalGeometry(MeshBuilder mesh,
                                              Vec3 targetPos,
                                              CrystalInstance crystal,
                                              float orbitYawRad,
                                              float scale,
                                              int argb,
                                              boolean filled) {
        if (mesh == null || crystal == null || targetPos == null) {
            return;
        }

        float s = CRYSTAL_BASE_SIZE * scale;
        float hPrism = s;
        float hPyramid = s * 1.5f;

        Vector3f[] topVertices = new Vector3f[CRYSTAL_SIDES];
        Vector3f[] bottomVertices = new Vector3f[CRYSTAL_SIDES];
        for (int i = 0; i < CRYSTAL_SIDES; i++) {
            float angle = (float) (2.0 * Math.PI * i / CRYSTAL_SIDES);
            float x = (float) (s * Math.cos(angle));
            float z = (float) (s * Math.sin(angle));
            topVertices[i] = new Vector3f(x, hPrism * 0.5f, z);
            bottomVertices[i] = new Vector3f(x, -hPrism * 0.5f, z);
        }

        Vector3f topPeak = new Vector3f(0.0f, hPrism * 0.5f + hPyramid, 0.0f);
        Vector3f bottomPeak = new Vector3f(0.0f, -hPrism * 0.5f - hPyramid, 0.0f);

        if (filled) {
            for (int i = 0; i < CRYSTAL_SIDES; i++) {
                Vector3f v1 = transformCrystalVertex(bottomVertices[i], crystal, orbitYawRad, targetPos);
                Vector3f v2 = transformCrystalVertex(bottomVertices[(i + 1) % CRYSTAL_SIDES], crystal, orbitYawRad, targetPos);
                Vector3f v3 = transformCrystalVertex(topVertices[(i + 1) % CRYSTAL_SIDES], crystal, orbitYawRad, targetPos);
                Vector3f v4 = transformCrystalVertex(topVertices[i], crystal, orbitYawRad, targetPos);
                addTriangle(mesh, v1, v2, v3, argb);
                addTriangle(mesh, v1, v3, v4, argb);
            }
            for (int i = 0; i < CRYSTAL_SIDES; i++) {
                Vector3f v1 = transformCrystalVertex(topPeak, crystal, orbitYawRad, targetPos);
                Vector3f v2 = transformCrystalVertex(topVertices[i], crystal, orbitYawRad, targetPos);
                Vector3f v3 = transformCrystalVertex(topVertices[(i + 1) % CRYSTAL_SIDES], crystal, orbitYawRad, targetPos);
                addTriangle(mesh, v1, v2, v3, argb);
            }
            for (int i = 0; i < CRYSTAL_SIDES; i++) {
                Vector3f v1 = transformCrystalVertex(bottomPeak, crystal, orbitYawRad, targetPos);
                Vector3f v2 = transformCrystalVertex(bottomVertices[(i + 1) % CRYSTAL_SIDES], crystal, orbitYawRad, targetPos);
                Vector3f v3 = transformCrystalVertex(bottomVertices[i], crystal, orbitYawRad, targetPos);
                addTriangle(mesh, v1, v2, v3, argb);
            }
            return;
        }

        for (int i = 0; i < CRYSTAL_SIDES; i++) {
            Vector3f bottomA = transformCrystalVertex(bottomVertices[i], crystal, orbitYawRad, targetPos);
            Vector3f bottomB = transformCrystalVertex(bottomVertices[(i + 1) % CRYSTAL_SIDES], crystal, orbitYawRad, targetPos);
            Vector3f topA = transformCrystalVertex(topVertices[i], crystal, orbitYawRad, targetPos);
            Vector3f topB = transformCrystalVertex(topVertices[(i + 1) % CRYSTAL_SIDES], crystal, orbitYawRad, targetPos);
            addLine(mesh, bottomA, bottomB, argb);
            addLine(mesh, topA, topB, argb);
            addLine(mesh, bottomA, topA, argb);
            addLine(mesh, transformCrystalVertex(topPeak, crystal, orbitYawRad, targetPos), topA, argb);
            addLine(mesh, transformCrystalVertex(bottomPeak, crystal, orbitYawRad, targetPos), bottomA, argb);
        }
    }

    private static void appendCrystalMaterialGeometry(MeshBuilder mesh,
                                                      Vec3 targetPos,
                                                      CrystalInstance crystal,
                                                      float orbitYawRad,
                                                      int argb,
                                                      float time,
                                                      int crystalIndex,
                                                      float hit,
                                                      float visualScale) {
        if (mesh == null || targetPos == null || crystal == null) return;

        float s = CRYSTAL_BASE_SIZE * crystal.sizeScale * visualScale;
        float hPrism = s * 0.92f;
        float hPyramid = s * 1.72f;
        Vector3f[] top = new Vector3f[CRYSTAL_SIDES];
        Vector3f[] bottom = new Vector3f[CRYSTAL_SIDES];
        for (int i = 0; i < CRYSTAL_SIDES; i++) {
            float angle = (float) (Math.PI * 2.0 * i / CRYSTAL_SIDES);
            float localRadius = s * (0.94f + 0.08f * Mth.sin(angle * 3.0f + crystal.phase * 6.2831855f));
            top[i] = new Vector3f(Mth.cos(angle) * localRadius, hPrism * 0.5f, Mth.sin(angle) * localRadius);
            bottom[i] = new Vector3f(Mth.cos(angle) * localRadius, -hPrism * 0.5f, Mth.sin(angle) * localRadius);
        }
        Vector3f topPeak = new Vector3f(0.0f, hPrism * 0.5f + hPyramid, 0.0f);
        Vector3f bottomPeak = new Vector3f(0.0f, -hPrism * 0.5f - hPyramid * 0.86f, 0.0f);
        int facet = 0;

        for (int i = 0; i < CRYSTAL_SIDES; i++) {
            Vector3f b0 = transformCrystalVertexAt(bottom[i], crystal, orbitYawRad, targetPos, time);
            Vector3f b1 = transformCrystalVertexAt(bottom[(i + 1) % CRYSTAL_SIDES], crystal, orbitYawRad, targetPos, time);
            Vector3f t1 = transformCrystalVertexAt(top[(i + 1) % CRYSTAL_SIDES], crystal, orbitYawRad, targetPos, time);
            Vector3f t0 = transformCrystalVertexAt(top[i], crystal, orbitYawRad, targetPos, time);
            appendCrystalMaterialTriangle(mesh, b0, b1, t1, argb, time, crystalIndex, hit, crystal.phase, facet++);
            appendCrystalMaterialTriangle(mesh, b0, t1, t0, argb, time, crystalIndex, hit, crystal.phase, facet++);
        }
        Vector3f topWorld = transformCrystalVertexAt(topPeak, crystal, orbitYawRad, targetPos, time);
        Vector3f bottomWorld = transformCrystalVertexAt(bottomPeak, crystal, orbitYawRad, targetPos, time);
        for (int i = 0; i < CRYSTAL_SIDES; i++) {
            Vector3f t0 = transformCrystalVertexAt(top[i], crystal, orbitYawRad, targetPos, time);
            Vector3f t1 = transformCrystalVertexAt(top[(i + 1) % CRYSTAL_SIDES], crystal, orbitYawRad, targetPos, time);
            Vector3f b0 = transformCrystalVertexAt(bottom[i], crystal, orbitYawRad, targetPos, time);
            Vector3f b1 = transformCrystalVertexAt(bottom[(i + 1) % CRYSTAL_SIDES], crystal, orbitYawRad, targetPos, time);
            appendCrystalMaterialTriangle(mesh, topWorld, t0, t1, argb, time, crystalIndex, hit, crystal.phase, facet++);
            appendCrystalMaterialTriangle(mesh, bottomWorld, b1, b0, argb, time, crystalIndex, hit, crystal.phase, facet++);
        }
    }

    private static void appendCrystalMaterialTriangle(MeshBuilder mesh,
                                                       Vector3f a,
                                                       Vector3f b,
                                                       Vector3f c,
                                                       int argb,
                                                       float time,
                                                       int crystalIndex,
                                                       float hit,
                                                       float phase,
                                                       int facetIndex) {
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (normal.lengthSquared() <= 1.0e-8f) return;
        normal.normalize();
        float facetPhase = Mth.frac(phase + facetIndex * 0.071f);
        mesh.ensureTriCapacity();
        int ia = addCrystalMaterialVertex(mesh, a, 0.0f, 0.0f, argb, time, crystalIndex, hit, normal, facetPhase);
        int ib = addCrystalMaterialVertex(mesh, b, 1.0f, 0.0f, argb, time, crystalIndex, hit, normal, facetPhase);
        int ic = addCrystalMaterialVertex(mesh, c, 0.5f, 1.0f, argb, time, crystalIndex, hit, normal, facetPhase);
        mesh.triangle(ia, ib, ic);
    }

    private static int addCrystalMaterialVertex(MeshBuilder mesh,
                                                Vector3f position,
                                                float u,
                                                float v,
                                                int argb,
                                                float time,
                                                int crystalIndex,
                                                float hit,
                                                Vector3f normal,
                                                float facetPhase) {
        mesh.vec3(position.x, position.y, position.z)
                .vec2(u, v)
                .colorArgb(argb)
                .vec4(time, crystalIndex, hit, 1.0f)
                .vec4(normal.x, normal.y, normal.z, facetPhase);
        return mesh.next();
    }

    private static Vector3f transformCrystalVertex(Vector3f vertex,
                                                   CrystalInstance crystal,
                                                   float orbitYawRad,
                                                   Vec3 targetPos) {
        Vector3f transformed = new Vector3f(vertex).mul(crystal.pulsation());
        transformed.rotateX((float) Math.toRadians(crystal.rotation.x));
        transformed.rotateY((float) Math.toRadians(crystal.rotation.y + crystal.selfRotationDeg()));
        transformed.rotateZ((float) Math.toRadians(crystal.rotation.z));
        transformed.add((float) crystal.position.x, (float) crystal.position.y, (float) crystal.position.z);
        transformed.rotateY(orbitYawRad);
        transformed.add((float) targetPos.x, (float) targetPos.y, (float) targetPos.z);
        return transformed;
    }

    private static Vector3f transformCrystalVertexAt(Vector3f vertex,
                                                     CrystalInstance crystal,
                                                     float orbitYawRad,
                                                     Vec3 targetPos,
                                                     float renderTime) {
        Vector3f transformed = new Vector3f(vertex).mul(crystal.pulsation(renderTime));
        transformed.rotateX((float) Math.toRadians(crystal.rotation.x));
        transformed.rotateY((float) Math.toRadians(crystal.rotation.y + crystal.selfRotationDeg(renderTime)));
        transformed.rotateZ((float) Math.toRadians(crystal.rotation.z));
        transformed.add((float) crystal.position.x, (float) crystal.position.y, (float) crystal.position.z);
        transformed.rotateY(orbitYawRad);
        transformed.add((float) targetPos.x, (float) targetPos.y, (float) targetPos.z);
        return transformed;
    }

    private static void appendBloomSphere(MeshBuilder mesh,
                                          Vec3 targetPos,
                                          CrystalInstance crystal,
                                          float orbitYawRad,
                                          int argb) {
        if (mesh == null || crystal == null || targetPos == null) {
            return;
        }

        Vector3f center = new Vector3f((float) crystal.position.x, (float) crystal.position.y, (float) crystal.position.z);
        center.rotateY(orbitYawRad);
        center.add((float) targetPos.x, (float) targetPos.y, (float) targetPos.z);

        float size = CRYSTAL_BASE_SIZE * 8.5f;
        Quaternionf camRot = RenderState.cameraRotation;
        float yaw = orbitYawRad;
        int bloomColor = applyOpacity(argb, 0.09f);

        for (int i = 0; i < 6; i++) {
            float angle = (360.0f / 6.0f) * i;
            Quaternionf rotation = new Quaternionf()
                    .rotateY((float) Math.toRadians(angle))
                    .rotateY(yaw)
                    .mul(camRot);
            addTexturedBillboard(mesh, center.x, center.y, center.z, size, rotation, bloomColor);
        }

        for (int i = 0; i < 6; i++) {
            float angle = (360.0f / 6.0f) * i;
            Quaternionf rotation = new Quaternionf()
                    .rotateX((float) Math.toRadians(90.0f))
                    .rotateY((float) Math.toRadians(angle))
                    .rotateY(yaw)
                    .mul(camRot);
            addTexturedBillboard(mesh, center.x, center.y, center.z, size, rotation, bloomColor);
        }
    }

    private static void addTexturedBillboard(MeshBuilder mesh,
                                             double cx, double cy, double cz,
                                             float size, Quaternionf rotation, int argb) {
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(rotation).mul(size * 0.5f);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(rotation).mul(size * 0.5f);
        addBillboardVertices(mesh, cx, cy, cz, right, up, argb, argb, argb, argb);
    }

    private static void addTriangle(MeshBuilder mesh, Vector3f v1, Vector3f v2, Vector3f v3, int argb) {
        mesh.ensureTriCapacity();
        int i1 = addVertex(mesh, v1.x, v1.y, v1.z, argb);
        int i2 = addVertex(mesh, v2.x, v2.y, v2.z, argb);
        int i3 = addVertex(mesh, v3.x, v3.y, v3.z, argb);
        mesh.triangle(i1, i2, i3);
    }

    private static void addLine(MeshBuilder mesh, Vector3f v1, Vector3f v2, int argb) {
        mesh.ensureLineCapacity();
        int i1 = addVertex(mesh, v1.x, v1.y, v1.z, argb);
        int i2 = addVertex(mesh, v2.x, v2.y, v2.z, argb);
        mesh.line(i1, i2);
    }

    private static void addRotatedBillboard(MeshBuilder mesh, double cx, double cy, double cz,
                                            float size, float angle, IntFunction<Integer> palette) {
        Quaternionf camRot = RenderState.cameraRotation;
        Vector3f right = new Vector3f(1, 0, 0).rotate(camRot);
        Vector3f up = new Vector3f(0, 1, 0).rotate(camRot);

        Vector3f r = new Vector3f(right).mul(size);
        Vector3f u = new Vector3f(up).mul(size);

        float sin = Mth.sin((float) Math.toRadians(angle));
        float cos = Mth.cos((float) Math.toRadians(angle));

        Vector3f rRot = new Vector3f(r).mul(cos).fma(sin, u);
        Vector3f uRot = new Vector3f(u).mul(cos).fma(-sin, r);

        addBillboardVertices(mesh, cx, cy, cz, rRot, uRot,
                palette.apply(90),
                palette.apply(0),
                palette.apply(180),
                palette.apply(270));
    }

    private static void addBillboardQuad(MeshBuilder mesh,
                                         double cx, double cy, double cz,
                                         float size, int argb) {
        Quaternionf camRot = RenderState.cameraRotation;
        Vector3f right = new Vector3f(1, 0, 0).rotate(camRot).mul(size);
        Vector3f up = new Vector3f(0, 1, 0).rotate(camRot).mul(size);
        addBillboardVertices(mesh, cx, cy, cz, right, up, argb, argb, argb, argb);
    }

    private static void addBillboardVertices(MeshBuilder mesh, double cx, double cy, double cz,
                                             Vector3f right, Vector3f up,
                                             int c1, int c2, int c3, int c4) {
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
        int i1 = addVertex(mesh, p1x, p1y, p1z, 0, 1, c1);
        int i2 = addVertex(mesh, p2x, p2y, p2z, 1, 1, c2);
        int i3 = addVertex(mesh, p3x, p3y, p3z, 1, 0, c3);
        int i4 = addVertex(mesh, p4x, p4y, p4z, 0, 0, c4);
        mesh.quad(i1, i2, i3, i4);
    }

    private static int shadeArgb(int argb, float rgbScale, float alphaScale) {
        float s = Mth.clamp(rgbScale, 0.0f, 1.0f);
        int a = Mth.clamp(Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alphaScale, 0.0f, 1.0f)), 0, 255);
        int r = Mth.clamp(Math.round(((argb >>> 16) & 0xFF) * s), 0, 255);
        int g = Mth.clamp(Math.round(((argb >>> 8) & 0xFF) * s), 0, 255);
        int b = Mth.clamp(Math.round((argb & 0xFF) * s), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int applyOpacity(int argb, float opacity) {
        opacity = Math.min(1f, Math.max(0f, opacity));
        int baseA = (argb >>> 24) & 0xFF;
        int a = Mth.clamp((int) (baseA * opacity), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int addVertex(MeshBuilder mesh, double x, double y, double z, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        mesh.vec3(x, y, z).color(r, g, b, a);
        return mesh.next();
    }

    private static int addVertex(MeshBuilder mesh, double x, double y, double z, double u, double v, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        mesh.vec3(x, y, z).vec2(u, v).color(r, g, b, a);
        return mesh.next();
    }

    private static double absSinAnimation(double input) {
        return Math.abs(1 + Math.sin(input)) / 2;
    }

    private static double interpolate(double oldValue, double newValue, double interpolationValue) {
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }

    private static float interpolateFloat(float oldValue, float newValue, double interpolationValue) {
        return (float) interpolate(oldValue, newValue, interpolationValue);
    }

    @Override
    public void onDisable() {
        target = null;
        renderTarget = null;
        targetAlpha = 0.0f;
        crystalList.clear();
        lastCrystalTarget = null;
        WorldTargetLenses.clear();
        WorldOrbitGravity.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null) {
            target = null;
            updateRenderTarget(null);
            crystalList.clear();
            lastCrystalTarget = null;
            return;
        }
        target = TargetingUtil.resolveManagedTarget(crosshairTargets.get(), playersOnly.get());
        updateRenderTarget(target);
        if (renderTarget == null) {
            crystalList.clear();
            lastCrystalTarget = null;
        }

        setCaptureSpeedScale(isCaptureMarkMode() ? captureSpinSpeed.get() : 1.0f);

        if (renderTarget != null && targetAlpha > 0.0f) {
            tickTargetEsp();
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    @Override
    public void onPrepareWorldPostProcess(float tickDelta) {
        if (!isEnabled() || renderTarget == null || targetAlpha <= 0.01f) return;
        Renderer3D.CullOptions cull = resolveCullOptions(renderTarget);
        if (!Renderer3D.Culling.shouldRender(renderTarget, tickDelta, cull)) return;

        String mode = espMode.get();
        if ("capture_mark".equals(mode) && captureRefraction.get()) {
            prepareCaptureOptics(tickDelta);
        } else if ("crystals".equals(mode) && distortion.get()) {
            prepareCrystalPrism(tickDelta);
        } else if ("orbit".equals(mode)) {
            prepareOrbitGravity(tickDelta);
        }
    }

    private void prepareCaptureOptics(float tickDelta) {
        float strength = captureRefractionStrength.get() * targetAlpha;
        if (strength <= 0.0005f || renderTarget == null) return;

        Vec3 base = RenderMath.getLerpedPos(renderTarget, tickDelta);
        Vec3 center = base.add(0.0, renderTarget.getEyeHeight(renderTarget.getPose()) / 2.0, 0.0);
        float time = (renderTarget.tickCount - 1 + tickDelta) / 20.0f;
        float pulse = capturePulse.get()
                ? 0.5f + 0.5f * Mth.sin(time * Math.max(0.05f, capturePulseSpeed.get()) * 3.64f)
                : 0.0f;
        float size = 0.75f * (capturePulse.get() ? 0.985f + pulse * 0.030f : 1.0f);
        float angleDeg = interpolateFloat((float) prevCaptureValue, (float) captureValue, tickDelta);
        float roll = (float) Math.toRadians(angleDeg);
        float opacity = Mth.clamp(0.54f + strength * 2.2f, 0.54f, 0.92f);

        WorldPostProcessMasks.distortionBillboard(
                CAPTURE_MARK_TEXTURE,
                center,
                size * 1.02f,
                roll,
                Mth.clamp(strength * 2.35f, 0.0f, 0.52f),
                0.10f,
                opacity,
                false
        );
    }

    private void prepareCrystalPrism(float tickDelta) {
        float strength = distortionStrength.get() * targetAlpha;
        if (strength <= 0.001f) return;
        if (crystalLayoutChanged(renderTarget)) {
            createCrystals(renderTarget);
            lastCrystalTarget = renderTarget;
        }
        if (crystalList.isEmpty()) return;

        float renderTime = (renderTarget.tickCount - 1 + tickDelta) / 20.0f;
        Vec3 center = obtainEntityLerpedPos(renderTarget, tickDelta);
        float orbitYawRad = (float) Math.toRadians(((renderTarget.tickCount - 1 + tickDelta) * 2.5f) % 360.0f);
        float red = crystalHitRed.get()
                ? Mth.clamp((renderTarget.hurtTime - tickDelta) / 20.0f, 0.0f, 1.0f)
                : 0.0f;

        for (int i = 0; i < crystalList.size(); i++) {
            CrystalInstance crystal = crystalList.get(i);
            Vec3 crystalCenter = crystal.worldCenter(center, orbitYawRad);
            Vec3 crystalAxis = crystal.worldAxis(center, orbitYawRad, renderTime);
            int tint = getCrystalColor(i * 8, red);
            float visualScale = crystalSize.get();
            float radius = CRYSTAL_BASE_SIZE * crystal.sizeScale * visualScale * 1.62f * distortionRadius.get();
            float axialLength = CRYSTAL_BASE_SIZE * crystal.sizeScale * visualScale * 3.55f * distortionLength.get();
            float lensStrength = strength * (1.72f + 0.44f * crystal.pulsation(renderTime));
            WorldTargetLenses.submit(
                    crystalCenter,
                    crystalAxis,
                    radius,
                    axialLength,
                    lensStrength,
                    Renderer3D.DepthMode.ENTITY_ONLY,
                    tint,
                    crystal.phase
            );
        }
    }


    private void prepareOrbitGravity(float tickDelta) {
        float time = (renderTarget.tickCount - 1 + tickDelta) / 20.0f;
        Vec3 center = RenderMath.getLerpedPos(renderTarget, tickDelta)
                .add(0.0, renderTarget.getBbHeight() * 0.52, 0.0);
        float horizon = Math.max(0.24f,
                Math.max(renderTarget.getBbWidth() * 0.46f, renderTarget.getBbHeight() * 0.16f))
                * Math.max(0.55f, orbitScale.get());
        int tint = AnimatedRenderColors.scaleAlpha(getColorArgb(42), targetAlpha);

        WorldOrbitGravity.submit(
                center,
                horizon,
                horizon * 2.85f,
                Mth.clamp(targetAlpha * (1.08f + orbitSpeed.get() * 0.05f), 0.0f, 1.25f),
                time * Math.max(0.15f, orbitSpeed.get()),
                tint,
                false
        );
    }


    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderTarget == null || targetAlpha <= 0.001f) return;
        Renderer3D.CullOptions cull = resolveCullOptions(renderTarget);
        if (!Renderer3D.Culling.shouldRender(renderTarget, tickDelta, cull)) return;

        IntFunction<Integer> palette = index -> AnimatedRenderColors.scaleAlpha(getColorArgb(index), targetAlpha);

        String mode = espMode.get();
        if ("ring".equals(mode)) {
            renderTargetRing(renderer, renderTarget, tickDelta, palette);
        } else if ("ghosts".equals(mode)) {
            renderClassicGhosts(renderer, renderTarget, tickDelta, palette);
        } else if ("caustics".equals(mode)) {
            renderCaustics(
                    renderer,
                    espLength.get(),
                    espFactor.get(),
                    espAmplitude.get(),
                    renderTarget,
                    tickDelta,
                    palette
            );
        } else if ("capture_mark".equals(mode)) {
            renderCaptureMark(renderer, renderTarget, tickDelta, palette);
        } else if ("crystals".equals(mode)) {
            renderCrystals(renderer, renderTarget, tickDelta);
        } else if ("orbit".equals(mode)) {
            renderOrbitRibbons(renderer, renderTarget, tickDelta, palette);
        }
    }

    private void updateRenderTarget(LivingEntity next) {
        float dt = Math.max(AnimationUtility.deltaTime(), ALPHA_TICK_DT);
        float speed = alphaAnimationSpeed.get();
        if (next != null) {
            if (renderTarget == null) {
                renderTarget = next;
                targetAlpha = AnimationUtility.snap(
                        AnimationUtility.approach(targetAlpha, 1.0f, dt, speed),
                        1.0f,
                        ALPHA_SNAP_EPSILON
                );
                return;
            }
            if (renderTarget != next) {
                renderTarget = next;
                targetAlpha = 1.0f;
                crystalList.clear();
                lastCrystalTarget = null;
                return;
            }
            targetAlpha = AnimationUtility.snap(
                    AnimationUtility.approach(targetAlpha, 1.0f, dt, speed),
                    1.0f,
                    ALPHA_SNAP_EPSILON
            );
            return;
        }

        targetAlpha = AnimationUtility.snap(
                AnimationUtility.approach(targetAlpha, 0.0f, dt, speed),
                0.0f,
                ALPHA_SNAP_EPSILON
        );
        if (targetAlpha <= 0.0f) {
            renderTarget = null;
            crystalList.clear();
            lastCrystalTarget = null;
        }
    }

    private Renderer3D.CullOptions resolveCullOptions(LivingEntity target) {
        if (target == null || mc.player == null) {
            return Renderer3D.CullOptions.SMART_TARGET;
        }
        // Allow rendering through walls if the current target is already selected,
        // so raycast-based targeting doesn't get culled by line-of-sight checks.
        if (!mc.player.hasLineOfSight(target)) {
            return Renderer3D.CullOptions.FRUSTUM_LOOK;
        }
        return Renderer3D.CullOptions.SMART_TARGET;
    }

    private Vec3 obtainEntityLerpedPos(Entity e, float f) {
        try {
            return e.getPosition(f);
        } catch (NoSuchMethodError ex) {
            if (e instanceof IEntity access) {
                Vec3 last = access.get$InstantRenderPos();
                return last.lerp(e.position(), f);
            }
            return e.position();
        }
    }






    private void renderOrbitRibbons(Renderer3D renderer,
                                    LivingEntity target,
                                    float tickDelta,
                                    IntFunction<Integer> palette) {
        if (renderer == null || target == null || palette == null) return;

        int segments = Mth.clamp(88 + orbitDensity.get() / 2, 104, 192);
        float time = (target.tickCount - 1 + tickDelta) / 20.0f;
        float speed = Math.max(0.05f, orbitSpeed.get());
        float scale = Math.max(0.55f, orbitScale.get());
        float horizon = Math.max(0.24f, Math.max(target.getBbWidth() * 0.46f, target.getBbHeight() * 0.16f)) * scale;
        float diskOuter = horizon * 3.05f;
        float diskInner = horizon * 1.46f;
        Vec3 center = RenderMath.getLerpedPos(target, tickDelta).add(0.0, target.getBbHeight() * 0.52, 0.0);

        MeshBuilder disk = renderer.batch(
                CombatantRenderPipelines.WORLD_TARGET_ORBIT_DEPTH,
                Renderer3D.DepthMode.ENTITY_ONLY
        );
        if (disk == null) return;

        Quaternionf diskRotation = new Quaternionf()
                .rotateY(time * speed * 0.16f)
                .rotateX((float) Math.toRadians(18.0))
                .rotateZ((float) Math.toRadians(-8.0));
        Vector3f normal = new Vector3f(0.0f, 1.0f, 0.0f).rotate(diskRotation).normalize();
        appendAccretionDisk(disk, center, diskRotation, diskInner, diskOuter, segments, time, speed, 0, normal, palette, 0.92f);

    }

    private static void appendAccretionDisk(MeshBuilder mesh,
                                            Vec3 center,
                                            Quaternionf rotation,
                                            float innerRadius,
                                            float outerRadius,
                                            int segments,
                                            float time,
                                            float speed,
                                            int track,
                                            Vector3f normal,
                                            IntFunction<Integer> palette,
                                            float alphaScale) {
        for (int i = 0; i < segments; i++) {
            float u0 = i / (float) segments;
            float u1 = (i + 1) / (float) segments;
            float a0 = u0 * Mth.TWO_PI;
            float a1 = u1 * Mth.TWO_PI;

            Vector3f p0Inner = orbitDiskPoint(rotation, innerRadius, a0, center);
            Vector3f p0Outer = orbitDiskPoint(rotation, outerRadius, a0, center);
            Vector3f p1Inner = orbitDiskPoint(rotation, innerRadius, a1, center);
            Vector3f p1Outer = orbitDiskPoint(rotation, outerRadius, a1, center);

            int phase0 = Math.round(u0 * 360.0f);
            int phase1 = Math.round(u1 * 360.0f);
            int color0 = applyOpacity(palette.apply(phase0), alphaScale);
            int color1 = applyOpacity(palette.apply(phase1), alphaScale);

            mesh.ensureQuadCapacity();
            int v0 = addOrbitVertex(mesh, p0Inner, u0, 0.0f, color0, time, track, speed, normal);
            int v1 = addOrbitVertex(mesh, p1Inner, u1, 0.0f, color1, time, track, speed, normal);
            int v2 = addOrbitVertex(mesh, p1Outer, u1, 1.0f, color1, time, track, speed, normal);
            int v3 = addOrbitVertex(mesh, p0Outer, u0, 1.0f, color0, time, track, speed, normal);
            mesh.quad(v0, v1, v2, v3);
        }
    }


    private static Vector3f orbitDiskPoint(Quaternionf rotation, float radius, float angle, Vec3 center) {
        Vector3f point = new Vector3f(Mth.cos(angle) * radius, 0.0f, Mth.sin(angle) * radius).rotate(rotation);
        point.add((float) center.x, (float) center.y, (float) center.z);
        return point;
    }

    private static void appendUvSphere(MeshBuilder mesh, Vec3 center, float radius, int lonSegments, int latSegments, int argb) {
        for (int lat = 0; lat < latSegments; lat++) {
            float v0 = lat / (float) latSegments;
            float v1 = (lat + 1) / (float) latSegments;
            float phi0 = (v0 - 0.5f) * (float) Math.PI;
            float phi1 = (v1 - 0.5f) * (float) Math.PI;
            float c0 = Mth.cos(phi0);
            float c1 = Mth.cos(phi1);
            float s0 = Mth.sin(phi0);
            float s1 = Mth.sin(phi1);
            for (int lon = 0; lon < lonSegments; lon++) {
                float u0 = lon / (float) lonSegments;
                float u1 = (lon + 1) / (float) lonSegments;
                float a0 = u0 * Mth.TWO_PI;
                float a1 = u1 * Mth.TWO_PI;
                Vector3f p00 = new Vector3f(
                        (float) center.x + Mth.cos(a0) * c0 * radius,
                        (float) center.y + s0 * radius,
                        (float) center.z + Mth.sin(a0) * c0 * radius
                );
                Vector3f p10 = new Vector3f(
                        (float) center.x + Mth.cos(a1) * c0 * radius,
                        (float) center.y + s0 * radius,
                        (float) center.z + Mth.sin(a1) * c0 * radius
                );
                Vector3f p11 = new Vector3f(
                        (float) center.x + Mth.cos(a1) * c1 * radius,
                        (float) center.y + s1 * radius,
                        (float) center.z + Mth.sin(a1) * c1 * radius
                );
                Vector3f p01 = new Vector3f(
                        (float) center.x + Mth.cos(a0) * c1 * radius,
                        (float) center.y + s1 * radius,
                        (float) center.z + Mth.sin(a0) * c1 * radius
                );
                mesh.ensureQuadCapacity();
                int q0 = addVertex(mesh, p00.x, p00.y, p00.z, argb);
                int q1 = addVertex(mesh, p10.x, p10.y, p10.z, argb);
                int q2 = addVertex(mesh, p11.x, p11.y, p11.z, argb);
                int q3 = addVertex(mesh, p01.x, p01.y, p01.z, argb);
                mesh.quad(q0, q1, q2, q3);
            }
        }
    }

    private static int addOrbitVertex(MeshBuilder mesh,
                                      Vector3f position,
                                      float u,
                                      float v,
                                      int argb,
                                      float time,
                                      int track,
                                      float speed,
                                      Vector3f normal) {
        mesh.vec3(position.x, position.y, position.z)
                .vec2(u, v)
                .colorArgb(argb)
                .vec4(time, track, speed, 1.0f)
                .vec4(normal.x, normal.y, normal.z, track);
        return mesh.next();
    }

























    private void renderCrystals(Renderer3D renderer, LivingEntity target, float tickDelta) {
        if (renderer == null || target == null) return;
        if (crystalLayoutChanged(target)) {
            createCrystals(target);
            lastCrystalTarget = target;
        }
        if (crystalList.isEmpty()) return;

        crystalRotationAngle = ((target.tickCount - 1 + tickDelta) * 2.5f) % 360.0f;
        float orbitYawRad = (float) Math.toRadians(crystalRotationAngle);
        float red = crystalHitRed.get()
                ? Mth.clamp((target.hurtTime - tickDelta) / 20.0f, 0.0f, 1.0f)
                : 0.0f;
        float time = (target.tickCount - 1 + tickDelta) / 20.0f;

        Renderer3D.DepthMode depthMode = Renderer3D.DepthMode.ENTITY_ONLY;
        MeshBuilder crystalMesh = renderer.batch(
                CombatantRenderPipelines.WORLD_TARGET_CRYSTAL_DEPTH,
                depthMode
        );
        if (crystalMesh == null) return;

        Vec3 targetPos = obtainEntityLerpedPos(target, tickDelta);
        for (int i = 0; i < crystalList.size(); i++) {
            CrystalInstance crystal = crystalList.get(i);
            int baseColor = getCrystalColor(i * 8, red);
            appendCrystalMaterialGeometry(crystalMesh, targetPos, crystal, orbitYawRad, baseColor, time, i, red,
                    crystalSize.get());
        }
    }

    private int getCrystalColor(int index, float redFactor) {
        int baseArgb = getColorArgb(index);
        int out;
        if (redFactor <= 0.0f) {
            out = baseArgb;
        } else {
            int redArgb = (baseArgb & 0xFF000000) | 0x00FF0000;
            out = AnimatedRenderColors.mixArgb(baseArgb, redArgb, redFactor);
        }
        return AnimatedRenderColors.scaleAlpha(out, targetAlpha);
    }

    private void createCrystals(Entity target) {
        crystalList.clear();
        if (target == null) return;
        int count = crystalCount.get();
        float radiusScale = crystalOrbitRadius.get();
        double radius = Math.max(target.getBbWidth() * 1.48, target.getBbWidth() + 0.30) * radiusScale;
        double height = target.getBbHeight();
        crystalLayoutCount = count;
        crystalLayoutRadius = radiusScale;
        for (int i = 0; i < count; i++) {
            double angle = i * (360.0 / count);
            double radians = Math.toRadians(angle);
            double radialScale = 0.92 + 0.16 * Math.sin(radians * 3.0 + 0.6);
            double x = Math.sin(radians) * radius * radialScale;
            double z = Math.cos(radians) * radius * radialScale;
            double yBand = 0.5 + 0.5 * Math.sin(radians * 2.0 + 0.55);
            double y = 0.20 + height * (0.12 + 0.64 * yBand);
            double tilt = 46.0 + 24.0 * Math.sin(radians * 2.0 + 0.8);
            float sizeScale = 1.18f + 0.34f * (float) (0.5 + 0.5 * Math.sin(radians * 3.0 + 0.4));
            crystalList.add(new CrystalInstance(
                    new Vec3(x, y, z),
                    new Vec3(tilt, angle + 90.0, angle * 0.31),
                    sizeScale,
                    i / (float) count
            ));
        }
    }

    private boolean crystalLayoutChanged(Entity target) {
        return crystalList.isEmpty()
                || target != lastCrystalTarget
                || crystalLayoutCount != crystalCount.get()
                || !Float.isFinite(crystalLayoutRadius)
                || Math.abs(crystalLayoutRadius - crystalOrbitRadius.get()) > 1.0e-4f;
    }

    private boolean isGhostMode() {
        return "ghosts".equals(espMode.get());
    }

    private boolean isGhostOrCausticsMode() {
        return isGhostMode() || "caustics".equals(espMode.get());
    }

    private boolean isCaptureMarkMode() {
        return "capture_mark".equals(espMode.get());
    }

    private boolean isRingMode() {
        return "ring".equals(espMode.get());
    }

    private boolean isCrystalsMode() {
        return "crystals".equals(espMode.get());
    }

    @Override
    public boolean needsEntityOnlyDepth() {
        return isEnabled() && (isCrystalsMode() || isGhostOrCausticsMode()
                || isOrbitMode() || isRingMode());
    }

    private boolean isOrbitMode() {
        return "orbit".equals(espMode.get());
    }



    private boolean usesSecondaryColor() {
        return AnimatedRenderColors.usesSecondary(animatedColorMode());
    }

    private int getColorArgb(int count) {
        int phase = colorPhase.get() + Math.round(count * colorSpread.get() / 90.0f);
        int animated = AnimatedRenderColors.resolve(
                animatedColorMode(),
                colorSpeed.get(),
                phase,
                colorValue.getArgb(),
                colorValue2.getArgb(),
                true
        );
        if (!useRelationColors.get() || renderTarget == null) {
            return animated;
        }

        int relation = relationColor(renderTarget, animated);
        relation = AnimatedRenderColors.withAlpha(relation, (animated >>> 24) & 0xFF);
        return AnimatedRenderColors.mixArgb(animated, relation, relationColorMix.get());
    }

    private int relationColor(LivingEntity entity, int fallback) {
        if (entity instanceof Player player) {
            return CategoryService.getColor(player);
        }
        String name = entity.getName() != null ? entity.getName().getString() : null;
        if (name == null || name.isBlank()) {
            return fallback;
        }
        return CategoryService.getColor(name);
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

    private static final class CrystalInstance {
        private final Vec3 position;
        private final Vec3 rotation;
        private final float rotationSpeed;
        private final float phase;
        private final float sizeScale;

        private CrystalInstance(Vec3 position, Vec3 rotation, float sizeScale, float phase) {
            this.position = position;
            this.rotation = rotation;
            this.sizeScale = sizeScale;
            this.phase = phase;
            this.rotationSpeed = 0.62f + phase * 0.78f;
        }

        private float selfRotationDeg() {
            return (System.currentTimeMillis() % 36000L) / 100.0f * rotationSpeed;
        }

        private float selfRotationDeg(float renderTime) {
            return renderTime * 36.0f * rotationSpeed;
        }

        private float pulsation() {
            return 1.0f + (float) (Math.sin(System.currentTimeMillis() / 500.0 + phase) * 0.1f);
        }

        private float pulsation(float renderTime) {
            return 1.0f + Mth.sin(renderTime * 2.0f + phase * Mth.TWO_PI) * 0.075f;
        }

        private Vec3 worldCenter(Vec3 targetPos, float orbitYawRad) {
            Vector3f center = new Vector3f((float) position.x, (float) position.y, (float) position.z);
            center.rotateY(orbitYawRad);
            center.add((float) targetPos.x, (float) targetPos.y, (float) targetPos.z);
            return new Vec3(center.x, center.y, center.z);
        }

        private Vec3 worldAxis(Vec3 targetPos, float orbitYawRad, float renderTime) {
            Vector3f bottom = transformCrystalVertexAt(new Vector3f(0.0f, -CRYSTAL_BASE_SIZE * sizeScale, 0.0f), this, orbitYawRad, targetPos, renderTime);
            Vector3f top = transformCrystalVertexAt(new Vector3f(0.0f, CRYSTAL_BASE_SIZE * sizeScale, 0.0f), this, orbitYawRad, targetPos, renderTime);
            Vector3f axis = top.sub(bottom);
            if (axis.lengthSquared() <= 1.0e-8f) axis.set(0.0f, 1.0f, 0.0f);
            else axis.normalize();
            return new Vec3(axis.x, axis.y, axis.z);
        }

        private void append(MeshBuilder additiveMesh,
                            MeshBuilder fillMesh,
                            MeshBuilder lineMesh,
                            MeshBuilder bloomMesh,
                            Vec3 targetPos,
                            float orbitYawRad,
                            int baseColor) {
            appendCrystalGeometry(additiveMesh, targetPos, this, orbitYawRad, 0.94f, applyOpacity(baseColor, 0.22f), true);
            appendCrystalGeometry(fillMesh, targetPos, this, orbitYawRad, 1.0f, applyOpacity(baseColor, 0.34f), true);
            appendCrystalGeometry(lineMesh, targetPos, this, orbitYawRad, 1.0f, applyOpacity(baseColor, 0.88f), false);
            appendCrystalGeometry(additiveMesh, targetPos, this, orbitYawRad, 1.16f, applyOpacity(baseColor, 0.18f), true);
            appendCrystalGeometry(lineMesh, targetPos, this, orbitYawRad, 1.24f, applyOpacity(baseColor, 0.34f), false);
            appendBloomSphere(bloomMesh, targetPos, this, orbitYawRad, baseColor);
        }
    }

}
