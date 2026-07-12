/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.world.entity.player.Player;

public enum ResetAttackCooldown {
    ;

    public static void resetAttackCooldown(Player player) {
        player.resetOnlyAttackStrengthTicker();
    }
}


