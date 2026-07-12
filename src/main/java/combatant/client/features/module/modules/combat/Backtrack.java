/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.util.network.BacktrackController;

/**
 * Backtrack module front-end for the shared BlinkManager-backed incoming packet controller.
 */
//todo Description
@ModuleInfo(
        id = "backtrack",
        displayName = "Backtrack",
        category = ModuleCategory.COMBAT
)
public final class Backtrack extends Module {

    private final NumberValue<Float> minRange = numCommon(
            "backtrackMinRange",
            "range_min",
            CommonSettingSchemas.COMBAT_BACKTRACK_RANGE_MIN,
            0.0f,
            0.0f,
            10.0f
    );

    private final NumberValue<Float> maxRange = numCommon(
            "backtrackMaxRange",
            "range_max",
            CommonSettingSchemas.COMBAT_BACKTRACK_RANGE_MAX,
            3.0f,
            0.0f,
            10.0f
    );

    private final NumberValue<Integer> minDelay = numCommon(
            "backtrackMinDelay",
            "delay_min",
            CommonSettingSchemas.COMBAT_BACKTRACK_DELAY_MIN,
            100,
            0,
            1000
    );
    private final NumberValue<Integer> maxDelay = numCommon(
            "backtrackMaxDelay",
            "delay_max",
            CommonSettingSchemas.COMBAT_BACKTRACK_DELAY_MAX,
            150,
            0,
            1000
    );

    private final NumberValue<Integer> minNextBacktrackDelay = numCommon(
            "backtrackMinNextDelay",
            "next_delay_min",
            CommonSettingSchemas.COMBAT_BACKTRACK_NEXT_DELAY_MIN,
            0,
            0,
            2000
    );

    private final NumberValue<Integer> maxNextBacktrackDelay = numCommon(
            "backtrackMaxNextDelay",
            "next_delay_max",
            CommonSettingSchemas.COMBAT_BACKTRACK_NEXT_DELAY_MAX,
            10,
            0,
            2000
    );

    private final NumberValue<Integer> trackingBuffer = numCommon(
            "backtrackTrackingBuffer",
            "tracking_buffer",
            CommonSettingSchemas.COMBAT_BACKTRACK_TRACKING_BUFFER,
            500,
            0,
            2000
    );

    private final NumberValue<Float> chance = numCommon(
            "backtrackChance",
            "chance",
            CommonSettingSchemas.COMBAT_BACKTRACK_CHANCE,
            100.0f,
            0.0f,
            100.0f
    );

    private final BooleanValue pauseOnHurtTime = boolCommon(
            "backtrackPauseOnHurtTime",
            "pause_on_hurt_time",
            CommonSettingSchemas.COMBAT_BACKTRACK_PAUSE_ON_HURT_TIME,
            false
    );

    private final NumberValue<Integer> hurtTime = visibleWhen(numCommon(
            "backtrackHurtTime",
            "hurt_time",
            CommonSettingSchemas.COMBAT_BACKTRACK_HURT_TIME,
            3,
            0,
            10
    ), pauseOnHurtTime::get);

    private final EnumValue<BacktrackController.TargetMode> targetMode = enumCommon(
            "backtrackTargetMode",
            "target_mode",
            CommonSettingSchemas.COMBAT_BACKTRACK_TARGET_MODE,
            BacktrackController.TargetMode.ATTACK,
            BacktrackController.TargetMode.class
    );

    private final NumberValue<Integer> lastAttackTime = visibleWhen(numCommon(
            "backtrackLastAttackTime",
            "last_attack_time",
            CommonSettingSchemas.COMBAT_BACKTRACK_LAST_ATTACK_TIME,
            1000,
            0,
            5000
    ), () -> targetMode.get() == BacktrackController.TargetMode.ATTACK);

    private final BooleanValue render = boolCommon(
            "backtrackRender",
            "render",
            CommonSettingSchemas.COMBAT_BACKTRACK_RENDER,
            true
    );

    private final RGBAColorValue fillColor = visibleWhen(color(
            "backtrackFillColor",
            "fill_color",
            "#552D8CFF"
    ), render::get);

    private final RGBAColorValue lineColor = visibleWhen(color(
            "backtrackLineColor",
            "line_color",
            "#FF9BE4FF"
    ), render::get);

    private final NumberValue<Float> lineWidth = visibleWhen(numCommon(
            "backtrackLineWidth",
            "line_width",
            CommonSettingSchemas.COMBAT_BACKTRACK_LINE_WIDTH,
            1.5f,
            0.5f,
            6.0f
    ), render::get);

    private BacktrackController.Config appliedConfig;

