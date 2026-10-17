/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

/**
 * Orbit camera around the schematic's centre with eased yaw, pitch, distance and screen-space pan. A narrow field
 * of view keeps the projection close to isometric while still giving depth.
 */
public final class OrbitCamera {

    public static final float FOV = (float) Math.toRadians(30.0);
    public static final float DEFAULT_YAW = (float) Math.toRadians(-45.0);
    public static final float DEFAULT_PITCH = (float) Math.toRadians(32.0);
    static final float NEAR = 0.05f;
    private static final float MAX_PITCH = 1.50f;
    private static final float ORBIT_RADIANS_PER_PIXEL = 0.0085f;
    private static final float AUTO_ROTATE_SPEED = 0.35f;
    private static final float EASE_RATE = 14f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private float yaw = DEFAULT_YAW, pitch = DEFAULT_PITCH, distance = 10f, panX, panY;
    private float targetYaw = DEFAULT_YAW, targetPitch = DEFAULT_PITCH, targetDistance = 10f, targetPanX, targetPanY;
    private float centreX, centreY, centreZ, radius = 1f;
    private float targetCentreX, targetCentreY, targetCentreZ;
    private boolean framed;

    private float eyeX, eyeY, eyeZ;
    private float rightX, rightY, rightZ;
    private float upX, upY, upZ;
    private float forwardX, forwardY, forwardZ;
    private float focal = 1f, originX, originY;

    /**
     * Aims at a box. The first call after {@link #clearFrame} jumps there; later calls (an exploded view growing,
     * say) ease the centre and scale the zoom with the box so the framing the user chose is kept.
     */
    public void frame(float cx, float cy, float cz, float boundingRadius) {
        targetCentreX = cx;
        targetCentreY = cy;
        targetCentreZ = cz;
        float next = Math.max(0.5f, boundingRadius);
        if (!framed) {
            framed = true;
            radius = next;
            centreX = cx;
            centreY = cy;
            centreZ = cz;
            targetDistance = fitDistance();
            distance = targetDistance;
            return;
        }
        if (Math.abs(next - radius) > 1.0e-3f) {
            targetDistance *= next / radius;
            radius = next;
        }
    }

    public void clearFrame() {
        framed = false;
    }

    public void reset() {
        targetYaw = nearestEquivalent(DEFAULT_YAW, yaw);
        targetPitch = DEFAULT_PITCH;
        targetDistance = fitDistance();
        targetPanX = 0f;
        targetPanY = 0f;
    }

    public void snap() {
        centreX = targetCentreX;
        centreY = targetCentreY;
        centreZ = targetCentreZ;
        yaw = targetYaw;
        pitch = targetPitch;
        distance = targetDistance;
        panX = targetPanX;
        panY = targetPanY;
    }

    public void orbit(float dxPixels, float dyPixels) {
        targetYaw -= dxPixels * ORBIT_RADIANS_PER_PIXEL;
        targetPitch = clamp(targetPitch + dyPixels * ORBIT_RADIANS_PER_PIXEL, -MAX_PITCH, MAX_PITCH);
    }

    public void pan(float dxPixels, float dyPixels) {
        targetPanX += dxPixels;
        targetPanY += dyPixels;
    }

    public void zoom(float steps) {
        targetDistance = clamp(targetDistance * (float) Math.pow(0.87, steps), radius * 0.35f, radius * 10f);
    }

    public void update(float dt, boolean autoRotate) {
        if (autoRotate) targetYaw += dt * AUTO_ROTATE_SPEED;
        float k = 1f - (float) Math.exp(-dt * EASE_RATE);
        yaw += (targetYaw - yaw) * k;
        pitch += (targetPitch - pitch) * k;
        distance += (targetDistance - distance) * k;
        panX += (targetPanX - panX) * k;
        panY += (targetPanY - panY) * k;
        centreX += (targetCentreX - centreX) * k;
        centreY += (targetCentreY - centreY) * k;
        centreZ += (targetCentreZ - centreZ) * k;
        // Keep the angles small so endless auto-rotation never costs float precision.
        if (Math.abs(yaw) > TWO_PI * 4f) {
            float wrap = (float) Math.floor(yaw / TWO_PI) * TWO_PI;
            yaw -= wrap;
            targetYaw -= wrap;
        }
    }

