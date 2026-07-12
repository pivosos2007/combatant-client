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
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.util.target.TargetManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

//todo Description
@ModuleInfo(id = "targetesp", displayName = "TargetESP", aliases = {"target", "targetrender"}, category = ModuleCategory.VISUALS)
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
    private static final String SETTING_CROSSHAIR_TARGETS = "crosshair_targets";
    private static final String SETTING_ALPHA_ANIMATION_SPEED = "alpha_animation_speed";
    private static final String SETTING_ESP_LENGTH = "esp_length";
    private static final String SETTING_ESP_FACTOR = "esp_factor";
    private static final String SETTING_ESP_SHAKING = "esp_shaking";
    private static final String SETTING_ESP_AMPLITUDE = "esp_amplitude";
    private static final String SETTING_CAPTURE_SPIN_SPEED = "capture_spin_speed";
    private static final String SETTING_CRYSTAL_HIT_RED = "crystal_hit_red";
    private static final Identifier GHOST_PARTICLE_TEXTURE = TextureStorage.FIRE_FLY;
    private static final Identifier CAPTURE_MARK_TEXTURE = TextureStorage.CAPTURE;
    private static final Identifier CRYSTAL_BLOOM = TextureStorage.BLOOM;
    private static final float CRYSTAL_BASE_SIZE = 0.05f;
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
            "ghosts", "ring", "capture_mark", "crystals"
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
    private final BooleanValue crosshairTargets =
            bool("targetEspCrosshairTargets", SETTING_CROSSHAIR_TARGETS, true);
    private final NumberValue<Float> alphaAnimationSpeed =
            num("targetEspAlphaAnimationSpeed", SETTING_ALPHA_ANIMATION_SPEED, 8.0f, 2.0f, 10.0f);
    private final NumberValue<Integer> espLength =
            visibleWhen(num("targetEspLength", SETTING_ESP_LENGTH, 14, 1, 40), this::isGhostMode);
    private final NumberValue<Integer> espFactor =
            visibleWhen(num("targetEspFactor", SETTING_ESP_FACTOR, 8, 1, 20), this::isGhostMode);
    private final NumberValue<Float> espShaking =
            visibleWhen(num("targetEspShaking", SETTING_ESP_SHAKING, 1.8f, 1.5f, 10f), this::isGhostMode);
    private final NumberValue<Float> espAmplitude =
            visibleWhen(num("targetEspAmplitude", SETTING_ESP_AMPLITUDE, 3f, 0.1f, 8f), this::isGhostMode);
    private final NumberValue<Float> captureSpinSpeed =
            visibleWhen(num("targetEspCaptureSpinSpeed", SETTING_CAPTURE_SPIN_SPEED, 1.0f, 0.2f, 4.0f), this::isCaptureMarkMode);
    private final BooleanValue crystalHitRed =
            visibleWhen(bool("targetEspCrystalHitRed", SETTING_CRYSTAL_HIT_RED, true), this::isCrystalsMode);
    private final List<CrystalInstance> crystalList = new ArrayList<>();
    private LivingEntity target;
    private LivingEntity renderTarget;
    private float targetAlpha;
    private Entity lastCrystalTarget;
    private float crystalRotationAngle;

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

    private static void drawOldTargetEsp(Renderer3D renderer, Entity target, float tickDelta, IntFunction<Integer> palette) {
        if (renderer == null || target == null) return;
        double cs = prevCircleStep + (circleStep - prevCircleStep) * tickDelta;
        double prevSinAnim = absSinAnimation(cs - 0.45f);
        double sinAnim = absSinAnimation(cs);
        Vec3 pos = RenderMath.getLerpedPos(target, tickDelta);
        double x = pos.x;
        double y = pos.y + prevSinAnim * target.getBbHeight();
        double z = pos.z;
        double nextY = pos.y + sinAnim * target.getBbHeight();

        MeshBuilder mesh = renderer.tris();

        final int segments = 96;
        final double step = Math.PI * 2.0 / segments;
        final double radius = target.getBbWidth() * 0.8;

        for (int i = 0; i < segments; i++) {
            double a0 = i * step;
            double a1 = (i + 1) * step;
            float cos = (float) (x + Math.cos(a0) * radius);
            float sin = (float) (z + Math.sin(a0) * radius);
            float cosNext = (float) (x + Math.cos(a1) * radius);
            float sinNext = (float) (z + Math.sin(a1) * radius);

            int idx0 = (int) Math.round(i * (360.0 / segments));
            int idx1 = (int) Math.round((i + 1) * (360.0 / segments));
            int cTop0 = applyOpacity(palette.apply(idx0), 170.0f / 255.0f);
            int cTop1 = applyOpacity(palette.apply(idx1), 170.0f / 255.0f);
            int cBottom0 = applyOpacity(palette.apply(idx0), 0.0f);
            int cBottom1 = applyOpacity(palette.apply(idx1), 0.0f);

            mesh.ensureQuadCapacity();
            int i1 = addVertex(mesh, cos, nextY, sin, cTop0);
            int i2 = addVertex(mesh, cosNext, nextY, sinNext, cTop1);
            int i3 = addVertex(mesh, cosNext, y, sinNext, cBottom1);
            int i4 = addVertex(mesh, cos, y, sin, cBottom0);
            mesh.quad(i1, i2, i3, i4);
        }

    }

    private static void renderGhosts(Renderer3D renderer,
                                     int espLength, int factor, float shaking, float amplitude,
                                     Entity target, float tickDelta, IntFunction<Integer> palette) {
        Minecraft mc = Minecraft.getInstance();
        if (renderer == null || mc == null || mc.player == null) return;

        Vec3 pos = RenderMath.getLerpedPos(target, tickDelta);
        double tPosX = pos.x;
        double tPosY = pos.y;
        double tPosZ = pos.z;
        float iAge = (float) interpolate(target.tickCount - 1, target.tickCount, tickDelta);

        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE,
                GHOST_PARTICLE_TEXTURE,
                Renderer3D.DepthMode.MAIN
        );
        if (mesh == null) return;

        for (int j = 0; j < 3; j++) {
            for (int i = 0; i <= espLength; i++) {
                double radians = Math.toRadians((((float) i / 1.5f + iAge) * factor + (j * 120)) % (factor * 360));
                double sinQuad = Math.sin(Math.toRadians(iAge * 2.5f + i * (j + 1)) * amplitude) / shaking;

                float offset = ((float) i / espLength);
                int color = applyOpacity(palette.apply((int) (180 * offset)), offset);
                float scale = Math.max(0.24f * (offset), 0.2f);

                double cx = tPosX + Math.cos(radians) * target.getBbWidth();
                double cy = tPosY + 1 + sinQuad;
                double cz = tPosZ + Math.sin(radians) * target.getBbWidth();

                addBillboardQuad(mesh, cx, cy, cz, scale, color);
            }
        }
    }

    private static void renderCaptureMark(Renderer3D renderer,
                                          Entity target,
                                          float tickDelta,
                                          IntFunction<Integer> palette) {
        if (renderer == null) return;
        Vec3 pos = RenderMath.getLerpedPos(target, tickDelta);
        double tPosX = pos.x;
        double tPosY = pos.y;
        double tPosZ = pos.z;

        MeshBuilder mesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE,
                CAPTURE_MARK_TEXTURE,
                Renderer3D.DepthMode.MAIN
        );
        if (mesh == null) return;

        float angle = interpolateFloat((float) prevCaptureValue, (float) captureValue, tickDelta);
        float size = 0.75f;

        double cy = tPosY + target.getEyeHeight(target.getPose()) / 2f;

        addRotatedBillboard(mesh, tPosX, cy, tPosZ, size, angle, palette);
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

        float size = CRYSTAL_BASE_SIZE * 13.0f;
        Quaternionf camRot = RenderState.cameraRotation;
        float yaw = orbitYawRad;
        int bloomColor = applyOpacity(argb, (0.4f * 25.0f) / 255.0f);

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

        Vector3f rRot = new Vector3f(
                r.x() * cos - u.x() * sin,
                r.y() * cos - u.y() * sin,
                r.z() * cos - u.z() * sin
        );
        Vector3f uRot = new Vector3f(
                r.x() * sin + u.x() * cos,
                r.y() * sin + u.y() * cos,
                r.z() * sin + u.z() * cos
        );

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
        target = TargetManager.getTarget(crosshairTargets.get());
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
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderTarget == null || targetAlpha <= 0.001f) return;
        Renderer3D.CullOptions cull = resolveCullOptions(renderTarget);
        if (!Renderer3D.Culling.shouldRender(renderTarget, tickDelta, cull)) return;

        IntFunction<Integer> palette = index -> AnimatedRenderColors.scaleAlpha(getColorArgb(index), targetAlpha);

        String mode = espMode.get();
        if ("ring".equals(mode)) {
            drawOldTargetEsp(renderer, renderTarget, tickDelta, palette);
        } else if ("ghosts".equals(mode)) {
            renderGhosts(
                    renderer,
                    espLength.get(),
                    espFactor.get(),
                    espShaking.get(),
                    espAmplitude.get(),
                    renderTarget,
                    tickDelta,
                    palette
            );
        } else if ("capture_mark".equals(mode)) {
            renderCaptureMark(renderer, renderTarget, tickDelta, palette);
        } else if ("crystals".equals(mode)) {
            renderCrystals(renderer, renderTarget, tickDelta);
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

    private void renderCrystals(Renderer3D renderer, LivingEntity target, float tickDelta) {
        if (renderer == null || target == null) {
            return;
        }
        if (crystalList.isEmpty() || target != lastCrystalTarget) {
            createCrystals(target);
            lastCrystalTarget = target;
        }
        if (crystalList.isEmpty()) {
            return;
        }

        crystalRotationAngle = (crystalRotationAngle + 0.5f) % 360.0f;
        float orbitYawRad = (float) Math.toRadians(crystalRotationAngle);
        float red = crystalHitRed.get()
                ? Mth.clamp((target.hurtTime - tickDelta) / 20.0f, 0.0f, 1.0f)
                : 0.0f;

        Renderer3D.DepthMode depthMode = Renderer3D.DepthMode.PRE_DEPTH;
        MeshBuilder additiveMesh = renderer.batch(
                CombatantRenderPipelines.WORLD_COLORED_ADDITIVE_DEPTH,
                depthMode
        );
        MeshBuilder fillMesh = renderer.batch(
                CombatantRenderPipelines.WORLD_COLORED_DEPTH,
                depthMode
        );
        MeshBuilder lineMesh = renderer.batch(
                CombatantRenderPipelines.WORLD_COLORED_LINES_DEPTH,
                depthMode
        );
        MeshBuilder bloomMesh = renderer.batchTextured(
                CombatantRenderPipelines.WORLD_TEXTURED_ADDITIVE_DEPTH,
                CRYSTAL_BLOOM,
                depthMode
        );
        if (additiveMesh == null || fillMesh == null || lineMesh == null || bloomMesh == null) {
            return;
        }

        float prevWidth = RenderState.lineWidth;
        RenderState.lineWidth = 1.0f;
        try {
            Vec3 targetPos = obtainEntityLerpedPos(target, tickDelta);
            for (int i = 0; i < crystalList.size(); i++) {
                CrystalInstance crystal = crystalList.get(i);
                int baseColor = getCrystalColor(i, red);
                crystal.append(additiveMesh, fillMesh, lineMesh, bloomMesh, targetPos, orbitYawRad, baseColor);
            }
        } finally {
            RenderState.lineWidth = prevWidth;
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
        if (target == null) {
            return;
        }
        crystalList.add(new CrystalInstance(new Vec3(0, 0.85, 0.8), new Vec3(-49, 0, 40)));
        crystalList.add(new CrystalInstance(new Vec3(0.2, 0.85, -0.675), new Vec3(35, 0, -30)));
        crystalList.add(new CrystalInstance(new Vec3(0.6, 1.35, 0.6), new Vec3(-30, 0, 35)));
        crystalList.add(new CrystalInstance(new Vec3(-0.74, 1.05, 0.4), new Vec3(-25, 0, -30)));
        crystalList.add(new CrystalInstance(new Vec3(0.74, 0.95, -0.4), new Vec3(0, 0, 0)));
        crystalList.add(new CrystalInstance(new Vec3(-0.475, 0.85, -0.375), new Vec3(30, 0, -25)));
        crystalList.add(new CrystalInstance(new Vec3(0, 1.35, -0.6), new Vec3(45, 0, 0)));
        crystalList.add(new CrystalInstance(new Vec3(0.85, 0.7, 0.1), new Vec3(-30, 0, 30)));
        crystalList.add(new CrystalInstance(new Vec3(-0.7, 1.35, -0.3), new Vec3(0, 0, 0)));
        crystalList.add(new CrystalInstance(new Vec3(-0.3, 1.35, 0.55), new Vec3(0, 0, 0)));
        crystalList.add(new CrystalInstance(new Vec3(-0.5, 0.7, 0.7), new Vec3(0, 0, 0)));
        crystalList.add(new CrystalInstance(new Vec3(0.5, 0.7, 0.7), new Vec3(0, 0, 0)));
        crystalList.add(new CrystalInstance(new Vec3(-0.7, 0.75, 0), new Vec3(0, 0, 0)));
        crystalList.add(new CrystalInstance(new Vec3(-0.2, 0.65, -0.7), new Vec3(0, 0, 0)));
    }

    private boolean isGhostMode() {
        return "ghosts".equals(espMode.get());
    }

    private boolean isCaptureMarkMode() {
        return "capture_mark".equals(espMode.get());
    }

    private boolean isCrystalsMode() {
        return "crystals".equals(espMode.get());
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

        private CrystalInstance(Vec3 position, Vec3 rotation) {
            this.position = position;
            this.rotation = rotation;
            this.rotationSpeed = 0.5f + (float) (Math.random() * 1.5f);
        }

        private float selfRotationDeg() {
            return (System.currentTimeMillis() % 36000L) / 100.0f * rotationSpeed;
        }

        private float pulsation() {
            return 1.0f + (float) (Math.sin(System.currentTimeMillis() / 500.0) * 0.1f);
        }

        private void append(MeshBuilder additiveMesh,
                            MeshBuilder fillMesh,
                            MeshBuilder lineMesh,
                            MeshBuilder bloomMesh,
                            Vec3 targetPos,
                            float orbitYawRad,
                            int baseColor) {
            appendCrystalGeometry(additiveMesh, targetPos, this, orbitYawRad, 1.0f, applyOpacity(baseColor, 0.2f), true);
            appendCrystalGeometry(fillMesh, targetPos, this, orbitYawRad, 1.0f, applyOpacity(baseColor, 0.3f), true);
            appendCrystalGeometry(lineMesh, targetPos, this, orbitYawRad, 1.0f, applyOpacity(baseColor, 0.8f), false);
            appendCrystalGeometry(additiveMesh, targetPos, this, orbitYawRad, 1.2f, applyOpacity(baseColor, 0.3f), true);
            appendBloomSphere(bloomMesh, targetPos, this, orbitYawRad, baseColor);
        }
    }
}
