/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.movement;

/** Pure geometric threshold for placing a full block on an anticipated landing support. */
final class SlimeMlgPlanningRules {
    private SlimeMlgPlanningRules() { }

    static boolean heightEligible(double predictedPeakFeetY, double supportTopY, double minHeight) {
        return Double.isFinite(predictedPeakFeetY) && Double.isFinite(supportTopY)
                && Double.isFinite(minHeight) && minHeight >= 0
                && predictedPeakFeetY - supportTopY >= minHeight
                // One-block-high slime must physically fit beneath the player at the jump apex.
                && predictedPeakFeetY >= supportTopY + 0.999;
    }
}
