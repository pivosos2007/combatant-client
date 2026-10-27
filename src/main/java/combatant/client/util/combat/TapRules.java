/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

/** Decision rules for resetting sprint knockback after a hit (W-tap / shift-tap). */
public final class TapRules {
    private TapRules() {
    }

    /**
     * @param sprinting      the player is sprinting right now (nothing to reset otherwise)
     * @param tapActive      a previous tap is still running
     * @param now            current tick
     * @param lastTapTick    tick the last tap started, or {@link Long#MIN_VALUE} for none
     * @param cooldownTicks  minimum ticks between two tap starts
     * @param chancePercent  0..100
     * @param roll           uniform random in [0, 1)
     */
    public static boolean shouldTap(boolean sprinting, boolean tapActive, long now, long lastTapTick,
                                    int cooldownTicks, int chancePercent, double roll) {
        if (!sprinting || tapActive) return false;
        if (lastTapTick != Long.MIN_VALUE && now - lastTapTick < cooldownTicks) return false;
        if (chancePercent >= 100) return true;
        if (chancePercent <= 0) return false;
        return roll * 100.0 < chancePercent;
    }

    /** Uniform integer in [min, max] from {@code roll} in [0, 1); bounds may come in either order. */
    public static int pickTicks(int min, int max, double roll) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return lo + (int) Math.min(hi - lo, Math.floor(roll * (hi - lo + 1)));
    }
}
