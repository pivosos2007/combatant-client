/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.actions;

/**
 * Pure state of "stay inside a tolerance for N consecutive ticks". Leaving the tolerance restarts the count, so a
 * rotation that overshoots does not count the ticks before the overshoot.
 */
public final class ToleranceHold {
    private final int holdTicks;
    private int satisfiedTicks;

    /** @param holdTicks consecutive in-tolerance ticks required in addition to the first one; 0 = succeed at once */
    public ToleranceHold(int holdTicks) {
        this.holdTicks = Math.max(0, holdTicks);
    }

    /** @return true once the condition has held for {@code holdTicks + 1} consecutive updates */
    public boolean update(boolean within) {
        satisfiedTicks = within ? satisfiedTicks + 1 : 0;
        return satisfiedTicks > holdTicks;
    }

    public void reset() {
        satisfiedTicks = 0;
    }

    public int satisfiedTicks() {
        return satisfiedTicks;
    }

    /**
     * Angular distance between two Minecraft rotations: yaw difference wrapped to [-180, 180], combined with the pitch
     * difference as a vector length and capped at 180, the same metric as {@code Rotation.angleTo}.
     */
    public static float angularError(float yawA, float pitchA, float yawB, float pitchB) {
        float dyaw = wrap(yawB - yawA);
        float dpitch = pitchB - pitchA;
        return Math.min((float) Math.sqrt(dyaw * dyaw + dpitch * dpitch), 180.0f);
    }

    private static float wrap(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }
}
