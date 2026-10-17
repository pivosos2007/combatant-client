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
    private static final Vector4f TMP_POS = new Vector4f();
    private static final double[] PROJ_OUT = new double[3];
    private static int cachedVersion = -1;
    private static double camX;
    private static double camY;
    private static double camZ;

    /**
     * @return coordinates in the currently active 2D viewport space (x, y, z), or null if behind camera
     */
    public static Vec3 worldToScreen(Vec3 worldPos, float tickDelta) {
        double[] out = new double[3];
        return project(worldPos.x, worldPos.y, worldPos.z, out) ? new Vec3(out[0], out[1], out[2]) : null;
    }

    /**
     * Allocation-free variant of {@link #worldToScreen}; writes x, y, ndcZ into out.
     * @return false if behind camera or not projectable
     */
    public static boolean project(double x, double y, double z, double[] out) {
        Minecraft mc = Minecraft.getInstance();
        if (!prepare(mc)) return false;
        return projectPrepared(mc, x, y, z, out);
    }

    /**
     * Projects the 8 corners of a box and writes the screen bounds (minX, minY, maxX, maxY) into out.
     * @return false if every corner is behind the camera / unprojectable, or the bounds are degenerate
     */
    public static boolean projectBounds(double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ, double[] out) {
        Minecraft mc = Minecraft.getInstance();
        if (!prepare(mc)) return false;

        double hx = (maxX - minX) * 0.5;
        double hy = (maxY - minY) * 0.5;
        double hz = (maxZ - minZ) * 0.5;
        double cx = minX + hx - camX;
        double cy = minY + hy - camY;
        double cz = minZ + hz - camZ;
        double wCenter = VP.m03() * cx + VP.m13() * cy + VP.m23() * cz + VP.m33();
        double wExtent = Math.abs(VP.m03()) * hx + Math.abs(VP.m13()) * hy + Math.abs(VP.m23()) * hz;
        if (wCenter + wExtent < -0.001) return false;

        double bMinX = Double.POSITIVE_INFINITY;
        double bMinY = Double.POSITIVE_INFINITY;
        double bMaxX = Double.NEGATIVE_INFINITY;
        double bMaxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        double[] p = PROJ_OUT;
        for (int i = 0; i < 8; i++) {
            double x = (i & 4) == 0 ? minX : maxX;
            double y = (i & 2) == 0 ? minY : maxY;
            double z = (i & 1) == 0 ? minZ : maxZ;
            if (!projectPrepared(mc, x, y, z, p)) continue;
            any = true;
            if (p[0] < bMinX) bMinX = p[0];
            if (p[0] > bMaxX) bMaxX = p[0];
            if (p[1] < bMinY) bMinY = p[1];
            if (p[1] > bMaxY) bMaxY = p[1];
        }
        if (!any || bMaxX <= bMinX || bMaxY <= bMinY) return false;
        out[0] = bMinX;
        out[1] = bMinY;
        out[2] = bMaxX;
        out[3] = bMaxY;
        return true;
    }

    private static boolean prepare(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null || mc.getWindow() == null) return false;
        if (!CombatantWorldMatrices.isValid()) {
            cachedVersion = -1;
            return rebuildFallback(mc);
        }
        return cachedVersion == CombatantWorldMatrices.version() || rebuildFromWorldMatrices(mc);
    }

    private static boolean projectPrepared(Minecraft mc, double x, double y, double z, double[] out) {
        TMP_POS.set((float) (x - camX), (float) (y - camY), (float) (z - camZ), 1.0f);
        VP.transform(TMP_POS);
        float w = TMP_POS.w;
        if (w <= 0.0001f) return false;

        double ndcX = TMP_POS.x / w;
        double ndcY = TMP_POS.y / w;
        double ndcZ = TMP_POS.z / w;
        if (!Double.isFinite(ndcX) || !Double.isFinite(ndcY) || !Double.isFinite(ndcZ)) return false;

        ViewportContext viewport = ViewportContext.current();
        double viewportWidth = viewport != null ? viewport.width() : mc.getWindow().getWidth();
        double viewportHeight = viewport != null ? viewport.height() : mc.getWindow().getHeight();
        out[0] = (viewportWidth * 0.5) * (1.0 + ndcX);
        out[1] = (viewportHeight * 0.5) * (1.0 - ndcY);
        out[2] = ndcZ;
        return true;
    }

    private static boolean rebuildFromWorldMatrices(Minecraft mc) {
        Vec3 camPos = CombatantWorldMatrices.cameraPosition();
        if (camPos == null) camPos = mc.gameRenderer.mainCamera().position();
        Matrix4f view = CombatantWorldMatrices.positionMatrix();
        Matrix4f proj = CombatantWorldMatrices.frustumProjectionMatrix();
        if (view == null || proj == null) return false;
        proj.mul(view, VP);
        camX = camPos.x;
        camY = camPos.y;
        camZ = camPos.z;
        cachedVersion = CombatantWorldMatrices.version();
        return true;
    }

    private static boolean rebuildFallback(Minecraft mc) {
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 camPos = cam.position();
        TMP_ROT.set(cam.rotation()).conjugate();
        Matrix4f view = new Matrix4f().rotate(TMP_ROT);
        currentRawCameraProjection(mc, cam).mul(view, VP);
        camX = camPos.x;
        camY = camPos.y;
        camZ = camPos.z;
        return true;
    }

    private static Matrix4f currentRawCameraProjection(Minecraft mc, Camera camera) {
        CameraRenderState cameraRenderState = new CameraRenderState();
        camera.extractRenderState(cameraRenderState, mc.getDeltaTracker());
        Matrix4f projection = new Matrix4f(cameraRenderState.projectionMatrix);
        AspectRatio.applyToProjection(projection);
        return projection;
    }
}
