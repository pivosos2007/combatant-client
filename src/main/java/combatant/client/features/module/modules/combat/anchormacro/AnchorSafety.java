/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.anchormacro;

/** Pure self-damage gate for detonating an anchor (same margin as {@code ExplosionDamageRules.isSafe}). */
public final class AnchorSafety {
    private static final float SURVIVAL_MARGIN = 0.5f;

    private AnchorSafety() {
    }

    /**
     * @param selfDamage    predicted explosion damage to the player
     * @param maxSelfDamage configured limit
     * @param health        health plus absorption
     * @param totemInHand   a totem is in the main or off hand
     * @param allowTotemPop a lethal explosion is accepted when a totem would catch it
     */
    public static boolean detonationAllowed(float selfDamage, float maxSelfDamage, float health,
                                            boolean totemInHand, boolean allowTotemPop) {
        if (selfDamage > maxSelfDamage) return false;
        if (selfDamage + SURVIVAL_MARGIN <= health) return true;
        return allowTotemPop && totemInHand;
    }
}