    private static void addBodyModel(MeshBuilder tris, MeshBuilder lines, Entity entity, Vec3 feet, int fillArgb, int lineArgb) {
        AABB currentBox = entity.getBoundingBox();
        double width = clamp(Math.max(currentBox.getXsize(), currentBox.getZsize()), 0.35, 1.35);
        double height = clamp(currentBox.getYsize(), 0.75, 3.2);
        float yaw = entity.getYRot();

        double depth = width * 0.32;
        double legHeight = height * 0.43;
        double torsoHeight = height * 0.34;
        double headSize = Math.min(width * 0.58, height * 0.24);

        double legWidth = width * 0.20;
        double legDepth = depth * 0.85;
        double legOffset = width * 0.14;
        double torsoWidth = width * 0.58;
        double armWidth = width * 0.17;
        double armOffset = torsoWidth * 0.5 + armWidth * 0.6;

        addBodyPart(tris, lines, feet, -legOffset, legHeight * 0.5, 0.0, legWidth, legHeight, legDepth, yaw, fillArgb, lineArgb);
        addBodyPart(tris, lines, feet, legOffset, legHeight * 0.5, 0.0, legWidth, legHeight, legDepth, yaw, fillArgb, lineArgb);
        addBodyPart(tris, lines, feet, 0.0, legHeight + torsoHeight * 0.5, 0.0, torsoWidth, torsoHeight, depth, yaw, fillArgb, lineArgb);
        addBodyPart(tris, lines, feet, -armOffset, legHeight + torsoHeight * 0.5, 0.0, armWidth, torsoHeight * 0.95, depth * 0.9, yaw, fillArgb, lineArgb);
        addBodyPart(tris, lines, feet, armOffset, legHeight + torsoHeight * 0.5, 0.0, armWidth, torsoHeight * 0.95, depth * 0.9, yaw, fillArgb, lineArgb);
        addBodyPart(tris, lines, feet, 0.0, height - headSize * 0.5, 0.0, headSize, headSize, headSize, yaw, fillArgb, lineArgb);
    }

    private static void addBodyPart(MeshBuilder tris,
                                    MeshBuilder lines,
                                    Vec3 origin,
                                    double centerX,
                                    double centerY,
                                    double centerZ,
                                    double sizeX,
                                    double sizeY,
                                    double sizeZ,
                                    float yaw,
                                    int fillArgb,
                                    int lineArgb) {
        Vec3[] corners = orientedBoxCorners(origin, centerX, centerY, centerZ, sizeX, sizeY, sizeZ, yaw);

        quad(tris, corners[0], corners[1], corners[2], corners[3], fillArgb);
        quad(tris, corners[4], corners[7], corners[6], corners[5], fillArgb);
        quad(tris, corners[0], corners[4], corners[5], corners[1], fillArgb);
        quad(tris, corners[3], corners[2], corners[6], corners[7], fillArgb);
        quad(tris, corners[1], corners[5], corners[6], corners[2], fillArgb);
        quad(tris, corners[0], corners[3], corners[7], corners[4], fillArgb);

        line(lines, corners[0], corners[1], lineArgb);
        line(lines, corners[1], corners[2], lineArgb);
        line(lines, corners[2], corners[3], lineArgb);
        line(lines, corners[3], corners[0], lineArgb);
        line(lines, corners[4], corners[5], lineArgb);
        line(lines, corners[5], corners[6], lineArgb);
        line(lines, corners[6], corners[7], lineArgb);
        line(lines, corners[7], corners[4], lineArgb);
        line(lines, corners[0], corners[4], lineArgb);
        line(lines, corners[1], corners[5], lineArgb);
        line(lines, corners[2], corners[6], lineArgb);
        line(lines, corners[3], corners[7], lineArgb);
    }

    private static Vec3[] orientedBoxCorners(Vec3 origin,
                                             double centerX,
                                             double centerY,
                                             double centerZ,
                                             double sizeX,
                                             double sizeY,
                                             double sizeZ,
                                             float yaw) {
        double halfX = sizeX * 0.5;
        double halfY = sizeY * 0.5;
        double halfZ = sizeZ * 0.5;

        return new Vec3[]{
                transformBodyVertex(origin, centerX - halfX, centerY - halfY, centerZ - halfZ, yaw),
                transformBodyVertex(origin, centerX + halfX, centerY - halfY, centerZ - halfZ, yaw),
                transformBodyVertex(origin, centerX + halfX, centerY - halfY, centerZ + halfZ, yaw),
                transformBodyVertex(origin, centerX - halfX, centerY - halfY, centerZ + halfZ, yaw),
                transformBodyVertex(origin, centerX - halfX, centerY + halfY, centerZ - halfZ, yaw),
                transformBodyVertex(origin, centerX + halfX, centerY + halfY, centerZ - halfZ, yaw),
                transformBodyVertex(origin, centerX + halfX, centerY + halfY, centerZ + halfZ, yaw),
                transformBodyVertex(origin, centerX - halfX, centerY + halfY, centerZ + halfZ, yaw)
        };
    }

