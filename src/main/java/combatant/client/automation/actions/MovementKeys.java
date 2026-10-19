/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.actions;

import combatant.client.automation.ActionContext;
import combatant.client.automation.LeaseHolder;

/**
 * Turns "go toward that point" into the WASD keys that get there, given where the player faces.
 * Eight directions, so the path is a short zig-zag at worst and never needs a view rotation.
 */
public final class MovementKeys {

    public static final int FORWARD = 1;
    public static final int BACKWARD = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;

    /** sin(22.5 degrees): a target further than that off an axis also presses the neighbouring key. */
    private static final double AXIS_THRESHOLD = Math.sin(Math.toRadians(22.5));

    private MovementKeys() {
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
        return keys(forward, left, AXIS_THRESHOLD);
    }

    /**
     * Like {@link #keysToward} but with a distance-dependent dead zone: far away only a clear sideways offset adds a
     * strafe key; close to the target a small one does too, so the body lines up with a one-wide gap instead of
     * rubbing along its corner.
     */
    public static int keysTowardPrecise(float yawDegrees, double dx, double dz) {
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) return 0;

        double yaw = Math.toRadians(yawDegrees);
        double forward = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
        double left = Math.cos(yaw) * dx + Math.sin(yaw) * dz;
        double threshold = Math.min(0.08 + 0.1 * length, 0.38 * length);
        return keys(forward, left, threshold);
    }

    private static int keys(double forward, double left, double threshold) {
        int keys = 0;
        if (forward > threshold) keys |= FORWARD;
        else if (forward < -threshold) keys |= BACKWARD;
        if (left > threshold) keys |= LEFT;
        else if (left < -threshold) keys |= RIGHT;
        return keys;
    }

    /** Writes {@code keys} as this tick's movement. Ignored unless {@code holder} holds MOVEMENT. */
    public static void apply(ActionContext ctx, LeaseHolder holder, int keys) {
        ctx.setMovement(holder, (keys & FORWARD) != 0, (keys & BACKWARD) != 0, (keys & LEFT) != 0,
                (keys & RIGHT) != 0);
    }
}
