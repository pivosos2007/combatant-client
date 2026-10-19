/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.rules;

/**
 * Pure decision rules and curves behind LongJump, Spider, NoWeb, Parkour (edge jump) and AutoJump. They take plain
 * values so they are covered by headless unit tests; the modules only gather the values from the player.
 */
public enum MovementRules {
    ;

    /**
     * Multiplier of the launch speed {@code tick} ticks into a boost: {@code peak} on tick 0, falling to exactly 1.0
     * at {@code duration}. {@code falloff} 1 is linear; higher values drop faster at the start.
     */
    public static double boostFactor(int tick, int duration, double peak, double falloff) {
        double top = Math.max(1.0, peak);
        if (duration <= 0 || tick >= duration) return 1.0;
        double left = 1.0 - Math.max(0, tick) / (double) duration;
        return 1.0 + (top - 1.0) * Math.pow(left, Math.max(0.01, falloff));
    }

    /** Horizontal speed to hold on a boost tick. {@code launchSpeed} is floored so a standing start still boosts. */
    public static double boostSpeed(double launchSpeed, double minLaunchSpeed, int tick, int duration,
                                    double peak, double falloff) {
        return Math.max(launchSpeed, minLaunchSpeed) * boostFactor(tick, duration, peak, falloff);
    }

    /** Edge jump: step off the ledge only when walking, grounded, nothing under the feet and not already jumping. */
    public static boolean edgeJump(boolean onGround, boolean sneaking, boolean jumpHeld, boolean moving,
                                   boolean supportBelow, double velocityY) {
        return onGround && !sneaking && !jumpHeld && moving && !supportBelow && velocityY <= 0.0;
    }

    /** AutoJump: jump on every grounded tick the player is walking and free to jump. */
    public static boolean autoJump(boolean allowed, boolean onGround, boolean moving, boolean sneaking) {
        return allowed && onGround && moving && !sneaking;
    }

    /**
     * Spider climb: the vertical velocity to use while pressed against a wall, or {@code currentY} when the
     * player is not climbing. Never lowers an upward velocity that is already larger than the climb speed.
     */
    public static double spiderVelocity(boolean horizontalCollision, boolean moving, boolean sneaking,
                                        double climbSpeed, double currentY) {
        if (!horizontalCollision || !moving || sneaking) return currentY;
        return Math.max(currentY, Math.max(0.0, climbSpeed));
    }

    /** Spider jump mode: hop only from the ground while blocked by a wall in the walking direction. */
    public static boolean spiderJump(boolean horizontalCollision, boolean moving, boolean sneaking, boolean onGround) {
        return horizontalCollision && moving && !sneaking && onGround;
    }

    /**
     * One axis of a cobweb-style stuck multiplier after NoWeb removed {@code reduction} (0..1) of the slowdown:
     * 0 keeps the vanilla multiplier, 1 makes it 1.0 (no slowdown).
     */
    public static double reducedStuckMultiplier(double multiplier, double reduction) {
        double r = Math.min(1.0, Math.max(0.0, reduction));
        return 1.0 - (1.0 - multiplier) * (1.0 - r);
    }
}
