/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.render;

/**
 * Pure math for off-screen indicator arrows drawn on a circle around the crosshair.
 *
 * <p>Angles are screen angles in degrees: {@code 0} points right, {@code 90} points down (screen Y grows downward),
 * {@code -90} points up. A target straight ahead of the camera therefore maps to {@code -90}.</p>
 */
public final class ArrowPlacement {

    private ArrowPlacement() {
    }

    /** A point in screen space. */
    public record Point(double x, double y) {
    }

    /**
     * Screen angle of a target relative to the camera heading.
     *
     * @param relX           target X minus camera X (world blocks)
     * @param relZ           target Z minus camera Z (world blocks)
     * @param cameraYawDeg   camera yaw in Minecraft degrees (0 = looking towards +Z, 90 = towards -X)
     * @return angle in degrees in {@code (-180, 180]}
     */
    public static double screenAngleDeg(double relX, double relZ, double cameraYawDeg) {
        double yawRad = Math.toRadians(cameraYawDeg);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double rotY = -(relZ * cos - relX * sin);
        double rotX = -(relX * cos + relZ * sin);
        return Math.toDegrees(Math.atan2(rotY, rotX));
    }

    /** Point on a circle of {@code radius} around ({@code cx}, {@code cy}) at the given screen angle. */
    public static Point onCircle(double angleDeg, double radius, double cx, double cy) {
        double rad = Math.toRadians(angleDeg);
        return new Point(cx + radius * Math.cos(rad), cy + radius * Math.sin(rad));
    }

    /**
     * Radius multiplier that pulls close targets towards the crosshair and pushes far ones to the full radius.
     * Linear between {@code near} and {@code far}, clamped outside.
     */
    public static float radiusFactor(double distance, double near, double far, float minFactor, float maxFactor) {
        if (distance <= near) return minFactor;
        if (distance >= far) return maxFactor;
        double t = (distance - near) / (far - near);
        return (float) (minFactor + (maxFactor - minFactor) * t);
    }

    /**
     * Linearly blends two ARGB colors by distance: {@code nearArgb} at or below {@code near}, {@code farArgb} at or
     * above {@code far}.
     */
    public static int distanceColor(double distance, double near, double far, int nearArgb, int farArgb) {
        double t;
        if (far <= near) {
            t = distance >= far ? 1.0 : 0.0;
        } else {
            t = Math.min(1.0, Math.max(0.0, (distance - near) / (far - near)));
        }
        int a = lerpChannel(nearArgb >>> 24, farArgb >>> 24, t);
        int r = lerpChannel(nearArgb >>> 16, farArgb >>> 16, t);
        int g = lerpChannel(nearArgb >>> 8, farArgb >>> 8, t);
        int b = lerpChannel(nearArgb, farArgb, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, double t) {
        int f = from & 0xFF;
        int o = to & 0xFF;
        return (int) Math.round(f + (o - f) * t);
    }
}
