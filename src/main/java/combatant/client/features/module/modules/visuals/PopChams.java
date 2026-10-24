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
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.WorldRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.helpers.PlayerPoseCapture;
import combatant.client.render.helpers.PlayerPoseCapture.Part;
import combatant.client.render.helpers.PlayerPoseCapture.Pose;
import combatant.client.util.pvp.opponents.TotemPopCounter;
import combatant.client.util.pvp.opponents.TotemPopSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "popchams",
        displayName = "PopChams",
        aliases = {"popghost"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.popchams.description")
public final class PopChams extends Module {
    private static final int MAX_GHOSTS = 16;
    /** Cube corner indices (bit0 = x max, bit1 = y max, bit2 = z max) per face, wound like Renderer3D.filledBox. */
    private static final int[][] FACES = {
            {0, 1, 5, 4}, {2, 6, 7, 3}, {4, 5, 7, 6}, {0, 2, 3, 1}, {1, 3, 7, 5}, {0, 4, 6, 2}
    };

    private final Minecraft mc = Minecraft.getInstance();
    private final RGBAColorValue color = color("popchams_color", "color", "#B4FFD24A");
    private final NumberValue<Integer> durationMs = num("popchams_duration_ms", "duration_ms", 1500, 200, 5000);
    private final BooleanValue outline = bool("popchams_outline", "outline", true);
    private final BooleanValue throughWalls = bool("popchams_through_walls", "through_walls", true);
    private final BooleanValue self = bool("popchams_self", "self", false);

    private final List<Ghost> ghosts = new ArrayList<>();

    private final TotemPopCounter.Listener listener = new TotemPopCounter.Listener() {
        @Override
        public void onPop(TotemPopSnapshot snapshot) {
            Minecraft client = Minecraft.getInstance();
            if (!isEnabled() || snapshot == null || snapshot.playerId() == null) return;
            client.execute(() -> spawn(snapshot));
        }
    };

    private record Ghost(Pose pose, long startMs) {
    }

    @Override
    public void onEnable() {
        PlayerPoseCapture.retain();
        TotemPopCounter.addListener(listener);
    }

    @Override
    public void onDisable() {
        TotemPopCounter.removeListener(listener);
        PlayerPoseCapture.release();
        ghosts.clear();
    }

    private void spawn(TotemPopSnapshot snapshot) {
        if (mc.level == null || mc.player == null) return;
        Player player = mc.level.getPlayerByUUID(snapshot.playerId());
        if (player == null || (player == mc.player && !self.get())) return;
        Pose pose = PlayerPoseCapture.latest(player.getId());
        if (pose == null) return;
        if (ghosts.size() >= MAX_GHOSTS) ghosts.remove(0);
        ghosts.add(new Ghost(pose, Util.getMillis()));
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || ghosts.isEmpty()) return;
        long now = Util.getMillis();
        long life = durationMs.get();
        ghosts.removeIf(g -> now - g.startMs() >= life);
        if (ghosts.isEmpty()) return;

        Renderer3D.DepthMode depth = throughWalls.get() ? Renderer3D.DepthMode.NONE : Renderer3D.DepthMode.MAIN;
        MeshBuilder tris = renderer.batch(WorldRenderPipelines.WORLD_COLORED, depth);
        float previous = RenderState.lineWidth;
        MeshBuilder lines;
        try {
            RenderState.lineWidth = 1.5f;
            lines = outline.get() ? renderer.batch(WorldRenderPipelines.WORLD_COLORED_LINES, depth) : null;
        } finally {
            RenderState.lineWidth = previous;
        }

        int base = color.getArgb();
        int baseAlpha = (base >>> 24) & 0xFF;
        double[] c = new double[24];
        for (Ghost ghost : ghosts) {
            float t = (now - ghost.startMs()) / (float) life;
            float fade = (1.0f - t) * (1.0f - t);
            int fill = (Math.max(0, Math.min(255, Math.round(baseAlpha * fade))) << 24) | (base & 0xFFFFFF);
            int line = (Math.max(0, Math.min(255, Math.round(255 * fade))) << 24) | (base & 0xFFFFFF);
            for (Part part : ghost.pose().parts()) {
                ghost.pose().corners(part, c);
                if (tris != null) addFill(tris, c, fill);
                if (lines != null) addOutline(lines, c, line);
            }
        }
    }

    private static void addFill(MeshBuilder mesh, double[] c, int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        for (int[] face : FACES) {
            mesh.ensureQuadCapacity();
            int i0 = mesh.vec3(c[face[0] * 3], c[face[0] * 3 + 1], c[face[0] * 3 + 2]).color(r, g, b, a).next();
            int i1 = mesh.vec3(c[face[1] * 3], c[face[1] * 3 + 1], c[face[1] * 3 + 2]).color(r, g, b, a).next();
            int i2 = mesh.vec3(c[face[2] * 3], c[face[2] * 3 + 1], c[face[2] * 3 + 2]).color(r, g, b, a).next();
            int i3 = mesh.vec3(c[face[3] * 3], c[face[3] * 3 + 1], c[face[3] * 3 + 2]).color(r, g, b, a).next();
            mesh.quad(i0, i1, i2, i3);
        }
    }

    private static void addOutline(MeshBuilder mesh, double[] c, int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) != 0) continue;
                int j = i | bit;
                mesh.ensureLineCapacity();
                int p = mesh.vec3(c[i * 3], c[i * 3 + 1], c[i * 3 + 2]).color(r, g, b, a).next();
                int q = mesh.vec3(c[j * 3], c[j * 3 + 1], c[j * 3 + 2]).color(r, g, b, a).next();
                mesh.line(p, q);
            }
        }
    }
}