    public boolean settled() {
        return Math.abs(targetYaw - yaw) < 1.0e-4f && Math.abs(targetPitch - pitch) < 1.0e-4f
                && Math.abs(targetDistance - distance) < 1.0e-3f
                && Math.abs(targetPanX - panX) < 0.05f && Math.abs(targetPanY - panY) < 0.05f;
    }

    /** Recomputes the view basis for a viewport; call once per frame before projecting. */
    public void computeView(float viewX, float viewY, float viewW, float viewH) {
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float ox = cp * sy, oy = sp, oz = cp * cy;
        eyeX = centreX + ox * distance;
        eyeY = centreY + oy * distance;
        eyeZ = centreZ + oz * distance;
        forwardX = -ox;
        forwardY = -oy;
        forwardZ = -oz;
        float rl = (float) Math.sqrt(forwardZ * forwardZ + forwardX * forwardX);
        if (rl < 1.0e-6f) rl = 1.0e-6f;
        rightX = -forwardZ / rl;
        rightY = 0f;
        rightZ = forwardX / rl;
        upX = rightY * forwardZ - rightZ * forwardY;
        upY = rightZ * forwardX - rightX * forwardZ;
        upZ = rightX * forwardY - rightY * forwardX;
        focal = Math.min(viewW, viewH) * 0.5f / (float) Math.tan(FOV * 0.5f);
        originX = viewX + viewW * 0.5f + panX;
        originY = viewY + viewH * 0.5f + panY;
    }

    /** Screen position of a world point into {@code out[off], out[off + 1]}; false when it is behind the camera. */
    public boolean project(float x, float y, float z, float[] out, int off) {
        float dx = x - eyeX, dy = y - eyeY, dz = z - eyeZ;
        float depth = dx * forwardX + dy * forwardY + dz * forwardZ;
        if (depth < NEAR) return false;
        float inv = focal / depth;
        out[off] = originX + (dx * rightX + dy * rightY + dz * rightZ) * inv;
        out[off + 1] = originY - (dx * upX + dy * upY + dz * upZ) * inv;
        return true;
    }

    /** Ray through a screen point as {originX, originY, originZ, dirX, dirY, dirZ} with a unit direction. */
    public void ray(float screenX, float screenY, float[] out) {
        float a = (screenX - originX) / focal;
        float b = -(screenY - originY) / focal;
        float dx = forwardX + rightX * a + upX * b;
        float dy = forwardY + rightY * a + upY * b;
        float dz = forwardZ + rightZ * a + upZ * b;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        out[0] = eyeX;
        out[1] = eyeY;
        out[2] = eyeZ;
        out[3] = dx / len;
        out[4] = dy / len;
        out[5] = dz / len;
    }

    /**
     * World-to-view matrix in column-major order (OpenGL layout) followed by the focal length scaling, for
     * callers that want the camera as one transform rather than through {@link #project}.
     */
    public void viewMatrix(float[] out) {
        out[0] = rightX;
        out[1] = upX;
        out[2] = forwardX;
        out[3] = 0f;
        out[4] = rightY;
        out[5] = upY;
        out[6] = forwardY;
        out[7] = 0f;
        out[8] = rightZ;
        out[9] = upZ;
        out[10] = forwardZ;
        out[11] = 0f;
        out[12] = -(rightX * eyeX + rightY * eyeY + rightZ * eyeZ);
        out[13] = -(upX * eyeX + upY * eyeY + upZ * eyeZ);
        out[14] = -(forwardX * eyeX + forwardY * eyeY + forwardZ * eyeZ);
        out[15] = 1f;
    }

    public float eyeX() {
        return eyeX;
    }

    public float eyeY() {
        return eyeY;
    }

    public float eyeZ() {
        return eyeZ;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float distance() {
        return distance;
    }

    public float targetDistance() {
        return targetDistance;
    }

    private float fitDistance() {
        return radius / (float) Math.sin(FOV * 0.5f) * 1.08f;
    }

    private static float nearestEquivalent(float angle, float reference) {
        float turns = (float) Math.rint((reference - angle) / TWO_PI);
        return angle + turns * TWO_PI;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
