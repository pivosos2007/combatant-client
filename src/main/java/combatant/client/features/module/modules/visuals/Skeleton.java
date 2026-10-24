/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.relations.CategoryService;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.WorldRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.helpers.PlayerPoseCapture;
import combatant.client.render.helpers.PlayerPoseCapture.PartId;
import combatant.client.render.helpers.PlayerPoseCapture.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "skeleton",
        displayName = "Skeleton",
        aliases = {"bones"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.skeleton.description")
public final class Skeleton extends Module {
    private final Minecraft mc = Minecraft.getInstance();

    private final RGBAColorValue color = color("skeleton_color", "color", "#FFFFFFFF");
    private final BooleanValue relationColor = bool("skeleton_relation_color", "relation_color", true);
    private final NumberValue<Float> lineWidth = num("skeleton_line_width", "line_width", 1.5f, 0.5f, 6.0f);
    private final NumberValue<Integer> maxDistance = num("skeleton_max_distance", "max_distance", 64, 8, 256);
    private final BooleanValue throughWalls = bool("skeleton_through_walls", "through_walls", true);
    private final BooleanValue showSelf = bool("skeleton_show_self", "show_self", false);

    @Override
    public void onEnable() {
        PlayerPoseCapture.retain();
    }

    @Override
    public void onDisable() {
        PlayerPoseCapture.release();
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.level == null || mc.player == null) return;

        float previous = RenderState.lineWidth;
        MeshBuilder lines;
        try {
            RenderState.lineWidth = lineWidth.get();
            lines = renderer.batch(WorldRenderPipelines.WORLD_COLORED_LINES,
                    throughWalls.get() ? Renderer3D.DepthMode.NONE : Renderer3D.DepthMode.MAIN);
        } finally {
            RenderState.lineWidth = previous;
        }
        if (lines == null) return;

        double maxSq = (double) maxDistance.get() * maxDistance.get();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Player player : mc.level.players()) {
            if (player == mc.player && (!showSelf.get() || firstPerson)) continue;
            if (player.isInvisible() || player.distanceToSqr(mc.player) > maxSq) continue;
            Pose pose = PlayerPoseCapture.latest(player.getId());
            if (pose == null) continue;
            int argb = relationColor.get() ? CategoryService.getColor(player) : color.getArgb();
            draw(lines, pose, argb);
        }
    }

    private static void draw(MeshBuilder lines, Pose pose, int argb) {
        var head = pose.part(PartId.HEAD);
        var body = pose.part(PartId.BODY);
        var rArm = pose.part(PartId.RIGHT_ARM);
        var lArm = pose.part(PartId.LEFT_ARM);
        var rLeg = pose.part(PartId.RIGHT_LEG);
        var lLeg = pose.part(PartId.LEFT_LEG);

        Vec3 headTop = pose.world(head, head.centerX(), head.minY(), head.centerZ());
        Vec3 neck = pose.world(body, body.centerX(), body.minY(), body.centerZ());
        Vec3 pelvis = pose.world(body, body.centerX(), body.maxY(), body.centerZ());
        Vec3 rShoulder = pose.world(rArm, 0, 0, 0);
        Vec3 lShoulder = pose.world(lArm, 0, 0, 0);
        Vec3 rHand = pose.world(rArm, rArm.centerX(), rArm.maxY(), rArm.centerZ());
        Vec3 lHand = pose.world(lArm, lArm.centerX(), lArm.maxY(), lArm.centerZ());
        Vec3 rHip = pose.world(rLeg, 0, 0, 0);
        Vec3 lHip = pose.world(lLeg, 0, 0, 0);
        Vec3 rFoot = pose.world(rLeg, rLeg.centerX(), rLeg.maxY(), rLeg.centerZ());
        Vec3 lFoot = pose.world(lLeg, lLeg.centerX(), lLeg.maxY(), lLeg.centerZ());

        line(lines, headTop, neck, argb);
        line(lines, neck, pelvis, argb);
        line(lines, rShoulder, lShoulder, argb);
        line(lines, rShoulder, rHand, argb);
        line(lines, lShoulder, lHand, argb);
        line(lines, rHip, lHip, argb);
        line(lines, rHip, rFoot, argb);
        line(lines, lHip, lFoot, argb);
    }

    private static void line(MeshBuilder mesh, Vec3 a, Vec3 b, int argb) {
        int al = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int bl = argb & 0xFF;
        mesh.ensureLineCapacity();
        int i1 = mesh.vec3(a.x, a.y, a.z).color(r, g, bl, al).next();
        int i2 = mesh.vec3(b.x, b.y, b.z).color(r, g, bl, al).next();
        mesh.line(i1, i2);
    }
}
