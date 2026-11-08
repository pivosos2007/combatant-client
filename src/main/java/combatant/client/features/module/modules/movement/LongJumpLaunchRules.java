/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.movement;

public final class LongJumpLaunchRules {
    static final double SLIME_TARGET_Y = 1.35;
    static final double VULCAN_SLIME_TARGET_Y = 1.42;
    private static final double SHULKER_TARGET_Y = 0.90;
    private static final double BOAT_TARGET_Y = 1.00;
    private static final double WATER_TARGET_Y = 0.56;

    private LongJumpLaunchRules() { }

    public static double slimeBounceVelocity(double bouncedY) {
        return bouncedY > 0 && Double.isFinite(bouncedY)
                ? Math.max(bouncedY, SLIME_TARGET_Y) : bouncedY;
    }

    public static double vulcanSlimeBounceVelocity(double bouncedY) {
        return bouncedY > 0 && Double.isFinite(bouncedY)
                ? Math.max(bouncedY, VULCAN_SLIME_TARGET_Y) : bouncedY;
    }

    public static double shulkerImpulse(double y) {
        return Math.max(y, SHULKER_TARGET_Y);
    }

    public static double boatImpulse(double y) {
        return Math.max(y, BOAT_TARGET_Y);
    }

    public static double waterImpulse(double y) {
        return Math.max(y, WATER_TARGET_Y);
    }
}
