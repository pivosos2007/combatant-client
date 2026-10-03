/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.holesnap;

/**
 * Turns "go toward that point" into the WASD keys that get there, given where the player faces.
 * Eight directions, so the path is a short zig-zag at worst and never needs a view rotation.
 */
public final class Steering {

    public static final int FORWARD = 1;
    public static final int BACKWARD = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;

    /** sin(22.5 degrees): a target further than that off an axis also presses the neighbouring key. */
    private static final double AXIS_THRESHOLD = Math.sin(Math.toRadians(22.5));

    private Steering() {
    }

    /**
     * @param yawDegrees Minecraft yaw: 0 faces +Z (south), 90 faces -X (west)
     * @param dx         world X offset to the target
     * @param dz         world Z offset to the target
     * @return bitmask of {@link #FORWARD}, {@link #BACKWARD}, {@link #LEFT}, {@link #RIGHT}; 0 when on target
     */
    public static int keysToward(float yawDegrees, double dx, double dz) {
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) return 0;

        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double forward = (-sin * dx + cos * dz) / length;
        double left = (cos * dx + sin * dz) / length;

        int keys = 0;
        if (forward > AXIS_THRESHOLD) keys |= FORWARD;
        else if (forward < -AXIS_THRESHOLD) keys |= BACKWARD;
        if (left > AXIS_THRESHOLD) keys |= LEFT;
        else if (left < -AXIS_THRESHOLD) keys |= RIGHT;
        return keys;
    }
}
