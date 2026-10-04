/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.antivoid;

/**
 * When AntiVoid should step in. Pure arithmetic so the boundaries (strictly below the band, only
 * while falling, never during the cooldown) are covered by unit tests instead of by falling into the
 * void on a server.
 */
public final class VoidGuard {

    private VoidGuard() {
    }

    /** Feet are inside the band of {@code triggerDistance} blocks above the world floor, or below it. */
    public static boolean inTriggerBand(double feetY, int worldMinY, int triggerDistance) {
        return feetY < worldMinY + triggerDistance;
    }

    /**
     * @param ignoredFlight  gliding with an elytra while the "ignore elytra" option is on
     * @param overVoid       nothing solid lies below the player; only consulted when {@code onlyOverVoid}
     * @param cooldownTicks  ticks left before the next rescue is allowed
     */
    public static boolean shouldAct(double feetY, double motionY, int worldMinY, int triggerDistance,
                                    boolean ignoredFlight, boolean overVoid, boolean onlyOverVoid,
                                    int cooldownTicks) {
        if (cooldownTicks > 0 || ignoredFlight) return false;
        if (motionY >= 0.0) return false;
        if (!inTriggerBand(feetY, worldMinY, triggerDistance)) return false;
        return !onlyOverVoid || overVoid;
    }
}
