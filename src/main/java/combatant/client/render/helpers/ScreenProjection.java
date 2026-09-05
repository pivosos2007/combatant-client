/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import combatant.client.features.module.modules.visuals.AspectRatio;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.core.ViewportContext;

/**
 * Helper for projecting world-space points into the currently active 2D viewport space.
 *
 * Screen overlays must use the same stable camera projection space as vanilla
 * GameRenderer.projectPointToScreen: raw camera projection * view rotation.
 * Full render projection is intentionally not used here because vanilla view
 * effects such as bob/hurt/nausea are applied to the world render pass and make
 * HUD markers jitter when mixed with 2D overlay coordinates.
 */
public enum ScreenProjection {
    ;

    private static final Matrix4f VP = new Matrix4f();
    private static final Quaternionf TMP_ROT = new Quaternionf();

    /**
     * @return coordinates in the currently active 2D viewport space (x, y, z), or null if behind camera
     */
    public static Vec3 worldToScreen(Vec3 worldPos, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.getWindow() == null) return null;

        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 camPos = CombatantWorldMatrices.cameraPosition();
        if (camPos == null) {
            camPos = cam.position();
        }

        Matrix4f view = CombatantWorldMatrices.positionMatrix();
        if (view == null) {
            TMP_ROT.set(cam.rotation()).conjugate();
            view = new Matrix4f().rotate(TMP_ROT);
        }

        Matrix4f proj = CombatantWorldMatrices.frustumProjectionMatrix();
        if (proj == null) {
            proj = currentRawCameraProjection(mc, cam);
        }

        proj.mul(view, VP);

        double relX = worldPos.x - camPos.x;
        double relY = worldPos.y - camPos.y;
        double relZ = worldPos.z - camPos.z;

        Vector4f pos = new Vector4f((float) relX, (float) relY, (float) relZ, 1.0f);
        VP.transform(pos);
        if (pos.w <= 0.0001f) return null;

        double ndcX = pos.x / pos.w;
        double ndcY = pos.y / pos.w;
        double ndcZ = pos.z / pos.w;
        if (!Double.isFinite(ndcX) || !Double.isFinite(ndcY) || !Double.isFinite(ndcZ)) return null;

        ViewportContext viewport = ViewportContext.current();
        double viewportWidth = viewport != null ? viewport.width() : mc.getWindow().getWidth();
        double viewportHeight = viewport != null ? viewport.height() : mc.getWindow().getHeight();
        double sx = (viewportWidth * 0.5) * (1.0 + ndcX);
        double sy = (viewportHeight * 0.5) * (1.0 - ndcY);

        return new Vec3(sx, sy, ndcZ);
    }

    private static Matrix4f currentRawCameraProjection(Minecraft mc, Camera camera) {
        CameraRenderState cameraRenderState = new CameraRenderState();
        camera.extractRenderState(cameraRenderState, camera.getFov());
        Matrix4f projection = new Matrix4f(cameraRenderState.projectionMatrix);
        AspectRatio.applyToProjection(projection);
        return projection;
    }
}