    private static Vec3 transformBodyVertex(Vec3 origin, double localX, double localY, double localZ, float yaw) {
        double radians = Math.toRadians(-yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double x = localX * cos - localZ * sin;
        double z = localX * sin + localZ * cos;
        return new Vec3(origin.x + x, origin.y + localY, origin.z + z);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void quad(MeshBuilder mesh,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double x4, double y4, double z4,
                             int argb) {
        mesh.ensureQuadCapacity();
        putVertex(mesh, x1, y1, z1, argb);
        int i1 = mesh.next();
        putVertex(mesh, x2, y2, z2, argb);
        int i2 = mesh.next();
        putVertex(mesh, x3, y3, z3, argb);
        int i3 = mesh.next();
        putVertex(mesh, x4, y4, z4, argb);
        int i4 = mesh.next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void quad(MeshBuilder mesh, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, int argb) {
        quad(mesh, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, p4.x, p4.y, p4.z, argb);
    }

    private static void line(MeshBuilder mesh,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             int argb) {
        mesh.ensureLineCapacity();
        putVertex(mesh, x1, y1, z1, argb);
        int i1 = mesh.next();
        putVertex(mesh, x2, y2, z2, argb);
        int i2 = mesh.next();
        mesh.line(i1, i2);
    }

    private static void line(MeshBuilder mesh, Vec3 p1, Vec3 p2, int argb) {
        line(mesh, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, argb);
    }

    private static void putVertex(MeshBuilder mesh, double x, double y, double z, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        mesh.vec3(x, y, z).color(r, g, b, a);
    }

    @Override
    public void onEnable() {
        syncControllerConfig();
        BacktrackController.INSTANCE.setEnabled(true);
    }

    @Override
    public void onDisable() {
        BacktrackController.INSTANCE.setEnabled(false);
        appliedConfig = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            return;
        }
        syncControllerConfig();
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || renderer == null) {
            return;
        }
        if (!BacktrackController.INSTANCE.shouldRenderTrackedBody()) {
            return;
        }

        Entity target = BacktrackController.INSTANCE.getTarget();
        Vec3 trackedPosition = BacktrackController.INSTANCE.getTrackedPosition();
        if (target == null || trackedPosition == null || trackedPosition.equals(Vec3.ZERO)) {
            return;
        }

        MeshBuilder tris = renderer.batch(CombatantRenderPipelines.WORLD_COLORED, Renderer3D.DepthMode.NONE);
        float previousLineWidth = RenderState.lineWidth;
        MeshBuilder lines;
        try {
            RenderState.lineWidth = Math.max(0.5f, lineWidth.get());
            lines = renderer.batch(CombatantRenderPipelines.WORLD_COLORED_LINES, Renderer3D.DepthMode.NONE);
        } finally {
            RenderState.lineWidth = previousLineWidth;
        }

        addBodyModel(tris, lines, target, trackedPosition, fillColor.getArgb(), lineColor.getArgb());
    }

    private void syncControllerConfig() {
        BacktrackController.Config config = buildConfig();
        if (!config.equals(appliedConfig) || !config.equals(BacktrackController.INSTANCE.getConfig())) {
            BacktrackController.INSTANCE.configure(config);
            appliedConfig = config;
        }
    }

    private BacktrackController.Config buildConfig() {
        float min = minRange.get();
        float max = maxRange.get();
        if (targetMode.get() == BacktrackController.TargetMode.ATTACK) {
            min = 0.0f;
        }
        int minDelayMs = minDelay.get();
        int maxDelayMs = maxDelay.get();
        int minNextDelayMs = minNextBacktrackDelay.get();
        int maxNextDelayMs = maxNextBacktrackDelay.get();

        return new BacktrackController.Config(
                Math.min(min, max),
                Math.max(min, max),
                Math.min(minDelayMs, maxDelayMs),
                Math.max(minDelayMs, maxDelayMs),
                Math.min(minNextDelayMs, maxNextDelayMs),
                Math.max(minNextDelayMs, maxNextDelayMs),
                trackingBuffer.get(),
                Math.round(chance.get()),
                pauseOnHurtTime.get(),
                hurtTime.get(),
                lastAttackTime.get(),
                targetMode.get()
        );
    }
}
