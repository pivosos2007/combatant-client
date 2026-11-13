/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

/** Charge-window expiration used by SpearAssist auto-recharge. */
public final class SpearKinetics {

    private SpearKinetics() {
    }

    /** An absent condition has duration -1; zero is a valid, immediately expiring window. */
    public static boolean shouldRecharge(boolean useKeyDown, int ticksUsingItem, int delayTicks,
                                         int dismountTicks, int knockbackTicks, int damageTicks) {
        if (!useKeyDown) return false;
        int activeTicks = ticksUsingItem - delayTicks;
        if (activeTicks < 0) return false;
        int longestWindow = Math.max(dismountTicks, Math.max(knockbackTicks, damageTicks));
        return longestWindow >= 0 && activeTicks > longestWindow;
    }
}
