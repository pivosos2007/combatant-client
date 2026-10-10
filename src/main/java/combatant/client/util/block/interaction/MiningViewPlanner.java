/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.block.interaction;

/**
 * Pure view-space selection rules. The Minecraft-facing planner resolves voxel visibility,
 * reach and tool policy before calling these methods. Deliberately independent of game state
 * so height, camera and distance decisions can be regression-tested without a client.
 */
public final class MiningViewPlanner {
    private MiningViewPlanner() { }

    public enum HeightMode { LINE, FOLLOW, SNEAK_DOWN }

    /** Minimum Y of a mineable block. LINE never lowers the activation floor. */
    public static int floorY(HeightMode mode, int activationY, int playerFeetY,
                             boolean sneaking, float cameraPitch) {
        return switch (mode) {
            case LINE -> Math.max(activationY, playerFeetY);
            case FOLLOW -> playerFeetY;
            case SNEAK_DOWN -> sneaking && cameraPitch >= 35.0f ? playerFeetY - 1 : playerFeetY;
        };
    }

    /** Horizontal direction follows the CAMERA's yaw, not horizontal motion or server yaw. */
    public static double forwardX(double yawDegrees) {
        return -Math.sin(Math.toRadians(yawDegrees));
    }

    public static double forwardZ(double yawDegrees) {
        return Math.cos(Math.toRadians(yawDegrees));
    }

    public static double forwardDistance(double yawDegrees, double dx, double dz) {
        return dx * forwardX(yawDegrees) + dz * forwardZ(yawDegrees);
    }

    public static double lateralDistance(double yawDegrees, double dx, double dz) {
        return dx * forwardZ(yawDegrees) - dz * forwardX(yawDegrees);
    }

    public static double viewAngle(double lookX, double lookY, double lookZ,
                                   double dx, double dy, double dz) {
        double lookLength = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance) || !Double.isFinite(lookLength)
                || distance < 1.0E-7 || lookLength < 1.0E-7) return 180.0;
        double dot = (lookX * dx + lookY * dy + lookZ * dz) / (lookLength * distance);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    public static boolean withinView(double angleDegrees, double limitDegrees) {
        return Double.isFinite(angleDegrees) && angleDegrees <= limitDegrees;
    }

    /** Score only physically feasible points. Point distance is to the visible surface, not block center. */
    public static double score(double angleDegrees, double surfaceDistance) {
        return angleDegrees * 1.0 + surfaceDistance * 7.0;
    }

    /** The camera-directed tunnel may never select a cell behind or outside its transverse width. */
    public static boolean inCorridor(double yawDegrees, double dx, double dz,
                                     double widthBlocks, double maxForwardDistance) {
        double forward = forwardDistance(yawDegrees, dx, dz);
        double lateral = Math.abs(lateralDistance(yawDegrees, dx, dz));
        return forward > 0.20 && forward <= maxForwardDistance
                && lateral <= widthBlocks * 0.5 + 0.20;
    }
}
